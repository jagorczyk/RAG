package com.rag.rag.knowledge.query;

import com.rag.rag.chat.service.QueryPlan;
import com.rag.rag.folder.entity.FileEntity;
import com.rag.rag.folder.repository.FileRepository;
import com.rag.rag.knowledge.graph.EntityMatchMode;
import com.rag.rag.knowledge.graph.GraphQueryService;
import com.rag.rag.knowledge.graph.GroundedVisualClaim;
import com.rag.rag.knowledge.graph.EntityVisualAnchor;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class DynamicVisualMatcherTest {
    @Mock private FileRepository fileRepository;
    @Mock private GraphQueryService graphQueryService;
    @Mock private ChatLanguageModel chatModel;
    @Mock private ChatLanguageModel visionModel;
    @Mock private ImageCandidateRetriever candidateRetriever;
    private DynamicVisualMatcher matcher;
    private FileEntity image;

    @BeforeEach
    void setUp() {
        matcher = new DynamicVisualMatcher(fileRepository, graphQueryService, chatModel, visionModel, candidateRetriever);
        image = FileEntity.builder().path("dir://photos/person.jpg").fileName("person.jpg")
                .fileType("image/jpeg").build();
        lenient().when(graphQueryService.imagePathsForEntities(any())).thenReturn(List.of(image.getPath()));
        lenient().when(graphQueryService.entityConfidenceForFile(any(), anyString())).thenReturn(java.math.BigDecimal.valueOf(0.9));
        lenient().when(candidateRetriever.recall(anyString())).thenReturn(java.util.Map.of());
        lenient().when(fileRepository.findByPath(image.getPath())).thenReturn(Optional.of(image));
        lenient().when(graphQueryService.buildFullContextForFile(image.getPath()))
                .thenReturn("Michał ma jasne, blond włosy.");
        lenient().when(graphQueryService.certainClaimsForFile(anyString(), any())).thenReturn(List.of(claim()));
    }

    @Test
    void evaluatesThePlannerConstraintWithoutAStaticAttributeList() {
        lenient().when(chatModel.generate(anyString())).thenReturn("""
                {"decision":"MATCH","confidence":0.94,"reasons":["włosy są blond"],"missingEvidence":[]}
                """);

        List<VisualQueryMatch> matches = matcher.findEvidence(plan("Czy Michał ma włosy koloru blond?"));

        assertEquals(List.of(image.getPath()), matches.stream().map(VisualQueryMatch::filePath).toList());
    }

    @Test
    void doesNotValidateImagesUnlessTheDynamicPlanRequestsIt() {
        assertTrue(matcher.findEvidence(new QueryPlan("pytanie", List.of("Michał"), "", "pytanie", false,
                false, QueryPlan.RetrievalMode.HYBRID, "")).isEmpty());
    }

    @Test
    void allSameFileUsesIntersectionNotUnionOfEntityPaths() {
        FileEntity joint = FileEntity.builder().path("dir://shared.jpg").fileName("shared.jpg")
                .fileType("image/jpeg").build();
        String partialPath = "dir://only-igor.jpg";
        when(graphQueryService.imagePathsForAllEntities(eq(List.of("Igor", "Anna"))))
                .thenReturn(List.of(joint.getPath()));
        // Union + retrieval would include a partial-only file; ALL_SAME_FILE must ignore them.
        when(candidateRetriever.recall(anyString())).thenReturn(Map.of(partialPath, java.math.BigDecimal.ONE));
        when(fileRepository.findByPath(joint.getPath())).thenReturn(Optional.of(joint));
        when(graphQueryService.buildFullContextForFile(joint.getPath()))
                .thenReturn("Igor i Anna na wspólnym zdjęciu.");
        when(chatModel.generate(anyString())).thenReturn("""
                {"decision":"MATCH","confidence":0.95,"reasons":["obie osoby"],"missingEvidence":[]}
                """);

        QueryPlan plan = new QueryPlan("Czy jest zdjęcie z Igorem i Anną?", List.of("Igor", "Anna"),
                List.of(), "Igor Anna", "Igor i Anna na zdjęciu", true, false,
                QueryPlan.RetrievalMode.VISUAL_VALIDATION, EntityMatchMode.ALL_SAME_FILE,
                "Oceń współwystępowanie.");

        List<VisualQueryMatch> matches = matcher.findEvidence(plan);

        assertEquals(List.of(joint.getPath()), matches.stream().map(VisualQueryMatch::filePath).toList());
        verify(graphQueryService).imagePathsForAllEntities(eq(List.of("Igor", "Anna")));
        verify(graphQueryService, never()).imagePathsForEntities(any());
        verify(graphQueryService, never()).buildFullContextForFile(partialPath);
        verify(fileRepository, never()).findByPath(partialPath);
    }

    @Test
    void allSameFileEmptyIntersectionYieldsNoVisualSources() {
        when(graphQueryService.imagePathsForAllEntities(eq(List.of("Igor", "Anna")))).thenReturn(List.of());
        QueryPlan plan = new QueryPlan("Czy jest zdjęcie z Igorem i Anną?", List.of("Igor", "Anna"),
                List.of("dir://only-igor.jpg"), "Igor Anna", "obie osoby", true, false,
                QueryPlan.RetrievalMode.VISUAL_VALIDATION, EntityMatchMode.ALL_SAME_FILE, "");

        assertTrue(matcher.findEvidence(plan).isEmpty());
        verify(chatModel, never()).generate(anyString());
        verify(fileRepository, never()).findByPath("dir://only-igor.jpg");
    }

    @Test
    void explicitFileScopeNeverExpandsToRecallCandidates() {
        when(chatModel.generate(anyString())).thenReturn("""
                {"decision":"MATCH","confidence":0.95,"reasons":["wskazany obraz"],"missingEvidence":[]}
                """);
        QueryPlan scoped = new QueryPlan("Co przedstawia @person.jpg?", List.of(),
                List.of(image.getPath()), "person.jpg", "opisz wskazany obraz", true, false,
                QueryPlan.RetrievalMode.VISUAL_VALIDATION, EntityMatchMode.ANY, "");

        assertEquals(List.of(image.getPath()), matcher.findEvidence(scoped).stream()
                .map(VisualQueryMatch::filePath).toList());
        verify(candidateRetriever, never()).recall(anyString());
        verify(graphQueryService, never()).imagePathsForEntities(any());
    }

    @Test
    void failedPixelVerificationDoesNotKeepStoredContextMatch() {
        image.setImageData(new byte[]{1, 2, 3});
        when(chatModel.generate(anyString())).thenReturn("""
                {"decision":"MATCH","confidence":0.75,"reasons":["słaby kontekst"],"missingEvidence":[]}
                """);
        when(visionModel.generate(any(UserMessage.class))).thenThrow(new RuntimeException("vision down"));

        assertTrue(matcher.findEvidence(plan("Czy Michał ma blond włosy?")).isEmpty());
    }

    @Test
    void rejectsPixelClaimForAnotherPersonEvenOnTheCorrectFile() {
        image.setImageData(new byte[]{1, 2, 3});
        when(graphQueryService.certainClaimsForFile(anyString(), any())).thenReturn(List.of());
        when(graphQueryService.certainEntityAnchorsForFile(image.getPath(), List.of("Michał")))
                .thenReturn(List.of(new EntityVisualAnchor(UUID.randomUUID(), "Michał", "face_1",
                        List.of(10f, 10f, 30f, 30f), BigDecimal.ONE)));
        when(visionModel.generate(any(UserMessage.class))).thenReturn(Response.from(AiMessage.from("""
                {"decision":"MATCH","confidence":0.96,"reasons":["widoczna czynność"],
                 "missingEvidence":[],"claims":[{"entity":"Olek","predicate":"biegnie",
                 "value":"","statementPl":"Olek biegnie.","confidence":0.96}]}
                """)));

        assertTrue(matcher.findEvidence(plan("Co robi Michał?")).isEmpty());
    }

    @Test
    void acceptsPixelClaimOnlyForCertainRequestedAnchor() {
        image.setImageData(new byte[]{1, 2, 3});
        when(graphQueryService.certainClaimsForFile(anyString(), any())).thenReturn(List.of());
        when(graphQueryService.certainEntityAnchorsForFile(image.getPath(), List.of("Michał")))
                .thenReturn(List.of(new EntityVisualAnchor(UUID.randomUUID(), "Michał", "face_1",
                        List.of(10f, 10f, 30f, 30f), BigDecimal.ONE)));
        when(visionModel.generate(any(UserMessage.class))).thenReturn(Response.from(AiMessage.from("""
                {"decision":"MATCH","confidence":0.96,"reasons":["twarz jest zakotwiczona"],
                 "missingEvidence":[],"claims":[{"entity":"Michał","predicate":"stoi",
                 "value":"","statementPl":"Michał stoi i patrzy w stronę aparatu.","confidence":0.96}]}
                """)));

        List<VisualQueryMatch> result = matcher.findEvidence(plan("Co robi Michał?"));

        assertEquals(1, result.size());
        assertEquals("Michał", result.get(0).claims().get(0).entityName());
        assertEquals("Michał stoi i patrzy w stronę aparatu.", result.get(0).claims().get(0).statementPl());
    }

    private QueryPlan plan(String question) {
        return new QueryPlan(question, List.of("Michał"), "", question, true, false,
                QueryPlan.RetrievalMode.VISUAL_VALIDATION, "Oceń cechę na obrazie.");
    }

    private GroundedVisualClaim claim() {
        return new GroundedVisualClaim("F-1", UUID.randomUUID(), "Michał", "ma", "blond włosy",
                "Michał ma blond włosy.", image == null ? "dir://photos/person.jpg" : image.getPath(),
                BigDecimal.valueOf(0.9), "GRAPH_FACT", "face_1");
    }
}

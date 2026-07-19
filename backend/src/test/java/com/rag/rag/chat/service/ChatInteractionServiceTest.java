package com.rag.rag.chat.service;

import com.rag.rag.chat.dto.MessageRequest;
import com.rag.rag.chat.dto.MessageResponse;
import com.rag.rag.chat.repository.ChatMemoryRepository;
import com.rag.rag.chat.repository.ChatMessageRepository;
import com.rag.rag.ingestion.dto.SourceDto;
import com.rag.rag.ingestion.service.IngestionService;
import com.rag.rag.knowledge.graph.EntityMatchMode;
import com.rag.rag.knowledge.graph.GraphQueryService;
import com.rag.rag.knowledge.graph.GraphEvidenceResult;
import com.rag.rag.knowledge.query.DynamicVisualMatcher;
import com.rag.rag.knowledge.query.VisualMatchDecision;
import com.rag.rag.knowledge.query.VisualQueryMatch;
import dev.langchain4j.service.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@ExtendWith(MockitoExtension.class)
class ChatInteractionServiceTest {
    @Mock private ChatService chatAiService;
    @Mock private ChatMemoryRepository chatMemoryRepository;
    @Mock private ChatMessageRepository chatMessageRepository;
    @Mock private IngestionService ingestionService;
    @Mock private GraphQueryService graphQueryService;
    @Mock private DynamicVisualMatcher dynamicVisualMatcher;
    @Mock private QueryPlanner queryPlanner;
    @Mock private VerifiedVisualAnswerService verifiedVisualAnswerService;
    @InjectMocks private ChatInteractionService service;
    private UUID chatId;

    @BeforeEach
    void setUp() {
        chatId = UUID.randomUUID();
        lenient().when(chatMemoryRepository.findById(chatId)).thenReturn(Optional.empty());
        lenient().when(graphQueryService.buildContextForEntities(any())).thenReturn("");
        lenient().when(graphQueryService.buildEvidence(anyList(), anyList(), any()))
                .thenReturn(new GraphEvidenceResult("", List.of()));
    }

    @Test
    void answersThroughTheDynamicPlanWithoutUsingPhraseRouting() {
        QueryPlan plan = new QueryPlan("Czy Igor jest w garniturze?", List.of("Igor"), "",
                "Czy Igor jest w garniturze?", false, false, QueryPlan.RetrievalMode.HYBRID,
                "Odpowiedz z dowodów.");
        when(queryPlanner.plan(eq(plan.question()), anyString())).thenReturn(plan);
        when(graphQueryService.buildEvidence(anyList(), anyList(), any()))
                .thenReturn(new GraphEvidenceResult("[Fakty]\n- Igor: garnitur", List.of("dir://a.jpg")));
        Result<String> result = Result.<String>builder().content("Tak, Igor jest w garniturze.").build();
        when(chatAiService.answer(eq(chatId), anyString())).thenReturn(result);
        SourceDto source = new SourceDto("dir://a.jpg", "a.jpg", 0.9, null, "IMAGE");
        when(ingestionService.getSources(result)).thenReturn(List.of(source));
        when(ingestionService.createGraphFactSourceDto(eq("dir://a.jpg"), any(), anyDouble()))
                .thenReturn(new SourceDto("dir://a.jpg", "a.jpg", 1.0, null, "GRAPH_FACT"));

        MessageResponse response = service.processChatMessage(chatId, new MessageRequest(plan.question()));

        assertEquals("Tak, Igor jest w garniturze.", response.response());
        assertEquals(QueryPlan.RetrievalMode.HYBRID.name(), response.answerKind());
        assertFalse(response.response().contains("dir://"));
        verify(queryPlanner).plan(eq(plan.question()), anyString());
        verifyNoInteractions(dynamicVisualMatcher);
    }

    @Test
    void doesNotPromoteUncertainGraphPathsAsSources() {
        QueryPlan plan = new QueryPlan("Co robi Igor?", List.of("Igor"), List.of("dir://maybe.jpg"),
                "Co robi Igor?", "", false, false, QueryPlan.RetrievalMode.GRAPH,
                "Odpowiedz z grafu.");
        when(queryPlanner.plan(eq(plan.question()), anyString())).thenReturn(plan);
        when(graphQueryService.buildEvidence(anyList(), anyList(), any()))
                .thenReturn(new GraphEvidenceResult("", List.of()));
        Result<String> result = Result.<String>builder().content("Nie znaleziono informacji w dokumentach.").build();
        when(chatAiService.answer(eq(chatId), anyString())).thenReturn(result);

        MessageResponse response = service.processChatMessage(chatId, new MessageRequest(plan.question()));

        assertTrue(response.sources().isEmpty());
        verify(ingestionService, never()).createGraphFactSourceDto(anyString(), any(), anyDouble());
    }

    @Test
    void usesVisualEvidenceOnlyWhenThePlannerRequestsIt() {
        QueryPlan plan = new QueryPlan("Czy Michał ma blond włosy?", List.of("Michał"), "",
                "Michał ma blond włosy", true, false, QueryPlan.RetrievalMode.VISUAL_VALIDATION,
                "Odpowiedz z obrazu.");
        when(queryPlanner.plan(eq(plan.question()), anyString())).thenReturn(plan);
        VisualQueryMatch match = new VisualQueryMatch("dir://michal.jpg", BigDecimal.valueOf(0.91),
                List.of("blond włosy"), VisualMatchDecision.Decision.MATCH, List.of(),
                BigDecimal.valueOf(0.8), BigDecimal.valueOf(0.9));
        when(dynamicVisualMatcher.findEvidence(plan)).thenReturn(List.of(match));
        SourceDto source = new SourceDto("dir://michal.jpg", "michal.jpg", 0.8, null, "IMAGE");
        when(ingestionService.createSourceDto(anyString(), any(), anyDouble())).thenReturn(source);
        when(verifiedVisualAnswerService.answer(eq(plan.question()), eq(1)))
                .thenReturn("Tak, Michał ma blond włosy.");

        MessageResponse response = service.processChatMessage(chatId, new MessageRequest(plan.question()));

        assertEquals("Tak, Michał ma blond włosy.", response.response());
        assertEquals(1, response.evidence().size());
        assertFalse(response.uncertain());
        verify(verifiedVisualAnswerService).answer(eq(plan.question()), eq(1));
    }

    @Test
    void doesNotPresentAnUnvalidatedRagImageAsVisualEvidence() {
        QueryPlan plan = new QueryPlan("Daj zdjęcia kolesia w białym stroju rajdowym", List.of(), "",
                "biały strój rajdowy", true, false, QueryPlan.RetrievalMode.VISUAL_VALIDATION, "");
        when(queryPlanner.plan(eq(plan.question()), anyString())).thenReturn(plan);
        when(dynamicVisualMatcher.findEvidence(plan)).thenReturn(List.of());
        SourceDto retrieved = new SourceDto("dir://photos/20220320_170940.jpg", "20220320_170940.jpg", 0.87, null, "IMAGE");

        MessageResponse response = service.processChatMessage(chatId, new MessageRequest(plan.question()));

        assertTrue(response.sources().isEmpty());
        assertFalse(response.response().contains(retrieved.fileName()));
    }

    @ParameterizedTest
    @EnumSource(value = QueryPlan.RetrievalMode.class, names = {"GRAPH", "HYBRID", "DOCUMENT"})
    void emptyAllSameFileIntersectionDeniesForEveryRetrievalMode(QueryPlan.RetrievalMode mode) {
        QueryPlan plan = coPresencePlan("Czy jest zdjęcie na którym jest Igor i Anna?",
                List.of("Igor", "Anna"), mode);
        when(queryPlanner.plan(eq(plan.question()), anyString())).thenReturn(plan);
        when(graphQueryService.buildEvidence(eq(List.of("Igor", "Anna")), anyList(), eq(EntityMatchMode.ALL_SAME_FILE)))
                .thenReturn(new GraphEvidenceResult("", List.of()));

        MessageResponse response = service.processChatMessage(chatId, new MessageRequest(plan.question()));

        assertEquals("Nie znaleziono potwierdzonego wspólnego zdjęcia tych osób.", response.response());
        assertTrue(response.sources().isEmpty());
        assertTrue(response.uncertain());
        assertEquals(mode.name(), response.answerKind());
        // LLM must not be invited to invent a joint photo from partial RAG hits.
        verify(chatAiService, never()).answer(any(), anyString());
        verify(ingestionService, never()).getSources(any());
        verify(ingestionService, never()).createGraphFactSourceDto(anyString(), any(), anyDouble());
    }

    @Test
    void hybridAllSameFileDoesNotReinjectPartialRetrievalSources() {
        QueryPlan plan = coPresencePlan("Czy jest wspólne zdjęcie Igora i Anny?",
                List.of("Igor", "Anna"), QueryPlan.RetrievalMode.HYBRID);
        when(queryPlanner.plan(eq(plan.question()), anyString())).thenReturn(plan);
        // Graph has no joint file; a hallucinating LLM path would still surface only-X from RAG.
        when(graphQueryService.buildEvidence(eq(List.of("Igor", "Anna")), anyList(), eq(EntityMatchMode.ALL_SAME_FILE)))
                .thenReturn(new GraphEvidenceResult("", List.of()));

        MessageResponse response = service.processChatMessage(chatId, new MessageRequest(plan.question()));

        assertTrue(response.sources().stream().noneMatch(s -> "dir://only-igor.jpg".equals(s.path())));
        assertTrue(response.sources().isEmpty());
        assertTrue(response.uncertain());
        assertFalse(response.response().toLowerCase().startsWith("tak"));
    }

    @Test
    void nonEmptyAllSameFileIntersectionAllowsJointSourceAndAnswer() {
        QueryPlan plan = coPresencePlan("Czy jest zdjęcie z Igorem i Anną?",
                List.of("Igor", "Anna"), QueryPlan.RetrievalMode.HYBRID);
        when(queryPlanner.plan(eq(plan.question()), anyString())).thenReturn(plan);
        when(graphQueryService.buildEvidence(eq(List.of("Igor", "Anna")), anyList(), eq(EntityMatchMode.ALL_SAME_FILE)))
                .thenReturn(new GraphEvidenceResult(
                        "- współwystępowanie=Igor, Anna; file=dir://shared.jpg",
                        List.of("dir://shared.jpg")));
        Result<String> result = Result.<String>builder().content("Tak, jest wspólne zdjęcie.").build();
        when(chatAiService.answer(eq(chatId), anyString())).thenReturn(result);
        when(ingestionService.createGraphFactSourceDto(eq("dir://shared.jpg"), any(), anyDouble()))
                .thenReturn(new SourceDto("dir://shared.jpg", "shared.jpg", 1.0, null, "GRAPH_FACT"));

        MessageResponse response = service.processChatMessage(chatId, new MessageRequest(plan.question()));

        assertEquals("Tak, jest wspólne zdjęcie.", response.response());
        assertEquals(1, response.sources().size());
        assertEquals("dir://shared.jpg", response.sources().get(0).path());
        assertFalse(response.uncertain());
        // Hybrid RAG sources are never consulted under ALL_SAME_FILE — only joint graph paths.
        verify(ingestionService, never()).getSources(any());
    }

    private static QueryPlan coPresencePlan(String question, List<String> entities,
                                            QueryPlan.RetrievalMode mode) {
        return new QueryPlan(question, entities, List.of(), question, question, false, false,
                mode, EntityMatchMode.ALL_SAME_FILE,
                "Jedno krótkie zdanie po polsku; nie wymyślaj wspólnego zdjęcia bez dowodu.");
    }
}

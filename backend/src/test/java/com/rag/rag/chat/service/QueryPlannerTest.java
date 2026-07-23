package com.rag.rag.chat.service;

import com.rag.rag.knowledge.graph.GraphQueryService;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import com.rag.rag.knowledge.graph.EntityMatchMode;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

@ExtendWith(MockitoExtension.class)
class QueryPlannerTest {
    @Mock private GraphQueryService graphQueryService;
    @Mock private ChatLanguageModel chatModel;
    @Mock private LibraryScopeService libraryScopeService;

    @BeforeEach
    void stubTextResolve() {
        lenient().when(graphQueryService.resolveEntityNamesFromText(anyString())).thenReturn(List.of());
    }

    @Test
    void acceptsAnUnseenVisualAttributeWithoutApplicationVocabulary() {
        String question = "Czy Michał ma włosy koloru blond?";
        when(graphQueryService.availableEntityNames()).thenReturn(List.of("Michał"));
        when(graphQueryService.validateEntityNames(List.of("Michał"))).thenReturn(List.of("Michał"));
        when(graphQueryService.validateFilePaths(List.of())).thenReturn(List.of());
        when(chatModel.generate(anyString())).thenReturn("""
                {"entities":["Michał"],"condition":"włosy Michała mają kolor blond",
                "visualCondition":true,"ambiguous":false,"retrievalMode":"VISUAL_VALIDATION",
                "answerInstruction":"Odpowiedz wyłącznie z dowodów obrazu."}
                """);

        QueryPlan plan = new QueryPlanner(graphQueryService, chatModel).plan(question);

        assertEquals(List.of("Michał"), plan.entities());
        assertTrue(plan.visualCondition());
        assertEquals(QueryPlan.RetrievalMode.VISUAL_VALIDATION, plan.retrievalMode());
        assertTrue(plan.condition().contains("blond"));
    }

    @Test
    void fallsBackToGenericHybridRetrievalWhenPlannerOutputIsInvalid() {
        when(graphQueryService.availableEntityNames()).thenReturn(List.of("Igor", "Anna"));
        when(chatModel.generate(anyString())).thenReturn("not-json");

        QueryPlan plan = new QueryPlanner(graphQueryService, chatModel).plan("Jakie jest saldo na fakturze?");

        assertEquals(QueryPlan.RetrievalMode.HYBRID, plan.retrievalMode());
        assertTrue(plan.entities().isEmpty());
        assertEquals("Jakie jest saldo na fakturze?", plan.condition());
    }

    @Test
    void invalidJsonWithResolvedPolishNameFallsBackToGraph() {
        when(graphQueryService.availableEntityNames()).thenReturn(List.of("Olek"));
        when(graphQueryService.resolveEntityNamesFromText("Gdzie jest Olka?")).thenReturn(List.of("Olek"));
        when(chatModel.generate(anyString())).thenReturn("not-json");

        QueryPlan plan = new QueryPlanner(graphQueryService, chatModel).plan("Gdzie jest Olka?");

        assertEquals(QueryPlan.RetrievalMode.GRAPH, plan.retrievalMode());
        assertEquals(List.of("Olek"), plan.entities());
    }

    @Test
    void supportsSemanticAllEntitiesOnTheSameFileOperation() {
        when(graphQueryService.availableEntityNames()).thenReturn(List.of("Igor", "Anna"));
        when(graphQueryService.validateEntityNames(List.of("Igor", "Anna"))).thenReturn(List.of("Igor", "Anna"));
        when(graphQueryService.validateFilePaths(List.of())).thenReturn(List.of());
        when(chatModel.generate(anyString())).thenReturn("""
                {"entities":["Igor","Anna"],"condition":"obydwie osoby na tym samym obrazie",
                "visualCondition":false,"ambiguous":false,"retrievalMode":"GRAPH",
                "entityMatchMode":"ALL_SAME_FILE","answerInstruction":"Odpowiedz krótko."}
                """);

        QueryPlan plan = new QueryPlanner(graphQueryService, chatModel).plan("Czy Igor i Anna są razem?");

        assertEquals(EntityMatchMode.ALL_SAME_FILE, plan.entityMatchMode());
        assertEquals(List.of("Igor", "Anna"), plan.entities());
    }

    @Test
    void plannerPromptIncludesConversationContextWithRolesAndSourcePaths() {
        String conversationContext = """
                USER: Pokaż Igora
                SOURCES: []
                AI: Igor jest na plaży.
                SOURCES: [dir://photos/igor.jpg]
                USER: A co jest na tym zdjęciu?
                SOURCES: []
                """;
        when(graphQueryService.availableEntityNames()).thenReturn(List.of("Igor"));
        when(graphQueryService.validateEntityNames(List.of("Igor"))).thenReturn(List.of("Igor"));
        when(graphQueryService.validateFilePaths(List.of("dir://photos/igor.jpg")))
                .thenReturn(List.of("dir://photos/igor.jpg"));
        when(chatModel.generate(anyString())).thenReturn("""
                {"entities":["Igor"],"fileScope":["dir://photos/igor.jpg"],
                "retrievalQuery":"co widać na zdjęciu Igora dir://photos/igor.jpg",
                "condition":"opis treści wcześniej zwróconego zdjęcia",
                "visualCondition":false,"ambiguous":false,"retrievalMode":"HYBRID",
                "answerInstruction":"Odpowiedz krótko po polsku."}
                """);

        QueryPlan plan = new QueryPlanner(graphQueryService, chatModel)
                .plan("A co jest na tym zdjęciu?", conversationContext);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(chatModel).generate(promptCaptor.capture());
        String prompt = promptCaptor.getValue();
        assertTrue(prompt.contains("Ostatnia rozmowa") || prompt.contains("SOURCES"));
        assertTrue(prompt.contains("Znane osoby") || prompt.contains("ludzie"));
        assertTrue(prompt.contains("GRAPH"));
        assertTrue(prompt.contains("USER:"));
        assertTrue(prompt.contains("AI:"));
        assertTrue(prompt.contains("dir://photos/igor.jpg"));
        assertTrue(prompt.contains("A co jest na tym zdjęciu?"));
        assertEquals(List.of("Igor"), plan.entities());
        assertEquals(List.of("dir://photos/igor.jpg"), plan.fileScope());
        assertTrue(plan.retrievalQuery().contains("igor") || plan.retrievalQuery().contains("zdję"));
        assertEquals(QueryPlan.RetrievalMode.HYBRID, plan.retrievalMode());
    }

    @Test
    void plannerPromptPrefersGraphForPeopleAndHybridOtherwise() {
        when(graphQueryService.availableEntityNames()).thenReturn(List.of("Igor"));
        when(graphQueryService.validateEntityNames(List.of("Igor"))).thenReturn(List.of("Igor"));
        when(graphQueryService.validateFilePaths(List.of())).thenReturn(List.of());
        when(chatModel.generate(anyString())).thenReturn("""
                {"entities":["Igor"],"condition":"kim jest Igor na zdjęciach",
                "visualCondition":false,"ambiguous":false,"retrievalMode":"GRAPH",
                "answerInstruction":"Odpowiedz krótko."}
                """);

        QueryPlan plan = new QueryPlanner(graphQueryService, chatModel).plan("Kim jest Igor?");

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(chatModel).generate(promptCaptor.capture());
        String prompt = promptCaptor.getValue();
        assertTrue(prompt.contains("Zwierzęta") || prompt.contains("ANIMALS") || prompt.contains("obiekty NIE"));
        assertTrue(prompt.contains("HYBRID"));
        assertEquals(QueryPlan.RetrievalMode.GRAPH, plan.retrievalMode());
        assertEquals(List.of("Igor"), plan.entities());
    }

    @Test
    void plansACompleteFolderOverviewWithoutChangingNonPersonHybridRouting() {
        var folderId = java.util.UUID.randomUUID();
        when(graphQueryService.availableEntityNames()).thenReturn(List.of());
        when(libraryScopeService.availableFolderNames()).thenReturn(List.of("Wakacje 2024"));
        when(libraryScopeService.resolveFolderNames(List.of("Wakacje 2024")))
                .thenReturn(new LibraryScopeService.FolderResolution(
                        List.of(folderId), false, false));
        when(graphQueryService.validateFilePaths(List.of())).thenReturn(List.of());
        when(chatModel.generate(anyString())).thenReturn("""
                {"entities":[],"fileScope":[],"folderNames":["Wakacje 2024"],
                "scopeKind":"FOLDER","collectionOverview":true,
                "retrievalQuery":"podsumowanie zawartości folderu Wakacje 2024",
                "condition":"inwentarz i opis zawartości folderu",
                "visualCondition":false,"ambiguous":false,"retrievalMode":"HYBRID",
                "entityMatchMode":"ANY","answerInstruction":"Odpowiedz krótko po polsku."}
                """);

        QueryPlan plan = new QueryPlanner(graphQueryService, chatModel, libraryScopeService)
                .plan("Co znajduje się w folderze Wakacje 2024?");

        assertEquals(QueryPlan.RetrievalMode.HYBRID, plan.retrievalMode());
        assertEquals(QueryPlan.ScopeKind.FOLDER, plan.scopeKind());
        assertEquals(List.of(folderId), plan.folderScope());
        assertTrue(plan.collectionOverview());
    }

    @Test
    void unnamedVisualSearchIsNotRoutedToCatalogOverview() {
        when(graphQueryService.availableEntityNames()).thenReturn(List.of("Igor", "Olek"));
        when(libraryScopeService.availableFolderNames()).thenReturn(List.of("Wakacje"));
        when(libraryScopeService.resolveFolderNames(List.of()))
                .thenReturn(LibraryScopeService.FolderResolution.empty());
        when(graphQueryService.validateEntityNames(List.of())).thenReturn(List.of());
        when(graphQueryService.validateFilePaths(List.of())).thenReturn(List.of());
        when(graphQueryService.resolveEntityNamesFromText(anyString())).thenReturn(List.of());
        when(chatModel.generate(anyString())).thenReturn("""
                {"entities":[],"fileScope":[],"folderNames":[],
                "scopeKind":"UNRESTRICTED","collectionOverview":false,
                "retrievalQuery":"osoba w czerwonej kurtce",
                "condition":"znajdź osobę w czerwonej kurtce",
                "visualCondition":true,"ambiguous":false,
                "retrievalMode":"VISUAL_VALIDATION","entityMatchMode":"ANY",
                "answerInstruction":"Odpowiedz z dowodów wizualnych."}
                """);

        QueryPlan plan = new QueryPlanner(graphQueryService, chatModel, libraryScopeService)
                .plan("Kto ma czerwoną kurtkę?");

        assertFalse(plan.collectionOverview());
        assertTrue(plan.visualCondition());
        assertEquals(QueryPlan.RetrievalMode.VISUAL_VALIDATION, plan.retrievalMode());
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(chatModel).generate(prompt.capture());
        assertTrue(prompt.getValue().contains("również wtedy, gdy pytanie nie"));
    }
}

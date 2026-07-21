package com.rag.rag.chat.service;

import com.rag.rag.chat.dto.MessageRequest;
import com.rag.rag.chat.dto.MessageResponse;
import com.rag.rag.chat.entity.ChatMessageEntity;
import com.rag.rag.chat.repository.ChatMemoryRepository;
import com.rag.rag.chat.repository.ChatMessageRepository;
import com.rag.rag.ingestion.dto.SourceDto;
import com.rag.rag.ingestion.service.IngestionService;
import com.rag.rag.knowledge.graph.EntityMatchMode;
import com.rag.rag.knowledge.graph.GraphQueryService;
import com.rag.rag.knowledge.graph.GraphEvidenceResult;
import com.rag.rag.knowledge.graph.GroundedVisualClaim;
import com.rag.rag.knowledge.query.DynamicVisualMatcher;
import com.rag.rag.knowledge.query.VisualMatchDecision;
import com.rag.rag.knowledge.query.VisualQueryMatch;
import dev.langchain4j.service.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
    @Mock private ChatMemoryService chatMemoryService;
    @Mock private IngestionService ingestionService;
    @Mock private GraphQueryService graphQueryService;
    @Mock private DynamicVisualMatcher dynamicVisualMatcher;
    @Mock private QueryPlanner queryPlanner;
    @Mock private VerifiedVisualAnswerService verifiedVisualAnswerService;
    @Mock private ClaimAnswerComposer claimAnswerComposer;
    @InjectMocks private ChatInteractionService service;
    private UUID chatId;

    @BeforeEach
    void setUp() {
        chatId = UUID.randomUUID();
        lenient().when(chatMemoryRepository.findById(chatId)).thenReturn(Optional.empty());
        lenient().when(graphQueryService.buildContextForEntities(any())).thenReturn("");
        lenient().when(graphQueryService.buildEvidence(anyList(), anyList(), any()))
                .thenReturn(new GraphEvidenceResult("", List.of()));
        lenient().when(graphQueryService.certainParticipantNamesForPaths(anyList()))
                .thenReturn(List.of());
        // Default: claim path not used (empty result) so free-form GRAPH tests keep working.
        lenient().when(claimAnswerComposer.answerFromClaims(anyString(), anyList(), anyList()))
                .thenReturn(ClaimAnswerComposer.ClaimAnswerResult.empty());
        lenient().when(claimAnswerComposer.answerFromClaims(anyString(), anyList()))
                .thenReturn(ClaimAnswerComposer.ClaimAnswerResult.empty());
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
    void graphMissWithNamedEntityAndUnrelatedHybridHitsForcesNoEvidence() {
        QueryPlan plan = new QueryPlan("Co robi Igor?", List.of("Igor"), List.of(),
                "Co robi Igor?", "", false, false, QueryPlan.RetrievalMode.GRAPH,
                "Odpowiedz z grafu.");
        when(queryPlanner.plan(eq(plan.question()), anyString())).thenReturn(plan);
        when(graphQueryService.buildEvidence(anyList(), anyList(), any()))
                .thenReturn(new GraphEvidenceResult("", List.of()));
        Result<String> result = Result.<String>builder().content("Igor je zupę według dokumentu.").build();
        when(chatAiService.answer(eq(chatId), anyString())).thenReturn(result);
        // Unrelated hybrid hit must not become a certain source for Igor, nor keep a free-form answer.
        SourceDto docSource = new SourceDto("dir://other-person.txt", "other-person.txt", 0.8, null, "TEXT");
        when(ingestionService.getSources(result)).thenReturn(List.of(docSource));

        MessageResponse response = service.processChatMessage(chatId, new MessageRequest(plan.question()));

        // Graph-empty GRAPH plans still invoke hybrid retrieval (not hard joint-file denial).
        verify(chatAiService).answer(eq(chatId), anyString());
        verify(ingestionService, atLeastOnce()).getSources(result);
        verify(ingestionService, never()).createGraphFactSourceDto(anyString(), any(), anyDouble());
        // Post-filter sources empty + no graph → refuse hallucination from world/wrong docs.
        assertEquals("Nie znaleziono informacji w dokumentach.", response.response());
        assertEquals("NO_EVIDENCE", response.answerKind());
        assertTrue(response.sources().isEmpty());
        assertTrue(response.uncertain());
    }

    @Test
    void graphMissWithoutNamedEntitiesKeepsHybridSourcesAndAnswer() {
        QueryPlan plan = new QueryPlan("Jakie jest saldo na fakturze?", List.of(), List.of(),
                "Jakie jest saldo na fakturze?", "", false, false, QueryPlan.RetrievalMode.GRAPH,
                "Odpowiedz z dokumentów.");
        when(queryPlanner.plan(eq(plan.question()), anyString())).thenReturn(plan);
        when(graphQueryService.buildEvidence(anyList(), anyList(), any()))
                .thenReturn(new GraphEvidenceResult("", List.of()));
        Result<String> result = Result.<String>builder().content("Saldo wynosi 1200 zł.").build();
        when(chatAiService.answer(eq(chatId), anyString())).thenReturn(result);
        SourceDto docSource = new SourceDto("dir://invoice.pdf", "invoice.pdf", 0.88, null, "PDF");
        when(ingestionService.getSources(result)).thenReturn(List.of(docSource));

        MessageResponse response = service.processChatMessage(chatId, new MessageRequest(plan.question()));

        assertEquals("Saldo wynosi 1200 zł.", response.response());
        assertEquals(QueryPlan.RetrievalMode.HYBRID.name(), response.answerKind());
        assertEquals(1, response.sources().size());
        assertEquals("dir://invoice.pdf", response.sources().get(0).path());
        // Graph-miss hybrid answers remain marked uncertain (no certain graph proof).
        assertTrue(response.uncertain());
    }

    @Test
    void emptyRetrievalAndEmptyGraphForcesNoEvidenceAnswer() {
        QueryPlan plan = new QueryPlan("Co jest na zdjęciu?", List.of(), List.of(),
                "Co jest na zdjęciu?", "", false, false, QueryPlan.RetrievalMode.HYBRID,
                "Odpowiedz z dowodów.");
        when(queryPlanner.plan(eq(plan.question()), anyString())).thenReturn(plan);
        Result<String> result = Result.<String>builder()
                .content("Na zdjęciu widać plażę i palmy.") // hallucinated without retrieval
                .build();
        when(chatAiService.answer(eq(chatId), anyString())).thenReturn(result);
        when(ingestionService.getSources(result)).thenReturn(List.of());

        MessageResponse response = service.processChatMessage(chatId, new MessageRequest(plan.question()));

        assertEquals("Nie znaleziono informacji w dokumentach.", response.response());
        assertTrue(response.sources().isEmpty());
        assertTrue(response.uncertain());
        assertEquals("NO_EVIDENCE", response.answerKind());
        verify(graphQueryService, never()).buildEvidence(anyList(), anyList(), any());
    }

    @Test
    void namedEntityHybridDropsSourcesWithoutCertainEvidenceForThatPerson() {
        QueryPlan plan = new QueryPlan("Gdzie jest Igor?", List.of("Igor"), List.of(),
                "Gdzie jest Igor?", "", false, false, QueryPlan.RetrievalMode.HYBRID,
                "Odpowiedz z dowodów.");
        when(queryPlanner.plan(eq(plan.question()), anyString())).thenReturn(plan);
        when(graphQueryService.buildEvidence(anyList(), anyList(), any()))
                .thenReturn(new GraphEvidenceResult(
                        "- entity=Igor; file=dir://igor-only.jpg",
                        List.of("dir://igor-only.jpg")));
        Result<String> result = Result.<String>builder().content("Igor jest na zdjęciu.").build();
        when(chatAiService.answer(eq(chatId), anyString())).thenReturn(result);
        when(ingestionService.getSources(result)).thenReturn(List.of(
                new SourceDto("dir://igor-only.jpg", "igor-only.jpg", 0.9, null, "IMAGE"),
                new SourceDto("dir://other-person.jpg", "other-person.jpg", 0.88, null, "IMAGE")
        ));
        when(ingestionService.createGraphFactSourceDto(eq("dir://igor-only.jpg"), any(), anyDouble()))
                .thenReturn(new SourceDto("dir://igor-only.jpg", "igor-only.jpg", 1.0, null, "GRAPH_FACT"));

        MessageResponse response = service.processChatMessage(chatId, new MessageRequest(plan.question()));

        assertEquals(1, response.sources().size());
        assertEquals("dir://igor-only.jpg", response.sources().get(0).path());
        assertTrue(response.sources().stream().noneMatch(s -> "dir://other-person.jpg".equals(s.path())));
    }

    @Test
    void fileScopeExcludesOutOfScopeHybridSources() {
        QueryPlan plan = new QueryPlan(
                "Co w folderze?",
                List.of("Igor"),
                List.of("dir://wakacje/a.jpg"),
                "Co w folderze?",
                "",
                false,
                false,
                QueryPlan.RetrievalMode.HYBRID,
                "Odpowiedz z dowodów.");
        when(queryPlanner.plan(eq(plan.question()), anyString())).thenReturn(plan);
        when(graphQueryService.buildEvidence(anyList(), anyList(), any()))
                .thenReturn(new GraphEvidenceResult("[Fakty]\n- Igor", List.of("dir://wakacje/a.jpg", "dir://other/x.jpg")));
        Result<String> result = Result.<String>builder().content("Igor jest na wakacjach.").build();
        when(chatAiService.answer(eq(chatId), anyString())).thenReturn(result);
        when(ingestionService.getSources(result)).thenReturn(List.of(
                new SourceDto("dir://wakacje/a.jpg", "a.jpg", 0.9, null, "IMAGE"),
                new SourceDto("dir://other/x.jpg", "x.jpg", 0.85, null, "IMAGE")
        ));
        when(ingestionService.createGraphFactSourceDto(eq("dir://wakacje/a.jpg"), any(), anyDouble()))
                .thenReturn(new SourceDto("dir://wakacje/a.jpg", "a.jpg", 1.0, null, "GRAPH_FACT"));

        MessageResponse response = service.processChatMessage(chatId, new MessageRequest(plan.question()));

        assertTrue(response.sources().stream().allMatch(s -> "dir://wakacje/a.jpg".equals(s.path())));
        assertTrue(response.sources().stream().noneMatch(s -> "dir://other/x.jpg".equals(s.path())));
        verify(chatAiService).answer(eq(chatId), anyString());
    }

    @Test
    void graphSuccessStillUsesGraphSourcesWithoutForcedDenial() {
        QueryPlan plan = new QueryPlan("Co robi Igor?", List.of("Igor"), List.of(),
                "Co robi Igor?", "", false, false, QueryPlan.RetrievalMode.GRAPH,
                "Odpowiedz z grafu.");
        when(queryPlanner.plan(eq(plan.question()), anyString())).thenReturn(plan);
        when(graphQueryService.buildEvidence(anyList(), anyList(), any()))
                .thenReturn(new GraphEvidenceResult("- entity=Igor; file=dir://a.jpg", List.of("dir://a.jpg")));
        Result<String> result = Result.<String>builder().content("Igor je zupę.").build();
        when(chatAiService.answer(eq(chatId), anyString())).thenReturn(result);
        when(ingestionService.createGraphFactSourceDto(eq("dir://a.jpg"), any(), anyDouble()))
                .thenReturn(new SourceDto("dir://a.jpg", "a.jpg", 1.0, null, "GRAPH_FACT"));

        MessageResponse response = service.processChatMessage(chatId, new MessageRequest(plan.question()));

        assertEquals("Igor je zupę.", response.response());
        assertEquals(QueryPlan.RetrievalMode.GRAPH.name(), response.answerKind());
        assertEquals(1, response.sources().size());
        verify(ingestionService).createGraphFactSourceDto(eq("dir://a.jpg"), any(), anyDouble());
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(chatAiService).answer(eq(chatId), promptCaptor.capture());
        assertTrue(promptCaptor.getValue().contains("[Pełny graf wiedzy dla wskazanych zdjęć]"));
        assertTrue(promptCaptor.getValue().contains("Sam zdecyduj") || promptCaptor.getValue().contains("kompletny przepływ grafu"));
    }

    @Test
    void usesVisualEvidenceOnlyWhenThePlannerRequestsIt() {
        QueryPlan plan = new QueryPlan("Czy Michał ma blond włosy?", List.of("Michał"), "",
                "Michał ma blond włosy", true, false, QueryPlan.RetrievalMode.VISUAL_VALIDATION,
                "Odpowiedz z obrazu.");
        when(queryPlanner.plan(eq(plan.question()), anyString())).thenReturn(plan);
        VisualQueryMatch match = new VisualQueryMatch("dir://michal.jpg", BigDecimal.valueOf(0.91),
                List.of("blond włosy"), VisualMatchDecision.Decision.MATCH, List.of(),
                BigDecimal.valueOf(0.8), BigDecimal.valueOf(0.9),
                List.of(claim("Michał", "Michał ma blond włosy.", "dir://michal.jpg")));
        when(dynamicVisualMatcher.findEvidence(plan)).thenReturn(List.of(match));
        SourceDto source = new SourceDto("dir://michal.jpg", "michal.jpg", 0.8, null, "IMAGE");
        when(ingestionService.createSourceDto(anyString(), any(), anyDouble())).thenReturn(source);
        when(verifiedVisualAnswerService.answer(eq(plan.question()), eq(List.of(match)), anyList()))
                .thenReturn("Tak, Michał ma blond włosy.");

        MessageResponse response = service.processChatMessage(chatId, new MessageRequest(plan.question()));

        assertEquals("Tak, Michał ma blond włosy.", response.response());
        assertEquals(1, response.evidence().size());
        assertFalse(response.uncertain());
        verify(verifiedVisualAnswerService).answer(eq(plan.question()), eq(List.of(match)), anyList());
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

    @Test
    void explicitAtFileOverridesPlannerAndConversationScope() {
        String question = "Co przedstawia @selected.jpg?";
        QueryPlan plannerPlan = new QueryPlan(question, List.of(), List.of("dir://old.jpg"),
                question, question, true, false, QueryPlan.RetrievalMode.VISUAL_VALIDATION,
                EntityMatchMode.ANY, "");
        when(queryPlanner.plan(eq(question), anyString())).thenReturn(plannerPlan);
        when(graphQueryService.resolveExplicitFileScope(question)).thenReturn(List.of("dir://selected.jpg"));
        when(dynamicVisualMatcher.findEvidence(any(QueryPlan.class))).thenReturn(List.of());

        service.processChatMessage(chatId, new MessageRequest(question));

        ArgumentCaptor<QueryPlan> captor = ArgumentCaptor.forClass(QueryPlan.class);
        verify(dynamicVisualMatcher).findEvidence(captor.capture());
        assertEquals(List.of("dir://selected.jpg"), captor.getValue().fileScope());
    }

    @Test
    void unknownExplicitAtFileStopsWithoutRetrieval() {
        String question = "Co przedstawia @missing.jpg?";
        QueryPlan plan = QueryPlan.fallback(question, List.of());
        when(queryPlanner.plan(eq(question), anyString())).thenReturn(plan);
        when(graphQueryService.resolveExplicitFileScope(question)).thenReturn(List.of());
        when(graphQueryService.hasExplicitFileReference(question)).thenReturn(true);

        MessageResponse response = service.processChatMessage(chatId, new MessageRequest(question));

        assertEquals("NO_EVIDENCE", response.answerKind());
        assertTrue(response.sources().isEmpty());
        verifyNoInteractions(chatAiService, dynamicVisualMatcher);
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

    @Test
    void simpleHybridQuestionWithDocumentSourcesIsNotOverwrittenByNoEvidence() {
        // Criterion 1: useful hybrid evidence → short PL answer, not template denial.
        QueryPlan plan = new QueryPlan("Jakie jest saldo na fakturze?", List.of(), List.of(),
                "Jakie jest saldo na fakturze?", "", false, false, QueryPlan.RetrievalMode.HYBRID,
                "Odpowiedz z dokumentów.");
        when(queryPlanner.plan(eq(plan.question()), anyString())).thenReturn(plan);
        Result<String> result = Result.<String>builder().content("Saldo wynosi 1200 zł.").build();
        when(chatAiService.answer(eq(chatId), anyString())).thenReturn(result);
        SourceDto docSource = new SourceDto("dir://invoice.pdf", "invoice.pdf", 0.91, null, "PDF");
        when(ingestionService.getSources(result)).thenReturn(List.of(docSource));

        MessageResponse response = service.processChatMessage(chatId, new MessageRequest(plan.question()));

        assertEquals("Saldo wynosi 1200 zł.", response.response());
        assertNotEquals("NO_EVIDENCE", response.answerKind());
        assertFalse(response.response().contains("Nie znaleziono informacji w dokumentach."));
        assertFalse(response.response().contains("Nie znaleziono potwierdzonych dowodów wizualnych."));
        assertFalse(response.response().isBlank());
        assertEquals(1, response.sources().size());
        assertEquals("dir://invoice.pdf", response.sources().get(0).path());
        // Answer prompt must expose the bare question for memory/injector extraction.
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(chatAiService).answer(eq(chatId), promptCaptor.capture());
        assertTrue(promptCaptor.getValue().contains("Pytanie użytkownika: Jakie jest saldo na fakturze?"));
    }

    @Test
    void followUpPassesConversationHistoryWithSourcesToPlanner() {
        // Criterion 2: prior AI turn with SOURCES is in conversationContext for the planner.
        String followUp = "A co jest na tym zdjęciu?";
        ChatMessageEntity priorUser = ChatMessageEntity.builder()
                .chatId(chatId).role("USER").textContext("Pokaż Igora")
                .imagePaths(List.of()).scores(List.of())
                .createdAt(LocalDateTime.now().minusMinutes(2)).build();
        ChatMessageEntity priorAi = ChatMessageEntity.builder()
                .chatId(chatId).role("AI").textContext("Igor jest na plaży.")
                .imagePaths(List.of("dir://photos/igor.jpg")).scores(List.of(0.95))
                .createdAt(LocalDateTime.now().minusMinutes(1)).build();
        // After saveUserMessage, repository returns prior turns + current (order desc in real repo).
        when(chatMessageRepository.findTop6ByChatIdOrderByCreatedAtDesc(chatId))
                .thenReturn(List.of(
                        ChatMessageEntity.builder().chatId(chatId).role("USER").textContext(followUp)
                                .imagePaths(List.of()).scores(List.of())
                                .createdAt(LocalDateTime.now()).build(),
                        priorAi,
                        priorUser
                ));
        QueryPlan plan = new QueryPlan(followUp, List.of("Igor"), List.of("dir://photos/igor.jpg"),
                "co jest na zdjęciu igor dir://photos/igor.jpg", followUp, false, false,
                QueryPlan.RetrievalMode.HYBRID, EntityMatchMode.ANY, "Odpowiedz krótko.");
        when(queryPlanner.plan(eq(followUp), anyString())).thenReturn(plan);
        when(graphQueryService.buildEvidence(anyList(), anyList(), any()))
                .thenReturn(new GraphEvidenceResult("- entity=Igor", List.of("dir://photos/igor.jpg")));
        Result<String> result = Result.<String>builder().content("Igor stoi na plaży.").build();
        when(chatAiService.answer(eq(chatId), anyString())).thenReturn(result);
        when(ingestionService.createGraphFactSourceDto(eq("dir://photos/igor.jpg"), any(), anyDouble()))
                .thenReturn(new SourceDto("dir://photos/igor.jpg", "igor.jpg", 1.0, null, "GRAPH_FACT"));

        MessageResponse response = service.processChatMessage(chatId, new MessageRequest(followUp));

        ArgumentCaptor<String> contextCaptor = ArgumentCaptor.forClass(String.class);
        verify(queryPlanner).plan(eq(followUp), contextCaptor.capture());
        String context = contextCaptor.getValue();
        assertTrue(context.contains("USER:"));
        assertTrue(context.contains("AI:"));
        assertTrue(context.contains("SOURCES:"));
        assertTrue(context.contains("dir://photos/igor.jpg"));
        assertTrue(context.contains("Igor jest na plaży.") || context.contains("Pokaż Igora"));
        assertEquals("Igor stoi na plaży.", response.response());
        assertNotEquals("NO_EVIDENCE", response.answerKind());
    }

    @Test
    void emptyVisualWithFileScopeFallsBackToHybridAnswer() {
        // Criterion 1+5: visual miss with history fileScope must not hard-stop when docs/graph can answer.
        String question = "Opisz to zdjęcie";
        QueryPlan plan = new QueryPlan(question, List.of(), List.of("dir://photos/a.jpg"),
                question, question, true, false, QueryPlan.RetrievalMode.VISUAL_VALIDATION,
                EntityMatchMode.ANY, "Odpowiedz z obrazu lub dokumentów.");
        when(queryPlanner.plan(eq(question), anyString())).thenReturn(plan);
        when(dynamicVisualMatcher.findEvidence(any(QueryPlan.class))).thenReturn(List.of());
        Result<String> result = Result.<String>builder().content("Na zdjęciu widać morze.").build();
        when(chatAiService.answer(eq(chatId), anyString())).thenReturn(result);
        when(ingestionService.getSources(result)).thenReturn(List.of(
                new SourceDto("dir://photos/a.jpg", "a.jpg", 0.9, null, "IMAGE")));

        MessageResponse response = service.processChatMessage(chatId, new MessageRequest(question));

        assertEquals("Na zdjęciu widać morze.", response.response());
        assertNotEquals("NO_EVIDENCE", response.answerKind());
        assertFalse(response.response().contains("Nie znaleziono potwierdzonych dowodów wizualnych."));
        verify(chatAiService).answer(eq(chatId), anyString());
        verify(dynamicVisualMatcher).findEvidence(any(QueryPlan.class));
    }

    @Test
    void pureVisualMissWithoutScopeKeepsVisualDenial() {
        QueryPlan plan = new QueryPlan("Czy ktoś ma czerwoną czapkę?", List.of(), List.of(),
                "czerwona czapka", "czerwona czapka", true, false,
                QueryPlan.RetrievalMode.VISUAL_VALIDATION, "");
        when(queryPlanner.plan(eq(plan.question()), anyString())).thenReturn(plan);
        when(dynamicVisualMatcher.findEvidence(plan)).thenReturn(List.of());

        MessageResponse response = service.processChatMessage(chatId, new MessageRequest(plan.question()));

        assertEquals("Nie znaleziono potwierdzonych dowodów wizualnych.", response.response());
        assertEquals("NO_EVIDENCE", response.answerKind());
        verify(chatAiService, never()).answer(any(), anyString());
    }

    @Test
    void pureVisualMatchWithBlankAnswerKeepsSourcesAndShortPolishFallback() {
        // Criterion 1: MATCH is grounding — blank/free-form parse failure must not become NO_EVIDENCE
        // and must not drop MATCH source paths (skeptic gap).
        String question = "Czy Michał ma blond włosy?";
        QueryPlan plan = new QueryPlan(question, List.of(), List.of(),
                question, "blond włosy", true, false,
                QueryPlan.RetrievalMode.VISUAL_VALIDATION, "");
        when(queryPlanner.plan(eq(question), anyString())).thenReturn(plan);
        VisualQueryMatch match = new VisualQueryMatch("dir://michal.jpg", BigDecimal.valueOf(0.91),
                List.of("blond włosy"), VisualMatchDecision.Decision.MATCH, List.of(),
                BigDecimal.valueOf(0.8), BigDecimal.valueOf(0.9),
                List.of(claim("Michał", "Michał ma blond włosy.", "dir://michal.jpg")));
        when(dynamicVisualMatcher.findEvidence(plan)).thenReturn(List.of(match));
        when(ingestionService.createSourceDto(eq("dir://michal.jpg"), any(), anyDouble()))
                .thenReturn(new SourceDto("dir://michal.jpg", "michal.jpg", 0.8, null, "IMAGE"));
        // Simulate JSON/free-form parse failure on the real answer service path.
        when(verifiedVisualAnswerService.answer(eq(question), eq(List.of(match)), anyList())).thenReturn("");

        MessageResponse response = service.processChatMessage(chatId, new MessageRequest(question));

        assertNotEquals("NO_EVIDENCE", response.answerKind());
        assertEquals(QueryPlan.RetrievalMode.VISUAL_VALIDATION.name(), response.answerKind());
        assertFalse(response.response().isBlank());
        assertFalse(response.response().contains("Nie znaleziono potwierdzonych dowodów wizualnych."));
        assertFalse(response.response().contains("Nie znaleziono informacji w dokumentach."));
        assertEquals(VerifiedVisualAnswerService.MATCH_FALLBACK_ANSWER, response.response());
        assertEquals(1, response.sources().size());
        assertEquals("dir://michal.jpg", response.sources().get(0).path());
        assertEquals(1, response.evidence().size());
        verify(chatAiService, never()).answer(any(), anyString());
    }

    @Test
    void pureVisualMatchWithFreeFormAnswerKeepsMatchSources() {
        String question = "Co robi osoba na zdjęciu?";
        QueryPlan plan = new QueryPlan(question, List.of(), List.of(),
                question, question, true, false,
                QueryPlan.RetrievalMode.VISUAL_VALIDATION, "");
        when(queryPlanner.plan(eq(question), anyString())).thenReturn(plan);
        VisualQueryMatch match = new VisualQueryMatch("dir://action.jpg", BigDecimal.valueOf(0.88),
                List.of("osoba biegnie"), VisualMatchDecision.Decision.MATCH, List.of(),
                BigDecimal.valueOf(0.7), BigDecimal.valueOf(0.85),
                List.of(claim("osoba", "Osoba biegnie.", "dir://action.jpg")));
        when(dynamicVisualMatcher.findEvidence(plan)).thenReturn(List.of(match));
        when(ingestionService.createSourceDto(eq("dir://action.jpg"), any(), anyDouble()))
                .thenReturn(new SourceDto("dir://action.jpg", "action.jpg", 0.7, null, "IMAGE"));
        when(verifiedVisualAnswerService.answer(eq(question), eq(List.of(match)), anyList()))
                .thenReturn("Osoba biegnie po plaży.");

        MessageResponse response = service.processChatMessage(chatId, new MessageRequest(question));

        assertEquals("Osoba biegnie po plaży.", response.response());
        assertNotEquals("NO_EVIDENCE", response.answerKind());
        assertEquals(1, response.sources().size());
        assertEquals("dir://action.jpg", response.sources().get(0).path());
    }

    @Test
    void piotrekActionUsesAnchoredClaimAndKeepsFilenameOnlyInSources() {
        String path = "dir://awdaw/4C Matura-342.jpg";
        String question = "a co robi Piotrek na zdjęciu @4C Matura-342.jpg";
        QueryPlan plan = new QueryPlan(question, List.of("Piotrek"), List.of(path), question,
                question, true, false, QueryPlan.RetrievalMode.VISUAL_VALIDATION, "");
        when(queryPlanner.plan(eq(question), anyString())).thenReturn(plan);
        when(graphQueryService.resolveExplicitFileScope(question)).thenReturn(List.of(path));
        GroundedVisualClaim grounded = claim("Piotrek",
                "Piotrek stoi w szeregu i pozuje do zdjęcia, patrząc w stronę aparatu.", path);
        VisualQueryMatch match = new VisualQueryMatch(path, BigDecimal.valueOf(0.96), List.of(),
                VisualMatchDecision.Decision.MATCH, List.of(), BigDecimal.ONE, BigDecimal.ONE,
                List.of(grounded));
        when(dynamicVisualMatcher.findEvidence(any(QueryPlan.class))).thenReturn(List.of(match));
        when(ingestionService.createSourceDto(eq(path), any(), anyDouble()))
                .thenReturn(new SourceDto(path, "4C Matura-342.jpg", 1.0, null, "IMAGE"));
        when(graphQueryService.certainParticipantNamesForPaths(List.of(path))).thenReturn(List.of("Piotrek"));
        when(verifiedVisualAnswerService.answer(eq(question), anyList(), eq(List.of("Piotrek"))))
                .thenReturn(grounded.statementPl());

        MessageResponse response = service.processChatMessage(chatId, new MessageRequest(question));

        assertEquals(grounded.statementPl(), response.response());
        assertFalse(response.response().contains("4C Matura-342.jpg"));
        assertEquals(1, response.sources().size());
        assertEquals(path, response.sources().get(0).path());
        assertFalse(response.uncertain());
    }

    @Test
    void certainGraphParticipantsOverrideVisionCapabilityDenial() {
        // Empty entities + fileScope: who-is-on-photo → full certain co-presence roster.
        String question = "Pytanie o uczestników pliku w zakresie.";
        String path = "dir://awdaw/4C Matura-342.jpg";
        QueryPlan plan = new QueryPlan(question, List.of(), List.of(path),
                question, "", false, false, QueryPlan.RetrievalMode.HYBRID,
                "Odpowiedz z grafu.");
        when(queryPlanner.plan(eq(question), anyString())).thenReturn(plan);
        when(graphQueryService.buildEvidence(anyList(), anyList(), any()))
                .thenReturn(new GraphEvidenceResult(
                        "- entity=Igor; file=" + path + "\n- entity=Piotrek; file=" + path,
                        List.of(path)));
        when(graphQueryService.certainParticipantNamesForPaths(anyList()))
                .thenReturn(List.of("Igor", "Piotrek", "Bargiel", "Dawid", "Olek"));
        Result<String> result = Result.<String>builder()
                .content("Niestety, nie mogę zobaczyć zdjęć ani obrazów, więc nie jestem w stanie określić, kto jest na zdjęciu.")
                .build();
        when(chatAiService.answer(eq(chatId), anyString())).thenReturn(result);
        when(ingestionService.createGraphFactSourceDto(eq(path), any(), anyDouble()))
                .thenReturn(new SourceDto(path, "4C Matura-342.jpg", 1.0, null, "GRAPH_FACT"));

        MessageResponse response = service.processChatMessage(chatId, new MessageRequest(question));

        assertFalse(ChatAnswerGrounding.isCapabilityDenial(response.response()));
        assertTrue(response.response().contains("Igor"));
        assertTrue(response.response().contains("Piotrek"));
        assertTrue(response.response().contains("Bargiel"));
        assertTrue(response.response().contains("Dawid"));
        assertTrue(response.response().contains("Olek"));
        assertEquals(1, response.sources().size());
        assertEquals(path, response.sources().get(0).path());
        assertFalse(response.uncertain());
        assertNotEquals("NO_EVIDENCE", response.answerKind());
        verify(graphQueryService).certainParticipantNamesForPaths(anyList());
    }

    @Test
    void namedEntityDenialUsesEntityScopedPresenceNotFullRoster() {
        // Plan names one entity; recovery must not dump co-present people from other sources.
        String question = "Na których zdjęciach jest ta osoba?";
        String pathA = "dir://awdaw/a.jpg";
        String pathB = "dir://awdaw/b.jpg";
        QueryPlan plan = new QueryPlan(question, List.of("Anna"), List.of(),
                question, "", false, false, QueryPlan.RetrievalMode.HYBRID,
                "Odpowiedz z grafu.");
        when(queryPlanner.plan(eq(question), anyString())).thenReturn(plan);
        when(graphQueryService.buildEvidence(anyList(), anyList(), any()))
                .thenReturn(new GraphEvidenceResult(
                        "- entity=Anna; file=" + pathA + "\n- entity=Anna; file=" + pathB,
                        List.of(pathA, pathB)));
        // If the service wrongly used full-path roster, these names would appear.
        lenient().when(graphQueryService.certainParticipantNamesForPaths(anyList()))
                .thenReturn(List.of("Anna", "Igor", "Dawid", "Olek", "Zosia"));
        Result<String> result = Result.<String>builder()
                .content("Potrzebuję więcej informacji. Opisz, jak wygląda ta osoba, abym mógł pomóc.")
                .build();
        when(chatAiService.answer(eq(chatId), anyString())).thenReturn(result);
        when(ingestionService.createGraphFactSourceDto(eq(pathA), any(), anyDouble()))
                .thenReturn(new SourceDto(pathA, "a.jpg", 1.0, null, "GRAPH_FACT"));
        when(ingestionService.createGraphFactSourceDto(eq(pathB), any(), anyDouble()))
                .thenReturn(new SourceDto(pathB, "b.jpg", 1.0, null, "GRAPH_FACT"));

        MessageResponse response = service.processChatMessage(chatId, new MessageRequest(question));

        assertFalse(ChatAnswerGrounding.isCapabilityDenial(response.response()));
        assertTrue(response.response().contains("Anna"));
        assertTrue(response.response().contains("potwierdzonych zdjęciach"));
        assertFalse(response.response().contains("Igor"));
        assertFalse(response.response().contains("Dawid"));
        assertFalse(response.response().contains("Olek"));
        assertFalse(response.response().contains("Zosia"));
        assertFalse(response.response().contains(".jpg"));
        assertEquals(2, response.sources().size());
        assertNotEquals("NO_EVIDENCE", response.answerKind());
        // Entity-scoped path must not load multi-person roster from all source paths.
        verify(graphQueryService, never()).certainParticipantNamesForPaths(anyList());
    }

    @Test
    void namedEntityGeneralKnowledgeEssayIsGroundedToPresence() {
        String question = "Opisz osobę z grafu";
        String path = "dir://awdaw/x.jpg";
        QueryPlan plan = new QueryPlan(question, List.of("Anna"), List.of(),
                question, "", false, false, QueryPlan.RetrievalMode.HYBRID,
                "Odpowiedz z grafu.");
        when(queryPlanner.plan(eq(question), anyString())).thenReturn(plan);
        when(graphQueryService.buildEvidence(anyList(), anyList(), any()))
                .thenReturn(new GraphEvidenceResult("- entity=Anna; file=" + path, List.of(path)));
        // Single certain path → photo roster (not entity-only presence).
        when(graphQueryService.certainParticipantNamesForPaths(anyList()))
                .thenReturn(List.of("Anna"));
        Result<String> result = Result.<String>builder()
                .content("To imię męskie, które jest zdrobnieniem. Imię pochodzi z języka greckiego. "
                        + "W kulturze popularnej jest lubiane.")
                .build();
        when(chatAiService.answer(eq(chatId), anyString())).thenReturn(result);
        when(ingestionService.createGraphFactSourceDto(eq(path), any(), anyDouble()))
                .thenReturn(new SourceDto(path, "x.jpg", 1.0, null, "GRAPH_FACT"));

        MessageResponse response = service.processChatMessage(chatId, new MessageRequest(question));

        assertFalse(ChatAnswerGrounding.isGeneralKnowledgeEssay(response.response()));
        assertEquals("Na zdjęciu jest Anna.", response.response());
        assertEquals(1, response.sources().size());
    }

    @Test
    void englishGreetingNonAnswerWithSourcesIsReplacedByEntityPresence() {
        // Live failure mode: DeepInfra returned a generic greeting while hybrid sources were good.
        String question = "Na których zdjęciach jest ta osoba?";
        String path = "dir://awdaw/a.jpg";
        QueryPlan plan = new QueryPlan(question, List.of("Anna"), List.of(),
                question, "", false, false, QueryPlan.RetrievalMode.HYBRID,
                "Odpowiedz z grafu.");
        when(queryPlanner.plan(eq(question), anyString())).thenReturn(plan);
        when(graphQueryService.buildEvidence(anyList(), anyList(), any()))
                .thenReturn(new GraphEvidenceResult("- entity=Anna; file=" + path, List.of(path)));
        when(graphQueryService.certainParticipantNamesForPaths(anyList()))
                .thenReturn(List.of("Anna"));
        Result<String> result = Result.<String>builder()
                .content("Hello! How can I assist you today?")
                .build();
        when(chatAiService.answer(eq(chatId), anyString())).thenReturn(result);
        when(ingestionService.createGraphFactSourceDto(eq(path), any(), anyDouble()))
                .thenReturn(new SourceDto(path, "a.jpg", 1.0, null, "GRAPH_FACT"));

        MessageResponse response = service.processChatMessage(chatId, new MessageRequest(question));

        assertFalse(ChatAnswerGrounding.isEmptyOrGreetingNonAnswer(response.response()));
        assertEquals("Na zdjęciu jest Anna.", response.response());
        assertEquals(1, response.sources().size());
        assertEquals(path, response.sources().get(0).path());
    }

    @Test
    void speculativeOlekHypothesisListWithGoodSourcesIsRewrittenWithoutDroppingSources() {
        // Live failure: sources (GRAPH_FACT paths) correct, model invents activity menu.
        String question = "Co robi Olek na zdjęciu?";
        String path = "dir://photos/olek.jpg";
        QueryPlan plan = new QueryPlan(question, List.of("Olek"), List.of(),
                question, "", false, false, QueryPlan.RetrievalMode.GRAPH,
                "Odpowiedz z grafu i fragmentów.");
        when(queryPlanner.plan(eq(question), anyString())).thenReturn(plan);
        when(graphQueryService.buildEvidence(anyList(), anyList(), any()))
                .thenReturn(new GraphEvidenceResult(
                        "=== Zdjęcie 1 ===\nUczestnicy: Olek\nOlek: koszulka",
                        List.of(path)));
        when(graphQueryService.certainParticipantNamesForPaths(anyList()))
                .thenReturn(List.of("Olek"));
        String speculative = """
                Na zdjęciu Olek może robić różne rzeczy, w zależności od kontekstu. Może na przykład:

                1. Pozować – uśmiechać się, robić miny lub przybierać różne pozy.
                2. Wykonywać jakąś aktywność – np. grać w piłkę, jeździć na rowerze, czytać książkę.
                3. Przebywać w określonym miejscu – np. na plaży, w górach, na imprezie.
                4. Interagować z innymi – rozmawiać, śmiać się lub bawić się z przyjaciółmi.
                5. Być uchwycony w naturalnej chwili – np. podczas jedzenia, spaceru czy odpoczynku.

                Jeśli masz więcej szczegółów na temat zdjęcia, mogę spróbować bardziej precyzyjnie odpowiedzieć!
                """;
        Result<String> result = Result.<String>builder().content(speculative).build();
        when(chatAiService.answer(eq(chatId), anyString())).thenReturn(result);
        when(ingestionService.createGraphFactSourceDto(eq(path), any(), anyDouble()))
                .thenReturn(new SourceDto(path, "olek.jpg", 1.0, null, "GRAPH_FACT"));

        MessageResponse response = service.processChatMessage(chatId, new MessageRequest(question));

        assertEquals(ChatAnswerGrounding.GROUNDED_NO_DETAIL_FALLBACK, response.response());
        assertFalse(response.response().toLowerCase().contains("pozować"));
        assertFalse(response.response().toLowerCase().contains("może robić"));
        assertEquals(1, response.sources().size());
        assertEquals(path, response.sources().get(0).path());
        assertEquals("GRAPH_FACT", response.sources().get(0).type());
        assertEquals(QueryPlan.RetrievalMode.GRAPH.name(), response.answerKind());
    }

    @Test
    void graphClaimSelectAnswersWithoutFreeFormLlm() {
        String question = "Co trzyma Olek?";
        String path = "dir://witaj/20230424_145146.jpg";
        GroundedVisualClaim claim = new GroundedVisualClaim(
                "F-1", UUID.randomUUID(), "Olek", "trzyma nóż", "",
                "Olek trzyma nóż.", path, BigDecimal.valueOf(0.9), "VISION_STRUCTURED", "face_1");
        QueryPlan plan = new QueryPlan(question, List.of("Olek"), List.of(path),
                question, "", false, false, QueryPlan.RetrievalMode.GRAPH, "Odpowiedz z grafu.");
        when(queryPlanner.plan(eq(question), anyString())).thenReturn(plan);
        when(graphQueryService.buildEvidence(anyList(), anyList(), any()))
                .thenReturn(new GraphEvidenceResult(
                        "=== Zdjęcie 1 ===\nUczestnicy: Olek, Bartek",
                        List.of(path),
                        List.of(claim)));
        when(claimAnswerComposer.answerFromClaims(anyString(), anyList(), anyList()))
                .thenReturn(new ClaimAnswerComposer.ClaimAnswerResult(
                        "Olek trzyma nóż.", List.of("F-1"), List.of(path), true));
        when(ingestionService.createGraphFactSourceDto(eq(path), any(), anyDouble()))
                .thenReturn(new SourceDto(path, "20230424_145146.jpg", 1.0, null, "GRAPH_FACT"));

        MessageResponse response = service.processChatMessage(chatId, new MessageRequest(question));

        assertEquals("Olek trzyma nóż.", response.response());
        assertEquals(1, response.sources().size());
        assertEquals(path, response.sources().get(0).path());
        assertEquals(QueryPlan.RetrievalMode.GRAPH.name(), response.answerKind());
        verify(chatAiService, never()).answer(any(), anyString());
    }

    @Test
    void olekNozSafetyLectureIsRewrittenToPhotoRoster() {
        // Live: olek-noz — GRAPH had Olek+Bartek on the knife photo; model gave police lecture.
        String question = "kto jest na zdjęciu Olka z nożem";
        String path = "dir://witaj/20230424_145146.jpg";
        QueryPlan plan = new QueryPlan(question, List.of("Olek"), List.of(path),
                question, "", false, false, QueryPlan.RetrievalMode.GRAPH,
                "Odpowiedz z grafu.");
        when(queryPlanner.plan(eq(question), anyString())).thenReturn(plan);
        when(graphQueryService.buildEvidence(anyList(), anyList(), any()))
                .thenReturn(new GraphEvidenceResult(
                        "=== Zdjęcie 1 ===\nUczestnicy: Olek, Bartek\nOlek: trzyma nóż\nBartek: za kierownicą",
                        List.of(path)));
        when(graphQueryService.certainParticipantNamesForPaths(anyList()))
                .thenReturn(List.of("Olek", "Bartek"));
        String safety = """
                Jeśli masz na myśli sytuację, w której ktoś o imieniu Olek używa noża, ważne jest, aby zachować ostrożność i odpowiedzialność. Używanie noża może być niebezpieczne zarówno dla osoby posługującej się nim, jak i dla innych. Jeśli jest to sytuacja fikcyjna lub związana z jakimś projektem artystycznym, upewnij się, że jest ona przedstawiona w sposób odpowiedzialny i bezpieczny.

                Jeśli natomiast jest to sytuacja realna i istnieje zagrożenie dla czyjegoś bezpieczeństwa, należy natychmiast skontaktować się z odpowiednimi służbami, takimi jak policja, aby zapewnić pomoc i interwencję.

                Jeśli masz konkretne pytanie lub potrzebujesz porady w związku z tą sytuacją, daj mi znać, a postaram się pomóc.
                """;
        Result<String> result = Result.<String>builder().content(safety).build();
        when(chatAiService.answer(eq(chatId), anyString())).thenReturn(result);
        when(ingestionService.createGraphFactSourceDto(eq(path), any(), anyDouble()))
                .thenReturn(new SourceDto(path, "20230424_145146.jpg", 1.0, null, "GRAPH_FACT"));

        MessageResponse response = service.processChatMessage(chatId, new MessageRequest(question));

        assertEquals("Na zdjęciu są Olek i Bartek.", response.response());
        assertFalse(response.response().toLowerCase().contains("policj"));
        assertEquals(1, response.sources().size());
        assertEquals(path, response.sources().get(0).path());
        assertEquals(QueryPlan.RetrievalMode.GRAPH.name(), response.answerKind());
        verify(graphQueryService).certainParticipantNamesForPaths(anyList());
    }

    @Test
    void noCertainGraphOrSourcesStillUsesNoEvidenceDenial() {
        QueryPlan plan = new QueryPlan("Pytanie bez dowodów", List.of(), List.of(),
                "Pytanie bez dowodów", "", false, false, QueryPlan.RetrievalMode.HYBRID,
                "Odpowiedz z dowodów.");
        when(queryPlanner.plan(eq(plan.question()), anyString())).thenReturn(plan);
        Result<String> result = Result.<String>builder()
                .content("Nie mogę zobaczyć zdjęć.")
                .build();
        when(chatAiService.answer(eq(chatId), anyString())).thenReturn(result);
        when(ingestionService.getSources(result)).thenReturn(List.of());

        MessageResponse response = service.processChatMessage(chatId, new MessageRequest(plan.question()));

        assertEquals("Nie znaleziono informacji w dokumentach.", response.response());
        assertEquals("NO_EVIDENCE", response.answerKind());
        assertTrue(response.sources().isEmpty());
        assertTrue(response.uncertain());
        verify(graphQueryService, never()).buildEvidence(anyList(), anyList(), any());
        // Roster path must not invent participants without certain evidence.
        verify(graphQueryService, never()).certainParticipantNamesForPaths(anyList());
    }

    @Test
    void strippingFilenameDoesNotLeaveEmptyQuoteShellsInAnswer() {
        String path = "dir://awdaw/4C Matura-342.jpg";
        String fileName = "4C Matura-342.jpg";
        QueryPlan plan = new QueryPlan("Opisz kontekst", List.of(), List.of(path),
                "Opisz kontekst", "", false, false, QueryPlan.RetrievalMode.HYBRID,
                "Odpowiedz krótko.");
        when(queryPlanner.plan(eq(plan.question()), anyString())).thenReturn(plan);
        when(graphQueryService.buildEvidence(anyList(), anyList(), any()))
                .thenReturn(new GraphEvidenceResult("- entity=Igor; file=" + path, List.of(path)));
        when(graphQueryService.certainParticipantNamesForPaths(anyList()))
                .thenReturn(List.of("Igor"));
        // Non-denial answer that embeds the filename in quotes (post-strip must clean shells).
        Result<String> result = Result.<String>builder()
                .content("Na zdjęciu widać grupę osób, takich jak „" + fileName + "”.")
                .build();
        when(chatAiService.answer(eq(chatId), anyString())).thenReturn(result);
        when(ingestionService.createGraphFactSourceDto(eq(path), any(), anyDouble()))
                .thenReturn(new SourceDto(path, fileName, 1.0, null, "GRAPH_FACT"));

        MessageResponse response = service.processChatMessage(chatId, new MessageRequest(plan.question()));

        assertFalse(response.response().contains(fileName));
        assertFalse(response.response().contains("„”"));
        assertFalse(response.response().contains("\"\""));
        assertFalse(response.response().contains("«»"));
        assertFalse(response.response().toLowerCase().contains("takich jak"));
    }

    @Test
    void visualMatchDenialWithCertainNamesIsReplacedByRoster() {
        String path = "dir://awdaw/4C Matura-342.jpg";
        String question = "Warunek wizualny z zakresem pliku";
        QueryPlan plan = new QueryPlan(question, List.of(), List.of(path),
                question, "warunek", true, false, QueryPlan.RetrievalMode.VISUAL_VALIDATION,
                "Odpowiedz z obrazu.");
        when(queryPlanner.plan(eq(question), anyString())).thenReturn(plan);
        VisualQueryMatch match = new VisualQueryMatch(path, BigDecimal.valueOf(0.9),
                List.of("scena potwierdzona"), VisualMatchDecision.Decision.MATCH, List.of(),
                BigDecimal.ONE, BigDecimal.valueOf(0.9),
                List.of(claim("", "Scena jest potwierdzona.", path)));
        when(dynamicVisualMatcher.findEvidence(any(QueryPlan.class))).thenReturn(List.of(match));
        when(ingestionService.createSourceDto(eq(path), any(), anyDouble()))
                .thenReturn(new SourceDto(path, "4C Matura-342.jpg", 1.0, null, "IMAGE"));
        when(graphQueryService.certainParticipantNamesForPaths(anyList()))
                .thenReturn(List.of("Igor", "Dawid"));
        when(verifiedVisualAnswerService.answer(eq(plan.question()), eq(List.of(match)), anyList()))
                .thenReturn("Nie mam dostępu do konkretnych plików, zdjęć ani obrazów.");

        MessageResponse response = service.processChatMessage(chatId, new MessageRequest(plan.question()));

        assertFalse(ChatAnswerGrounding.isCapabilityDenial(response.response()));
        assertTrue(response.response().contains("Igor"));
        assertTrue(response.response().contains("Dawid"));
        assertEquals(1, response.sources().size());
        assertEquals(QueryPlan.RetrievalMode.VISUAL_VALIDATION.name(), response.answerKind());
    }

    @Test
    void shortFollowUpWithEnglishClarifyIsRewrittenToEntityPresence() {
        // Regression: test-kon-123 — "a olek?" with good hybrid/graph sources but EN clarify prose.
        String question = "a olek?";
        String path = "dir://awdaw/4C Matura-342.jpg";
        QueryPlan plan = new QueryPlan(question, List.of("Olek"), List.of(),
                "zdjęcia na których jest Olek", "Olek na potwierdzonych zdjęciach",
                false, false, QueryPlan.RetrievalMode.HYBRID, "Odpowiedz krótko po polsku.");
        when(queryPlanner.plan(eq(question), anyString())).thenReturn(plan);
        when(graphQueryService.buildEvidence(anyList(), anyList(), any()))
                .thenReturn(new GraphEvidenceResult("- entity=Olek; file=" + path, List.of(path)));
        when(graphQueryService.certainParticipantNamesForPaths(anyList()))
                .thenReturn(List.of("Olek"));
        Result<String> result = Result.<String>builder()
                .content("It seems like you might be saying \"a olek,\" but I'm not entirely sure what you mean. "
                        + "Could you clarify or provide more context? Let me know so I can assist you better! 😊")
                .build();
        when(chatAiService.answer(eq(chatId), anyString())).thenReturn(result);
        when(ingestionService.getSources(result))
                .thenReturn(List.of(new SourceDto(path, "4C Matura-342.jpg", 0.9, null, "IMAGE")));
        when(ingestionService.createGraphFactSourceDto(eq(path), any(), anyDouble()))
                .thenReturn(new SourceDto(path, "4C Matura-342.jpg", 1.0, null, "GRAPH_FACT"));

        MessageResponse response = service.processChatMessage(chatId, new MessageRequest(question));

        // Single certain path → photo roster grounding (not multi-file "presence in library").
        assertEquals("Na zdjęciu jest Olek.", response.response());
        assertEquals(1, response.sources().size());
        assertEquals("GRAPH_FACT", response.sources().get(0).type());
        assertFalse(response.uncertain());
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(chatAiService).answer(eq(chatId), promptCaptor.capture());
        assertTrue(promptCaptor.getValue().contains("zdjęcia na których jest Olek"));
        assertTrue(promptCaptor.getValue().contains("Oryginalne brzmienie użytkownika: a olek?"));
        verify(chatMemoryService).replaceLastAiMessage(eq(chatId),
                eq("Na zdjęciu jest Olek."));
    }

    @Test
    void answerFacingQuestionPrefersStandaloneRetrievalQuery() {
        QueryPlan plan = new QueryPlan("a olek?", List.of("Olek"), List.of(),
                "zdjęcia na których jest Olek", "condition", false, false,
                QueryPlan.RetrievalMode.HYBRID, "");
        assertEquals("zdjęcia na których jest Olek", ChatInteractionService.answerFacingQuestion(plan));

        QueryPlan same = new QueryPlan("co robi?", List.of(), List.of(),
                "co robi?", "co robi osoba na zdjęciu", false, false,
                QueryPlan.RetrievalMode.HYBRID, "");
        assertEquals("co robi osoba na zdjęciu", ChatInteractionService.answerFacingQuestion(same));
    }

    @Test
    void graphFactPreferredOverHybridForSamePath() {
        String path = "dir://awdaw/shared.jpg";
        QueryPlan plan = new QueryPlan("Gdzie jest Olek?", List.of("Olek"), List.of(),
                "Gdzie jest Olek?", "", false, false, QueryPlan.RetrievalMode.HYBRID, "");
        when(queryPlanner.plan(eq(plan.question()), anyString())).thenReturn(plan);
        when(graphQueryService.buildEvidence(anyList(), anyList(), any()))
                .thenReturn(new GraphEvidenceResult("- entity=Olek; file=" + path, List.of(path)));
        Result<String> result = Result.<String>builder().content("Olek jest na potwierdzonych zdjęciach.").build();
        when(chatAiService.answer(eq(chatId), anyString())).thenReturn(result);
        when(ingestionService.getSources(result))
                .thenReturn(List.of(new SourceDto(path, "shared.jpg", 0.75, null, "IMAGE")));
        when(ingestionService.createGraphFactSourceDto(eq(path), any(), anyDouble()))
                .thenReturn(new SourceDto(path, "shared.jpg", 1.0, null, "GRAPH_FACT"));

        MessageResponse response = service.processChatMessage(chatId, new MessageRequest(plan.question()));

        assertEquals(1, response.sources().size());
        assertEquals("GRAPH_FACT", response.sources().get(0).type());
        assertEquals(1.0, response.sources().get(0).score());
        assertEquals("CONFIRMED", response.evidence().get(0).matchStatus());
    }

    private static QueryPlan coPresencePlan(String question, List<String> entities,
                                            QueryPlan.RetrievalMode mode) {
        return new QueryPlan(question, entities, List.of(), question, question, false, false,
                mode, EntityMatchMode.ALL_SAME_FILE,
                "Jedno krótkie zdanie po polsku; nie wymyślaj wspólnego zdjęcia bez dowodu.");
    }

    private static GroundedVisualClaim claim(String entity, String statement, String path) {
        return new GroundedVisualClaim("T-" + UUID.randomUUID(), UUID.randomUUID(), entity,
                "opisuje", "", statement, path, BigDecimal.valueOf(0.9),
                "PIXEL_VERIFICATION", "face_1");
    }
}

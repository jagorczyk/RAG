package com.rag.rag.chat.service;

import com.rag.rag.chat.dto.MessageRequest;
import com.rag.rag.chat.dto.MessageResponse;
import com.rag.rag.chat.dto.QueryEvidenceDto;
import com.rag.rag.chat.entity.ChatMessageEntity;
import com.rag.rag.chat.repository.ChatMemoryRepository;
import com.rag.rag.chat.repository.ChatMessageRepository;
import com.rag.rag.core.retrieval.RetrievalPathScope;
import com.rag.rag.core.retrieval.RetrievalQueryContext;
import com.rag.rag.ingestion.dto.SourceDto;
import com.rag.rag.ingestion.service.IngestionService;
import com.rag.rag.knowledge.graph.GraphQueryService;
import com.rag.rag.knowledge.graph.GraphEvidenceResult;
import com.rag.rag.knowledge.query.DynamicVisualMatcher;
import com.rag.rag.knowledge.query.VisualMatchDecision;
import com.rag.rag.knowledge.query.VisualQueryMatch;
import dev.langchain4j.service.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Comparator;
import java.util.regex.Pattern;

/**
 * Executes QueryPlanner output under AGENTS.md:
 * VISUAL_VALIDATION → claim answers; GRAPH (people) / HYBRID → AI from evidence.
 * No phrase-based question routing in this class.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatInteractionService {

    private static final Pattern DIR_PATH = Pattern.compile("dir://\\S+");
    private static final Pattern AT_FILENAME = Pattern.compile("@[\\w.\\-]+");

    private final ChatService chatAiService;
    private final ChatMemoryRepository chatMemoryRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatMemoryService chatMemoryService;
    private final IngestionService ingestionService;
    private final GraphQueryService graphQueryService;
    private final DynamicVisualMatcher dynamicVisualMatcher;
    private final QueryPlanner queryPlanner;
    private final VerifiedVisualAnswerService verifiedVisualAnswerService;
    private final ClaimAnswerComposer claimAnswerComposer;

    /** Cap sources shown in UI / stored on the AI turn (GRAPH can otherwise dump all entity photos). */
    @Value("${rag.answer.max-sources:3}")
    private int maxSources = 3;

    @Transactional
    public MessageResponse processChatMessage(UUID chatId, MessageRequest messageRequest) {
        String question = messageRequest.message() == null ? "" : messageRequest.message().trim();
        saveUserMessage(chatId, question);
        chatMemoryRepository.findById(chatId).ifPresent(chat -> {
            chat.setLastMessageAt(LocalDateTime.now());
            chatMemoryRepository.save(chat);
        });

        String conversationContext = chatMessageRepository.findTop6ByChatIdOrderByCreatedAtDesc(chatId).stream()
                .sorted(Comparator.comparing(ChatMessageEntity::getCreatedAt))
                .map(message -> message.getRole() + ": " + message.getTextContext()
                        + "\nSOURCES: " + (message.getImagePaths() == null ? List.of() : message.getImagePaths()))
                .reduce("", (left, right) -> left + "\n" + right);
        QueryPlan plan = queryPlanner == null
                ? QueryPlan.fallback(question, List.of())
                : queryPlanner.plan(question, conversationContext);
        List<String> explicitFileScope = graphQueryService.resolveExplicitFileScope(question);
        if (!explicitFileScope.isEmpty()) {
            plan = plan.withFileScope(explicitFileScope);
        } else if (graphQueryService.hasExplicitFileReference(question)) {
            String denial = "Nie znaleziono wskazanego pliku w Twojej bibliotece.";
            saveAiMessage(chatId, denial, List.of(), List.of(), "NO_EVIDENCE", true);
            return new MessageResponse(denial, List.of(), true, List.of(), "NO_EVIDENCE");
        }
        log.info("Dynamic query plan: mode={}, visual={}, entities={}, ambiguous={}",
                plan.retrievalMode(), plan.visualCondition(), plan.entities(), plan.ambiguous());

        if (plan.visualCondition() && dynamicVisualMatcher != null) {
            // Persist hard visual denial only when text/graph fallback is not allowed.
            boolean allowTextFallback = ChatRetrievalPolicy.shouldFallbackFromEmptyVisual(plan);
            MessageResponse visual = processVisualPlan(chatId, plan, !allowTextFallback);
            if (!"NO_EVIDENCE".equals(visual.answerKind())) {
                return visual;
            }
            if (!allowTextFallback) {
                return visual;
            }
            log.info("Visual path empty; falling back to text/graph for mode={}, fileScope={}, entities={}",
                    plan.retrievalMode(), plan.fileScope(), plan.entities());
        }
        return processTextOrGraphPlan(chatId, plan, conversationContext);
    }

    private MessageResponse processTextOrGraphPlan(UUID chatId, QueryPlan plan, String conversationContext) {
        GraphEvidenceResult graphEvidence = ChatRetrievalPolicy.needsGraphEvidence(plan)
                ? graphQueryService.buildEvidence(plan.entities(), plan.fileScope(), plan.entityMatchMode())
                : new GraphEvidenceResult("", List.of());
        // Co-presence is a set operation on planner output: when ALL_SAME_FILE has no
        // intersection evidence, never let the LLM or hybrid retrieval affirm a joint photo.
        if (ChatRetrievalPolicy.mustDenyJointFile(plan, graphEvidence)) {
            String denial = "Nie znaleziono potwierdzonego wspólnego zdjęcia tych osób.";
            saveAiMessage(chatId, denial, List.of(), List.of(), plan.retrievalMode().name(), true);
            return new MessageResponse(denial, List.of(), true, List.of(), plan.retrievalMode().name());
        }

        boolean graphMissFallback = ChatRetrievalPolicy.shouldFallbackFromGraph(plan, graphEvidence);
        QueryPlan.RetrievalMode effectiveMode = ChatRetrievalPolicy.effectiveRetrievalMode(plan, graphEvidence);

        // Evidence-first GRAPH: select immutable claims (same as visual) before free-form LLM.
        if (!graphMissFallback
                && effectiveMode == QueryPlan.RetrievalMode.GRAPH
                && claimAnswerComposer != null
                && graphEvidence.claims() != null
                && !graphEvidence.claims().isEmpty()) {
            MessageResponse claimResponse = tryClaimGraphAnswer(chatId, plan, graphEvidence, effectiveMode);
            if (claimResponse != null) {
                return claimResponse;
            }
        }

        String graphContext = graphEvidence.context();
        String questionForModel = answerFacingQuestion(plan);
        String originalQuestion = plan.question() == null ? "" : plan.question().trim();
        String recentTurns = compactConversationContext(conversationContext);
        String answerStyle = """
                Odpowiedź po polsku: konkretna, naturalna, zwykle 1–3 zdania.
                Wykorzystaj z kontekstu wszystko, o co pyta użytkownik: ubiór, kolory, włosy,
                wygląd, czynności, relacje przestrzenne, scenę — bez zgadywania poza dowodami.
                Bez list hipotez („może na przykład… 1. 2. 3.”) i bez prośby o więcej szczegółów.
                Bez pewności, score i listy plików.
                """;

        String prompt;
        if (graphMissFallback) {
            // GRAPH without certain people evidence — hybrid only; refuse if retrieval empty.
            prompt = """
                    [Instrukcja odpowiedzi]
                    %s
                    Graf wiedzy nie ma pewnych dowodów o osobach dla tego pytania; użyj wyłącznie kontekstu dokumentów z retrieval.
                    Gdy brak fragmentów dokumentów, odpowiedz dokładnie: Nie znaleziono informacji w dokumentach.
                    %s
                    %s
                    Oryginalne brzmienie użytkownika: %s

                    Pytanie użytkownika: %s
                    """.formatted(plan.answerInstruction(), answerStyle, recentTurns, originalQuestion, questionForModel);
        } else if (effectiveMode == QueryPlan.RetrievalMode.GRAPH && !graphContext.isBlank()) {
            // Full graph snapshot for relevant photos — model selects facts and answers.
            prompt = """
                    [Pełny graf wiedzy dla wskazanych zdjęć]
                    %s

                    [Instrukcja odpowiedzi]
                    %s
                    %s
                    Powyżej masz kompletny przepływ grafu (uczestnicy, visual_cues, obiekty, relacje,
                    fakty, scena, claimy). Sam zdecyduj, które elementy odpowiadają na pytanie,
                    i sformułuj naturalną odpowiedź po polsku. Nie zgaduj poza tym grafem.
                    Fragmenty retrieval (jeśli są) traktuj jako uzupełnienie tych samych plików.
                    %s
                    Oryginalne brzmienie użytkownika: %s

                    Pytanie użytkownika: %s
                    """.formatted(graphContext, plan.answerInstruction(), answerStyle, recentTurns, originalQuestion, questionForModel);
        } else if (graphContext.isBlank()) {
            prompt = """
                    [Instrukcja odpowiedzi]
                    %s
                    %s
                    Używaj wyłącznie fragmentów dokumentów z retrieval — nie zgaduj.
                    %s
                    Oryginalne brzmienie użytkownika: %s

                    Pytanie użytkownika: %s
                    """.formatted(plan.answerInstruction(), answerStyle, recentTurns, originalQuestion, questionForModel);
        } else {
            prompt = """
                    [Kontekst zweryfikowany]
                    %s

                    [Instrukcja odpowiedzi]
                    %s
                    %s
                    Używaj wyłącznie powyższego kontekstu i fragmentów dokumentów z retrieval — nie zgaduj.
                    %s
                    Oryginalne brzmienie użytkownika: %s

                    Pytanie użytkownika: %s
                    """.formatted(graphContext, plan.answerInstruction(), answerStyle, recentTurns, originalQuestion, questionForModel);
        }

        // Named people / fileScope: restrict hybrid to proven files. Keep scoped hybrid on GRAPH
        // so appearance/clothing details from embeddings can answer open questions.
        List<String> retrievalScope = ChatRetrievalPolicy.retrievalScope(plan, graphEvidence);
        RetrievalPathScope.set(retrievalScope);
        RetrievalQueryContext.set(plan.retrievalQuery());
        RetrievalQueryContext.setDisabled(false);
        Result<String> result;
        try {
            result = chatAiService.answer(chatId, answerPrompt(prompt));
        } finally {
            RetrievalPathScope.clear();
            RetrievalQueryContext.clear();
        }

        String answer = result == null ? "" : result.content();
        if (answer == null || answer.isBlank()) {
            answer = "Nie udało się przygotować odpowiedzi na podstawie dostępnych danych.";
        }
        List<SourceDto> sources = mergeSources(result, plan, graphEvidence, effectiveMode);
        // Grounding uses post-filter sources (certain graph / allowed hybrid), not raw retrieval hits.
        boolean noGrounding = ChatRetrievalPolicy.lacksGrounding(graphEvidence, !sources.isEmpty());
        if (noGrounding) {
            answer = "Nie znaleziono informacji w dokumentach.";
            sources = List.of();
        } else {
            // GRAPH/HYBRID: keep AI prose; rewrite only capability refusals / ungrounded essays.
            // When the turn is scoped to specific photo(s) (fileScope or single certain path),
            // recovery uses the full participant roster so "kto jest na zdjęciu Olka" includes co-present people.
            boolean preferPhotoRoster = (plan.fileScope() != null && !plan.fileScope().isEmpty())
                    || (graphEvidence.certainPaths() != null && graphEvidence.certainPaths().size() == 1);
            boolean entityScoped = ChatRetrievalPolicy.hasNamedEntities(plan) && !preferPhotoRoster;
            List<String> recoveryNames;
            if (entityScoped) {
                recoveryNames = plan.entities();
            } else {
                List<String> paths = rosterPaths(plan, graphEvidence, sources);
                recoveryNames = graphQueryService.certainParticipantNamesForPaths(paths);
            }
            boolean hasCertain = graphEvidence.hasEvidence() || !sources.isEmpty();
            answer = ChatAnswerGrounding.resolveGroundedAnswer(
                    answer, recoveryNames, hasCertain, entityScoped);
        }
        String cleaned = removeTechnicalReferences(answer, sources);
        syncGroundedAnswerToMemory(chatId, cleaned);
        List<QueryEvidenceDto> sourceEvidence = sources.stream()
                .map(source -> new QueryEvidenceDto(source.path(),
                        java.math.BigDecimal.valueOf(source.score() == null ? 0.0 : source.score()),
                        List.of("GRAPH_FACT".equals(source.type())
                                ? "Potwierdzony fakt z grafu wiedzy."
                                : "Fragment wybrany przez wyszukiwanie hybrydowe."),
                        "GRAPH_FACT".equals(source.type()) ? "CONFIRMED" : "RETRIEVED"))
                .toList();
        boolean uncertain = plan.ambiguous()
                || graphMissFallback
                || noGrounding
                || sources.isEmpty();
        String answerKind = noGrounding ? "NO_EVIDENCE" : effectiveMode.name();
        saveAiMessage(chatId, cleaned, sources, sourceEvidence, answerKind, uncertain);
        return new MessageResponse(cleaned, sources, uncertain, sourceEvidence, answerKind);
    }

    /**
     * GRAPH claim path: LLM only picks claim IDs; answer prose is immutable statementPl.
     * @return null when no grounded claim prose — caller continues with free-form LLM
     */
    private MessageResponse tryClaimGraphAnswer(
            UUID chatId, QueryPlan plan, GraphEvidenceResult graphEvidence,
            QueryPlan.RetrievalMode effectiveMode) {
        String questionForModel = answerFacingQuestion(plan);
        ClaimAnswerComposer.ClaimAnswerResult claimResult = claimAnswerComposer.answerFromClaims(
                questionForModel, graphEvidence.claims(), plan.entities());
        if (!claimResult.hasGroundedProse()) {
            // Free-form LLM + grounding remains the fallback (PR1 safety/roster detectors apply).
            return null;
        }
        List<String> preferredPaths = !claimResult.usedFilePaths().isEmpty()
                ? claimResult.usedFilePaths()
                : graphEvidence.certainPaths();
        return finishGroundedGraphAnswer(chatId, plan, graphEvidence, effectiveMode,
                claimResult.answer(), preferredPaths);
    }

    private MessageResponse finishGroundedGraphAnswer(
            UUID chatId, QueryPlan plan, GraphEvidenceResult graphEvidence,
            QueryPlan.RetrievalMode effectiveMode, String answer, List<String> preferredPaths) {
        Map<String, SourceDto> unique = new LinkedHashMap<>();
        List<String> fileScope = plan.fileScope();
        List<String> paths = preferredPaths == null ? List.of() : preferredPaths;
        for (String path : paths) {
            if (path == null || path.isBlank()) {
                continue;
            }
            if (!RetrievalPathScope.pathInScope(path, fileScope)) {
                continue;
            }
            putSource(unique, ingestionService.createGraphFactSourceDto(path, fileName(path), 1.0), true);
        }
        // Fall back to all certain graph paths if preferred set was empty/filtered out.
        if (unique.isEmpty()) {
            for (String path : graphEvidence.certainPaths()) {
                if (RetrievalPathScope.pathInScope(path, fileScope)) {
                    putSource(unique, ingestionService.createGraphFactSourceDto(path, fileName(path), 1.0), true);
                }
            }
        }
        List<SourceDto> sources = limitSources(List.copyOf(unique.values()));
        String cleaned = removeTechnicalReferences(answer, sources);
        if (cleaned == null || cleaned.isBlank()) {
            cleaned = ClaimAnswerComposer.EMPTY_FALLBACK;
        }
        syncGroundedAnswerToMemory(chatId, cleaned);
        List<QueryEvidenceDto> sourceEvidence = sources.stream()
                .map(source -> new QueryEvidenceDto(source.path(),
                        java.math.BigDecimal.valueOf(source.score() == null ? 0.0 : source.score()),
                        List.of("Potwierdzony fakt z grafu wiedzy."),
                        "CONFIRMED"))
                .toList();
        boolean uncertain = plan.ambiguous() || sources.isEmpty();
        String answerKind = sources.isEmpty() ? "NO_EVIDENCE" : effectiveMode.name();
        saveAiMessage(chatId, cleaned, sources, sourceEvidence, answerKind, uncertain);
        return new MessageResponse(cleaned, sources, uncertain, sourceEvidence, answerKind);
    }

    /**
     * @param persistDenial when false and matches are empty, returns NO_EVIDENCE without writing
     *                      an AI turn so the caller can fall back to text/graph on the same plan.
     */
    private MessageResponse processVisualPlan(UUID chatId, QueryPlan plan, boolean persistDenial) {
        List<VisualQueryMatch> matches = dynamicVisualMatcher.findEvidence(plan).stream()
                .filter(match -> match != null && match.claims() != null && !match.claims().isEmpty())
                .toList();
        // Principle 2: only MATCH evidence becomes a source (matcher already filters).
        List<QueryEvidenceDto> evidence = matches.stream()
                .map(match -> new QueryEvidenceDto(match.filePath(), match.confidence(), match.reasons(),
                        match.decision().name(), match.retrievalScore(), match.entityConfidence(), match.confidence()))
                .toList();
        List<SourceDto> visualSources = matches.stream()
                .filter(match -> match.decision() == VisualMatchDecision.Decision.MATCH)
                .map(match -> ingestionService.createSourceDto(match.filePath(), null,
                        match.retrievalScore() == null ? match.confidence().doubleValue() : match.retrievalScore().doubleValue()))
                .toList();
        if (matches.isEmpty()) {
            String answer = "Nie znaleziono potwierdzonych dowodów wizualnych.";
            if (persistDenial) {
                saveAiMessage(chatId, answer, List.of(), List.of(), "NO_EVIDENCE", true);
            }
            return new MessageResponse(answer, List.of(), true, List.of(), "NO_EVIDENCE");
        }
        boolean uncertain = plan.ambiguous() || matches.stream()
                .anyMatch(match -> match.decision() == VisualMatchDecision.Decision.UNCERTAIN);
        // Answer prose stays short; detailed reasons stay only in evidence/sources UI.
        // MATCH evidence is already grounding (criterion 1) — never drop sources or force
        // NO_EVIDENCE because the answer model returned blank / non-JSON prose.
        List<String> matchPaths = visualSources.stream()
                .map(SourceDto::path)
                .filter(path -> path != null && !path.isBlank())
                .toList();
        List<String> certainNames = matchPaths.isEmpty()
                ? List.of()
                : graphQueryService.certainParticipantNamesForPaths(matchPaths);
        String answer = verifiedVisualAnswerService.answer(plan.question(), matches, certainNames);
        if (VerifiedVisualAnswerService.MATCH_FALLBACK_ANSWER.equals(answer)
                || VerifiedVisualAnswerService.NO_VISUAL_EVIDENCE.equals(answer)) {
            String denial = "Nie mam potwierdzonej informacji, która odpowiada na to pytanie.";
            if (persistDenial) {
                saveAiMessage(chatId, denial, List.of(), List.of(), "NO_EVIDENCE", true);
            }
            return new MessageResponse(denial, List.of(), true, List.of(), "NO_EVIDENCE");
        }
        if (answer == null || answer.isBlank()) {
            answer = !certainNames.isEmpty()
                    ? ChatAnswerGrounding.formatParticipantRoster(certainNames)
                    : VerifiedVisualAnswerService.MATCH_FALLBACK_ANSWER;
            uncertain = true;
        }
        if (!visualSources.isEmpty()) {
            boolean entityScoped = ChatRetrievalPolicy.hasNamedEntities(plan);
            List<String> recoveryNames = entityScoped && plan.entities() != null
                    ? plan.entities()
                    : certainNames;
            answer = ChatAnswerGrounding.resolveGroundedAnswer(
                    answer, recoveryNames, true, entityScoped);
        }
        String cleaned = removeTechnicalReferences(answer, visualSources);
        if (cleaned == null || cleaned.isBlank()) {
            cleaned = VerifiedVisualAnswerService.MATCH_FALLBACK_ANSWER;
            uncertain = true;
        }
        syncGroundedAnswerToMemory(chatId, cleaned);
        saveAiMessage(chatId, cleaned, visualSources, evidence, QueryPlan.RetrievalMode.VISUAL_VALIDATION.name(), uncertain);
        return new MessageResponse(cleaned, visualSources, uncertain, evidence,
                QueryPlan.RetrievalMode.VISUAL_VALIDATION.name());
    }

    private List<SourceDto> mergeSources(Result<String> result, QueryPlan plan,
                                         GraphEvidenceResult graphEvidence,
                                         QueryPlan.RetrievalMode effectiveMode) {
        Map<String, SourceDto> unique = new LinkedHashMap<>();
        QueryPlan.RetrievalMode mode = effectiveMode == null ? plan.retrievalMode() : effectiveMode;
        List<String> fileScope = plan.fileScope();

        // ALL_SAME_FILE: only joint-intersection files may appear as sources.
        // Hybrid/vector hits that cover only a subset of entities must not be re-injected.
        if (ChatRetrievalPolicy.requiresJointFileEvidence(plan)) {
            for (String path : graphEvidence.certainPaths()) {
                putSource(unique, ingestionService.createGraphFactSourceDto(path, fileName(path), 1.0), true);
            }
            return limitSources(List.copyOf(unique.values()));
        }

        // Graph facts first; then scoped hybrid hits (also for GRAPH — clothing/actions in embeddings).
        if (mode != QueryPlan.RetrievalMode.DOCUMENT) {
            for (String path : graphEvidence.certainPaths()) {
                if (RetrievalPathScope.pathInScope(path, fileScope)) {
                    putSource(unique, ingestionService.createGraphFactSourceDto(path, fileName(path), 1.0), true);
                }
            }
        }

        if (result != null) {
            for (SourceDto source : ingestionService.getSources(result)) {
                if (source == null || !RetrievalPathScope.pathInScope(source.path(), fileScope)) {
                    continue;
                }
                // Named people: hybrid hits must already have certain graph evidence for them
                // (never promote another person's RAG path as a source).
                if (!ChatRetrievalPolicy.allowsHybridSourceForNamedEntities(plan, graphEvidence, source.path())) {
                    continue;
                }
                putSource(unique, source, false);
            }
        }

        return limitSources(List.copyOf(unique.values()));
    }

    /**
     * Prefer GRAPH_FACT and higher scores, then cap to {@link #maxSources} for UX.
     * ALL_SAME_FILE already returns early with only joint paths (typically few).
     */
    private List<SourceDto> limitSources(List<SourceDto> sources) {
        if (sources == null || sources.isEmpty()) {
            return List.of();
        }
        int cap = Math.max(1, maxSources);
        if (sources.size() <= cap) {
            return sources;
        }
        List<SourceDto> ranked = new ArrayList<>(sources);
        ranked.sort((a, b) -> {
            boolean aGraph = a != null && "GRAPH_FACT".equals(a.type());
            boolean bGraph = b != null && "GRAPH_FACT".equals(b.type());
            if (aGraph != bGraph) {
                return aGraph ? -1 : 1;
            }
            double as = a == null || a.score() == null ? 0.0 : a.score();
            double bs = b == null || b.score() == null ? 0.0 : b.score();
            return Double.compare(bs, as);
        });
        return List.copyOf(ranked.subList(0, cap));
    }

    /**
     * @param preferOverExisting when true (graph facts), replaces a non-GRAPH_FACT entry for the same path
     */
    private void putSource(Map<String, SourceDto> unique, SourceDto source, boolean preferOverExisting) {
        if (source == null) return;
        String key = source.path() == null ? "source-" + unique.size() : source.path();
        SourceDto existing = unique.get(key);
        if (existing == null) {
            unique.put(key, source);
            return;
        }
        if (preferOverExisting && !"GRAPH_FACT".equals(existing.type())) {
            unique.put(key, source);
        }
    }

    /**
     * Standalone question for the answer model: planner retrievalQuery/condition when they
     * expand a short follow-up; otherwise the raw user question. Technical fields only.
     */
    static String answerFacingQuestion(QueryPlan plan) {
        if (plan == null) {
            return "";
        }
        String raw = plan.question() == null ? "" : plan.question().trim();
        String retrieval = plan.retrievalQuery() == null ? "" : plan.retrievalQuery().trim();
        String condition = plan.condition() == null ? "" : plan.condition().trim();
        if (!retrieval.isBlank() && !retrieval.equalsIgnoreCase(raw)) {
            return retrieval;
        }
        if (!condition.isBlank() && !condition.equalsIgnoreCase(raw)) {
            return condition;
        }
        return raw;
    }

    /** Compact recent turns for the answer prompt (no phrase routing). */
    static String compactConversationContext(String conversationContext) {
        if (conversationContext == null || conversationContext.isBlank()) {
            return "";
        }
        String trimmed = conversationContext.trim();
        if (trimmed.length() > 1200) {
            trimmed = trimmed.substring(trimmed.length() - 1200).trim();
        }
        return "\n[Ostatnie tury rozmowy]\n" + trimmed + "\n";
    }

    private void syncGroundedAnswerToMemory(UUID chatId, String cleaned) {
        if (chatMemoryService == null || cleaned == null || cleaned.isBlank()) {
            return;
        }
        try {
            chatMemoryService.replaceLastAiMessage(chatId, cleaned);
        } catch (Exception e) {
            log.warn("Failed to sync grounded answer into chat memory for {}: {}", chatId, e.getMessage());
        }
    }

    private String answerPrompt(String context) {
        return ChatService.ANSWER_INSTRUCTIONS + """

                [Styl odpowiedzi]
                Odpowiadaj naturalnie po polsku (1–3 zdania). Podaj żądane szczegóły z dowodów
                (ubiór, wygląd, czynności, relacje, scena). Bez pewności i listy plików.

                """ + context;
    }

    private String removeTechnicalReferences(String answer, List<SourceDto> sources) {
        if (answer == null) {
            return "";
        }
        String cleaned = answer;
        if (sources != null) {
            for (SourceDto source : sources) {
                if (source.path() != null) cleaned = cleaned.replace(source.path(), "");
                if (source.fileName() != null) {
                    cleaned = cleaned.replace("@" + source.fileName(), "");
                    cleaned = cleaned.replace(source.fileName(), "");
                }
            }
        }
        cleaned = DIR_PATH.matcher(cleaned).replaceAll("");
        cleaned = AT_FILENAME.matcher(cleaned).replaceAll("");
        cleaned = cleaned.replaceAll("(?i)\\b(źródło|zrodlo|plik|source)\\s*[:=]\\s*\\S+", "");
        cleaned = cleaned.replaceAll("[ \\t]+", " ").replaceAll("\\n{3,}", "\n\n").trim();
        return ChatAnswerGrounding.cleanEmptyQuoteArtifacts(cleaned);
    }

    /**
     * Paths used to load certain participant names: plan fileScope, then certain graph paths,
     * then final source paths. Technical set only — no question routing.
     */
    private static List<String> rosterPaths(QueryPlan plan, GraphEvidenceResult graphEvidence,
                                            List<SourceDto> sources) {
        LinkedHashMap<String, Boolean> paths = new LinkedHashMap<>();
        if (plan != null && plan.fileScope() != null) {
            for (String path : plan.fileScope()) {
                if (path != null && !path.isBlank()) {
                    paths.putIfAbsent(path.trim(), Boolean.TRUE);
                }
            }
        }
        if (graphEvidence != null && graphEvidence.certainPaths() != null) {
            for (String path : graphEvidence.certainPaths()) {
                if (path != null && !path.isBlank()) {
                    paths.putIfAbsent(path.trim(), Boolean.TRUE);
                }
            }
        }
        if (sources != null) {
            for (SourceDto source : sources) {
                if (source != null && source.path() != null && !source.path().isBlank()) {
                    paths.putIfAbsent(source.path().trim(), Boolean.TRUE);
                }
            }
        }
        return List.copyOf(paths.keySet());
    }

    private String fileName(String path) {
        int separator = path.lastIndexOf('/');
        return separator >= 0 ? path.substring(separator + 1) : path;
    }

    private void saveUserMessage(UUID chatId, String question) {
        chatMessageRepository.save(ChatMessageEntity.builder().chatId(chatId).role("USER")
                .textContext(question).imagePaths(List.of()).scores(List.of()).build());
    }

    private void saveAiMessage(UUID chatId, String text, List<SourceDto> sources,
                               List<QueryEvidenceDto> evidence, String answerKind, boolean uncertain) {
        chatMessageRepository.save(ChatMessageEntity.builder().chatId(chatId).role("AI")
                .textContext(text).imagePaths(sources.stream().map(SourceDto::path).toList())
                .scores(sources.stream().map(SourceDto::score).toList()).evidence(evidence)
                .answerKind(answerKind).uncertain(uncertain).build());
    }
}

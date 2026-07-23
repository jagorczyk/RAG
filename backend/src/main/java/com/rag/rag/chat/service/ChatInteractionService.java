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
 * VISUAL_VALIDATION → claim answers; GRAPH (people) / HYBRID → free-form AI from
 * the full graph dump (claims are context, not the answer prose on freeform branch).
 * No phrase-based question routing in this class.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatInteractionService {

    private static final Pattern DIR_PATH = Pattern.compile("dir://\\S+");
    private static final Pattern AT_FILENAME = Pattern.compile("@[\\w.\\-]+");
    static final String ATTRIBUTION_FAILURE_ANSWER =
            "Nie udało się potwierdzić odpowiedzi na podstawie dostępnych zdjęć.";

    private final ChatService chatAiService;
    private final ChatMemoryRepository chatMemoryRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatMemoryService chatMemoryService;
    private final IngestionService ingestionService;
    private final GraphQueryService graphQueryService;
    private final DynamicVisualMatcher dynamicVisualMatcher;
    private final QueryPlanner queryPlanner;
    private final VerifiedVisualAnswerService verifiedVisualAnswerService;
    private final GraphContextReducer graphContextReducer;
    private final GraphSourceAttributionService graphSourceAttributionService;
    private final FreeformAnswerRepairService freeformAnswerRepairService;
    private final GraphGroundedAnswerRegenerationService graphGroundedAnswerRegenerationService;
    private final LibraryScopeService libraryScopeService;
    private final LibraryOverviewService libraryOverviewService;

    /** Cap un-attributed retrieval sources; verified graph attribution is never truncated. */
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
        List<UUID> requestedFolderIds = messageRequest.folderIds() == null
                ? List.of() : messageRequest.folderIds().stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (!requestedFolderIds.isEmpty()) {
            List<UUID> validatedFolderIds = libraryScopeService == null
                    ? List.of() : libraryScopeService.validateFolderIds(requestedFolderIds);
            if (validatedFolderIds.size() != requestedFolderIds.size()) {
                return folderScopeDenial(chatId);
            }
            plan = plan.withFolderScope(validatedFolderIds,
                    libraryScopeService.filePathsForFolders(validatedFolderIds));
        } else if (plan.scopeKind() == QueryPlan.ScopeKind.FOLDER) {
            List<UUID> validatedFolderIds = libraryScopeService == null
                    ? List.of() : libraryScopeService.validateFolderIds(plan.folderScope());
            if (validatedFolderIds.isEmpty()) {
                return folderScopeDenial(chatId);
            }
            plan = plan.withFolderScope(validatedFolderIds,
                    libraryScopeService.filePathsForFolders(validatedFolderIds));
        }
        List<String> explicitFileScope = graphQueryService.resolveExplicitFileScope(question);
        if (!explicitFileScope.isEmpty()) {
            plan = plan.withFileScope(explicitFileScope);
        } else if (graphQueryService.hasExplicitFileReference(question)
                && plan.scopeKind() != QueryPlan.ScopeKind.FOLDER) {
            String denial = "Nie znaleziono wskazanego pliku w Twojej bibliotece.";
            saveAiMessage(chatId, denial, List.of(), List.of(), "NO_EVIDENCE", true);
            return new MessageResponse(denial, List.of(), true, List.of(), "NO_EVIDENCE");
        }
        log.info("Dynamic query plan: mode={}, visual={}, entities={}, ambiguous={}, scopeKind={}, folders={}, overview={}, fileScope={}",
                plan.retrievalMode(), plan.visualCondition(), plan.entities(), plan.ambiguous(),
                plan.scopeKind(), plan.folderScope(), plan.collectionOverview(), plan.fileScope());

        if (plan.collectionOverview()) {
            return processCollectionOverview(chatId, plan);
        }

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

    private MessageResponse folderScopeDenial(UUID chatId) {
        String denial = "Nie znaleziono wskazanego folderu albo jego nazwa jest niejednoznaczna.";
        saveAiMessage(chatId, denial, List.of(), List.of(), "NO_EVIDENCE", true);
        return new MessageResponse(denial, List.of(), true, List.of(), "NO_EVIDENCE");
    }

    private MessageResponse processCollectionOverview(UUID chatId, QueryPlan plan) {
        if (libraryOverviewService == null) {
            String denial = "Nie udało się odczytać zawartości biblioteki.";
            saveAiMessage(chatId, denial, List.of(), List.of(), "NO_EVIDENCE", true);
            return new MessageResponse(denial, List.of(), true, List.of(), "NO_EVIDENCE");
        }
        LibraryOverviewService.Overview overview = libraryOverviewService.build(plan);
        if (overview.unavailable()) {
            saveAiMessage(chatId, overview.inventoryAnswer(), List.of(), List.of(), "NO_EVIDENCE", true);
            return new MessageResponse(overview.inventoryAnswer(), List.of(), true, List.of(), "NO_EVIDENCE");
        }
        if (overview.empty()) {
            String answer = overview.inventoryAnswer();
            saveAiMessage(chatId, answer, List.of(), List.of(), plan.retrievalMode().name(), false);
            return new MessageResponse(answer, List.of(), false, List.of(), plan.retrievalMode().name());
        }

        String question = answerFacingQuestion(plan);
        String overviewStyle = """
                Odpowiedz naturalnie po polsku w 1–3 zdaniach wyłącznie z dowodów INVENTORY.
                Używaj dokładnych liczb oraz wyłącznie podanych nazw folderów, osób i plików.
                Nazwy plików są tylko identyfikatorami katalogowymi: nie wnioskuj z nich o scenie,
                obiektach, osobach, czynnościach ani zawartości pliku.
                Nazwy plików wymieniaj tylko wtedy, gdy odpowiada to pytaniu albo krótka lista
                istotnie pomaga. Nie pokazuj ścieżek. Jeśli lista została ograniczona, powiedz
                dokładnie, ile pozycji pokazano i ile pominięto. Nie dopisuj żadnych obserwacji.
                """;
        GraphEvidenceResult answerEvidence = overview.evidence();
        String prompt = """
                [Zamknięty inwentarz katalogowy]
                %s

                %s
                Pytanie użytkownika: %s
                """.formatted(answerEvidence.context(), overviewStyle, question);

        String answer = "";
        RetrievalPathScope.set(plan.fileScope());
        RetrievalQueryContext.set(retrievalQueryForPlan(plan));
        RetrievalQueryContext.setDisabled(true);
        try {
            Result<String> result = chatAiService.answer(chatId, answerPrompt(prompt));
            answer = result == null || result.content() == null ? "" : result.content().trim();
        } catch (Exception e) {
            log.warn("Collection overview answer failed: {}", e.getMessage());
        } finally {
            RetrievalPathScope.clear();
            RetrievalQueryContext.clear();
        }

        GraphSourceAttributionService.Attribution attribution = null;
        if (!answer.isBlank() && graphSourceAttributionService != null) {
            attribution = graphSourceAttributionService.attributeCollection(
                    question, answer, answerEvidence, 1);
        }
        if ((attribution == null || !attribution.reliable())
                && graphGroundedAnswerRegenerationService != null
                && !answerEvidence.context().isBlank()) {
            String regenerated = graphGroundedAnswerRegenerationService.regenerate(
                    question, answerEvidence.context());
            if (regenerated != null && !regenerated.isBlank() && graphSourceAttributionService != null) {
                GraphSourceAttributionService.Attribution retry =
                        graphSourceAttributionService.attributeCollection(
                                question, regenerated, answerEvidence, 1);
                if (retry.reliable()) {
                    answer = regenerated;
                    attribution = retry;
                }
            }
        }

        boolean useFallback = attribution == null || !attribution.reliable();
        if (useFallback) {
            answer = overview.inventoryAnswer();
        }
        List<SourceDto> sources = List.of();
        String cleaned = removeTechnicalReferences(answer, sources);
        if (cleaned.isBlank()) cleaned = overview.inventoryAnswer();
        List<QueryEvidenceDto> evidence = List.of();
        boolean uncertain = plan.ambiguous();
        syncGroundedAnswerToMemory(chatId, cleaned);
        saveAiMessage(chatId, cleaned, sources, evidence, plan.retrievalMode().name(), uncertain);
        return new MessageResponse(cleaned, sources, uncertain, evidence, plan.retrievalMode().name());
    }

    private MessageResponse processTextOrGraphPlan(UUID chatId, QueryPlan plan, String conversationContext) {
        GraphEvidenceResult graphEvidence = ChatRetrievalPolicy.needsGraphEvidence(plan)
                ? buildGraphEvidence(plan)
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

        // Free-form path: full graph dump (when present) goes to the answer LLM, which
        // formulates natural Polish. Immutable claim short-circuit is disabled on this branch
        // (preferClaimAnswer → false). Post-answer grounding still blocks essays/denials.

        String questionForModel = answerFacingQuestion(plan);
        String originalQuestion = plan.question() == null ? "" : plan.question().trim();
        String recentTurns = compactConversationContext(conversationContext);
        // Free-form: soft style cue only — do not re-impose stiff planner answerInstruction templates.
        String answerStyle = freeformAnswerStyle();

        GraphContextReducer.Selection graphSelection = graphContextReducer == null
                ? new GraphContextReducer.Selection(graphEvidence.context(), graphEvidence.certainPaths(), false,
                0, 0, 0, 0)
                : graphContextReducer.select(graphEvidence, questionForModel, answerStyle + recentTurns);
        String graphContext = graphSelection.context();
        GraphEvidenceResult answerEvidence = graphSelection.hasEvidence()
                ? new GraphEvidenceResult(graphContext, graphSelection.selectedPaths(), graphEvidence.claims(),
                graphEvidence.photos())
                : new GraphEvidenceResult("", List.of());

        // Pinned @file: always force-load embedding JSON for those paths. Short queries like
        // "co wiesz o @x.jpg" often miss vector/lexical recall even when the index has a full photo dump.
        List<String> fileScope = plan.fileScope() == null ? List.of() : plan.fileScope();
        if (!fileScope.isEmpty() && ingestionService != null
                && effectiveMode != QueryPlan.RetrievalMode.GRAPH) {
            String indexBlock = ingestionService.formatEmbeddingContextForPaths(fileScope);
            if (indexBlock != null && !indexBlock.isBlank()) {
                graphContext = graphContext.isBlank()
                        ? indexBlock
                        : graphContext + "\n\n" + indexBlock;
                log.info("Injected embedding index block for {} fileScope path(s), chars={}",
                        fileScope.size(), indexBlock.length());
            } else {
                log.warn("No embedding index text for fileScope paths={}", fileScope);
            }
        }
        String prompt;
        if (graphMissFallback && graphContext.isBlank()) {
            // GRAPH without certain people evidence — hybrid only; refuse if retrieval empty.
            prompt = """
                    %s
                    Graf nie ma pewnych dowodów o osobach; użyj wyłącznie fragmentów z retrieval.
                    Gdy brak fragmentów: Nie znaleziono informacji w dokumentach.
                    %s
                    Użytkownik powiedział: %s

                    Pytanie użytkownika: %s
                    """.formatted(answerStyle, recentTurns, originalQuestion, questionForModel);
        } else if (!graphContext.isBlank()) {
            // Full graph + forced index dump — model selects facts and formulates freely.
            prompt = """
                    [Kontekst zdjęć — graf i indeks wiedzy]
                    %s

                    %s
                    Masz pełny przepływ dowodów. Sam wybierz, co odpowiada na pytanie, i napisz
                    naturalną, swobodną odpowiedź po polsku — nie listę claimów, nie raport.
                    Nie zgaduj poza tym kontekstem.
                    %s
                    Użytkownik powiedział: %s

                    Pytanie użytkownika: %s
                    """.formatted(graphContext, answerStyle, recentTurns, originalQuestion, questionForModel);
        } else {
            prompt = """
                    %s
                    Używaj wyłącznie fragmentów dokumentów z retrieval — nie zgaduj.
                    %s
                    Użytkownik powiedział: %s

                    Pytanie użytkownika: %s
                    """.formatted(answerStyle, recentTurns, originalQuestion, questionForModel);
        }

        // Named people / fileScope: restrict hybrid to proven files. Keep scoped hybrid on GRAPH
        // so appearance/clothing details from embeddings can answer open questions.
        List<String> retrievalScope = ChatRetrievalPolicy.retrievalScope(plan, graphEvidence);
        RetrievalPathScope.set(retrievalScope);
        // Include bare filenames in the retrieval query so lexical recall hits the pinned file.
        RetrievalQueryContext.set(retrievalQueryForPlan(plan));
        // A complete GRAPH packet is the source of truth. Hybrid remains the fallback only when
        // the graph is empty, and stays enabled for HYBRID/DOCUMENT plans.
        RetrievalQueryContext.setDisabled(effectiveMode == QueryPlan.RetrievalMode.GRAPH
                && graphEvidence.hasEvidence());
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
        List<SourceDto> sources = mergeSources(result, plan, answerEvidence, effectiveMode);
        // Grounding uses post-filter sources (certain graph / allowed hybrid), not raw retrieval hits.
        boolean noGrounding = ChatRetrievalPolicy.lacksGrounding(answerEvidence, !sources.isEmpty());
        if (noGrounding) {
            answer = "Nie znaleziono informacji w dokumentach.";
            sources = List.of();
        } else {
            // Freeform GraphRAG: keep model prose unless hard failure (denial/essay/speculative/safety).
            // Recovery roster only for those failures — never for ordinary free-form photo answers.
            if (ChatAnswerGrounding.isHardFailureShape(answer)
                    && answerEvidence.hasEvidence()
                    && freeformAnswerRepairService != null) {
                String repaired = freeformAnswerRepairService.repair(
                        questionForModel, answerEvidence.context(), answer);
                if (repaired != null && !repaired.isBlank()) {
                    answer = repaired;
                }
            }
            boolean preferPhotoRoster = (plan.fileScope() != null && !plan.fileScope().isEmpty())
                    || (answerEvidence.certainPaths() != null && answerEvidence.certainPaths().size() == 1);
            boolean entityScoped = ChatRetrievalPolicy.hasNamedEntities(plan) && !preferPhotoRoster;
            List<String> recoveryNames;
            if (entityScoped) {
                recoveryNames = plan.entities();
            } else {
                List<String> paths = rosterPaths(plan, answerEvidence, sources);
                recoveryNames = graphQueryService.certainParticipantNamesForPaths(paths);
            }
            boolean hasCertain = answerEvidence.hasEvidence() || !sources.isEmpty();
            answer = ChatAnswerGrounding.resolveGroundedAnswer(
                    answer, recoveryNames, hasCertain, entityScoped);
        }
        GraphSourceAttributionService.Attribution attribution = attributionForFinalAnswerSources(
                questionForModel, answer, answerEvidence);
        boolean attributionFailure = false;
        if (attribution != null) {
            if (!hasAttributedPaths(attribution) && graphGroundedAnswerRegenerationService != null) {
                String regenerated = graphGroundedAnswerRegenerationService.regenerate(
                        questionForModel, answerEvidence.context());
                if (regenerated != null && !regenerated.isBlank()) {
                    GraphSourceAttributionService.Attribution retryAttribution =
                            graphSourceAttributionService.attribute(
                                    questionForModel, regenerated, answerEvidence);
                    if (hasAttributedPaths(retryAttribution)) {
                        answer = regenerated;
                        attribution = retryAttribution;
                        log.info("Recovered an ungrounded graph answer with one bounded regeneration");
                    }
                }
            }
            // Typed graph evidence is authoritative here: after post-answer attribution do not
            // re-add retrieval hits and do not truncate evidence that supports the final prose.
            sources = hasAttributedPaths(attribution)
                    ? exactAttributedGraphSources(attribution.paths(), plan.fileScope())
                    : List.of();
            attributionFailure = sources.isEmpty();
            if (attributionFailure) {
                answer = ATTRIBUTION_FAILURE_ANSWER;
            }
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
                || attributionFailure
                || sources.isEmpty();
        String answerKind = noGrounding || attributionFailure ? "NO_EVIDENCE" : effectiveMode.name();
        saveAiMessage(chatId, cleaned, sources, sourceEvidence, answerKind, uncertain);
        return new MessageResponse(cleaned, sources, uncertain, sourceEvidence, answerKind);
    }

    private GraphEvidenceResult buildGraphEvidence(QueryPlan plan) {
        if (plan.retrievalMode() == QueryPlan.RetrievalMode.GRAPH
                && plan.entities().isEmpty()
                && plan.fileScope().isEmpty()) {
            return graphQueryService.buildEvidence(plan);
        }
        return graphQueryService.buildEvidence(plan.entities(), plan.fileScope(), plan.entityMatchMode());
    }

    private GraphSourceAttributionService.Attribution attributionForFinalAnswerSources(
            String question, String answer, GraphEvidenceResult evidence) {
        if (evidence == null || evidence.photos().isEmpty()
                || graphSourceAttributionService == null) {
            return null;
        }
        return graphSourceAttributionService.attribute(question, answer, evidence);
    }

    private static boolean hasAttributedPaths(GraphSourceAttributionService.Attribution attribution) {
        return attribution != null && attribution.reliable()
                && attribution.paths() != null && !attribution.paths().isEmpty();
    }

    private List<SourceDto> exactAttributedGraphSources(List<String> paths, List<String> fileScope) {
        if (paths == null || paths.isEmpty()) return List.of();
        Map<String, SourceDto> unique = new LinkedHashMap<>();
        for (String path : paths) {
            if (path == null || path.isBlank() || !RetrievalPathScope.pathInScope(path, fileScope)) {
                continue;
            }
            putSource(unique,
                    ingestionService.createGraphFactSourceDto(path, fileName(path), 1.0), true);
        }
        return List.copyOf(unique.values());
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
        // Free-form branch: formulate from visual claims with the answer LLM (not rigid claim join).
        String answer = freeformAnswerFromVisualMatches(chatId, plan, matches, certainNames);
        if (answer == null || answer.isBlank()
                || VerifiedVisualAnswerService.MATCH_FALLBACK_ANSWER.equals(answer)
                || VerifiedVisualAnswerService.NO_VISUAL_EVIDENCE.equals(answer)) {
            // Fallback to immutable claim prose if free-form model blanked.
            answer = verifiedVisualAnswerService.answer(plan.question(), matches, certainNames);
        }
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
        // Free-form: prefer the user's own wording for full questions. Only expand short
        // follow-ups (e.g. "a olek?") via retrieval/condition — never meta planner noise.
        boolean shortFollowUp = raw.length() > 0 && raw.length() <= 24
                && !raw.contains("@")
                && raw.split("\\s+").length <= 4;
        if (shortFollowUp
                && !retrieval.isBlank()
                && !retrieval.equalsIgnoreCase(raw)
                && !isMetaPlannerPhrase(retrieval)) {
            return retrieval;
        }
        if (shortFollowUp
                && !condition.isBlank()
                && !condition.equalsIgnoreCase(raw)
                && !isMetaPlannerPhrase(condition)) {
            return condition;
        }
        if (!raw.isBlank()) {
            return raw;
        }
        if (!retrieval.isBlank() && !isMetaPlannerPhrase(retrieval)) {
            return retrieval;
        }
        if (!condition.isBlank() && !isMetaPlannerPhrase(condition)) {
            return condition;
        }
        return raw;
    }

    /**
     * Planner schema used to put technical examples into {@code condition} (e.g. old
     * "pełne ograniczenie semantyczne") — those must never become the answer-facing question.
     */
    static boolean isMetaPlannerPhrase(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("ograniczenie semantyczne")
                || lower.contains("semantic constraint")
                || lower.contains("answerinstruction")
                || lower.contains("retrievalmode")
                || lower.contains("visualcondition")
                || lower.contains("entitymatchmode")
                || lower.startsWith("odpowiedz po polsku z dowodów")
                || lower.startsWith("odpowiedz po polsku z dowodow")
                || (lower.contains("bez list plików") && lower.length() < 120)
                || (lower.contains("bez list plikow") && lower.length() < 120);
    }

    static String freeformAnswerStyle() {
        return """
                [Jak odpowiadać]
                Odpowiedz naturalnie i swobodnie po polsku na podstawie kontekstu poniżej.
                Ułóż własną płynną wypowiedź — nie kopiuj claimów, nie pisz raportu ani listy.
                Zwróć zwykły tekst, zawsze od jednego do trzech krótkich zdań.
                Tylko fakty zapisane wprost w kontekście; bez ocen, nastroju i domyślnej atmosfery.
                Różne zdjęcia lub sceny opisuj oddzielnie, nie jako jedną równoczesną sytuację.
                Bez spekulacji, esejów encyklopedycznych, Markdownu, nazw plików i score.
                """;
    }

    /**
     * Hybrid/vector query for this turn. Appends bare filenames from fileScope so lexical
     * recall can hit the pinned photo even when the user question is only "co wiesz o @…".
     */
    static String retrievalQueryForPlan(QueryPlan plan) {
        if (plan == null) {
            return "";
        }
        String base = plan.retrievalQuery() == null || plan.retrievalQuery().isBlank()
                ? (plan.question() == null ? "" : plan.question().trim())
                : plan.retrievalQuery().trim();
        if (plan.fileScope() == null || plan.fileScope().isEmpty()) {
            return base;
        }
        StringBuilder extra = new StringBuilder();
        for (String path : plan.fileScope()) {
            if (path == null || path.isBlank()) {
                continue;
            }
            String name = fileName(path);
            if (name != null && !name.isBlank() && !base.toLowerCase().contains(name.toLowerCase())) {
                if (!extra.isEmpty()) {
                    extra.append(' ');
                }
                extra.append(name);
            }
        }
        if (extra.isEmpty()) {
            return base;
        }
        return base.isBlank() ? extra.toString() : base + " " + extra;
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
        // SystemMessage carries the hard answer contract; the user turn already contains one
        // compact style cue. Do not duplicate either block.
        return context == null ? "" : context;
    }

    /**
     * Free-form visual answer: claim statements are evidence context, not the final prose.
     */
    private String freeformAnswerFromVisualMatches(
            UUID chatId, QueryPlan plan, List<VisualQueryMatch> matches, List<String> certainNames) {
        if (chatAiService == null || chatId == null || matches == null || matches.isEmpty()) {
            return null;
        }
        StringBuilder evidence = new StringBuilder();
        for (VisualQueryMatch match : matches) {
            if (match == null) {
                continue;
            }
            if (match.filePath() != null && !match.filePath().isBlank()) {
                evidence.append("Plik: ").append(fileName(match.filePath())).append('\n');
            }
            if (match.claims() != null) {
                for (var claim : match.claims()) {
                    if (claim != null && claim.statementPl() != null && !claim.statementPl().isBlank()) {
                        evidence.append("- ").append(claim.statementPl().trim()).append('\n');
                    }
                }
            }
            if (match.reasons() != null) {
                for (String reason : match.reasons()) {
                    if (reason != null && !reason.isBlank()) {
                        evidence.append("- ").append(reason.trim()).append('\n');
                    }
                }
            }
        }
        if (evidence.isEmpty()) {
            return null;
        }
        if (certainNames != null && !certainNames.isEmpty()) {
            evidence.append("Uczestnicy: ").append(String.join(", ", certainNames)).append('\n');
        }
        String question = answerFacingQuestion(plan);
        String prompt = """
                [Dowody wizualne MATCH]
                %s

                %s
                Napisz naturalną odpowiedź na pytanie wyłącznie z powyższych dowodów.

                Pytanie użytkownika: %s
                """.formatted(evidence.toString().trim(), freeformAnswerStyle(), question);
        List<String> scope = matches.stream()
                .map(VisualQueryMatch::filePath)
                .filter(path -> path != null && !path.isBlank())
                .distinct()
                .toList();
        RetrievalPathScope.set(scope);
        RetrievalQueryContext.set(retrievalQueryForPlan(plan));
        try {
            Result<String> result = chatAiService.answer(chatId, answerPrompt(prompt));
            return result == null ? null : result.content();
        } catch (Exception e) {
            log.warn("Free-form visual answer failed: {}", e.getMessage());
            return null;
        } finally {
            RetrievalPathScope.clear();
            RetrievalQueryContext.clear();
        }
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
        cleaned = cleaned.replaceAll("\\[(?:E\\d+\\.\\d+|A\\.\\d+|[ICG]\\d*\\.\\d+)\\]\\s*", "");
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

    private static String fileName(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
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

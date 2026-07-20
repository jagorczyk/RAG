package com.rag.rag.chat.service;

import com.rag.rag.chat.dto.MessageRequest;
import com.rag.rag.chat.dto.MessageResponse;
import com.rag.rag.chat.dto.QueryEvidenceDto;
import com.rag.rag.chat.entity.ChatMessageEntity;
import com.rag.rag.chat.repository.ChatMemoryRepository;
import com.rag.rag.chat.repository.ChatMessageRepository;
import com.rag.rag.core.retrieval.RetrievalPathScope;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Comparator;
import java.util.regex.Pattern;

/** Executes model-produced query plans; it contains no semantic question routing. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatInteractionService {

    private static final Pattern DIR_PATH = Pattern.compile("dir://\\S+");
    private static final Pattern AT_FILENAME = Pattern.compile("@[\\w.\\-]+");

    private final ChatService chatAiService;
    private final ChatMemoryRepository chatMemoryRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final IngestionService ingestionService;
    private final GraphQueryService graphQueryService;
    private final DynamicVisualMatcher dynamicVisualMatcher;
    private final QueryPlanner queryPlanner;
    private final VerifiedVisualAnswerService verifiedVisualAnswerService;

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
        log.info("Dynamic query plan: mode={}, visual={}, entities={}, ambiguous={}",
                plan.retrievalMode(), plan.visualCondition(), plan.entities(), plan.ambiguous());

        if (plan.visualCondition() && dynamicVisualMatcher != null) {
            return processVisualPlan(chatId, plan);
        }
        return processTextOrGraphPlan(chatId, plan);
    }

    private MessageResponse processTextOrGraphPlan(UUID chatId, QueryPlan plan) {
        GraphEvidenceResult graphEvidence = graphQueryService.buildEvidence(
                plan.entities(), plan.fileScope(), plan.entityMatchMode());
        // Co-presence is a set operation on planner output: when ALL_SAME_FILE has no
        // intersection evidence, never let the LLM or hybrid retrieval affirm a joint photo.
        if (ChatRetrievalPolicy.mustDenyJointFile(plan, graphEvidence)) {
            String denial = "Nie znaleziono potwierdzonego wspólnego zdjęcia tych osób.";
            saveAiMessage(chatId, denial, List.of(), List.of(), plan.retrievalMode().name(), true);
            return new MessageResponse(denial, List.of(), true, List.of(), plan.retrievalMode().name());
        }

        boolean graphMissFallback = ChatRetrievalPolicy.shouldFallbackFromGraph(plan, graphEvidence);
        QueryPlan.RetrievalMode effectiveMode = ChatRetrievalPolicy.effectiveRetrievalMode(plan, graphEvidence);

        String graphContext = graphEvidence.context();
        String prompt;
        if (graphMissFallback) {
            // No certain graph rows — answer from hybrid document retrieval on the question.
            prompt = """
                    [Instrukcja odpowiedzi]
                    %s
                    Graf wiedzy nie ma pewnych dowodów dla tego pytania; użyj wyłącznie kontekstu dokumentów z retrieval.
                    Gdy brak fragmentów dokumentów, odpowiedz dokładnie: Nie znaleziono informacji w dokumentach.
                    Odpowiedź: jedno krótkie zdanie po polsku. Bez opisu wyglądu, sceny, pewności i bez listy plików.

                    Pytanie użytkownika: %s
                    """.formatted(plan.answerInstruction(), plan.question());
        } else if (graphContext.isBlank()) {
            prompt = plan.question();
        } else {
            prompt = """
                    [Kontekst zweryfikowany]
                    %s

                    [Instrukcja odpowiedzi]
                    %s
                    Odpowiedź: jedno krótkie zdanie po polsku. Bez opisu wyglądu, sceny, pewności i bez listy plików.
                    Używaj wyłącznie powyższego kontekstu i fragmentów dokumentów z retrieval — nie zgaduj.

                    Pytanie użytkownika: %s
                    """.formatted(graphContext, plan.answerInstruction(), plan.question());
        }

        // Named-entity turns with certain graph paths: restrict hybrid RAG to those files
        // (plus planner fileScope). Prevents off-person corpus chunks from polluting the prompt.
        List<String> retrievalScope = ChatRetrievalPolicy.retrievalScope(plan, graphEvidence);
        RetrievalPathScope.set(retrievalScope);
        Result<String> result;
        try {
            result = chatAiService.answer(chatId, answerPrompt(prompt));
        } finally {
            RetrievalPathScope.clear();
        }

        String answer = result == null ? "" : result.content();
        if (answer == null || answer.isBlank()) {
            answer = "Nie udało się przygotować odpowiedzi na podstawie dostępnych danych.";
        }
        List<SourceDto> sources = mergeSources(result, plan, graphEvidence, effectiveMode);
        // Grounding uses post-filter sources (certain graph / allowed hybrid), not raw retrieval hits.
        // Named-entity hybrid hits without certain graph paths never become sources and must not
        // keep a free-form LLM answer (anti-hallucination, AGENTS.md principle 2).
        boolean noGrounding = ChatRetrievalPolicy.lacksGrounding(graphEvidence, !sources.isEmpty());
        if (noGrounding) {
            answer = "Nie znaleziono informacji w dokumentach.";
            sources = List.of();
        }
        String cleaned = removeTechnicalReferences(answer, sources);
        boolean uncertain = plan.ambiguous()
                || graphMissFallback
                || noGrounding
                || sources.isEmpty();
        String answerKind = noGrounding ? "NO_EVIDENCE" : effectiveMode.name();
        saveAiMessage(chatId, cleaned, sources, List.of(), answerKind, uncertain);
        return new MessageResponse(cleaned, sources, uncertain, List.of(), answerKind);
    }

    private MessageResponse processVisualPlan(UUID chatId, QueryPlan plan) {
        List<VisualQueryMatch> matches = dynamicVisualMatcher.findEvidence(plan);
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
            saveAiMessage(chatId, answer, List.of(), List.of(), "NO_EVIDENCE", true);
            return new MessageResponse(answer, List.of(), true, List.of(), "NO_EVIDENCE");
        }
        boolean uncertain = plan.ambiguous() || matches.stream()
                .anyMatch(match -> match.decision() == VisualMatchDecision.Decision.UNCERTAIN);
        // Answer prose stays short; detailed reasons stay only in evidence/sources UI.
        String answer = verifiedVisualAnswerService.answer(plan.question(), matches.size());
        if (answer == null || answer.isBlank()) {
            answer = "Oto potwierdzone zdjęcia.";
        }
        String cleaned = removeTechnicalReferences(answer, visualSources);
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
                addSource(unique, ingestionService.createGraphFactSourceDto(path, fileName(path), 1.0));
            }
            return List.copyOf(unique.values());
        }

        if (mode != QueryPlan.RetrievalMode.GRAPH && result != null) {
            for (SourceDto source : ingestionService.getSources(result)) {
                if (source == null || !RetrievalPathScope.pathInScope(source.path(), fileScope)) {
                    continue;
                }
                // Named people: hybrid hits must already have certain graph evidence for them
                // (never promote another person's RAG path as a source).
                if (!ChatRetrievalPolicy.allowsHybridSourceForNamedEntities(plan, graphEvidence, source.path())) {
                    continue;
                }
                addSource(unique, source);
            }
        }

        if (mode != QueryPlan.RetrievalMode.DOCUMENT) {
            for (String path : graphEvidence.certainPaths()) {
                if (RetrievalPathScope.pathInScope(path, fileScope)) {
                    addSource(unique, ingestionService.createGraphFactSourceDto(path, fileName(path), 1.0));
                }
            }
        }

        return List.copyOf(unique.values());
    }

    private void addSource(Map<String, SourceDto> unique, SourceDto source) {
        if (source == null) return;
        String key = source.path() == null ? "source-" + unique.size() : source.path();
        unique.putIfAbsent(key, source);
    }

    private String answerPrompt(String context) {
        return ChatService.ANSWER_INSTRUCTIONS + """

                [Styl odpowiedzi]
                Jedno krótkie zdanie po polsku. Bez opisu wyglądu, sceny, pewności i bez listy plików.

                """ + context;
    }

    private String removeTechnicalReferences(String answer, List<SourceDto> sources) {
        if (answer == null) {
            return "";
        }
        String cleaned = answer;
        for (SourceDto source : sources) {
            if (source.path() != null) cleaned = cleaned.replace(source.path(), "");
            if (source.fileName() != null) {
                cleaned = cleaned.replace("@" + source.fileName(), "");
                cleaned = cleaned.replace(source.fileName(), "");
            }
        }
        cleaned = DIR_PATH.matcher(cleaned).replaceAll("");
        cleaned = AT_FILENAME.matcher(cleaned).replaceAll("");
        cleaned = cleaned.replaceAll("(?i)\\b(źródło|zrodlo|plik|source)\\s*[:=]\\s*\\S+", "");
        return cleaned.replaceAll("[ \\t]+", " ").replaceAll("\\n{3,}", "\n\n").trim();
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

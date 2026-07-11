package com.rag.rag.chat.service;

import com.rag.rag.chat.dto.MessageRequest;
import com.rag.rag.chat.dto.MessageResponse;
import com.rag.rag.chat.entity.ChatMessageEntity;
import com.rag.rag.chat.repository.ChatMemoryRepository;
import com.rag.rag.chat.repository.ChatMessageRepository;
import com.rag.rag.ingestion.dto.SourceDto;
import com.rag.rag.ingestion.service.IngestionService;
import com.rag.rag.knowledge.graph.GraphQueryService;
import com.rag.rag.knowledge.graph.PolishNameMatcher;
import dev.langchain4j.service.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatInteractionService {

    private final ChatService chatAiService;
    private final ChatMemoryRepository chatMemoryRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final IngestionService ingestionService;
    private final QueryRouter queryRouter;
    private final GraphQueryService graphQueryService;
    private final ChatEntityReferenceService chatEntityReferenceService;

    private static final Pattern GRAPH_FILE_PATH_PATTERN = Pattern.compile("plik: (.*)");
    private static final Pattern DIR_PATH_PATTERN = Pattern.compile("dir://[^\\s\\]\\),]+");
    private static final Pattern PHOTO_COUNT_PATTERN = Pattern.compile("(?i)(\\d+)\\s*zdj");

    @Transactional
    public MessageResponse processChatMessage(UUID chatId, MessageRequest messageRequest) {
        ChatMessageEntity userMsg = ChatMessageEntity.builder()
                .chatId(chatId)
                .role("USER")
                .textContext(messageRequest.message())
                .imagePaths(List.of())
                .scores(List.of())
                .build();
        chatMessageRepository.save(userMsg);

        chatMemoryRepository.findById(chatId).ifPresent(chat -> {
            chat.setLastMessageAt(LocalDateTime.now());
            chatMemoryRepository.save(chat);
        });

        String question = messageRequest.message();
        QueryRouter.QueryRoute route = queryRouter.classify(question);
        log.info("Query Route for question '{}': {}", question, route);

        String fullQuestion = question;
        String graphContext = buildGraphContext(chatId, question, route);
        String fileGraphContext = buildFileScopedGraphContext(chatId, question);
        if (fileGraphContext != null && !fileGraphContext.isBlank()) {
            graphContext = fileGraphContext + (graphContext.isBlank() ? "" : "\n" + graphContext);
        }
        if (graphContext != null && !graphContext.isEmpty()) {
            fullQuestion = graphContext + "\n\nPytanie użytkownika: " + question;
        }

        Result<String> result = chatAiService.answer(chatId, fullQuestion);
        String aiResponse = result.content();
        
        log.info("AI RESPONSE: [{}]", aiResponse);

        if (aiResponse == null || aiResponse.trim().isEmpty()) {
            aiResponse = "Przepraszam, model zwrócił pustą odpowiedź.";
        }

        List<SourceDto> filteredSources = extractSourcesFromResponse(result, aiResponse, graphContext, route, question);
        
        String cleanedResponse = cleanUpAiResponse(aiResponse, filteredSources);

        saveAiMessage(chatId, cleanedResponse, filteredSources);

        return new MessageResponse(cleanedResponse, filteredSources);
    }

    private String buildGraphContext(UUID chatId, String question, QueryRouter.QueryRoute route) {
        return switch (route) {
            case ENTITY_NEIGHBOR -> graphQueryService.buildNeighborContextForQuestion(question);
            case ENTITY_SPATIAL_LEFT -> graphQueryService.buildSpatialLeftContextForQuestion(question);
            case ENTITY_SPATIAL_RIGHT -> graphQueryService.buildSpatialRightContextForQuestion(question);
            case ENTITY_FILES, ENTITY_LIST -> graphQueryService.buildFileListContextForQuestion(question);
            case ENTITY_CO_OCCURRENCE -> graphQueryService.buildCoOccurrenceContextForQuestion(question);
            case ENTITY_DESCRIPTION -> buildDescriptionContext(chatId, question);
            case ENTITY_ACTIVITY -> buildEntityContext(question);
            case HYBRID -> buildEntityContext(question);
            case DOCUMENT -> "";
        };
    }

    private String buildEntityContext(String question) {
        Optional<String> entityName = graphQueryService.findEntityNameInQuestion(question);
        if (entityName.isPresent()) {
            String context = graphQueryService.buildContextForEntity(entityName.get());
            if (!context.isEmpty()) {
                return context;
            }
        }
        return graphQueryService.buildContextForQuestion(question);
    }

    private String buildDescriptionContext(UUID chatId, String question) {
        Optional<String> resolvedEntity = chatEntityReferenceService.resolveReference(chatId, question);
        if (resolvedEntity.isPresent()) {
            String entityContext = graphQueryService.buildContextForEntity(resolvedEntity.get());
            if (!entityContext.isEmpty()) {
                return entityContext;
            }
        }
        Optional<String> entityFromQuestion = graphQueryService.findEntityNameInQuestion(question);
        if (entityFromQuestion.isPresent()) {
            String entityContext = graphQueryService.buildContextForEntity(entityFromQuestion.get());
            if (!entityContext.isEmpty()) {
                return entityContext;
            }
        }
        return graphQueryService.buildContextForQuestion(question);
    }

    private String buildFileScopedGraphContext(UUID chatId, String question) {
        Optional<String> filePath = graphQueryService.resolveFilePathFromQuestion(question);
        if (filePath.isEmpty()) {
            filePath = chatEntityReferenceService.resolveRecentSourceFilePath(chatId);
        }
        return filePath.map(graphQueryService::buildContextForFile).orElse("");
    }

    private List<SourceDto> extractSourcesFromResponse(
            Result<String> result,
            String aiResponse,
            String graphContext,
            QueryRouter.QueryRoute route,
            String question
    ) {
        boolean noInfo = aiResponse.toLowerCase().contains("nie znaleziono informacji") || aiResponse.trim().isEmpty();

        if (noInfo) {
            return List.of();
        }

        List<SourceDto> retrievalSources = ingestionService.getSources(result);
        List<SourceDto> graphSources = extractGraphSources(graphContext);
        String lowerResponse = aiResponse.toLowerCase(Locale.ROOT);
        Optional<String> entityName = graphQueryService.findEntityNameInQuestion(question);

        if (!graphSources.isEmpty() && usesGraphDrivenSources(route, graphContext)) {
            List<String> contextKeywords = extractContextKeywords(question, entityName);
            List<SourceDto> contextRetrieval = filterRetrievalByQuestionContext(result, question, entityName);
            List<SourceDto> retrievalPool = !contextRetrieval.isEmpty() ? contextRetrieval : retrievalSources;

            List<SourceDto> candidates;
            if (isGraphOnlySourceRoute(route, contextKeywords)) {
                candidates = dedupeSources(graphSources);
            } else if (isPureFileListQuestion(route, contextKeywords)) {
                candidates = dedupeSources(graphSources);
            } else {
                candidates = narrowWithRetrieval(graphSources, retrievalPool);

                if (candidates.isEmpty() && !retrievalSources.isEmpty()) {
                    candidates = narrowWithRetrieval(graphSources, retrievalSources);
                }
                if (candidates.isEmpty()) {
                    candidates = dedupeSources(graphSources);
                }
            }

            candidates = rankByRetrievalScore(candidates, retrievalSources);

            List<SourceDto> mentioned = filterSourcesReferencedInResponse(lowerResponse, candidates);
            if (!mentioned.isEmpty()) {
                return applyDeclaredCountLimit(mentioned, aiResponse, retrievalSources);
            }
            if (!candidates.isEmpty()) {
                return applyDeclaredCountLimit(candidates, aiResponse, retrievalSources);
            }
        }

        List<SourceDto> allSources = new ArrayList<>(retrievalSources);
        allSources.addAll(graphSources);
        List<SourceDto> mentioned = filterSourcesReferencedInResponse(lowerResponse, allSources);
        if (!mentioned.isEmpty()) {
            return applyDeclaredCountLimit(mentioned, aiResponse, retrievalSources);
        }

        if (!retrievalSources.isEmpty()) {
            return applyDeclaredCountLimit(dedupeSources(retrievalSources), aiResponse, retrievalSources);
        }

        log.info("FILTERED SOURCES COUNT: 0");
        return List.of();
    }

    private List<SourceDto> filterRetrievalByQuestionContext(
            Result<String> result,
            String question,
            Optional<String> entityName
    ) {
        if (result.sources() == null || result.sources().isEmpty()) {
            return List.of();
        }

        List<String> contextKeywords = extractContextKeywords(question, entityName);
        if (contextKeywords.isEmpty()) {
            return List.of();
        }

        Map<String, SourceDto> bestByPath = new LinkedHashMap<>();
        for (var source : result.sources()) {
            String segmentText = source.textSegment().text().toLowerCase(Locale.ROOT);
            boolean matches = contextKeywords.stream().anyMatch(segmentText::contains);
            if (!matches) {
                continue;
            }

            var metadata = source.textSegment().metadata();
            String path = metadata.getString("path");
            if (path == null) {
                continue;
            }

            double score = parseScore(metadata.getString("score"));
            SourceDto candidate = ingestionService.createSourceDto(
                    path,
                    metadata.getString("filename"),
                    score
            );
            SourceDto existing = bestByPath.get(path);
            if (existing == null || candidate.score() > existing.score()) {
                bestByPath.put(path, candidate);
            }
        }

        return bestByPath.values().stream()
                .sorted(Comparator.<SourceDto>comparingDouble(source ->
                        source.score() != null ? source.score() : 0.0).reversed())
                .toList();
    }

    private List<String> extractContextKeywords(String question, Optional<String> entityName) {
        Set<String> keywords = new LinkedHashSet<>();
        Set<String> entityVariants = new LinkedHashSet<>();
        entityName.ifPresent(name -> entityVariants.addAll(PolishNameMatcher.generateVariants(name)));

        for (String token : PolishNameMatcher.extractEntityTokens(question)) {
            if (entityVariants.contains(token)) {
                continue;
            }
            keywords.add(token);
            if (token.length() > 4 && token.endsWith("i")) {
                keywords.add(token.substring(0, token.length() - 1) + "a");
            }
            if (token.length() > 4 && token.endsWith("ie")) {
                keywords.add(token.substring(0, token.length() - 2) + "a");
            }
        }
        return new ArrayList<>(keywords);
    }

    private double parseScore(String scoreStr) {
        if (scoreStr == null) {
            return 0.0;
        }
        try {
            return Double.parseDouble(scoreStr);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private List<SourceDto> rankByRetrievalScore(List<SourceDto> candidates, List<SourceDto> retrievalSources) {
        Map<String, Double> scoresByPath = new HashMap<>();
        for (SourceDto source : retrievalSources) {
            if (source.path() != null) {
                scoresByPath.merge(source.path(), source.score(), Math::max);
            }
        }

        return candidates.stream()
                .sorted(Comparator.<SourceDto>comparingDouble(source -> scoresByPath.getOrDefault(
                        source.path(),
                        source.score() != null ? source.score() : 0.0
                )).reversed())
                .toList();
    }

    private List<SourceDto> applyDeclaredCountLimit(
            List<SourceDto> candidates,
            String aiResponse,
            List<SourceDto> retrievalSources
    ) {
        List<SourceDto> ranked = rankByRetrievalScore(candidates, retrievalSources);
        Optional<Integer> declaredCount = parseDeclaredPhotoCount(aiResponse);
        if (declaredCount.isPresent() && ranked.size() > declaredCount.get()) {
            ranked = ranked.subList(0, declaredCount.get());
        }
        return dedupeSources(ranked);
    }

    private Optional<Integer> parseDeclaredPhotoCount(String aiResponse) {
        Matcher matcher = PHOTO_COUNT_PATTERN.matcher(aiResponse);
        if (!matcher.find()) {
            return Optional.empty();
        }
        try {
            int count = Integer.parseInt(matcher.group(1));
            return count > 0 ? Optional.of(count) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private boolean usesGraphDrivenSources(QueryRouter.QueryRoute route, String graphContext) {
        if (graphContext == null || graphContext.isBlank()) {
            return false;
        }
        return isFileListRoute(route)
                || graphContext.startsWith("[Pliki z grafu wiedzy]")
                || graphContext.startsWith("[Relacje z grafu wiedzy]")
                || graphContext.startsWith("[Współwystępowania z grafu wiedzy]")
                || graphContext.startsWith("[Fakty z grafu wiedzy]")
                || graphContext.startsWith("[Osoby z grafu wiedzy na pliku]")
                || route == QueryRouter.QueryRoute.HYBRID
                || route == QueryRouter.QueryRoute.ENTITY_CO_OCCURRENCE
                || route == QueryRouter.QueryRoute.ENTITY_ACTIVITY
                || route == QueryRouter.QueryRoute.ENTITY_DESCRIPTION
                || route == QueryRouter.QueryRoute.ENTITY_NEIGHBOR
                || route == QueryRouter.QueryRoute.ENTITY_SPATIAL_LEFT
                || route == QueryRouter.QueryRoute.ENTITY_SPATIAL_RIGHT;
    }

    private List<SourceDto> narrowWithRetrieval(List<SourceDto> graphSources, List<SourceDto> retrievalSources) {
        if (retrievalSources.isEmpty()) {
            return List.of();
        }

        Set<String> retrievalPaths = new HashSet<>();
        for (SourceDto source : retrievalSources) {
            if (source.path() != null) {
                retrievalPaths.add(source.path());
            }
        }

        List<SourceDto> intersection = graphSources.stream()
                .filter(source -> source.path() != null && retrievalPaths.contains(source.path()))
                .toList();

        return intersection.isEmpty() ? List.of() : dedupeSources(intersection);
    }

    private List<SourceDto> filterSourcesReferencedInResponse(String lowerResponse, List<SourceDto> sources) {
        Map<String, SourceDto> uniqueSources = new LinkedHashMap<>();
        for (SourceDto source : sources) {
            if (responseReferencesSource(lowerResponse, source)) {
                uniqueSources.putIfAbsent(source.path(), source);
            }
        }
        return new ArrayList<>(uniqueSources.values());
    }

    private List<SourceDto> dedupeSources(List<SourceDto> sources) {
        Map<String, SourceDto> uniqueSources = new LinkedHashMap<>();
        for (SourceDto source : sources) {
            if (source.path() != null) {
                uniqueSources.putIfAbsent(source.path(), source);
            }
        }
        List<SourceDto> filtered = new ArrayList<>(uniqueSources.values());
        log.info("FILTERED SOURCES COUNT: {}", filtered.size());
        return filtered;
    }

    private boolean isFileListRoute(QueryRouter.QueryRoute route) {
        return route == QueryRouter.QueryRoute.ENTITY_FILES || route == QueryRouter.QueryRoute.ENTITY_LIST;
    }

    private boolean isGraphOnlySourceRoute(QueryRouter.QueryRoute route, List<String> contextKeywords) {
        if (route == QueryRouter.QueryRoute.ENTITY_NEIGHBOR
                || route == QueryRouter.QueryRoute.ENTITY_SPATIAL_LEFT
                || route == QueryRouter.QueryRoute.ENTITY_SPATIAL_RIGHT
                || route == QueryRouter.QueryRoute.ENTITY_CO_OCCURRENCE) {
            return true;
        }
        return isPureFileListQuestion(route, contextKeywords);
    }

    private boolean isPureFileListQuestion(QueryRouter.QueryRoute route, List<String> contextKeywords) {
        return isFileListRoute(route) && contextKeywords.isEmpty();
    }

    private boolean responseReferencesSource(String lowerResponse, SourceDto source) {
        if (source.fileName() == null || source.fileName().isBlank()) {
            return false;
        }
        String lowerFileName = source.fileName().toLowerCase(Locale.ROOT);
        if (lowerResponse.contains(lowerFileName)) {
            return true;
        }
        String stem = fileNameStem(lowerFileName);
        return stem.length() >= 8 && lowerResponse.contains(stem);
    }

    private String fileNameStem(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private List<SourceDto> extractGraphSources(String graphContext) {
        if (graphContext == null || graphContext.isBlank()) {
            return List.of();
        }

        Map<String, SourceDto> uniqueSources = new LinkedHashMap<>();
        Matcher matcher = GRAPH_FILE_PATH_PATTERN.matcher(graphContext);
        while (matcher.find()) {
            String path = matcher.group(1).trim();
            String fileName = fileNameFromPath(path);
            uniqueSources.putIfAbsent(path, ingestionService.createSourceDto(path, fileName, 1.0));
        }

        return new ArrayList<>(uniqueSources.values());
    }

    private String fileNameFromPath(String path) {
        String[] parts = path.split("/");
        return parts[parts.length - 1];
    }

    private String cleanUpAiResponse(String aiResponse, List<SourceDto> sources) {
        String cleaned = aiResponse;

        cleaned = DIR_PATH_PATTERN.matcher(cleaned).replaceAll("");

        for (SourceDto s : sources) {
            if (s.path() != null) {
                cleaned = cleaned.replace(s.path(), "");
            }
            if (s.fileName() != null) {
                String fileName = s.fileName();
                String stem = fileNameStem(fileName.toLowerCase(Locale.ROOT));
                cleaned = cleaned.replace("@" + fileName, "");
                cleaned = cleaned.replace(fileName, "");
                if (!stem.isEmpty()) {
                    cleaned = cleaned.replaceAll(
                            "(?m)^[\\-\\*•]?\\s*" + Pattern.quote(stem) + "\\s*$",
                            ""
                    );
                    cleaned = cleaned.replace(stem, "");
                }
            }
        }

        cleaned = cleaned.replaceAll("(?i)na następujących zdjęciach:\\s*", "");
        cleaned = cleaned.replaceAll("(?i)na następujących plikach:\\s*", "");
        cleaned = cleaned.replaceAll("@([\\w\\-]+(?:\\.[a-zA-Z0-9]+)?)", "");
        cleaned = cleaned.replaceAll("(?m)^[\\-\\*•]?\\s*dir://[^\\n]*\\n?", "");
        cleaned = cleaned.replaceAll("(?m)^[\\-\\*•]?\\s*\\d{8}_\\d{6}\\s*$", "");
        cleaned = cleaned.replaceAll(":[/]{2}[^\\s]+\\.(?:jpg|jpeg|png|gif|webp|pdf|txt|docx?)\\b", "");
        cleaned = cleaned.replaceAll(" +", " ");
        cleaned = cleaned.replaceAll("(?m)^[ \\t]*\r?\n", "");
        cleaned = cleaned.replaceAll("\n{3,}", "\n\n");
        cleaned = cleaned.replaceAll("(?m)^[\\-\\*•]\\s*$", "");
        return cleaned.trim();
    }

    private void saveAiMessage(UUID chatId, String textContext, List<SourceDto> sources) {
        List<String> paths = sources.stream().map(SourceDto::path).toList();
        List<Double> scores = sources.stream().map(SourceDto::score).toList();

        ChatMessageEntity aiMsg = ChatMessageEntity.builder()
                .chatId(chatId)
                .role("AI")
                .textContext(textContext)
                .imagePaths(paths)
                .scores(scores)
                .build();

        chatMessageRepository.save(aiMsg);
    }
}
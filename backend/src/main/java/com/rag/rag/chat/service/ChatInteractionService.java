package com.rag.rag.chat.service;

import com.rag.rag.chat.dto.MessageRequest;
import com.rag.rag.chat.dto.MessageResponse;
import com.rag.rag.chat.dto.QueryEvidenceDto;
import com.rag.rag.chat.entity.ChatMessageEntity;
import com.rag.rag.chat.repository.ChatMemoryRepository;
import com.rag.rag.chat.repository.ChatMessageRepository;
import com.rag.rag.ingestion.dto.SourceDto;
import com.rag.rag.ingestion.service.IngestionService;
import com.rag.rag.knowledge.graph.GraphQueryService;
import com.rag.rag.knowledge.graph.PolishNameMatcher;
import com.rag.rag.knowledge.entity.MentionStatus;
import com.rag.rag.knowledge.query.VisualQueryMatch;
import com.rag.rag.knowledge.query.DynamicVisualMatcher;
import dev.langchain4j.service.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
    private final DynamicVisualMatcher dynamicVisualMatcher;

    private static final Pattern GRAPH_FILE_PATH_PATTERN = Pattern.compile("plik: (.*)");
    private static final Pattern DIR_PATH_PATTERN = Pattern.compile("dir://[^\\s\\]\\),]+");
    private static final Pattern PHOTO_COUNT_PATTERN = Pattern.compile("(?i)(\\d+)\\s*zdj");

    @Value("${rag.retrieval.photo-min-score:0.55}")
    private double photoMinScore = 0.55;

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

        if (queryRouter.isExactPhotoListQuestion(question, route)) {
            return processGraphPhotoQuery(chatId, question);
        }
        if (dynamicVisualMatcher != null
                && (route == QueryRouter.QueryRoute.ENTITY_PHOTO_SEARCH
                || route == QueryRouter.QueryRoute.HYBRID)
                && dynamicVisualMatcher.isVisualSelectionQuestion(question)) {
            return processDynamicVisualQuery(chatId, question);
        }

        String fullQuestion = question;
        String graphContext = buildGraphContext(chatId, question, route);
        if (route != QueryRouter.QueryRoute.FILE_SCOPED) {
            String fileGraphContext = buildFileScopedGraphContext(chatId, question);
            if (fileGraphContext != null && !fileGraphContext.isBlank()) {
                graphContext = fileGraphContext + (graphContext.isBlank() ? "" : "\n" + graphContext);
            }
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
        boolean uncertain = isUncertainGraphAnswer(graphContext);
        
        String cleanedResponse = route == QueryRouter.QueryRoute.ENTITY_PHOTO_SEARCH
                ? formatPhotoSearchResponse(
                        filteredSources,
                        hasPhotoQualifier(question, graphQueryService.findEntityNameInQuestion(question)))
                : cleanUpAiResponse(aiResponse, filteredSources);

        String answerKind = route == QueryRouter.QueryRoute.DOCUMENT ? "DOCUMENT"
                : route == QueryRouter.QueryRoute.HYBRID ? "HYBRID" : "GRAPH_QUERY";
        saveAiMessage(chatId, cleanedResponse, filteredSources, List.of(), answerKind, uncertain);

        return new MessageResponse(cleanedResponse, filteredSources, uncertain, List.of(), answerKind);
    }

    private MessageResponse processGraphPhotoQuery(UUID chatId, String question) {
        List<String> entities = orderEntitiesByQuestion(
                graphQueryService.findAllEntityNamesInQuestion(question), question);
        List<GraphQueryService.PhotoMatch> matches = graphQueryService.findPhotoMatchesForEntities(entities);
        List<GraphQueryService.PhotoMatch> confirmed = matches.stream()
                .filter(match -> match.status() == MentionStatus.CONFIRMED).toList();
        List<GraphQueryService.PhotoMatch> suggested = matches.stream()
                .filter(match -> match.status() == MentionStatus.SUGGESTED).toList();

        List<SourceDto> sources = matches.stream()
                .map(match -> ingestionService.createGraphFactSourceDto(
                        match.filePath(), fileNameFromPath(match.filePath()), match.confidence().doubleValue()))
                .toList();
        List<QueryEvidenceDto> evidence = matches.stream()
                .map(match -> new QueryEvidenceDto(
                        match.filePath(), match.confidence(),
                        List.of("Graf potwierdza obecność: " + String.join(", ", match.entityNames())),
                        match.status().name()))
                .toList();

        String names = joinEntityNames(entities);
        String response = formatGraphPhotoResponse(names, entities.size(), confirmed.size(), suggested.size());
        boolean uncertain = !suggested.isEmpty();
        saveAiMessage(chatId, response, sources, evidence, "GRAPH_QUERY", uncertain);
        return new MessageResponse(response, sources, uncertain, evidence,
                matches.isEmpty() ? "NO_EVIDENCE" : "GRAPH_QUERY");
    }

    private String formatGraphPhotoResponse(String names, int entityCount, int confirmed, int suggested) {
        StringBuilder response = new StringBuilder();
        if (entityCount > 1) {
            if (confirmed == 0) {
                response.append("Nie znaleziono potwierdzonych wspólnych zdjęć, na których występują ")
                        .append(names).append(".");
            } else {
                response.append("Znaleziono ").append(confirmed).append(" ")
                        .append(photoWord(confirmed)).append(", na których wspólnie występują ")
                        .append(names).append(".");
            }
        } else {
            if (confirmed == 0) {
                response.append("Nie znaleziono potwierdzonych zdjęć, na których występuje ")
                        .append(names).append(".");
            } else {
                response.append("Znaleziono ").append(confirmed).append(" ")
                        .append(photoWord(confirmed)).append(", na których występuje ")
                        .append(names).append(".");
            }
        }
        if (suggested > 0) {
            response.append(" Dodatkowo znaleziono ").append(suggested).append(" ")
                    .append(photoWord(suggested)).append(" z niepewnym rozpoznaniem.");
        }
        return response.toString();
    }

    private String photoWord(int count) {
        int mod100 = count % 100;
        int mod10 = count % 10;
        return count == 1 ? "zdjęcie"
                : mod10 >= 2 && mod10 <= 4 && !(mod100 >= 12 && mod100 <= 14)
                ? "zdjęcia" : "zdjęć";
    }

    private String joinEntityNames(List<String> names) {
        if (names == null || names.isEmpty()) return "wskazane osoby";
        if (names.size() == 1) return names.get(0);
        if (names.size() == 2) return names.get(0) + " i " + names.get(1);
        return String.join(", ", names.subList(0, names.size() - 1))
                + " i " + names.get(names.size() - 1);
    }

    private List<String> orderEntitiesByQuestion(List<String> entities, String question) {
        return entities.stream()
                .sorted(Comparator.comparingInt(entity -> firstEntityOccurrence(question, entity)))
                .toList();
    }

    private int firstEntityOccurrence(String question, String entity) {
        int first = Integer.MAX_VALUE;
        for (String variant : PolishNameMatcher.generateVariants(entity)) {
            int index = question.toLowerCase(Locale.ROOT).indexOf(variant.toLowerCase(Locale.ROOT));
            if (index >= 0) first = Math.min(first, index);
        }
        return first;
    }

    private MessageResponse processDynamicVisualQuery(UUID chatId, String question) {
        List<VisualQueryMatch> matches = dynamicVisualMatcher.findMatches(question);
        List<SourceDto> sources = matches.stream()
                .map(match -> ingestionService.createSourceDto(match.filePath(), null,
                        match.confidence().doubleValue()))
                .toList();
        List<QueryEvidenceDto> evidence = matches.stream()
                .map(match -> new QueryEvidenceDto(match.filePath(), match.confidence(), match.reasons(), "CONFIRMED"))
                .toList();
        String response = matches.isEmpty()
                ? "Nie znalazłem zdjęcia spełniającego wszystkie warunki pytania."
                : "Znalazłem " + matches.size() + (matches.size() == 1
                    ? " zdjęcie spełniające wszystkie warunki pytania."
                    : " zdjęcia spełniające wszystkie warunki pytania.");
        saveAiMessage(chatId, response, sources, evidence, "GRAPH_QUERY", false);
        return new MessageResponse(response, sources, false, evidence,
                matches.isEmpty() ? "NO_EVIDENCE" : "GRAPH_QUERY");
    }

    private String buildGraphContext(UUID chatId, String question, QueryRouter.QueryRoute route) {
        return switch (route) {
            case ENTITY_NEIGHBOR -> graphQueryService.buildNeighborContextForQuestion(question);
            case ENTITY_SPATIAL_LEFT -> graphQueryService.buildSpatialLeftContextForQuestion(question);
            case ENTITY_SPATIAL_RIGHT -> graphQueryService.buildSpatialRightContextForQuestion(question);
            case ENTITY_FILES, ENTITY_LIST -> graphQueryService.buildFileListContextForQuestion(question);
            case ENTITY_CO_OCCURRENCE -> graphQueryService.buildCoOccurrenceContextForQuestion(question);
            // DynamicVisualMatcher handles semantic conditions. This graph
            // context remains a compatibility fallback for older clients.
            case ENTITY_PHOTO_SEARCH -> graphQueryService.buildFileListContextForQuestion(question);
            case ENTITY_DESCRIPTION -> buildDescriptionContext(chatId, question);
            case ENTITY_ACTIVITY -> buildEntityContext(question);
            case HYBRID -> buildEntityContext(question);
            case FILE_SCOPED -> buildFileScopedGraphContext(chatId, question);
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
        StringBuilder contextBuilder = new StringBuilder();
        for (String imagePath : graphQueryService.resolveImageFilePathsFromQuestion(question)) {
            String imageContext = graphQueryService.buildFullContextForFile(imagePath);
            if (!imageContext.isBlank()) {
                if (!contextBuilder.isEmpty()) {
                    contextBuilder.append("\n");
                }
                contextBuilder.append(imageContext);
            }
        }
        if (!contextBuilder.isEmpty()) {
            return contextBuilder.toString();
        }

        Optional<String> filePath = graphQueryService.resolveFilePathFromQuestion(question);
        if (filePath.isEmpty()) {
            filePath = chatEntityReferenceService.resolveRecentSourceFilePath(chatId);
        }
        return filePath.map(graphQueryService::buildFullContextForFile).orElse("");
    }

    private List<SourceDto> extractSourcesFromResponse(
            Result<String> result,
            String aiResponse,
            String graphContext,
            QueryRouter.QueryRoute route,
            String question
    ) {
        if (route == QueryRouter.QueryRoute.ENTITY_PHOTO_SEARCH) {
            return extractPhotoSearchSources(result, graphContext, question);
        }
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
            if (isGraphOnlySourceRoute(route, contextKeywords) || route == QueryRouter.QueryRoute.FILE_SCOPED) {
                candidates = prioritizeGraphSources(graphSources, retrievalPool, route);
                if (candidates.isEmpty()) {
                    candidates = dedupeSources(graphSources);
                }
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

    private List<SourceDto> extractPhotoSearchSources(
            Result<String> result,
            String graphContext,
            String question
    ) {
        List<SourceDto> graphSources = extractGraphSources(graphContext);
        if (graphSources.isEmpty()) {
            return List.of();
        }

        Optional<String> entityName = graphQueryService.findEntityNameInQuestion(question);
        if (!hasPhotoQualifier(question, entityName)) {
            return dedupeSources(graphSources);
        }

        Set<String> graphPaths = graphSources.stream()
                .map(SourceDto::path)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        List<SourceDto> matching = ingestionService.getSources(result).stream()
                .filter(source -> source.path() != null && graphPaths.contains(source.path()))
                .filter(source -> source.score() != null && source.score() >= photoMinScore)
                .sorted(Comparator.comparingDouble(source -> source.score() == null ? 0.0 : -source.score()))
                .toList();

        return dedupeSources(matching);
    }

    private boolean hasPhotoQualifier(String question, Optional<String> entityName) {
        Set<String> ignored = Set.of(
                "daj", "podaj", "zwróć", "zwroc", "mi", "pokaz", "pokaż", "znajdz", "znajdź", "wyswietl", "wyświetl",
                "zdjęcie", "zdjecie", "zdjęcia", "zdjecia", "foto", "fotkę", "fotke",
                "obraz", "wszystkie", "wszystkim", "pasujące", "pasujace", "jakimś", "jakims", "z"
        );
        Set<String> entityVariants = new HashSet<>();
        entityName.ifPresent(name -> entityVariants.addAll(PolishNameMatcher.generateVariants(name)));
        return PolishNameMatcher.extractEntityTokens(question).stream()
                .filter(token -> !entityVariants.contains(token))
                .anyMatch(token -> !ignored.contains(token));
    }

    private String formatPhotoSearchResponse(List<SourceDto> sources, boolean qualified) {
        int count = sources.size();
        if (count == 0) {
            return "Nie znalazłem zdjęcia pasującego do opisu.";
        }
        if (count == 1) {
            return qualified ? "Znalazłem pasujące zdjęcie." : "Znalazłem 1 zdjęcie.";
        }
        if (!qualified) {
            return "Znalazłem " + count + " zdjęć.";
        }
        return "Znalazłem " + count + (count < 5
                ? " pasujące zdjęcia."
                : " pasujących zdjęć.");
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
                || route == QueryRouter.QueryRoute.ENTITY_SPATIAL_RIGHT
                || route == QueryRouter.QueryRoute.FILE_SCOPED;
    }

    private List<SourceDto> prioritizeGraphSources(
            List<SourceDto> graphSources,
            List<SourceDto> retrievalSources,
            QueryRouter.QueryRoute route
    ) {
        List<SourceDto> graphFirst = dedupeSources(graphSources);
        if (route != QueryRouter.QueryRoute.FILE_SCOPED || retrievalSources.isEmpty()) {
            return graphFirst;
        }

        Set<String> graphPaths = new LinkedHashSet<>();
        for (SourceDto source : graphFirst) {
            if (source.path() != null) {
                graphPaths.add(source.path());
            }
        }

        List<SourceDto> supplements = retrievalSources.stream()
                .filter(source -> source.path() != null && graphPaths.contains(source.path()))
                .toList();
        if (supplements.isEmpty()) {
            return graphFirst;
        }

        Map<String, SourceDto> merged = new LinkedHashMap<>();
        for (SourceDto source : graphFirst) {
            merged.put(source.path(), source);
        }
        for (SourceDto source : supplements) {
            merged.putIfAbsent(source.path(), source);
        }
        return new ArrayList<>(merged.values());
    }

    private boolean isUncertainGraphAnswer(String graphContext) {
        if (graphContext == null || graphContext.isBlank()) {
            return false;
        }
        if (graphContext.contains("status: SUGGESTED")) {
            return true;
        }
        Matcher matcher = Pattern.compile("pewność:\\s*([0-9.]+)").matcher(graphContext);
        double minConfidence = 1.0;
        boolean found = false;
        while (matcher.find()) {
            found = true;
            try {
                minConfidence = Math.min(minConfidence, Double.parseDouble(matcher.group(1)));
            } catch (NumberFormatException ignored) {
            }
        }
        return found && minConfidence < 0.75;
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
            uniqueSources.putIfAbsent(path, ingestionService.createGraphFactSourceDto(path, fileName, 1.0));
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
        saveAiMessage(chatId, textContext, sources, List.of(), "DOCUMENT", false);
    }

    private void saveAiMessage(UUID chatId, String textContext, List<SourceDto> sources,
                               List<QueryEvidenceDto> evidence) {
        saveAiMessage(chatId, textContext, sources, evidence, "DOCUMENT", false);
    }

    private void saveAiMessage(UUID chatId, String textContext, List<SourceDto> sources,
                               List<QueryEvidenceDto> evidence, String answerKind, boolean uncertain) {
        List<String> paths = sources.stream().map(SourceDto::path).toList();
        List<Double> scores = sources.stream().map(SourceDto::score).toList();

        ChatMessageEntity aiMsg = ChatMessageEntity.builder()
                .chatId(chatId)
                .role("AI")
                .textContext(textContext)
                .imagePaths(paths)
                .scores(scores)
                .evidence(evidence)
                .answerKind(answerKind)
                .uncertain(uncertain)
                .build();

        chatMessageRepository.save(aiMsg);
    }
}

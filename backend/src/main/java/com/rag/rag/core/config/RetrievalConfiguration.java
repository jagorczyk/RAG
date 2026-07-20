package com.rag.rag.core.config;

import com.rag.rag.auth.security.CurrentUserService;
import com.rag.rag.core.rerank.LlmDocumentReranker;
import com.rag.rag.core.retrieval.HybridRetrievalMerger;
import com.rag.rag.core.retrieval.LexicalEmbeddingSearch;
import com.rag.rag.core.retrieval.RetrievalPathScope;
import com.rag.rag.core.retrieval.RetrievalQueryContext;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.injector.ContentInjector;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

@Configuration
public class RetrievalConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(RetrievalConfiguration.class);

    @Value("${rag.max-segment-chars:1500}")
    private int maxSegmentChars;

    // Default matches application.properties rag.retrieval.max-results
    @Value("${rag.retrieval.max-results:5}")
    private int maxResults;

    @Value("${rag.retrieval.min-score:0.5}")
    private double minScore;

    @Value("${rag.retrieval.photo-max-results:20}")
    private int photoMaxResults;

    @Value("${rag.retrieval.recall-max-results:40}")
    private int recallMaxResults;

    @Value("${rag.retrieval.graph-max-results:15}")
    private int graphMaxResults;

    @Value("${rag.retrieval.lexical-enabled:true}")
    private boolean lexicalEnabled;

    @Value("${rag.retrieval.lexical-max-results:40}")
    private int lexicalMaxResults;

    @Value("${rag.retrieval.final-min-score:0.25}")
    private double finalMinScore;

    @Bean
    ContentRetriever contentRetriever(
            EmbeddingStore<TextSegment> embeddingStore,
            EmbeddingModel embeddingModel,
            LlmDocumentReranker reranker,
            LexicalEmbeddingSearch lexicalEmbeddingSearch,
            CurrentUserService currentUserService
    ) {

        return query -> {
            String queryText = query.text();
            logger.info("RAG QUERY: {}", queryText);

            if (RetrievalQueryContext.isDisabled()
                    || queryText == null || queryText.trim().isEmpty()) {
                return Collections.emptyList();
            }

            String cleanQuery = RetrievalQueryContext.get();
            if (cleanQuery.isBlank()) {
                cleanQuery = extractRetrievalQuery(queryText);
            }
            if (cleanQuery.isEmpty()) {
                cleanQuery = queryText;
            }
            logger.info("CLEAN QUERY: {}", cleanQuery);

            var queryEmbedding = embeddingModel.embed(cleanQuery).content();
            EmbeddingSearchRequest.EmbeddingSearchRequestBuilder searchRequestBuilder = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(recallMaxResults)
                    .minScore(minScore);
            String ownerId = currentUserService.findUserId().map(Object::toString).orElse(null);
            Filter ownerFilter = ownerId == null ? null : metadataKey("owner_id").isEqualTo(ownerId);
            if (ownerFilter != null) searchRequestBuilder.filter(ownerFilter);

            // Technical @path / @filename tokens only — not phrase routing over people/actions.
            List<String> mentions = new ArrayList<>();
            Matcher matcher = Pattern.compile("@\"([^\"]+)\"|@([^\\s,\\]\\!\\?]+)").matcher(queryText);
            while (matcher.find()) {
                String mention = matcher.group(1) != null ? matcher.group(1).trim() : matcher.group(2).trim();
                mentions.add(mention);
                mentions.add(mention.toLowerCase());
            }

            List<String> pathFilter = new ArrayList<>();
            if (!mentions.isEmpty()) {
                logger.info("DETECTED MENTIONS: {}", mentions);
                Filter pathMetaFilter = metadataKey("path").isIn(mentions);
                Filter filenameFilter = metadataKey("filename").isIn(mentions);
                Filter folderFilter = metadataKey("document_id").isIn(mentions);

                Filter mentionFilter = pathMetaFilter.or(filenameFilter).or(folderFilter);
                searchRequestBuilder.filter(ownerFilter == null ? mentionFilter : ownerFilter.and(mentionFilter));
                searchRequestBuilder.minScore(0.01);
                searchRequestBuilder.maxResults(recallMaxResults);
                pathFilter.addAll(mentions);
            }

            // Plan fileScope / certain graph paths (exact paths and/or folder prefixes) from chat turn.
            // No closed Polish phrase markers — scope is set by ChatInteractionService only.
            List<String> planScope = RetrievalPathScope.get();
            if (!planScope.isEmpty()) {
                logger.info("PLAN FILE SCOPE: {}", planScope);
                pathFilter.clear();
                pathFilter.addAll(planScope);
                List<String> exactPaths = planScope.stream()
                        .filter(p -> RetrievalPathScope.folderPrefix(p).isEmpty())
                        .toList();
                if (!exactPaths.isEmpty() && exactPaths.size() == planScope.size()) {
                    // All exact files — push filter into vector search when possible.
                    Filter exactFilter = metadataKey("path").isIn(exactPaths);
                    searchRequestBuilder.filter(ownerFilter == null ? exactFilter : ownerFilter.and(exactFilter));
                    searchRequestBuilder.minScore(0.01);
                    // Named-entity / joint-file scopes are usually small — prefer graph-sized recall.
                    searchRequestBuilder.maxResults(Math.min(recallMaxResults, Math.max(graphMaxResults, exactPaths.size())));
                }
            }

            EmbeddingSearchResult<TextSegment> vectorResults = embeddingStore.search(searchRequestBuilder.build());
            logger.info("VECTOR RESULTS COUNT: {}", vectorResults.matches().size());
            vectorResults.matches().forEach(match -> logger.info(
                    "VECTOR MATCH: {} FROM {} SCORE: {}",
                    match.embedded().metadata().getString("filename"),
                    match.embedded().metadata().getString("document_id"),
                    match.score()
            ));

            List<Content> vectorContents = vectorResults.matches().stream()
                    .filter(match -> RetrievalPathScope.pathInScope(
                            match.embedded().metadata().getString("path"), planScope))
                    .map(match -> {
                        TextSegment segment = match.embedded();
                        Map<String, Object> metadata = new HashMap<>(segment.metadata().toMap());
                        metadata.put("score", String.valueOf(match.score()));
                        return Content.from(TextSegment.from(segment.text(), Metadata.from(metadata)));
                    })
                    .collect(Collectors.toList());

            List<Content> hybridContents = vectorContents;
            if (lexicalEnabled && lexicalEmbeddingSearch != null) {
                List<LexicalEmbeddingSearch.LexicalHit> lexicalHits = lexicalEmbeddingSearch.search(
                        cleanQuery,
                        lexicalMaxResults,
                        pathFilter.isEmpty() ? List.of() : pathFilter
                );
                logger.info("LEXICAL RESULTS COUNT: {}", lexicalHits.size());
                hybridContents = HybridRetrievalMerger.merge(
                        vectorContents,
                        lexicalHits,
                        Math.max(recallMaxResults, lexicalMaxResults)
                );
                // Enforce plan scope after merge (lexical may expand; folder prefixes already SQL-filtered).
                if (!planScope.isEmpty()) {
                    hybridContents = hybridContents.stream()
                            .filter(content -> RetrievalPathScope.pathInScope(
                                    content.textSegment().metadata().getString("path"), planScope))
                            .collect(Collectors.toList());
                }
                logger.info("HYBRID MERGED COUNT: {}", hybridContents.size());
            }

            List<Content> rerankedContents = reranker.rerank(cleanQuery, hybridContents);
            rerankedContents.forEach(content -> {
                TextSegment segment = content.textSegment();
                logger.info(
                        "FINAL MATCH: {} FROM {} RETRIEVAL_SCORE: {} RERANK_SCORE: {} HYBRID_RRF: {}",
                        segment.metadata().getString("filename"),
                        segment.metadata().getString("document_id"),
                        segment.metadata().getString("retrieval_score"),
                        segment.metadata().getString("rerank_score"),
                        segment.metadata().getString("hybrid_rrf")
                );
            });

            // Wider final cut when the plan scoped to a small set of proven image paths
            // (multi-entity / visual-adjacent hybrid turns); otherwise default maxResults.
            int finalLimit = !planScope.isEmpty() && planScope.size() <= graphMaxResults
                    ? Math.max(maxResults, Math.min(photoMaxResults, planScope.size() * 2))
                    : maxResults;
            return rerankedContents.stream()
                    .filter(content -> RetrievalPathScope.pathInScope(
                            content.textSegment().metadata().getString("path"), planScope))
                    .filter(content -> !planScope.isEmpty()
                            || score(content.textSegment().metadata().getString("score")) >= finalMinScore)
                    .limit(finalLimit)
                    .collect(Collectors.toList());
        };
    }

    @Bean
    public RetrievalAugmentor retrievalAugmentor(ContentRetriever contentRetriever) {
        ContentInjector contentInjector = (contents, query) -> {
            logger.info("CONTENT INJECTOR RECEIVED CONTENTS COUNT: {}", contents.size());
            String userQuestion = query.singleText();
            if (userQuestion == null || userQuestion.trim().isEmpty()) {
                userQuestion = "Pytanie";
            }

            String graphContext = extractGraphContext(userQuestion);
            String actualQuestion = extractUserQuestion(userQuestion);

            if (contents.isEmpty()) {
                if (!graphContext.isEmpty()) {
                    return UserMessage.from(graphContext + "\n\nPytanie użytkownika: " + actualQuestion);
                }
                // No document chunks and no graph block in the query — refuse instead of free-form.
                return UserMessage.from("""
                        Brak fragmentów dokumentów w indeksie dla tego pytania.
                        Odpowiedz dokładnie jednym zdaniem: Nie znaleziono informacji w dokumentach.
                        Nie zgaduj i nie używaj wiedzy spoza systemu.

                        Pytanie użytkownika: %s
                        """.formatted(actualQuestion));
            }

            String contextJoined = contents.stream()
                    .map(content -> {
                        TextSegment segment = content.textSegment();
                        String filename = segment.metadata().getString("filename");
                        String folderName = segment.metadata().getString("document_id");

                        String text = segment.text();
                        if (text.length() > maxSegmentChars) {
                            text = text.substring(0, maxSegmentChars) + "...";
                        }

                        return String.format("[Folder: %s, Plik: %s]\n%s",
                                folderName != null ? folderName : "nieznany",
                                filename != null ? filename : "nieznany",
                                text);
                    })
                    .collect(Collectors.joining("\n---\n"));

            StringBuilder promptBuilder = new StringBuilder();
            if (!graphContext.isEmpty()) {
                promptBuilder.append(graphContext).append("\n\n");
            }
            promptBuilder.append("Odpowiedz po polsku jedną krótką zdaniami (max 1–2 zdania). ")
                    .append("Graf wiedzy (jeśli podany) jest źródłem prawdy dla osób i relacji. ")
                    .append("Gdy pytanie ma dwie części, odpowiedz na obie krótko. ")
                    .append("Używaj wyłącznie dostarczonych dowodów. ")
                    .append("Nie opisuj wyglądu, ubrań, włosów ani sceny, jeśli pytanie prosi tylko o wskazanie zdjęć. ")
                    .append("Nie pisz o pewności ani „na podstawie dowodów”. ")
                    .append("Nie wypisuj nazw plików, ścieżek ani list źródeł — źródła są w UI.\n\n")
                    .append("Pytanie: ").append(actualQuestion).append("\n\n")
                    .append("Dokumenty:\n").append(contextJoined);

            String finalPrompt = promptBuilder.toString();

            logger.info("FINAL PROMPT LENGTH: {}", finalPrompt.length());
            return UserMessage.from(finalPrompt);
        };

        return DefaultRetrievalAugmentor.builder()
                .contentRetriever(contentRetriever)
                .contentInjector(contentInjector)
                .build();
    }

    private static String extractUserQuestion(String queryText) {
        if (queryText == null) {
            return "";
        }
        // Prefer the last marker so nested instruction blocks cannot hide the real question.
        int marker = queryText.lastIndexOf("Pytanie użytkownika:");
        if (marker >= 0) {
            String after = queryText.substring(marker + "Pytanie użytkownika:".length()).trim();
            int docs = after.indexOf("\nDokumenty:");
            if (docs >= 0) {
                after = after.substring(0, docs).trim();
            }
            if (!after.isBlank()) {
                return after;
            }
        }
        int shortMarker = queryText.lastIndexOf("Pytanie:");
        if (shortMarker >= 0) {
            String after = queryText.substring(shortMarker + "Pytanie:".length()).trim();
            int docs = after.indexOf("\nDokumenty:");
            if (docs < 0) {
                docs = after.indexOf("\n\nDokumenty:");
            }
            if (docs >= 0) {
                after = after.substring(0, docs).trim();
            }
            if (!after.isBlank()
                    && !after.contains("Jesteś asystentem dokumentów")
                    && !after.contains("[Styl odpowiedzi]")) {
                return after;
            }
        }
        return queryText.trim();
    }

    private static String extractGraphContext(String queryText) {
        if (queryText == null || !queryText.contains("Pytanie użytkownika:")) {
            return "";
        }
        int marker = queryText.lastIndexOf("Pytanie użytkownika:");
        return queryText.substring(0, marker).trim();
    }

    private static String extractRetrievalQuery(String queryText) {
        String question = extractUserQuestion(queryText);
        return question.replaceAll("@[^\\s,\\]]+", "").trim();
    }

    private static double score(String value) {
        try {
            return value == null ? 0.0 : Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return 0.0;
        }
    }
}

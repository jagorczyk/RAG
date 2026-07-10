package com.rag.rag.core.config;

import com.rag.rag.core.rerank.LlmDocumentReranker;
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

    @Value("${rag.retrieval.max-results:20}")
    private int maxResults;

    @Value("${rag.retrieval.min-score:0.5}")
    private double minScore;

    @Bean
    ContentRetriever contentRetriever(
            EmbeddingStore<TextSegment> embeddingStore,
            EmbeddingModel embeddingModel,
            LlmDocumentReranker reranker
    ) {

        return query -> {
            String queryText = query.text();
            logger.info("RAG QUERY: {}", queryText);

            if (queryText == null || queryText.trim().isEmpty()) {
                return Collections.emptyList();
            }

            String cleanQuery = extractRetrievalQuery(queryText);
            if (cleanQuery.isEmpty()) {
                cleanQuery = queryText;
            }
            logger.info("CLEAN QUERY: {}", cleanQuery);

            var queryEmbedding = embeddingModel.embed(cleanQuery).content();
            EmbeddingSearchRequest.EmbeddingSearchRequestBuilder searchRequestBuilder = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(40)
                    .minScore(minScore);

            List<String> mentions = new ArrayList<>();
            Matcher matcher = Pattern.compile("@\"([^\"]+)\"|@([^\\s,\\]\\!\\?]+)").matcher(queryText);
            while (matcher.find()) {
                String mention = matcher.group(1) != null ? matcher.group(1).trim() : matcher.group(2).trim();
                mentions.add(mention);
                mentions.add(mention.toLowerCase());
            }

            if (!mentions.isEmpty()) {
                logger.info("DETECTED MENTIONS: {}", mentions);
                Filter pathFilter = metadataKey("path").isIn(mentions);
                Filter filenameFilter = metadataKey("filename").isIn(mentions);
                Filter folderFilter = metadataKey("document_id").isIn(mentions);

                searchRequestBuilder.filter(pathFilter.or(filenameFilter).or(folderFilter));
                searchRequestBuilder.minScore(0.01);
                searchRequestBuilder.maxResults(15);
            }

            EmbeddingSearchResult<TextSegment> vectorResults = embeddingStore.search(searchRequestBuilder.build());
            logger.info("VECTOR RESULTS COUNT: {}", vectorResults.matches().size());
            vectorResults.matches().forEach(match -> logger.info(
                    "VECTOR MATCH: {} FROM {} SCORE: {}",
                    match.embedded().metadata().getString("filename"),
                    match.embedded().metadata().getString("document_id"),
                    match.score()
            ));

            List<Content> contents = vectorResults.matches().stream()
                    .map(match -> {
                        TextSegment segment = match.embedded();
                        Map<String, Object> metadata = new HashMap<>(segment.metadata().toMap());
                        metadata.put("score", String.valueOf(match.score()));
                        return Content.from(TextSegment.from(segment.text(), Metadata.from(metadata)));
                    })
                    .collect(Collectors.toList());

            List<Content> rerankedContents = reranker.rerank(cleanQuery, contents);
            rerankedContents.forEach(content -> {
                TextSegment segment = content.textSegment();
                logger.info(
                        "FINAL MATCH: {} FROM {} RETRIEVAL_SCORE: {} RERANK_SCORE: {}",
                        segment.metadata().getString("filename"),
                        segment.metadata().getString("document_id"),
                        segment.metadata().getString("retrieval_score"),
                        segment.metadata().getString("rerank_score")
                );
            });

            return rerankedContents.stream()
                    .limit(maxResults)
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
                return UserMessage.from(actualQuestion);
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
            promptBuilder.append("Odpowiedz na pytanie na podstawie faktów z grafu wiedzy (jeśli podane) ")
                    .append("oraz fragmentów dokumentów poniżej. Odpowiadaj po polsku. ")
                    .append("Nie wypisuj nazw plików, identyfikatorów zdjęć ani list plików w odpowiedzi.\n\n")
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
        int marker = queryText.indexOf("Pytanie użytkownika:");
        if (marker >= 0) {
            return queryText.substring(marker + "Pytanie użytkownika:".length()).trim();
        }
        return queryText.trim();
    }

    private static String extractGraphContext(String queryText) {
        if (queryText == null || !queryText.contains("Pytanie użytkownika:")) {
            return "";
        }
        int marker = queryText.indexOf("Pytanie użytkownika:");
        return queryText.substring(0, marker).trim();
    }

    private static String extractRetrievalQuery(String queryText) {
        String question = extractUserQuestion(queryText);
        return question.replaceAll("@[^\\s,\\]]+", "").trim();
    }
}

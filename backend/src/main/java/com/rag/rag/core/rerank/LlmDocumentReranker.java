package com.rag.rag.core.rerank;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.rag.content.Content;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class LlmDocumentReranker {

    private static final Pattern SCORE_PATTERN = Pattern.compile("(\\d+)\\s*[|:]\\s*(-?\\d+(?:\\.\\d+)?)");

    private final ChatLanguageModel chatLanguageModel;

    @Value("${rag.rerank.enabled:true}")
    private boolean enabled;

    @Value("${rag.rerank.max-candidates:10}")
    private int maxCandidates;

    @Value("${rag.rerank.max-segment-chars:700}")
    private int maxSegmentChars;

    public LlmDocumentReranker(@Qualifier("chatLanguageModel") ChatLanguageModel chatLanguageModel) {
        this.chatLanguageModel = chatLanguageModel;
    }

    public List<Content> rerank(String query, List<Content> candidates) {
        if (!enabled || query == null || query.isBlank() || candidates.size() < 2) {
            return candidates;
        }

        List<Content> rerankCandidates = candidates.stream()
                .limit(Math.max(1, maxCandidates))
                .toList();

        try {
            String prompt = buildPrompt(query, rerankCandidates);
            String response = chatLanguageModel.generate(prompt);
            Map<Integer, Double> scoreByIndex = parseScores(response, rerankCandidates.size());

            if (scoreByIndex.size() != rerankCandidates.size()) {
                log.warn("Reranker returned incomplete scores ({}/{}), keeping original order",
                        scoreByIndex.size(), rerankCandidates.size());
                return candidates;
            }

            List<ScoredContent> scoredContents = new ArrayList<>();
            for (int i = 0; i < rerankCandidates.size(); i++) {
                Content content = rerankCandidates.get(i);
                double rerankScore = scoreByIndex.getOrDefault(i, 0.0);
                scoredContents.add(new ScoredContent(withRerankMetadata(content, rerankScore), rerankScore));
            }

            List<Content> rerankedPrefix = new ArrayList<>(scoredContents.stream()
                    .sorted(Comparator.comparingDouble(ScoredContent::score).reversed())
                    .map(ScoredContent::content)
                    .toList());

            List<Content> remaining = candidates.stream().skip(rerankCandidates.size()).toList();
            rerankedPrefix.addAll(remaining);
            return rerankedPrefix;
        } catch (Exception e) {
            log.warn("Reranker failed, keeping original order: {}", e.getMessage());
            return candidates;
        }
    }

    private String buildPrompt(String query, List<Content> candidates) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("""
                Oceń trafność fragmentów względem pytania użytkownika.
                Zwróć WYŁĄCZNIE linie w formacie:
                indeks|score

                Gdzie:
                - indeks to numer fragmentu
                - score to liczba od 0 do 1
                - 1 oznacza fragment bardzo trafny i potrzebny do odpowiedzi
                - 0 oznacza fragment nietrafny
                - oceń każdy fragment osobno
                - nie dodawaj komentarzy, JSON ani dodatkowego tekstu

                Pytanie:
                """);
        prompt.append(query).append("\n\n");
        prompt.append("Fragmenty:\n");

        for (int i = 0; i < candidates.size(); i++) {
            TextSegment segment = candidates.get(i).textSegment();
            String filename = segment.metadata().getString("filename");
            String folder = segment.metadata().getString("document_id");
            prompt.append("[").append(i).append("]\n");
            prompt.append("Plik: ").append(filename != null ? filename : "nieznany").append("\n");
            prompt.append("Folder: ").append(folder != null ? folder : "nieznany").append("\n");
            prompt.append(trim(segment.text())).append("\n\n");
        }

        return prompt.toString();
    }

    private String trim(String text) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxSegmentChars) {
            return text;
        }
        return text.substring(0, maxSegmentChars) + "...";
    }

    private Map<Integer, Double> parseScores(String response, int candidateCount) {
        Map<Integer, Double> scoreByIndex = new LinkedHashMap<>();
        Matcher matcher = SCORE_PATTERN.matcher(response);

        while (matcher.find()) {
            int index = Integer.parseInt(matcher.group(1));
            double score = Double.parseDouble(matcher.group(2));
            if (index < 0 || index >= candidateCount) {
                continue;
            }
            scoreByIndex.put(index, Math.max(0.0, Math.min(1.0, score)));
        }

        return scoreByIndex;
    }

    private Content withRerankMetadata(Content content, double rerankScore) {
        TextSegment segment = content.textSegment();
        Map<String, Object> metadata = new HashMap<>(segment.metadata().toMap());
        metadata.put("retrieval_score", metadata.getOrDefault("score", "0"));
        metadata.put("rerank_score", String.valueOf(rerankScore));
        // Keep the vector score as the public source score. Rerank score is
        // auxiliary metadata and must not turn missing/partial LLM scores
        // into artificial zero-score sources.
        return Content.from(TextSegment.from(segment.text(), Metadata.from(metadata)));
    }

    private record ScoredContent(Content content, double score) {
    }
}

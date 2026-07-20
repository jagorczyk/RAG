package com.rag.rag.core.retrieval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Lexical (keyword / filename) recall over the embeddings table.
 * Complements semantic vector search for hybrid RAG (AGENTS.md principle 1).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LexicalEmbeddingSearch {

    private static final Pattern TOKEN_SPLIT = Pattern.compile("[^\\p{L}\\p{N}_./-]+");
    private static final Pattern SAFE_TOKEN = Pattern.compile("^[\\p{L}\\p{N}_./-]{2,64}$");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${rag.retrieval.lexical-max-results:40}")
    private int defaultMaxResults = 40;

    @Value("${rag.retrieval.lexical-min-token-length:2}")
    private int minTokenLength = 2;

    public List<LexicalHit> search(String query) {
        return search(query, defaultMaxResults, List.of());
    }

    public List<LexicalHit> search(String query, int maxResults, List<String> pathFilter) {
        if (query == null || query.isBlank() || maxResults <= 0) {
            return List.of();
        }
        List<String> tokens = tokenize(query);
        if (tokens.isEmpty()) {
            return List.of();
        }

        StringBuilder sql = new StringBuilder("""
                SELECT text, metadata::text AS metadata
                FROM embeddings
                WHERE (
                """);
        List<Object> params = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            if (i > 0) {
                sql.append(" OR ");
            }
            sql.append("(LOWER(COALESCE(text, '')) LIKE ?")
                    .append(" OR LOWER(COALESCE(metadata->>'filename', '')) LIKE ?")
                    .append(" OR LOWER(COALESCE(metadata->>'path', '')) LIKE ?)");
            String pattern = "%" + escapeLike(tokens.get(i).toLowerCase(Locale.ROOT)) + "%";
            params.add(pattern);
            params.add(pattern);
            params.add(pattern);
        }
        sql.append(')');

        if (pathFilter != null && !pathFilter.isEmpty()) {
            // Exact paths use =; folder-style entries (trailing slash or no filename extension)
            // use prefix LIKE so HYBRID stays inside plan fileScope.
            sql.append(" AND (");
            for (int i = 0; i < pathFilter.size(); i++) {
                if (i > 0) {
                    sql.append(" OR ");
                }
                String entry = pathFilter.get(i);
                String folderPrefix = RetrievalPathScope.folderPrefix(entry);
                if (!folderPrefix.isEmpty()) {
                    sql.append("metadata->>'path' LIKE ?");
                    params.add(escapeLike(folderPrefix) + "%");
                } else {
                    sql.append("metadata->>'path' = ?");
                    params.add(entry);
                }
            }
            sql.append(')');
        }

        sql.append(" LIMIT ?");
        params.add(Math.max(maxResults * 3, maxResults));

        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());
            List<LexicalHit> scored = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                String text = row.get("text") == null ? "" : String.valueOf(row.get("text"));
                String metadataJson = row.get("metadata") == null ? null : String.valueOf(row.get("metadata"));
                Map<String, String> metadata = parseMetadata(metadataJson);
                double score = scoreHit(tokens, text, metadata);
                if (score > 0) {
                    scored.add(new LexicalHit(text, metadata, score));
                }
            }

            scored.sort((a, b) -> Double.compare(b.score(), a.score()));
            if (scored.size() > maxResults) {
                return List.copyOf(scored.subList(0, maxResults));
            }
            return List.copyOf(scored);
        } catch (Exception e) {
            log.warn("Lexical embedding search failed: {}", e.getMessage());
            return List.of();
        }
    }

    /** Path → best lexical score for image/document candidate recall. */
    public Map<String, Double> recallPaths(String query, int maxResults) {
        Map<String, Double> paths = new LinkedHashMap<>();
        for (LexicalHit hit : search(query, maxResults, List.of())) {
            String path = hit.metadata().get("path");
            if (path == null || path.isBlank()) {
                continue;
            }
            paths.merge(path, hit.score(), Math::max);
        }
        return paths;
    }

    static List<String> tokenize(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        String cleaned = query
                .replaceAll("@\"([^\"]+)\"", "$1")
                .replaceAll("@([^\\s,\\]\\!\\?]+)", "$1");
        String[] parts = TOKEN_SPLIT.split(cleaned.toLowerCase(Locale.ROOT));
        List<String> tokens = new ArrayList<>();
        for (String part : parts) {
            if (part == null) {
                continue;
            }
            String token = part.trim();
            if (token.length() < 2 || !SAFE_TOKEN.matcher(token).matches()) {
                continue;
            }
            if (!tokens.contains(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private double scoreHit(List<String> tokens, String text, Map<String, String> metadata) {
        String haystack = (text == null ? "" : text).toLowerCase(Locale.ROOT);
        String filename = metadata.getOrDefault("filename", "").toLowerCase(Locale.ROOT);
        String path = metadata.getOrDefault("path", "").toLowerCase(Locale.ROOT);
        int matched = 0;
        double boost = 0;
        for (String token : tokens) {
            boolean inText = haystack.contains(token);
            boolean inName = filename.contains(token) || path.contains(token);
            if (inText || inName) {
                matched++;
            }
            if (inName) {
                boost = Math.max(boost, 0.25);
            }
            if (filename.equals(token) || path.endsWith("/" + token)) {
                boost = Math.max(boost, 0.45);
            }
        }
        if (matched == 0) {
            return 0;
        }
        double base = matched / (double) tokens.size();
        return Math.min(1.0, base + boost);
    }

    private Map<String, String> parseMetadata(String metadataJson) {
        Map<String, String> metadata = new LinkedHashMap<>();
        if (metadataJson == null || metadataJson.isBlank()) {
            return metadata;
        }
        try {
            JsonNode root = objectMapper.readTree(metadataJson);
            root.fields().forEachRemaining(entry -> {
                if (entry.getValue() != null && entry.getValue().isValueNode()) {
                    metadata.put(entry.getKey(), entry.getValue().asText(""));
                }
            });
        } catch (Exception ignored) {
            // keep empty metadata
        }
        return metadata;
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    public record LexicalHit(String text, Map<String, String> metadata, double score) {}
}

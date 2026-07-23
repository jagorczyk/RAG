package com.rag.rag.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.rag.knowledge.graph.GraphEvidenceItem;
import com.rag.rag.knowledge.graph.GraphEvidenceResult;
import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.text.BreakIterator;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves UI sources from the evidence that directly supports the final answer.
 * Paths never enter the LLM prompt; the model selects immutable evidence IDs and
 * the server maps those IDs back to owned files.
 */
@Slf4j
@Service
public class GraphSourceAttributionService {

    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]{3,}");

    private final ChatLanguageModel controlModel;
    private final Tokenizer tokenizer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${rag.graph.attribution.max-input-tokens:8192}")
    private int maxInputTokens = 8192;

    @Value("${rag.graph.attribution.lexical-min-score:0.18}")
    private double lexicalMinScore = 0.18;

    @Value("${rag.graph.attribution.lexical-min-margin:0.08}")
    private double lexicalMinMargin = 0.08;

    public GraphSourceAttributionService(
            @Qualifier("attributionLanguageModel") ChatLanguageModel controlModel,
            Tokenizer tokenizer) {
        this.controlModel = controlModel;
        this.tokenizer = tokenizer;
    }

    public Attribution attribute(String question, String finalAnswer, GraphEvidenceResult evidence) {
        return attributeInternal(question, finalAnswer, evidence,
                true, false, Integer.MAX_VALUE, true, false);
    }

    /**
     * Collection overview attribution keeps deterministic aggregate facts grounded without
     * turning every file counted by the catalog into a UI source. Semantic claims still
     * require direct source-backed evidence and must fit the configured representative cap.
     */
    public Attribution attributeCollection(String question, String finalAnswer,
                                           GraphEvidenceResult evidence, int maxSources) {
        return attributeInternal(question, finalAnswer, evidence,
                false, true, Math.max(1, maxSources), false, true);
    }

    private Attribution attributeInternal(String question, String finalAnswer, GraphEvidenceResult evidence,
                                          boolean mapAggregateToAllPaths,
                                          boolean allowAggregateWithoutPaths,
                                          int maxSources,
                                          boolean allowLexicalFallback,
                                          boolean catalogOnly) {
        if (evidence == null || evidence.context().isBlank() || evidence.photos().isEmpty()
                || finalAnswer == null || finalAnswer.isBlank()) {
            return Attribution.unavailable();
        }
        List<GraphEvidenceItem> availableItems = evidence.photos().stream()
                .flatMap(photo -> photo.items().stream())
                .filter(item -> !item.id().isBlank() && !item.statementPl().isBlank())
                // A reduced answer context may contain only a subset of the raw photo items.
                .filter(item -> evidence.context().contains("[" + item.id() + "]"))
                .toList();
        if (availableItems.isEmpty()) {
            return Attribution.unavailable();
        }
        List<AnswerClaim> answerClaims = answerClaims(finalAnswer);
        if (answerClaims.isEmpty()) {
            return Attribution.unavailable();
        }

        int fixedTokens = tokens(question) + tokens(finalAnswer) + 350;
        int itemBudget = Math.max(512, maxInputTokens - fixedTokens);
        List<List<GraphEvidenceItem>> batches = batches(availableItems, itemBudget);
        Map<String, LinkedHashSet<String>> selectedByClaim = new LinkedHashMap<>();
        answerClaims.forEach(claim -> selectedByClaim.put(claim.id(), new LinkedHashSet<>()));
        for (List<GraphEvidenceItem> batch : batches) {
            ParseResult result = selectSupportingIds(question, answerClaims, batch, catalogOnly);
            if (!result.valid()) {
                return allowLexicalFallback
                        ? lexicalFallback(answerClaims, availableItems, "invalid structured attribution")
                        : new Attribution(List.of(), List.of(), true, false);
            }
            result.idsByClaim().forEach((claimId, ids) ->
                    selectedByClaim.get(claimId).addAll(ids));
        }
        boolean incomplete = selectedByClaim.values().stream().anyMatch(Set::isEmpty);
        if (incomplete) {
            // A valid auditor response with an unsupported claim is authoritative.
            // Never promote it through lexical similarity merely to obtain a UI source.
            log.warn("Graph source attribution rejected an answer with unsupported claim(s)");
            return new Attribution(List.of(), List.of(), true, false);
        }

        LinkedHashSet<String> selectedIds = selectedByClaim.values().stream()
                .flatMap(Set::stream)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        LinkedHashSet<String> paths = new LinkedHashSet<>();
        availableItems.stream()
                .filter(item -> selectedIds.contains(item.id()))
                .map(GraphEvidenceItem::sourcePath)
                .filter(path -> path != null && !path.isBlank())
                .forEach(paths::add);

        // Aggregate facts (for example a global photo count) have no single path.
        // All certain input paths are their provenance and are preferable to a random subset.
        boolean aggregateSelected = availableItems.stream()
                .anyMatch(item -> selectedIds.contains(item.id())
                        && (item.sourcePath() == null || item.sourcePath().isBlank()));
        if (aggregateSelected && mapAggregateToAllPaths) {
            evidence.certainPaths().stream()
                    .filter(path -> path != null && !path.isBlank())
                    .forEach(paths::add);
        }
        if (paths.isEmpty()) {
            if (aggregateSelected && allowAggregateWithoutPaths) {
                return new Attribution(List.of(), List.copyOf(selectedIds), true, true);
            }
            return allowLexicalFallback
                    ? lexicalFallback(answerClaims, availableItems, "empty structured attribution")
                    : new Attribution(List.of(), List.copyOf(selectedIds), true, false);
        }
        if (paths.size() > maxSources) {
            log.warn("Collection source attribution exceeded representative source cap: paths={}, cap={}",
                    paths.size(), maxSources);
            return new Attribution(List.of(), List.copyOf(selectedIds), true, false);
        }
        log.info("Graph source attribution: evidenceItems={}, supportingItems={}, sourcePaths={}",
                availableItems.size(), selectedIds.size(), paths.size());
        return new Attribution(List.copyOf(paths), List.copyOf(selectedIds), true, true);
    }

    private Attribution lexicalFallback(List<AnswerClaim> claims, List<GraphEvidenceItem> items,
                                        String reason) {
        if (claims == null || claims.isEmpty()) {
            return new Attribution(List.of(), List.of(), true, false);
        }
        Map<String, List<GraphEvidenceItem>> itemsByPath = new java.util.LinkedHashMap<>();
        for (GraphEvidenceItem item : items) {
            if (item.sourcePath() != null && !item.sourcePath().isBlank()) {
                itemsByPath.computeIfAbsent(item.sourcePath(), ignored -> new ArrayList<>()).add(item);
            }
        }
        if (itemsByPath.isEmpty()) {
            return new Attribution(List.of(), List.of(), true, false);
        }

        Map<String, Set<String>> documentTerms = new LinkedHashMap<>();
        itemsByPath.forEach((path, pathItems) -> documentTerms.put(path, pathItems.stream()
                .map(GraphEvidenceItem::statementPl)
                .map(this::terms)
                .flatMap(Set::stream)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new))));
        LinkedHashSet<String> paths = new LinkedHashSet<>();
        LinkedHashSet<String> supportingIds = new LinkedHashSet<>();
        double weakestScore = 1.0;
        double weakestMargin = 1.0;

        for (AnswerClaim claim : claims) {
            Set<String> claimTerms = terms(claim.text());
            if (claimTerms.isEmpty()) {
                continue;
            }
            Map<String, Integer> documentFrequency = new HashMap<>();
            claimTerms.forEach(term -> documentFrequency.put(term, (int) documentTerms.values().stream()
                    .filter(values -> values.contains(term)).count()));

            int documentCount = documentTerms.size();
            double denominator = claimTerms.stream()
                    .mapToDouble(term -> inverseDocumentWeight(
                            documentCount, documentFrequency.getOrDefault(term, 0)))
                    .sum();
            List<PathScore> ranking = documentTerms.entrySet().stream()
                    .map(entry -> new PathScore(entry.getKey(), claimTerms.stream()
                            .filter(entry.getValue()::contains)
                            .mapToDouble(term -> inverseDocumentWeight(
                                    documentCount, documentFrequency.getOrDefault(term, 0)))
                            .sum() / Math.max(1.0, denominator)))
                    .sorted((left, right) -> Double.compare(right.score(), left.score()))
                    .toList();
            PathScore best = ranking.get(0);
            double secondScore = ranking.size() > 1 ? ranking.get(1).score() : 0.0;
            double margin = best.score() - secondScore;
            weakestScore = Math.min(weakestScore, best.score());
            weakestMargin = Math.min(weakestMargin, margin);
            if (best.score() < lexicalMinScore || margin < lexicalMinMargin) {
                log.warn("Graph source attribution has no confident fallback: reason={}, claim={}, bestScore={}, margin={}",
                        reason, claim.id(), best.score(), margin);
                return new Attribution(List.of(), List.of(), true, false);
            }

            paths.add(best.path());
            itemsByPath.getOrDefault(best.path(), List.of()).stream()
                    .filter(item -> terms(item.statementPl()).stream().anyMatch(claimTerms::contains))
                    .map(GraphEvidenceItem::id)
                    .forEach(supportingIds::add);
        }
        if (paths.isEmpty() || supportingIds.isEmpty()) {
            return new Attribution(List.of(), List.of(), true, false);
        }
        log.info("Graph source attribution used lexical fallback: reason={}, weakestScore={}, weakestMargin={}, pathCount={}",
                reason, weakestScore, weakestMargin, paths.size());
        return new Attribution(List.copyOf(paths), List.copyOf(supportingIds), true, true);
    }

    private Set<String> terms(String value) {
        if (value == null || value.isBlank()) return Set.of();
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
        Matcher matcher = WORD.matcher(normalized);
        LinkedHashSet<String> result = new LinkedHashSet<>();
        while (matcher.find()) {
            String term = matcher.group();
            // A small language-neutral prefix stem handles Polish inflection without vocabularies.
            result.add(term.length() > 5 ? term.substring(0, 5) : term);
        }
        return result;
    }

    private static double inverseDocumentWeight(int documentCount, int documentFrequency) {
        return Math.log((documentCount + 1.0) / (documentFrequency + 0.5)) + 1.0;
    }

    private ParseResult selectSupportingIds(String question, List<AnswerClaim> claims,
                                            List<GraphEvidenceItem> items,
                                            boolean catalogOnly) {
        String evidenceBlock = items.stream().map(GraphEvidenceItem::render)
                .collect(java.util.stream.Collectors.joining("\n"));
        String claimsBlock = claims.stream().map(AnswerClaim::render)
                .collect(java.util.stream.Collectors.joining("\n"));
        String prompt = """
                Jesteś deterministycznym audytorem źródeł GraphRAG. Wskaż najmniejszy zestaw
                dowodów, które BEZPOŚREDNIO potwierdzają WSZYSTKIE konkretne informacje zawarte
                w każdym ponumerowanym fragmencie gotowej odpowiedzi. Jeśli odpowiedź opisuje
                kilka zdjęć lub scen, dowody muszą pokryć każdą z nich. Nie wybieraj zdjęcia tylko
                dlatego, że występuje na nim ta sama osoba.
                Nie wybieraj faktów podobnych, ale dotyczących innej sceny, czynności lub ubioru.
                Nie twórz odpowiedzi ani nowych faktów.
                %s

                Zwróć wyłącznie JSON:
                {"claims":[{"claimId":"C1","fullySupported":true,"supportingEvidenceIds":["E2.3"]}]}.
                fullySupported=true ONLY when the selected evidence directly supports EVERY factual
                detail in that claim. If any detail, evaluation, emotion, intention or atmosphere
                is absent from the evidence, return fullySupported=false and an empty ID list.
                Topic similarity or support for only part of a sentence is not full support.
                Zwroty porządkujące odpowiedź, takie jak "na pierwszym", "na drugim" lub
                "na innym zdjęciu", nie są osobnymi faktami o zawartości obrazu i nie wymagają
                dowodu. Wszystkie obserwacje opisane po takim zwrocie nadal wymagają pełnego wsparcia.
                Zwróć dokładnie jeden element dla każdego claimId z wejścia, w tej samej kolejności.
                Użyj wyłącznie identyfikatorów dowodów z wejścia. Pusta lista dla danego claimId
                jest poprawna tylko wtedy, gdy żaden dowód w tej partii go nie podpiera.

                Pytanie: %s
                Fragmenty gotowej odpowiedzi:
                %s
                Dowody:
                %s
                """.formatted(catalogOnly ? """
                        W trybie inwentarza dowody INVENTORY potwierdzają wyłącznie zapisane liczby,
                        nazwy folderów, kanoniczne imiona, nazwy plików i statusy. Nazwa pliku jest
                        tylko identyfikatorem i nigdy nie potwierdza jego treści, sceny ani obiektów.
                        """ : "", question == null ? "" : question, claimsBlock, evidenceBlock);
        ParseResult parsed = parse(generate(prompt), claims, items);
        if (parsed.valid()) return parsed;
        String repairPrompt = """
                Zwróć sam poprawny JSON w schemacie
                {"claims":[{"claimId":"C1","fullySupported":false,"supportingEvidenceIds":[]}]}.
                Include the boolean fullySupported for every claim. It may be true only when every
                detail is directly present in the supplied evidence.
                %s
                Musi istnieć dokładnie jeden element dla każdego claimId z wejścia.
                Wartości supportingEvidenceIds mogą być wyłącznie identyfikatorami z dowodów.
                Pytanie: %s
                Fragmenty gotowej odpowiedzi:
                %s
                Dowody:
                %s
                """.formatted(catalogOnly
                        ? "Nazwy plików są wyłącznie identyfikatorami i nie dowodzą zawartości pliku."
                        : "", question == null ? "" : question, claimsBlock, evidenceBlock);
        return parse(generate(repairPrompt), claims, items);
    }

    private String generate(String prompt) {
        try {
            return controlModel == null ? "" : controlModel.generate(prompt);
        } catch (Exception e) {
            log.warn("Graph source attribution model failed: {}", e.getMessage());
            return "";
        }
    }

    private ParseResult parse(String response, List<AnswerClaim> claims,
                              List<GraphEvidenceItem> allowedItems) {
        if (response == null || response.isBlank()) return ParseResult.invalid();
        try {
            int start = response.indexOf('{');
            int end = response.lastIndexOf('}');
            String json = start >= 0 && end > start ? response.substring(start, end + 1) : response;
            JsonNode claimsNode = objectMapper.readTree(json).path("claims");
            if (!claimsNode.isArray()) return ParseResult.invalid();
            Set<String> allowed = allowedItems.stream().map(GraphEvidenceItem::id)
                    .collect(java.util.stream.Collectors.toSet());
            Set<String> allowedClaims = claims.stream().map(AnswerClaim::id)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            Map<String, List<String>> idsByClaim = new LinkedHashMap<>();
            for (JsonNode claimNode : claimsNode) {
                String claimId = claimNode.path("claimId").asText("").trim();
                JsonNode idsNode = claimNode.path("supportingEvidenceIds");
                JsonNode fullySupportedNode = claimNode.path("fullySupported");
                if (!allowedClaims.contains(claimId) || idsByClaim.containsKey(claimId)
                        || !idsNode.isArray() || !fullySupportedNode.isBoolean()) {
                    return ParseResult.invalid();
                }
                LinkedHashSet<String> ids = new LinkedHashSet<>();
                for (JsonNode idNode : idsNode) {
                    if (!idNode.isTextual()) return ParseResult.invalid();
                    String id = idNode.asText("").trim();
                    if (!allowed.contains(id)) return ParseResult.invalid();
                    ids.add(id);
                }
                boolean fullySupported = fullySupportedNode.asBoolean(false);
                if ((fullySupported && ids.isEmpty()) || (!fullySupported && !ids.isEmpty())) {
                    return ParseResult.invalid();
                }
                idsByClaim.put(claimId, List.copyOf(ids));
            }
            if (!idsByClaim.keySet().equals(allowedClaims)) return ParseResult.invalid();
            return new ParseResult(Map.copyOf(idsByClaim), true);
        } catch (Exception ignored) {
            return ParseResult.invalid();
        }
    }

    /**
     * Keeps attribution exhaustive without turning it into intent routing. Every sentence is
     * audited separately so one supported observation cannot hide an unsupported follow-up.
     */
    private List<AnswerClaim> answerClaims(String answer) {
        if (answer == null || answer.isBlank()) return List.of();
        BreakIterator iterator = BreakIterator.getSentenceInstance(Locale.forLanguageTag("pl-PL"));
        iterator.setText(answer.trim());
        List<String> sentences = new ArrayList<>();
        int start = iterator.first();
        for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
            String sentence = answer.substring(start, end).trim();
            if (!sentence.isBlank()) sentences.add(sentence);
        }
        if (sentences.isEmpty()) sentences.add(answer.trim());

        List<AnswerClaim> claims = new ArrayList<>();
        for (String sentence : sentences) {
            claims.add(new AnswerClaim("C" + (claims.size() + 1), sentence));
        }
        return List.copyOf(claims);
    }

    private List<List<GraphEvidenceItem>> batches(List<GraphEvidenceItem> items, int tokenLimit) {
        List<List<GraphEvidenceItem>> result = new ArrayList<>();
        List<GraphEvidenceItem> current = new ArrayList<>();
        int currentTokens = 0;
        for (GraphEvidenceItem item : items) {
            int itemTokens = Math.max(1, tokens(item.render()));
            if (!current.isEmpty() && currentTokens + itemTokens > tokenLimit) {
                result.add(List.copyOf(current));
                current.clear();
                currentTokens = 0;
            }
            current.add(item);
            currentTokens += itemTokens;
        }
        if (!current.isEmpty()) result.add(List.copyOf(current));
        return result;
    }

    private int tokens(String value) {
        return value == null || value.isBlank() ? 0 : tokenizer.estimateTokenCountInText(value);
    }

    public record Attribution(List<String> paths, List<String> evidenceIds,
                              boolean attempted, boolean reliable) {
        public Attribution {
            paths = paths == null ? List.of() : List.copyOf(paths);
            evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
        }

        public static Attribution unavailable() {
            return new Attribution(List.of(), List.of(), false, false);
        }
    }

    private record AnswerClaim(String id, String text) {
        private String render() {
            return "[" + id + "] " + text;
        }
    }

    private record ParseResult(Map<String, List<String>> idsByClaim, boolean valid) {
        private static ParseResult invalid() {
            return new ParseResult(Map.of(), false);
        }
    }

    private record PathScore(String path, double score) {
    }
}

package com.rag.rag.knowledge.query;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.rag.folder.entity.FileEntity;
import com.rag.rag.folder.repository.FileRepository;
import com.rag.rag.knowledge.graph.GraphQueryService;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import com.rag.rag.knowledge.graph.PolishNameMatcher;

/**
 * Matches a natural-language visual condition against the actual image
 * context. User vocabulary is intentionally never expanded through a static
 * domain dictionary.
 */
@Slf4j
@Service
public class DynamicVisualMatcher {
    private final FileRepository fileRepository;
    private final GraphQueryService graphQueryService;
    private final ChatLanguageModel chatModel;
    private final ChatLanguageModel visionModel;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${rag.visual-match.context-accept-confidence:0.80}")
    private double contextAcceptConfidence = 0.80;

    @Value("${rag.visual-match.vision-accept-confidence:0.70}")
    private double visionAcceptConfidence = 0.70;

    @Value("${rag.visual-match.max-context-chars:5000}")
    private int maxContextChars = 5000;

    public DynamicVisualMatcher(
            FileRepository fileRepository,
            GraphQueryService graphQueryService,
            @Qualifier("chatLanguageModel") ChatLanguageModel chatModel,
            @Qualifier("visionModel") ChatLanguageModel visionModel
    ) {
        this.fileRepository = fileRepository;
        this.graphQueryService = graphQueryService;
        this.chatModel = chatModel;
        this.visionModel = visionModel;
    }

    public boolean isVisualSelectionQuestion(String question) {
        if (question == null || question.isBlank()) return false;
        String q = normalize(question);
        boolean image = containsAny(q, "zdjec", "foto", "obraz", "plik", "fotograf");
        boolean selection = containsAny(q, "daj", "podaj", "pokaz", "znajdz", "wyszukaj", "szukaj", "odnajdz", "wyswietl",
                "ktorym", "ktorych", "na jakim", "na ktorym", "gdzie", "czy",
                " obok ", " z ", " ze ");
        boolean knownEntity = !graphQueryService.findAllEntityNamesInQuestion(question).isEmpty();
        boolean hasQualifier = containsAny(q, " w ", " na ", " przy ", " obok ", " z ", " ze ");
        return image && (selection || (knownEntity && hasQualifier));
    }

    public List<VisualQueryMatch> findMatches(String question) {
        if (!isVisualSelectionQuestion(question) || chatModel == null) return List.of();
        List<FileEntity> candidates = findCandidates(question);
        List<VisualQueryMatch> matches = new ArrayList<>();
        for (FileEntity file : candidates) {
            VisualMatchDecision contextDecision = evaluateStoredContext(question, file);
            VisualMatchDecision finalDecision = contextDecision;
            if (needsVisionVerification(contextDecision, file)) {
                finalDecision = evaluateImage(question, file, contextDecision);
            }
            if (finalDecision.decision() == VisualMatchDecision.Decision.MATCH
                    && finalDecision.confidence().doubleValue() >= visionAcceptConfidence) {
                matches.add(new VisualQueryMatch(file.getPath(), finalDecision.confidence(), finalDecision.reasons()));
            }
        }
        matches.sort(Comparator.comparing(VisualQueryMatch::confidence, Comparator.reverseOrder())
                .thenComparing(VisualQueryMatch::filePath));
        return matches;
    }

    private List<FileEntity> findCandidates(String question) {
        LinkedHashSet<String> paths = new LinkedHashSet<>(graphQueryService.resolveImageFilePathsFromQuestion(question));
        if (paths.isEmpty()) {
            List<String> entities = graphQueryService.findAllEntityNamesInQuestion(question);
            if (entities.size() > 1) {
                // A semantic condition over multiple people must be evaluated
                // only on files where all people co-occur, not on the union
                // of their individual photo sets.
                graphQueryService.findPhotoMatchesForEntities(entities)
                        .forEach(match -> paths.add(match.filePath()));
            } else {
                for (String entity : entities) {
                    for (String variant : PolishNameMatcher.generateVariants(entity)) {
                        graphQueryService.getFilesForEntity(variant).forEach(row -> paths.add(row.filePath()));
                    }
                }
            }
        }

        List<FileEntity> files;
        if (paths.isEmpty()) {
            files = fileRepository.findAll();
        } else {
            files = paths.stream().map(fileRepository::findByPath).flatMap(Optional::stream).toList();
        }
        return files.stream()
                .filter(this::isImage)
                .sorted(Comparator.comparing(FileEntity::getPath, Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    private VisualMatchDecision evaluateStoredContext(String question, FileEntity file) {
        String context = graphQueryService.buildFullContextForFile(file.getPath());
        if (context == null) context = "";
        context = trim(context);
        if (context.isBlank()) {
            return uncertain("Brak zapisanego kontekstu obrazu");
        }
        String prompt = """
                Oceń, czy opisane zdjęcie spełnia dokładnie warunek z pytania użytkownika.
                Możesz używać rozumowania semantycznego (np. opis stroju może wskazywać rolę osoby),
                ale wolno Ci opierać się wyłącznie na podanym kontekście. Nie wymagaj identycznych słów.
                Jeżeli kontekst nie zawiera wystarczających dowodów, zwróć UNCERTAIN, nie NO_MATCH.
                Zwróć wyłącznie JSON: {"decision":"MATCH|NO_MATCH|UNCERTAIN","confidence":0.0,
                "reasons":["..."],"missingEvidence":["..."]}.

                Pytanie użytkownika:
                %s

                Plik: %s
                Kontekst zdjęcia:
                %s
                """.formatted(question, file.getPath(), context);
        return parseDecision(generateText(prompt), "Nie udało się ocenić kontekstu");
    }

    private VisualMatchDecision evaluateImage(String question, FileEntity file, VisualMatchDecision previous) {
        if (visionModel == null || file.getImageData() == null || file.getImageData().length == 0) return previous;
        String identityContext = graphQueryService.buildFullContextForFile(file.getPath());
        String prompt = """
                Odpowiedz, czy to konkretne zdjęcie spełnia warunek pytania użytkownika.
                Uwzględnij relacje, wygląd, czynności, obiekty, napisy i kontekst sceny.
                Nie zgaduj tożsamości osoby; użyj mapowania z kontekstu, jeśli istnieje.
                Zwróć wyłącznie JSON: {"decision":"MATCH|NO_MATCH|UNCERTAIN","confidence":0.0,
                "reasons":["..."],"missingEvidence":["..."]}.

                Pytanie użytkownika:
                %s

                Zapisany kontekst pomocniczy:
                %s
                """.formatted(question, trim(identityContext == null ? "" : identityContext));
        try {
            String mime = file.getFileType() == null ? "image/jpeg" : file.getFileType();
            String response = visionModel.generate(UserMessage.from(
                    TextContent.from(prompt), ImageContent.from(Base64.getEncoder().encodeToString(file.getImageData()), mime)
            )).content().text();
            return parseDecision(response, "Weryfikacja obrazu nie dała rozstrzygnięcia");
        } catch (Exception e) {
            log.warn("Visual verification failed for {}: {}", file.getPath(), e.getMessage());
            return previous;
        }
    }

    private boolean needsVisionVerification(VisualMatchDecision decision, FileEntity file) {
        return decision.decision() == VisualMatchDecision.Decision.UNCERTAIN
                || decision.confidence().doubleValue() < contextAcceptConfidence
                || file.getImageData() == null || file.getImageData().length == 0;
    }

    private VisualMatchDecision parseDecision(String response, String fallbackReason) {
        try {
            String json = extractJson(response);
            JsonNode root = objectMapper.readTree(json);
            VisualMatchDecision.Decision decision = VisualMatchDecision.Decision.valueOf(
                    root.path("decision").asText("UNCERTAIN").toUpperCase(Locale.ROOT));
            BigDecimal confidence = BigDecimal.valueOf(Math.max(0, Math.min(1, root.path("confidence").asDouble(0))));
            return new VisualMatchDecision(decision, confidence, readStrings(root.path("reasons")),
                    readStrings(root.path("missingEvidence")));
        } catch (Exception e) {
            return uncertain(fallbackReason);
        }
    }

    private String generateText(String prompt) {
        try {
            return chatModel.generate(prompt);
        } catch (Exception e) {
            log.warn("Stored visual context evaluation failed: {}", e.getMessage());
            return "";
        }
    }

    private String extractJson(String response) {
        if (response == null) return "{}";
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        return start >= 0 && end > start ? response.substring(start, end + 1) : response;
    }

    private List<String> readStrings(JsonNode node) {
        if (!node.isArray()) return List.of();
        List<String> values = new ArrayList<>();
        node.forEach(value -> { if (value.isTextual() && !value.asText().isBlank()) values.add(value.asText()); });
        return values;
    }

    private VisualMatchDecision uncertain(String reason) {
        return new VisualMatchDecision(VisualMatchDecision.Decision.UNCERTAIN, BigDecimal.ZERO, List.of(), List.of(reason));
    }

    private String trim(String value) {
        return value.length() <= maxContextChars ? value : value.substring(0, maxContextChars) + "...";
    }

    private boolean isImage(FileEntity file) {
        return file.getFileType() != null && file.getFileType().toLowerCase(Locale.ROOT).contains("image");
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) if (value.contains(candidate)) return true;
        return false;
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replace("ą", "a").replace("ć", "c").replace("ę", "e").replace("ł", "l")
                .replace("ń", "n").replace("ó", "o").replace("ś", "s").replace("ź", "z").replace("ż", "z");
    }
}

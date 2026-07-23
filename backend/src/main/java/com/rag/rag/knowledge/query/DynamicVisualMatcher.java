package com.rag.rag.knowledge.query;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.rag.chat.service.QueryPlan;
import com.rag.rag.folder.entity.FileEntity;
import com.rag.rag.folder.repository.FileRepository;
import com.rag.rag.knowledge.graph.EntityMatchMode;
import com.rag.rag.knowledge.graph.GraphQueryService;
import com.rag.rag.knowledge.graph.GroundedVisualClaim;
import com.rag.rag.knowledge.graph.EntityVisualAnchor;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Evaluates only dynamically planned visual conditions against ranked image evidence. */
@Slf4j
@Service
public class DynamicVisualMatcher {
    private final FileRepository fileRepository;
    private final GraphQueryService graphQueryService;
    private final ChatLanguageModel chatModel;
    private final ChatLanguageModel visionModel;
    private final ImageCandidateRetriever candidateRetriever;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${rag.visual-match.context-accept-confidence:0.80}")
    private double contextAcceptConfidence = 0.80;
    @Value("${rag.visual-match.vision-accept-confidence:0.70}")
    private double visionAcceptConfidence = 0.70;
    @Value("${rag.visual-match.max-context-chars:5000}")
    private int maxContextChars = 5000;
    @Value("${rag.visual-match.max-candidates:50}")
    private int maxCandidates = 50;
    @Value("${rag.visual-match.max-vision-analyses:12}")
    private int maxVisionAnalyses = 12;

    public DynamicVisualMatcher(FileRepository fileRepository, GraphQueryService graphQueryService,
                                @Qualifier("structuredControlLanguageModel") ChatLanguageModel chatModel,
                                @Qualifier("visionModel") ChatLanguageModel visionModel,
                                ImageCandidateRetriever candidateRetriever) {
        this.fileRepository = fileRepository;
        this.graphQueryService = graphQueryService;
        this.chatModel = chatModel;
        this.visionModel = visionModel;
        this.candidateRetriever = candidateRetriever;
    }

    public List<VisualQueryMatch> findEvidence(QueryPlan plan) {
        if (plan == null || !plan.visualCondition() || chatModel == null) return List.of();
        String condition = plan.condition().isBlank() ? plan.question() : plan.condition();
        List<VisualQueryMatch> evidence = new ArrayList<>();
        int visionAnalyses = 0;
        boolean exactFileQuestion = plan.fileScope() != null && !plan.fileScope().isEmpty();
        for (Candidate candidate : findCandidates(plan)) {
            VisualMatchDecision decision = evaluateStoredContext(condition, candidate.file(), plan);
            boolean verifyPixels = !exactFileQuestion || needsVisionVerification(decision, candidate.file());
            if (verifyPixels && visionAnalyses++ < maxVisionAnalyses) {
                decision = evaluateImage(condition, candidate.file(), decision, plan.entities());
            }
            if (decision.decision() == VisualMatchDecision.Decision.MATCH
                    && decision.confidence().doubleValue() >= visionAcceptConfidence
                    && !decision.claims().isEmpty()) {
                evidence.add(new VisualQueryMatch(candidate.file().getPath(), decision.confidence(), decision.reasons(),
                        VisualMatchDecision.Decision.MATCH, decision.missingEvidence(),
                        candidate.retrievalScore(), candidate.entityConfidence(), decision.claims()));
            }
        }
        return evidence.stream().sorted(Comparator.comparing(VisualQueryMatch::confidence).reversed()
                .thenComparing(VisualQueryMatch::retrievalScore, Comparator.reverseOrder())).toList();
    }

    private List<Candidate> findCandidates(QueryPlan plan) {
        Map<String, Candidate> candidates = new LinkedHashMap<>();
        boolean requireJointFile = plan.entityMatchMode() == EntityMatchMode.ALL_SAME_FILE
                && plan.entities() != null && !plan.entities().isEmpty();
        // A user-selected @file is a hard scope, never merely a ranking boost.
        if (plan.fileScope() != null && !plan.fileScope().isEmpty()) {
            Set<String> jointAllowed = requireJointFile
                    ? new LinkedHashSet<>(graphQueryService.imagePathsForAllEntities(plan.entities()))
                    : null;
            for (String path : plan.fileScope()) {
                if (jointAllowed != null && !jointAllowed.contains(path)) continue;
                add(candidates, path, BigDecimal.ONE,
                        graphQueryService.entityConfidenceForFile(plan.entities(), path));
            }
            return candidates.values().stream()
                    .filter(candidate -> isImage(candidate.file()))
                    .sorted(Comparator.comparing(candidate -> candidate.file().getPath()))
                    .limit(Math.max(0, maxCandidates))
                    .toList();
        }
        // Intersection (not union) when co-presence of all named entities is required.
        List<String> entityPaths = requireJointFile
                ? graphQueryService.imagePathsForAllEntities(plan.entities())
                : graphQueryService.imagePathsForEntities(plan.entities());
        Set<String> jointAllowed = requireJointFile
                ? new LinkedHashSet<>(entityPaths)
                : null;
        for (String path : entityPaths) {
            add(candidates, path, BigDecimal.ZERO, graphQueryService.entityConfidenceForFile(plan.entities(), path));
        }
        candidateRetriever.recall(plan.retrievalQuery()).forEach((path, score) -> {
            if (jointAllowed != null && !jointAllowed.contains(path)) return;
            add(candidates, path, score, graphQueryService.entityConfidenceForFile(plan.entities(), path));
        });
        return candidates.values().stream().filter(candidate -> isImage(candidate.file()))
                .sorted(Comparator.comparing(Candidate::rank).reversed()
                        .thenComparing(candidate -> candidate.file().getPath()))
                .limit(Math.max(0, maxCandidates)).toList();
    }

    private void add(Map<String, Candidate> candidates, String path, BigDecimal retrievalScore, BigDecimal entityConfidence) {
        fileRepository.findByPath(path).ifPresent(file -> candidates.merge(path,
                new Candidate(file, retrievalScore, entityConfidence), Candidate::merge));
    }

    private VisualMatchDecision evaluateStoredContext(String condition, FileEntity file, QueryPlan plan) {
        List<GroundedVisualClaim> claims = graphQueryService.certainClaimsForFile(file.getPath(), plan.entities());
        if (!plan.entities().isEmpty() && claims.isEmpty()) {
            return uncertain("Brak pewnego faktu przypisanego do wskazanej osoby");
        }
        String context = trim(graphQueryService.buildFullContextForFile(file.getPath()));
        if (context.isBlank()) return uncertain("No stored visual evidence");
        String prompt = """
                Evaluate whether this image evidence proves the requested condition. Bind every claimed property to
                the entity identified by the supplied evidence. Do not use knowledge not present in the evidence.
                Write reasons and missingEvidence in Polish.
                Return JSON only: {"decision":"MATCH|NO_MATCH|UNCERTAIN","confidence":0.0,
                "reasons":["..."],"missingEvidence":["..."]}.
                Condition: %s
                Certain claims: %s
                File evidence: %s
                """.formatted(condition, claims, context);
        VisualMatchDecision parsed = parseDecision(generateText(prompt), "Stored evidence could not be evaluated");
        if (parsed.decision() != VisualMatchDecision.Decision.MATCH) return parsed;
        return new VisualMatchDecision(parsed.decision(), parsed.confidence(), parsed.reasons(),
                parsed.missingEvidence(), claims);
    }

    private VisualMatchDecision evaluateImage(String condition, FileEntity file, VisualMatchDecision previous,
                                              List<String> requestedEntities) {
        if (visionModel == null || file.getImageData() == null || file.getImageData().length == 0) return previous;
        List<EntityVisualAnchor> anchors = graphQueryService.certainEntityAnchorsForFile(file.getPath(), requestedEntities);
        String prompt = """
                Determine whether this image proves the requested condition. Attribute evidence only to the entity
                supported by a supplied certain face bbox; do not infer an identity from appearance.
                Every positive answer must contain at least one atomic Polish claim. Return JSON only:
                {"decision":"MATCH|NO_MATCH|UNCERTAIN","confidence":0.0,"reasons":["..."],
                "missingEvidence":["..."],"claims":[{"entity":"canonical name","predicate":"open predicate",
                "value":"optional value","statementPl":"one short Polish evidence sentence","confidence":0.0}]}.
                Condition: %s
                Certain identity anchors (name and face bbox): %s
                Supporting context: %s
                """.formatted(condition, anchors, trim(graphQueryService.buildFullContextForFile(file.getPath())));
        try {
            byte[] verificationImage = EvidenceImageRenderer.render(file.getImageData(), anchors);
            String response = visionModel.generate(UserMessage.from(TextContent.from(prompt),
                    ImageContent.from(Base64.getEncoder().encodeToString(verificationImage), "image/jpeg"))).content().text();
            return parsePixelDecision(response, file, anchors);
        } catch (Exception exception) {
            log.warn("Visual verification failed for {}: {}", file.getPath(), exception.getMessage());
            return uncertain("Image verification could not be completed");
        }
    }

    private boolean needsVisionVerification(VisualMatchDecision decision, FileEntity file) {
        return file.getImageData() != null && file.getImageData().length > 0
                && (decision.decision() != VisualMatchDecision.Decision.MATCH
                || decision.confidence().doubleValue() < contextAcceptConfidence
                || decision.claims().isEmpty());
    }

    private VisualMatchDecision parseDecision(String response, String reason) {
        try {
            JsonNode root = objectMapper.readTree(extractJson(response));
            VisualMatchDecision.Decision decision = VisualMatchDecision.Decision.valueOf(
                    root.path("decision").asText("UNCERTAIN").toUpperCase(Locale.ROOT));
            BigDecimal confidence = BigDecimal.valueOf(Math.max(0, Math.min(1, root.path("confidence").asDouble())));
            return new VisualMatchDecision(decision, confidence, strings(root.path("reasons")), strings(root.path("missingEvidence")));
        } catch (Exception ignored) {
            return uncertain(reason);
        }
    }

    private VisualMatchDecision parsePixelDecision(String response, FileEntity file,
                                                    List<EntityVisualAnchor> anchors) {
        try {
            JsonNode root = objectMapper.readTree(extractJson(response));
            VisualMatchDecision.Decision decision = VisualMatchDecision.Decision.valueOf(
                    root.path("decision").asText("UNCERTAIN").toUpperCase(Locale.ROOT));
            BigDecimal confidence = bounded(root.path("confidence").asDouble());
            List<GroundedVisualClaim> claims = new ArrayList<>();
            JsonNode claimNodes = root.path("claims");
            if (claimNodes.isArray()) {
                int index = 0;
                for (JsonNode claim : claimNodes) {
                    String entity = claim.path("entity").asText("").trim();
                    String predicate = claim.path("predicate").asText("").trim();
                    String value = claim.path("value").asText("").trim();
                    String statement = claim.path("statementPl").asText("").trim();
                    EntityVisualAnchor anchor = anchors.stream()
                            .filter(item -> item.entityName() != null && item.entityName().equalsIgnoreCase(entity))
                            .findFirst().orElse(null);
                    // Named claims require a certain identity bbox. Unnamed scene claims may omit an entity.
                    if (!entity.isBlank() && anchor == null) continue;
                    if (predicate.isBlank() || statement.isBlank()) continue;
                    index++;
                    claims.add(new GroundedVisualClaim("P-" + file.getId() + "-" + index,
                            anchor == null ? null : anchor.mentionId(), entity, predicate, value, statement,
                            file.getPath(), bounded(claim.path("confidence").asDouble(confidence.doubleValue())),
                            "PIXEL_VERIFICATION", anchor == null ? "" : anchor.faceAnchorId()));
                }
            }
            if (decision == VisualMatchDecision.Decision.MATCH && claims.isEmpty()) {
                return new VisualMatchDecision(VisualMatchDecision.Decision.UNCERTAIN, BigDecimal.ZERO,
                        List.of(), List.of("Brak claimu przypisanego do pewnego dowodu"), List.of());
            }
            return new VisualMatchDecision(decision, confidence, strings(root.path("reasons")),
                    strings(root.path("missingEvidence")), claims);
        } catch (Exception ignored) {
            return uncertain("Image verification could not be evaluated");
        }
    }

    private BigDecimal bounded(double value) {
        return BigDecimal.valueOf(Math.max(0, Math.min(1, value)));
    }

    private String generateText(String prompt) { try { return chatModel.generate(prompt); } catch (Exception exception) { return ""; } }
    private List<String> strings(JsonNode node) { List<String> values = new ArrayList<>(); if (node.isArray()) node.forEach(value -> { if (value.isTextual()) values.add(value.asText()); }); return values; }
    private VisualMatchDecision uncertain(String reason) { return new VisualMatchDecision(VisualMatchDecision.Decision.UNCERTAIN, BigDecimal.ZERO, List.of(), List.of(reason)); }
    private String extractJson(String response) { if (response == null) return "{}"; int start = response.indexOf('{'); int end = response.lastIndexOf('}'); return start >= 0 && end > start ? response.substring(start, end + 1) : response; }
    private String trim(String value) { if (value == null) return ""; return value.length() <= maxContextChars ? value : value.substring(0, maxContextChars) + "..."; }
    private boolean isImage(FileEntity file) { return file.getFileType() != null && file.getFileType().toLowerCase(Locale.ROOT).startsWith("image/"); }
    private String imageMime(String value) { return value == null || value.isBlank() || value.equalsIgnoreCase("image/jpg") ? "image/jpeg" : value; }

    private record Candidate(FileEntity file, BigDecimal retrievalScore, BigDecimal entityConfidence) {
        Candidate merge(Candidate other) { return new Candidate(file, retrievalScore.max(other.retrievalScore), entityConfidence.max(other.entityConfidence)); }
        BigDecimal rank() { return retrievalScore.add(entityConfidence); }
    }
}

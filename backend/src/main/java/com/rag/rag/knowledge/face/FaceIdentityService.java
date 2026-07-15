package com.rag.rag.knowledge.face;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.rag.knowledge.entity.EntityMention;
import com.rag.rag.knowledge.entity.KnowledgeEntity;
import com.rag.rag.knowledge.entity.MentionStatus;
import com.rag.rag.knowledge.identity.IdentityResolutionService;
import com.rag.rag.knowledge.repository.EntityMentionRepository;
import com.rag.rag.knowledge.repository.FaceEmbeddingRepository;
import com.rag.rag.knowledge.repository.FaceObservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.math.RoundingMode;

@Slf4j
@Service
@RequiredArgsConstructor
public class FaceIdentityService {

    private static final int TOP_EMBEDDINGS_PER_ENTITY = 2;
    private static final double MIN_BBOX_MATCH_SCORE = 0.20;
    private static final double FACE_NMS_IOU_THRESHOLD = 0.35;
    private static final float MIN_FACE_SIZE = 12f;

    private final FaceRecognitionClient faceClient;
    private final FaceEmbeddingRepository faceEmbeddingRepository;
    private final FaceObservationRepository faceObservationRepository;
    private final EntityMentionRepository mentionRepository;
    private final IdentityResolutionService identityResolutionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${face.match.threshold:0.55}")
    private double matchThreshold;

    @Value("${face.match.suggestion-threshold:0.50}")
    private double suggestionThreshold;

    @Value("${face.match.min-margin:0.08}")
    private double minMargin;

    @Value("${face.match.min-det-score:0.50}")
    private double minDetScore;

    @Value("${face.match.vector-search.enabled:true}")
    private boolean vectorSearchEnabled;

    @Value("${face.match.vector-search.top-k:40}")
    private int vectorSearchTopK;

    @Value("${face.match.batch-cluster-threshold:0.48}")
    private double batchClusterThreshold;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void replaceFaceEmbeddingsForFile(
            byte[] imageBytes,
            String filePath,
            String fileName,
            List<EntityMention> personMentions
    ) {
        faceEmbeddingRepository.deleteByFilePath(filePath);
        faceObservationRepository.deleteByFilePath(filePath);
        processImageFaces(imageBytes, filePath, fileName, personMentions);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processImageFaces(byte[] imageBytes, String filePath, String fileName, List<EntityMention> personMentions) {
        List<DetectedFaceDto> faces = faceClient.analyze(imageBytes, fileName);
        processDetectedFaces(faces, filePath, fileName, personMentions);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processDetectedFaces(List<DetectedFaceDto> faces, String filePath, String fileName, List<EntityMention> personMentions) {
        // Face analysis may be retried after a failed request or cache refresh.
        // Replace the file's results instead of accumulating duplicate boxes.
        faceEmbeddingRepository.deleteByFilePath(filePath);
        faceObservationRepository.deleteByFilePath(filePath);
        if (faces.isEmpty()) {
            return;
        }

        faces = suppressOverlappingFaces(faces);

        // Use the mentions produced by the current vision pass. They can still be
        // uncommitted in the outer upload transaction, so reloading here would
        // incorrectly create a second mention for every detected face.
        List<EntityMention> personOnlyMentions = (personMentions == null ? List.<EntityMention>of() : personMentions).stream()
                .filter(this::isPersonMention)
                .filter(mention -> mention.getStatus() != MentionStatus.REJECTED)
                .toList();
        List<FaceAssignment> assignments = assignMentionsToFaces(faces, personOnlyMentions);
        Set<UUID> matchedMentionIds = new HashSet<>();

        for (FaceAssignment assignment : assignments) {
            DetectedFaceDto face = assignment.face();
            if (face.detScore() < minDetScore) {
                log.debug("Skipping low-confidence face (det_score={}) in {}", face.detScore(), filePath);
                continue;
            }

            float[] embedding = normalizeEmbedding(face.embeddingArray());
            if (embedding.length == 0) {
                continue;
            }

            Optional<EntityMatch> existingMatch = findBestEntityMatch(embedding, filePath, suggestionThreshold);
            EntityMention mention = assignment.mention();
            KnowledgeEntity entity = null;

            if (existingMatch.isPresent() && existingMatch.get().score() >= matchThreshold) {
                entity = existingMatch.get().entity();
                if (mention != null) {
                    identityResolutionService.confirmFaceMatch(mention, entity, mention.getLabel());
                } else {
                    mention = EntityMention.builder()
                            .filePath(filePath)
                            .label(entity.getDisplayName())
                            .entityType("PERSON")
                            .confidence(confidence(face.detScore()))
                            .status(MentionStatus.CONFIRMED)
                            .entity(entity)
                            .build();
                    mention = mentionRepository.save(mention);
                }
            } else if (mention != null && mention.getEntity() != null) {
                entity = mention.getEntity();
            } else if (mention != null) {
                entity = identityResolutionService.createUnconfirmedEntity(uniqueUnknownName(filePath), "PERSON");
                mention.setEntity(entity);
                mention.setStatus(MentionStatus.CONFIRMED);
                mentionRepository.save(mention);
            } else {
                entity = identityResolutionService.createUnconfirmedEntity(uniqueUnknownName(filePath), "PERSON");
                mention = EntityMention.builder()
                        .filePath(filePath)
                        .label(entity.getDisplayName())
                        .entityType("PERSON")
                        .confidence(confidence(face.detScore()))
                        .status(MentionStatus.CONFIRMED)
                        .entity(entity)
                        .build();
                mention = mentionRepository.save(mention);
            }

            if (mention != null && mention.getId() != null) {
                matchedMentionIds.add(mention.getId());
            }

            if (entity == null) {
                saveFaceObservation(mention, filePath, face, embedding);
            } else {
                saveFaceEmbedding(entity, mention, filePath, face, embedding);
            }
        }

        List<String> unmatchedMentions = personOnlyMentions.stream()
                .filter(mention -> mention.getId() != null && !matchedMentionIds.contains(mention.getId()))
                .map(EntityMention::getLabel)
                .toList();
        if (!unmatchedMentions.isEmpty()) {
            log.debug("Mentions without matched face in {}: {}", filePath, unmatchedMentions);
        }
    }

    private void saveFaceEmbedding(
            KnowledgeEntity entity,
            EntityMention mention,
            String filePath,
            DetectedFaceDto face,
            float[] embedding
    ) {
        float[] bbox = null;
        if (face.bbox() != null && !face.bbox().isEmpty()) {
            bbox = new float[face.bbox().size()];
            for (int i = 0; i < face.bbox().size(); i++) {
                bbox[i] = face.bbox().get(i);
            }
            bbox = clampBbox(normalizeBboxLength(bbox), face.imageWidth(), face.imageHeight());
        }

        faceEmbeddingRepository.save(FaceEmbedding.builder()
                .entity(entity)
                .mention(mention)
                .filePath(filePath)
                .embedding(embedding)
                .embeddingVector(toVectorLiteral(embedding))
                .bbox(bbox)
                .detScore(confidence(face.detScore()))
                .build());
    }

    private void saveFaceObservation(
            EntityMention mention,
            String filePath,
            DetectedFaceDto face,
            float[] embedding
    ) {
        float[] bbox = null;
        if (face.bbox() != null) {
            bbox = new float[face.bbox().size()];
            for (int i = 0; i < face.bbox().size(); i++) {
                bbox[i] = face.bbox().get(i);
            }
        }
        faceObservationRepository.save(FaceObservation.builder()
                .mention(mention)
                .filePath(filePath)
                .embedding(embedding)
                .bbox(bbox)
                .detScore(confidence(face.detScore()))
                .status("PENDING")
                .build());
    }

    @Transactional
    public void promoteObservation(EntityMention mention, KnowledgeEntity entity) {
        if (mention == null || entity == null) {
            return;
        }
        faceObservationRepository.findFirstByMentionIdAndStatus(mention.getId(), "PENDING")
                .ifPresent(observation -> {
                    faceEmbeddingRepository.save(FaceEmbedding.builder()
                            .entity(entity)
                            .mention(mention)
                            .filePath(observation.getFilePath())
                            .embedding(observation.getEmbedding())
                            .embeddingVector(toVectorLiteral(normalizeEmbedding(observation.getEmbedding())))
                            .bbox(observation.getBbox())
                            .detScore(observation.getDetScore())
                            .build());
                    observation.setStatus("CONFIRMED");
                    faceObservationRepository.save(observation);
                });
    }

    public Optional<EntityMatch> findBestEntityMatch(float[] queryEmbedding, String excludeFilePath, double threshold) {
        List<FaceEmbedding> storedEmbeddings = List.of();
        if (vectorSearchEnabled) {
            try {
                storedEmbeddings = faceEmbeddingRepository.findTopKByVectorDistance(
                        toVectorLiteral(normalizeEmbedding(queryEmbedding)),
                        excludeFilePath,
                        BigDecimal.valueOf(minDetScore),
                        vectorSearchTopK
                );
            } catch (Exception e) {
                log.warn("Vector top-K face search failed, using fallback: {}", e.getMessage());
            }
        }
        if (storedEmbeddings.isEmpty()) {
            storedEmbeddings = excludeFilePath == null || excludeFilePath.isBlank()
                    ? faceEmbeddingRepository.findAllConfirmedGallery()
                    : faceEmbeddingRepository.findAllExceptFilePath(excludeFilePath);
        }

        return rankEntityMatches(storedEmbeddings.stream()
                .filter(this::isGalleryCandidate)
                .toList(), queryEmbedding, threshold);
    }

    Optional<EntityMatch> rankEntityMatches(List<FaceEmbedding> storedEmbeddings, float[] queryEmbedding, double threshold) {
        float[] normalizedQuery = normalizeEmbedding(queryEmbedding);
        Map<UUID, List<ScoredEmbedding>> scoresByEntity = new LinkedHashMap<>();

        for (FaceEmbedding stored : storedEmbeddings) {
            if (stored.getEmbedding() == null || stored.getEntity() == null) {
                continue;
            }

            double score = cosineSimilarity(normalizedQuery, normalizeEmbedding(stored.getEmbedding()));
            UUID entityId = stored.getEntity().getId();
            double quality = stored.getDetScore() != null ? stored.getDetScore().doubleValue() : 1.0;
            scoresByEntity.computeIfAbsent(entityId, ignored -> new ArrayList<>())
                    .add(new ScoredEmbedding(score, Math.max(0.05, quality)));
        }

        List<EntityMatch> ranked = new ArrayList<>();
        for (Map.Entry<UUID, List<ScoredEmbedding>> entry : scoresByEntity.entrySet()) {
            KnowledgeEntity entity = storedEmbeddings.stream()
                    .filter(stored -> stored.getEntity() != null && stored.getEntity().getId().equals(entry.getKey()))
                    .map(FaceEmbedding::getEntity)
                    .findFirst()
                    .orElse(null);
            if (entity == null) {
                continue;
            }

            List<ScoredEmbedding> scores = entry.getValue();
            scores.sort(Comparator.comparingDouble(ScoredEmbedding::score).reversed());
            double bestScore = scores.get(0).score();
            int topCount = Math.min(TOP_EMBEDDINGS_PER_ENTITY, scores.size());
            double weightedSum = 0.0;
            double weightSum = 0.0;
            double centroidSum = 0.0;
            double centroidWeight = 0.0;
            for (int i = 0; i < topCount; i++) {
                weightedSum += scores.get(i).score() * scores.get(i).quality();
                weightSum += scores.get(i).quality();
            }
            double weightedScore = weightSum == 0.0 ? bestScore : weightedSum / weightSum;
            // Keep the strongest exemplar dominant, while letting several good
            // reference photos stabilize the score for pose/lighting changes.
            for (ScoredEmbedding scored : scores) {
                centroidSum += scored.score() * scored.quality();
                centroidWeight += scored.quality();
            }
            double centroidScore = centroidWeight == 0.0 ? bestScore : centroidSum / centroidWeight;
            double aggregateScore = (0.65 * bestScore) + (0.20 * weightedScore) + (0.15 * centroidScore);
            ranked.add(new EntityMatch(entity, bestScore, aggregateScore));
        }

        ranked.removeIf(match -> match.score() < threshold);
        ranked.sort(Comparator.comparingDouble(EntityMatch::rankingScore).reversed());
        if (ranked.isEmpty()) {
            return Optional.empty();
        }

        if (ranked.size() >= 2) {
            double margin = ranked.get(0).rankingScore() - ranked.get(1).rankingScore();
            if (margin < minMargin) {
                log.debug(
                        "Skipping ambiguous face match: best={} second={} margin={}",
                        ranked.get(0).rankingScore(),
                        ranked.get(1).rankingScore(),
                        margin
                );
                return Optional.empty();
            }
        }

        return Optional.of(ranked.get(0));
    }

    /** Returns the threshold used when linking faces inside one upload batch. */
    public double batchClusterThreshold() {
        return batchClusterThreshold;
    }

    private List<FaceAssignment> assignMentionsToFaces(List<DetectedFaceDto> faces, List<EntityMention> personMentions) {
        List<DetectedFaceDto> sortedFaces = new ArrayList<>(faces);
        sortedFaces.sort(Comparator.comparingDouble(this::bboxCenterX));
        if (personMentions == null || personMentions.isEmpty()) {
            return sortedFaces.stream().map(face -> new FaceAssignment(face, null)).toList();
        }

        int imageWidth = sortedFaces.stream().mapToInt(DetectedFaceDto::imageWidth).filter(value -> value > 0).findFirst().orElse(0);
        int imageHeight = sortedFaces.stream().mapToInt(DetectedFaceDto::imageHeight).filter(value -> value > 0).findFirst().orElse(0);
        List<MentionCandidate> mentionCandidates = personMentions.stream()
                .sorted(Comparator.comparing(EntityMention::getCreatedAt))
                .map(mention -> new MentionCandidate(mention, parseMentionBbox(mention, imageWidth, imageHeight)))
                .toList();

        boolean hasMentionBbox = mentionCandidates.stream().anyMatch(candidate -> candidate.bbox().length == 4);
        if (!hasMentionBbox) {
            return assignSequentially(sortedFaces, mentionCandidates);
        }

        List<FaceAssignment> assignments = new ArrayList<>();
        Set<UUID> usedMentions = new HashSet<>();
        for (DetectedFaceDto face : sortedFaces) {
            MentionCandidate best = null;
            double bestScore = 0.0;
            float[] faceBbox = toBboxArray(face);
            for (MentionCandidate candidate : mentionCandidates) {
                UUID mentionId = candidate.mention().getId();
                if (mentionId != null && usedMentions.contains(mentionId)) {
                    continue;
                }
                if (candidate.bbox().length != 4 || faceBbox.length != 4) {
                    continue;
                }
                double score = bboxMatchScore(faceBbox, candidate.bbox());
                if (score > bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }
            if (best != null && bestScore >= MIN_BBOX_MATCH_SCORE) {
                if (best.mention().getId() != null) {
                    usedMentions.add(best.mention().getId());
                }
                assignments.add(new FaceAssignment(face, best.mention()));
            } else {
                assignments.add(new FaceAssignment(face, null));
            }
        }

        List<EntityMention> remainingMentions = mentionCandidates.stream()
                .map(MentionCandidate::mention)
                .filter(mention -> mention.getId() == null || !usedMentions.contains(mention.getId()))
                .toList();
        if (!remainingMentions.isEmpty()) {
            List<Integer> unassignedFaceIndexes = new ArrayList<>();
            for (int i = 0; i < assignments.size(); i++) {
                if (assignments.get(i).mention() == null) {
                    unassignedFaceIndexes.add(i);
                }
            }
            for (int i = 0; i < Math.min(unassignedFaceIndexes.size(), remainingMentions.size()); i++) {
                int faceIndex = unassignedFaceIndexes.get(i);
                assignments.set(faceIndex, new FaceAssignment(assignments.get(faceIndex).face(), remainingMentions.get(i)));
            }
        }
        return assignments;
    }

    private List<FaceAssignment> assignSequentially(List<DetectedFaceDto> sortedFaces, List<MentionCandidate> mentionCandidates) {
        List<FaceAssignment> assignments = new ArrayList<>();
        for (int i = 0; i < sortedFaces.size(); i++) {
            EntityMention mention = i < mentionCandidates.size() ? mentionCandidates.get(i).mention() : null;
            assignments.add(new FaceAssignment(sortedFaces.get(i), mention));
        }
        return assignments;
    }

    private float[] parseMentionBbox(EntityMention mention, int imageWidth, int imageHeight) {
        if (mention == null || mention.getBbox() == null || mention.getBbox().isBlank()) {
            return new float[0];
        }
        try {
            return normalizeBboxCoordinates(
                    normalizeBboxLength(objectMapper.readValue(mention.getBbox(), float[].class)),
                    imageWidth,
                    imageHeight
            );
        } catch (Exception e) {
            log.debug("Failed to parse mention bbox for {}: {}", mention.getId(), e.getMessage());
            return new float[0];
        }
    }

    private List<DetectedFaceDto> suppressOverlappingFaces(List<DetectedFaceDto> faces) {
        List<DetectedFaceDto> candidates = faces.stream()
                .filter(face -> face != null && face.bbox() != null && face.bbox().size() >= 4)
                .filter(face -> face.bbox().get(2) - face.bbox().get(0) >= MIN_FACE_SIZE
                        && face.bbox().get(3) - face.bbox().get(1) >= MIN_FACE_SIZE)
                .sorted(Comparator.comparingDouble(DetectedFaceDto::detScore).reversed())
                .toList();

        List<DetectedFaceDto> selected = new ArrayList<>();
        for (DetectedFaceDto candidate : candidates) {
            float[] candidateBox = toBboxArray(candidate);
            boolean overlaps = selected.stream()
                    .map(this::toBboxArray)
                    .anyMatch(selectedBox -> bboxIoU(candidateBox, selectedBox) >= FACE_NMS_IOU_THRESHOLD);
            if (!overlaps) {
                selected.add(candidate);
            }
        }
        return selected;
    }

    private float[] toBboxArray(DetectedFaceDto face) {
        List<Float> bbox = face.bbox();
        if (bbox == null || bbox.size() < 4) {
            return new float[0];
        }
        float[] result = new float[4];
        for (int i = 0; i < 4; i++) {
            result[i] = bbox.get(i);
        }
        return clampBbox(
                normalizeBboxCoordinates(normalizeBboxLength(result), face.imageWidth(), face.imageHeight()),
                face.imageWidth(),
                face.imageHeight()
        );
    }

    private float[] normalizeBboxCoordinates(float[] bbox, int imageWidth, int imageHeight) {
        if (bbox.length != 4 || imageWidth <= 0 || imageHeight <= 0) {
            return bbox;
        }
        boolean alreadyNormalized = bbox[0] >= 0f && bbox[1] >= 0f && bbox[2] <= 1f && bbox[3] <= 1f;
        if (alreadyNormalized) {
            return bbox;
        }
        return new float[] {
                bbox[0] / imageWidth,
                bbox[1] / imageHeight,
                bbox[2] / imageWidth,
                bbox[3] / imageHeight
        };
    }

    private float[] clampBbox(float[] bbox, int imageWidth, int imageHeight) {
        if (bbox.length != 4 || imageWidth <= 0 || imageHeight <= 0) {
            return bbox;
        }
        float x1 = Math.max(0f, Math.min(bbox[0], imageWidth));
        float y1 = Math.max(0f, Math.min(bbox[1], imageHeight));
        float x2 = Math.max(x1, Math.min(bbox[2], imageWidth));
        float y2 = Math.max(y1, Math.min(bbox[3], imageHeight));
        return new float[] {x1, y1, x2, y2};
    }

    private float[] normalizeBboxLength(float[] bbox) {
        if (bbox == null || bbox.length < 4) {
            return new float[0];
        }
        return new float[] {bbox[0], bbox[1], bbox[2], bbox[3]};
    }

    private double bboxMatchScore(float[] faceBbox, float[] mentionBbox) {
        double iou = bboxIoU(faceBbox, mentionBbox);
        double centerDistance = centerDistance(faceBbox, mentionBbox);
        double normalizer = Math.max(1.0, (bboxDiagonal(faceBbox) + bboxDiagonal(mentionBbox)) / 2.0);
        double centerSimilarity = Math.max(0.0, 1.0 - (centerDistance / normalizer));
        return 0.7 * iou + 0.3 * centerSimilarity;
    }

    private double bboxIoU(float[] a, float[] b) {
        double xLeft = Math.max(a[0], b[0]);
        double yTop = Math.max(a[1], b[1]);
        double xRight = Math.min(a[2], b[2]);
        double yBottom = Math.min(a[3], b[3]);
        double intersection = Math.max(0.0, xRight - xLeft) * Math.max(0.0, yBottom - yTop);
        if (intersection <= 0.0) {
            return 0.0;
        }
        double areaA = Math.max(0.0, a[2] - a[0]) * Math.max(0.0, a[3] - a[1]);
        double areaB = Math.max(0.0, b[2] - b[0]) * Math.max(0.0, b[3] - b[1]);
        double union = areaA + areaB - intersection;
        if (union <= 0.0) {
            return 0.0;
        }
        return intersection / union;
    }

    private double centerDistance(float[] a, float[] b) {
        double ax = (a[0] + a[2]) / 2.0;
        double ay = (a[1] + a[3]) / 2.0;
        double bx = (b[0] + b[2]) / 2.0;
        double by = (b[1] + b[3]) / 2.0;
        return Math.sqrt((ax - bx) * (ax - bx) + (ay - by) * (ay - by));
    }

    private double bboxDiagonal(float[] bbox) {
        double width = Math.max(0.0, bbox[2] - bbox[0]);
        double height = Math.max(0.0, bbox[3] - bbox[1]);
        return Math.sqrt(width * width + height * height);
    }

    private String toVectorLiteral(float[] embedding) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(embedding[i]);
        }
        return builder.append(']').toString();
    }

    private boolean isPersonMention(EntityMention mention) {
        if (mention == null) {
            return false;
        }
        return "PERSON".equalsIgnoreCase(mention.getEntityType())
                || (mention.getEntity() != null && "PERSON".equalsIgnoreCase(mention.getEntity().getType()));
    }

    private boolean isGalleryCandidate(FaceEmbedding embedding) {
        if (embedding == null || embedding.getEntity() == null) {
            return false;
        }
        KnowledgeEntity entity = embedding.getEntity();
        if (!"PERSON".equalsIgnoreCase(entity.getType())) {
            return false;
        }
        if (identityResolutionService.isGenericPersonLabel(entity.getDisplayName())) {
            return false;
        }
        return embedding.getDetScore() == null || embedding.getDetScore().doubleValue() >= minDetScore;
    }

    private String uniqueUnknownName(String filePath) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return "Osoba #" + suffix;
    }

    private BigDecimal confidence(double value) {
        double clipped = Math.max(0.0, Math.min(0.999, value));
        return BigDecimal.valueOf(clipped).setScale(3, RoundingMode.HALF_UP);
    }

    public static float[] normalizeEmbedding(float[] embedding) {
        if (embedding == null || embedding.length == 0) {
            return new float[0];
        }

        double norm = 0.0;
        for (float value : embedding) {
            norm += value * value;
        }
        if (norm == 0.0) {
            return embedding.clone();
        }

        float scale = (float) (1.0 / Math.sqrt(norm));
        float[] normalized = new float[embedding.length];
        for (int i = 0; i < embedding.length; i++) {
            normalized[i] = embedding[i] * scale;
        }
        return normalized;
    }

    public static double cosineSimilarity(float[] a, float[] b) {
        if (a.length == 0 || b.length == 0 || a.length != b.length) {
            return 0.0;
        }
        double dot = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
        }
        return dot;
    }

    private double bboxCenterX(DetectedFaceDto face) {
        if (face.bbox() == null || face.bbox().size() < 4) {
            return 0.0;
        }
        return (face.bbox().get(0) + face.bbox().get(2)) / 2.0;
    }

    record MentionCandidate(EntityMention mention, float[] bbox) {}
    record FaceAssignment(DetectedFaceDto face, EntityMention mention) {}
    record ScoredEmbedding(double score, double quality) {}
    public record EntityMatch(KnowledgeEntity entity, double score, double rankingScore) {}
}

package com.rag.rag.knowledge.face;

import com.rag.rag.knowledge.entity.EntityMention;
import com.rag.rag.knowledge.entity.KnowledgeEntity;
import com.rag.rag.knowledge.entity.MentionStatus;
import com.rag.rag.knowledge.identity.IdentityResolutionService;
import com.rag.rag.knowledge.repository.EntityMentionRepository;
import com.rag.rag.knowledge.repository.FaceEmbeddingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FaceIdentityService {

    private static final int TOP_EMBEDDINGS_PER_ENTITY = 2;

    private final FaceRecognitionClient faceClient;
    private final FaceEmbeddingRepository faceEmbeddingRepository;
    private final EntityMentionRepository mentionRepository;
    private final IdentityResolutionService identityResolutionService;

    @Value("${face.match.threshold:0.55}")
    private double matchThreshold;

    @Value("${face.match.suggestion-threshold:0.50}")
    private double suggestionThreshold;

    @Value("${face.match.min-margin:0.08}")
    private double minMargin;

    @Value("${face.match.min-det-score:0.50}")
    private double minDetScore;

    @Transactional
    public void replaceFaceEmbeddingsForFile(
            byte[] imageBytes,
            String filePath,
            String fileName,
            List<EntityMention> personMentions
    ) {
        faceEmbeddingRepository.deleteByFilePath(filePath);
        processImageFaces(imageBytes, filePath, fileName, personMentions);
    }

    @Transactional
    public void processImageFaces(byte[] imageBytes, String filePath, String fileName, List<EntityMention> personMentions) {
        List<DetectedFaceDto> faces = faceClient.analyze(imageBytes, fileName);
        if (faces.isEmpty()) {
            return;
        }

        List<DetectedFaceDto> sortedFaces = new ArrayList<>(faces);
        sortedFaces.sort(Comparator.comparingDouble(this::bboxCenterX));

        List<EntityMention> sortedMentions = new ArrayList<>(personMentions);
        sortedMentions.sort(Comparator.comparing(EntityMention::getCreatedAt));

        for (int i = 0; i < sortedFaces.size(); i++) {
            DetectedFaceDto face = sortedFaces.get(i);
            if (face.detScore() < minDetScore) {
                log.debug("Skipping low-confidence face (det_score={}) in {}", face.detScore(), filePath);
                continue;
            }

            float[] embedding = normalizeEmbedding(face.embeddingArray());
            if (embedding.length == 0) {
                continue;
            }

            Optional<EntityMatch> existingMatch = findBestEntityMatch(embedding, filePath, suggestionThreshold);
            EntityMention mention = i < sortedMentions.size() ? sortedMentions.get(i) : null;
            KnowledgeEntity entity;

            if (existingMatch.isPresent() && existingMatch.get().score() >= matchThreshold) {
                entity = existingMatch.get().entity();
                if (mention != null) {
                    identityResolutionService.confirmFaceMatch(mention, entity, mention.getLabel());
                }
            } else if (mention != null && mention.getEntity() != null) {
                entity = mention.getEntity();
            } else if (mention != null) {
                if (existingMatch.isPresent()) {
                    identityResolutionService.suggestFaceMatch(mention, existingMatch.get().entity(), existingMatch.get().score());
                }
                if (mention.getEntity() != null) {
                    entity = mention.getEntity();
                } else if (identityResolutionService.looksLikePersonName(mention.getLabel())) {
                    entity = identityResolutionService.findOrCreateEntityByName(mention.getLabel());
                    mention.setEntity(entity);
                    mention.setStatus(MentionStatus.SUGGESTED);
                    mentionRepository.save(mention);
                } else {
                    entity = identityResolutionService.findOrCreateEntityByName("Nieznana osoba " + (i + 1));
                    mention.setEntity(entity);
                    mention.setStatus(MentionStatus.SUGGESTED);
                    mentionRepository.save(mention);
                }
            } else if (existingMatch.isPresent() && existingMatch.get().score() >= matchThreshold) {
                entity = existingMatch.get().entity();
            } else {
                entity = identityResolutionService.findOrCreateEntityByName("Nieznana osoba " + (i + 1));
                mention = EntityMention.builder()
                        .filePath(filePath)
                        .label(entity.getDisplayName())
                        .confidence(BigDecimal.valueOf(face.detScore()))
                        .status(MentionStatus.SUGGESTED)
                        .entity(entity)
                        .build();
                mention = mentionRepository.save(mention);
            }

            if (mention != null && mention.getEntity() == null) {
                mention.setEntity(entity);
                mentionRepository.save(mention);
            }

            saveFaceEmbedding(entity, mention, filePath, face, embedding);
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
        }

        faceEmbeddingRepository.save(FaceEmbedding.builder()
                .entity(entity)
                .mention(mention)
                .filePath(filePath)
                .embedding(embedding)
                .bbox(bbox)
                .detScore(BigDecimal.valueOf(face.detScore()))
                .build());
    }

    Optional<EntityMatch> findBestEntityMatch(float[] queryEmbedding, String excludeFilePath, double threshold) {
        List<FaceEmbedding> storedEmbeddings = excludeFilePath == null || excludeFilePath.isBlank()
                ? faceEmbeddingRepository.findAll()
                : faceEmbeddingRepository.findAllExceptFilePath(excludeFilePath);

        return rankEntityMatches(storedEmbeddings, queryEmbedding, threshold);
    }

    Optional<EntityMatch> rankEntityMatches(List<FaceEmbedding> storedEmbeddings, float[] queryEmbedding, double threshold) {
        float[] normalizedQuery = normalizeEmbedding(queryEmbedding);
        Map<UUID, List<Double>> scoresByEntity = new LinkedHashMap<>();

        for (FaceEmbedding stored : storedEmbeddings) {
            if (stored.getEmbedding() == null || stored.getEntity() == null) {
                continue;
            }

            double score = cosineSimilarity(normalizedQuery, normalizeEmbedding(stored.getEmbedding()));
            UUID entityId = stored.getEntity().getId();
            scoresByEntity.computeIfAbsent(entityId, ignored -> new ArrayList<>()).add(score);
        }

        List<EntityMatch> ranked = new ArrayList<>();
        for (Map.Entry<UUID, List<Double>> entry : scoresByEntity.entrySet()) {
            KnowledgeEntity entity = storedEmbeddings.stream()
                    .filter(stored -> stored.getEntity() != null && stored.getEntity().getId().equals(entry.getKey()))
                    .map(FaceEmbedding::getEntity)
                    .findFirst()
                    .orElse(null);
            if (entity == null) {
                continue;
            }

            List<Double> scores = entry.getValue();
            scores.sort(Comparator.reverseOrder());
            int topCount = Math.min(TOP_EMBEDDINGS_PER_ENTITY, scores.size());
            double aggregateScore = 0.0;
            for (int i = 0; i < topCount; i++) {
                aggregateScore += scores.get(i);
            }
            aggregateScore /= topCount;
            ranked.add(new EntityMatch(entity, aggregateScore));
        }

        ranked.sort(Comparator.comparingDouble(EntityMatch::score).reversed());
        if (ranked.isEmpty() || ranked.get(0).score() < threshold) {
            return Optional.empty();
        }

        if (ranked.size() >= 2) {
            double margin = ranked.get(0).score() - ranked.get(1).score();
            if (margin < minMargin) {
                log.debug(
                        "Skipping ambiguous face match: best={} second={} margin={}",
                        ranked.get(0).score(),
                        ranked.get(1).score(),
                        margin
                );
                return Optional.empty();
            }
        }

        return Optional.of(ranked.get(0));
    }

    static float[] normalizeEmbedding(float[] embedding) {
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

    static double cosineSimilarity(float[] a, float[] b) {
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

    record EntityMatch(KnowledgeEntity entity, double score) {}
}

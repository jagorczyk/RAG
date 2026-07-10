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
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FaceIdentityService {

    private final FaceRecognitionClient faceClient;
    private final FaceEmbeddingRepository faceEmbeddingRepository;
    private final EntityMentionRepository mentionRepository;
    private final IdentityResolutionService identityResolutionService;

    @Value("${face.match.threshold:0.42}")
    private double matchThreshold;

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
            float[] embedding = face.embeddingArray();
            if (embedding.length == 0) {
                continue;
            }

            Optional<EntityMatch> existingMatch = findBestEntityMatch(embedding);
            EntityMention mention = i < sortedMentions.size() ? sortedMentions.get(i) : null;

            KnowledgeEntity entity;
            if (mention != null && mention.getEntity() != null) {
                entity = mention.getEntity();
            } else if (mention != null) {
                entity = identityResolutionService.findOrCreateEntityByName(mention.getLabel());
            } else if (existingMatch.isPresent()) {
                entity = existingMatch.get().entity();
            } else {
                entity = identityResolutionService.findOrCreateEntityByName("Nieznana osoba " + (i + 1));
                mention = EntityMention.builder()
                        .filePath(filePath)
                        .label(entity.getDisplayName())
                        .confidence(BigDecimal.valueOf(face.detScore()))
                        .status(MentionStatus.CONFIRMED)
                        .entity(entity)
                        .build();
                mention = mentionRepository.save(mention);
            }

            if (mention != null && existingMatch.isPresent()) {
                KnowledgeEntity matchedEntity = existingMatch.get().entity();
                if (!matchedEntity.getId().equals(entity.getId())) {
                    identityResolutionService.suggestFaceMatch(mention, matchedEntity, existingMatch.get().score());
                }
            }

            if (mention != null) {
                mention.setEntity(entity);
                mention.setStatus(MentionStatus.CONFIRMED);
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

    private Optional<EntityMatch> findBestEntityMatch(float[] queryEmbedding) {
        EntityMatch best = null;

        for (FaceEmbedding stored : faceEmbeddingRepository.findAll()) {
            if (stored.getEmbedding() == null || stored.getEntity() == null) {
                continue;
            }
            double score = cosineSimilarity(queryEmbedding, stored.getEmbedding());
            if (score >= matchThreshold && (best == null || score > best.score())) {
                best = new EntityMatch(stored.getEntity(), score);
            }
        }

        return Optional.ofNullable(best);
    }

    static double cosineSimilarity(float[] a, float[] b) {
        if (a.length == 0 || b.length == 0 || a.length != b.length) {
            return 0.0;
        }
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private double bboxCenterX(DetectedFaceDto face) {
        if (face.bbox() == null || face.bbox().size() < 4) {
            return 0.0;
        }
        return (face.bbox().get(0) + face.bbox().get(2)) / 2.0;
    }

    private record EntityMatch(KnowledgeEntity entity, double score) {}
}

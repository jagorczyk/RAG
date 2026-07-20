package com.rag.rag.knowledge.face;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.rag.auth.security.CurrentUserService;
import com.rag.rag.core.cache.IdentityMatchCacheService;
import com.rag.rag.core.cache.IdentityMatchCacheService.CachedIdentityMatch;
import com.rag.rag.folder.repository.FileRepository;
import com.rag.rag.knowledge.entity.EntityMention;
import com.rag.rag.knowledge.entity.KnowledgeEntity;
import com.rag.rag.knowledge.entity.MentionStatus;
import com.rag.rag.knowledge.identity.IdentityResolutionService;
import com.rag.rag.knowledge.graph.MentionEvidencePolicy;
import com.rag.rag.knowledge.repository.EntityMentionRepository;
import com.rag.rag.knowledge.repository.FaceEmbeddingRepository;
import com.rag.rag.knowledge.repository.FaceObservationRepository;
import com.rag.rag.knowledge.repository.FactRepository;
import com.rag.rag.knowledge.repository.IdentitySuggestionRepository;
import com.rag.rag.knowledge.repository.KnowledgeEntityRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.math.RoundingMode;

@Slf4j
@Service
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
    private final MentionEvidencePolicy mentionEvidencePolicy;
    private final IdentitySuggestionRepository suggestionRepository;
    private final FactRepository factRepository;
    private final IdentityMatchCacheService identityMatchCacheService;
    private final KnowledgeEntityRepository knowledgeEntityRepository;
    private final FileRepository fileRepository;
    private final CurrentUserService currentUserService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public FaceIdentityService(FaceRecognitionClient faceClient,
                               FaceEmbeddingRepository faceEmbeddingRepository,
                               FaceObservationRepository faceObservationRepository,
                               EntityMentionRepository mentionRepository,
                               IdentityResolutionService identityResolutionService,
                               MentionEvidencePolicy mentionEvidencePolicy,
                               IdentitySuggestionRepository suggestionRepository,
                               FactRepository factRepository,
                               IdentityMatchCacheService identityMatchCacheService,
                               KnowledgeEntityRepository knowledgeEntityRepository,
                               FileRepository fileRepository,
                               CurrentUserService currentUserService) {
        this.faceClient = faceClient;
        this.faceEmbeddingRepository = faceEmbeddingRepository;
        this.faceObservationRepository = faceObservationRepository;
        this.mentionRepository = mentionRepository;
        this.identityResolutionService = identityResolutionService;
        this.mentionEvidencePolicy = mentionEvidencePolicy;
        this.suggestionRepository = suggestionRepository;
        this.factRepository = factRepository;
        this.identityMatchCacheService = identityMatchCacheService;
        this.knowledgeEntityRepository = knowledgeEntityRepository;
        this.fileRepository = fileRepository;
        this.currentUserService = currentUserService;
    }

    FaceIdentityService(FaceRecognitionClient faceClient,
                        FaceEmbeddingRepository faceEmbeddingRepository,
                        FaceObservationRepository faceObservationRepository,
                        EntityMentionRepository mentionRepository,
                        IdentityResolutionService identityResolutionService) {
        this(faceClient, faceEmbeddingRepository, faceObservationRepository, mentionRepository,
                identityResolutionService, new MentionEvidencePolicy(), null, null, null, null, null, null);
    }

    FaceIdentityService(FaceRecognitionClient faceClient,
                        FaceEmbeddingRepository faceEmbeddingRepository,
                        FaceObservationRepository faceObservationRepository,
                        EntityMentionRepository mentionRepository,
                        IdentityResolutionService identityResolutionService,
                        MentionEvidencePolicy mentionEvidencePolicy,
                        IdentitySuggestionRepository suggestionRepository,
                        FactRepository factRepository,
                        IdentityMatchCacheService identityMatchCacheService,
                        KnowledgeEntityRepository knowledgeEntityRepository) {
        this(faceClient, faceEmbeddingRepository, faceObservationRepository, mentionRepository,
                identityResolutionService, mentionEvidencePolicy, suggestionRepository, factRepository,
                identityMatchCacheService, knowledgeEntityRepository, null, null);
    }

    // Defaults must match application.properties (single source of truth when env unset)
    @Value("${face.match.threshold:0.50}")
    private double matchThreshold;

    @Value("${face.match.suggestion-threshold:0.45}")
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
        // Keep work inside this REQUIRES_NEW boundary (no self-invocation of processImageFaces).
        FaceAnalyzeResponse response = faceClient.analyzeResponseOrThrow(imageBytes, fileName);
        List<DetectedFaceDto> faces = response.faces() == null ? List.of() : response.faces().stream()
                .map(face -> face.withImageDimensions(
                        response.imageWidth() == null ? 0 : response.imageWidth(),
                        response.imageHeight() == null ? 0 : response.imageHeight()))
                .toList();
        processDetectedFacesInternal(faces, filePath, fileName, personMentions);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processImageFaces(byte[] imageBytes, String filePath, String fileName, List<EntityMention> personMentions) {
        FaceAnalyzeResponse response = faceClient.analyzeResponseOrThrow(imageBytes, fileName);
        List<DetectedFaceDto> faces = response.faces() == null ? List.of() : response.faces().stream()
                .map(face -> face.withImageDimensions(
                        response.imageWidth() == null ? 0 : response.imageWidth(),
                        response.imageHeight() == null ? 0 : response.imageHeight()))
                .toList();
        processDetectedFacesInternal(faces, filePath, fileName, personMentions);
    }

    /**
     * Separate transaction so face matching failures never mark the upload/vision TX
     * as rollback-only (which previously caused UnexpectedRollbackException).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processDetectedFaces(List<DetectedFaceDto> faces, String filePath, String fileName, List<EntityMention> personMentions) {
        processDetectedFacesInternal(faces, filePath, fileName, personMentions);
    }

    private void processDetectedFacesInternal(List<DetectedFaceDto> faces, String filePath, String fileName, List<EntityMention> personMentions) {
        // Preserve the previous face-to-mention mapping before replacing artifacts.
        // A retry must reuse the same mention instead of creating another unknown person.
        List<ExistingFaceLink> existingFaceLinks = loadExistingFaceLinks(filePath);
        faceEmbeddingRepository.deleteByFilePath(filePath);
        faceObservationRepository.deleteByFilePath(filePath);

        faces = suppressOverlappingFaces(faces == null ? List.of() : faces);

        // Always re-load mentions in THIS persistence context. Callers often pass entities
        // from a suspended outer transaction (REQUIRES_NEW), which causes
        // LazyInitializationException on mention.entity / proxy access.
        List<EntityMention> personOnlyMentions = loadPersonMentionsInCurrentSession(filePath, personMentions);
        List<FaceAssignment> assignments = assignMentionsToFaces(faces, personOnlyMentions, existingFaceLinks);
        Set<UUID> matchedMentionIds = new HashSet<>();
        Set<UUID> usedEntityIds = personOnlyMentions.stream()
                .filter(mentionEvidencePolicy::isCertain)
                .map(EntityMention::getEntity)
                .filter(java.util.Objects::nonNull)
                .map(KnowledgeEntity::getId)
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));

        List<PreparedFaceAssignment> preparedAssignments = assignments.stream()
                .filter(assignment -> assignment.face().detScore() >= minDetScore)
                .map(assignment -> {
                    float[] embedding = normalizeEmbedding(assignment.face().embeddingArray());
                    Optional<EntityMatch> candidate = embedding.length == 0
                            ? Optional.empty()
                            : findBestEntityMatch(embedding, filePath, suggestionThreshold);
                    return new PreparedFaceAssignment(assignment, embedding, candidate);
                })
                .filter(prepared -> prepared.embedding().length > 0)
                .sorted(Comparator.comparingDouble((PreparedFaceAssignment prepared) -> prepared.candidate()
                        .map(EntityMatch::rankingScore).orElse(-1.0)).reversed())
                .toList();

        for (PreparedFaceAssignment prepared : preparedAssignments) {
            FaceAssignment assignment = prepared.assignment();
            DetectedFaceDto face = assignment.face();
            float[] embedding = prepared.embedding();
            Optional<EntityMatch> existingMatch = prepared.candidate();
            EntityMention mention = assignment.mention();
            KnowledgeEntity entity = null;

            boolean automaticMatch = existingMatch.isPresent()
                    && existingMatch.get().score() >= matchThreshold
                    && usedEntityIds.add(existingMatch.get().entity().getId());
            if (automaticMatch) {
                EntityMatch match = existingMatch.get();
                entity = match.entity();
                if (mention != null) {
                    identityResolutionService.confirmFaceMatch(
                            mention, entity, mention.getLabel(), match.score(), match.margin());
                } else {
                    mention = EntityMention.builder()
                            .filePath(filePath)
                            .label(entity.getDisplayName())
                            .entityType("PERSON")
                            .confidence(confidence(face.detScore()))
                            .identityConfidence(confidence(match.score()))
                            .identityMargin(confidence(match.margin()))
                            .identitySource(com.rag.rag.knowledge.entity.IdentityEvidenceSource.FACE_MATCH)
                            .status(MentionStatus.CONFIRMED)
                            .entity(entity)
                            .build();
                    mention = mentionRepository.save(mention);
                }
            } else if (mention != null && mentionEvidencePolicy.isCertain(mention)) {
                entity = mention.getEntity();
            } else {
                if (mention == null) {
                    mention = EntityMention.builder()
                            .filePath(filePath)
                            .label(uniqueUnknownName(filePath))
                            .entityType("PERSON")
                            .confidence(confidence(face.detScore()))
                            .status(MentionStatus.PENDING)
                            .build();
                } else {
                    mention.setStatus(MentionStatus.PENDING);
                    mention.setIdentitySource(null);
                    mention.setIdentityConfidence(null);
                    mention.setIdentityMargin(null);
                }
                mention = mentionRepository.save(mention);
                if (existingMatch.isPresent()) {
                    identityResolutionService.suggestFaceMatch(
                            mention, existingMatch.get().entity(), existingMatch.get().score());
                }
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
        removeEvidenceFreeOrphans(filePath, matchedMentionIds);
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
        if (mention == null || entity == null || mention.getId() == null) {
            return;
        }
        EntityMention managedMention = mentionRepository.findById(mention.getId()).orElse(mention);
        KnowledgeEntity managedEntity = entity;
        faceObservationRepository.findFirstByMentionIdAndStatus(managedMention.getId(), "PENDING")
                .ifPresent(observation -> {
                    faceEmbeddingRepository.save(FaceEmbedding.builder()
                            .entity(managedEntity)
                            .mention(managedMention)
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
        float[] normalizedQuery = normalizeEmbedding(queryEmbedding);
        UUID ownerId = resolveGalleryOwnerId(excludeFilePath);
        String cacheKey = identityMatchCacheService != null
                ? identityMatchCacheService.buildKey(normalizedQuery, excludeFilePath, threshold, ownerId)
                : null;
        if (cacheKey != null && identityMatchCacheService != null) {
            Optional<CachedIdentityMatch> cached = identityMatchCacheService.get(cacheKey);
            if (cached.isPresent()) {
                CachedIdentityMatch hit = cached.get();
                if (hit.isNegative()) {
                    return Optional.empty();
                }
                if (knowledgeEntityRepository != null) {
                    Optional<KnowledgeEntity> entity = knowledgeEntityRepository.findById(hit.entityId());
                    if (entity.isPresent() && sameOwner(entity.get(), ownerId)) {
                        return Optional.of(new EntityMatch(
                                entity.get(), hit.score(), hit.rankingScore(), hit.margin()));
                    }
                }
            }
        }

        Optional<EntityMatch> match = findBestEntityMatchUncached(
                normalizedQuery, excludeFilePath, threshold, ownerId);

        if (cacheKey != null && identityMatchCacheService != null) {
            if (match.isPresent()) {
                EntityMatch m = match.get();
                identityMatchCacheService.putHit(
                        cacheKey, m.entity().getId(), m.score(), m.rankingScore(), m.margin());
            } else {
                identityMatchCacheService.putMiss(cacheKey);
            }
        }
        return match;
    }

    /**
     * Full gallery / vector search path without Redis. Used by cache miss and tests.
     */
    Optional<EntityMatch> findBestEntityMatchUncached(float[] queryEmbedding, String excludeFilePath, double threshold) {
        return findBestEntityMatchUncached(
                queryEmbedding, excludeFilePath, threshold, resolveGalleryOwnerId(excludeFilePath));
    }

    /**
     * Gallery match restricted to the owner of the file being resolved (or current user).
     * Package-visible for unit tests.
     */
    Optional<EntityMatch> findBestEntityMatchUncached(
            float[] queryEmbedding, String excludeFilePath, double threshold, UUID ownerId) {
        List<FaceEmbedding> storedEmbeddings = List.of();
        if (vectorSearchEnabled && faceEmbeddingRepository != null) {
            try {
                if (ownerId != null) {
                    storedEmbeddings = faceEmbeddingRepository.findTopKByVectorDistanceForOwner(
                            toVectorLiteral(normalizeEmbedding(queryEmbedding)),
                            excludeFilePath,
                            ownerId,
                            BigDecimal.valueOf(minDetScore),
                            vectorSearchTopK
                    );
                } else {
                    // No owner context: never search the full multi-user gallery.
                    storedEmbeddings = List.of();
                }
                // Native query does not JOIN FETCH associations — force-load while session is open.
                for (FaceEmbedding embedding : storedEmbeddings) {
                    if (embedding.getEntity() != null) {
                        embedding.getEntity().getId();
                        embedding.getEntity().getDisplayName();
                        embedding.getEntity().getOwnerId();
                    }
                    if (embedding.getMention() != null) {
                        embedding.getMention().getStatus();
                        if (embedding.getMention().getEntity() != null) {
                            embedding.getMention().getEntity().getId();
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Vector top-K face search failed, using fallback: {}", e.getMessage());
                storedEmbeddings = List.of();
            }
        }
        if (storedEmbeddings.isEmpty() && faceEmbeddingRepository != null && ownerId != null) {
            storedEmbeddings = excludeFilePath == null || excludeFilePath.isBlank()
                    ? faceEmbeddingRepository.findAllConfirmedGalleryForOwner(ownerId)
                    : faceEmbeddingRepository.findAllExceptFilePathForOwner(excludeFilePath, ownerId);
        }

        final UUID galleryOwner = ownerId;
        return rankEntityMatches(storedEmbeddings.stream()
                .filter(this::isGalleryCandidate)
                .filter(embedding -> belongsToOwner(embedding, galleryOwner))
                .toList(), queryEmbedding, threshold);
    }

    /**
     * Owner of the gallery to search: file owner when path is known, else current user.
     * Package-visible for tests.
     */
    UUID resolveGalleryOwnerId(String filePath) {
        if (filePath != null && !filePath.isBlank() && fileRepository != null) {
            Optional<UUID> fromFile = fileRepository.findByPath(filePath)
                    .map(file -> file.getOwnerId())
                    .filter(Objects::nonNull);
            if (fromFile.isPresent()) {
                return fromFile.get();
            }
        }
        if (currentUserService != null) {
            return currentUserService.findUserId().orElse(null);
        }
        return null;
    }

    /** True when the gallery embedding's person entity belongs to the given owner. */
    boolean belongsToOwner(FaceEmbedding embedding, UUID ownerId) {
        if (embedding == null || embedding.getEntity() == null) {
            return false;
        }
        return sameOwner(embedding.getEntity(), ownerId);
    }

    private boolean sameOwner(KnowledgeEntity entity, UUID ownerId) {
        if (entity == null) {
            return false;
        }
        if (ownerId == null) {
            return entity.getOwnerId() == null;
        }
        return ownerId.equals(entity.getOwnerId());
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
            ranked.add(new EntityMatch(entity, bestScore, aggregateScore, 0.0));
        }

        ranked.removeIf(match -> match.score() < threshold);
        ranked.sort(Comparator.comparingDouble(EntityMatch::rankingScore).reversed());
        if (ranked.isEmpty()) {
            return Optional.empty();
        }

        double margin = ranked.size() >= 2
                ? ranked.get(0).rankingScore() - ranked.get(1).rankingScore()
                : ranked.get(0).rankingScore();
        if (margin < minMargin) {
            log.debug(
                    "Skipping ambiguous face match: best={} second={} margin={}",
                    ranked.get(0).rankingScore(),
                    ranked.size() >= 2 ? ranked.get(1).rankingScore() : null,
                    margin
            );
            return Optional.empty();
        }

        EntityMatch best = ranked.get(0);
        return Optional.of(new EntityMatch(best.entity(), best.score(), best.rankingScore(), margin));
    }

    /** Returns the threshold used when linking faces inside one upload batch. */
    public double batchClusterThreshold() {
        return batchClusterThreshold;
    }

    private List<EntityMention> loadPersonMentionsInCurrentSession(
            String filePath,
            List<EntityMention> preferred
    ) {
        List<EntityMention> fromDb = mentionRepository.findByFilePath(filePath).stream()
                .filter(this::isPersonMention)
                .filter(mention -> mention.getStatus() != MentionStatus.REJECTED)
                .toList();
        if (!fromDb.isEmpty()) {
            return fromDb;
        }
        if (preferred == null || preferred.isEmpty()) {
            return List.of();
        }
        // Fallback: re-fetch by id so we never use detached proxies from another session.
        List<UUID> ids = preferred.stream()
                .map(EntityMention::getId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        return mentionRepository.findAllById(ids).stream()
                .filter(this::isPersonMention)
                .filter(mention -> mention.getStatus() != MentionStatus.REJECTED)
                .toList();
    }

    private List<ExistingFaceLink> loadExistingFaceLinks(String filePath) {
        List<ExistingFaceLink> links = new ArrayList<>();
        for (FaceEmbedding embedding : faceEmbeddingRepository.findByFilePath(filePath)) {
            EntityMention mention = reattachMention(embedding.getMention());
            if (mention != null && embedding.getBbox() != null && embedding.getBbox().length >= 4) {
                links.add(new ExistingFaceLink(mention, normalizeBboxLength(embedding.getBbox())));
            }
        }
        for (FaceObservation observation : faceObservationRepository.findByFilePath(filePath)) {
            EntityMention mention = reattachMention(observation.getMention());
            if (mention != null && observation.getBbox() != null
                    && observation.getBbox().length >= 4 && "PENDING".equals(observation.getStatus())) {
                links.add(new ExistingFaceLink(mention, normalizeBboxLength(observation.getBbox())));
            }
        }
        return links;
    }

    private EntityMention reattachMention(EntityMention mention) {
        if (mention == null) {
            return null;
        }
        UUID id = mention.getId();
        if (id == null) {
            return mention;
        }
        return mentionRepository.findById(id).orElse(null);
    }

    private void removeEvidenceFreeOrphans(String filePath, Set<UUID> currentFaceMentionIds) {
        if (suggestionRepository == null || factRepository == null) {
            return;
        }
        List<UUID> orphanIds = mentionRepository.findByFilePath(filePath).stream()
                .filter(mention -> mention.getId() != null)
                .filter(mention -> !currentFaceMentionIds.contains(mention.getId()))
                .filter(mention -> mention.getStatus() == MentionStatus.PENDING)
                .filter(mention -> mention.getEntity() == null)
                .filter(mention -> isEmptyEvidence(mention.getBbox()))
                .filter(mention -> isEmptyEvidence(mention.getVisualCues()))
                .filter(mention -> isEmptyEvidence(mention.getContextObjects()))
                .filter(mention -> isEmptyEvidence(mention.getNearbyText()))
                .filter(mention -> !factRepository.existsByMentionOrTargetMentionId(mention.getId()))
                .map(EntityMention::getId)
                .toList();
        if (orphanIds.isEmpty()) {
            return;
        }

        suggestionRepository.deleteByMentionIds(orphanIds);
        faceObservationRepository.deleteByMentionIds(orphanIds);
        faceEmbeddingRepository.deleteByMentionIdIn(orphanIds);
        mentionRepository.deleteAllByIdInBatch(orphanIds);
        log.info("Removed {} evidence-free duplicate face mentions from {}", orphanIds.size(), filePath);
    }

    private boolean isEmptyEvidence(String value) {
        return value == null || value.isBlank() || "[]".equals(value.trim()) || "{}".equals(value.trim());
    }

    private List<FaceAssignment> assignMentionsToFaces(List<DetectedFaceDto> faces, List<EntityMention> personMentions) {
        return assignMentionsToFaces(faces, personMentions, List.of());
    }

    private List<FaceAssignment> assignMentionsToFaces(
            List<DetectedFaceDto> faces,
            List<EntityMention> personMentions,
            List<ExistingFaceLink> existingFaceLinks
    ) {
        List<DetectedFaceDto> sortedFaces = new ArrayList<>(faces);
        sortedFaces.sort(Comparator.comparingDouble(this::bboxCenterX));

        int imageWidth = sortedFaces.stream().mapToInt(DetectedFaceDto::imageWidth).filter(value -> value > 0).findFirst().orElse(0);
        int imageHeight = sortedFaces.stream().mapToInt(DetectedFaceDto::imageHeight).filter(value -> value > 0).findFirst().orElse(0);
        List<MentionCandidate> mentionCandidates = (personMentions == null ? List.<EntityMention>of() : personMentions).stream()
                .sorted(Comparator.comparing(EntityMention::getCreatedAt))
                .map(mention -> new MentionCandidate(mention, parseMentionBbox(mention, imageWidth, imageHeight)))
                .toList();
        List<MentionCandidate> previousFaceCandidates = (existingFaceLinks == null
                ? List.<ExistingFaceLink>of() : existingFaceLinks).stream()
                .filter(link -> link.mention() != null && link.bbox() != null && link.bbox().length == 4)
                .map(link -> new MentionCandidate(
                        link.mention(), normalizeBboxCoordinates(link.bbox().clone(), imageWidth, imageHeight)))
                .toList();

        List<FaceAssignment> assignments = new ArrayList<>();
        Set<UUID> usedMentions = new HashSet<>();
        for (DetectedFaceDto face : sortedFaces) {
            float[] faceBbox = toBboxArray(face);
            MentionCandidate best = bestBboxCandidate(faceBbox, previousFaceCandidates, usedMentions);
            if (best == null) {
                best = bestBboxCandidate(faceBbox, mentionCandidates, usedMentions);
            }
            if (best != null) {
                if (best.mention().getId() != null) {
                    usedMentions.add(best.mention().getId());
                }
                assignments.add(new FaceAssignment(face, best.mention()));
            } else {
                assignments.add(new FaceAssignment(face, null));
            }
        }

        return assignments;
    }

    private MentionCandidate bestBboxCandidate(
            float[] faceBbox,
            List<MentionCandidate> candidates,
            Set<UUID> usedMentions
    ) {
        MentionCandidate best = null;
        double bestScore = MIN_BBOX_MATCH_SCORE;
        for (MentionCandidate candidate : candidates) {
            UUID mentionId = candidate.mention().getId();
            if (mentionId != null && usedMentions.contains(mentionId)) {
                continue;
            }
            if (candidate.bbox().length != 4 || faceBbox.length != 4) {
                continue;
            }
            double score = bboxMatchScore(faceBbox, candidate.bbox());
            if (score >= bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
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
        if (embedding.getDetScore() != null && embedding.getDetScore().doubleValue() < minDetScore) {
            return false;
        }
        EntityMention mention = embedding.getMention();
        if (mention == null) {
            return false;
        }
        try {
            // Gallery rows may carry lazy proxies; skip stale/missing mentions instead of failing the whole file.
            return mentionEvidencePolicy.isCertain(mention);
        } catch (Exception e) {
            log.debug("Skipping gallery face embedding {} (lazy mention unavailable): {}",
                    embedding.getId(), e.getMessage());
            return false;
        }
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
    record ExistingFaceLink(EntityMention mention, float[] bbox) {}
    record FaceAssignment(DetectedFaceDto face, EntityMention mention) {}
    record PreparedFaceAssignment(FaceAssignment assignment, float[] embedding, Optional<EntityMatch> candidate) {}
    record ScoredEmbedding(double score, double quality) {}
    public record EntityMatch(KnowledgeEntity entity, double score, double rankingScore, double margin) {}
}

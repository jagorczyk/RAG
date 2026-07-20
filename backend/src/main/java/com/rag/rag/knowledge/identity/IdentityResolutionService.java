package com.rag.rag.knowledge.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.rag.auth.security.CurrentUserService;
import com.rag.rag.core.cache.IdentityMatchCacheService;
import com.rag.rag.folder.repository.FileRepository;
import com.rag.rag.knowledge.entity.*;
import com.rag.rag.knowledge.fact.Fact;
import com.rag.rag.knowledge.repository.EntityAliasRepository;
import com.rag.rag.knowledge.repository.EntityMentionRepository;
import com.rag.rag.knowledge.repository.FactRepository;
import com.rag.rag.knowledge.repository.IdentitySuggestionRepository;
import com.rag.rag.knowledge.repository.KnowledgeEntityRepository;
import com.rag.rag.knowledge.repository.FaceEmbeddingRepository;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class IdentityResolutionService {

    private final KnowledgeEntityRepository entityRepository;
    private final EntityAliasRepository aliasRepository;
    private final EntityMentionRepository mentionRepository;
    private final IdentitySuggestionRepository suggestionRepository;
    private final FaceEmbeddingRepository faceEmbeddingRepository;
    private final FactRepository factRepository;
    private final FileRepository fileRepository;
    private final CurrentUserService currentUserService;
    private final ChatLanguageModel chatModel;
    private final IdentityMatchCacheService identityMatchCacheService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${identity.llm-matcher.enabled:false}")
    private boolean llmMatcherEnabled;

    @Value("${identity.llm-matcher.max-candidates:2}")
    private int llmMatcherMaxCandidates;

    @Value("${identity.description-auto-confirm-threshold:0.85}")
    private double autoConfirmThreshold = 0.85;

    @Value("${identity.description-suggest-threshold:0.60}")
    private double suggestThreshold = 0.60;

    /** Max mentions scored after type/folder/token pre-filter (heuristic path). */
    @Value("${identity.heuristic.max-candidates:40}")
    private int heuristicMaxCandidates = 40;

    public IdentityResolutionService(
            KnowledgeEntityRepository entityRepository,
            EntityAliasRepository aliasRepository,
            EntityMentionRepository mentionRepository,
            IdentitySuggestionRepository suggestionRepository,
            FaceEmbeddingRepository faceEmbeddingRepository,
            FactRepository factRepository,
            FileRepository fileRepository,
            CurrentUserService currentUserService,
            @Qualifier("chatLanguageModel") ChatLanguageModel chatModel,
            IdentityMatchCacheService identityMatchCacheService
    ) {
        this.entityRepository = entityRepository;
        this.aliasRepository = aliasRepository;
        this.mentionRepository = mentionRepository;
        this.suggestionRepository = suggestionRepository;
        this.faceEmbeddingRepository = faceEmbeddingRepository;
        this.factRepository = factRepository;
        this.fileRepository = fileRepository;
        this.currentUserService = currentUserService;
        this.chatModel = chatModel;
        this.identityMatchCacheService = identityMatchCacheService;
    }

    @Transactional
    public void resolve(EntityMention mention, String fileEntityTag, String entityType) {
        long started = System.nanoTime();
        try {
            resolveInternal(mention, fileEntityTag, entityType);
        } finally {
            long ms = (System.nanoTime() - started) / 1_000_000L;
            if (ms >= 50) {
                log.info("Identity resolve took {} ms for mention label='{}' path='{}'",
                        ms, mention != null ? mention.getLabel() : null,
                        mention != null ? mention.getFilePath() : null);
            } else {
                log.debug("Identity resolve took {} ms for mention label='{}'",
                        ms, mention != null ? mention.getLabel() : null);
            }
        }
    }

    private void resolveInternal(EntityMention mention, String fileEntityTag, String entityType) {
        String type = LivingEntityTypes.normalize(entityType);
        if (type == null) {
            log.debug("Ignoring unsupported entity type '{}' for mention '{}'", entityType, mention.getLabel());
            return;
        }
        UUID ownerId = resolveOwnerId(mention);

        if (fileEntityTag != null && !fileEntityTag.isBlank()) {
            linkMention(mention, findOrCreateEntityByName(fileEntityTag, type, ownerId), MentionStatus.CONFIRMED,
                    IdentityEvidenceSource.USER_TAG, 1.0, null);
            return;
        }

        String label = normalizeLabel(mention.getLabel());
        if (label == null) {
            mention.setStatus(MentionStatus.SUGGESTED);
            mentionRepository.save(mention);
            return;
        }

        if (!isGenericLabel(label)) {
            Optional<KnowledgeEntity> exactMatch = findEntityByNameOrAlias(label, type, ownerId);
            if (exactMatch.isPresent()) {
                linkMention(mention, exactMatch.get(), MentionStatus.CONFIRMED,
                        IdentityEvidenceSource.DESCRIPTION_MATCH, 1.0, null);
                return;
            }
        }

        // Vision produces neutral descriptions ("person 1", clothing, pose),
        // which must remain observations until a user names or confirms them.
        if (LivingEntityTypes.PERSON.equals(type)
                && (isGenericLabel(label) || !looksLikePersonName(label))) {
            mention.setStatus(MentionStatus.PENDING);
            mentionRepository.save(mention);
            return;
        }

        EntityMention bestSuggestionCandidate = null;
        double bestSuggestionScore = 0.0;
        EntityMention bestConfirmCandidate = null;
        double bestConfirmScore = 0.0;
        List<CandidateScore> heuristicCandidates = new ArrayList<>();

        List<EntityMention> prefiltered = loadAndPrefilterCandidates(mention, type, ownerId);
        for (EntityMention candidate : prefiltered) {
            double score = computeHeuristicSimilarity(mention, candidate);
            heuristicCandidates.add(new CandidateScore(candidate, score));
            if (score >= autoConfirmThreshold && candidate.getEntity() != null && score > bestConfirmScore) {
                bestConfirmCandidate = candidate;
                bestConfirmScore = score;
                continue;
            }
            if (score >= suggestThreshold && score > bestSuggestionScore) {
                bestSuggestionCandidate = candidate;
                bestSuggestionScore = score;
            }
        }

        if (bestConfirmCandidate == null && llmMatcherEnabled) {
            List<CandidateScore> llmCandidates = heuristicCandidates.stream()
                    .sorted((a, b) -> Double.compare(b.score(), a.score()))
                    .limit(Math.max(0, llmMatcherMaxCandidates))
                    .toList();
            for (CandidateScore candidateScore : llmCandidates) {
                double score = computeLlmSimilarity(mention, candidateScore.candidate());
                if (score >= autoConfirmThreshold && candidateScore.candidate().getEntity() != null
                        && score > bestConfirmScore) {
                    bestConfirmCandidate = candidateScore.candidate();
                    bestConfirmScore = score;
                    continue;
                }
                if (score >= suggestThreshold && score > bestSuggestionScore) {
                    bestSuggestionCandidate = candidateScore.candidate();
                    bestSuggestionScore = score;
                }
            }
        }

        if (bestConfirmCandidate != null && bestConfirmCandidate.getEntity() != null) {
            linkMention(mention, bestConfirmCandidate.getEntity(), MentionStatus.CONFIRMED,
                    IdentityEvidenceSource.DESCRIPTION_MATCH, bestConfirmScore, null);
            return;
        }
        if (bestSuggestionCandidate != null) {
            saveSuggestion(mention, bestSuggestionCandidate, bestSuggestionScore);
        }

        Optional<KnowledgeEntity> existing = looksLikePersonName(label)
                ? findEntityByNameOrAlias(label, type, ownerId)
                : Optional.empty();
        if (existing.isPresent()) {
            linkMention(mention, existing.get(), MentionStatus.CONFIRMED,
                    IdentityEvidenceSource.DESCRIPTION_MATCH, 1.0, null);
            return;
        }

        KnowledgeEntity entity = looksLikePersonName(label)
                ? findOrCreateEntityByName(label, type, ownerId)
                : createSuggestedEntity(label, type, ownerId);
        boolean named = looksLikePersonName(label);
        linkMention(mention, entity, named ? MentionStatus.CONFIRMED : MentionStatus.SUGGESTED,
                named ? IdentityEvidenceSource.DESCRIPTION_MATCH : null,
                named ? 1.0 : null, null);
    }

    /**
     * Loads type/owner-scoped mentions, applies folder/token pre-filter, caps count.
     * Package-visible for unit tests.
     */
    List<EntityMention> loadAndPrefilterCandidates(EntityMention mention, String type, UUID ownerId) {
        List<EntityMention> scoped = ownerId != null
                ? mentionRepository.findLinkedByEntityTypeAndOwner(type, ownerId)
                : mentionRepository.findLinkedByEntityTypeWithoutOwner(type);

        return scoped.stream()
                .filter(candidate -> candidate.getId() != null && mention.getId() != null
                        && !candidate.getId().equals(mention.getId()))
                .filter(candidate -> candidate.getEntity() != null)
                .filter(candidate -> type.equals(LivingEntityTypes.normalize(candidate.getEntity().getType())))
                .filter(candidate -> sameOwner(candidate.getEntity(), ownerId))
                .filter(candidate -> passesIdentityPreFilter(mention, candidate))
                .sorted(Comparator
                        .comparing((EntityMention c) -> sameFolder(mention, c) ? 0 : 1)
                        .thenComparing(c -> c.getLabel() == null ? "" : c.getLabel(), String.CASE_INSENSITIVE_ORDER))
                .limit(Math.max(1, heuristicMaxCandidates))
                .toList();
    }

    /**
     * Keep candidates that share folder path prefix, exact label, or label/visual-cue tokens.
     * Package-visible for unit tests.
     */
    boolean passesIdentityPreFilter(EntityMention mention, EntityMention candidate) {
        if (mention == null || candidate == null) {
            return false;
        }
        if (sameFolder(mention, candidate)) {
            return true;
        }
        String labelA = normalizeLabel(mention.getLabel());
        String labelB = normalizeLabel(candidate.getLabel());
        if (labelA != null && labelA.equalsIgnoreCase(labelB)) {
            return true;
        }
        Set<String> tokensA = tokenize(labelA);
        Set<String> tokensB = tokenize(labelB);
        if (!tokensA.isEmpty() && !tokensB.isEmpty()) {
            for (String t : tokensA) {
                if (tokensB.contains(t)) {
                    return true;
                }
            }
        }
        Set<String> cuesA = tokenizeVisualCues(mention.getVisualCues());
        Set<String> cuesB = tokenizeVisualCues(candidate.getVisualCues());
        if (!cuesA.isEmpty() && !cuesB.isEmpty()) {
            for (String t : cuesA) {
                if (cuesB.contains(t)) {
                    return true;
                }
            }
        }
        return false;
    }

    boolean sameFolder(EntityMention a, EntityMention b) {
        String fa = folderPrefix(a != null ? a.getFilePath() : null);
        String fb = folderPrefix(b != null ? b.getFilePath() : null);
        return !fa.isEmpty() && fa.equals(fb);
    }

    static String folderPrefix(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(0, slash + 1) : path;
    }

    @Transactional
    public KnowledgeEntity findOrCreateEntityByName(String name, String type) {
        return findOrCreateEntityByName(name, type, resolveOwnerId(null));
    }

    @Transactional
    public KnowledgeEntity findOrCreateEntityByName(String name, String type, UUID ownerId) {
        String normalized = normalizeLabel(name);
        if (normalized == null) {
            throw new IllegalArgumentException("Entity name cannot be blank");
        }
        if (isGenericLabel(normalized)) {
            throw new IllegalArgumentException(
                    "Cannot create a person from a vision placeholder (e.g. person 1 / osoba 2)");
        }

        String normalizedType = LivingEntityTypes.normalize(type);
        if (normalizedType == null) {
            throw new IllegalArgumentException("Entity type must be PERSON or ANIMAL");
        }

        UUID effectiveOwner = ownerId != null ? ownerId : resolveOwnerId(null);
        Optional<KnowledgeEntity> existing = findEntityByNameOrAlias(normalized, normalizedType, effectiveOwner);
        if (existing.isPresent()) {
            return existing.get();
        }

        KnowledgeEntity newEntity = KnowledgeEntity.builder()
                .displayName(normalized)
                .type(normalizedType)
                .ownerId(effectiveOwner)
                .build();
        newEntity = entityRepository.save(newEntity);

        EntityAlias newAlias = EntityAlias.builder()
                .entity(newEntity)
                .alias(normalized)
                .source(AliasSource.AUTO)
                .build();
        aliasRepository.save(newAlias);

        return newEntity;
    }

    @Transactional
    public KnowledgeEntity createUnconfirmedEntity(String name, String type) {
        String normalizedName = normalizeLabel(name);
        String normalizedType = LivingEntityTypes.normalize(type);
        if (normalizedName == null) {
            throw new IllegalArgumentException("Entity name cannot be blank");
        }
        if (normalizedType == null) {
            throw new IllegalArgumentException("Entity type must be PERSON or ANIMAL");
        }
        return createSuggestedEntity(normalizedName, normalizedType, resolveOwnerId(null));
    }

    @Transactional
    public int consolidateDuplicateEntities() {
        UUID ownerId = resolveOwnerId(null);
        Map<String, List<KnowledgeEntity>> grouped = new LinkedHashMap<>();
        List<KnowledgeEntity> scope = ownerId != null
                ? entityRepository.findAllByOwnerId(ownerId)
                : entityRepository.findAll();
        for (KnowledgeEntity entity : scope) {
            String normalizedType = LivingEntityTypes.normalize(entity.getType());
            String normalizedName = normalizeLabel(entity.getDisplayName());
            if (normalizedType == null || normalizedName == null) {
                continue;
            }
            String ownerKey = entity.getOwnerId() == null ? "null" : entity.getOwnerId().toString();
            String key = ownerKey + "\u0000" + normalizedType + "\u0000" + normalizedName.toLowerCase(Locale.ROOT);
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(entity);
        }

        int mergedCount = 0;
        for (List<KnowledgeEntity> duplicates : grouped.values()) {
            if (duplicates.size() <= 1) {
                continue;
            }

            KnowledgeEntity canonical = duplicates.get(0);
            for (int i = 1; i < duplicates.size(); i++) {
                mergeEntityInto(canonical, duplicates.get(i));
                mergedCount++;
            }
        }

        return mergedCount;
    }

    private void mergeEntityInto(KnowledgeEntity target, KnowledgeEntity duplicate) {
        faceEmbeddingRepository.relinkEntity(duplicate.getId(), target);
        for (EntityMention mention : mentionRepository.findByEntityId(duplicate.getId())) {
            mention.setEntity(target);
            mention.setStatus(MentionStatus.CONFIRMED);
            mentionRepository.save(mention);
        }

        for (EntityAlias alias : aliasRepository.findAll()) {
            if (alias.getEntity() == null || !duplicate.getId().equals(alias.getEntity().getId())) {
                continue;
            }
            boolean aliasExists = aliasRepository
                    .findFirstByAliasIgnoreCaseAndEntity_TypeIgnoreCase(alias.getAlias(), target.getType())
                    .filter(existing -> existing.getEntity().getId().equals(target.getId()))
                    .isPresent();
            if (aliasExists) {
                aliasRepository.delete(alias);
            } else {
                alias.setEntity(target);
                aliasRepository.save(alias);
            }
        }

        entityRepository.delete(duplicate);
        log.info("Merged duplicate entity '{}' into '{}'", duplicate.getDisplayName(), target.getDisplayName());
    }

    private Optional<KnowledgeEntity> findEntityByNameOrAlias(String name, String type, UUID ownerId) {
        if (ownerId != null) {
            Optional<KnowledgeEntity> byName = entityRepository
                    .findFirstByDisplayNameIgnoreCaseAndTypeIgnoreCaseAndOwnerId(name, type, ownerId);
            if (byName.isPresent()) {
                return byName;
            }
            return aliasRepository
                    .findFirstByAliasIgnoreCaseAndEntity_TypeIgnoreCaseAndEntity_OwnerId(name, type, ownerId)
                    .map(EntityAlias::getEntity);
        }

        // No owner context: only match legacy entities without owner (never cross-user).
        return entityRepository.findFirstByDisplayNameIgnoreCaseAndTypeIgnoreCase(name, type)
                .filter(entity -> entity.getOwnerId() == null);
    }

    private UUID resolveOwnerId(EntityMention mention) {
        Optional<UUID> fromSecurity = currentUserService.findUserId();
        if (fromSecurity.isPresent()) {
            return fromSecurity.get();
        }
        if (mention != null && mention.getFilePath() != null) {
            return fileRepository.findByPath(mention.getFilePath())
                    .map(file -> file.getOwnerId())
                    .orElse(null);
        }
        return null;
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

    private void linkMention(EntityMention mention, KnowledgeEntity entity, MentionStatus status,
                             IdentityEvidenceSource source, Double identityConfidence, Double identityMargin) {
        mention.setEntity(entity);
        mention.setStatus(status);
        mention.setIdentitySource(source);
        mention.setIdentityConfidence(decimal(identityConfidence));
        mention.setIdentityMargin(decimal(identityMargin));
        mentionRepository.save(mention);
        if (mention.getId() != null) {
            faceEmbeddingRepository.relinkByMentionId(mention.getId(), entity);
        }
    }

    @Transactional
    public void confirmUserAssignment(EntityMention mention, KnowledgeEntity entity) {
        if (mention == null || entity == null) {
            return;
        }
        EntityMention managedMention = mention.getId() == null
                ? mention
                : mentionRepository.findById(mention.getId()).orElse(mention);
        KnowledgeEntity managedEntity = entity.getId() == null
                ? entity
                : entityRepository.findById(entity.getId()).orElse(entity);
        linkMention(managedMention, managedEntity, MentionStatus.CONFIRMED,
                IdentityEvidenceSource.USER, 1.0, null);
        removeGenericAliases(managedEntity);
    }

    /**
     * Rename a person entity and keep graph/album consistent:
     * mention labels, fact object labels, aliases, merge-on-collision.
     */
    @Transactional
    public KnowledgeEntity renameNamedEntity(UUID entityId, String newName) {
        KnowledgeEntity entity = entityRepository.findById(entityId)
                .orElseThrow(() -> new IllegalArgumentException("Entity not found"));
        String oldName = entity.getDisplayName();
        String normalized = normalizeLabel(newName);
        if (normalized == null) {
            throw new IllegalArgumentException("Entity name cannot be blank");
        }
        if (isGenericLabel(normalized)) {
            throw new IllegalArgumentException("Cannot rename to a generic vision label");
        }

        String type = LivingEntityTypes.normalize(entity.getType());
        if (type == null) {
            type = LivingEntityTypes.PERSON;
        }

        Optional<KnowledgeEntity> existing = findEntityByNameOrAlias(normalized, type, entity.getOwnerId());
        if (existing.isPresent() && !existing.get().getId().equals(entityId)) {
            KnowledgeEntity target = existing.get();
            mergeEntityInto(target, entity);
            refreshMentionAndFactLabels(target.getId(), oldName, target.getDisplayName());
            removeGenericAliases(target);
            return target;
        }

        entity.setDisplayName(normalized);
        entityRepository.save(entity);
        refreshMentionAndFactLabels(entityId, oldName, normalized);
        if (oldName != null && !isGenericLabel(oldName) && !normalized.equalsIgnoreCase(oldName)) {
            addAliasIfMissing(entity, oldName);
        }
        removeGenericAliases(entity);
        return entity;
    }

    /**
     * After a mention is named / reassigned by the user: keep fact object labels and
     * clean orphan previous entities that would still pollute the album/graph.
     */
    @Transactional
    public void afterMentionIdentityAssigned(UUID mentionId, UUID previousEntityId, String newLabel) {
        if (mentionId == null) {
            return;
        }
        EntityMention mention = mentionRepository.findById(mentionId).orElse(null);
        if (mention == null) {
            return;
        }
        String label = normalizeLabel(newLabel);
        if (label != null) {
            String oldLabel = mention.getLabel();
            mention.setLabel(label);
            mentionRepository.save(mention);
            syncFactObjectLabelsForMention(mention, label, oldLabel);
        }
        if (mention.getEntity() != null) {
            removeGenericAliases(mention.getEntity());
        }
        if (previousEntityId != null
                && (mention.getEntity() == null || !previousEntityId.equals(mention.getEntity().getId()))) {
            cleanupOrphanEntity(previousEntityId);
        }
    }

    private void refreshMentionAndFactLabels(UUID entityId, String oldName, String newName) {
        for (EntityMention mention : mentionRepository.findByEntityId(entityId)) {
            String label = mention.getLabel();
            if (label == null || label.isBlank() || isGenericLabel(label)
                    || (oldName != null && oldName.equalsIgnoreCase(label))) {
                mention.setLabel(newName);
                mentionRepository.save(mention);
            }
            syncFactObjectLabelsForMention(mention, newName, oldName);
        }
        // Catch facts that still point at this entity via targetMention after reassignment/merge.
        for (Fact fact : factRepository.findAllWithMentionAndEntity()) {
            if (fact.getTargetMention() != null
                    && fact.getTargetMention().getEntity() != null
                    && entityId.equals(fact.getTargetMention().getEntity().getId())) {
                fact.setObject(newName);
                factRepository.save(fact);
            }
        }
    }

    private void syncFactObjectLabelsForMention(EntityMention mention, String newLabel, String oldLabel) {
        if (mention == null || mention.getId() == null || newLabel == null || mention.getFilePath() == null) {
            return;
        }
        for (Fact fact : factRepository.findByFilePath(mention.getFilePath())) {
            boolean isTarget = fact.getTargetMention() != null
                    && mention.getId().equals(fact.getTargetMention().getId());
            boolean objectIsStalePlaceholder = fact.getObject() != null && (
                    isGenericLabel(fact.getObject())
                            || (oldLabel != null && oldLabel.equalsIgnoreCase(fact.getObject()))
            );
            if (isTarget) {
                fact.setObject(newLabel);
                factRepository.save(fact);
            } else if (objectIsStalePlaceholder
                    && fact.getTargetMention() == null
                    && fact.getMention() != null
                    && mention.getId().equals(fact.getMention().getId())) {
                // Subject fact with vision placeholder object and no linked target — keep in sync.
                fact.setObject(newLabel);
                factRepository.save(fact);
            }
        }
    }

    private void removeGenericAliases(KnowledgeEntity entity) {
        if (entity == null || entity.getId() == null) {
            return;
        }
        for (EntityAlias alias : aliasRepository.findAll()) {
            if (alias.getEntity() != null
                    && entity.getId().equals(alias.getEntity().getId())
                    && isGenericLabel(alias.getAlias())) {
                aliasRepository.delete(alias);
            }
        }
    }

    private void cleanupOrphanEntity(UUID entityId) {
        if (entityId == null) {
            return;
        }
        boolean hasRemainingMentions = !mentionRepository.findByEntityId(entityId).isEmpty();
        boolean hasUserAlias = aliasRepository.existsByEntityIdAndSource(entityId, AliasSource.USER);
        if (!OrphanEntityCleanupPolicy.shouldDeleteOrphan(hasRemainingMentions, hasUserAlias)) {
            if (!hasRemainingMentions && hasUserAlias) {
                log.debug("Kept entity {} — no mentions but has USER alias", entityId);
            }
            return;
        }
        KnowledgeEntity entity = entityRepository.findById(entityId).orElse(null);
        if (entity == null) {
            return;
        }
        faceEmbeddingRepository.deleteByEntityId(entityId);
        aliasRepository.deleteByEntityId(entityId);
        entityRepository.delete(entity);
        log.info("Removed orphan entity '{}' ({})", entity.getDisplayName(), entityId);
    }

    @Transactional
    public KnowledgeEntity createUnresolvedPerson(String label) {
        String normalized = normalizeLabel(label);
        if (normalized == null) {
            throw new IllegalArgumentException("Unresolved person label cannot be blank");
        }
        return createSuggestedEntity(normalized, "PERSON", resolveOwnerId(null));
    }

    private void saveSuggestion(EntityMention mention, EntityMention candidate, double score) {
        if (mention == null || candidate == null || mention.getId().equals(candidate.getId())) {
            return;
        }
        if (suggestionRepository.existsBetweenMentions(mention.getId(), candidate.getId())) {
            return;
        }
        IdentitySuggestion suggestion = IdentitySuggestion.builder()
                .mentionA(mention)
                .mentionB(candidate)
                .similarityScore(BigDecimal.valueOf(score))
                .status(SuggestionStatus.PENDING)
                .build();
        suggestionRepository.save(suggestion);
    }

    @Transactional
    public void confirmFaceMatch(EntityMention mention, KnowledgeEntity matchedEntity, String visionLabel,
                                 double identityConfidence, double identityMargin) {
        if (mention == null || matchedEntity == null) {
            return;
        }
        // Re-attach when callers pass a mention from another persistence context.
        EntityMention managedMention = mention.getId() == null
                ? mention
                : mentionRepository.findById(mention.getId()).orElse(mention);
        KnowledgeEntity managedEntity = matchedEntity.getId() == null
                ? matchedEntity
                : entityRepository.findById(matchedEntity.getId()).orElse(matchedEntity);
        linkMention(managedMention, managedEntity, MentionStatus.CONFIRMED,
                IdentityEvidenceSource.FACE_MATCH, identityConfidence, identityMargin);
        addAliasIfMissing(managedEntity, visionLabel);
    }

    public void confirmFaceMatch(EntityMention mention, KnowledgeEntity matchedEntity, String visionLabel) {
        confirmFaceMatch(mention, matchedEntity, visionLabel, 1.0, 1.0);
    }

    private BigDecimal decimal(Double value) {
        if (value == null || !Double.isFinite(value)) {
            return null;
        }
        return BigDecimal.valueOf(Math.max(0.0, Math.min(1.0, value))).setScale(3, java.math.RoundingMode.HALF_UP);
    }

    public void suggestFaceMatch(EntityMention mention, KnowledgeEntity matchedEntity, double score) {
        if (mention == null || matchedEntity == null) {
            return;
        }
        if (mention.getEntity() != null && mention.getEntity().getId().equals(matchedEntity.getId())) {
            return;
        }

        Optional<EntityMention> candidateMention = mentionRepository.findByEntityId(matchedEntity.getId()).stream()
                .filter(other -> !other.getId().equals(mention.getId()))
                .findFirst();
        if (candidateMention.isEmpty()) {
            return;
        }

        if (suggestionRepository.existsBetweenMentions(mention.getId(), candidateMention.get().getId())) {
            return;
        }

        saveSuggestion(mention, candidateMention.get(), score);
    }

    private void addAliasIfMissing(KnowledgeEntity entity, String aliasLabel) {
        String normalized = normalizeLabel(aliasLabel);
        if (normalized == null || isGenericLabel(normalized)) {
            return;
        }
        if (normalized.equalsIgnoreCase(entity.getDisplayName())) {
            return;
        }
        boolean exists = aliasRepository
                .findFirstByAliasIgnoreCaseAndEntity_TypeIgnoreCase(normalized, entity.getType())
                .filter(alias -> alias.getEntity().getId().equals(entity.getId()))
                .isPresent();
        if (exists) {
            return;
        }
        aliasRepository.save(EntityAlias.builder()
                .entity(entity)
                .alias(normalized)
                .source(AliasSource.AUTO)
                .build());
    }

    public boolean isGenericPersonLabel(String label) {
        return isGenericLabel(label);
    }

    public boolean looksLikePersonName(String label) {
        if (isGenericLabel(label)) {
            return false;
        }
        String lower = label.toLowerCase(Locale.ROOT);
        if (lower.contains("mężczyzna") || lower.contains("mezczyzna")
                || lower.contains("kobieta") || lower.contains("koszul")
                || lower.contains("spodn") || lower.contains("włos") || lower.contains("wlos")
                || lower.contains("chłopak") || lower.contains("chlopak")
                || lower.contains("dziewczyn") || lower.contains("osoba ")) {
            return false;
        }
        return label.trim().split("\\s+").length <= 2;
    }

    private KnowledgeEntity createSuggestedEntity(String label, String type, UUID ownerId) {
        KnowledgeEntity entity = KnowledgeEntity.builder()
                .displayName(label)
                .type(type)
                .ownerId(ownerId)
                .build();
        entity = entityRepository.save(entity);
        EntityAlias alias = EntityAlias.builder()
                .entity(entity)
                .alias(label)
                .source(AliasSource.AUTO)
                .build();
        aliasRepository.save(alias);
        return entity;
    }

    private double computeHeuristicSimilarity(EntityMention a, EntityMention b) {
        if (a.getEntity() != null && b.getEntity() != null
                && a.getEntity().getId().equals(b.getEntity().getId())) {
            return 1.0;
        }

        String labelA = normalizeLabel(a.getLabel());
        String labelB = normalizeLabel(b.getLabel());
        if (labelA != null && labelA.equalsIgnoreCase(labelB)) {
            return 0.99;
        }

        if (isGenericLabel(labelA) || isGenericLabel(labelB)) {
            return 0.0;
        }

        Set<String> labelTokensA = tokenize(labelA);
        Set<String> labelTokensB = tokenize(labelB);
        double labelScore = jaccard(labelTokensA, labelTokensB);
        if (labelA.toLowerCase(Locale.ROOT).contains(labelB.toLowerCase(Locale.ROOT))
                || labelB.toLowerCase(Locale.ROOT).contains(labelA.toLowerCase(Locale.ROOT))) {
            labelScore = Math.max(labelScore, 0.70);
        }

        Set<String> cuesA = tokenizeVisualCues(a.getVisualCues());
        Set<String> cuesB = tokenizeVisualCues(b.getVisualCues());
        double cuesScore = jaccard(cuesA, cuesB);

        return 0.7 * labelScore + 0.3 * cuesScore;
    }

    private double computeLlmSimilarity(EntityMention a, EntityMention b) {
        String cacheKey = descriptionPairCacheKey(a, b);
        if (identityMatchCacheService != null && cacheKey != null) {
            Optional<IdentityMatchCacheService.CachedIdentityMatch> cached = identityMatchCacheService.get(cacheKey);
            if (cached.isPresent()) {
                if (cached.get().isNegative()) {
                    return 0.0;
                }
                return cached.get().score();
            }
        }

        String prompt = String.format(
                "Are these two descriptions about the same person? " +
                        "Person A: %s, cues: %s. Person B: %s, cues: %s. " +
                        "Return STRICTLY JSON: {\"same\": boolean, \"confidence\": float (0.0 to 1.0)}",
                a.getLabel(), a.getVisualCues(), b.getLabel(), b.getVisualCues()
        );

        double score = 0.0;
        try {
            String response = chatModel.generate(prompt);
            String jsonContent = extractJson(response);
            JsonNode node = objectMapper.readTree(jsonContent);
            if (node.has("same") && node.get("same").asBoolean()) {
                score = node.has("confidence") ? node.get("confidence").asDouble() : 0.80;
            }
        } catch (Exception e) {
            log.warn("LLM matching failed", e);
        }

        if (identityMatchCacheService != null && cacheKey != null) {
            if (score <= 0.0) {
                identityMatchCacheService.putMiss(cacheKey);
            } else {
                UUID entityId = b.getEntity() != null ? b.getEntity().getId() : null;
                identityMatchCacheService.putHit(cacheKey, entityId, score, score, 0.0);
            }
        }
        return score;
    }

    private String descriptionPairCacheKey(EntityMention a, EntityMention b) {
        if (a == null || b == null) {
            return null;
        }
        String left = normalizeLabel(a.getLabel()) + "|" + nullToEmpty(a.getVisualCues());
        String right = normalizeLabel(b.getLabel()) + "|" + nullToEmpty(b.getVisualCues());
        // Order-independent pair key for A↔B description match
        String pair = left.compareToIgnoreCase(right) <= 0 ? left + "||" + right : right + "||" + left;
        return identityMatchCacheService.buildKey(null, "desc:" + pair, autoConfirmThreshold);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private Set<String> tokenizeVisualCues(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        try {
            JsonNode node = objectMapper.readTree(value);
            if (node.isArray()) {
                Set<String> tokens = new HashSet<>();
                for (JsonNode item : node) {
                    if (item.isTextual()) {
                        tokens.addAll(tokenize(item.asText()));
                    }
                }
                return tokens;
            }
        } catch (Exception ignored) {
        }
        return tokenize(value);
    }

    private Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^\\p{L}0-9]+"))
                .map(String::trim)
                .filter(token -> token.length() >= 3)
                .collect(Collectors.toSet());
    }

    private double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }
        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        if (intersection.isEmpty()) {
            return 0.0;
        }
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return (double) intersection.size() / union.size();
    }

    private String normalizeLabel(String label) {
        if (label == null) {
            return null;
        }
        String normalized = label.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private boolean isGenericLabel(String label) {
        if (label == null) {
            return true;
        }
        String lower = label.toLowerCase(Locale.ROOT).trim();
        // Vision uses stable placeholders (person 1 / osoba 2) — never treat as identity names.
        return lower.startsWith("nieznana")
                || lower.startsWith("nieznany")
                || lower.startsWith("unknown")
                || lower.matches("osoba\\s*\\d*")
                || lower.matches("person\\s*\\d*")
                || lower.matches("people\\s*\\d*")
                || lower.matches("man\\s*\\d*")
                || lower.matches("woman\\s*\\d*")
                || lower.equals("osoba")
                || lower.equals("person")
                || lower.equals("people")
                || lower.equals("man")
                || lower.equals("woman")
                || lower.equals("boy")
                || lower.equals("girl")
                || lower.equals("postać")
                || lower.equals("postac")
                || lower.equals("figure")
                || lower.equals("individual");
    }

    private String extractJson(String text) {
        if (text == null || text.isBlank()) {
            return "{}";
        }

        String trimmed = text.trim();
        if (trimmed.contains("```json")) {
            int start = trimmed.indexOf("```json") + 7;
            int end = trimmed.indexOf("```", start);
            if (end > start) {
                trimmed = trimmed.substring(start, end).trim();
            }
        } else if (trimmed.contains("```")) {
            int start = trimmed.indexOf("```") + 3;
            int end = trimmed.indexOf("```", start);
            if (end > start) {
                trimmed = trimmed.substring(start, end).trim();
            }
        }

        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    private record CandidateScore(EntityMention candidate, double score) {}
}

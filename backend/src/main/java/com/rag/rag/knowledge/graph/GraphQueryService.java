package com.rag.rag.knowledge.graph;

import com.rag.rag.auth.security.CurrentUserService;
import com.rag.rag.folder.entity.FileEntity;
import com.rag.rag.folder.repository.FileRepository;
import com.rag.rag.knowledge.entity.EntityMention;
import com.rag.rag.knowledge.entity.KnowledgeEntity;
import com.rag.rag.knowledge.entity.MentionStatus;
import com.rag.rag.knowledge.fact.Fact;
import com.rag.rag.knowledge.repository.EntityMentionRepository;
import com.rag.rag.knowledge.repository.FactRepository;
import com.rag.rag.knowledge.repository.KnowledgeEntityRepository;
import com.rag.rag.knowledge.identity.IdentityResolutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Reads graph evidence selected by the query planner.  This service never
 * interprets the wording of a user question; entities and file paths are
 * already validated planner output.
 *
 * <p>AGENTS.md principle 2: only certain (CONFIRMED, confidence ≥ threshold)
 * mentions and facts are exposed as graph evidence / sources.</p>
 */
@Service
@RequiredArgsConstructor
public class GraphQueryService {
    private final KnowledgeEntityRepository entityRepository;
    private final EntityMentionRepository mentionRepository;
    private final FileRepository fileRepository;
    private final FactRepository factRepository;
    private final MentionEvidencePolicy mentionEvidencePolicy;
    private final IdentityResolutionService identityResolutionService;
    private final CurrentUserService currentUserService;

    @Value("${rag.graph.min-fact-confidence:0.75}")
    private double minFactConfidence = 0.75;

    @Transactional(readOnly = true)
    public List<String> availableEntityNames() {
        return ownedEntities().stream()
                .filter(entity -> !"PERSON".equalsIgnoreCase(entity.getType())
                        || !identityResolutionService.isGenericPersonLabel(entity.getDisplayName()))
                .filter(entity -> mentionRepository.findByEntityId(entity.getId()).stream()
                        .anyMatch(this::isCertainMention))
                .map(KnowledgeEntity::getDisplayName)
                .filter(name -> name != null && !name.isBlank())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<String> validateEntityNames(List<String> requestedNames) {
        if (requestedNames == null || requestedNames.isEmpty()) return List.of();
        List<KnowledgeEntity> entities = ownedEntities();
        List<String> result = new ArrayList<>();
        for (String requested : requestedNames) {
            if (requested == null || requested.isBlank()) continue;
            entities.stream().map(KnowledgeEntity::getDisplayName)
                    .filter(name -> name.equalsIgnoreCase(requested.trim()))
                    .findFirst().ifPresent(result::add);
        }
        return result.stream().distinct().toList();
    }

    @Transactional(readOnly = true)
    public List<String> validateFilePaths(List<String> requestedPaths) {
        if (requestedPaths == null || requestedPaths.isEmpty()) return List.of();
        UUID ownerId = currentUserService.findUserId().orElse(null);
        return requestedPaths.stream()
                .filter(path -> path != null && !path.isBlank())
                .filter(path -> ownerId == null
                        ? fileRepository.findByPath(path).isPresent()
                        : fileRepository.findByPathAndOwnerId(path, ownerId).isPresent())
                .distinct().toList();
    }

    @Transactional(readOnly = true)
    public List<String> imagePathsForEntities(List<String> entityNames) {
        if (entityNames == null || entityNames.isEmpty()) return List.of();
        Set<String> paths = new LinkedHashSet<>();
        for (String name : validateEntityNames(entityNames)) {
            findOwnedEntityByName(name).ifPresent(entity ->
                    mentionRepository.findByEntityId(entity.getId()).stream()
                            .filter(this::isCertainMention)
                            .map(EntityMention::getFilePath)
                            .filter(this::isImagePath)
                            .forEach(paths::add));
        }
        return List.copyOf(paths);
    }

    @Transactional(readOnly = true)
    public List<String> imagePathsForAllEntities(List<String> entityNames) {
        List<String> validated = validateEntityNames(entityNames);
        if (validated.isEmpty()) return List.of();

        Set<String> intersection = null;
        for (String name : validated) {
            Optional<KnowledgeEntity> entity = findOwnedEntityByName(name);
            if (entity.isEmpty()) return List.of();
            Set<String> paths = mentionRepository.findByEntityId(entity.get().getId()).stream()
                    .filter(this::isCertainMention)
                    .map(EntityMention::getFilePath)
                    .filter(this::isImagePath)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            if (intersection == null) intersection = paths;
            else intersection.retainAll(paths);
            if (intersection.isEmpty()) return List.of();
        }
        return intersection == null ? List.of() : List.copyOf(intersection);
    }

    private List<KnowledgeEntity> ownedEntities() {
        UUID ownerId = currentUserService.findUserId().orElse(null);
        if (ownerId == null) {
            return List.of();
        }
        return entityRepository.findAllByOwnerId(ownerId);
    }

    private Optional<KnowledgeEntity> findOwnedEntityByName(String name) {
        UUID ownerId = currentUserService.findUserId().orElse(null);
        if (ownerId == null || name == null || name.isBlank()) {
            return Optional.empty();
        }
        return entityRepository.findFirstByDisplayNameIgnoreCaseAndOwnerId(name.trim(), ownerId);
    }

    @Transactional(readOnly = true)
    public GraphEvidenceResult buildEvidence(List<String> entityNames, List<String> fileScope,
                                             EntityMatchMode entityMatchMode) {
        List<String> validatedEntities = validateEntityNames(entityNames);
        List<String> validatedScope = validateFilePaths(fileScope);
        EntityMatchMode mode = entityMatchMode == null ? EntityMatchMode.ANY : entityMatchMode;
        LinkedHashSet<String> certainPaths = new LinkedHashSet<>();
        List<String> contexts = new ArrayList<>();

        if (mode == EntityMatchMode.ALL_SAME_FILE && !validatedEntities.isEmpty()) {
            certainPaths.addAll(imagePathsForAllEntities(validatedEntities));
            if (!validatedScope.isEmpty()) certainPaths.retainAll(validatedScope);
            for (String path : certainPaths) {
                contexts.add("- współwystępowanie=" + String.join(", ", validatedEntities) + "; file=" + path);
                String fileContext = buildFullContextForFile(path);
                if (!fileContext.isBlank()) contexts.add(fileContext);
            }
        } else {
            String entityContext = buildContextForEntities(validatedEntities);
            if (!entityContext.isBlank()) contexts.add(entityContext);
            certainPaths.addAll(imagePathsForEntities(validatedEntities));
            for (String path : validatedScope) {
                String fileContext = buildFullContextForFile(path);
                if (!fileContext.isBlank()) contexts.add(fileContext);
                if (hasCertainEvidenceForFile(path, validatedEntities)) certainPaths.add(path);
            }
        }
        return new GraphEvidenceResult(String.join("\n", contexts), List.copyOf(certainPaths));
    }

    @Transactional(readOnly = true)
    public BigDecimal entityConfidenceForFile(List<String> entityNames, String filePath) {
        if (entityNames == null || entityNames.isEmpty() || filePath == null) return BigDecimal.ZERO;
        return mentionRepository.findByFilePath(filePath).stream()
                .filter(this::isCertainMention)
                .filter(mention -> mention.getEntity() != null)
                .filter(mention -> entityNames.stream().anyMatch(name ->
                        mention.getEntity().getDisplayName().equalsIgnoreCase(name)))
                .map(mentionEvidencePolicy::evidenceConfidence)
                .filter(value -> value != null)
                .max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
    }

    /**
     * True when the file has confirmed graph evidence suitable as a certain source.
     */
    @Transactional(readOnly = true)
    public boolean hasCertainEvidenceForFile(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return false;
        }
        boolean hasMention = mentionRepository.findByFilePath(filePath).stream()
                .anyMatch(this::isCertainMention);
        if (hasMention) {
            return true;
        }
        return !getCertainFactsForFile(filePath).isEmpty();
    }

    @Transactional(readOnly = true)
    public boolean hasCertainEvidenceForFile(String filePath, List<String> entityNames) {
        if (entityNames != null && !entityNames.isEmpty()) {
            return entityConfidenceForFile(entityNames, filePath).compareTo(BigDecimal.ZERO) > 0;
        }
        return hasCertainEvidenceForFile(filePath);
    }

    @Transactional(readOnly = true)
    public String buildContextForEntities(List<String> entityNames) {
        StringBuilder context = new StringBuilder();
        for (String name : validateEntityNames(entityNames)) {
            findOwnedEntityByName(name)
                    .ifPresent(entity -> appendEntityContext(context, entity));
        }
        return context.toString();
    }

    @Transactional(readOnly = true)
    public String buildFullContextForFile(String filePath) {
        if (filePath == null || filePath.isBlank()) return "";
        StringBuilder context = new StringBuilder();
        // Certain identity-linked mentions and facts only (principle 2).
        List<EntityMention> mentions = mentionRepository.findByFilePath(filePath).stream()
                .filter(this::isCertainMention).toList();
        for (EntityMention mention : mentions) appendMention(context, mention);
        for (Fact fact : getCertainFactsForFile(filePath)) appendFact(context, fact);
        // Observed structured vision (not identity claims) supports visual matching.
        fileRepository.findByPath(filePath).ifPresent(file -> appendFileContext(context, file));
        return context.toString();
    }

    @Transactional(readOnly = true)
    public List<Fact> getFactsForFile(String filePath) {
        return getCertainFactsForFile(filePath);
    }

    @Transactional(readOnly = true)
    public List<Fact> getCertainFactsForFile(String filePath) {
        if (filePath == null || filePath.isBlank()) return List.of();
        return factRepository.findByFilePath(filePath).stream()
                .filter(this::isCertainFact)
                .sorted(Comparator.comparing(Fact::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private void appendEntityContext(StringBuilder context, KnowledgeEntity entity) {
        for (EntityMention mention : mentionRepository.findByEntityId(entity.getId())) {
            if (isCertainMention(mention)) appendMention(context, mention);
        }
    }

    private void appendMention(StringBuilder context, EntityMention mention) {
        String name = mention.getEntity() == null ? mention.getLabel() : mention.getEntity().getDisplayName();
        context.append("- entity=").append(name).append("; confidence=")
                .append(mention.getConfidence()).append("; file=").append(mention.getFilePath());
        if (mention.getVisualCues() != null) context.append("; visual_cues=").append(mention.getVisualCues());
        if (mention.getContextObjects() != null) context.append("; nearby_objects=").append(mention.getContextObjects());
        if (mention.getNearbyText() != null) context.append("; nearby_text=").append(mention.getNearbyText());
        context.append('\n');
    }

    private void appendFact(StringBuilder context, Fact fact) {
        String name = fact.getMention().getEntity() == null ? fact.getMention().getLabel()
                : fact.getMention().getEntity().getDisplayName();
        String value = fact.getObject();
        if (fact.getTargetMention() != null) {
            value = fact.getTargetMention().getEntity() == null
                    ? fact.getTargetMention().getLabel()
                    : fact.getTargetMention().getEntity().getDisplayName();
        }
        context.append("- entity=").append(name).append("; predicate=").append(fact.getAction())
                .append("; value=").append(value).append("; confidence=")
                .append(fact.getConfidence()).append("; file=").append(fact.getFilePath()).append('\n');
    }

    private void appendFileContext(StringBuilder context, FileEntity file) {
        if (file.getStructuredVisionContext() != null && !file.getStructuredVisionContext().isBlank())
            context.append("- structured_vision=").append(file.getStructuredVisionContext()).append("; file=").append(file.getPath()).append('\n');
        if (file.getImageScene() != null && !file.getImageScene().isBlank())
            context.append("- scene=").append(file.getImageScene()).append("; file=").append(file.getPath()).append('\n');
        if (file.getImageSummary() != null && !file.getImageSummary().isBlank())
            context.append("- summary=").append(file.getImageSummary()).append("; file=").append(file.getPath()).append('\n');
    }

    /** Certain mention: CONFIRMED status and confidence at or above threshold. */
    private boolean isCertainMention(EntityMention mention) {
        return mentionEvidencePolicy.isCertain(mention);
    }

    private boolean isCertainFact(Fact fact) {
        return fact != null
                && fact.getConfidence() != null
                && fact.getConfidence().compareTo(BigDecimal.valueOf(minFactConfidence)) >= 0
                && isCertainMention(fact.getMention());
    }

    private boolean isImagePath(String path) {
        return fileRepository.findByPath(path).map(file -> file.getFileType() != null
                && file.getFileType().toLowerCase(Locale.ROOT).startsWith("image/")).orElse(false);
    }
}

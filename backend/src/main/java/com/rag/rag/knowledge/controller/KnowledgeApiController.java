package com.rag.rag.knowledge.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.rag.folder.entity.FileEntity;
import com.rag.rag.folder.repository.FileRepository;
import com.rag.rag.knowledge.dto.EntityAppearanceDto;
import com.rag.rag.knowledge.dto.EntityMentionViewDto;
import com.rag.rag.knowledge.dto.EntityPhotoDto;
import com.rag.rag.knowledge.dto.EntitySummaryDto;
import com.rag.rag.knowledge.dto.IdentitySuggestionViewDto;
import com.rag.rag.knowledge.dto.IdentityConflictDto;
import com.rag.rag.knowledge.dto.PersonGraphDto;
import com.rag.rag.knowledge.dto.SuggestionMentionViewDto;
import com.rag.rag.knowledge.graph.PersonRelationGraphService;
import com.rag.rag.knowledge.entity.EntityAlias;
import com.rag.rag.knowledge.entity.EntityMention;
import com.rag.rag.knowledge.entity.KnowledgeEntity;
import com.rag.rag.knowledge.entity.LivingEntityTypes;
import com.rag.rag.knowledge.entity.MentionStatus;
import com.rag.rag.knowledge.face.FaceCropService;
import com.rag.rag.knowledge.face.FaceEmbedding;
import com.rag.rag.knowledge.face.FaceIdentityService;
import com.rag.rag.knowledge.face.FaceObservation;
import com.rag.rag.knowledge.repository.FaceObservationRepository;
import com.rag.rag.knowledge.repository.FactRepository;
import com.rag.rag.knowledge.identity.IdentitySuggestion;
import com.rag.rag.knowledge.identity.IdentityResolutionService;
import com.rag.rag.knowledge.identity.SuggestionStatus;
import com.rag.rag.knowledge.repository.EntityAliasRepository;
import com.rag.rag.knowledge.repository.EntityMentionRepository;
import com.rag.rag.knowledge.repository.FaceEmbeddingRepository;
import com.rag.rag.knowledge.repository.IdentitySuggestionRepository;
import com.rag.rag.knowledge.repository.KnowledgeEntityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Comparator;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.Locale;
import java.util.HashMap;
import java.math.BigDecimal;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class KnowledgeApiController {

    private final IdentitySuggestionRepository suggestionRepository;
    private final EntityMentionRepository mentionRepository;
    private final KnowledgeEntityRepository entityRepository;
    private final EntityAliasRepository aliasRepository;
    private final IdentityResolutionService identityResolutionService;
    private final FileRepository fileRepository;
    private final FaceEmbeddingRepository faceEmbeddingRepository;
    private final FaceIdentityService faceIdentityService;
    private final FaceCropService faceCropService;
    private final FaceObservationRepository faceObservationRepository;
    private final FactRepository factRepository;
    private final PersonRelationGraphService personRelationGraphService;
    private final ObjectMapper objectMapper = new ObjectMapper();


    public record FaceBatchRequest(List<String> paths) {}

    @GetMapping("/review/pending")
    @Transactional
    public ResponseEntity<List<IdentitySuggestionViewDto>> getPendingSuggestions() {
        List<IdentitySuggestion> pending = suggestionRepository.findAll().stream()
                .filter(s -> s.getStatus() == SuggestionStatus.PENDING)
                .filter(s -> isPersonMention(s.getMentionA()) && isPersonMention(s.getMentionB()))
                .toList();

        Set<String> ensuredFiles = new HashSet<>();
        for (IdentitySuggestion suggestion : pending) {
            ensureFaceEmbeddingsForFile(suggestion.getMentionA().getFilePath(), ensuredFiles);
            ensureFaceEmbeddingsForFile(suggestion.getMentionB().getFilePath(), ensuredFiles);
        }

        List<IdentitySuggestionViewDto> result = pending.stream()
                .map(this::toSuggestionViewDto)
                .toList();
        return ResponseEntity.ok(result);
    }

    private void ensureFaceEmbeddingsForFile(String filePath, Set<String> ensuredFiles) {
        if (filePath == null || filePath.isBlank() || !ensuredFiles.add(filePath)) {
            return;
        }
        if (!faceEmbeddingRepository.findByFilePath(filePath).isEmpty()
                || faceObservationRepository.existsByFilePath(filePath)) {
            return;
        }

        fileRepository.findByPath(filePath).ifPresent(file -> {
            if (file.getImageData() == null || file.getImageData().length == 0) {
                return;
            }
            List<EntityMention> mentions = personMentionsForFile(filePath);
            faceIdentityService.processImageFaces(file.getImageData(), filePath, file.getFileName(), mentions);
        });
    }

    private IdentitySuggestionViewDto toSuggestionViewDto(IdentitySuggestion suggestion) {
        return new IdentitySuggestionViewDto(
                suggestion.getId(),
                toSuggestionMentionViewDto(suggestion.getMentionA()),
                toSuggestionMentionViewDto(suggestion.getMentionB()),
                suggestion.getSimilarityScore(),
                suggestion.getStatus() != null ? suggestion.getStatus().name() : null
        );
    }

    private SuggestionMentionViewDto toSuggestionMentionViewDto(EntityMention mention) {
        String fileName = fileRepository.findByPath(mention.getFilePath())
                .map(FileEntity::getFileName)
                .orElse(mention.getFilePath());

        String faceCropBase64 = faceCropService.cropFaceBase64ForMention(mention.getId()).orElse(null);

        return new SuggestionMentionViewDto(
                mention.getId(),
                mention.getLabel(),
                mention.getFilePath(),
                fileName,
                faceCropBase64
        );
    }

    @PostMapping("/mentions/{id}/confirm")
    @Transactional
    public ResponseEntity<Void> confirmMention(@PathVariable UUID id, @RequestParam UUID entityId,
                                                @RequestParam(defaultValue = "false") boolean allowDuplicateOnFile) {
        EntityMention mention = mentionRepository.findById(id).orElseThrow();
        KnowledgeEntity entity = entityRepository.findById(entityId).orElseThrow();
        ensureNoDuplicateOnFile(mention, entity, allowDuplicateOnFile);
        relinkMention(mention, entity);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/mentions/{id}/reject")
    @Transactional
    public ResponseEntity<Void> rejectMention(@PathVariable UUID id) {
        EntityMention mention = mentionRepository.findById(id).orElseThrow();
        mention.setStatus(MentionStatus.REJECTED);
        mentionRepository.save(mention);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/suggestions/{id}/merge")
    @Transactional
    public ResponseEntity<Void> mergeSuggestion(@PathVariable UUID id,
                                                 @RequestParam(defaultValue = "false") boolean allowDuplicateOnFile) {
        IdentitySuggestion suggestion = suggestionRepository.findById(id).orElseThrow();
        suggestion.setStatus(SuggestionStatus.MERGED);
        suggestionRepository.save(suggestion);

        EntityMention mA = suggestion.getMentionA();
        EntityMention mB = suggestion.getMentionB();

        // If neither has entity, create one
        KnowledgeEntity entity = mA.getEntity() != null ? mA.getEntity() : mB.getEntity();
        if (entity == null) {
            entity = identityResolutionService.findOrCreateEntityByName(mA.getLabel(), "PERSON");
        }

        ensureNoDuplicateOnFile(mA, entity, allowDuplicateOnFile);
        relinkMention(mA, entity);
        ensureNoDuplicateOnFile(mB, entity, allowDuplicateOnFile);
        relinkMention(mB, entity);

        return ResponseEntity.ok().build();
    }

    @PostMapping("/suggestions/{id}/split")
    @Transactional
    public ResponseEntity<Void> splitSuggestion(@PathVariable UUID id) {
        IdentitySuggestion suggestion = suggestionRepository.findById(id).orElseThrow();
        suggestion.setStatus(SuggestionStatus.REJECTED);
        suggestionRepository.save(suggestion);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/entities")
    public ResponseEntity<KnowledgeEntity> createEntity(@RequestBody KnowledgeEntity entity) {
        String normalizedType = LivingEntityTypes.normalize(entity.getType());
        if (!"PERSON".equals(normalizedType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only PERSON entities are supported for new records");
        }
        entity.setType(normalizedType);
        return ResponseEntity.ok(entityRepository.save(entity));
    }

    @PostMapping("/entities/{id}/aliases")
    public ResponseEntity<EntityAlias> addAlias(@PathVariable UUID id, @RequestBody EntityAlias alias) {
        KnowledgeEntity entity = entityRepository.findById(id).orElseThrow();
        alias.setEntity(entity);
        return ResponseEntity.ok(aliasRepository.save(alias));
    }

    @GetMapping("/entities")
    @Transactional
    public ResponseEntity<List<EntitySummaryDto>> getAllEntities() {
        Map<String, EntitySummaryDto> entitiesByKey = new LinkedHashMap<>();
        for (KnowledgeEntity entity : entityRepository.findAll()) {
            if (!"PERSON".equalsIgnoreCase(entity.getType())
                    || identityResolutionService.isGenericPersonLabel(entity.getDisplayName())) {
                continue;
            }
            String normalizedType = LivingEntityTypes.normalize(entity.getType());
            String key = entityKey(entity.getDisplayName(), normalizedType);
            if (key == null) {
                continue;
            }
            entitiesByKey.putIfAbsent(key, toEntitySummary(entity));
        }

        List<EntitySummaryDto> entities = entitiesByKey.values().stream()
                .sorted(Comparator.comparing(EntitySummaryDto::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        return ResponseEntity.ok(entities);
    }

    @GetMapping("/entities/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<EntitySummaryDto> getEntity(@PathVariable UUID id) {
        KnowledgeEntity entity = requireNamedPersonEntity(id);
        return ResponseEntity.ok(new EntitySummaryDto(
                entity.getId(),
                entity.getDisplayName(),
                entity.getType(),
                List.of()
        ));
    }

    @GetMapping("/entities/{id}/appearances")
    @Transactional(readOnly = true)
    public ResponseEntity<List<EntityAppearanceDto>> getEntityAppearances(@PathVariable UUID id) {
        requireNamedPersonEntity(id);

        List<EntityAppearanceDto> appearances = new ArrayList<>();
        Set<String> seenPaths = new LinkedHashSet<>();
        for (EntityMention mention : mentionRepository.findByEntityId(id)) {
            if (mention.getStatus() == MentionStatus.REJECTED) {
                continue;
            }
            // Prefer confirmed identity links; still include other non-rejected links
            // so the album matches photos shown on the entity list.
            String path = mention.getFilePath();
            if (path == null || path.isBlank() || !seenPaths.add(path)) {
                continue;
            }
            Optional<FileEntity> fileOpt = fileRepository.findByPath(path);
            if (fileOpt.isEmpty()) {
                continue;
            }
            FileEntity file = fileOpt.get();
            if (file.getFileType() == null || !file.getFileType().toLowerCase(Locale.ROOT).startsWith("image/")) {
                continue;
            }

            ResolvedBbox resolvedBbox = resolveMentionBbox(mention, file);

            appearances.add(new EntityAppearanceDto(
                    mention.getId(),
                    path,
                    file.getFileName() != null ? file.getFileName() : path,
                    mention.getStatus() != null ? mention.getStatus().name() : null,
                    mention.getConfidence(),
                    resolvedBbox.bbox(),
                    resolvedBbox.source()
            ));
        }

        appearances.sort(Comparator.comparing(
                EntityAppearanceDto::fileName,
                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)
        ));
        return ResponseEntity.ok(appearances);
    }

    @GetMapping("/graph/person-relations")
    @Transactional(readOnly = true)
    public ResponseEntity<PersonGraphDto> getPersonRelationGraph() {
        return ResponseEntity.ok(personRelationGraphService.buildPersonRelationGraph());
    }

    private KnowledgeEntity requireNamedPersonEntity(UUID id) {
        KnowledgeEntity entity = entityRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity not found"));
        if (!"PERSON".equalsIgnoreCase(entity.getType())
                || identityResolutionService.isGenericPersonLabel(entity.getDisplayName())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity not found");
        }
        return entity;
    }

    private EntitySummaryDto toEntitySummary(KnowledgeEntity entity) {
        return new EntitySummaryDto(
                entity.getId(),
                entity.getDisplayName(),
                entity.getType(),
                loadPhotosForEntity(entity.getId())
        );
    }

    private String entityKey(String name, String type) {
        if (name == null || name.isBlank() || type == null) {
            return null;
        }
        return type + "\u0000" + name.trim().toLowerCase(Locale.ROOT);
    }

    private List<EntityPhotoDto> loadPhotosForEntity(UUID entityId) {
        return loadPhotosForMentions(mentionRepository.findByEntityId(entityId));
    }

    private List<EntityPhotoDto> loadPhotosForMentions(List<EntityMention> mentions) {
        Set<String> filePaths = new LinkedHashSet<>();
        for (EntityMention mention : mentions) {
            if (mention.getFilePath() != null && !mention.getFilePath().isBlank()) {
                filePaths.add(mention.getFilePath());
            }
        }

        List<EntityPhotoDto> photos = new ArrayList<>();
        for (String path : filePaths) {
            toEntityPhoto(path).ifPresent(photos::add);
        }
        return photos;
    }

    private Optional<EntityPhotoDto> toEntityPhoto(String path) {
        Optional<FileEntity> fileOpt = fileRepository.findByPath(path);
        if (fileOpt.isEmpty()) {
            return Optional.empty();
        }

        FileEntity file = fileOpt.get();
        if (file.getImageData() == null || file.getImageData().length == 0) {
            return Optional.empty();
        }

        String mimeType = file.getFileType() != null ? file.getFileType() : "image/jpeg";
        if (!mimeType.toLowerCase().contains("image")) {
            return Optional.empty();
        }

        return Optional.of(new EntityPhotoDto(
                file.getPath(),
                file.getFileName(),
                Base64.getEncoder().encodeToString(file.getImageData()),
                mimeType
        ));
    }

    @PutMapping("/entities/{id}/rename")
    @Transactional
    public ResponseEntity<Void> renameEntity(@PathVariable UUID id, @RequestParam String newName) {
        KnowledgeEntity entity = entityRepository.findById(id).orElseThrow();
        entity.setDisplayName(newName);
        entityRepository.save(entity);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/entities/{id}")
    @Transactional
    public ResponseEntity<Void> deleteEntityRecognition(@PathVariable UUID id) {
        KnowledgeEntity entity = requireNamedPersonEntity(id);
        List<UUID> mentionIds = mentionRepository.findByEntityId(id).stream()
                .map(EntityMention::getId)
                .filter(java.util.Objects::nonNull)
                .toList();

        // These records describe recognition only. FileEntity and its document
        // embeddings deliberately remain untouched, so original uploads survive.
        if (!mentionIds.isEmpty()) {
            suggestionRepository.deleteByMentionIds(mentionIds);
            factRepository.deleteByMentionIds(mentionIds);
            faceObservationRepository.deleteByMentionIds(mentionIds);
            faceEmbeddingRepository.deleteByMentionIdIn(mentionIds);
        }
        faceEmbeddingRepository.deleteByEntityId(id);
        mentionRepository.deleteByEntityId(id);
        aliasRepository.deleteByEntityId(id);
        entityRepository.delete(entity);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/mentions/{id}/rename")
    @Transactional
    public ResponseEntity<Void> renameMention(@PathVariable UUID id, @RequestParam String newName,
                                               @RequestParam(defaultValue = "false") boolean allowDuplicateOnFile) {
        EntityMention mention = mentionRepository.findById(id).orElseThrow();
        KnowledgeEntity entity = identityResolutionService.findOrCreateEntityByName(newName, "PERSON");
        ensureNoDuplicateOnFile(mention, entity, allowDuplicateOnFile);
        mention.setLabel(newName.trim());
        relinkMention(mention, entity);
        return ResponseEntity.ok().build();
    }

    private void relinkMention(EntityMention mention, KnowledgeEntity entity) {
        identityResolutionService.confirmUserAssignment(mention, entity);
        if (mention.getId() != null) {
            faceEmbeddingRepository.relinkByMentionId(mention.getId(), entity);
            faceIdentityService.promoteObservation(mention, entity);
        }
    }

    private void ensureNoDuplicateOnFile(EntityMention mention, KnowledgeEntity entity, boolean allowDuplicateOnFile) {
        if (allowDuplicateOnFile || mention == null || entity == null || mention.getFilePath() == null) {
            return;
        }
        mentionRepository.findByFilePath(mention.getFilePath()).stream()
                .filter(other -> other.getId() != null && !other.getId().equals(mention.getId()))
                .filter(other -> other.getStatus() != MentionStatus.REJECTED)
                .filter(other -> other.getEntity() != null && entity.getId().equals(other.getEntity().getId()))
                .findFirst()
                .ifPresent(other -> {
                    throw new IdentityAssignmentConflictException(other.getId());
                });
    }

    @ExceptionHandler(IdentityAssignmentConflictException.class)
    public ResponseEntity<IdentityConflictDto> handleIdentityConflict(IdentityAssignmentConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new IdentityConflictDto(
                "ENTITY_ALREADY_ON_FILE",
                "Ta osoba jest już przypisana do innej twarzy na tym obrazie.",
                exception.existingMentionId
        ));
    }

    private static final class IdentityAssignmentConflictException extends RuntimeException {
        private final UUID existingMentionId;

        private IdentityAssignmentConflictException(UUID existingMentionId) {
            this.existingMentionId = existingMentionId;
        }
    }

    @PostMapping("/entities/consolidate-duplicates")
    @Transactional
    public ResponseEntity<Map<String, Integer>> consolidateDuplicateEntities() {
        int merged = identityResolutionService.consolidateDuplicateEntities();
        return ResponseEntity.ok(Map.of("merged", merged));
    }

    @GetMapping("/mentions/by-file")
    @Transactional(readOnly = true)
    public ResponseEntity<List<EntityMentionViewDto>> getMentionsForFile(@RequestParam String path) {
        List<EntityMentionViewDto> mentions = mentionRepository.findByFilePath(path).stream()
                .filter(this::isReviewableMention)
                .map(this::toMentionViewDto)
                .sorted(this::compareMentionsForDisplay)
                .toList();
        return ResponseEntity.ok(mentions);
    }

    @PostMapping("/mentions/by-file/detect-faces")
    @Transactional
    public ResponseEntity<List<EntityMentionViewDto>> detectFacesForFile(@RequestParam String path) {
        FileEntity file = fileRepository.findByPathForUpdate(path)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found"));

        if (file.getImageData() == null || file.getImageData().length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File has no image data");
        }

        List<EntityMention> mentions = personMentionsForFile(path);
        faceIdentityService.replaceFaceEmbeddingsForFile(file.getImageData(), path, file.getFileName(), mentions);

        List<EntityMentionViewDto> result = mentionRepository.findByFilePath(path).stream()
                .filter(this::isLivingOrUnlinkedMention)
                .map(this::toMentionViewDto)
                .sorted(this::compareMentionsForDisplay)
                .toList();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/faces/resolve-batch")
    @Transactional
    public ResponseEntity<Map<String, Integer>> resolveFaceBatch(@RequestBody FaceBatchRequest request) {
        List<String> paths = request == null || request.paths() == null
                ? List.of()
                : request.paths().stream().filter(path -> path != null && !path.isBlank()).distinct().toList();
        Map<UUID, BatchFace> candidatesByMention = new LinkedHashMap<>();
        for (String path : paths) {
            if (faceEmbeddingRepository.findByFilePath(path).isEmpty()
                    && !faceObservationRepository.existsByFilePath(path)) {
                ensureFaceEmbeddingsForFile(path, new HashSet<>());
            }
            for (FaceEmbedding face : faceEmbeddingRepository.findByFilePath(path)) {
                if (face.getMention() == null || face.getMention().getId() == null) continue;
                if (face.getEntity() == null
                        || identityResolutionService.isGenericPersonLabel(face.getEntity().getDisplayName())) {
                    candidatesByMention.putIfAbsent(face.getMention().getId(), new BatchFace(
                            face.getMention(), face.getFilePath(), face.getEmbedding(), face.getDetScore()));
                }
            }
            for (FaceObservation observation : faceObservationRepository.findByFilePath(path)) {
                if (!"PENDING".equalsIgnoreCase(observation.getStatus())
                        || observation.getMention() == null || observation.getMention().getId() == null) continue;
                candidatesByMention.putIfAbsent(observation.getMention().getId(), new BatchFace(
                        observation.getMention(), observation.getFilePath(), observation.getEmbedding(),
                        observation.getDetScore()));
            }
        }

        List<BatchFace> candidates = candidatesByMention.values().stream()
                .filter(face -> face.embedding() != null && face.embedding().length > 0)
                .toList();
        List<List<BatchFace>> groups = new ArrayList<>();
        Set<UUID> consumed = new HashSet<>();
        for (BatchFace seed : candidates) {
            if (consumed.contains(seed.mention().getId())) continue;
            List<BatchFace> group = new ArrayList<>();
            List<BatchFace> frontier = new ArrayList<>(List.of(seed));
            while (!frontier.isEmpty()) {
                BatchFace current = frontier.remove(frontier.size() - 1);
                if (!consumed.add(current.mention().getId())) continue;
                group.add(current);
                for (BatchFace other : candidates) {
                    if (other == current || consumed.contains(other.mention().getId())) continue;
                    if (FaceIdentityService.cosineSimilarity(
                            FaceIdentityService.normalizeEmbedding(current.embedding()),
                            FaceIdentityService.normalizeEmbedding(other.embedding())) >= faceIdentityService.batchClusterThreshold()) {
                        frontier.add(other);
                    }
                }
            }
            groups.add(group);
        }

        int linked = 0;
        int clusters = 0;
        int unresolved = 0;
        for (List<BatchFace> group : groups) {
            if (group.isEmpty()) continue;
            float[] representative = averageEmbedding(group);
            Optional<FaceIdentityService.EntityMatch> match =
                    faceIdentityService.findBestEntityMatch(representative, null, 0.50);
            if (match.isEmpty()) {
                for (BatchFace face : group) markAsFaceCluster(face.mention());
                unresolved++;
            } else {
                Map<String, BatchFace> bestPerFile = new LinkedHashMap<>();
                for (BatchFace face : group) {
                    bestPerFile.merge(face.filePath(), face, (left, right) ->
                            score(left).compareTo(score(right)) >= 0 ? left : right);
                }
                for (BatchFace face : group) {
                    if (bestPerFile.get(face.filePath()) != face) {
                        markAsFaceCluster(face.mention());
                        continue;
                    }
                    identityResolutionService.confirmFaceMatch(face.mention(), match.get().entity(),
                            face.mention().getLabel(), match.get().score(), match.get().margin());
                    faceIdentityService.promoteObservation(face.mention(), match.get().entity());
                    linked++;
                }
            }
            if (group.size() > 1) clusters++;
        }
        return ResponseEntity.ok(Map.of("linked", linked, "clusters", clusters, "unresolved", unresolved));
    }

    private float[] averageEmbedding(List<BatchFace> faces) {
        int length = faces.get(0).embedding().length;
        float[] average = new float[length];
        for (BatchFace face : faces) {
            float[] normalized = FaceIdentityService.normalizeEmbedding(face.embedding());
            for (int i = 0; i < length && i < normalized.length; i++) average[i] += normalized[i];
        }
        float norm = 0f;
        for (float value : average) norm += value * value;
        norm = (float) Math.sqrt(norm);
        if (norm > 0f) for (int i = 0; i < average.length; i++) average[i] /= norm;
        return average;
    }

    private void markAsFaceCluster(EntityMention mention) {
        mention.setStatus(MentionStatus.PENDING);
        mention.setIdentitySource(com.rag.rag.knowledge.entity.IdentityEvidenceSource.FACE_CLUSTER);
        mention.setIdentityConfidence(BigDecimal.valueOf(faceIdentityService.batchClusterThreshold()));
        mention.setIdentityMargin(null);
        mentionRepository.save(mention);
    }

    private BigDecimal score(BatchFace face) {
        return face.detScore() == null ? BigDecimal.ZERO : face.detScore();
    }

    private record BatchFace(EntityMention mention, String filePath, float[] embedding, BigDecimal detScore) {}

    private List<EntityMention> personMentionsForFile(String path) {
        return mentionRepository.findByFilePath(path).stream()
                .filter(this::isPersonMention)
                .toList();
    }

    private boolean isLivingOrUnlinkedMention(EntityMention mention) {
        return isPersonMention(mention);
    }

    private boolean isReviewableMention(EntityMention mention) {
        if (mention == null || mention.getStatus() == MentionStatus.REJECTED) {
            return false;
        }
        return "PERSON".equalsIgnoreCase(mention.getEntityType())
                || (mention.getEntity() != null && "PERSON".equalsIgnoreCase(mention.getEntity().getType()));
    }

    private boolean isPersonMention(EntityMention mention) {
        if (mention == null) {
            return false;
        }
        if (mention.getStatus() == MentionStatus.REJECTED
                || "Osoba do weryfikacji".equalsIgnoreCase(mention.getLabel())) {
            return false;
        }
        if (mention.getEntity() != null
                && identityResolutionService.isGenericPersonLabel(mention.getEntity().getDisplayName())) {
            return false;
        }
        return "PERSON".equalsIgnoreCase(mention.getEntityType())
                || (mention.getEntity() != null && "PERSON".equalsIgnoreCase(mention.getEntity().getType()));
    }

    private EntityMentionViewDto toMentionViewDto(EntityMention mention) {
        Optional<FileEntity> file = fileRepository.findByPath(mention.getFilePath());
        ResolvedBbox resolvedBbox = resolveMentionBbox(mention, file.orElse(null));

        KnowledgeEntity entity = mention.getEntity();
        return new EntityMentionViewDto(
                mention.getId(),
                mention.getFilePath(),
                mention.getLabel(),
                mention.getEntityType() == null ? "PERSON" : mention.getEntityType(),
                mention.getConfidence(),
                mention.getIdentityConfidence(),
                mention.getIdentityMargin(),
                mention.getIdentitySource() != null ? mention.getIdentitySource().name() : null,
                mention.getStatus() != null ? mention.getStatus().name() : null,
                mention.getVisualCues(),
                entity != null ? entity.getId() : null,
                entity != null ? entity.getDisplayName() : null,
                resolvedBbox.bbox(),
                resolvedBbox.source()
        );
    }

    private ResolvedBbox resolveMentionBbox(EntityMention mention, FileEntity file) {
        Optional<List<Float>> embeddingBbox = faceEmbeddingRepository.findFirstByMention_Id(mention.getId())
                .map(FaceEmbedding::getBbox)
                .map(this::bboxToList)
                .filter(java.util.Objects::nonNull);
        if (embeddingBbox.isPresent()) {
            return new ResolvedBbox(embeddingBbox.get(), "FACE");
        }

        Optional<List<Float>> observationBbox = faceObservationRepository
                .findFirstByMentionIdAndStatus(mention.getId(), "PENDING")
                .map(FaceObservation::getBbox)
                .map(this::bboxToList)
                .filter(java.util.Objects::nonNull);
        if (observationBbox.isPresent()) {
            return new ResolvedBbox(observationBbox.get(), "FACE");
        }

        List<Float> visionBbox = parseMentionBbox(mention.getBbox(), file);
        return new ResolvedBbox(visionBbox, visionBbox == null ? null : "VISION");
    }

    private List<Float> parseMentionBbox(String rawBbox, FileEntity file) {
        if (rawBbox == null || rawBbox.isBlank()) {
            return null;
        }
        try {
            float[] values = objectMapper.readValue(rawBbox, float[].class);
            if (values.length < 4) {
                return null;
            }

            int imageWidth = 0;
            int imageHeight = 0;
            if (file != null && file.getImageData() != null) {
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(file.getImageData()));
                if (image != null) {
                    imageWidth = image.getWidth();
                    imageHeight = image.getHeight();
                }
            }

            // Vision returns normalized coordinates for some models, while face-service
            // returns pixels. Convert both formats to the pixel format used by the UI.
            if (imageWidth > 0 && imageHeight > 0
                    && values[0] >= 0f && values[1] >= 0f
                    && values[2] <= 1f && values[3] <= 1f) {
                values[0] *= imageWidth;
                values[2] *= imageWidth;
                values[1] *= imageHeight;
                values[3] *= imageHeight;
            }
            return bboxToList(clampBbox(values, imageWidth, imageHeight));
        } catch (Exception e) {
            return null;
        }
    }

    private List<Float> bboxToList(float[] bbox) {
        if (bbox == null || bbox.length < 4) {
            return null;
        }
        List<Float> values = new ArrayList<>(4);
        for (float value : bbox) {
            values.add(value);
        }
        return values;
    }

    private float[] clampBbox(float[] bbox, int imageWidth, int imageHeight) {
        if (bbox == null || bbox.length < 4 || imageWidth <= 0 || imageHeight <= 0) {
            return bbox;
        }
        float x1 = Math.max(0f, Math.min(bbox[0], imageWidth));
        float y1 = Math.max(0f, Math.min(bbox[1], imageHeight));
        float x2 = Math.max(x1, Math.min(bbox[2], imageWidth));
        float y2 = Math.max(y1, Math.min(bbox[3], imageHeight));
        if (x2 - x1 < 1f || y2 - y1 < 1f) {
            return null;
        }
        return new float[]{x1, y1, x2, y2};
    }

    private int compareMentionsForDisplay(EntityMentionViewDto a, EntityMentionViewDto b) {
        double centerA = bboxCenterX(a.bbox());
        double centerB = bboxCenterX(b.bbox());
        if (centerA >= 0 && centerB >= 0) {
            return Double.compare(centerA, centerB);
        }
        if (centerA >= 0) {
            return -1;
        }
        if (centerB >= 0) {
            return 1;
        }
        return a.label().compareToIgnoreCase(b.label());
    }

    private double bboxCenterX(List<Float> bbox) {
        if (bbox == null || bbox.size() < 4) {
            return -1;
        }
        return (bbox.get(0) + bbox.get(2)) / 2.0;
    }

    private record ResolvedBbox(List<Float> bbox, String source) {}
}

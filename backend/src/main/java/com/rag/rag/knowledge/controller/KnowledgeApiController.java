package com.rag.rag.knowledge.controller;

import com.rag.rag.folder.entity.FileEntity;
import com.rag.rag.folder.repository.FileRepository;
import com.rag.rag.knowledge.dto.EntityMentionViewDto;
import com.rag.rag.knowledge.dto.EntityPhotoDto;
import com.rag.rag.knowledge.dto.EntitySummaryDto;
import com.rag.rag.knowledge.entity.EntityAlias;
import com.rag.rag.knowledge.entity.EntityMention;
import com.rag.rag.knowledge.entity.KnowledgeEntity;
import com.rag.rag.knowledge.entity.MentionStatus;
import com.rag.rag.knowledge.face.FaceEmbedding;
import com.rag.rag.knowledge.identity.IdentitySuggestion;
import com.rag.rag.knowledge.identity.IdentityResolutionService;
import com.rag.rag.knowledge.identity.SuggestionStatus;
import com.rag.rag.knowledge.repository.EntityAliasRepository;
import com.rag.rag.knowledge.repository.EntityMentionRepository;
import com.rag.rag.knowledge.repository.FaceEmbeddingRepository;
import com.rag.rag.knowledge.repository.IdentitySuggestionRepository;
import com.rag.rag.knowledge.repository.KnowledgeEntityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

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

    private static final int MAX_PHOTOS_PER_ENTITY = 4;

    @GetMapping("/review/pending")
    public ResponseEntity<List<IdentitySuggestion>> getPendingSuggestions() {
        // Just for MVP, returning all. In real app, filter by PENDING.
        List<IdentitySuggestion> pending = suggestionRepository.findAll().stream()
                .filter(s -> s.getStatus() == SuggestionStatus.PENDING)
                .toList();
        return ResponseEntity.ok(pending);
    }

    @PostMapping("/mentions/{id}/confirm")
    @Transactional
    public ResponseEntity<Void> confirmMention(@PathVariable UUID id, @RequestParam UUID entityId) {
        EntityMention mention = mentionRepository.findById(id).orElseThrow();
        KnowledgeEntity entity = entityRepository.findById(entityId).orElseThrow();
        mention.setEntity(entity);
        mention.setStatus(MentionStatus.CONFIRMED);
        mentionRepository.save(mention);
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
    public ResponseEntity<Void> mergeSuggestion(@PathVariable UUID id) {
        IdentitySuggestion suggestion = suggestionRepository.findById(id).orElseThrow();
        suggestion.setStatus(SuggestionStatus.MERGED);
        suggestionRepository.save(suggestion);

        EntityMention mA = suggestion.getMentionA();
        EntityMention mB = suggestion.getMentionB();

        // If neither has entity, create one
        KnowledgeEntity entity = mA.getEntity() != null ? mA.getEntity() : mB.getEntity();
        if (entity == null) {
            entity = identityResolutionService.findOrCreateEntityByName(mA.getLabel());
        }

        mA.setEntity(entity);
        mA.setStatus(MentionStatus.CONFIRMED);
        mB.setEntity(entity);
        mB.setStatus(MentionStatus.CONFIRMED);

        mentionRepository.save(mA);
        mentionRepository.save(mB);

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
        return ResponseEntity.ok(entityRepository.save(entity));
    }

    @PostMapping("/entities/{id}/aliases")
    public ResponseEntity<EntityAlias> addAlias(@PathVariable UUID id, @RequestBody EntityAlias alias) {
        KnowledgeEntity entity = entityRepository.findById(id).orElseThrow();
        alias.setEntity(entity);
        return ResponseEntity.ok(aliasRepository.save(alias));
    }

    @GetMapping("/entities")
    public ResponseEntity<List<EntitySummaryDto>> getAllEntities() {
        identityResolutionService.consolidateDuplicateEntities();
        List<EntitySummaryDto> entities = entityRepository.findAll().stream()
                .map(entity -> new EntitySummaryDto(
                        entity.getId(),
                        entity.getDisplayName(),
                        entity.getType(),
                        loadPhotosForEntity(entity.getId())
                ))
                .toList();
        return ResponseEntity.ok(entities);
    }

    private List<EntityPhotoDto> loadPhotosForEntity(UUID entityId) {
        Set<String> filePaths = new LinkedHashSet<>();
        for (EntityMention mention : mentionRepository.findByEntityId(entityId)) {
            if (mention.getFilePath() != null && !mention.getFilePath().isBlank()) {
                filePaths.add(mention.getFilePath());
            }
        }

        List<EntityPhotoDto> photos = new ArrayList<>();
        for (String path : filePaths) {
            if (photos.size() >= MAX_PHOTOS_PER_ENTITY) {
                break;
            }
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

    @PutMapping("/mentions/{id}/rename")
    @Transactional
    public ResponseEntity<Void> renameMention(@PathVariable UUID id, @RequestParam String newName) {
        EntityMention mention = mentionRepository.findById(id).orElseThrow();
        KnowledgeEntity entity = identityResolutionService.findOrCreateEntityByName(newName);
        mention.setEntity(entity);
        mention.setStatus(MentionStatus.CONFIRMED);
        mentionRepository.save(mention);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/entities/consolidate-duplicates")
    @Transactional
    public ResponseEntity<Map<String, Integer>> consolidateDuplicateEntities() {
        int merged = identityResolutionService.consolidateDuplicateEntities();
        return ResponseEntity.ok(Map.of("merged", merged));
    }

    @GetMapping("/mentions/by-file")
    public ResponseEntity<List<EntityMentionViewDto>> getMentionsForFile(@RequestParam String path) {
        List<EntityMentionViewDto> mentions = mentionRepository.findByFilePath(path).stream()
                .map(this::toMentionViewDto)
                .sorted(this::compareMentionsForDisplay)
                .toList();
        return ResponseEntity.ok(mentions);
    }

    private EntityMentionViewDto toMentionViewDto(EntityMention mention) {
        List<Float> bbox = faceEmbeddingRepository.findFirstByMentionId(mention.getId())
                .map(FaceEmbedding::getBbox)
                .map(this::bboxToList)
                .orElse(null);

        KnowledgeEntity entity = mention.getEntity();
        return new EntityMentionViewDto(
                mention.getId(),
                mention.getFilePath(),
                mention.getLabel(),
                mention.getConfidence(),
                mention.getStatus() != null ? mention.getStatus().name() : null,
                mention.getVisualCues(),
                entity != null ? entity.getId() : null,
                entity != null ? entity.getDisplayName() : null,
                bbox
        );
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
}

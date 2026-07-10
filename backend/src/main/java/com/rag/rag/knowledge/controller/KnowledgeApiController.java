package com.rag.rag.knowledge.controller;

import com.rag.rag.knowledge.entity.EntityAlias;
import com.rag.rag.knowledge.entity.EntityMention;
import com.rag.rag.knowledge.entity.KnowledgeEntity;
import com.rag.rag.knowledge.entity.MentionStatus;
import com.rag.rag.knowledge.identity.IdentitySuggestion;
import com.rag.rag.knowledge.identity.IdentityResolutionService;
import com.rag.rag.knowledge.identity.SuggestionStatus;
import com.rag.rag.knowledge.repository.EntityAliasRepository;
import com.rag.rag.knowledge.repository.EntityMentionRepository;
import com.rag.rag.knowledge.repository.IdentitySuggestionRepository;
import com.rag.rag.knowledge.repository.KnowledgeEntityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
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
    public ResponseEntity<List<KnowledgeEntity>> getAllEntities() {
        identityResolutionService.consolidateDuplicateEntities();
        return ResponseEntity.ok(entityRepository.findAll());
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
    public ResponseEntity<List<EntityMention>> getMentionsForFile(@RequestParam String path) {
        List<EntityMention> mentions = mentionRepository.findAll().stream()
                .filter(m -> path.equals(m.getFilePath()))
                .toList();
        return ResponseEntity.ok(mentions);
    }
}

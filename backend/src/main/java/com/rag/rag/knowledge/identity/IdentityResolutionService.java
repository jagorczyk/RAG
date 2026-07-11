package com.rag.rag.knowledge.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.rag.knowledge.entity.*;
import com.rag.rag.knowledge.repository.EntityAliasRepository;
import com.rag.rag.knowledge.repository.EntityMentionRepository;
import com.rag.rag.knowledge.repository.IdentitySuggestionRepository;
import com.rag.rag.knowledge.repository.KnowledgeEntityRepository;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class IdentityResolutionService {

    private final KnowledgeEntityRepository entityRepository;
    private final EntityAliasRepository aliasRepository;
    private final EntityMentionRepository mentionRepository;
    private final IdentitySuggestionRepository suggestionRepository;
    private final ChatLanguageModel chatModel;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public IdentityResolutionService(
            KnowledgeEntityRepository entityRepository,
            EntityAliasRepository aliasRepository,
            EntityMentionRepository mentionRepository,
            IdentitySuggestionRepository suggestionRepository,
            @Qualifier("chatLanguageModel") ChatLanguageModel chatModel
    ) {
        this.entityRepository = entityRepository;
        this.aliasRepository = aliasRepository;
        this.mentionRepository = mentionRepository;
        this.suggestionRepository = suggestionRepository;
        this.chatModel = chatModel;
    }

    @Transactional
    public void resolve(EntityMention mention, String fileEntityTag) {
        if (fileEntityTag != null && !fileEntityTag.isBlank()) {
            linkMention(mention, findOrCreateEntityByName(fileEntityTag), MentionStatus.CONFIRMED);
            return;
        }

        String label = normalizeLabel(mention.getLabel());
        if (label == null) {
            mention.setStatus(MentionStatus.SUGGESTED);
            mentionRepository.save(mention);
            return;
        }

        for (EntityMention candidate : mentionRepository.findAll()) {
            if (candidate.getId().equals(mention.getId())) {
                continue;
            }

            double score = computeSimilarity(mention, candidate);
            if (score >= 0.85 && candidate.getEntity() != null) {
                linkMention(mention, candidate.getEntity(), MentionStatus.CONFIRMED);
                return;
            }
            if (score >= 0.60) {
                saveSuggestion(mention, candidate, score);
            }
        }

        Optional<KnowledgeEntity> existing = looksLikePersonName(label)
                ? findEntityByNameOrAlias(label)
                : Optional.empty();
        if (existing.isPresent()) {
            linkMention(mention, existing.get(), MentionStatus.CONFIRMED);
            return;
        }

        KnowledgeEntity entity = looksLikePersonName(label)
                ? findOrCreateEntityByName(label)
                : createSuggestedEntity(label);
        linkMention(mention, entity, looksLikePersonName(label) ? MentionStatus.CONFIRMED : MentionStatus.SUGGESTED);
    }

    @Transactional
    public KnowledgeEntity findOrCreateEntityByName(String name) {
        String normalized = normalizeLabel(name);
        if (normalized == null) {
            throw new IllegalArgumentException("Entity name cannot be blank");
        }

        Optional<KnowledgeEntity> existing = findEntityByNameOrAlias(normalized);
        if (existing.isPresent()) {
            return existing.get();
        }

        KnowledgeEntity newEntity = KnowledgeEntity.builder()
                .displayName(normalized)
                .type("PERSON")
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
    public int consolidateDuplicateEntities() {
        Map<String, List<KnowledgeEntity>> grouped = new LinkedHashMap<>();
        for (KnowledgeEntity entity : entityRepository.findAll()) {
            String key = normalizeLabel(entity.getDisplayName());
            if (key == null) {
                continue;
            }
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
        for (EntityMention mention : mentionRepository.findByEntityId(duplicate.getId())) {
            mention.setEntity(target);
            mention.setStatus(MentionStatus.CONFIRMED);
            mentionRepository.save(mention);
        }

        for (EntityAlias alias : aliasRepository.findAll()) {
            if (alias.getEntity() == null || !duplicate.getId().equals(alias.getEntity().getId())) {
                continue;
            }
            boolean aliasExists = aliasRepository.findFirstByAliasIgnoreCase(alias.getAlias())
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

    private Optional<KnowledgeEntity> findEntityByNameOrAlias(String name) {
        Optional<KnowledgeEntity> byName = entityRepository.findFirstByDisplayNameIgnoreCase(name);
        if (byName.isPresent()) {
            return byName;
        }

        return aliasRepository.findFirstByAliasIgnoreCase(name).map(EntityAlias::getEntity);
    }

    private void linkMention(EntityMention mention, KnowledgeEntity entity, MentionStatus status) {
        mention.setEntity(entity);
        mention.setStatus(status);
        mentionRepository.save(mention);
    }

    private void saveSuggestion(EntityMention mention, EntityMention candidate, double score) {
        IdentitySuggestion suggestion = IdentitySuggestion.builder()
                .mentionA(mention)
                .mentionB(candidate)
                .similarityScore(BigDecimal.valueOf(score))
                .status(SuggestionStatus.PENDING)
                .build();
        suggestionRepository.save(suggestion);
    }

    @Transactional
    public void confirmFaceMatch(EntityMention mention, KnowledgeEntity matchedEntity, String visionLabel) {
        if (mention == null || matchedEntity == null) {
            return;
        }
        linkMention(mention, matchedEntity, MentionStatus.CONFIRMED);
        addAliasIfMissing(matchedEntity, visionLabel);
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

        boolean alreadySuggested = suggestionRepository.findAll().stream()
                .anyMatch(suggestion -> suggestion.getStatus() == SuggestionStatus.PENDING
                        && ((suggestion.getMentionA().getId().equals(mention.getId())
                                && suggestion.getMentionB().getId().equals(candidateMention.get().getId()))
                            || (suggestion.getMentionB().getId().equals(mention.getId())
                                && suggestion.getMentionA().getId().equals(candidateMention.get().getId()))));
        if (alreadySuggested) {
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
        boolean exists = aliasRepository.findFirstByAliasIgnoreCase(normalized)
                .filter(alias -> alias.getEntity().getId().equals(entity.getId()))
                .isPresent();
        if (exists) {
            return;
        }
        aliasRepository.save(EntityAlias.builder()
                .entity(entity)
                .alias(normalized)
                .source(AliasSource.FACE)
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

    private KnowledgeEntity createSuggestedEntity(String label) {
        KnowledgeEntity entity = KnowledgeEntity.builder()
                .displayName(label)
                .type("PERSON")
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

    private double computeSimilarity(EntityMention a, EntityMention b) {
        String labelA = normalizeLabel(a.getLabel());
        String labelB = normalizeLabel(b.getLabel());
        if (labelA != null && labelA.equalsIgnoreCase(labelB)) {
            return 0.0;
        }

        if (isGenericLabel(labelA) || isGenericLabel(labelB)) {
            return 0.0;
        }

        String prompt = String.format(
                "Are these two descriptions about the same person? " +
                        "Person A: %s, cues: %s. Person B: %s, cues: %s. " +
                        "Return STRICTLY JSON: {\"same\": boolean, \"confidence\": float (0.0 to 1.0)}",
                a.getLabel(), a.getVisualCues(), b.getLabel(), b.getVisualCues()
        );

        try {
            String response = chatModel.generate(prompt);
            String jsonContent = extractJson(response);
            JsonNode node = objectMapper.readTree(jsonContent);
            if (node.has("same") && node.get("same").asBoolean()) {
                return node.has("confidence") ? node.get("confidence").asDouble() : 0.80;
            }
        } catch (Exception e) {
            log.warn("LLM matching failed", e);
        }
        return 0.0;
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
        String lower = label.toLowerCase(Locale.ROOT);
        return lower.startsWith("nieznana")
                || lower.startsWith("nieznany")
                || lower.matches("osoba\\s+\\d+")
                || lower.equals("osoba")
                || lower.equals("postać")
                || lower.equals("postac");
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
}

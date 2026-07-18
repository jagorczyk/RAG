package com.rag.rag.knowledge.graph;

import com.rag.rag.knowledge.dto.PersonGraphDto;
import com.rag.rag.knowledge.dto.PersonGraphDto.PersonGraphEdgeDto;
import com.rag.rag.knowledge.dto.PersonGraphDto.PersonGraphNodeDto;
import com.rag.rag.knowledge.entity.EntityMention;
import com.rag.rag.knowledge.entity.KnowledgeEntity;
import com.rag.rag.knowledge.entity.MentionStatus;
import com.rag.rag.knowledge.fact.Fact;
import com.rag.rag.knowledge.identity.IdentityResolutionService;
import com.rag.rag.knowledge.repository.EntityMentionRepository;
import com.rag.rag.knowledge.repository.FactRepository;
import com.rag.rag.knowledge.repository.KnowledgeEntityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Builds a person–person relation graph for visualization.
 * Only CONFIRMED mentions and high-confidence facts are used (AGENTS.md principle 2).
 */
@Service
@RequiredArgsConstructor
public class PersonRelationGraphService {

    private static final Set<String> TECHNICAL_ACTIONS = Set.of(
            "RELATED_OBJECT",
            "NEAR_TEXT"
    );

    public static final String KIND_SPATIAL = "SPATIAL";
    public static final String KIND_CO_OCCURRENCE = "CO_OCCURRENCE";
    public static final String CO_OCCURRENCE_LABEL = "współwystępuje";

    private final KnowledgeEntityRepository entityRepository;
    private final EntityMentionRepository mentionRepository;
    private final FactRepository factRepository;
    private final IdentityResolutionService identityResolutionService;
    private final MentionEvidencePolicy mentionEvidencePolicy;

    @Value("${rag.graph.min-fact-confidence:0.75}")
    private double minFactConfidence = 0.75;

    @Transactional(readOnly = true)
    public PersonGraphDto buildPersonRelationGraph() {
        List<KnowledgeEntity> personEntities = listNamedPersonEntities();
        if (personEntities.isEmpty()) {
            return new PersonGraphDto(List.of(), List.of());
        }

        Map<UUID, KnowledgeEntity> entitiesById = new LinkedHashMap<>();
        Map<String, KnowledgeEntity> entitiesByName = new HashMap<>();
        for (KnowledgeEntity entity : personEntities) {
            entitiesById.put(entity.getId(), entity);
            entitiesByName.put(normalizeName(entity.getDisplayName()), entity);
        }

        Map<UUID, Integer> photoCounts = new HashMap<>();
        Map<String, List<EntityMention>> certainMentionsByFile = new HashMap<>();

        for (KnowledgeEntity entity : personEntities) {
            List<EntityMention> mentions = mentionRepository.findByEntityId(entity.getId());
            Set<String> paths = new LinkedHashSet<>();
            for (EntityMention mention : mentions) {
                if (!isCertainPersonMention(mention)) {
                    continue;
                }
                if (mention.getFilePath() != null && !mention.getFilePath().isBlank()) {
                    paths.add(mention.getFilePath());
                    certainMentionsByFile
                            .computeIfAbsent(mention.getFilePath(), key -> new ArrayList<>())
                            .add(mention);
                }
            }
            photoCounts.put(entity.getId(), paths.size());
        }

        Map<String, Integer> edgeWeights = new LinkedHashMap<>();
        Map<String, PersonGraphEdgeDto> edgeMeta = new LinkedHashMap<>();

        addCoOccurrenceEdges(certainMentionsByFile, entitiesById.keySet(), edgeWeights, edgeMeta);
        addSpatialEdges(entitiesById, entitiesByName, certainMentionsByFile, edgeWeights, edgeMeta);

        List<PersonGraphNodeDto> nodes = personEntities.stream()
                .filter(entity -> photoCounts.getOrDefault(entity.getId(), 0) > 0)
                .map(entity -> new PersonGraphNodeDto(
                        entity.getId(),
                        entity.getDisplayName(),
                        photoCounts.getOrDefault(entity.getId(), 0)
                ))
                .sorted(Comparator.comparing(PersonGraphNodeDto::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        List<PersonGraphEdgeDto> edges = edgeMeta.values().stream()
                .map(edge -> new PersonGraphEdgeDto(
                        edge.sourceId(),
                        edge.targetId(),
                        edge.relation(),
                        edgeWeights.getOrDefault(edgeKey(edge), 0),
                        edge.kind()
                ))
                .filter(edge -> edge.weight() > 0)
                .sorted(Comparator
                        .comparing(PersonGraphEdgeDto::kind)
                        .thenComparing(PersonGraphEdgeDto::relation, String.CASE_INSENSITIVE_ORDER))
                .toList();

        return new PersonGraphDto(nodes, edges);
    }

    private void addCoOccurrenceEdges(
            Map<String, List<EntityMention>> certainMentionsByFile,
            Set<UUID> allowedEntityIds,
            Map<String, Integer> edgeWeights,
            Map<String, PersonGraphEdgeDto> edgeMeta
    ) {
        for (List<EntityMention> mentions : certainMentionsByFile.values()) {
            Set<UUID> entityIds = new LinkedHashSet<>();
            for (EntityMention mention : mentions) {
                if (mention.getEntity() != null && allowedEntityIds.contains(mention.getEntity().getId())) {
                    entityIds.add(mention.getEntity().getId());
                }
            }
            List<UUID> ordered = new ArrayList<>(entityIds);
            for (int i = 0; i < ordered.size(); i++) {
                for (int j = i + 1; j < ordered.size(); j++) {
                    UUID a = ordered.get(i);
                    UUID b = ordered.get(j);
                    UUID source = a.compareTo(b) <= 0 ? a : b;
                    UUID target = a.compareTo(b) <= 0 ? b : a;
                    PersonGraphEdgeDto edge = new PersonGraphEdgeDto(
                            source, target, CO_OCCURRENCE_LABEL, 0, KIND_CO_OCCURRENCE
                    );
                    String key = edgeKey(edge);
                    edgeMeta.putIfAbsent(key, edge);
                    edgeWeights.merge(key, 1, Integer::sum);
                }
            }
        }
    }

    private void addSpatialEdges(
            Map<UUID, KnowledgeEntity> entitiesById,
            Map<String, KnowledgeEntity> entitiesByName,
            Map<String, List<EntityMention>> certainMentionsByFile,
            Map<String, Integer> edgeWeights,
            Map<String, PersonGraphEdgeDto> edgeMeta
    ) {
        List<Fact> facts = factRepository.findAllWithMentionAndEntity();
        for (Fact fact : facts) {
            if (fact == null || !isCertainFact(fact)) {
                continue;
            }
            String action = fact.getAction() == null ? "" : fact.getAction().trim();
            if (action.isBlank() || TECHNICAL_ACTIONS.contains(action)) {
                continue;
            }

            EntityMention subjectMention = fact.getMention();
            if (!isCertainPersonMention(subjectMention) || subjectMention.getEntity() == null) {
                continue;
            }
            UUID sourceId = subjectMention.getEntity().getId();
            if (!entitiesById.containsKey(sourceId)) {
                continue;
            }

            KnowledgeEntity target = null;
            if (fact.getTargetMention() != null
                    && isCertainPersonMention(fact.getTargetMention())) {
                target = fact.getTargetMention().getEntity();
            }
            if (target == null) {
                target = resolveObjectEntity(
                        fact.getObject(),
                        fact.getFilePath(),
                        certainMentionsByFile,
                        entitiesByName
                );
            }
            if (target == null || target.getId().equals(sourceId) || !entitiesById.containsKey(target.getId())) {
                continue;
            }

            // Keep spatial edges undirected for a stable force layout keying.
            UUID a = sourceId;
            UUID b = target.getId();
            UUID source = a.compareTo(b) <= 0 ? a : b;
            UUID targetId = a.compareTo(b) <= 0 ? b : a;
            PersonGraphEdgeDto edge = new PersonGraphEdgeDto(source, targetId, action, 0, KIND_SPATIAL);
            String key = edgeKey(edge);
            edgeMeta.putIfAbsent(key, edge);
            edgeWeights.merge(key, 1, Integer::sum);
        }
    }

    private KnowledgeEntity resolveObjectEntity(
            String objectLabel,
            String filePath,
            Map<String, List<EntityMention>> certainMentionsByFile,
            Map<String, KnowledgeEntity> entitiesByName
    ) {
        if (objectLabel == null || objectLabel.isBlank()) {
            return null;
        }
        String normalized = normalizeName(objectLabel);

        if (filePath != null) {
            List<EntityMention> onFile = certainMentionsByFile.getOrDefault(filePath, List.of());
            for (EntityMention mention : onFile) {
                if (mention.getEntity() == null) {
                    continue;
                }
                if (normalizeName(mention.getLabel()).equals(normalized)
                        || normalizeName(mention.getEntity().getDisplayName()).equals(normalized)) {
                    return mention.getEntity();
                }
            }
        }

        return entitiesByName.get(normalized);
    }

    private List<KnowledgeEntity> listNamedPersonEntities() {
        Map<String, KnowledgeEntity> byKey = new LinkedHashMap<>();
        for (KnowledgeEntity entity : entityRepository.findAll()) {
            if (entity == null || entity.getId() == null) {
                continue;
            }
            if (!"PERSON".equalsIgnoreCase(entity.getType())) {
                continue;
            }
            if (identityResolutionService.isGenericPersonLabel(entity.getDisplayName())) {
                continue;
            }
            String key = entityKey(entity.getDisplayName(), "PERSON");
            if (key == null) {
                continue;
            }
            byKey.putIfAbsent(key, entity);
        }
        return new ArrayList<>(byKey.values());
    }

    private boolean isCertainPersonMention(EntityMention mention) {
        if (!mentionEvidencePolicy.isCertain(mention)) {
            return false;
        }
        String type = mention.getEntityType();
        if (type != null && !"PERSON".equalsIgnoreCase(type)
                && (mention.getEntity().getType() == null
                || !"PERSON".equalsIgnoreCase(mention.getEntity().getType()))) {
            return false;
        }
        return true;
    }

    private boolean isCertainFact(Fact fact) {
        if (fact.getConfidence() == null || fact.getConfidence().doubleValue() < minFactConfidence) {
            return false;
        }
        EntityMention mention = fact.getMention();
        return isCertainPersonMention(mention);
    }

    private static String edgeKey(PersonGraphEdgeDto edge) {
        return edge.sourceId() + "\u0000" + edge.targetId() + "\u0000"
                + edge.kind() + "\u0000" + normalizeName(edge.relation());
    }

    private static String entityKey(String name, String type) {
        if (name == null || name.isBlank() || type == null) {
            return null;
        }
        return type + "\u0000" + name.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeName(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }
}

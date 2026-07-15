package com.rag.rag.knowledge.graph;

import com.rag.rag.knowledge.dto.PersonGraphDto;
import com.rag.rag.knowledge.entity.EntityMention;
import com.rag.rag.knowledge.entity.KnowledgeEntity;
import com.rag.rag.knowledge.entity.MentionStatus;
import com.rag.rag.knowledge.fact.Fact;
import com.rag.rag.knowledge.identity.IdentityResolutionService;
import com.rag.rag.knowledge.repository.EntityMentionRepository;
import com.rag.rag.knowledge.repository.FactRepository;
import com.rag.rag.knowledge.repository.KnowledgeEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersonRelationGraphServiceTest {

    @Mock KnowledgeEntityRepository entityRepository;
    @Mock EntityMentionRepository mentionRepository;
    @Mock FactRepository factRepository;
    @Mock IdentityResolutionService identityResolutionService;
    @InjectMocks PersonRelationGraphService service;

    private KnowledgeEntity igor;
    private KnowledgeEntity anna;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "minMentionConfidence", 0.75);
        ReflectionTestUtils.setField(service, "minFactConfidence", 0.75);
        igor = KnowledgeEntity.builder().id(UUID.randomUUID()).displayName("Igor").type("PERSON").build();
        anna = KnowledgeEntity.builder().id(UUID.randomUUID()).displayName("Anna").type("PERSON").build();
        when(identityResolutionService.isGenericPersonLabel("Igor")).thenReturn(false);
        when(identityResolutionService.isGenericPersonLabel("Anna")).thenReturn(false);
    }

    @Test
    void buildsCoOccurrenceAndSpatialEdgesFromCertainMentions() {
        when(entityRepository.findAll()).thenReturn(List.of(igor, anna));

        EntityMention igorMention = EntityMention.builder()
                .id(UUID.randomUUID())
                .entity(igor)
                .filePath("dir://pair.jpg")
                .label("Igor")
                .entityType("PERSON")
                .confidence(new BigDecimal("0.900"))
                .status(MentionStatus.CONFIRMED)
                .build();
        EntityMention annaMention = EntityMention.builder()
                .id(UUID.randomUUID())
                .entity(anna)
                .filePath("dir://pair.jpg")
                .label("Anna")
                .entityType("PERSON")
                .confidence(new BigDecimal("0.900"))
                .status(MentionStatus.CONFIRMED)
                .build();

        when(mentionRepository.findByEntityId(igor.getId())).thenReturn(List.of(igorMention));
        when(mentionRepository.findByEntityId(anna.getId())).thenReturn(List.of(annaMention));

        Fact spatial = Fact.builder()
                .id(UUID.randomUUID())
                .mention(igorMention)
                .action("z lewej od")
                .object("Anna")
                .filePath("dir://pair.jpg")
                .confidence(new BigDecimal("0.900"))
                .build();
        when(factRepository.findAllWithMentionAndEntity()).thenReturn(List.of(spatial));

        PersonGraphDto graph = service.buildPersonRelationGraph();

        assertEquals(2, graph.nodes().size());
        assertTrue(graph.edges().stream().anyMatch(e ->
                PersonRelationGraphService.KIND_CO_OCCURRENCE.equals(e.kind())
                        && e.weight() == 1));
        assertTrue(graph.edges().stream().anyMatch(e ->
                PersonRelationGraphService.KIND_SPATIAL.equals(e.kind())
                        && "z lewej od".equals(e.relation())
                        && e.weight() == 1));
    }

    @Test
    void ignoresSuggestedMentionsAndTechnicalFacts() {
        when(entityRepository.findAll()).thenReturn(List.of(igor, anna));

        EntityMention suggested = EntityMention.builder()
                .id(UUID.randomUUID())
                .entity(igor)
                .filePath("dir://maybe.jpg")
                .label("Igor")
                .entityType("PERSON")
                .confidence(new BigDecimal("0.900"))
                .status(MentionStatus.SUGGESTED)
                .build();
        when(mentionRepository.findByEntityId(igor.getId())).thenReturn(List.of(suggested));
        when(mentionRepository.findByEntityId(anna.getId())).thenReturn(List.of());

        Fact technical = Fact.builder()
                .id(UUID.randomUUID())
                .mention(suggested)
                .action("RELATED_OBJECT")
                .object("stół")
                .filePath("dir://maybe.jpg")
                .confidence(new BigDecimal("0.900"))
                .build();
        when(factRepository.findAllWithMentionAndEntity()).thenReturn(List.of(technical));

        PersonGraphDto graph = service.buildPersonRelationGraph();

        assertEquals(2, graph.nodes().size());
        assertTrue(graph.edges().isEmpty());
        assertEquals(0, graph.nodes().stream()
                .filter(n -> n.id().equals(igor.getId()))
                .findFirst()
                .orElseThrow()
                .photoCount());
    }
}

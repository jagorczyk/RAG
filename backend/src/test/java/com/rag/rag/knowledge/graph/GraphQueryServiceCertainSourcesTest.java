package com.rag.rag.knowledge.graph;

import com.rag.rag.folder.repository.FileRepository;
import com.rag.rag.knowledge.entity.EntityMention;
import com.rag.rag.knowledge.entity.KnowledgeEntity;
import com.rag.rag.knowledge.entity.MentionStatus;
import com.rag.rag.knowledge.repository.EntityMentionRepository;
import com.rag.rag.knowledge.repository.KnowledgeEntityRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GraphQueryServiceCertainSourcesTest {

    @Mock EntityManager entityManager;
    @Mock KnowledgeEntityRepository entityRepository;
    @Mock EntityMentionRepository mentionRepository;
    @Mock FileRepository fileRepository;
    @InjectMocks GraphQueryService service;

    private KnowledgeEntity entity;
    private UUID entityId;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "minMentionConfidence", 0.75);
        ReflectionTestUtils.setField(service, "minFactConfidence", 0.75);
        entityId = UUID.randomUUID();
        entity = KnowledgeEntity.builder().id(entityId).displayName("Igor").type("PERSON").build();
    }

    @Test
    void imagePathsUseOnlyConfirmedHighConfidenceMentions() {
        when(entityRepository.findAll()).thenReturn(List.of(entity));
        when(entityRepository.findFirstByDisplayNameIgnoreCase("Igor")).thenReturn(Optional.of(entity));

        EntityMention confirmed = EntityMention.builder()
                .id(UUID.randomUUID())
                .entity(entity)
                .filePath("dir://ok.jpg")
                .label("Igor")
                .confidence(new BigDecimal("0.900"))
                .status(MentionStatus.CONFIRMED)
                .build();
        EntityMention suggested = EntityMention.builder()
                .id(UUID.randomUUID())
                .entity(entity)
                .filePath("dir://maybe.jpg")
                .label("Igor")
                .confidence(new BigDecimal("0.900"))
                .status(MentionStatus.SUGGESTED)
                .build();
        EntityMention lowConfidence = EntityMention.builder()
                .id(UUID.randomUUID())
                .entity(entity)
                .filePath("dir://low.jpg")
                .label("Igor")
                .confidence(new BigDecimal("0.400"))
                .status(MentionStatus.CONFIRMED)
                .build();

        when(mentionRepository.findByEntityId(entityId)).thenReturn(List.of(confirmed, suggested, lowConfidence));
        when(fileRepository.findByPath("dir://ok.jpg")).thenReturn(Optional.of(
                com.rag.rag.folder.entity.FileEntity.builder().path("dir://ok.jpg").fileType("image/jpeg").build()));

        List<String> paths = service.imagePathsForEntities(List.of("Igor"));

        assertEquals(List.of("dir://ok.jpg"), paths);
    }

    @Test
    void hasCertainEvidenceRequiresConfirmedMention() {
        EntityMention suggested = EntityMention.builder()
                .id(UUID.randomUUID())
                .filePath("dir://x.jpg")
                .label("person 1")
                .confidence(new BigDecimal("0.900"))
                .status(MentionStatus.SUGGESTED)
                .build();
        when(mentionRepository.findByFilePath("dir://x.jpg")).thenReturn(List.of(suggested));

        jakarta.persistence.Query query = mock(jakarta.persistence.Query.class);
        when(entityManager.createNativeQuery(anyString(), eq(com.rag.rag.knowledge.fact.Fact.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());

        assertFalse(service.hasCertainEvidenceForFile("dir://x.jpg"));
    }
}

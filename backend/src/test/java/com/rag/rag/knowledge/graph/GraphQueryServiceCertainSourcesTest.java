package com.rag.rag.knowledge.graph;

import com.rag.rag.folder.repository.FileRepository;
import com.rag.rag.knowledge.entity.EntityMention;
import com.rag.rag.knowledge.entity.KnowledgeEntity;
import com.rag.rag.knowledge.entity.MentionStatus;
import com.rag.rag.knowledge.repository.EntityMentionRepository;
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
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GraphQueryServiceCertainSourcesTest {

    @Mock KnowledgeEntityRepository entityRepository;
    @Mock EntityMentionRepository mentionRepository;
    @Mock FileRepository fileRepository;
    @Mock com.rag.rag.knowledge.repository.FactRepository factRepository;
    @Mock MentionEvidencePolicy mentionEvidencePolicy;
    @Mock com.rag.rag.knowledge.identity.IdentityResolutionService identityResolutionService;
    @InjectMocks GraphQueryService service;

    private KnowledgeEntity entity;
    private UUID entityId;

    @BeforeEach
    void setUp() {
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
        when(mentionEvidencePolicy.isCertain(confirmed)).thenReturn(true);
        when(mentionEvidencePolicy.isCertain(suggested)).thenReturn(false);
        when(mentionEvidencePolicy.isCertain(lowConfidence)).thenReturn(false);
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

        when(mentionEvidencePolicy.isCertain(suggested)).thenReturn(false);
        when(factRepository.findByFilePath("dir://x.jpg")).thenReturn(List.of());

        assertFalse(service.hasCertainEvidenceForFile("dir://x.jpg"));
    }

    @Test
    void allEntityPathsReturnOnlyTheIntersection() {
        KnowledgeEntity anna = KnowledgeEntity.builder().id(UUID.randomUUID())
                .displayName("Anna").type("PERSON").build();
        when(entityRepository.findAll()).thenReturn(List.of(entity, anna));
        when(entityRepository.findFirstByDisplayNameIgnoreCase("Igor")).thenReturn(Optional.of(entity));
        when(entityRepository.findFirstByDisplayNameIgnoreCase("Anna")).thenReturn(Optional.of(anna));

        EntityMention igorShared = confirmed(entity, "dir://shared.jpg");
        EntityMention igorOnly = confirmed(entity, "dir://igor.jpg");
        EntityMention annaShared = confirmed(anna, "dir://shared.jpg");
        when(mentionRepository.findByEntityId(entityId)).thenReturn(List.of(igorShared, igorOnly));
        when(mentionRepository.findByEntityId(anna.getId())).thenReturn(List.of(annaShared));
        when(mentionEvidencePolicy.isCertain(any(EntityMention.class))).thenReturn(true);
        when(fileRepository.findByPath(anyString())).thenAnswer(invocation -> Optional.of(
                com.rag.rag.folder.entity.FileEntity.builder()
                        .path(invocation.getArgument(0)).fileType("image/jpeg").build()));

        assertEquals(List.of("dir://shared.jpg"),
                service.imagePathsForAllEntities(List.of("Igor", "Anna")));
    }

    private EntityMention confirmed(KnowledgeEntity owner, String path) {
        return EntityMention.builder().id(UUID.randomUUID()).entity(owner).filePath(path)
                .label(owner.getDisplayName()).confidence(new BigDecimal("0.900"))
                .status(MentionStatus.CONFIRMED).build();
    }
}

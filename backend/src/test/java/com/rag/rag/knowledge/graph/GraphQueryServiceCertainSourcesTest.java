package com.rag.rag.knowledge.graph;

import com.rag.rag.auth.security.CurrentUserService;
import com.rag.rag.folder.repository.FileRepository;
import com.rag.rag.folder.entity.FileEntity;
import com.rag.rag.knowledge.entity.EntityMention;
import com.rag.rag.knowledge.entity.KnowledgeEntity;
import com.rag.rag.knowledge.entity.MentionStatus;
import com.rag.rag.knowledge.fact.Fact;
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
    @Mock CurrentUserService currentUserService;
    @InjectMocks GraphQueryService service;

    private KnowledgeEntity entity;
    private UUID entityId;
    private final UUID ownerId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "minFactConfidence", 0.75);
        entityId = UUID.randomUUID();
        entity = KnowledgeEntity.builder().id(entityId).displayName("Igor").type("PERSON").ownerId(ownerId).build();
        lenient().when(currentUserService.findUserId()).thenReturn(Optional.of(ownerId));
    }

    @Test
    void certainParticipantNamesForPathsReturnsOnlyCertainNonGenericNames() {
        KnowledgeEntity igor = entity;
        KnowledgeEntity animal = KnowledgeEntity.builder()
                .id(UUID.randomUUID()).displayName("animal 1").type("ANIMAL").ownerId(ownerId).build();
        KnowledgeEntity pendingEntity = KnowledgeEntity.builder()
                .id(UUID.randomUUID()).displayName("person 1").type("PERSON").ownerId(ownerId).build();

        EntityMention confirmedIgor = EntityMention.builder()
                .id(UUID.randomUUID()).entity(igor).filePath("dir://photo.jpg")
                .label("Igor").confidence(new BigDecimal("0.900")).status(MentionStatus.CONFIRMED).build();
        EntityMention suggested = EntityMention.builder()
                .id(UUID.randomUUID()).entity(igor).filePath("dir://photo.jpg")
                .label("Igor").confidence(new BigDecimal("0.900")).status(MentionStatus.SUGGESTED).build();
        EntityMention pendingPerson = EntityMention.builder()
                .id(UUID.randomUUID()).entity(pendingEntity).filePath("dir://photo.jpg")
                .label("person 1").confidence(new BigDecimal("0.900")).status(MentionStatus.PENDING).build();
        EntityMention confirmedAnimal = EntityMention.builder()
                .id(UUID.randomUUID()).entity(animal).filePath("dir://photo.jpg")
                .label("animal 1").confidence(new BigDecimal("0.900")).status(MentionStatus.CONFIRMED).build();

        when(mentionRepository.findByFilePath("dir://photo.jpg"))
                .thenReturn(List.of(confirmedIgor, suggested, pendingPerson, confirmedAnimal));
        when(mentionEvidencePolicy.isCertain(confirmedIgor)).thenReturn(true);
        when(mentionEvidencePolicy.isCertain(suggested)).thenReturn(false);
        when(mentionEvidencePolicy.isCertain(pendingPerson)).thenReturn(false);
        when(mentionEvidencePolicy.isCertain(confirmedAnimal)).thenReturn(true);
        when(identityResolutionService.isGenericPersonLabel("Igor")).thenReturn(false);

        List<String> names = service.certainParticipantNamesForPaths(List.of("dir://photo.jpg"));

        assertEquals(List.of("Igor"), names);
        assertFalse(names.contains("person 1"));
        assertFalse(names.contains("animal 1"));
    }

    @Test
    void imagePathsUseOnlyConfirmedHighConfidenceMentions() {
        when(entityRepository.findAllByOwnerId(ownerId)).thenReturn(List.of(entity));
        when(entityRepository.findFirstByDisplayNameIgnoreCaseAndOwnerId("Igor", ownerId)).thenReturn(Optional.of(entity));

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
    void resolvesAtFileOnlyInsideCurrentOwnerLibrary() {
        FileEntity selected = FileEntity.builder().path("dir://mine/photo.jpg")
                .fileName("photo.jpg").ownerId(ownerId).build();
        FileEntity other = FileEntity.builder().path("dir://mine/other.jpg")
                .fileName("other.jpg").ownerId(ownerId).build();
        when(fileRepository.findAllByOwnerId(ownerId)).thenReturn(List.of(selected, other));

        assertEquals(List.of(selected.getPath()),
                service.resolveExplicitFileScope("Co przedstawia @photo.jpg?"));
    }

    @Test
    void resolvesAtFileWithSpacesFromOwnedLibrary() {
        FileEntity selected = FileEntity.builder().path("dir://mine/wakacje/photo one.jpg")
                .fileName("photo one.jpg").ownerId(ownerId).build();
        when(fileRepository.findAllByOwnerId(ownerId)).thenReturn(List.of(selected));

        assertEquals(List.of(selected.getPath()),
                service.resolveExplicitFileScope("Co przedstawia @photo one.jpg?"));
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
    void relationWithUncertainTargetIsNotCertainGraphEvidence() {
        EntityMention subject = confirmed(entity, "dir://relation.jpg");
        EntityMention target = EntityMention.builder().id(UUID.randomUUID())
                .filePath("dir://relation.jpg").label("person 2")
                .confidence(new BigDecimal("0.900")).status(MentionStatus.PENDING).build();
        Fact relation = Fact.builder().mention(subject).targetMention(target).action("obok")
                .object("person 2").filePath("dir://relation.jpg")
                .confidence(new BigDecimal("0.900")).build();
        when(factRepository.findByFilePath("dir://relation.jpg")).thenReturn(List.of(relation));
        when(mentionEvidencePolicy.isCertain(subject)).thenReturn(true);
        when(mentionEvidencePolicy.isCertain(target)).thenReturn(false);

        assertTrue(service.getCertainFactsForFile("dir://relation.jpg").isEmpty());
    }

    @Test
    void allEntityPathsReturnOnlyTheIntersection() {
        KnowledgeEntity anna = KnowledgeEntity.builder().id(UUID.randomUUID())
                .displayName("Anna").type("PERSON").build();
        when(entityRepository.findAllByOwnerId(ownerId)).thenReturn(List.of(entity, anna));
        when(entityRepository.findFirstByDisplayNameIgnoreCaseAndOwnerId("Igor", ownerId)).thenReturn(Optional.of(entity));
        when(entityRepository.findFirstByDisplayNameIgnoreCaseAndOwnerId("Anna", ownerId)).thenReturn(Optional.of(anna));

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

    @Test
    void buildEvidenceAllSameFileEmptyWhenNoJointPhoto() {
        KnowledgeEntity anna = KnowledgeEntity.builder().id(UUID.randomUUID())
                .displayName("Anna").type("PERSON").build();
        when(entityRepository.findAllByOwnerId(ownerId)).thenReturn(List.of(entity, anna));
        when(entityRepository.findFirstByDisplayNameIgnoreCaseAndOwnerId("Igor", ownerId)).thenReturn(Optional.of(entity));
        when(entityRepository.findFirstByDisplayNameIgnoreCaseAndOwnerId("Anna", ownerId)).thenReturn(Optional.of(anna));

        EntityMention igorOnly = confirmed(entity, "dir://only-igor.jpg");
        EntityMention annaOnly = confirmed(anna, "dir://only-anna.jpg");
        when(mentionRepository.findByEntityId(entityId)).thenReturn(List.of(igorOnly));
        when(mentionRepository.findByEntityId(anna.getId())).thenReturn(List.of(annaOnly));
        when(mentionEvidencePolicy.isCertain(any(EntityMention.class))).thenReturn(true);
        when(fileRepository.findByPath(anyString())).thenAnswer(invocation -> Optional.of(
                com.rag.rag.folder.entity.FileEntity.builder()
                        .path(invocation.getArgument(0)).fileType("image/jpeg").build()));

        GraphEvidenceResult evidence = service.buildEvidence(
                List.of("Igor", "Anna"), List.of(), EntityMatchMode.ALL_SAME_FILE);

        assertFalse(evidence.hasEvidence());
        assertTrue(evidence.certainPaths().isEmpty());
        assertTrue(evidence.context().isBlank());
    }

    @Test
    void buildEvidenceAllSameFileReturnsOnlyJointPaths() {
        KnowledgeEntity anna = KnowledgeEntity.builder().id(UUID.randomUUID())
                .displayName("Anna").type("PERSON").build();
        when(entityRepository.findAllByOwnerId(ownerId)).thenReturn(List.of(entity, anna));
        when(entityRepository.findFirstByDisplayNameIgnoreCaseAndOwnerId("Igor", ownerId)).thenReturn(Optional.of(entity));
        when(entityRepository.findFirstByDisplayNameIgnoreCaseAndOwnerId("Anna", ownerId)).thenReturn(Optional.of(anna));

        EntityMention igorShared = confirmed(entity, "dir://shared.jpg");
        EntityMention igorOnly = confirmed(entity, "dir://only-igor.jpg");
        EntityMention annaShared = confirmed(anna, "dir://shared.jpg");
        when(mentionRepository.findByEntityId(entityId)).thenReturn(List.of(igorShared, igorOnly));
        when(mentionRepository.findByEntityId(anna.getId())).thenReturn(List.of(annaShared));
        when(mentionEvidencePolicy.isCertain(any(EntityMention.class))).thenReturn(true);
        when(fileRepository.findByPath(anyString())).thenAnswer(invocation -> Optional.of(
                com.rag.rag.folder.entity.FileEntity.builder()
                        .path(invocation.getArgument(0)).fileType("image/jpeg").build()));
        when(fileRepository.findByPathAndOwnerId("dir://shared.jpg", ownerId)).thenReturn(Optional.of(
                com.rag.rag.folder.entity.FileEntity.builder().path("dir://shared.jpg")
                        .fileType("image/jpeg").ownerId(ownerId).build()));
        when(mentionRepository.findByFilePath("dir://shared.jpg")).thenReturn(List.of(igorShared, annaShared));
        when(factRepository.findByFilePath("dir://shared.jpg")).thenReturn(List.of());

        GraphEvidenceResult evidence = service.buildEvidence(
                List.of("Igor", "Anna"), List.of(), EntityMatchMode.ALL_SAME_FILE);

        assertTrue(evidence.hasEvidence());
        assertEquals(List.of("dir://shared.jpg"), evidence.certainPaths());
        assertFalse(evidence.certainPaths().contains("dir://only-igor.jpg"));
        assertTrue(evidence.context().contains("współwystępowanie"));
    }

    private EntityMention confirmed(KnowledgeEntity owner, String path) {
        return EntityMention.builder().id(UUID.randomUUID()).entity(owner).filePath(path)
                .label(owner.getDisplayName()).confidence(new BigDecimal("0.900"))
                .status(MentionStatus.CONFIRMED).build();
    }
}

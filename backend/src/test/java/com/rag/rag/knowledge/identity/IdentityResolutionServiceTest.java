package com.rag.rag.knowledge.identity;

import com.rag.rag.auth.security.CurrentUserService;
import com.rag.rag.core.cache.IdentityMatchCacheService;
import com.rag.rag.folder.repository.FileRepository;
import com.rag.rag.knowledge.entity.AliasSource;
import com.rag.rag.knowledge.entity.EntityAlias;
import com.rag.rag.knowledge.entity.EntityMention;
import com.rag.rag.knowledge.entity.IdentityEvidenceSource;
import com.rag.rag.knowledge.entity.KnowledgeEntity;
import com.rag.rag.knowledge.entity.MentionStatus;
import com.rag.rag.knowledge.repository.EntityAliasRepository;
import com.rag.rag.knowledge.repository.EntityMentionRepository;
import com.rag.rag.knowledge.repository.FaceEmbeddingRepository;
import com.rag.rag.knowledge.repository.FactRepository;
import com.rag.rag.knowledge.repository.IdentitySuggestionRepository;
import com.rag.rag.knowledge.repository.KnowledgeEntityRepository;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityResolutionServiceTest {

    @Mock
    private KnowledgeEntityRepository entityRepository;
    @Mock
    private EntityAliasRepository aliasRepository;
    @Mock
    private EntityMentionRepository mentionRepository;
    @Mock
    private IdentitySuggestionRepository suggestionRepository;
    @Mock
    private FaceEmbeddingRepository faceEmbeddingRepository;
    @Mock
    private FactRepository factRepository;
    @Mock
    private FileRepository fileRepository;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private ChatLanguageModel chatModel;
    @Mock
    private IdentityMatchCacheService identityMatchCacheService;
    @Mock
    private ObjectProvider<CanonicalEmbeddingRefreshService> embeddingRefreshProvider;
    @Mock
    private CanonicalEmbeddingRefreshService embeddingRefresh;

    private IdentityResolutionService service;
    private final UUID ownerId = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @BeforeEach
    void setUp() {
        lenient().when(currentUserService.findUserId()).thenReturn(Optional.of(ownerId));
        lenient().when(identityMatchCacheService.buildKey(any(), any(), org.mockito.ArgumentMatchers.anyDouble()))
                .thenAnswer(inv -> "key:" + inv.getArgument(1));
        lenient().when(embeddingRefreshProvider.getIfAvailable()).thenReturn(embeddingRefresh);
        service = new IdentityResolutionService(
                entityRepository,
                aliasRepository,
                mentionRepository,
                suggestionRepository,
                faceEmbeddingRepository,
                factRepository,
                fileRepository,
                currentUserService,
                chatModel,
                identityMatchCacheService,
                embeddingRefreshProvider
        );
        ReflectionTestUtils.setField(service, "autoConfirmThreshold", 0.85);
        ReflectionTestUtils.setField(service, "suggestThreshold", 0.60);
        ReflectionTestUtils.setField(service, "autoConfirmMinMargin", 0.05);
        ReflectionTestUtils.setField(service, "heuristicMaxCandidates", 40);
        ReflectionTestUtils.setField(service, "llmMatcherEnabled", false);
        ReflectionTestUtils.setField(service, "llmMatcherMaxCandidates", 2);
    }

    @ParameterizedTest
    @CsvSource({
            "Bartek, true",
            "Pati Kowalska, true",
            "mężczyzna w czerwonej koszulce, false",
            "Osoba 1, false",
            "person 1, false",
            "Person 2, false",
            "nieznana postać, false"
    })
    void shouldDetectPersonNameLabels(String label, boolean expected) {
        assertEquals(expected, service.looksLikePersonName(label));
    }

    @ParameterizedTest
    @CsvSource({
            "person 1, true",
            "Person 2, true",
            "osoba 3, true",
            "Bartek, false"
    })
    void shouldTreatVisionPlaceholdersAsGeneric(String label, boolean expected) {
        assertEquals(expected, service.isGenericPersonLabel(label));
    }

    @Test
    void shouldLinkMentionWithEntityTagAsConfirmed() {
        EntityMention mention = EntityMention.builder()
                .id(UUID.randomUUID())
                .label("mężczyzna w czerwonej koszulce")
                .build();

        KnowledgeEntity entity = KnowledgeEntity.builder()
                .id(UUID.randomUUID())
                .displayName("Bartek")
                .type("PERSON")
                .build();

        when(entityRepository.findFirstByDisplayNameIgnoreCaseAndTypeIgnoreCaseAndOwnerId("Bartek", "PERSON", ownerId))
                .thenReturn(Optional.empty());
        when(entityRepository.save(any(KnowledgeEntity.class))).thenReturn(entity);
        when(aliasRepository.save(any())).thenReturn(null);

        service.resolve(mention, "Bartek", "PERSON");

        ArgumentCaptor<EntityMention> captor = ArgumentCaptor.forClass(EntityMention.class);
        verify(mentionRepository).save(captor.capture());
        assertEquals(entity, captor.getValue().getEntity());
        assertEquals(MentionStatus.CONFIRMED, captor.getValue().getStatus());
        verify(mentionRepository, never()).findAll();
    }

    @Test
    void shouldKeepDescriptiveVisionLabelPendingWithoutEntity() {
        EntityMention mention = EntityMention.builder()
                .id(UUID.randomUUID())
                .label("mężczyzna w czerwonej koszulce")
                .build();

        KnowledgeEntity savedEntity = KnowledgeEntity.builder()
                .id(UUID.randomUUID())
                .displayName("mężczyzna w czerwonej koszulce")
                .type("PERSON")
                .build();

        service.resolve(mention, null, "PERSON");

        ArgumentCaptor<EntityMention> captor = ArgumentCaptor.forClass(EntityMention.class);
        verify(mentionRepository).save(captor.capture());
        assertEquals(MentionStatus.PENDING, captor.getValue().getStatus());
        assertEquals(null, captor.getValue().getEntity());
        assertFalse(service.looksLikePersonName("mężczyzna w czerwonej koszulce"));
    }

    @Test
    void shouldLinkKnownPersonNameToExistingEntity() {
        EntityMention mention = EntityMention.builder()
                .id(UUID.randomUUID())
                .label("Bartek")
                .build();

        KnowledgeEntity existing = KnowledgeEntity.builder()
                .id(UUID.randomUUID())
                .displayName("Bartek")
                .type("PERSON")
                .build();

        when(entityRepository.findFirstByDisplayNameIgnoreCaseAndTypeIgnoreCaseAndOwnerId("Bartek", "PERSON", ownerId))
                .thenReturn(Optional.of(existing));

        service.resolve(mention, null, "PERSON");

        ArgumentCaptor<EntityMention> captor = ArgumentCaptor.forClass(EntityMention.class);
        verify(mentionRepository).save(captor.capture());
        assertEquals(existing, captor.getValue().getEntity());
        assertEquals(MentionStatus.CONFIRMED, captor.getValue().getStatus());
        assertTrue(service.looksLikePersonName("Bartek"));
    }

    @Test
    void shouldConfirmFaceMatchAndAddVisionLabelAlias() {
        KnowledgeEntity igor = KnowledgeEntity.builder()
                .id(UUID.randomUUID())
                .displayName("Igor")
                .type("PERSON")
                .build();
        EntityMention mention = EntityMention.builder()
                .id(UUID.randomUUID())
                .label("mężczyzna")
                .build();

        when(aliasRepository.findFirstByAliasIgnoreCaseAndEntity_TypeIgnoreCase("mężczyzna", "PERSON"))
                .thenReturn(Optional.empty());
        when(aliasRepository.save(any())).thenReturn(null);

        service.confirmFaceMatch(mention, igor, mention.getLabel(), 0.72, 0.12);

        ArgumentCaptor<EntityMention> mentionCaptor = ArgumentCaptor.forClass(EntityMention.class);
        verify(mentionRepository).save(mentionCaptor.capture());
        assertEquals(MentionStatus.CONFIRMED, mentionCaptor.getValue().getStatus());
        assertEquals(igor, mentionCaptor.getValue().getEntity());
        assertEquals(IdentityEvidenceSource.FACE_MATCH, mentionCaptor.getValue().getIdentitySource());
        assertEquals("0.720", mentionCaptor.getValue().getIdentityConfidence().toPlainString());
        verify(aliasRepository).save(any());
    }

    @Test
    void manualAssignmentStoresAuthoritativeIdentityEvidence() {
        KnowledgeEntity person = KnowledgeEntity.builder().id(UUID.randomUUID())
                .displayName("Bartek").type("PERSON").build();
        EntityMention mention = EntityMention.builder().id(UUID.randomUUID())
                .label("Bartek").confidence(new java.math.BigDecimal("0.736")).build();

        service.confirmUserAssignment(mention, person);

        assertEquals(MentionStatus.CONFIRMED, mention.getStatus());
        assertEquals(IdentityEvidenceSource.USER, mention.getIdentitySource());
        assertEquals("1.000", mention.getIdentityConfidence().toPlainString());
        assertEquals("0.736", mention.getConfidence().toPlainString());
    }

    @Test
    void shouldKeepSameNameSeparatedByEntityType() {
        KnowledgeEntity person = KnowledgeEntity.builder()
                .id(UUID.randomUUID())
                .displayName("Figa")
                .type("PERSON")
                .build();
        KnowledgeEntity animal = KnowledgeEntity.builder()
                .id(UUID.randomUUID())
                .displayName("Figa")
                .type("ANIMAL")
                .build();

        when(entityRepository.findFirstByDisplayNameIgnoreCaseAndTypeIgnoreCaseAndOwnerId("Figa", "PERSON", ownerId))
                .thenReturn(Optional.of(person));
        when(entityRepository.findFirstByDisplayNameIgnoreCaseAndTypeIgnoreCaseAndOwnerId("Figa", "ANIMAL", ownerId))
                .thenReturn(Optional.of(animal));

        assertEquals(person, service.findOrCreateEntityByName("Figa", "PERSON"));
        assertEquals(animal, service.findOrCreateEntityByName("Figa", "ANIMAL"));
    }

    @Test
    void shouldRejectUnsupportedEntityType() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.findOrCreateEntityByName("Krzesło", "OBJECT")
        );
    }

    @Test
    void resolveConfirmsWhenHeuristicScoreAtLeast085() {
        // Equal non-generic labels → heuristic 0.99 ≥ 0.85 → auto-confirm to candidate entity
        KnowledgeEntity known = KnowledgeEntity.builder()
                .id(UUID.randomUUID()).displayName("Szymon").type("PERSON").ownerId(ownerId).build();
        EntityMention candidate = EntityMention.builder()
                .id(UUID.randomUUID()).label("Szymon").entity(known).entityType("PERSON")
                .filePath("dir://photos/a.jpg").build();
        EntityMention mention = EntityMention.builder()
                .id(UUID.randomUUID()).label("Szymon").entityType("PERSON")
                .filePath("dir://photos/b.jpg").build();

        when(entityRepository.findFirstByDisplayNameIgnoreCaseAndTypeIgnoreCaseAndOwnerId("Szymon", "PERSON", ownerId))
                .thenReturn(Optional.empty());
        when(aliasRepository.findFirstByAliasIgnoreCaseAndEntity_TypeIgnoreCaseAndEntity_OwnerId(
                "Szymon", "PERSON", ownerId)).thenReturn(Optional.empty());
        when(mentionRepository.findLinkedByEntityTypeAndOwner("PERSON", ownerId))
                .thenReturn(List.of(candidate));

        service.resolve(mention, null, "PERSON");

        ArgumentCaptor<EntityMention> captor = ArgumentCaptor.forClass(EntityMention.class);
        verify(mentionRepository).save(captor.capture());
        assertEquals(MentionStatus.CONFIRMED, captor.getValue().getStatus());
        assertEquals(known, captor.getValue().getEntity());
        assertEquals(IdentityEvidenceSource.DESCRIPTION_MATCH, captor.getValue().getIdentitySource());
        verify(suggestionRepository, never()).save(any());
    }

    @Test
    void resolveCreatesPendingSuggestionInBand060To085() {
        // Partial label overlap + shared visual cues → score in suggestion band, not auto-confirm
        KnowledgeEntity known = KnowledgeEntity.builder()
                .id(UUID.randomUUID()).displayName("Jan Nowak").type("PERSON").ownerId(ownerId).build();
        String cues = "[\"czerwona koszulka\",\"krótkie włosy\"]";
        EntityMention candidate = EntityMention.builder()
                .id(UUID.randomUUID())
                .label("Jan Nowak")
                .entity(known)
                .entityType("PERSON")
                .filePath("dir://photos/a.jpg")
                .visualCues(cues)
                .build();
        EntityMention mention = EntityMention.builder()
                .id(UUID.randomUUID())
                .label("Nowak")
                .entityType("PERSON")
                .filePath("dir://photos/b.jpg")
                .visualCues(cues)
                .build();

        when(entityRepository.findFirstByDisplayNameIgnoreCaseAndTypeIgnoreCaseAndOwnerId("Nowak", "PERSON", ownerId))
                .thenReturn(Optional.empty());
        when(aliasRepository.findFirstByAliasIgnoreCaseAndEntity_TypeIgnoreCaseAndEntity_OwnerId(
                "Nowak", "PERSON", ownerId)).thenReturn(Optional.empty());
        when(mentionRepository.findLinkedByEntityTypeAndOwner("PERSON", ownerId))
                .thenReturn(List.of(candidate));
        when(suggestionRepository.existsBetweenMentions(mention.getId(), candidate.getId())).thenReturn(false);
        when(entityRepository.save(any(KnowledgeEntity.class))).thenAnswer(inv -> {
            KnowledgeEntity e = inv.getArgument(0);
            if (e.getId() == null) {
                e.setId(UUID.randomUUID());
            }
            return e;
        });
        when(aliasRepository.save(any())).thenReturn(null);

        service.resolve(mention, null, "PERSON");

        ArgumentCaptor<IdentitySuggestion> suggestionCaptor = ArgumentCaptor.forClass(IdentitySuggestion.class);
        verify(suggestionRepository).save(suggestionCaptor.capture());
        assertEquals(SuggestionStatus.PENDING, suggestionCaptor.getValue().getStatus());
        assertTrue(suggestionCaptor.getValue().getSimilarityScore().doubleValue() >= 0.60);
        assertTrue(suggestionCaptor.getValue().getSimilarityScore().doubleValue() < 0.85);
    }

    @Test
    void preFilterKeepsSameFolderOrSharedLabelTokens() {
        EntityMention mention = EntityMention.builder()
                .id(UUID.randomUUID()).label("Anna").filePath("dir://wakacje/1.jpg").build();
        EntityMention sameFolder = EntityMention.builder()
                .id(UUID.randomUUID()).label("other name").filePath("dir://wakacje/2.jpg").build();
        EntityMention sharedToken = EntityMention.builder()
                .id(UUID.randomUUID()).label("Anna Kowalska").filePath("dir://inne/x.jpg").build();
        EntityMention unrelated = EntityMention.builder()
                .id(UUID.randomUUID()).label("Piotr").filePath("dir://inne/y.jpg").build();

        assertTrue(service.passesIdentityPreFilter(mention, sameFolder));
        assertTrue(service.passesIdentityPreFilter(mention, sharedToken));
        assertFalse(service.passesIdentityPreFilter(mention, unrelated));
        assertEquals("dir://wakacje/", IdentityResolutionService.folderPrefix("dir://wakacje/1.jpg"));
    }

    @Test
    void loadAndPrefilterCandidatesCapsAndSkipsUnrelated() {
        ReflectionTestUtils.setField(service, "heuristicMaxCandidates", 2);
        KnowledgeEntity entity = KnowledgeEntity.builder()
                .id(UUID.randomUUID()).displayName("X").type("PERSON").ownerId(ownerId).build();
        EntityMention mention = EntityMention.builder()
                .id(UUID.randomUUID()).label("Anna").filePath("dir://f/a.jpg").build();
        EntityMention c1 = EntityMention.builder().id(UUID.randomUUID()).label("Anna A")
                .entity(entity).filePath("dir://f/b.jpg").build();
        EntityMention c2 = EntityMention.builder().id(UUID.randomUUID()).label("Anna B")
                .entity(entity).filePath("dir://f/c.jpg").build();
        EntityMention c3 = EntityMention.builder().id(UUID.randomUUID()).label("Anna C")
                .entity(entity).filePath("dir://f/d.jpg").build();
        EntityMention far = EntityMention.builder().id(UUID.randomUUID()).label("Zygmunt")
                .entity(entity).filePath("dir://other/z.jpg").build();

        when(mentionRepository.findLinkedByEntityTypeAndOwner("PERSON", ownerId))
                .thenReturn(List.of(c1, c2, c3, far));

        List<EntityMention> result = service.loadAndPrefilterCandidates(mention, "PERSON", ownerId);

        assertEquals(2, result.size());
        assertFalse(result.contains(far));
        verify(mentionRepository, never()).findAll();
    }

    @Test
    void suggestFaceMatchPersistsPendingSuggestion() {
        KnowledgeEntity matched = KnowledgeEntity.builder()
                .id(UUID.randomUUID()).displayName("Igor").type("PERSON").ownerId(ownerId).build();
        EntityMention existing = EntityMention.builder()
                .id(UUID.randomUUID()).label("Igor").entity(matched).build();
        EntityMention mention = EntityMention.builder()
                .id(UUID.randomUUID()).label("person 1").build();

        when(mentionRepository.findByEntityId(matched.getId())).thenReturn(List.of(existing));
        when(suggestionRepository.existsBetweenMentions(mention.getId(), existing.getId())).thenReturn(false);

        service.suggestFaceMatch(mention, matched, 0.68);

        ArgumentCaptor<IdentitySuggestion> captor = ArgumentCaptor.forClass(IdentitySuggestion.class);
        verify(suggestionRepository).save(captor.capture());
        assertEquals(SuggestionStatus.PENDING, captor.getValue().getStatus());
        assertEquals(0, new BigDecimal("0.68").compareTo(captor.getValue().getSimilarityScore()));
    }

    @Test
    void renameNamedEntityUpdatesDisplayNameAndKeepsOldAsAlias() {
        UUID entityId = UUID.randomUUID();
        KnowledgeEntity entity = KnowledgeEntity.builder()
                .id(entityId).displayName("Bartek").type("PERSON").ownerId(ownerId).build();
        when(entityRepository.findById(entityId)).thenReturn(Optional.of(entity));
        when(entityRepository.findFirstByDisplayNameIgnoreCaseAndTypeIgnoreCaseAndOwnerId("Bartosz", "PERSON", ownerId))
                .thenReturn(Optional.empty());
        when(aliasRepository.findFirstByAliasIgnoreCaseAndEntity_TypeIgnoreCaseAndEntity_OwnerId(
                "Bartosz", "PERSON", ownerId)).thenReturn(Optional.empty());
        when(entityRepository.save(any(KnowledgeEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mentionRepository.findByEntityId(entityId)).thenReturn(List.of());
        when(factRepository.findAllWithMentionAndEntity()).thenReturn(List.of());
        when(aliasRepository.findAll()).thenReturn(List.of());
        when(aliasRepository.findFirstByAliasIgnoreCaseAndEntity_TypeIgnoreCase("Bartek", "PERSON"))
                .thenReturn(Optional.empty());

        KnowledgeEntity renamed = service.renameNamedEntity(entityId, "Bartosz");

        assertEquals("Bartosz", renamed.getDisplayName());
        ArgumentCaptor<EntityAlias> aliasCaptor = ArgumentCaptor.forClass(EntityAlias.class);
        verify(aliasRepository).save(aliasCaptor.capture());
        assertEquals("Bartek", aliasCaptor.getValue().getAlias());
    }

    @Test
    void renameNamedEntityMergesIntoExistingNameCollision() {
        UUID sourceId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        KnowledgeEntity source = KnowledgeEntity.builder()
                .id(sourceId).displayName("Igor K").type("PERSON").ownerId(ownerId).build();
        KnowledgeEntity target = KnowledgeEntity.builder()
                .id(targetId).displayName("Igor").type("PERSON").ownerId(ownerId).build();
        EntityMention sourceMention = EntityMention.builder()
                .id(UUID.randomUUID()).label("Igor K").entity(source).filePath("dir://a.jpg").build();

        when(entityRepository.findById(sourceId)).thenReturn(Optional.of(source));
        when(entityRepository.findFirstByDisplayNameIgnoreCaseAndTypeIgnoreCaseAndOwnerId("Igor", "PERSON", ownerId))
                .thenReturn(Optional.of(target));
        when(mentionRepository.findByEntityId(sourceId)).thenReturn(List.of(sourceMention));
        when(mentionRepository.findByEntityId(targetId)).thenReturn(List.of());
        when(aliasRepository.findAll()).thenReturn(List.of());
        when(factRepository.findAllWithMentionAndEntity()).thenReturn(List.of());

        KnowledgeEntity result = service.renameNamedEntity(sourceId, "Igor");

        assertEquals(target, result);
        verify(mentionRepository).save(sourceMention);
        assertEquals(target, sourceMention.getEntity());
        assertEquals(MentionStatus.CONFIRMED, sourceMention.getStatus());
        verify(entityRepository).delete(source);
        verify(faceEmbeddingRepository).relinkEntity(sourceId, target);
    }

    @Test
    void consolidateDuplicateEntitiesMergesSameNameAndType() {
        KnowledgeEntity a = KnowledgeEntity.builder()
                .id(UUID.randomUUID()).displayName("Pati").type("PERSON").ownerId(ownerId).build();
        KnowledgeEntity b = KnowledgeEntity.builder()
                .id(UUID.randomUUID()).displayName("Pati").type("PERSON").ownerId(ownerId).build();
        when(entityRepository.findAllByOwnerId(ownerId)).thenReturn(List.of(a, b));
        when(mentionRepository.findByEntityId(b.getId())).thenReturn(List.of());
        when(aliasRepository.findAll()).thenReturn(List.of());

        int merged = service.consolidateDuplicateEntities();

        assertEquals(1, merged);
        verify(entityRepository).delete(b);
        verify(faceEmbeddingRepository).relinkEntity(b.getId(), a);
    }

    @Test
    void afterMentionIdentityAssignedKeepsPreviousEntityWithUserAlias() {
        UUID previousId = UUID.randomUUID();
        UUID mentionId = UUID.randomUUID();
        KnowledgeEntity previous = KnowledgeEntity.builder()
                .id(previousId).displayName("Stara").type("PERSON").ownerId(ownerId).build();
        KnowledgeEntity next = KnowledgeEntity.builder()
                .id(UUID.randomUUID()).displayName("Nowa").type("PERSON").ownerId(ownerId).build();
        EntityMention mention = EntityMention.builder()
                .id(mentionId).label("Nowa").entity(next).filePath("dir://x.jpg").build();

        when(mentionRepository.findById(mentionId)).thenReturn(Optional.of(mention));
        when(mentionRepository.findByEntityId(previousId)).thenReturn(List.of());
        when(aliasRepository.existsByEntityIdAndSource(previousId, AliasSource.USER)).thenReturn(true);
        when(aliasRepository.findAll()).thenReturn(List.of());
        when(factRepository.findByFilePath("dir://x.jpg")).thenReturn(List.of());

        service.afterMentionIdentityAssigned(mentionId, previousId, "Nowa");

        verify(entityRepository, never()).delete(any());
        verify(faceEmbeddingRepository, never()).deleteByEntityId(previousId);
    }

    @Test
    void afterMentionIdentityAssignedDeletesTrueOrphanPreviousEntity() {
        UUID previousId = UUID.randomUUID();
        UUID mentionId = UUID.randomUUID();
        KnowledgeEntity previous = KnowledgeEntity.builder()
                .id(previousId).displayName("Orphan").type("PERSON").ownerId(ownerId).build();
        KnowledgeEntity next = KnowledgeEntity.builder()
                .id(UUID.randomUUID()).displayName("Nowa").type("PERSON").ownerId(ownerId).build();
        EntityMention mention = EntityMention.builder()
                .id(mentionId).label("Nowa").entity(next).filePath("dir://x.jpg").build();

        when(mentionRepository.findById(mentionId)).thenReturn(Optional.of(mention));
        when(mentionRepository.findByEntityId(previousId)).thenReturn(List.of());
        when(aliasRepository.existsByEntityIdAndSource(previousId, AliasSource.USER)).thenReturn(false);
        when(entityRepository.findById(previousId)).thenReturn(Optional.of(previous));
        when(aliasRepository.findAll()).thenReturn(List.of());
        when(factRepository.findByFilePath("dir://x.jpg")).thenReturn(List.of());

        service.afterMentionIdentityAssigned(mentionId, previousId, "Nowa");

        verify(faceEmbeddingRepository).deleteByEntityId(previousId);
        verify(aliasRepository).deleteByEntityId(previousId);
        verify(entityRepository).delete(previous);
    }

    @Test
    void ambiguousNearTieDoesNotAutoConfirmWrongPeer() {
        // Best and second both clear the auto-confirm floor but are too close — keep uncertain.
        assertTrue(service.isUnambiguousAutoConfirm(0.92, 0.0));
        assertTrue(service.isUnambiguousAutoConfirm(0.92, 0.80));
        assertFalse(service.isUnambiguousAutoConfirm(0.90, 0.88));
        assertFalse(service.isUnambiguousAutoConfirm(0.86, 0.85));
        assertFalse(service.isUnambiguousAutoConfirm(0.80, 0.0));
    }

    @Test
    void loadAndPrefilterCandidatesNeverReturnsOtherOwnersPeople() {
        UUID otherOwner = UUID.fromString("22222222-2222-2222-2222-222222222222");
        KnowledgeEntity ownEntity = KnowledgeEntity.builder()
                .id(UUID.randomUUID()).displayName("Igor").type("PERSON").ownerId(ownerId).build();
        KnowledgeEntity foreignEntity = KnowledgeEntity.builder()
                .id(UUID.randomUUID()).displayName("Igor").type("PERSON").ownerId(otherOwner).build();
        EntityMention mention = EntityMention.builder()
                .id(UUID.randomUUID()).label("Igor").filePath("dir://f/a.jpg").build();
        EntityMention ownCandidate = EntityMention.builder().id(UUID.randomUUID()).label("Igor")
                .entity(ownEntity).filePath("dir://f/b.jpg").build();
        EntityMention foreignCandidate = EntityMention.builder().id(UUID.randomUUID()).label("Igor")
                .entity(foreignEntity).filePath("dir://f/c.jpg").build();

        // Repository already owner-scoped; sameOwner is a second guard if a foreign row leaked in.
        when(mentionRepository.findLinkedByEntityTypeAndOwner("PERSON", ownerId))
                .thenReturn(List.of(ownCandidate, foreignCandidate));

        List<EntityMention> result = service.loadAndPrefilterCandidates(mention, "PERSON", ownerId);

        assertEquals(1, result.size());
        assertEquals(ownEntity.getId(), result.get(0).getEntity().getId());
        assertFalse(result.stream().anyMatch(c -> otherOwner.equals(c.getEntity().getOwnerId())));
    }
}


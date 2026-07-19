package com.rag.rag.knowledge.identity;

import com.rag.rag.knowledge.entity.EntityMention;
import com.rag.rag.knowledge.entity.KnowledgeEntity;
import com.rag.rag.knowledge.entity.MentionStatus;
import com.rag.rag.knowledge.entity.IdentityEvidenceSource;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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
    private ChatLanguageModel chatModel;

    private IdentityResolutionService service;

    @BeforeEach
    void setUp() {
        service = new IdentityResolutionService(
                entityRepository,
                aliasRepository,
                mentionRepository,
                suggestionRepository,
                faceEmbeddingRepository,
                factRepository,
                chatModel
        );
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

        when(entityRepository.findFirstByDisplayNameIgnoreCaseAndTypeIgnoreCase("Bartek", "PERSON"))
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

        when(entityRepository.findFirstByDisplayNameIgnoreCaseAndTypeIgnoreCase("Bartek", "PERSON"))
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

        when(entityRepository.findFirstByDisplayNameIgnoreCaseAndTypeIgnoreCase("Figa", "PERSON"))
                .thenReturn(Optional.of(person));
        when(entityRepository.findFirstByDisplayNameIgnoreCaseAndTypeIgnoreCase("Figa", "ANIMAL"))
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
}

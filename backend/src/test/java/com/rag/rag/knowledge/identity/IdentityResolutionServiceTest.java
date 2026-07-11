package com.rag.rag.knowledge.identity;

import com.rag.rag.knowledge.entity.EntityMention;
import com.rag.rag.knowledge.entity.KnowledgeEntity;
import com.rag.rag.knowledge.entity.MentionStatus;
import com.rag.rag.knowledge.repository.EntityAliasRepository;
import com.rag.rag.knowledge.repository.EntityMentionRepository;
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
    private ChatLanguageModel chatModel;

    private IdentityResolutionService service;

    @BeforeEach
    void setUp() {
        service = new IdentityResolutionService(
                entityRepository,
                aliasRepository,
                mentionRepository,
                suggestionRepository,
                chatModel
        );
    }

    @ParameterizedTest
    @CsvSource({
            "Bartek, true",
            "Pati Kowalska, true",
            "mężczyzna w czerwonej koszulce, false",
            "Osoba 1, false",
            "nieznana postać, false"
    })
    void shouldDetectPersonNameLabels(String label, boolean expected) {
        assertEquals(expected, service.looksLikePersonName(label));
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

        when(entityRepository.findFirstByDisplayNameIgnoreCase("Bartek")).thenReturn(Optional.empty());
        when(entityRepository.save(any(KnowledgeEntity.class))).thenReturn(entity);
        when(aliasRepository.save(any())).thenReturn(null);

        service.resolve(mention, "Bartek");

        ArgumentCaptor<EntityMention> captor = ArgumentCaptor.forClass(EntityMention.class);
        verify(mentionRepository).save(captor.capture());
        assertEquals(entity, captor.getValue().getEntity());
        assertEquals(MentionStatus.CONFIRMED, captor.getValue().getStatus());
        verify(mentionRepository, never()).findAll();
    }

    @Test
    void shouldCreateSuggestedEntityForDescriptiveLabelWithoutTag() {
        EntityMention mention = EntityMention.builder()
                .id(UUID.randomUUID())
                .label("mężczyzna w czerwonej koszulce")
                .build();

        KnowledgeEntity savedEntity = KnowledgeEntity.builder()
                .id(UUID.randomUUID())
                .displayName("mężczyzna w czerwonej koszulce")
                .type("PERSON")
                .build();

        when(mentionRepository.findAll()).thenReturn(List.of());
        when(entityRepository.save(any(KnowledgeEntity.class))).thenReturn(savedEntity);
        when(aliasRepository.save(any())).thenReturn(null);

        service.resolve(mention, null);

        ArgumentCaptor<EntityMention> captor = ArgumentCaptor.forClass(EntityMention.class);
        verify(mentionRepository).save(captor.capture());
        assertEquals(MentionStatus.SUGGESTED, captor.getValue().getStatus());
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

        when(mentionRepository.findAll()).thenReturn(List.of());
        when(entityRepository.findFirstByDisplayNameIgnoreCase("Bartek")).thenReturn(Optional.of(existing));

        service.resolve(mention, null);

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

        when(aliasRepository.findFirstByAliasIgnoreCase("mężczyzna")).thenReturn(Optional.empty());
        when(aliasRepository.save(any())).thenReturn(null);

        service.confirmFaceMatch(mention, igor, mention.getLabel());

        ArgumentCaptor<EntityMention> mentionCaptor = ArgumentCaptor.forClass(EntityMention.class);
        verify(mentionRepository).save(mentionCaptor.capture());
        assertEquals(MentionStatus.CONFIRMED, mentionCaptor.getValue().getStatus());
        assertEquals(igor, mentionCaptor.getValue().getEntity());
        verify(aliasRepository).save(any());
    }
}

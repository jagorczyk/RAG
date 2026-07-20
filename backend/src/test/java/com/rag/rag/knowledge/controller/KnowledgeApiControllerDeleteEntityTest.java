package com.rag.rag.knowledge.controller;

import com.rag.rag.auth.security.CurrentUserService;
import com.rag.rag.core.exception.ApiException;
import com.rag.rag.folder.repository.FileRepository;
import com.rag.rag.knowledge.entity.EntityMention;
import com.rag.rag.knowledge.entity.KnowledgeEntity;
import com.rag.rag.knowledge.entity.MentionStatus;
import com.rag.rag.knowledge.face.FaceObservation;
import com.rag.rag.knowledge.face.FaceCropService;
import com.rag.rag.knowledge.face.FaceIdentityService;
import com.rag.rag.knowledge.graph.MentionEvidencePolicy;
import com.rag.rag.knowledge.graph.PersonRelationGraphService;
import com.rag.rag.knowledge.identity.IdentityResolutionService;
import com.rag.rag.knowledge.repository.EntityAliasRepository;
import com.rag.rag.knowledge.repository.EntityMentionRepository;
import com.rag.rag.knowledge.repository.FaceEmbeddingRepository;
import com.rag.rag.knowledge.repository.FaceObservationRepository;
import com.rag.rag.knowledge.repository.FactRepository;
import com.rag.rag.knowledge.repository.IdentitySuggestionRepository;
import com.rag.rag.knowledge.repository.KnowledgeEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeApiControllerDeleteEntityTest {
    private KnowledgeEntityRepository entities;
    private EntityMentionRepository mentions;
    private EntityAliasRepository aliases;
    private IdentitySuggestionRepository suggestions;
    private FaceEmbeddingRepository faceEmbeddings;
    private FaceObservationRepository faceObservations;
    private FactRepository facts;
    private FileRepository files;
    private CurrentUserService currentUserService;
    private KnowledgeApiController controller;
    private final UUID ownerId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @BeforeEach
    void setUp() {
        entities = mock(KnowledgeEntityRepository.class);
        mentions = mock(EntityMentionRepository.class);
        aliases = mock(EntityAliasRepository.class);
        suggestions = mock(IdentitySuggestionRepository.class);
        faceEmbeddings = mock(FaceEmbeddingRepository.class);
        faceObservations = mock(FaceObservationRepository.class);
        facts = mock(FactRepository.class);
        files = mock(FileRepository.class);
        currentUserService = mock(CurrentUserService.class);
        when(currentUserService.requireUserId()).thenReturn(ownerId);
        IdentityResolutionService identities = mock(IdentityResolutionService.class);
        controller = new KnowledgeApiController(suggestions, mentions, entities, aliases, identities, files,
                faceEmbeddings, mock(FaceIdentityService.class), mock(FaceCropService.class), faceObservations,
                facts, mock(PersonRelationGraphService.class), mock(MentionEvidencePolicy.class), currentUserService);
    }

    @Test
    void deletesOnlyRecognitionDataAndKeepsFiles() {
        UUID entityId = UUID.randomUUID();
        UUID mentionId = UUID.randomUUID();
        KnowledgeEntity person = KnowledgeEntity.builder().id(entityId).displayName("Anna Kowalska").type("PERSON")
                .ownerId(ownerId).build();
        EntityMention mention = EntityMention.builder().id(mentionId).entity(person).filePath("folder/anna.jpg").label("Anna").build();
        when(entities.findByIdAndOwnerId(entityId, ownerId)).thenReturn(Optional.of(person));
        when(mentions.findByEntityId(entityId)).thenReturn(List.of(mention));

        assertEquals(HttpStatus.NO_CONTENT, controller.deleteEntityRecognition(entityId).getStatusCode());

        verify(suggestions).deleteByMentionIds(List.of(mentionId));
        verify(facts).deleteByMentionIds(List.of(mentionId));
        verify(faceObservations).deleteByMentionIds(List.of(mentionId));
        verify(faceEmbeddings).deleteByMentionIdIn(List.of(mentionId));
        verify(faceEmbeddings).deleteByEntityId(entityId);
        verify(mentions).deleteByEntityId(entityId);
        verify(aliases).deleteByEntityId(entityId);
        verify(entities).delete(person);
        verify(files, never()).delete(any());
    }

    @Test
    void deletesPersonWithoutMentions() {
        UUID entityId = UUID.randomUUID();
        KnowledgeEntity person = KnowledgeEntity.builder().id(entityId).displayName("Anna Kowalska").type("PERSON")
                .ownerId(ownerId).build();
        when(entities.findByIdAndOwnerId(entityId, ownerId)).thenReturn(Optional.of(person));
        when(mentions.findByEntityId(entityId)).thenReturn(List.of());

        assertEquals(HttpStatus.NO_CONTENT, controller.deleteEntityRecognition(entityId).getStatusCode());
        verify(suggestions, never()).deleteByMentionIds(any());
        verify(facts, never()).deleteByMentionIds(any());
        verify(faceObservations, never()).deleteByMentionIds(any());
        verify(faceEmbeddings, never()).deleteByMentionIdIn(any());
        verify(entities).delete(person);
    }

    @Test
    void returnsNotFoundForUnknownPerson() {
        UUID entityId = UUID.randomUUID();
        when(entities.findByIdAndOwnerId(entityId, ownerId)).thenReturn(Optional.empty());

        ApiException error = assertThrows(ApiException.class, () -> controller.deleteEntityRecognition(entityId));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatus());
        verify(mentions, never()).findByEntityId(eq(entityId));
    }

    @Test
    void returnsPendingObservationBboxAsFaceEvidence() {
        UUID mentionId = UUID.randomUUID();
        EntityMention mention = EntityMention.builder()
                .id(mentionId)
                .filePath("folder/group.jpg")
                .label("person 1")
                .entityType("PERSON")
                .status(MentionStatus.PENDING)
                .build();
        FaceObservation observation = FaceObservation.builder()
                .mention(mention)
                .filePath(mention.getFilePath())
                .bbox(new float[]{10f, 20f, 30f, 40f})
                .embedding(new float[]{1f})
                .status("PENDING")
                .build();
        when(files.findByPathAndOwnerId(mention.getFilePath(), ownerId)).thenReturn(Optional.of(
                com.rag.rag.folder.entity.FileEntity.builder().path(mention.getFilePath()).fileType("image/jpeg").build()));
        when(mentions.findByFilePath(mention.getFilePath())).thenReturn(List.of(mention));
        when(files.findByPath(mention.getFilePath())).thenReturn(Optional.empty());
        when(faceEmbeddings.findFirstByMention_Id(mentionId)).thenReturn(Optional.empty());
        when(faceObservations.findFirstByMentionIdAndStatus(mentionId, "PENDING"))
                .thenReturn(Optional.of(observation));

        var result = controller.getMentionsForFile(mention.getFilePath()).getBody();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("FACE", result.get(0).bboxSource());
        assertEquals(List.of(10f, 20f, 30f, 40f), result.get(0).bbox());
    }
}

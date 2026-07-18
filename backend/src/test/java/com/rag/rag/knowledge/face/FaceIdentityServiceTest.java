package com.rag.rag.knowledge.face;

import com.rag.rag.knowledge.entity.KnowledgeEntity;
import com.rag.rag.knowledge.entity.EntityMention;
import com.rag.rag.knowledge.entity.MentionStatus;
import com.rag.rag.knowledge.graph.MentionEvidencePolicy;
import com.rag.rag.knowledge.identity.IdentityResolutionService;
import com.rag.rag.knowledge.repository.EntityMentionRepository;
import com.rag.rag.knowledge.repository.FaceEmbeddingRepository;
import com.rag.rag.knowledge.repository.FaceObservationRepository;
import com.rag.rag.knowledge.repository.FactRepository;
import com.rag.rag.knowledge.repository.IdentitySuggestionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FaceIdentityServiceTest {

    private FaceIdentityService service;

    @BeforeEach
    void setUp() {
        service = new FaceIdentityService(null, null, null, null, null);
        ReflectionTestUtils.setField(service, "suggestionThreshold", 0.50);
        ReflectionTestUtils.setField(service, "minMargin", 0.08);
    }

    @Test
    void shouldReturnOneForIdenticalNormalizedEmbeddings() {
        float[] vector = FaceIdentityService.normalizeEmbedding(new float[] {1f, 0f, 0f});
        assertEquals(1.0, FaceIdentityService.cosineSimilarity(vector, vector), 0.0001);
    }

    @Test
    void shouldReturnZeroForOrthogonalEmbeddings() {
        float[] a = FaceIdentityService.normalizeEmbedding(new float[] {1f, 0f});
        float[] b = FaceIdentityService.normalizeEmbedding(new float[] {0f, 1f});
        assertEquals(0.0, FaceIdentityService.cosineSimilarity(a, b), 0.0001);
    }

    @Test
    void shouldPickBestEntityUsingTopEmbeddingAverage() {
        float[] query = FaceIdentityService.normalizeEmbedding(new float[] {1f, 0f, 0f});

        KnowledgeEntity entityA = KnowledgeEntity.builder().id(UUID.randomUUID()).displayName("A").build();
        KnowledgeEntity entityB = KnowledgeEntity.builder().id(UUID.randomUUID()).displayName("B").build();

        List<FaceEmbedding> stored = List.of(
                FaceEmbedding.builder().entity(entityA).embedding(new float[] {0.80f, 0.60f, 0f}).build(),
                FaceEmbedding.builder().entity(entityA).embedding(new float[] {0.5f, 0.5f, 0f}).build(),
                FaceEmbedding.builder().entity(entityB).embedding(new float[] {0.99f, 0.01f, 0f}).build()
        );

        Optional<FaceIdentityService.EntityMatch> match = service.rankEntityMatches(stored, query, 0.50);

        assertTrue(match.isPresent());
        assertEquals(entityB.getId(), match.get().entity().getId());
        assertTrue(match.get().score() > 0.99);
    }

    @Test
    void shouldRejectAmbiguousMatchWhenMarginIsTooSmall() {
        float[] query = FaceIdentityService.normalizeEmbedding(new float[] {1f, 0f, 0f});

        KnowledgeEntity entityA = KnowledgeEntity.builder().id(UUID.randomUUID()).displayName("A").build();
        KnowledgeEntity entityB = KnowledgeEntity.builder().id(UUID.randomUUID()).displayName("B").build();

        List<FaceEmbedding> stored = List.of(
                FaceEmbedding.builder().entity(entityA).embedding(new float[] {0.92f, 0.08f, 0f}).build(),
                FaceEmbedding.builder().entity(entityB).embedding(new float[] {0.90f, 0.10f, 0f}).build()
        );

        Optional<FaceIdentityService.EntityMatch> match = service.rankEntityMatches(stored, query, 0.50);

        assertFalse(match.isPresent());
    }

    @Test
    void shouldNotPairFacesSequentiallyWhenVisionMentionsHaveNoBoundingBoxes() {
        EntityMention mention = EntityMention.builder().id(UUID.randomUUID()).label("person 1")
                .entityType("PERSON").build();
        DetectedFaceDto face = new DetectedFaceDto(List.of(1f, 0f), List.of(10f, 10f, 30f, 30f),
                0.9, 100, 100);

        @SuppressWarnings("unchecked")
        List<FaceIdentityService.FaceAssignment> assignments = ReflectionTestUtils.invokeMethod(
                service, "assignMentionsToFaces", List.of(face), List.of(mention));

        assertEquals(1, assignments.size());
        assertEquals(null, assignments.get(0).mention());
    }

    @Test
    void shouldReuseMentionFromPreviousFaceBboxOnRetry() {
        EntityMention previousMention = EntityMention.builder().id(UUID.randomUUID())
                .label("Nieznana osoba").entityType("PERSON").status(MentionStatus.PENDING).build();
        EntityMention unrelatedVisionMention = EntityMention.builder().id(UUID.randomUUID())
                .label("person 1").entityType("PERSON").bbox("[0,0,20,90]").build();
        DetectedFaceDto face = new DetectedFaceDto(List.of(1f, 0f), List.of(40f, 10f, 60f, 30f),
                0.9, 100, 100);

        @SuppressWarnings("unchecked")
        List<FaceIdentityService.FaceAssignment> assignments = ReflectionTestUtils.invokeMethod(
                service,
                "assignMentionsToFaces",
                List.of(face),
                List.of(unrelatedVisionMention),
                List.of(new FaceIdentityService.ExistingFaceLink(previousMention, new float[]{40f, 10f, 60f, 30f}))
        );

        assertEquals(1, assignments.size());
        assertEquals(previousMention.getId(), assignments.get(0).mention().getId());
    }

    @Test
    void shouldDeleteOnlyPendingEvidenceFreeOrphans() {
        FaceEmbeddingRepository embeddings = mock(FaceEmbeddingRepository.class);
        FaceObservationRepository observations = mock(FaceObservationRepository.class);
        EntityMentionRepository mentions = mock(EntityMentionRepository.class);
        IdentitySuggestionRepository suggestions = mock(IdentitySuggestionRepository.class);
        FactRepository facts = mock(FactRepository.class);
        FaceIdentityService cleanupService = new FaceIdentityService(
                null, embeddings, observations, mentions, mock(IdentityResolutionService.class),
                new MentionEvidencePolicy(), suggestions, facts);

        UUID orphanId = UUID.randomUUID();
        EntityMention orphan = EntityMention.builder().id(orphanId).filePath("photo.jpg")
                .label("Nieznana osoba").entityType("PERSON").status(MentionStatus.PENDING).build();
        EntityMention visionMention = EntityMention.builder().id(UUID.randomUUID()).filePath("photo.jpg")
                .label("person 1").entityType("PERSON").status(MentionStatus.PENDING)
                .bbox("[1,2,3,4]").build();
        when(mentions.findByFilePath("photo.jpg")).thenReturn(List.of(orphan, visionMention));
        when(facts.existsByMentionOrTargetMentionId(orphanId)).thenReturn(false);

        ReflectionTestUtils.invokeMethod(cleanupService, "removeEvidenceFreeOrphans", "photo.jpg", java.util.Set.of());

        verify(suggestions).deleteByMentionIds(List.of(orphanId));
        verify(observations).deleteByMentionIds(List.of(orphanId));
        verify(embeddings).deleteByMentionIdIn(List.of(orphanId));
        verify(mentions).deleteAllByIdInBatch(List.of(orphanId));
    }

    @Test
    void shouldKeepFreshUploadMentionsInTheCallerPersistenceContext() throws Exception {
        Transactional transaction = FaceIdentityService.class.getMethod(
                "processDetectedFaces", List.class, String.class, String.class, List.class)
                .getAnnotation(Transactional.class);
        Modifying deleteOperation = FaceEmbeddingRepository.class
                .getMethod("deleteByFilePath", String.class)
                .getAnnotation(Modifying.class);

        assertEquals(Propagation.REQUIRED, transaction.propagation());
        assertFalse(deleteOperation.clearAutomatically());
    }
}

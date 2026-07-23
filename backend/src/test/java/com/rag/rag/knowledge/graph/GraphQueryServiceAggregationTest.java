package com.rag.rag.knowledge.graph;

import com.rag.rag.auth.security.CurrentUserService;
import com.rag.rag.folder.entity.FileEntity;
import com.rag.rag.folder.repository.FileRepository;
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
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Aggregation, relations/facts, co-occurrence and file-scope filtering via real {@link GraphQueryService}.
 */
@ExtendWith(MockitoExtension.class)
class GraphQueryServiceAggregationTest {

    @Mock KnowledgeEntityRepository entityRepository;
    @Mock EntityMentionRepository mentionRepository;
    @Mock FileRepository fileRepository;
    @Mock FactRepository factRepository;
    @Mock MentionEvidencePolicy mentionEvidencePolicy;
    @Mock IdentityResolutionService identityResolutionService;
    @Mock CurrentUserService currentUserService;
    @InjectMocks GraphQueryService service;

    private final UUID ownerId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private KnowledgeEntity igor;
    private KnowledgeEntity anna;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "minFactConfidence", 0.75);
        igor = KnowledgeEntity.builder().id(UUID.randomUUID()).displayName("Igor").type("PERSON").ownerId(ownerId).build();
        anna = KnowledgeEntity.builder().id(UUID.randomUUID()).displayName("Anna").type("PERSON").ownerId(ownerId).build();
        lenient().when(currentUserService.findUserId()).thenReturn(Optional.of(ownerId));
        lenient().when(entityRepository.findAllByOwnerId(ownerId)).thenReturn(List.of(igor, anna));
        lenient().when(entityRepository.findFirstByDisplayNameIgnoreCaseAndOwnerId("Igor", ownerId))
                .thenReturn(Optional.of(igor));
        lenient().when(entityRepository.findFirstByDisplayNameIgnoreCaseAndOwnerId("Anna", ownerId))
                .thenReturn(Optional.of(anna));
        lenient().when(mentionEvidencePolicy.isCertain(any(EntityMention.class))).thenReturn(true);
        lenient().when(identityResolutionService.isGenericPersonLabel(anyString())).thenReturn(false);
        lenient().when(fileRepository.findByPath(anyString())).thenAnswer(inv -> Optional.of(
                FileEntity.builder().path(inv.getArgument(0)).fileType("image/jpeg").ownerId(ownerId).build()));
        lenient().when(fileRepository.findByPathAndOwnerId(anyString(), any(UUID.class))).thenAnswer(inv -> Optional.of(
                FileEntity.builder().path(inv.getArgument(0)).fileType("image/jpeg").ownerId(ownerId).build()));
    }

    @Test
    void buildEvidenceAnyAggregatesPathsAcrossEntityMentions() {
        EntityMention m1 = confirmed(igor, "dir://a.jpg");
        EntityMention m2 = confirmed(igor, "dir://b.jpg");
        when(mentionRepository.findByEntityId(igor.getId())).thenReturn(List.of(m1, m2));
        when(mentionRepository.findByFilePath("dir://a.jpg")).thenReturn(List.of(m1));
        when(mentionRepository.findByFilePath("dir://b.jpg")).thenReturn(List.of(m2));
        when(factRepository.findByFilePath(anyString())).thenReturn(List.of());

        GraphEvidenceResult evidence = service.buildEvidence(
                List.of("Igor"), List.of(), EntityMatchMode.ANY);

        assertTrue(evidence.hasEvidence());
        assertEquals(2, evidence.certainPaths().size());
        assertTrue(evidence.certainPaths().containsAll(List.of("dir://a.jpg", "dir://b.jpg")));
        assertTrue(evidence.context().contains("Igor"));
        assertTrue(evidence.context().contains("=== Zdjęcie 1 ==="));
        assertTrue(evidence.context().contains("=== Zdjęcie 2 ==="));
        assertFalse(evidence.context().contains("dir://a.jpg"));
        assertFalse(evidence.context().contains("structured_vision="));
    }

    @Test
    void buildEvidenceDoesNotDropPhotosAfterTheFormerFiveFileLimit() {
        List<EntityMention> mentions = java.util.stream.IntStream.rangeClosed(1, 7)
                .mapToObj(index -> confirmed(igor, "dir://photo-" + index + ".jpg"))
                .toList();
        when(mentionRepository.findByEntityId(igor.getId())).thenReturn(mentions);
        for (EntityMention mention : mentions) {
            when(mentionRepository.findByFilePath(mention.getFilePath())).thenReturn(List.of(mention));
            when(factRepository.findByFilePath(mention.getFilePath())).thenReturn(List.of());
        }

        GraphEvidenceResult evidence = service.buildEvidence(
                List.of("Igor"), List.of(), EntityMatchMode.ANY);

        assertEquals(7, evidence.certainPaths().size());
        assertEquals(7, evidence.photos().size());
        assertTrue(evidence.context().contains("=== Zdjęcie 7 ==="));
        assertFalse(evidence.context().contains("pominięto"));
    }

    @Test
    void unscopedGraphPlanLoadsAllOwnedPhotosWithCertainPeople() {
        EntityMention first = confirmed(igor, "dir://one.jpg");
        EntityMention second = confirmed(anna, "dir://two.jpg");
        List<FileEntity> files = List.of(
                FileEntity.builder().path(first.getFilePath()).fileType("image/jpeg").ownerId(ownerId).build(),
                FileEntity.builder().path(second.getFilePath()).fileType("image/jpeg").ownerId(ownerId).build());
        when(fileRepository.findAllByOwnerId(ownerId)).thenReturn(files);
        when(mentionRepository.findByFilePathIn(List.of(first.getFilePath(), second.getFilePath())))
                .thenReturn(List.of(first, second));
        when(fileRepository.findAllByPathInAndOwnerId(any(), any())).thenReturn(files);
        when(factRepository.findByFilePathIn(any())).thenReturn(List.of());

        var plan = new com.rag.rag.chat.service.QueryPlan(
                "Kto pojawia się najczęściej?", List.of(), List.of(), "osoby w bibliotece",
                "osoby", false, false, com.rag.rag.chat.service.QueryPlan.RetrievalMode.GRAPH,
                EntityMatchMode.ANY, "");
        GraphEvidenceResult evidence = service.buildEvidence(plan);

        assertEquals(List.of("dir://one.jpg", "dir://two.jpg"), evidence.certainPaths());
        assertTrue(evidence.context().contains("Podsumowanie grafu"));
        assertTrue(evidence.context().contains("Igor występuje na jednym pewnym zdjęciu"));
        assertTrue(evidence.context().contains("Anna występuje na jednym pewnym zdjęciu"));
    }

    @Test
    void buildEvidenceFiltersPathsByFileScope() {
        EntityMention shared = confirmed(igor, "dir://shared.jpg");
        EntityMention alone = confirmed(igor, "dir://alone.jpg");
        when(mentionRepository.findByEntityId(igor.getId())).thenReturn(List.of(shared, alone));
        when(mentionRepository.findByEntityId(anna.getId())).thenReturn(List.of(confirmed(anna, "dir://shared.jpg")));
        when(mentionRepository.findByFilePath("dir://shared.jpg")).thenReturn(List.of(shared));
        when(factRepository.findByFilePath(anyString())).thenReturn(List.of());

        GraphEvidenceResult evidence = service.buildEvidence(
                List.of("Igor", "Anna"),
                List.of("dir://shared.jpg"),
                EntityMatchMode.ALL_SAME_FILE);

        assertEquals(List.of("dir://shared.jpg"), evidence.certainPaths());
        assertFalse(evidence.certainPaths().contains("dir://alone.jpg"));
    }

    @Test
    void buildEvidenceIncludesRelationFactsInContext() {
        EntityMention igorMention = confirmed(igor, "dir://pair.jpg");
        EntityMention annaMention = confirmed(anna, "dir://pair.jpg");
        Fact relation = Fact.builder()
                .id(UUID.randomUUID())
                .mention(igorMention)
                .targetMention(annaMention)
                .action("LEFT_OF")
                .object("Anna")
                .filePath("dir://pair.jpg")
                .confidence(new BigDecimal("0.900"))
                .build();

        when(mentionRepository.findByEntityId(igor.getId())).thenReturn(List.of(igorMention));
        when(mentionRepository.findByFilePath("dir://pair.jpg")).thenReturn(List.of(igorMention, annaMention));
        when(factRepository.findByFilePath("dir://pair.jpg")).thenReturn(List.of(relation));
        when(mentionEvidencePolicy.evidenceConfidence(igorMention)).thenReturn(new BigDecimal("0.900"));

        GraphEvidenceResult evidence = service.buildEvidence(
                List.of("Igor"), List.of("dir://pair.jpg"), EntityMatchMode.ANY);

        assertTrue(evidence.hasEvidence());
        assertTrue(evidence.certainPaths().contains("dir://pair.jpg"));
        // Natural PL context — no machine entity=/predicate= dump, no file paths.
        assertTrue(evidence.context().contains("Igor"));
        assertTrue(evidence.context().contains("Anna"));
        assertTrue(evidence.context().contains("z lewej od") || evidence.context().contains("LEFT_OF"));
        assertTrue(evidence.context().contains("=== Zdjęcie 1 ==="));
        assertTrue(evidence.context().contains("=== Claimy grafu ===")
                || evidence.context().contains("z lewej od")
                || evidence.context().contains("LEFT_OF"));
        assertFalse(evidence.context().contains("dir://pair.jpg"));
    }

    @Test
    void getCertainFactsForFileDropsLowConfidenceFacts() {
        EntityMention mention = confirmed(igor, "dir://f.jpg");
        Fact high = Fact.builder().id(UUID.randomUUID()).mention(mention).action("eats")
                .object("soup").filePath("dir://f.jpg").confidence(new BigDecimal("0.900")).build();
        Fact low = Fact.builder().id(UUID.randomUUID()).mention(mention).action("maybe")
                .object("x").filePath("dir://f.jpg").confidence(new BigDecimal("0.400")).build();
        when(factRepository.findByFilePath("dir://f.jpg")).thenReturn(List.of(high, low));

        List<Fact> facts = service.getCertainFactsForFile("dir://f.jpg");

        assertEquals(1, facts.size());
        assertEquals("eats", facts.get(0).getAction());
    }

    @Test
    void entityConfidenceForFileUsesCertainMentionsOnly() {
        EntityMention confirmed = confirmed(igor, "dir://c.jpg");
        EntityMention suggested = EntityMention.builder()
                .id(UUID.randomUUID()).entity(igor).filePath("dir://c.jpg")
                .label("Igor").confidence(new BigDecimal("0.500")).status(MentionStatus.SUGGESTED).build();
        when(mentionRepository.findByFilePath("dir://c.jpg")).thenReturn(List.of(confirmed, suggested));
        when(mentionEvidencePolicy.isCertain(confirmed)).thenReturn(true);
        when(mentionEvidencePolicy.isCertain(suggested)).thenReturn(false);
        when(mentionEvidencePolicy.evidenceConfidence(confirmed)).thenReturn(new BigDecimal("0.900"));

        assertEquals(0, new BigDecimal("0.900").compareTo(
                service.entityConfidenceForFile(List.of("Igor"), "dir://c.jpg")));
        assertEquals(0, BigDecimal.ZERO.compareTo(
                service.entityConfidenceForFile(List.of("Anna"), "dir://c.jpg")));
    }

    @Test
    void validateFilePathsKeepsOnlyOwnedPaths() {
        when(fileRepository.findByPathAndOwnerId("dir://mine.jpg", ownerId))
                .thenReturn(Optional.of(FileEntity.builder().path("dir://mine.jpg").ownerId(ownerId).build()));
        when(fileRepository.findByPathAndOwnerId("dir://other.jpg", ownerId))
                .thenReturn(Optional.empty());

        assertEquals(List.of("dir://mine.jpg"),
                service.validateFilePaths(List.of("dir://mine.jpg", "dir://other.jpg", "")));
    }

    private EntityMention confirmed(KnowledgeEntity owner, String path) {
        return EntityMention.builder()
                .id(UUID.randomUUID())
                .entity(owner)
                .filePath(path)
                .label(owner.getDisplayName())
                .confidence(new BigDecimal("0.900"))
                .status(MentionStatus.CONFIRMED)
                .build();
    }
}

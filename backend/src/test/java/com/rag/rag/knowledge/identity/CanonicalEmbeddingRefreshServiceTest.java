package com.rag.rag.knowledge.identity;

import com.rag.rag.folder.entity.FileEntity;
import com.rag.rag.folder.repository.FileRepository;
import com.rag.rag.knowledge.entity.EntityMention;
import com.rag.rag.knowledge.entity.KnowledgeEntity;
import com.rag.rag.knowledge.entity.MentionStatus;
import com.rag.rag.knowledge.fact.Fact;
import com.rag.rag.knowledge.repository.EntityMentionRepository;
import com.rag.rag.knowledge.repository.FactRepository;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Drives real {@link CanonicalEmbeddingRefreshService} replace-by-path path after identity rename.
 */
@ExtendWith(MockitoExtension.class)
class CanonicalEmbeddingRefreshServiceTest {

    private static final String PATH_A = "dir://photos/party.jpg";
    private static final String PATH_B = "dir://other/alone.jpg";

    @Mock private EntityMentionRepository mentionRepository;
    @Mock private FactRepository factRepository;
    @Mock private FileRepository fileRepository;
    @Mock private EmbeddingStoreIngestor embeddingStoreIngestor;
    @Mock private JdbcTemplate jdbcTemplate;

    private CanonicalEmbeddingRefreshService service;
    private KnowledgeEntity person;
    private UUID entityId;

    @BeforeEach
    void setUp() {
        service = new CanonicalEmbeddingRefreshService(
                mentionRepository, factRepository, fileRepository, embeddingStoreIngestor, jdbcTemplate);
        entityId = UUID.randomUUID();
        person = KnowledgeEntity.builder().id(entityId).displayName("Bartek").type("PERSON").build();
    }

    @Test
    void refreshForEntityReplacesEmbeddingsWithTextContainingNewName() {
        EntityMention mention = EntityMention.builder()
                .id(UUID.randomUUID())
                .entity(person)
                .filePath(PATH_A)
                .label("Bartek")
                .entityType("PERSON")
                .confidence(BigDecimal.ONE)
                .status(MentionStatus.CONFIRMED)
                .visualCues("[\"czerwona koszulka\"]")
                .build();
        FileEntity file = FileEntity.builder()
                .path(PATH_A)
                .fileName("party.jpg")
                .fileType("image/jpeg")
                .imageScene("kuchnia")
                .imageSummary("osoba je zupę")
                .structuredVisionContext("{\"entities\":[{\"label\":\"person 1\"}]}")
                .build();
        Fact fact = Fact.builder()
                .id(UUID.randomUUID())
                .mention(mention)
                .action("je")
                .object("zupa")
                .filePath(PATH_A)
                .confidence(new BigDecimal("0.900"))
                .build();

        when(mentionRepository.findByEntityId(entityId)).thenReturn(List.of(mention));
        when(fileRepository.findByPath(PATH_A)).thenReturn(Optional.of(file));
        when(mentionRepository.findByFilePath(PATH_A)).thenReturn(List.of(mention));
        when(factRepository.findByFilePath(PATH_A)).thenReturn(List.of(fact));

        service.refreshForEntity(entityId);

        verify(jdbcTemplate).update(
                eq("DELETE FROM embeddings WHERE metadata->>'path' = ?"), eq(PATH_A));
        ArgumentCaptor<Document> docCaptor = ArgumentCaptor.forClass(Document.class);
        verify(embeddingStoreIngestor).ingest(docCaptor.capture());
        String text = docCaptor.getValue().text();
        assertTrue(text.contains("Bartek"), "canonical embed text must include new display name");
        assertTrue(text.contains("\"type\"") && text.contains("image_knowledge"),
                "embedding must be structured image_knowledge JSON");
        assertTrue(text.contains("\"participants\""));
        assertTrue(text.contains("je") || text.contains("zupa"));
        assertEquals(PATH_A, docCaptor.getValue().metadata().getString("path"));
        assertEquals("party.jpg", docCaptor.getValue().metadata().getString("filename"));
    }

    @Test
    void refreshForEntityDoesNotTouchUnrelatedPaths() {
        EntityMention onlyA = EntityMention.builder()
                .id(UUID.randomUUID())
                .entity(person)
                .filePath(PATH_A)
                .label("Bartek")
                .entityType("PERSON")
                .confidence(BigDecimal.ONE)
                .status(MentionStatus.CONFIRMED)
                .build();
        FileEntity fileA = FileEntity.builder()
                .path(PATH_A).fileName("party.jpg").fileType("image/jpeg").build();

        when(mentionRepository.findByEntityId(entityId)).thenReturn(List.of(onlyA));
        when(fileRepository.findByPath(PATH_A)).thenReturn(Optional.of(fileA));
        when(mentionRepository.findByFilePath(PATH_A)).thenReturn(List.of(onlyA));
        when(factRepository.findByFilePath(PATH_A)).thenReturn(List.of());

        service.refreshForEntity(entityId);

        verify(jdbcTemplate).update(
                eq("DELETE FROM embeddings WHERE metadata->>'path' = ?"), eq(PATH_A));
        verify(fileRepository, never()).findByPath(PATH_B);
        verify(jdbcTemplate, never()).update(
                eq("DELETE FROM embeddings WHERE metadata->>'path' = ?"), eq(PATH_B));
    }

    @Test
    void pathsForEntityCollectsOnlyLinkedFiles() {
        EntityMention a = EntityMention.builder().id(UUID.randomUUID()).filePath(PATH_A).entity(person).build();
        EntityMention b = EntityMention.builder().id(UUID.randomUUID()).filePath(PATH_B).entity(person).build();
        when(mentionRepository.findByEntityId(entityId)).thenReturn(List.of(a, b));

        assertEquals(Set.of(PATH_A, PATH_B), service.pathsForEntity(entityId));
    }

    @Test
    void buildCanonicalTextUsesEntityDisplayNameNotPlaceholderAlone() {
        EntityMention mention = EntityMention.builder()
                .entity(person)
                .label("person 1")
                .entityType("PERSON")
                .status(MentionStatus.CONFIRMED)
                .build();
        FileEntity file = FileEntity.builder()
                .fileName("x.jpg")
                .imageScene("park")
                .build();

        String text = service.buildCanonicalText(file, List.of(mention), List.of());

        assertTrue(text.contains("Bartek"));
        assertTrue(text.contains("image_knowledge"));
        assertFalse(text.contains("\"name\" : \"person 1\"") || text.contains("\"name\":\"person 1\""));
    }

    @Test
    void refreshPathSkipsWhenNoFileRow() {
        when(fileRepository.findByPath(PATH_A)).thenReturn(Optional.empty());

        service.refreshPaths(List.of(PATH_A));

        verify(embeddingStoreIngestor, never()).ingest(any(Document.class));
        verify(jdbcTemplate, never()).update(any(String.class), any(Object.class));
    }
}

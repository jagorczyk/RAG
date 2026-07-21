package com.rag.rag.knowledge.identity;

import com.rag.rag.folder.entity.FileEntity;
import com.rag.rag.folder.repository.FileRepository;
import com.rag.rag.knowledge.entity.EntityMention;
import com.rag.rag.knowledge.fact.Fact;
import com.rag.rag.knowledge.graph.ImageEmbeddingDocumentBuilder;
import com.rag.rag.knowledge.repository.EntityMentionRepository;
import com.rag.rag.knowledge.repository.FactRepository;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Rebuilds document (text) embeddings for image paths after person rename/identity assign.
 * Does not re-run vision and does not touch face embeddings.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CanonicalEmbeddingRefreshService {

    private static final String DELETE_EMBEDDINGS_SQL =
            "DELETE FROM embeddings WHERE metadata->>'path' = ?";

    private final EntityMentionRepository mentionRepository;
    private final FactRepository factRepository;
    private final FileRepository fileRepository;
    private final EmbeddingStoreIngestor embeddingStoreIngestor;
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public void refreshForEntity(UUID entityId) {
        if (entityId == null) {
            return;
        }
        refreshPaths(pathsForEntity(entityId));
    }

    @Transactional
    public void refreshPaths(Collection<String> paths) {
        if (paths == null || paths.isEmpty()) {
            return;
        }
        for (String path : paths) {
            if (path == null || path.isBlank()) {
                continue;
            }
            refreshPath(path.trim());
        }
    }

    Set<String> pathsForEntity(UUID entityId) {
        if (entityId == null) {
            return Set.of();
        }
        Set<String> paths = new LinkedHashSet<>();
        for (EntityMention mention : mentionRepository.findByEntityId(entityId)) {
            if (mention.getFilePath() != null && !mention.getFilePath().isBlank()) {
                paths.add(mention.getFilePath().trim());
            }
        }
        return paths;
    }

    void refreshPath(String path) {
        FileEntity file = fileRepository.findByPath(path).orElse(null);
        if (file == null) {
            log.debug("Skip embedding refresh — no file row for {}", path);
            return;
        }
        if (!isImageFile(file)) {
            log.debug("Skip embedding refresh — not an image path {}", path);
            return;
        }

        List<EntityMention> mentions = mentionRepository.findByFilePath(path);
        List<Fact> facts = factRepository.findByFilePath(path);
        String text = buildCanonicalText(file, mentions, facts);
        if (text == null || text.isBlank()) {
            log.debug("Skip embedding refresh — empty canonical text for {}", path);
            return;
        }

        Document document = Document.from(text);
        document.metadata().put("path", path);
        String fileName = file.getFileName() != null ? file.getFileName() : fileNameFromPath(path);
        document.metadata().put("filename", fileName);
        document.metadata().put("source_type", "IMAGE");
        document.metadata().put("document_id", folderIdFromPath(path));
        if (file.getOwnerId() != null) {
            document.metadata().put("owner_id", file.getOwnerId().toString());
        }

        jdbcTemplate.update(DELETE_EMBEDDINGS_SQL, path);
        embeddingStoreIngestor.ingest(document);
        log.info("Refreshed structured JSON embeddings for path {} ({} chars)", path, text.length());
    }

    /**
     * Structured JSON document for hybrid/vector recall. Package-visible for unit tests.
     */
    String buildCanonicalText(FileEntity file, List<EntityMention> mentions, List<Fact> facts) {
        return ImageEmbeddingDocumentBuilder.build(file, mentions, facts);
    }

    private static boolean isImageFile(FileEntity file) {
        if (file.getFileType() != null && file.getFileType().toLowerCase(Locale.ROOT).startsWith("image/")) {
            return true;
        }
        String name = file.getFileName() != null ? file.getFileName() : file.getPath();
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")
                || lower.endsWith(".webp") || lower.endsWith(".gif");
    }

    private static String fileNameFromPath(String path) {
        if (path == null) {
            return "";
        }
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private static String folderIdFromPath(String path) {
        if (path == null || !path.startsWith("dir://")) {
            return "";
        }
        int start = "dir://".length();
        int slash = path.indexOf('/', start);
        return slash > start ? path.substring(start, slash) : "";
    }
}

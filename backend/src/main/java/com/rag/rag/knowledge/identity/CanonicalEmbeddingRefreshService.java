package com.rag.rag.knowledge.identity;

import com.rag.rag.folder.entity.FileEntity;
import com.rag.rag.folder.repository.FileRepository;
import com.rag.rag.knowledge.entity.EntityMention;
import com.rag.rag.knowledge.entity.KnowledgeEntity;
import com.rag.rag.knowledge.fact.Fact;
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

    /**
     * Re-embeds every image path that still has a mention linked to this entity.
     */
    @Transactional
    public void refreshForEntity(UUID entityId) {
        if (entityId == null) {
            return;
        }
        Set<String> paths = pathsForEntity(entityId);
        refreshPaths(paths);
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

    /**
     * Paths currently linked to the entity (for tests and callers).
     */
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

        jdbcTemplate.update(DELETE_EMBEDDINGS_SQL, path);
        embeddingStoreIngestor.ingest(document);
        log.info("Refreshed document embeddings for path {} after identity rename", path);
    }

    /**
     * Builds embeddable text from graph labels (current names) + stored vision context.
     * Package-visible for unit tests. No vision LLM call.
     */
    String buildCanonicalText(FileEntity file, List<EntityMention> mentions, List<Fact> facts) {
        StringBuilder canonical = new StringBuilder();
        if (file != null) {
            if (file.getImageScene() != null && !file.getImageScene().isBlank()) {
                canonical.append("Scena: ").append(file.getImageScene()).append(". ");
            }
            if (file.getImageSummary() != null && !file.getImageSummary().isBlank()) {
                canonical.append("Kontekst sceny: ").append(file.getImageSummary()).append(". ");
            }
            String fileName = file.getFileName() != null ? file.getFileName() : fileNameFromPath(file.getPath());
            if (fileName != null && !fileName.isBlank()) {
                canonical.append("Plik: ").append(fileName).append(". ");
            }
        }

        if (mentions != null) {
            for (EntityMention mention : mentions) {
                if (mention == null) {
                    continue;
                }
                String participant = participantName(mention);
                String type = mention.getEntityType() != null ? mention.getEntityType() : "PERSON";
                canonical.append("Uczestnik: ").append(participant)
                        .append(" (typ: ").append(type).append("). ");
                if (mention.getVisualCues() != null && !mention.getVisualCues().isBlank()) {
                    canonical.append("Wygląd: ").append(mention.getVisualCues()).append(". ");
                }
                if (mention.getContextObjects() != null && !mention.getContextObjects().isBlank()) {
                    canonical.append("Obiekty i otoczenie: ").append(mention.getContextObjects()).append(". ");
                }
                if (mention.getNearbyText() != null && !mention.getNearbyText().isBlank()) {
                    canonical.append("Napisy obok: ").append(mention.getNearbyText()).append(". ");
                }
            }
        }

        if (facts != null) {
            for (Fact fact : facts) {
                if (fact == null || fact.getAction() == null || fact.getAction().isBlank()) {
                    continue;
                }
                String subject = fact.getMention() != null
                        ? participantName(fact.getMention())
                        : "unknown";
                String value = factObjectLabel(fact);
                canonical.append("Relacja: ")
                        .append(subject)
                        .append(' ')
                        .append(fact.getAction().trim())
                        .append(' ')
                        .append(value)
                        .append(". ");
            }
        }

        if (file != null && file.getStructuredVisionContext() != null
                && !file.getStructuredVisionContext().isBlank()) {
            canonical.append("Opis strukturalny JSON: ")
                    .append(file.getStructuredVisionContext())
                    .append(' ');
        }
        if (file != null && file.getVisibleTexts() != null && !file.getVisibleTexts().isBlank()) {
            canonical.append("Widoczne napisy: ").append(file.getVisibleTexts()).append(". ");
        }

        return canonical.toString().trim();
    }

    static String participantName(EntityMention mention) {
        if (mention == null) {
            return "unknown";
        }
        KnowledgeEntity entity = mention.getEntity();
        if (entity != null && entity.getDisplayName() != null && !entity.getDisplayName().isBlank()) {
            return entity.getDisplayName().trim();
        }
        if (mention.getLabel() != null && !mention.getLabel().isBlank()) {
            return mention.getLabel().trim();
        }
        return "unknown";
    }

    private static String factObjectLabel(Fact fact) {
        if (fact.getTargetMention() != null) {
            return participantName(fact.getTargetMention());
        }
        if (fact.getObject() != null && !fact.getObject().isBlank()) {
            return fact.getObject().trim();
        }
        return "";
    }

    private static boolean isImageFile(FileEntity file) {
        if (file.getFileType() != null && file.getFileType().toLowerCase(Locale.ROOT).startsWith("image/")) {
            return true;
        }
        String path = file.getPath();
        if (path == null) {
            return false;
        }
        String lower = path.toLowerCase(Locale.ROOT);
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
        int slash = path.indexOf('/', "dir://".length());
        return slash > 0 ? path.substring("dir://".length(), slash) : "";
    }
}

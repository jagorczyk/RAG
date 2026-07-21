package com.rag.rag.knowledge.identity;

import com.rag.rag.folder.entity.FileEntity;
import com.rag.rag.folder.repository.FileRepository;
import com.rag.rag.knowledge.entity.EntityMention;
import com.rag.rag.knowledge.entity.KnowledgeEntity;
import com.rag.rag.knowledge.entity.MentionStatus;
import com.rag.rag.knowledge.fact.Fact;
import com.rag.rag.knowledge.fact.FactStatementRewriter;
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
        if (file.getOwnerId() != null) document.metadata().put("owner_id", file.getOwnerId().toString());

        jdbcTemplate.update(DELETE_EMBEDDINGS_SQL, path);
        embeddingStoreIngestor.ingest(document);
        log.info("Refreshed document embeddings for path {} after identity rename ({} chars)", path, text.length());
    }

    /**
     * Builds embeddable text from graph labels (current names) + stored vision context.
     * Prefer Polish claim statements over technical action codes.
     * Package-visible for unit tests. No vision LLM call.
     */
    String buildCanonicalText(FileEntity file, List<EntityMention> mentions, List<Fact> facts) {
        StringBuilder canonical = new StringBuilder();
        if (file != null) {
            if (file.getImageScene() != null && !file.getImageScene().isBlank()) {
                canonical.append("Scena: ").append(file.getImageScene().trim()).append(". ");
            }
            if (file.getImageSummary() != null && !file.getImageSummary().isBlank()) {
                canonical.append("Kontekst sceny: ").append(file.getImageSummary().trim()).append(". ");
            }
            if (file.getSceneAttributes() != null && !file.getSceneAttributes().isBlank()) {
                canonical.append("Atrybuty sceny: ").append(file.getSceneAttributes().trim()).append(". ");
            }
            String fileName = file.getFileName() != null ? file.getFileName() : fileNameFromPath(file.getPath());
            if (fileName != null && !fileName.isBlank()) {
                canonical.append("Plik: ").append(fileName).append(". ");
            }
        }

        if (mentions != null) {
            for (EntityMention mention : mentions) {
                if (mention == null || mention.getStatus() == MentionStatus.REJECTED) {
                    continue;
                }
                String participant = participantName(mention);
                String type = mention.getEntityType() != null ? mention.getEntityType() : "PERSON";
                canonical.append("Uczestnik: ").append(participant)
                        .append(" (typ: ").append(type).append("). ");
                appendJsonField(canonical, "Wygląd", mention.getVisualCues());
                appendJsonField(canonical, "Obiekty i otoczenie", mention.getContextObjects());
                appendJsonField(canonical, "Napisy obok", mention.getNearbyText());
            }
        }

        if (facts != null) {
            LinkedHashSet<String> seenStatements = new LinkedHashSet<>();
            for (Fact fact : facts) {
                if (fact == null) {
                    continue;
                }
                String statement = fact.getStatementPl();
                if (statement == null || statement.isBlank()) {
                    String subject = fact.getMention() != null
                            ? participantName(fact.getMention())
                            : "uczestnik";
                    String value = factObjectLabel(fact);
                    statement = FactStatementRewriter.buildStatement(subject, fact.getAction(), value);
                }
                statement = statement.trim();
                if (statement.isEmpty()) {
                    continue;
                }
                String key = statement.toLowerCase(Locale.ROOT);
                if (!seenStatements.add(key)) {
                    continue;
                }
                canonical.append(statement.endsWith(".") ? statement : statement + ".").append(' ');
            }
        }

        // Structured JSON only as last-resort recall if prose is still thin.
        int proseLen = canonical.length();
        if (file != null && proseLen < 120
                && file.getStructuredVisionContext() != null
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

    private static void appendJsonField(StringBuilder canonical, String label, String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return;
        }
        String cleaned = rawJson.trim();
        // Strip crude JSON array brackets for readable embed text.
        if (cleaned.startsWith("[") && cleaned.endsWith("]")) {
            cleaned = cleaned.substring(1, cleaned.length() - 1)
                    .replace("\"", "")
                    .trim();
        }
        if (cleaned.isBlank()) {
            return;
        }
        canonical.append(label).append(": ").append(cleaned).append(". ");
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

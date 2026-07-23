package com.rag.rag.ingestion.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.rag.core.text.Utf8MojibakeRepair;
import com.rag.rag.folder.entity.FileEntity;
import com.rag.rag.folder.repository.FileRepository;
import com.rag.rag.knowledge.entity.EntityMention;
import com.rag.rag.knowledge.extraction.StructuredVisionExtractor;
import com.rag.rag.knowledge.fact.Fact;
import com.rag.rag.knowledge.repository.EntityMentionRepository;
import com.rag.rag.knowledge.repository.FactRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Repairs legacy mixed-encoding vision text without invoking vision or face analysis. */
@Slf4j
@Service
@RequiredArgsConstructor
public class StoredTextEncodingRepairService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String MOJIBAKE_EMBEDDING_CANDIDATES_SQL = """
            SELECT metadata->>'path' AS path, text
            FROM embeddings
            WHERE metadata->>'path' IS NOT NULL
              AND text IS NOT NULL
              AND (
                    position(chr(194) in text) > 0
                 OR position(chr(195) in text) > 0
                 OR position(chr(196) in text) > 0
                 OR position(chr(197) in text) > 0
                 OR position(chr(226) in text) > 0
                 OR position(chr(240) in text) > 0
              )
            """;

    private final FileRepository fileRepository;
    private final EntityMentionRepository mentionRepository;
    private final FactRepository factRepository;
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public Set<String> repairStoredText() {
        Set<String> changedPaths = new LinkedHashSet<>();

        List<FileEntity> changedFiles = new ArrayList<>();
        for (FileEntity file : fileRepository.findAll()) {
            boolean changed = false;
            changed |= setIfChanged(file.getFileName(), repair(file.getFileName()), file::setFileName);
            changed |= setIfChanged(file.getImageScene(), repair(file.getImageScene()), file::setImageScene);
            changed |= setIfChanged(file.getImageSummary(), repairVisionText(file.getImageSummary()), file::setImageSummary);
            changed |= setIfChanged(file.getSceneAttributes(), repair(file.getSceneAttributes()), file::setSceneAttributes);
            changed |= setIfChanged(file.getVisibleTexts(), repair(file.getVisibleTexts()), file::setVisibleTexts);
            changed |= setIfChanged(file.getStructuredVisionContext(), repair(file.getStructuredVisionContext()),
                    file::setStructuredVisionContext);
            if (changed) {
                changedFiles.add(file);
                addPath(changedPaths, file.getPath());
            }
        }
        if (!changedFiles.isEmpty()) {
            fileRepository.saveAll(changedFiles);
        }

        List<EntityMention> changedMentions = new ArrayList<>();
        for (EntityMention mention : mentionRepository.findAll()) {
            boolean changed = false;
            changed |= setIfChanged(mention.getLabel(), repair(mention.getLabel()), mention::setLabel);
            changed |= setIfChanged(mention.getVisionLabel(), repair(mention.getVisionLabel()), mention::setVisionLabel);
            changed |= setIfChanged(mention.getVisualCues(), repair(mention.getVisualCues()), mention::setVisualCues);
            changed |= setIfChanged(mention.getContextObjects(), repair(mention.getContextObjects()), mention::setContextObjects);
            changed |= setIfChanged(mention.getNearbyText(), repair(mention.getNearbyText()), mention::setNearbyText);
            if (changed) {
                changedMentions.add(mention);
                addPath(changedPaths, mention.getFilePath());
            }
        }
        if (!changedMentions.isEmpty()) {
            mentionRepository.saveAll(changedMentions);
        }

        List<Fact> changedFacts = new ArrayList<>();
        for (Fact fact : factRepository.findAll()) {
            boolean changed = false;
            changed |= setIfChanged(fact.getAction(), repair(fact.getAction()), fact::setAction);
            changed |= setIfChanged(fact.getObject(), repair(fact.getObject()), fact::setObject);
            changed |= setIfChanged(fact.getStatementPl(), repair(fact.getStatementPl()), fact::setStatementPl);
            if (changed) {
                changedFacts.add(fact);
                addPath(changedPaths, fact.getFilePath());
            }
        }
        if (!changedFacts.isEmpty()) {
            factRepository.saveAll(changedFacts);
        }

        if (!changedPaths.isEmpty()) {
            log.info("Repaired legacy UTF-8 text for {} image path(s)", changedPaths.size());
        }
        return changedPaths;
    }

    /** Also finds old embeddings when the database text was repaired during an earlier interrupted run. */
    @Transactional(readOnly = true)
    public Set<String> findCorruptedEmbeddingPaths() {
        Set<String> paths = new LinkedHashSet<>();
        jdbcTemplate.query(MOJIBAKE_EMBEDDING_CANDIDATES_SQL, resultSet -> {
            String text = resultSet.getString("text");
            if (Utf8MojibakeRepair.looksCorrupted(text)) {
                addPath(paths, resultSet.getString("path"));
            }
        });
        return paths;
    }

    private static String repairVisionText(String value) {
        String repaired = repair(value);
        if (repaired == null || repaired.isBlank()) {
            return repaired;
        }
        String extracted = StructuredVisionExtractor.extractJsonObject(repaired);
        String structurallyRepaired = StructuredVisionExtractor.repairCommonVisionJsonErrors(extracted);
        if (structurallyRepaired.equals(repaired)) {
            return repaired;
        }
        try {
            JsonNode parsed = MAPPER.readTree(structurallyRepaired);
            if (parsed != null && parsed.isObject()) {
                return MAPPER.writeValueAsString(parsed);
            }
        } catch (Exception ignored) {
            // Keep the encoding repair even when a different structural model error remains.
        }
        return repaired;
    }

    private static String repair(String value) {
        return value == null ? null : Utf8MojibakeRepair.repair(value);
    }

    private static boolean setIfChanged(String previous, String next, java.util.function.Consumer<String> setter) {
        if (Objects.equals(previous, next)) {
            return false;
        }
        setter.accept(next);
        return true;
    }

    private static void addPath(Set<String> paths, String path) {
        if (path != null && !path.isBlank()) {
            paths.add(path.trim());
        }
    }
}

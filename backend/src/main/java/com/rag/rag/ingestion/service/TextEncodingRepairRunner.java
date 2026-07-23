package com.rag.rag.ingestion.service;

import com.rag.rag.knowledge.identity.CanonicalEmbeddingRefreshService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Idempotent startup backfill; it never runs image analysis or face detection. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rag.text-encoding.repair-on-startup", havingValue = "true", matchIfMissing = true)
public class TextEncodingRepairRunner implements ApplicationRunner {

    private final StoredTextEncodingRepairService repairService;
    private final CanonicalEmbeddingRefreshService embeddingRefreshService;

    @Override
    public void run(ApplicationArguments args) {
        Set<String> paths = new LinkedHashSet<>(repairService.repairStoredText());
        paths.addAll(repairService.findCorruptedEmbeddingPaths());
        if (paths.isEmpty()) {
            log.info("Legacy UTF-8 repair: no affected embeddings");
            return;
        }
        log.info("Legacy UTF-8 repair: rebuilding {} canonical image embedding(s)", paths.size());
        for (String path : paths) {
            try {
                embeddingRefreshService.refreshPaths(List.of(path));
            } catch (Exception exception) {
                // The stale embedding remains detectable and will be retried on the next startup.
                log.warn("Could not rebuild repaired embedding for {}: {}", path, exception.getMessage());
            }
        }
    }
}

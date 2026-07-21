package com.rag.rag.ingestion.service;

import com.rag.rag.folder.entity.FileEntity;
import com.rag.rag.folder.repository.FileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Optional one-shot background pass that upgrades stale image graph projections on startup. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rag.image-graph.repair-enabled", havingValue = "true")
public class ImageGraphRepairRunner implements ApplicationRunner {
    private final FileRepository fileRepository;
    private final IngestionService ingestionService;

    @Value("${vision.structured.analyzer-version:v6-face-anchored}")
    private String targetVersion;

    @Override
    public void run(ApplicationArguments args) {
        CompletableFuture.runAsync(() -> {
            List<FileEntity> stale = fileRepository.findImagesWithStaleGraphProjection(targetVersion);
            if (stale.isEmpty()) {
                log.info("Image graph repair: nothing stale for version {}", targetVersion);
                return;
            }
            log.info("Image graph repair: reanalyzing {} image(s) for version {}", stale.size(), targetVersion);
            for (FileEntity image : stale) {
                try {
                    image.setGraphProjectionStatus("PROCESSING");
                    fileRepository.save(image);
                    ingestionService.reanalyzeExistingImage(image);
                } catch (Exception exception) {
                    fileRepository.findById(image.getId()).ifPresent(failed -> {
                        failed.setGraphProjectionVersion(targetVersion);
                        failed.setGraphProjectionStatus("FAILED");
                        fileRepository.save(failed);
                    });
                    log.warn("Image graph repair failed for {}: {}", image.getPath(), exception.getMessage());
                }
            }
        });
    }
}

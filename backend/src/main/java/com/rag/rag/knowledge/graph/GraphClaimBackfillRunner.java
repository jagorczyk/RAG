package com.rag.rag.knowledge.graph;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/** Optional startup pass: appearance claims + statement rewrite without re-vision. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rag.graph.claim-backfill-enabled", havingValue = "true")
public class GraphClaimBackfillRunner implements ApplicationRunner {

    private final GraphClaimBackfillService graphClaimBackfillService;

    @Override
    public void run(ApplicationArguments args) {
        CompletableFuture.runAsync(() -> {
            try {
                int created = graphClaimBackfillService.backfillAll();
                log.info("Startup graph claim backfill finished ({} new appearance facts)", created);
            } catch (Exception e) {
                log.warn("Startup graph claim backfill failed: {}", e.getMessage());
            }
        });
    }
}

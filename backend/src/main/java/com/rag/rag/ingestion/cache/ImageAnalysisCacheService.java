package com.rag.rag.ingestion.cache;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Service
public class ImageAnalysisCacheService {

    private final ImageAnalysisCacheRepository repository;
    private final ImageAnalysisCacheService self;
    private final Map<String, Object> locks = new ConcurrentHashMap<>();

    public ImageAnalysisCacheService(
            ImageAnalysisCacheRepository repository,
            @Lazy ImageAnalysisCacheService self
    ) {
        this.repository = repository;
        this.self = self;
    }

    public String getOrCompute(
            String contentHash,
            ImageAnalysisAnalyzer analyzer,
            String analyzerVersion,
            Supplier<String> supplier
    ) {
        String lockKey = contentHash + ":" + analyzer + ":" + analyzerVersion;
        Object lock = locks.computeIfAbsent(lockKey, ignored -> new Object());
        try {
            synchronized (lock) {
                // Route through the Spring proxy so REQUIRES_NEW applies.
                // Unit tests may construct the service without a proxy (self == null).
                ImageAnalysisCacheService target = self != null ? self : this;
                return target.computeInTransaction(contentHash, analyzer, analyzerVersion, supplier);
            }
        } finally {
            locks.remove(lockKey, lock);
        }
    }

    /**
     * REQUIRES_NEW so a failed analyzer (e.g. face-service) does not mark the caller
     * transaction rollback-only when the exception is caught upstream.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String computeInTransaction(
            String contentHash,
            ImageAnalysisAnalyzer analyzer,
            String analyzerVersion,
            Supplier<String> supplier
    ) {
        var existing = repository.findByContentHashAndAnalyzerAndAnalyzerVersion(
                contentHash, analyzer, analyzerVersion);
        ImageAnalysisCache row = existing.orElseGet(() -> ImageAnalysisCache.builder()
                .contentHash(contentHash)
                .analyzer(analyzer)
                .analyzerVersion(analyzerVersion)
                .status(ImageAnalysisStatus.PROCESSING)
                .build());

        if (row.getStatus() == ImageAnalysisStatus.COMPLETED) {
            return row.getPayload();
        }

        row.setStatus(ImageAnalysisStatus.PROCESSING);
        row.setErrorMessage(null);
        repository.saveAndFlush(row);

        try {
            String payload = supplier.get();
            row.setPayload(payload);
            row.setStatus(ImageAnalysisStatus.COMPLETED);
            row.setErrorMessage(null);
            repository.save(row);
            return payload;
        } catch (RuntimeException e) {
            repository.findByContentHashAndAnalyzerAndAnalyzerVersion(contentHash, analyzer, analyzerVersion)
                    .ifPresent(persisted -> {
                        persisted.setStatus(ImageAnalysisStatus.FAILED);
                        persisted.setPayload(null);
                        persisted.setErrorMessage(e.getMessage());
                        repository.save(persisted);
                    });
            throw e;
        }
    }
}

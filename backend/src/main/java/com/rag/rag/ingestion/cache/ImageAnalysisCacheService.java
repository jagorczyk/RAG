package com.rag.rag.ingestion.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Slf4j
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
            String cached = row.getPayload();
            // Reject legacy @Lob OID leftovers ("162191") and empty rows — recompute.
            if (isUsableCachePayload(cached)) {
                return cached;
            }
            log.warn("Discarding unusable {} cache payload for hash={} (len={})",
                    analyzer, contentHash, cached == null ? 0 : cached.length());
            row.setStatus(ImageAnalysisStatus.PROCESSING);
            row.setPayload(null);
            row.setErrorMessage("invalid_cached_payload");
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

    /**
     * Cached payloads must be real JSON/text, not a PostgreSQL large-object OID digit string.
     */
    static boolean isUsableCachePayload(String payload) {
        if (payload == null || payload.isBlank()) {
            return false;
        }
        String trimmed = payload.trim();
        // Legacy @Lob OID only (all digits, short).
        if (trimmed.length() <= 12 && trimmed.chars().allMatch(Character::isDigit)) {
            return false;
        }
        // Vision/face payloads are JSON objects/arrays.
        char first = trimmed.charAt(0);
        return first == '{' || first == '[';
    }
}

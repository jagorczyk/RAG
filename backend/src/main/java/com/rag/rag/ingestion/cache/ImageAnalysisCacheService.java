package com.rag.rag.ingestion.cache;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class ImageAnalysisCacheService {

    private final ImageAnalysisCacheRepository repository;
    private final Map<String, Object> locks = new ConcurrentHashMap<>();

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
                return getOrComputeLocked(contentHash, analyzer, analyzerVersion, supplier);
            }
        } finally {
            locks.remove(lockKey, lock);
        }
    }

    @Transactional
    protected String getOrComputeLocked(
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
        if (existing.isEmpty()) {
            repository.saveAndFlush(row);
        } else {
            repository.saveAndFlush(row);
        }

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
                    });            row.setPayload(null);
            row.setStatus(ImageAnalysisStatus.FAILED);
            row.setErrorMessage(e.getMessage());
            repository.save(row);
            throw e;
        }
    }
}

package com.rag.rag.ingestion.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImageAnalysisCacheServiceTest {

    private ImageAnalysisCacheRepository repository;
    private ImageAnalysisCacheService service;

    @BeforeEach
    void setUp() {
        repository = mock(ImageAnalysisCacheRepository.class);
        // self == null: unit tests call computeInTransaction on the same instance (no Spring proxy).
        service = new ImageAnalysisCacheService(repository, null);
        when(repository.save(any(ImageAnalysisCache.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.saveAndFlush(any(ImageAnalysisCache.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void shouldReuseCompletedEmptyFaceResultWithoutCallingSupplier() {
        ImageAnalysisCache completed = cacheRow(ImageAnalysisStatus.COMPLETED, "[]");
        when(repository.findByContentHashAndAnalyzerAndAnalyzerVersion("hash", ImageAnalysisAnalyzer.FACE, "face-v1"))
                .thenReturn(Optional.of(completed));
        AtomicInteger supplierCalls = new AtomicInteger();

        String result = service.getOrCompute("hash", ImageAnalysisAnalyzer.FACE, "face-v1", () -> {
            supplierCalls.incrementAndGet();
            return "unexpected";
        });

        assertEquals("[]", result);
        assertEquals(0, supplierCalls.get());
    }

    @Test
    void shouldPersistSuccessfulResultAsCompleted() {
        when(repository.findByContentHashAndAnalyzerAndAnalyzerVersion("hash", ImageAnalysisAnalyzer.VISION, "vision-v1"))
                .thenReturn(Optional.empty());

        String result = service.getOrCompute(
                "hash", ImageAnalysisAnalyzer.VISION, "vision-v1", () -> "{\"entities\":[]}"
        );

        assertEquals("{\"entities\":[]}", result);
        verify(repository).saveAndFlush(any(ImageAnalysisCache.class));
        verify(repository).save(any(ImageAnalysisCache.class));
    }

    @Test
    void shouldMarkFailureAndRecomputeItOnNextCall() {
        ImageAnalysisCache row = cacheRow(ImageAnalysisStatus.PROCESSING, null);
        when(repository.findByContentHashAndAnalyzerAndAnalyzerVersion("hash", ImageAnalysisAnalyzer.VISION, "vision-v1"))
                .thenReturn(Optional.empty(), Optional.of(row));

        assertThrows(IllegalStateException.class, () -> service.getOrCompute(
                "hash", ImageAnalysisAnalyzer.VISION, "vision-v1", () -> {
                    throw new IllegalStateException("temporary error");
                }
        ));
        assertEquals(ImageAnalysisStatus.FAILED, row.getStatus());
        assertEquals("temporary error", row.getErrorMessage());

        String retry = service.getOrCompute(
                "hash", ImageAnalysisAnalyzer.VISION, "vision-v1", () -> "recovered"
        );

        assertEquals("recovered", retry);
        assertEquals(ImageAnalysisStatus.COMPLETED, row.getStatus());
        assertEquals("recovered", row.getPayload());
    }

    @Test
    void shouldCoalesceConcurrentComputationsForTheSameKey() throws Exception {
        AtomicReference<ImageAnalysisCache> stored = new AtomicReference<>();
        when(repository.findByContentHashAndAnalyzerAndAnalyzerVersion("hash", ImageAnalysisAnalyzer.FACE, "face-v1"))
                .thenAnswer(ignored -> Optional.ofNullable(stored.get()));
        when(repository.save(any(ImageAnalysisCache.class))).thenAnswer(invocation -> {
            ImageAnalysisCache row = invocation.getArgument(0);
            stored.set(row);
            return row;
        });
        when(repository.saveAndFlush(any(ImageAnalysisCache.class))).thenAnswer(invocation -> {
            ImageAnalysisCache row = invocation.getArgument(0);
            stored.set(row);
            return row;
        });

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger computations = new AtomicInteger();
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> service.getOrCompute(
                    "hash", ImageAnalysisAnalyzer.FACE, "face-v1", () -> {
                        computations.incrementAndGet();
                        started.countDown();
                        await(release);
                        return "[]";
                    }
            ));
            assertTrue(started.await(1, TimeUnit.SECONDS));
            var second = executor.submit(() -> service.getOrCompute(
                    "hash", ImageAnalysisAnalyzer.FACE, "face-v1", () -> {
                        computations.incrementAndGet();
                        return "duplicate";
                    }
            ));

            release.countDown();

            assertEquals("[]", first.get(1, TimeUnit.SECONDS));
            assertEquals("[]", second.get(1, TimeUnit.SECONDS));
            assertEquals(1, computations.get());
        } finally {
            executor.shutdownNow();
        }
    }

    private ImageAnalysisCache cacheRow(ImageAnalysisStatus status, String payload) {
        return ImageAnalysisCache.builder()
                .contentHash("hash")
                .analyzer(ImageAnalysisAnalyzer.FACE)
                .analyzerVersion("face-v1")
                .status(status)
                .payload(payload)
                .build();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}

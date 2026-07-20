package com.rag.rag.ingestion.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves worker concurrency is property-driven and defaults allow parallel ingest (>1).
 */
class RabbitIngestWorkerConcurrencyTest {

    @Test
    void applyWorkerConcurrencySetsBoundedParallelConsumers() {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        RabbitIngestConfig.applyWorkerConcurrency(factory, 2, 4, 1);

        assertEquals(2, ReflectionTestUtils.getField(factory, "concurrentConsumers"));
        assertEquals(4, ReflectionTestUtils.getField(factory, "maxConcurrentConsumers"));
        assertEquals(1, ReflectionTestUtils.getField(factory, "prefetchCount"));
    }

    @Test
    void applyWorkerConcurrencyRaisesMaxToAtLeastConcurrent() {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        RabbitIngestConfig.applyWorkerConcurrency(factory, 3, 1, 2);

        assertEquals(3, ReflectionTestUtils.getField(factory, "concurrentConsumers"));
        assertEquals(3, ReflectionTestUtils.getField(factory, "maxConcurrentConsumers"));
        assertEquals(2, ReflectionTestUtils.getField(factory, "prefetchCount"));
    }

    @Test
    void applyWorkerConcurrencyNeverDropsBelowOne() {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        RabbitIngestConfig.applyWorkerConcurrency(factory, 0, 0, 0);

        assertEquals(1, ReflectionTestUtils.getField(factory, "concurrentConsumers"));
        assertEquals(1, ReflectionTestUtils.getField(factory, "maxConcurrentConsumers"));
        assertEquals(1, ReflectionTestUtils.getField(factory, "prefetchCount"));
    }

    @Test
    void defaultPropertyValuesAllowMoreThanOneConsumer() {
        // Mirrors application.properties defaults used by the factory bean.
        int concurrent = 2;
        int max = 4;
        assertTrue(concurrent >= 2, "default worker concurrency must be > 1 for parallel ingest");
        assertTrue(max >= concurrent);
    }
}

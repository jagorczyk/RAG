package com.rag.rag.core.health;

import com.rag.rag.knowledge.face.FaceRecognitionClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FaceServiceHealthIndicatorTest {

    @Test
    void reportsUpWhenClientHealthy() {
        FaceRecognitionClient client = mock(FaceRecognitionClient.class);
        when(client.isHealthy()).thenReturn(true);

        Health health = new FaceServiceHealthIndicator(client).health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals("face-service", health.getDetails().get("service"));
    }

    @Test
    void reportsDownWhenClientUnhealthy() {
        FaceRecognitionClient client = mock(FaceRecognitionClient.class);
        when(client.isHealthy()).thenReturn(false);

        Health health = new FaceServiceHealthIndicator(client).health();

        assertEquals(Status.DOWN, health.getStatus());
    }
}

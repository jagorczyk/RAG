package com.rag.rag.core.health;

import com.rag.rag.knowledge.face.FaceRecognitionClient;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Actuator health component for the external face recognition service.
 * Exposed as {@code faceService} under {@code /actuator/health}.
 */
@Component("faceService")
public class FaceServiceHealthIndicator implements HealthIndicator {

    private final FaceRecognitionClient faceRecognitionClient;

    public FaceServiceHealthIndicator(FaceRecognitionClient faceRecognitionClient) {
        this.faceRecognitionClient = faceRecognitionClient;
    }

    @Override
    public Health health() {
        boolean healthy = faceRecognitionClient.isHealthy();
        if (healthy) {
            return Health.up()
                    .withDetail("service", "face-service")
                    .build();
        }
        return Health.down()
                .withDetail("service", "face-service")
                .withDetail("reason", "unreachable or disabled")
                .build();
    }
}

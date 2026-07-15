package com.rag.rag.knowledge.face;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Slf4j
@Component
public class FaceRecognitionClient {

    private final RestTemplate restTemplate;
    private final String analyzeUrl;
    private final String healthUrl;
    private final boolean enabled;

    public FaceRecognitionClient(
            @Value("${face.service.url:http://localhost:8001}") String baseUrl,
            @Value("${face.service.enabled:true}") boolean enabled
    ) {
        this.enabled = enabled;
        this.restTemplate = new RestTemplate();
        String normalizedBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.analyzeUrl = normalizedBaseUrl + "/analyze";
        this.healthUrl = normalizedBaseUrl + "/health";
    }

    public List<DetectedFaceDto> analyze(byte[] imageBytes, String fileName) {
        FaceAnalyzeResponse response = analyzeResponse(imageBytes, fileName);
        if (response == null || response.faces() == null) {
            return List.of();
        }
        return response.faces().stream()
                .map(face -> face.withImageDimensions(response.imageWidth() == null ? 0 : response.imageWidth(), response.imageHeight() == null ? 0 : response.imageHeight()))
                .toList();
    }

    public FaceAnalyzeResponse analyzeResponse(byte[] imageBytes, String fileName) {
        try {
            return analyzeResponseOrThrow(imageBytes, fileName);
        } catch (Exception e) {
            log.warn("Face recognition service call failed: {}", e.getMessage());
            return new FaceAnalyzeResponse(List.of(), 0, 0, 0);
        }
    }

    public FaceAnalyzeResponse analyzeResponseOrThrow(byte[] imageBytes, String fileName) {
        if (!enabled || imageBytes == null || imageBytes.length == 0) {
            return new FaceAnalyzeResponse(List.of(), 0, 0, 0);
        }

        String resolvedFileName = StringUtils.hasText(fileName) ? fileName : "image.jpg";
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new NamedByteArrayResource(imageBytes, resolvedFileName));

        FaceAnalyzeResponse response = restTemplate.postForObject(analyzeUrl, body, FaceAnalyzeResponse.class);
        if (response == null || response.faces() == null) {
            return new FaceAnalyzeResponse(List.of(), 0, 0, 0);
        }
        return response;
    }

    public boolean isHealthy() {
        if (!enabled) {
            return false;
        }
        try {
            restTemplate.getForEntity(healthUrl, String.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static final class NamedByteArrayResource extends ByteArrayResource {
        private final String filename;

        private NamedByteArrayResource(byte[] bytes, String filename) {
            super(bytes);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}

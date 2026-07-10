package com.rag.rag.knowledge.face;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.List;

@Slf4j
@Component
public class FaceRecognitionClient {

    private final RestClient restClient;
    private final boolean enabled;

    public FaceRecognitionClient(
            @Value("${face.service.url:http://localhost:8001}") String baseUrl,
            @Value("${face.service.enabled:true}") boolean enabled
    ) {
        this.enabled = enabled;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    public List<DetectedFaceDto> analyze(byte[] imageBytes, String fileName) {
        if (!enabled || imageBytes == null || imageBytes.length == 0) {
            return List.of();
        }

        try {
            String resolvedFileName = StringUtils.hasText(fileName) ? fileName : "image.jpg";
            HttpHeaders fileHeaders = new HttpHeaders();
            fileHeaders.setContentDisposition(ContentDisposition.formData()
                    .name("file")
                    .filename(resolvedFileName)
                    .build());

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new HttpEntity<>(new ByteArrayResource(imageBytes), fileHeaders));

            FaceAnalyzeResponse response = restClient.post()
                    .uri("/analyze")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(FaceAnalyzeResponse.class);

            if (response == null || response.faces() == null) {
                return List.of();
            }
            return response.faces();
        } catch (Exception e) {
            log.warn("Face recognition service call failed: {}", e.getMessage());
            return List.of();
        }
    }

    public boolean isHealthy() {
        if (!enabled) {
            return false;
        }
        try {
            restClient.get()
                    .uri("/health")
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}

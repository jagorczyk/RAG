package com.rag.rag.knowledge.face;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FaceRecognitionClientTest {

    private MockRestServiceServer server;
    private FaceRecognitionClient client;

    @BeforeEach
    void setUp() {
        client = new FaceRecognitionClient("http://localhost:8001", true);
        RestTemplate restTemplate = (RestTemplate) org.springframework.test.util.ReflectionTestUtils
                .getField(client, "restTemplate");
        server = MockRestServiceServer.bindTo(restTemplate).build();
    }

    @AfterEach
    void verify() {
        server.verify();
    }

    @Test
    void shouldSendMultipartFileToAnalyzeEndpoint() {
        server.expect(requestTo("http://localhost:8001/analyze"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(request -> {
                    String contentType = request.getHeaders().getFirst("Content-Type");
                    assertTrue(contentType != null && contentType.startsWith("multipart/form-data"));
                    String body = request.getBody().toString();
                    assertTrue(body.contains("filename=\"photo.jpg\""));
                    assertTrue(body.contains("name=\"file\""));
                })
                .andRespond(withSuccess(
                        "{\"faces\":[{\"embedding\":[1.0],\"bbox\":[0,0,10,10],\"det_score\":0.99}],\"count\":1}",
                        MediaType.APPLICATION_JSON
                ));

        var faces = client.analyze("fake-image".getBytes(StandardCharsets.UTF_8), "photo.jpg");

        assertEquals(1, faces.size());
        assertEquals(0f, faces.get(0).bbox().get(0));
    }
}

package com.rag.rag.core.config;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Guards single source of truth for retrieval max-results and face match thresholds
 * in application.properties (Sprint 6.4).
 */
class RetrievalAndFaceDefaultsTest {

    @Test
    void applicationPropertiesHoldCanonicalFaceAndRetrievalDefaults() throws Exception {
        Properties props = loadApplicationProperties();

        assertEquals("5", props.getProperty("rag.retrieval.max-results"));
        assertEquals("20", props.getProperty("rag.retrieval.photo-max-results"));
        assertEquals("40", props.getProperty("rag.retrieval.recall-max-results"));
        assertEquals("15", props.getProperty("rag.retrieval.graph-max-results"));
        assertEquals("40", props.getProperty("rag.retrieval.lexical-max-results"));

        assertEquals("90", defaultFromPlaceholder(props.getProperty("llm.timeout-seconds")));
        assertEquals("120", defaultFromPlaceholder(props.getProperty("llm.vision.timeout-seconds")));
        assertEquals("0", defaultFromPlaceholder(props.getProperty("llm.vision.max-retries")));

        // Env placeholders: ${FACE_MATCH_THRESHOLD:0.50}
        assertEquals("0.50", defaultFromPlaceholder(props.getProperty("face.match.threshold")));
        assertEquals("0.45", defaultFromPlaceholder(props.getProperty("face.match.suggestion-threshold")));
        assertEquals("0.08", defaultFromPlaceholder(props.getProperty("face.match.min-margin")));
        assertEquals("0.50", defaultFromPlaceholder(props.getProperty("face.match.min-det-score")));
    }

    private static Properties loadApplicationProperties() throws Exception {
        try (InputStream in = RetrievalAndFaceDefaultsTest.class
                .getClassLoader()
                .getResourceAsStream("application.properties")) {
            assertNotNull(in, "application.properties must be on test classpath");
            Properties props = new Properties();
            props.load(in);
            return props;
        }
    }

    /** Extracts default from ${ENV:default} or returns raw value. */
    static String defaultFromPlaceholder(String raw) {
        assertNotNull(raw);
        if (raw.startsWith("${") && raw.contains(":") && raw.endsWith("}")) {
            int colon = raw.lastIndexOf(':');
            return raw.substring(colon + 1, raw.length() - 1);
        }
        return raw;
    }
}

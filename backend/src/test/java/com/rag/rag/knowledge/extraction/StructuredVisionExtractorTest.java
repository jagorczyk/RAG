package com.rag.rag.knowledge.extraction;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructuredVisionExtractorTest {

    @Test
    void repairFixesPrematureEntityCloseBeforeBbox() {
        // Live failure shape from DeepInfra/Qwen vision on car+knife photos.
        String broken = """
                {"entities":[{"label":"person 1","face_anchor_id":"face_1","type":"PERSON",\
                "actions":["siedzi"],"objects":["noz"],"visual_cues":["kurtka"],\
                "nearby_objects":["kierownica"],"nearby_text":["VW"]},"bbox":[0,190,1000,650]}],\
                "relations":[],"scene":"auto","scene_summary":"osoba w aucie"}
                """.replace("\\", "");
        // The above still has the broken pattern — rebuild explicitly:
        broken = "{\"entities\":[{\"label\":\"person 1\",\"type\":\"PERSON\","
                + "\"nearby_text\":[\"VW\"]},\"bbox\":[0,190,1000,650]}],"
                + "\"relations\":[],\"scene\":\"auto\",\"scene_summary\":\"osoba w aucie\"}";

        assertTrue(broken.contains("]},\"bbox\":") || broken.contains("]},\"bbox\":".replace("]","]")));
        // broken is: ...nearby_text":["VW"]},"bbox":...
        assertTrue(broken.contains("\"VW\"]},\"bbox\":"));

        String fixed = StructuredVisionExtractor.repairCommonVisionJsonErrors(broken);
        assertFalse(fixed.contains("\"VW\"]},\"bbox\":"), "premature close before bbox should be fixed");
        assertTrue(fixed.contains("\"VW\"],\"bbox\":"));
        assertTrue(fixed.contains("\"bbox\":[0,190,1000,650]"));
    }

    @Test
    void extractJsonObjectStripsMarkdownFence() {
        String fenced = "```json\n{\"scene\":\"park\"}\n```";
        assertTrue(StructuredVisionExtractor.extractJsonObject(fenced).contains("\"scene\""));
    }
}

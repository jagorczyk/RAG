package com.rag.rag.core.text;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Utf8MojibakeRepairTest {

    @Test
    void repairsOnlyBrokenUtf8SequencesInsideMixedPolishText() {
        String broken = "si\u00C4\u0099, r\u00C4\u0099k\u00C4\u0099, kr\u00C3\u00B3tkie blond w\u00C5\u0082osy, bia\u0142a pokrywka";

        String repaired = Utf8MojibakeRepair.repair(broken);

        assertEquals("si\u0119, r\u0119k\u0119, kr\u00F3tkie blond w\u0142osy, bia\u0142a pokrywka", repaired);
        assertFalse(Utf8MojibakeRepair.looksCorrupted(repaired));
    }

    @Test
    void preservesAlreadyCorrectPolishAndJsonSyntax() {
        String correct = "{\"summary\":\"M\u0119\u017Cczyzna podnosi r\u0119k\u0119; bia\u0142a pokrywka.\"}";

        assertEquals(correct, Utf8MojibakeRepair.repair(correct));
        assertFalse(Utf8MojibakeRepair.looksCorrupted(correct));
        assertTrue(Utf8MojibakeRepair.looksCorrupted("w\u00C5\u0082osy"));
    }
}

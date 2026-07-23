package com.rag.rag.core.text;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Repairs UTF-8 byte sequences that were decoded as ISO-8859-1/Windows-1252.
 *
 * <p>The repair is deliberately local: only a valid mojibake byte sequence is
 * replaced. Correct Polish characters elsewhere in the same value are left
 * untouched.</p>
 */
public final class Utf8MojibakeRepair {

    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");
    private static final int MAX_PASSES = 2;

    private Utf8MojibakeRepair() {
    }

    public static String repair(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        String current = value;
        for (int pass = 0; pass < MAX_PASSES; pass++) {
            String repaired = repairOnce(current);
            if (repaired.equals(current)) {
                break;
            }
            current = repaired;
        }
        return current;
    }

    public static boolean looksCorrupted(String value) {
        return value != null && !value.isEmpty() && !repairOnce(value).equals(value);
    }

    private static String repairOnce(String value) {
        StringBuilder output = new StringBuilder(value.length());
        int index = 0;
        while (index < value.length()) {
            int lead = originalByte(value.charAt(index));
            int sequenceLength = utf8SequenceLength(lead);
            if (sequenceLength > 0 && index + sequenceLength <= value.length()) {
                byte[] bytes = new byte[sequenceLength];
                bytes[0] = (byte) lead;
                boolean valid = true;
                for (int offset = 1; offset < sequenceLength; offset++) {
                    int continuation = originalByte(value.charAt(index + offset));
                    if (continuation < 0x80 || continuation > 0xBF) {
                        valid = false;
                        break;
                    }
                    bytes[offset] = (byte) continuation;
                }
                if (valid) {
                    String decoded = new String(bytes, StandardCharsets.UTF_8);
                    if (!decoded.contains("\uFFFD")) {
                        output.append(decoded);
                        index += sequenceLength;
                        continue;
                    }
                }
            }
            output.append(value.charAt(index));
            index++;
        }
        return output.toString();
    }

    private static int utf8SequenceLength(int lead) {
        if (lead >= 0xC2 && lead <= 0xDF) {
            return 2;
        }
        if (lead >= 0xE0 && lead <= 0xEF) {
            return 3;
        }
        if (lead >= 0xF0 && lead <= 0xF4) {
            return 4;
        }
        return 0;
    }

    /** Reverse the common single-byte decoding step without touching real Unicode. */
    private static int originalByte(char value) {
        if (value <= 0xFF) {
            return value;
        }
        byte[] encoded = String.valueOf(value).getBytes(WINDOWS_1252);
        if (encoded.length == 1 && encoded[0] != (byte) '?') {
            return encoded[0] & 0xFF;
        }
        return -1;
    }
}

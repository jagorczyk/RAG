package com.rag.rag.core.exception;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        List<Map<String, String>> fieldErrors
) {
    public static ErrorResponse of(
            int status,
            String error,
            String code,
            String message,
            String path,
            List<Map<String, String>> fieldErrors
    ) {
        return new ErrorResponse(Instant.now(), status, error, code, message, path, fieldErrors);
    }
}

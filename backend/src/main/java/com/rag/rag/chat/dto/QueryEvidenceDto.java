package com.rag.rag.chat.dto;

import java.math.BigDecimal;
import java.util.List;

public record QueryEvidenceDto(String path, BigDecimal confidence, List<String> reasons,
                               String matchStatus, BigDecimal retrievalScore,
                               BigDecimal entityConfidence, BigDecimal conditionConfidence) {
    public QueryEvidenceDto(String path, BigDecimal confidence, List<String> reasons) {
        this(path, confidence, reasons, "CONFIRMED", null, null, confidence);
    }

    public QueryEvidenceDto(String path, BigDecimal confidence, List<String> reasons, String matchStatus) {
        this(path, confidence, reasons, matchStatus, null, null, confidence);
    }
}

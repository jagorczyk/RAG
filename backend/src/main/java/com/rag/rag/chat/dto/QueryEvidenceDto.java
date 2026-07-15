package com.rag.rag.chat.dto;

import java.math.BigDecimal;
import java.util.List;

public record QueryEvidenceDto(String path, BigDecimal confidence, List<String> reasons,
                               String matchStatus) {
    public QueryEvidenceDto(String path, BigDecimal confidence, List<String> reasons) {
        this(path, confidence, reasons, "CONFIRMED");
    }
}

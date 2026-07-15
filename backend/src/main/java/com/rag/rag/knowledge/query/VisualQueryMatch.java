package com.rag.rag.knowledge.query;

import java.math.BigDecimal;
import java.util.List;

public record VisualQueryMatch(String filePath, BigDecimal confidence, List<String> reasons) {}

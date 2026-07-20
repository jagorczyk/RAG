package com.rag.rag.core.config;

import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmbeddingConfigurationDimensionTest {

    private static final Pattern VECTOR_DIM = Pattern.compile("vector\\((\\d+)\\)", Pattern.CASE_INSENSITIVE);

    @Test
    void parsesVectorTypmod() {
        Matcher m1024 = VECTOR_DIM.matcher("vector(1024)");
        Matcher m2560 = VECTOR_DIM.matcher("vector(2560)");
        assertEquals(true, m1024.find());
        assertEquals(1024, Integer.parseInt(m1024.group(1)));
        assertEquals(true, m2560.find());
        assertEquals(2560, Integer.parseInt(m2560.group(1)));
    }

    @Test
    void readVectorDimensionReturnsNullWhenMissing() throws SQLException {
        Statement statement = mock(Statement.class);
        ResultSet rs = mock(ResultSet.class);
        when(statement.executeQuery(anyString())).thenReturn(rs);
        when(rs.next()).thenReturn(false);

        assertNull(EmbeddingConfiguration.readVectorDimension(statement, "embeddings", "embedding"));
    }

    @Test
    void readVectorDimensionParsesPgFormatType() throws SQLException {
        Statement statement = mock(Statement.class);
        ResultSet rs = mock(ResultSet.class);
        when(statement.executeQuery(anyString())).thenReturn(rs);
        when(rs.next()).thenReturn(true);
        when(rs.getString(1)).thenReturn("vector(1024)");

        assertEquals(1024, EmbeddingConfiguration.readVectorDimension(statement, "embeddings", "embedding"));
    }
}

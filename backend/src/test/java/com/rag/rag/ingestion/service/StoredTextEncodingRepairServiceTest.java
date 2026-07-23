package com.rag.rag.ingestion.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.rag.folder.entity.FileEntity;
import com.rag.rag.folder.repository.FileRepository;
import com.rag.rag.knowledge.entity.EntityMention;
import com.rag.rag.knowledge.fact.Fact;
import com.rag.rag.knowledge.repository.EntityMentionRepository;
import com.rag.rag.knowledge.repository.FactRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoredTextEncodingRepairServiceTest {

    @Mock FileRepository fileRepository;
    @Mock EntityMentionRepository mentionRepository;
    @Mock FactRepository factRepository;
    @Mock JdbcTemplate jdbcTemplate;

    @Test
    void repairsStoredTextAndMakesRecoverableVisionJsonValid() throws Exception {
        String broken = "{\"entities\":[{\"label\":\"person 1\",\"type\":\"PERSON\"," 
                + "\"actions\":[\"u\u00C5\u009Bmiecha si\u00C4\u0099\"]},"
                + "\"label\":\"kurtka\",\"type\":\"CLOTHING\"}],"
                + "\"scene_summary\":\"Podnosi r\u00C4\u0099k\u00C4\u0099.\"}";
        FileEntity file = FileEntity.builder()
                .path("dir://dupek/20230417_115245.jpg")
                .imageSummary(broken)
                .build();
        EntityMention mention = EntityMention.builder()
                .filePath(file.getPath()).label("Igor")
                .visualCues("[\"kr\u00C3\u00B3tkie w\u00C5\u0082osy\"]")
                .build();
        Fact fact = Fact.builder()
                .filePath(file.getPath()).action("podnosi")
                .object("r\u00C4\u0099k\u00C4\u0099")
                .build();
        when(fileRepository.findAll()).thenReturn(List.of(file));
        when(mentionRepository.findAll()).thenReturn(List.of(mention));
        when(factRepository.findAll()).thenReturn(List.of(fact));
        StoredTextEncodingRepairService service = new StoredTextEncodingRepairService(
                fileRepository, mentionRepository, factRepository, jdbcTemplate);

        Set<String> paths = service.repairStoredText();

        assertEquals(Set.of(file.getPath()), paths);
        JsonNode recovered = new ObjectMapper().readTree(file.getImageSummary());
        assertEquals(2, recovered.path("entities").size());
        assertEquals("u\u015Bmiecha si\u0119", recovered.path("entities").get(0).path("actions").get(0).asText());
        assertEquals("[\"kr\u00F3tkie w\u0142osy\"]", mention.getVisualCues());
        assertEquals("r\u0119k\u0119", fact.getObject());
        assertFalse(file.getImageSummary().contains("\u00C4"));
        verify(fileRepository).saveAll(List.of(file));
        verify(mentionRepository).saveAll(List.of(mention));
        verify(factRepository).saveAll(List.of(fact));
    }
}

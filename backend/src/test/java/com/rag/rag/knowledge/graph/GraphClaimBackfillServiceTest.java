package com.rag.rag.knowledge.graph;

import com.rag.rag.knowledge.entity.EntityMention;
import com.rag.rag.knowledge.entity.KnowledgeEntity;
import com.rag.rag.knowledge.entity.MentionStatus;
import com.rag.rag.knowledge.fact.Fact;
import com.rag.rag.knowledge.fact.FactStatementRewriter;
import com.rag.rag.knowledge.repository.EntityMentionRepository;
import com.rag.rag.knowledge.repository.FactRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GraphClaimBackfillServiceTest {

    @Mock EntityMentionRepository mentionRepository;
    @Mock FactRepository factRepository;
    @Mock FactStatementRewriter factStatementRewriter;
    @Mock MentionEvidencePolicy mentionEvidencePolicy;
    @InjectMocks GraphClaimBackfillService service;

    @Test
    void createsMissingAppearanceFactsFromVisualCues() {
        KnowledgeEntity olek = KnowledgeEntity.builder()
                .id(UUID.randomUUID()).displayName("Olek").type("PERSON").build();
        EntityMention mention = EntityMention.builder()
                .id(UUID.randomUUID())
                .entity(olek)
                .label("Olek")
                .filePath("dir://a.jpg")
                .status(MentionStatus.CONFIRMED)
                .visualCues("[\"czarna kurtka\",\"krotkie wlosy\"]")
                .build();
        when(factRepository.findByFilePath("dir://a.jpg")).thenReturn(List.of());

        int created = service.backfillAppearanceForMention(mention);

        assertEquals(2, created);
        ArgumentCaptor<Fact> captor = ArgumentCaptor.forClass(Fact.class);
        verify(factRepository, org.mockito.Mockito.atLeast(2)).save(captor.capture());
        assertTrue(captor.getAllValues().stream()
                .allMatch(f -> FactStatementRewriter.ACTION_APPEARANCE.equals(f.getAction())));
        assertTrue(captor.getAllValues().stream()
                .anyMatch(f -> "czarna kurtka".equals(f.getObject())));
    }

    @Test
    void skipsCuesAlreadyPresentAsAppearanceFacts() {
        KnowledgeEntity olek = KnowledgeEntity.builder()
                .id(UUID.randomUUID()).displayName("Olek").type("PERSON").build();
        EntityMention mention = EntityMention.builder()
                .id(UUID.randomUUID())
                .entity(olek)
                .label("Olek")
                .filePath("dir://a.jpg")
                .status(MentionStatus.CONFIRMED)
                .visualCues("[\"czarna kurtka\"]")
                .build();
        Fact existing = Fact.builder()
                .id(UUID.randomUUID())
                .mention(mention)
                .action(FactStatementRewriter.ACTION_APPEARANCE)
                .object("czarna kurtka")
                .filePath("dir://a.jpg")
                .confidence(new BigDecimal("0.850"))
                .build();
        when(factRepository.findByFilePath("dir://a.jpg")).thenReturn(List.of(existing));

        int created = service.backfillAppearanceForMention(mention);

        assertEquals(0, created);
        verify(factRepository, never()).save(any());
    }
}

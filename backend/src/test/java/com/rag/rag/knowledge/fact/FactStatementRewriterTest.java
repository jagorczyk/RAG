package com.rag.rag.knowledge.fact;

import com.rag.rag.knowledge.entity.EntityMention;
import com.rag.rag.knowledge.entity.KnowledgeEntity;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FactStatementRewriterTest {

    @Mock FactRepository factRepository;
    @InjectMocks FactStatementRewriter rewriter;

    @Test
    void buildStatementUsesReadableAppearanceGlue() {
        assertEquals("Olek ma krótkie brązowe włosy.",
                FactStatementRewriter.buildStatement("Olek", FactStatementRewriter.ACTION_APPEARANCE,
                        "krótkie brązowe włosy"));
        assertEquals("Olek ma przy sobie nóż.",
                FactStatementRewriter.buildStatement("Olek", FactStatementRewriter.ACTION_RELATED_OBJECT, "nóż"));
        assertEquals("Olek trzyma nóż.",
                FactStatementRewriter.buildStatement("Olek", "trzyma nóż", null));
    }

    @Test
    void rewriteFactsForMentionReplacesPerson1Statements() {
        KnowledgeEntity olek = KnowledgeEntity.builder()
                .id(UUID.randomUUID()).displayName("Olek").type("PERSON").build();
        EntityMention mention = EntityMention.builder()
                .id(UUID.randomUUID())
                .entity(olek)
                .label("Olek")
                .visionLabel("person 1")
                .filePath("dir://a.jpg")
                .build();
        Fact stale = Fact.builder()
                .id(UUID.randomUUID())
                .mention(mention)
                .action("trzyma nóż")
                .object(null)
                .statementPl("person 1 trzyma nóż.")
                .filePath("dir://a.jpg")
                .confidence(new BigDecimal("0.900"))
                .build();
        Fact appearance = Fact.builder()
                .id(UUID.randomUUID())
                .mention(mention)
                .action(FactStatementRewriter.ACTION_APPEARANCE)
                .object("czarna kurtka")
                .statementPl("person 1 ma czarna kurtka.")
                .filePath("dir://a.jpg")
                .confidence(new BigDecimal("0.850"))
                .build();
        when(factRepository.findByFilePath("dir://a.jpg")).thenReturn(List.of(stale, appearance));

        rewriter.rewriteFactsForMention(mention);

        ArgumentCaptor<Fact> captor = ArgumentCaptor.forClass(Fact.class);
        verify(factRepository, org.mockito.Mockito.atLeast(2)).save(captor.capture());
        List<Fact> saved = captor.getAllValues();
        assertTrue(saved.stream().anyMatch(f -> "Olek trzyma nóż.".equals(f.getStatementPl())));
        assertTrue(saved.stream().anyMatch(f -> "Olek ma czarna kurtka.".equals(f.getStatementPl())
                || "Olek ma czarna kurtka".equals(f.getStatementPl().replace(".", ""))));
        assertEquals("Olek ma czarna kurtka.",
                FactStatementRewriter.buildStatement("Olek", FactStatementRewriter.ACTION_APPEARANCE, "czarna kurtka"));
    }
}

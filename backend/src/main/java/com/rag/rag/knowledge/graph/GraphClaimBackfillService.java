package com.rag.rag.knowledge.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.rag.knowledge.entity.EntityMention;
import com.rag.rag.knowledge.entity.MentionStatus;
import com.rag.rag.knowledge.fact.Fact;
import com.rag.rag.knowledge.fact.FactStatementRewriter;
import com.rag.rag.knowledge.repository.EntityMentionRepository;
import com.rag.rag.knowledge.repository.FactRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Lightweight graph upgrade without re-running vision:
 * materialize {@code HAS_APPEARANCE} facts from stored {@code visual_cues}
 * and rewrite {@code statement_pl} with canonical names.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GraphClaimBackfillService {

    private final EntityMentionRepository mentionRepository;
    private final FactRepository factRepository;
    private final FactStatementRewriter factStatementRewriter;
    private final MentionEvidencePolicy mentionEvidencePolicy;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * @return number of appearance facts created
     */
    @Transactional
    public int backfillAll() {
        int created = 0;
        int rewritten = 0;
        List<EntityMention> mentions = mentionRepository.findAll();
        for (EntityMention mention : mentions) {
            if (mention == null || mention.getStatus() == MentionStatus.REJECTED) {
                continue;
            }
            created += backfillAppearanceForMention(mention);
            if (mentionEvidencePolicy.isCertain(mention) || mention.getEntity() != null) {
                factStatementRewriter.rewriteFactsForMention(mention);
                rewritten++;
            }
        }
        log.info("Graph claim backfill: created {} appearance facts; rewrote statements for {} mentions",
                created, rewritten);
        return created;
    }

    @Transactional
    public int backfillAppearanceForMention(EntityMention mention) {
        if (mention == null || mention.getId() == null || mention.getFilePath() == null) {
            return 0;
        }
        List<String> cues = parseStringList(mention.getVisualCues());
        if (cues.isEmpty()) {
            return 0;
        }
        Set<String> existing = new HashSet<>();
        for (Fact fact : factRepository.findByFilePath(mention.getFilePath())) {
            if (fact.getMention() == null || !mention.getId().equals(fact.getMention().getId())) {
                continue;
            }
            if (!FactStatementRewriter.ACTION_APPEARANCE.equalsIgnoreCase(fact.getAction())) {
                continue;
            }
            if (fact.getObject() != null && !fact.getObject().isBlank()) {
                existing.add(fact.getObject().trim().toLowerCase(Locale.ROOT));
            }
        }
        int created = 0;
        for (String cue : cues) {
            if (cue == null || cue.isBlank()) {
                continue;
            }
            String key = cue.trim().toLowerCase(Locale.ROOT);
            if (existing.contains(key)) {
                continue;
            }
            String subject = FactStatementRewriter.displayName(mention);
            factRepository.save(Fact.builder()
                    .mention(mention)
                    .action(FactStatementRewriter.ACTION_APPEARANCE)
                    .object(cue.trim())
                    .statementPl(FactStatementRewriter.buildStatement(
                            subject, FactStatementRewriter.ACTION_APPEARANCE, cue.trim()))
                    .evidenceOrigin("VISION_STRUCTURED_BACKFILL")
                    .filePath(mention.getFilePath())
                    .confidence(new BigDecimal("0.850"))
                    .build());
            existing.add(key);
            created++;
        }
        return created;
    }

    private List<String> parseStringList(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String trimmed = raw.trim();
        try {
            if (trimmed.startsWith("[")) {
                JsonNode arr = objectMapper.readTree(trimmed);
                if (!arr.isArray()) {
                    return List.of();
                }
                java.util.ArrayList<String> out = new java.util.ArrayList<>();
                arr.forEach(node -> {
                    String v = node.asText("").trim();
                    if (!v.isBlank()) {
                        out.add(v);
                    }
                });
                return out;
            }
        } catch (Exception ignored) {
            // fall through
        }
        return List.of(trimmed);
    }
}

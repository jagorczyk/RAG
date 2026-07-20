package com.rag.rag.knowledge.graph;

import com.rag.rag.knowledge.entity.EntityMention;
import com.rag.rag.knowledge.entity.IdentityEvidenceSource;
import com.rag.rag.knowledge.entity.KnowledgeEntity;
import com.rag.rag.knowledge.entity.MentionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class MentionEvidencePolicyTest {
    private MentionEvidencePolicy policy;
    private KnowledgeEntity person;

    @BeforeEach
    void setUp() {
        policy = new MentionEvidencePolicy();
        ReflectionTestUtils.setField(policy, "minMentionConfidence", 0.75);
        ReflectionTestUtils.setField(policy, "faceMatchThreshold", 0.50);
        ReflectionTestUtils.setField(policy, "faceMatchMinMargin", 0.08);
        ReflectionTestUtils.setField(policy, "faceMinDetectionScore", 0.50);
        ReflectionTestUtils.setField(policy, "descriptionAutoConfirmThreshold", 0.85);
        person = KnowledgeEntity.builder().displayName("Bartek").type("PERSON").build();
    }

    @Test
    void manualConfirmationOverridesLowDetectorScoreWithoutChangingIt() {
        EntityMention mention = mention("0.736", IdentityEvidenceSource.USER, "1.000", null);
        assertTrue(policy.isCertain(mention));
        assertEquals(new BigDecimal("0.736"), mention.getConfidence());
        assertEquals(BigDecimal.ONE, policy.evidenceConfidence(mention));
    }

    @Test
    void legacyLowConfidenceAndFaceClusterRemainUncertain() {
        assertFalse(policy.isCertain(mention("0.736", null, null, null)));
        assertFalse(policy.isCertain(mention("0.900", IdentityEvidenceSource.FACE_CLUSTER, "0.700", null)));
    }

    @Test
    void acceptedFaceMatchRequiresDetectionIdentityAndMarginThresholds() {
        assertTrue(policy.isCertain(mention("0.736", IdentityEvidenceSource.FACE_MATCH, "0.620", "0.100")));
        assertFalse(policy.isCertain(mention("0.736", IdentityEvidenceSource.FACE_MATCH, "0.620", "0.040")));
    }

    @Test
    void visionAnimalPlaceholderIsNeverCertainEvenIfConfirmedDescriptionMatch() {
        KnowledgeEntity animal = KnowledgeEntity.builder().displayName("animal 1").type("ANIMAL").build();
        EntityMention mention = EntityMention.builder()
                .entity(animal)
                .label("animal 1")
                .entityType("ANIMAL")
                .status(MentionStatus.CONFIRMED)
                .confidence(new BigDecimal("0.900"))
                .identitySource(IdentityEvidenceSource.DESCRIPTION_MATCH)
                .identityConfidence(new BigDecimal("1.000"))
                .build();
        assertFalse(policy.isCertain(mention));
        assertEquals(BigDecimal.ZERO, policy.evidenceConfidence(mention));
    }

    @Test
    void realNamedAnimalCanBeCertainViaDescriptionMatch() {
        KnowledgeEntity animal = KnowledgeEntity.builder().displayName("Figa").type("ANIMAL").build();
        EntityMention mention = EntityMention.builder()
                .entity(animal)
                .label("Figa")
                .entityType("ANIMAL")
                .status(MentionStatus.CONFIRMED)
                .confidence(new BigDecimal("0.900"))
                .identitySource(IdentityEvidenceSource.DESCRIPTION_MATCH)
                .identityConfidence(new BigDecimal("1.000"))
                .build();
        assertTrue(policy.isCertain(mention));
    }

    @Test
    void faceMatchWithPlaceholderLabelAndRealEntityDisplayNameIsCertain() {
        EntityMention mention = EntityMention.builder()
                .entity(person)
                .label("person 1")
                .entityType("PERSON")
                .status(MentionStatus.CONFIRMED)
                .confidence(new BigDecimal("0.900"))
                .identitySource(IdentityEvidenceSource.FACE_MATCH)
                .identityConfidence(new BigDecimal("0.810"))
                .identityMargin(new BigDecimal("0.791"))
                .build();
        assertTrue(policy.isCertain(mention));
        assertEquals(new BigDecimal("0.810"), policy.evidenceConfidence(mention));
    }

    @Test
    void faceMatchWithPlaceholderEntityDisplayNameIsNotCertain() {
        KnowledgeEntity placeholder = KnowledgeEntity.builder()
                .displayName("person 1")
                .type("PERSON")
                .build();
        EntityMention mention = EntityMention.builder()
                .entity(placeholder)
                .label("person 1")
                .entityType("PERSON")
                .status(MentionStatus.CONFIRMED)
                .confidence(new BigDecimal("0.900"))
                .identitySource(IdentityEvidenceSource.FACE_MATCH)
                .identityConfidence(new BigDecimal("0.810"))
                .identityMargin(new BigDecimal("0.791"))
                .build();
        assertFalse(policy.isCertain(mention));
        assertEquals(BigDecimal.ZERO, policy.evidenceConfidence(mention));
    }

    @Test
    void faceMatchPlaceholderLabelStillRequiresThresholds() {
        EntityMention lowMargin = EntityMention.builder()
                .entity(person)
                .label("person 2")
                .status(MentionStatus.CONFIRMED)
                .confidence(new BigDecimal("0.900"))
                .identitySource(IdentityEvidenceSource.FACE_MATCH)
                .identityConfidence(new BigDecimal("0.620"))
                .identityMargin(new BigDecimal("0.040"))
                .build();
        assertFalse(policy.isCertain(lowMargin));
    }

    private EntityMention mention(String observation, IdentityEvidenceSource source,
                                  String identity, String margin) {
        return EntityMention.builder()
                .entity(person)
                .label("Bartek")
                .status(MentionStatus.CONFIRMED)
                .confidence(new BigDecimal(observation))
                .identitySource(source)
                .identityConfidence(identity == null ? null : new BigDecimal(identity))
                .identityMargin(margin == null ? null : new BigDecimal(margin))
                .build();
    }
}

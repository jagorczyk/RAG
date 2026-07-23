package com.rag.rag.knowledge.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@jakarta.persistence.Entity
@Table(name = "entity_mentions")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EntityMention {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id")
    private KnowledgeEntity entity;

    @Column(nullable = false)
    private String filePath;

    @Column(nullable = false)
    private String label;

    /** Stable label emitted by vision, retained when the user-facing identity changes. */
    @Column(name = "vision_label")
    private String visionLabel;

    /** Identifier of the detector bbox used to bind vision facts to a face. */
    @Column(name = "face_anchor_id")
    private String faceAnchorId;

    @Column(name = "entity_type")
    @Builder.Default
    private String entityType = "PERSON";

    @Column(nullable = false, precision = 4, scale = 3)
    private BigDecimal confidence;

    @Column(name = "identity_confidence", precision = 4, scale = 3)
    private BigDecimal identityConfidence;

    @Column(name = "identity_margin", precision = 4, scale = 3)
    private BigDecimal identityMargin;

    @Enumerated(EnumType.STRING)
    @Column(name = "identity_source")
    private IdentityEvidenceSource identitySource;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private MentionStatus status = MentionStatus.SUGGESTED;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String visualCues;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String contextObjects;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String nearbyText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String bbox;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}

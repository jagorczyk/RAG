package com.rag.rag.knowledge.face;

import com.rag.rag.knowledge.entity.EntityMention;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "face_observations")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FaceObservation {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mention_id", nullable = false)
    private EntityMention mention;

    @Column(nullable = false)
    private String filePath;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private float[] embedding;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private float[] bbox;

    @Column(precision = 4, scale = 3)
    private BigDecimal detScore;

    @Column(nullable = false)
    @Builder.Default
    private String status = "PENDING";

    private String rejectionReason;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}

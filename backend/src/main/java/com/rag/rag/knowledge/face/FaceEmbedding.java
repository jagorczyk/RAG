package com.rag.rag.knowledge.face;

import com.rag.rag.knowledge.entity.EntityMention;
import com.rag.rag.knowledge.entity.KnowledgeEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "face_embeddings")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FaceEmbedding {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id")
    private KnowledgeEntity entity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mention_id")
    private EntityMention mention;

    @Column(nullable = false)
    private String filePath;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private float[] embedding;

    @Column(name = "embedding_vector", columnDefinition = "vector(512)")
    @ColumnTransformer(write = "?::vector")
    private String embeddingVector;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private float[] bbox;

    @Column(precision = 4, scale = 3)
    private BigDecimal detScore;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}

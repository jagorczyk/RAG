package com.rag.rag.knowledge.fact;

import com.rag.rag.knowledge.entity.EntityMention;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@jakarta.persistence.Entity
@Table(name = "facts")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Fact {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mention_id", nullable = false)
    private EntityMention mention;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_mention_id")
    private EntityMention targetMention;

    @Column(nullable = false)
    private String action;

    private String object;

    @Column(nullable = false)
    private String filePath;

    @Column(nullable = false, precision = 4, scale = 3)
    private BigDecimal confidence;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}

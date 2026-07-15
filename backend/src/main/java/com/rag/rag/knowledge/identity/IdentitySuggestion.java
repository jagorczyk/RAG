package com.rag.rag.knowledge.identity;

import com.rag.rag.knowledge.entity.EntityMention;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@jakarta.persistence.Entity
@Table(name = "identity_suggestions")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class IdentitySuggestion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mention_id_a", nullable = false)
    private EntityMention mentionA;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mention_id_b", nullable = false)
    private EntityMention mentionB;

    @Column(nullable = false, precision = 4, scale = 3)
    private BigDecimal similarityScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private SuggestionStatus status = SuggestionStatus.PENDING;
}

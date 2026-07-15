package com.rag.rag.knowledge.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@jakarta.persistence.Entity
@Table(name = "entity_aliases")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EntityAlias {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id", nullable = false)
    private KnowledgeEntity entity;

    @Column(nullable = false)
    private String alias;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private AliasSource source = AliasSource.USER;
}

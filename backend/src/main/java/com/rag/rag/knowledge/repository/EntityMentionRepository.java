package com.rag.rag.knowledge.repository;

import com.rag.rag.knowledge.entity.EntityMention;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Collection;
import java.util.UUID;

@Repository
public interface EntityMentionRepository extends JpaRepository<EntityMention, UUID> {

    @EntityGraph(attributePaths = "entity")
    List<EntityMention> findByFilePath(String filePath);

    @EntityGraph(attributePaths = "entity")
    List<EntityMention> findByFilePathIn(Collection<String> filePaths);

    @EntityGraph(attributePaths = "entity")
    List<EntityMention> findByEntityId(UUID entityId);

    /**
     * Mentions already linked to a living entity of the given type for one owner.
     * Used by identity resolution to avoid full-table {@code findAll()}.
     */
    @EntityGraph(attributePaths = "entity")
    @Query("""
            SELECT m FROM EntityMention m
            JOIN m.entity e
            WHERE UPPER(e.type) = UPPER(:type)
              AND e.ownerId = :ownerId
            """)
    List<EntityMention> findLinkedByEntityTypeAndOwner(@Param("type") String type, @Param("ownerId") UUID ownerId);

    @EntityGraph(attributePaths = "entity")
    @Query("""
            SELECT m FROM EntityMention m
            JOIN m.entity e
            WHERE UPPER(e.type) = UPPER(:type)
              AND e.ownerId IS NULL
            """)
    List<EntityMention> findLinkedByEntityTypeWithoutOwner(@Param("type") String type);

    void deleteByFilePath(String filePath);

    void deleteByEntityId(UUID entityId);
}

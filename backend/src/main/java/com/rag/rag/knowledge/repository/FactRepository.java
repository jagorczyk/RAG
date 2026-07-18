package com.rag.rag.knowledge.repository;

import com.rag.rag.knowledge.fact.Fact;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

@Repository
public interface FactRepository extends JpaRepository<Fact, UUID> {

    void deleteByFilePath(String filePath);

    @EntityGraph(attributePaths = {"mention", "mention.entity", "targetMention", "targetMention.entity"})
    List<Fact> findByFilePath(String filePath);

    @Modifying
    @Query("DELETE FROM Fact f WHERE f.mention.id IN :mentionIds OR f.targetMention.id IN :mentionIds")
    void deleteByMentionIds(@Param("mentionIds") Collection<UUID> mentionIds);

    @Query("SELECT COUNT(f) > 0 FROM Fact f WHERE f.mention.id = :mentionId OR f.targetMention.id = :mentionId")
    boolean existsByMentionOrTargetMentionId(@Param("mentionId") UUID mentionId);

    @EntityGraph(attributePaths = {"mention", "mention.entity", "targetMention", "targetMention.entity"})
    @Query("SELECT f FROM Fact f")
    List<Fact> findAllWithMentionAndEntity();
}

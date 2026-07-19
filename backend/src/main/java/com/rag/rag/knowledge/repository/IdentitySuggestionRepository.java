package com.rag.rag.knowledge.repository;

import com.rag.rag.knowledge.identity.IdentitySuggestion;
import com.rag.rag.knowledge.identity.SuggestionStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IdentitySuggestionRepository extends JpaRepository<IdentitySuggestion, UUID> {

    @EntityGraph(attributePaths = {"mentionA", "mentionA.entity", "mentionB", "mentionB.entity"})
    @Query("SELECT s FROM IdentitySuggestion s WHERE s.status = :status")
    List<IdentitySuggestion> findAllByStatusWithMentions(@Param("status") SuggestionStatus status);

    @EntityGraph(attributePaths = {"mentionA", "mentionA.entity", "mentionB", "mentionB.entity"})
    @Query("SELECT s FROM IdentitySuggestion s WHERE s.id = :id")
    Optional<IdentitySuggestion> findByIdWithMentions(@Param("id") UUID id);

    @Modifying
    @Query("DELETE FROM IdentitySuggestion s WHERE s.mentionA.id IN :mentionIds OR s.mentionB.id IN :mentionIds")
    void deleteByMentionIds(@Param("mentionIds") Collection<UUID> mentionIds);

    @Query("""
            SELECT COUNT(s) > 0 FROM IdentitySuggestion s
            WHERE (s.mentionA.id = :mentionAId AND s.mentionB.id = :mentionBId)
               OR (s.mentionA.id = :mentionBId AND s.mentionB.id = :mentionAId)
            """)
    boolean existsBetweenMentions(@Param("mentionAId") UUID mentionAId, @Param("mentionBId") UUID mentionBId);
}

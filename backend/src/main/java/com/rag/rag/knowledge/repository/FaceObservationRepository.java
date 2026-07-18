package com.rag.rag.knowledge.repository;

import com.rag.rag.knowledge.face.FaceObservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface FaceObservationRepository extends JpaRepository<FaceObservation, UUID> {
    boolean existsByFilePath(String filePath);
    @EntityGraph(attributePaths = {"mention", "mention.entity"})
    List<FaceObservation> findByFilePath(String filePath);
    Optional<FaceObservation> findFirstByMentionIdAndStatus(UUID mentionId, String status);

    @Modifying
    @Query("DELETE FROM FaceObservation fo WHERE fo.filePath = :filePath")
    void deleteByFilePath(@Param("filePath") String filePath);

    @Modifying
    @Query("DELETE FROM FaceObservation fo WHERE fo.mention.id IN :mentionIds")
    void deleteByMentionIds(@Param("mentionIds") Collection<UUID> mentionIds);
}

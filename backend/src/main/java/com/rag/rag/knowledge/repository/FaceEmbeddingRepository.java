package com.rag.rag.knowledge.repository;

import com.rag.rag.knowledge.face.FaceEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FaceEmbeddingRepository extends JpaRepository<FaceEmbedding, UUID> {

    List<FaceEmbedding> findByEntityId(UUID entityId);

    List<FaceEmbedding> findByFilePath(String filePath);

    java.util.Optional<FaceEmbedding> findFirstByMention_Id(UUID mentionId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM FaceEmbedding fe WHERE fe.filePath = :filePath")
    void deleteByFilePath(@Param("filePath") String filePath);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM FaceEmbedding fe WHERE fe.mention.id IN :mentionIds")
    void deleteByMentionIdIn(@Param("mentionIds") List<UUID> mentionIds);

    @Query("SELECT fe FROM FaceEmbedding fe WHERE fe.filePath <> :filePath")
    List<FaceEmbedding> findAllExceptFilePath(@Param("filePath") String filePath);
}

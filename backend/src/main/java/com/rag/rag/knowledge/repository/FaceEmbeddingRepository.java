package com.rag.rag.knowledge.repository;

import com.rag.rag.knowledge.face.FaceEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import java.math.BigDecimal;

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

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE FaceEmbedding fe SET fe.entity = :target WHERE fe.mention.id = :mentionId")
    int relinkByMentionId(
            @Param("mentionId") UUID mentionId,
            @Param("target") com.rag.rag.knowledge.entity.KnowledgeEntity target
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE FaceEmbedding fe SET fe.entity = :target WHERE fe.entity.id = :sourceId")
    int relinkEntity(@Param("sourceId") UUID sourceId, @Param("target") com.rag.rag.knowledge.entity.KnowledgeEntity target);

    @Query("""
            SELECT fe FROM FaceEmbedding fe
            JOIN FETCH fe.entity e
            JOIN FETCH fe.mention m
            WHERE fe.filePath <> :filePath
              AND UPPER(e.type) = 'PERSON'
              AND m.status = com.rag.rag.knowledge.entity.MentionStatus.CONFIRMED
            """)
    List<FaceEmbedding> findAllExceptFilePath(@Param("filePath") String filePath);

    @Query("""
            SELECT fe FROM FaceEmbedding fe
            JOIN FETCH fe.entity e
            JOIN FETCH fe.mention m
            WHERE UPPER(e.type) = 'PERSON'
              AND m.status = com.rag.rag.knowledge.entity.MentionStatus.CONFIRMED
            """)
    List<FaceEmbedding> findAllConfirmedGallery();

    @Query(value = """
            SELECT fe.* FROM face_embeddings fe
            JOIN entities e ON e.id = fe.entity_id
            JOIN entity_mentions em ON em.id = fe.mention_id
            WHERE (:excludeFilePath IS NULL OR fe.file_path <> :excludeFilePath)
              AND fe.embedding_vector IS NOT NULL
              AND UPPER(e.type) = 'PERSON'
              AND em.status = 'CONFIRMED'
              AND fe.det_score >= :minDetScore
            ORDER BY fe.embedding_vector <=> CAST(:embeddingLiteral AS vector)
            LIMIT :topK
            """, nativeQuery = true)
    List<FaceEmbedding> findTopKByVectorDistance(
            @Param("embeddingLiteral") String embeddingLiteral,
            @Param("excludeFilePath") String excludeFilePath,
            @Param("minDetScore") BigDecimal minDetScore,
            @Param("topK") int topK
    );
}

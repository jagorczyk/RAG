package com.rag.rag.knowledge.repository;

import com.rag.rag.knowledge.face.FaceEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FaceEmbeddingRepository extends JpaRepository<FaceEmbedding, UUID> {

    List<FaceEmbedding> findByEntityId(UUID entityId);

    void deleteByFilePath(String filePath);
}

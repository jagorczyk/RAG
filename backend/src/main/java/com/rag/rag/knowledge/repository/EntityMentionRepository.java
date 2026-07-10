package com.rag.rag.knowledge.repository;

import com.rag.rag.knowledge.entity.EntityMention;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EntityMentionRepository extends JpaRepository<EntityMention, UUID> {

    List<EntityMention> findByFilePath(String filePath);

    List<EntityMention> findByEntityId(UUID entityId);

    void deleteByFilePath(String filePath);
}

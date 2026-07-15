package com.rag.rag.knowledge.repository;

import com.rag.rag.knowledge.entity.KnowledgeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface KnowledgeEntityRepository extends JpaRepository<KnowledgeEntity, UUID> {

    Optional<KnowledgeEntity> findFirstByDisplayNameIgnoreCase(String displayName);

    Optional<KnowledgeEntity> findFirstByDisplayNameIgnoreCaseAndTypeIgnoreCase(String displayName, String type);
}

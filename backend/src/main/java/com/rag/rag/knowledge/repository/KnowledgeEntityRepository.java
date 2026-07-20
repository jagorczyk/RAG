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

    Optional<KnowledgeEntity> findFirstByDisplayNameIgnoreCaseAndTypeIgnoreCaseAndOwnerId(
            String displayName, String type, UUID ownerId);

    Optional<KnowledgeEntity> findFirstByDisplayNameIgnoreCaseAndOwnerId(String displayName, UUID ownerId);

    Optional<KnowledgeEntity> findByIdAndOwnerId(UUID id, UUID ownerId);

    List<KnowledgeEntity> findAllByOwnerId(UUID ownerId);
}

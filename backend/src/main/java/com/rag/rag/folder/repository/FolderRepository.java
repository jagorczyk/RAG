package com.rag.rag.folder.repository;

import com.rag.rag.folder.entity.FolderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FolderRepository extends JpaRepository<FolderEntity, UUID> {
    List<FolderEntity> findAllByOwnerIdOrderByUpdatedAtDesc(UUID ownerId);

    Optional<FolderEntity> findByIdAndOwnerId(UUID id, UUID ownerId);

    void deleteByIdAndOwnerId(UUID id, UUID ownerId);
}

package com.rag.rag.folder.repository;

import com.rag.rag.folder.entity.FileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FileRepository extends JpaRepository<FileEntity, Long> {
    Optional<FileEntity> findByPath(String path);

    Optional<FileEntity> findByPathAndOwnerId(String path, UUID ownerId);

    List<FileEntity> findAllByOwnerId(UUID ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM FileEntity f WHERE f.path = :path")
    Optional<FileEntity> findByPathForUpdate(@Param("path") String path);

    Optional<FileEntity> findFirstByFileNameIgnoreCase(String fileName);
}

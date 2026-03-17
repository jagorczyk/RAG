package com.rag.rag.Repository;

import com.rag.rag.Entity.FolderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FolderRepository extends JpaRepository<FolderEntity, UUID> {}

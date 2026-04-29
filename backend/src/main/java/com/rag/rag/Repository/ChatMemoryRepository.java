package com.rag.rag.Repository;

import com.rag.rag.Entity.ChatMemoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatMemoryRepository extends JpaRepository<ChatMemoryEntity, UUID> {
    List<ChatMemoryEntity> findAllByOrderByLastMessageAtDesc();
}

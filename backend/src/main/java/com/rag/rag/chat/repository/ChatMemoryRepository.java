package com.rag.rag.chat.repository;

import com.rag.rag.chat.entity.ChatMemoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatMemoryRepository extends JpaRepository<ChatMemoryEntity, UUID> {
    List<ChatMemoryEntity> findAllByOrderByLastMessageAtDesc();

    List<ChatMemoryEntity> findAllByOwnerIdOrderByLastMessageAtDesc(UUID ownerId);

    Optional<ChatMemoryEntity> findByChatIdAndOwnerId(UUID chatId, UUID ownerId);
}

package com.rag.rag.Repository;

import com.rag.rag.Entity.ChatMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, UUID> {
    List<ChatMessageEntity> findAllByChatIdOrderByCreatedAtAsc(UUID chatId);
}

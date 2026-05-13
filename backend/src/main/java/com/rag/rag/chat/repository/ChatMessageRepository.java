package com.rag.rag.chat.repository;

import com.rag.rag.chat.entity.ChatMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, UUID> {
    List<ChatMessageEntity> findAllByChatIdOrderByCreatedAtAsc(UUID chatId);
}

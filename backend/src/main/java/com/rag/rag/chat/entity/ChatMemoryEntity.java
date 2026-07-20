package com.rag.rag.chat.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "conversations")
@Data
@Builder
public class ChatMemoryEntity {

    @Id
    @Column(name = "chat_id")
    private UUID chatId;

    @Column(name = "messages", columnDefinition = "TEXT")
    private String messages;

    @Column(name = "name")
    private String name;

    @Column(name = "owner_id")
    private UUID ownerId;

    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;

    public ChatMemoryEntity() {}

    public ChatMemoryEntity(UUID chatId, String messages, String name, LocalDateTime lastMessageAt) {
        this.chatId = chatId;
        this.messages = messages;
        this.name = name;
        this.lastMessageAt = lastMessageAt;
    }

    public ChatMemoryEntity(UUID chatId, String messages, String name, UUID ownerId, LocalDateTime lastMessageAt) {
        this.chatId = chatId;
        this.messages = messages;
        this.name = name;
        this.ownerId = ownerId;
        this.lastMessageAt = lastMessageAt;
    }

    @PrePersist
    public void prePersist() {
        if (this.name == null && this.chatId != null) {
            this.name = this.chatId.toString();
        }
        if (this.lastMessageAt == null) {
            this.lastMessageAt = LocalDateTime.now();
        }
    }
}

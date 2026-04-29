package com.rag.rag.Entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "conversations")
public class ChatMemoryEntity {

    @Id
    @Column(name = "chat_id")
    private UUID chatId;

    @Column(name = "messages", columnDefinition = "TEXT")
    private String messages;

    @Column(name = "name")
    private String name;

    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;

    public ChatMemoryEntity() {}

    public ChatMemoryEntity(UUID chatId, String messages, String name, LocalDateTime lastMessageAt) {
        this.chatId = chatId;
        this.messages = messages;
        this.name = name;
        this.lastMessageAt = lastMessageAt;
    }

    public UUID getChatId() { return chatId; }
    public void setChatId(UUID chatId) { this.chatId = chatId; }

    public String getMessages() { return messages; }
    public void setMessages(String messages) { this.messages = messages; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public LocalDateTime getLastMessageAt() { return lastMessageAt; }
    public void setLastMessageAt(LocalDateTime lastMessageAt) { this.lastMessageAt = lastMessageAt; }

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

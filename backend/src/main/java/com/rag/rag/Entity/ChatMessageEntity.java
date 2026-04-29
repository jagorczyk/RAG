package com.rag.rag.Entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "messages")
public class ChatMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_id")
    private UUID chatId;

    private String role;

    @Column(columnDefinition = "TEXT")
    private String textContext;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "image_paths", columnDefinition = "jsonb")
    private List<String> imagePaths;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "scores", columnDefinition = "jsonb")
    private List<Double> scores;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public ChatMessageEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getChatId() { return chatId; }
    public void setChatId(UUID chatId) { this.chatId = chatId; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getTextContext() { return textContext; }
    public void setTextContext(String textContext) { this.textContext = textContext; }

    public List<String> getImagePaths() { return imagePaths; }
    public void setImagePaths(List<String> imagePaths) { this.imagePaths = imagePaths; }

    public List<Double> getScores() { return scores; }
    public void setScores(List<Double> scores) { this.scores = scores; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}

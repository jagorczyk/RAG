package com.rag.rag.chat.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "messages")
@Builder
@Data
@AllArgsConstructor
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

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}

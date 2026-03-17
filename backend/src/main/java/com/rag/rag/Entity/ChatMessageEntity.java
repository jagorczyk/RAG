package com.rag.rag.Entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name="chat_messages")
@NoArgsConstructor
@AllArgsConstructor
@Data
public class ChatMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "chat_id", nullable = false)
    private UUID chatId;

    @Column(name = "role", nullable = false)
    private String role;

    @Column(name="text_context", columnDefinition = "TEXT") 
    private String textContext;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name="image_paths", columnDefinition = "jsonb")
    private List<String> imagePaths;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name="scores", columnDefinition = "jsonb")
    private List<Double> scores;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}

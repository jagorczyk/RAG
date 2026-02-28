package com.rag.rag.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "conversations")
@NoArgsConstructor
@AllArgsConstructor
@Data
public class ChatMemoryEntity {

    @Id
    @Column(name = "chat_id")
    private UUID chatId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "messages", columnDefinition = "jsonb")
    private String messages;
}

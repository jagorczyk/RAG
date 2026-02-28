package com.rag.rag.Controller;

import com.rag.rag.Dto.MessageRequest;
import com.rag.rag.Service.ChatService;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;
    private final JdbcTemplate jdbcTemplate;

    public ChatController(ChatService chatService, JdbcTemplate jdbcTemplate) {
        this.chatService = chatService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @DeleteMapping("/clear")
    public ResponseEntity<String> clearEmbeddingsTable() {
        try {
            jdbcTemplate.execute("TRUNCATE TABLE embeddings");
            return ResponseEntity.ok("Truncated embeddings.");
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("Error truncating: " + e.getMessage());
        }
    }

    @PostMapping("/create")
    public UUID createChat() {
        return UUID.randomUUID();
    }

    @PostMapping("/{chatId}/send")
    public String chat(@PathVariable UUID chatId, @RequestBody MessageRequest messageRequest) {
        return chatService.answer(chatId, messageRequest.message());
    }
}

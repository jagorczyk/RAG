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

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
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

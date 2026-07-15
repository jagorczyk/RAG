package com.rag.rag.chat.controller;

import com.rag.rag.chat.dto.ChatMessageDto;
import com.rag.rag.chat.dto.ChatRenameDto;
import com.rag.rag.chat.dto.MessageRequest;
import com.rag.rag.chat.entity.ChatMemoryEntity;
import com.rag.rag.chat.repository.ChatMemoryRepository;
import com.rag.rag.chat.repository.ChatMessageRepository;
import com.rag.rag.chat.service.ChatInteractionService;
import com.rag.rag.ingestion.dto.SourceDto;
import com.rag.rag.ingestion.service.IngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import java.time.LocalDateTime;

@Slf4j
@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class ChatController {

    private final ChatMemoryRepository chatMemoryRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final IngestionService ingestionService;
    private final ChatInteractionService chatInteractionService;

    @GetMapping("/all")
    public List<UUID> getAllChats() {
        return chatMemoryRepository.findAllByOrderByLastMessageAtDesc().stream()
                .map(ChatMemoryEntity::getChatId)
                .toList();
    }

    public record ChatSummary(String id, String name, LocalDateTime updatedAt) {}

    @GetMapping("/summaries")
    public List<ChatSummary> getChatSummaries() {
        return chatMemoryRepository.findAllByOrderByLastMessageAtDesc().stream()
                .map(chat -> new ChatSummary(chat.getChatId().toString(), chat.getName(), chat.getLastMessageAt()))
                .toList();
    }

    @Transactional
    @PostMapping("/create")
    public ResponseEntity<?> createChat() {
        try {
            UUID chatId = UUID.randomUUID();
            ChatMemoryEntity entity = new ChatMemoryEntity();
            entity.setChatId(chatId);
            entity.setMessages("[]");
            entity.setName(chatId.toString());
            chatMemoryRepository.save(entity);
            return ResponseEntity.ok(Map.of("id", chatId.toString()));
        } catch (Exception e) {
            log.error("Error creating chat", e);
            return ResponseEntity.internalServerError().body("An error occurred while creating the chat.");
        }
    }

    @Transactional(readOnly = true)
    @GetMapping("/{chatId}/messages")
    public List<ChatMessageDto> getMessages(@PathVariable UUID chatId) {
        return chatMessageRepository.findAllByChatIdOrderByCreatedAtAsc(chatId).stream()
                .map(entity -> {
                    List<SourceDto> sources = List.of();
                    if (entity.getImagePaths() != null) {
                        List<String> paths = entity.getImagePaths();
                        sources = IntStream.range(0, paths.size())
                                .mapToObj(i -> ingestionService.createSourceDto(paths.get(i), null, entity.getScores().get(i)))
                                .toList();
                    }
                    return new ChatMessageDto(entity.getTextContext(), entity.getRole(), sources, entity.getEvidence(),
                            Boolean.TRUE.equals(entity.getUncertain()),
                            entity.getAnswerKind() == null ? "DOCUMENT" : entity.getAnswerKind());
                })
                .toList();
    }

    @PostMapping("/{chatId}/send")
    public ResponseEntity<?> chat(@PathVariable UUID chatId, @RequestBody MessageRequest messageRequest) {
        if (messageRequest.message() == null || messageRequest.message().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Message content cannot be empty");
        }

        try {
            return ResponseEntity.ok(chatInteractionService.processChatMessage(chatId, messageRequest));
        } catch (Exception e) {
            log.error("Chat processing failed for {}", chatId, e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Chat processing failed",
                    "detail", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()
            ));
        }
    }

    @PostMapping("/{chatId}/rename")
    public ResponseEntity<?> rename(@PathVariable UUID chatId, @RequestBody ChatRenameDto chatRenameDto) {
        return chatMemoryRepository.findById(chatId)
                .map(chat -> {
                    chat.setName(chatRenameDto.newName());
                    chatMemoryRepository.save(chat);
                    return ResponseEntity.ok().build();
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}

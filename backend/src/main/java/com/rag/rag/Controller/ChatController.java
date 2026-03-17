package com.rag.rag.Controller;

import com.rag.rag.Dto.ChatMessageDto;
import com.rag.rag.Dto.MessageRequest;
import com.rag.rag.Dto.MessageResponse;
import com.rag.rag.Dto.SourceDto;
import com.rag.rag.Entity.ChatMemoryEntity;
import com.rag.rag.Entity.ChatMessageEntity;
import com.rag.rag.Repository.ChatMemoryRepository;
import com.rag.rag.Repository.ChatMessageRepository;
import com.rag.rag.Repository.ImageRepository;
import com.rag.rag.Service.ChatMemoryService;
import com.rag.rag.Service.ChatService;
import com.rag.rag.Service.IngestionService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.service.Result;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")
public class ChatController {

    private final ChatService chatService;
    private final ImageRepository imageRepository;
    private final ChatMemoryRepository chatMemoryRepository;
    private final ChatMemoryService chatMemoryService;
    private final ChatMessageRepository chatMessageRepository;
    private final IngestionService ingestionService;

    public ChatController(
            ChatService chatService,
            ImageRepository imageRepository,
            ChatMemoryRepository chatMemoryRepository,
            ChatMemoryService chatMemoryService,
            ChatMessageRepository chatMessageRepository,
            IngestionService ingestionService
    ) {
        this.chatService = chatService;
        this.imageRepository = imageRepository;
        this.chatMemoryRepository = chatMemoryRepository;
        this.chatMemoryService = chatMemoryService;
        this.chatMessageRepository = chatMessageRepository;
        this.ingestionService = ingestionService;
    }

    @GetMapping("/all")
    public List<UUID> getAllChats() {
        return chatMemoryRepository.findAll().stream()
                .map(ChatMemoryEntity::getChatId)
                .toList();
    }

    @PostMapping("/create")
    public UUID createChat() {
        UUID chatId = UUID.randomUUID();
        chatMemoryService.updateMessages(chatId, new ArrayList<>());
        return chatId;
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
                                .mapToObj(i -> {
                                    String path = paths.get(i);
                                    Double score = entity.getScores().get(i);

                                    return ingestionService.createSourceDto(
                                            path,
                                            null,
                                            score
                                    );
                                })
                                .toList();
                    }

                    return new ChatMessageDto(entity.getTextContext(), entity.getRole(), sources);
                })
                .toList();
    }

    @Transactional()
    @PostMapping("/{chatId}/send")
    public MessageResponse chat(@PathVariable UUID chatId, @RequestBody MessageRequest messageRequest) {
        ChatMessageEntity userMsg = new ChatMessageEntity();
        userMsg.setChatId(chatId);
        userMsg.setRole("USER");
        userMsg.setTextContext(messageRequest.message());
        userMsg.setImagePaths(List.of());
        userMsg.setScores(List.of());
        chatMessageRepository.save(userMsg);

        Result<String> result = chatService.answer(chatId, messageRequest.message());

        System.out.println(
                result.sources().stream()
                        .map(n -> n.textSegment().metadata().getDouble("score"))
                        .toList()
        );

        List<String> paths = result.sources().stream()
                .map(source -> source.textSegment().metadata().getString("path"))
                .toList();

        List<Double> scores = result.sources().stream()
                .map(source -> source.textSegment().metadata().getDouble("score"))
                .toList();

        ChatMessageEntity aiMsg = new ChatMessageEntity();
        aiMsg.setChatId(chatId);
        aiMsg.setRole("AI");
        aiMsg.setTextContext(result.content());
        aiMsg.setImagePaths(paths);
        aiMsg.setScores(scores);
        chatMessageRepository.save(aiMsg);

        List<SourceDto> sources = ingestionService.getSources(result);
        return new MessageResponse(result.content(), sources);
    }
}

package com.rag.rag.Controller;

import com.rag.rag.Dto.*;
import com.rag.rag.Entity.ChatMemoryEntity;
import com.rag.rag.Entity.ChatMessageEntity;
import com.rag.rag.Repository.ChatMemoryRepository;
import com.rag.rag.Repository.ChatMessageRepository;
import com.rag.rag.Service.ChatService;
import com.rag.rag.Service.IngestionService;
import dev.langchain4j.service.Result;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.IntStream;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")
public class ChatController {

    private final ChatService chatService;
    private final ChatMemoryRepository chatMemoryRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final IngestionService ingestionService;

    public ChatController(
            ChatService chatService,
            ChatMemoryRepository chatMemoryRepository,
            ChatMessageRepository chatMessageRepository,
            IngestionService ingestionService
    ) {
        this.chatService = chatService;
        this.chatMemoryRepository = chatMemoryRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.ingestionService = ingestionService;
    }

    @GetMapping("/all")
    public List<UUID> getAllChats() {
        return chatMemoryRepository.findAllByOrderByLastMessageAtDesc().stream()
                .map(ChatMemoryEntity::getChatId)
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
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(e.getMessage());
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
                                .mapToObj(i -> {
                                    String path = paths.get(i);
                                    Double score = entity.getScores().get(i);
                                    return ingestionService.createSourceDto(path, null, score);
                                })
                                .toList();
                    }

                    return new ChatMessageDto(entity.getTextContext(), entity.getRole(), sources);
                })
                .toList();
    }

    @PostMapping("/{chatId}/send")
    public ResponseEntity<?> chat(@PathVariable UUID chatId, @RequestBody MessageRequest messageRequest) {
        if (messageRequest.message() == null || messageRequest.message().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Message content cannot be empty");
        }

        ChatMessageEntity userMsg = new ChatMessageEntity();
        userMsg.setChatId(chatId);
        userMsg.setRole("USER");
        userMsg.setTextContext(messageRequest.message());
        userMsg.setImagePaths(List.of());
        userMsg.setScores(List.of());
        chatMessageRepository.save(userMsg);

        chatMemoryRepository.findById(chatId).ifPresent(chat -> {
            chat.setLastMessageAt(LocalDateTime.now());
            chatMemoryRepository.save(chat);
        });

        Result<String> result = chatService.answer(chatId, messageRequest.message());

        String aiResponse = result.content();
        System.out.println("AI RESPONSE: [" + aiResponse + "]");

        if (aiResponse == null || aiResponse.trim().isEmpty()) {
            aiResponse = "Przepraszam, model zwrócił pustą odpowiedź.";
        }

        boolean noInfo = aiResponse.toLowerCase().contains("nie znaleziono informacji") ||
                aiResponse.trim().isEmpty();

        // Filtruj źródła na podstawie @wzmianek w ODPOWIEDZI modelu (nie w pytaniu)
        // Model ma instrukcję żeby cytować źródła jako @ścieżka — wyciągamy tylko te
        List<SourceDto> filteredSources;
        if (noInfo) {
            filteredSources = List.of();
        } else {
            List<SourceDto> allSources = ingestionService.getSources(result);

            // Wyciągnij @wzmianki z odpowiedzi modelu
            List<String> mentionedInResponse = new ArrayList<>();
            java.util.regex.Matcher responseMatcher = java.util.regex.Pattern
                    .compile("@([^\\s,\\]\\)]+)")
                    .matcher(aiResponse);
            while (responseMatcher.find()) {
                mentionedInResponse.add(responseMatcher.group(1).toLowerCase());
            }
            System.out.println("MENTIONS IN RESPONSE: " + mentionedInResponse);

            if (!mentionedInResponse.isEmpty()) {
                // Pokaż tylko źródła które model faktycznie wymienił w odpowiedzi
                filteredSources = allSources.stream()
                        .filter(s -> mentionedInResponse.stream().anyMatch(m ->
                                s.path().toLowerCase().contains(m) ||
                                        s.fileName().toLowerCase().contains(m)))
                        .toList();
            } else {
                // Model nie użył @wzmianek — pokaż wszystkie źródła jako fallback
                filteredSources = allSources;
            }
            System.out.println("FILTERED SOURCES COUNT: " + filteredSources.size());
        }

        List<String> finalPaths = filteredSources.stream().map(SourceDto::path).toList();
        List<Double> finalScores = filteredSources.stream().map(SourceDto::score).toList();

        ChatMessageEntity aiMsg = new ChatMessageEntity();
        aiMsg.setChatId(chatId);
        aiMsg.setRole("AI");
        aiMsg.setTextContext(aiResponse);
        aiMsg.setImagePaths(finalPaths);
        aiMsg.setScores(finalScores);
        chatMessageRepository.save(aiMsg);

        return ResponseEntity.ok(new MessageResponse(aiResponse, filteredSources));
    }

    @PostMapping("/{chatId}/rename")
    public ResponseEntity<?> rename(@PathVariable UUID chatId, @RequestBody ChatRenameDto chatRenameDto) {
        ChatMemoryEntity chatMemoryEntity = chatMemoryRepository.findById(chatId).orElse(null);

        if (chatMemoryEntity == null) {
            return ResponseEntity.notFound().build();
        }

        chatMemoryEntity.setName(chatRenameDto.newName());
        chatMemoryRepository.save(chatMemoryEntity);
        return ResponseEntity.ok().build();
    }
}
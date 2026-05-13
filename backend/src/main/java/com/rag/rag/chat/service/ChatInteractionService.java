package com.rag.rag.chat.service;

import com.rag.rag.chat.dto.MessageRequest;
import com.rag.rag.chat.dto.MessageResponse;
import com.rag.rag.chat.entity.ChatMessageEntity;
import com.rag.rag.chat.repository.ChatMemoryRepository;
import com.rag.rag.chat.repository.ChatMessageRepository;
import com.rag.rag.ingestion.dto.SourceDto;
import com.rag.rag.ingestion.service.IngestionService;
import dev.langchain4j.service.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatInteractionService {

    private final ChatService chatAiService;
    private final ChatMemoryRepository chatMemoryRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final IngestionService ingestionService;

    private static final Pattern CITATION_PATTERN = Pattern.compile("@([^\\s,\\]\\)]+)");

    @Transactional
    public MessageResponse processChatMessage(UUID chatId, MessageRequest messageRequest) {
        ChatMessageEntity userMsg = ChatMessageEntity.builder()
                .chatId(chatId)
                .role("USER")
                .textContext(messageRequest.message())
                .imagePaths(List.of())
                .scores(List.of())
                .build();
        chatMessageRepository.save(userMsg);

        chatMemoryRepository.findById(chatId).ifPresent(chat -> {
            chat.setLastMessageAt(LocalDateTime.now());
            chatMemoryRepository.save(chat);
        });

        Result<String> result = chatAiService.answer(chatId, messageRequest.message());
        String aiResponse = result.content();
        
        log.info("AI RESPONSE: [{}]", aiResponse);

        if (aiResponse == null || aiResponse.trim().isEmpty()) {
            aiResponse = "Przepraszam, model zwrócił pustą odpowiedź.";
        }

        List<SourceDto> filteredSources = extractSourcesFromResponse(result, aiResponse);
        
        String cleanedResponse = cleanUpAiResponse(aiResponse);

        saveAiMessage(chatId, cleanedResponse, filteredSources);

        return new MessageResponse(cleanedResponse, filteredSources);
    }

    private List<SourceDto> extractSourcesFromResponse(Result<String> result, String aiResponse) {
        boolean noInfo = aiResponse.toLowerCase().contains("nie znaleziono informacji") || aiResponse.trim().isEmpty();

        if (noInfo) {
            return List.of();
        }

        List<SourceDto> allSources = ingestionService.getSources(result);
        List<String> mentionedInResponse = new ArrayList<>();
        Matcher responseMatcher = CITATION_PATTERN.matcher(aiResponse);
        
        while (responseMatcher.find()) {
            mentionedInResponse.add(responseMatcher.group(1).toLowerCase());
        }
        
        log.info("MENTIONS IN RESPONSE: {}", mentionedInResponse);

        if (mentionedInResponse.isEmpty()) {
            return List.of();
        }

        Map<String, SourceDto> uniqueSources = new HashMap<>();
        allSources.stream()
                .filter(s -> isSourceMentioned(s, mentionedInResponse))
                .forEach(s -> uniqueSources.putIfAbsent(s.path(), s));

        List<SourceDto> filtered = new ArrayList<>(uniqueSources.values());
        log.info("FILTERED SOURCES COUNT: {}", filtered.size());
        return filtered;
    }

    private boolean isSourceMentioned(SourceDto source, List<String> mentions) {
        String lowerPath = source.path().toLowerCase();
        String lowerFileName = source.fileName().toLowerCase();

        return mentions.stream().anyMatch(m -> {
            if (m.length() < 3) return false;
            boolean fileNameMatch = lowerFileName.equals(m);
            boolean pathSuffixMatch = lowerPath.endsWith("/" + m);
            boolean partialMatch = m.contains(lowerFileName) || lowerFileName.contains(m);
            
            return fileNameMatch || pathSuffixMatch || (partialMatch && m.length() > 5);
        });
    }

    private String cleanUpAiResponse(String aiResponse) {
        String cleaned = aiResponse.replaceAll("@([^\\s,\\]\\)]+)", "").trim();
        return cleaned.replaceAll(" +", " ").replaceAll("(?m)^[ \t]*\r?\n", "");
    }

    private void saveAiMessage(UUID chatId, String textContext, List<SourceDto> sources) {
        List<String> paths = sources.stream().map(SourceDto::path).toList();
        List<Double> scores = sources.stream().map(SourceDto::score).toList();

        ChatMessageEntity aiMsg = ChatMessageEntity.builder()
                .chatId(chatId)
                .role("AI")
                .textContext(textContext)
                .imagePaths(paths)
                .scores(scores)
                .build();

        chatMessageRepository.save(aiMsg);
    }
}
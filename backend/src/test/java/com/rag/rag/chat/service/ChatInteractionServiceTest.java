package com.rag.rag.chat.service;

import com.rag.rag.chat.dto.MessageRequest;
import com.rag.rag.chat.dto.MessageResponse;
import com.rag.rag.chat.entity.ChatMemoryEntity;
import com.rag.rag.chat.entity.ChatMessageEntity;
import com.rag.rag.chat.repository.ChatMemoryRepository;
import com.rag.rag.chat.repository.ChatMessageRepository;
import com.rag.rag.ingestion.dto.SourceDto;
import com.rag.rag.ingestion.service.IngestionService;
import dev.langchain4j.service.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatInteractionServiceTest {

    @Mock
    private ChatService chatAiService;

    @Mock
    private ChatMemoryRepository chatMemoryRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private IngestionService ingestionService;

    @InjectMocks
    private ChatInteractionService chatInteractionService;

    private UUID chatId;

    @BeforeEach
    void setUp() {
        chatId = UUID.randomUUID();
    }

    @Test
    void shouldProcessChatMessageAndReturnResponse() {
        // Arrange
        MessageRequest request = new MessageRequest("Hello, what is in file.txt?");
        
        ChatMemoryEntity chatMemory = new ChatMemoryEntity();
        chatMemory.setChatId(chatId);
        
        when(chatMemoryRepository.findById(chatId)).thenReturn(Optional.of(chatMemory));
        
        String aiResponseContent = "Here is the content. @file.txt";
        Result<String> aiResult = Result.<String>builder().content(aiResponseContent).build();
        
        when(chatAiService.answer(chatId, "Hello, what is in file.txt?")).thenReturn(aiResult);
        
        SourceDto sourceDto = new SourceDto("path/to/file.txt", "file.txt", 0.9, null, null);
        when(ingestionService.getSources(aiResult)).thenReturn(List.of(sourceDto));

        // Act
        MessageResponse response = chatInteractionService.processChatMessage(chatId, request);

        // Assert
        assertEquals("Here is the content.", response.response());
        assertEquals(1, response.sources().size());
        assertEquals("file.txt", response.sources().get(0).fileName());
        
        // Verify User message saved
        ArgumentCaptor<ChatMessageEntity> messageCaptor = ArgumentCaptor.forClass(ChatMessageEntity.class);
        verify(chatMessageRepository, times(2)).save(messageCaptor.capture());
        
        List<ChatMessageEntity> savedMessages = messageCaptor.getAllValues();
        assertEquals("USER", savedMessages.get(0).getRole());
        assertEquals("Hello, what is in file.txt?", savedMessages.get(0).getTextContext());
        
        // Verify AI message saved
        assertEquals("AI", savedMessages.get(1).getRole());
        assertEquals("Here is the content.", savedMessages.get(1).getTextContext());
        assertTrue(savedMessages.get(1).getImagePaths().contains("path/to/file.txt"));
    }

    @Test
    void shouldReturnDefaultMessageWhenAiResponseIsEmpty() {
        // Arrange
        MessageRequest request = new MessageRequest("Empty response test");
        
        when(chatMemoryRepository.findById(chatId)).thenReturn(Optional.empty());
        
        Result<String> aiResult = Result.<String>builder().content("").build();
        when(chatAiService.answer(chatId, "Empty response test")).thenReturn(aiResult);
        
        // Act
        MessageResponse response = chatInteractionService.processChatMessage(chatId, request);

        // Assert
        assertEquals("Przepraszam, model zwrócił pustą odpowiedź.", response.response());
        assertTrue(response.sources().isEmpty());
    }

    @Test
    void shouldNotIncludeSourcesWhenNoInfoFound() {
        // Arrange
        MessageRequest request = new MessageRequest("Test no info");
        
        Result<String> aiResult = Result.<String>builder().content("Nie znaleziono informacji w dokumentach.").build();
        when(chatAiService.answer(chatId, "Test no info")).thenReturn(aiResult);
        
        // Act
        MessageResponse response = chatInteractionService.processChatMessage(chatId, request);

        // Assert
        assertEquals("Nie znaleziono informacji w dokumentach.", response.response());
        assertTrue(response.sources().isEmpty());
        
        // Verify IngestionService is NOT called since noInfo = true
        verify(ingestionService, never()).getSources(any());
    }
}
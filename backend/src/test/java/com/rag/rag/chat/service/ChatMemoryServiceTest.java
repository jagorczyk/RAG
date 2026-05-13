package com.rag.rag.chat.service;

import com.rag.rag.chat.entity.ChatMemoryEntity;
import com.rag.rag.chat.repository.ChatMemoryRepository;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.AiMessage;
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
class ChatMemoryServiceTest {

    @Mock
    private ChatMemoryRepository repository;

    @InjectMocks
    private ChatMemoryService chatMemoryService;

    private UUID chatId;

    @BeforeEach
    void setUp() {
        chatId = UUID.randomUUID();
    }

    @Test
    void shouldReturnMessagesWhenChatMemoryExists() {
        // Arrange
        ChatMemoryEntity entity = new ChatMemoryEntity();
        entity.setChatId(chatId);
        entity.setMessages("[{\"type\":\"USER\",\"text\":\"Hello\"}]");
        when(repository.findById(chatId)).thenReturn(Optional.of(entity));

        // Act
        List<ChatMessage> result = chatMemoryService.getMessages(chatId);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.get(0) instanceof UserMessage);
        assertEquals("Hello", ((UserMessage) result.get(0)).singleText());
    }

    @Test
    void shouldReturnEmptyListWhenChatMemoryDoesNotExist() {
        // Arrange
        when(repository.findById(chatId)).thenReturn(Optional.empty());

        // Act
        List<ChatMessage> result = chatMemoryService.getMessages(chatId);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnEmptyListWhenMemoryIdIsNotUUID() {
        // Act
        List<ChatMessage> result = chatMemoryService.getMessages("not-a-uuid");

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldSaveMessagesAndCleanSpecificDbString() {
        // Arrange
        String longText = "PYTANIE UŻYTKOWNIKA: What is this?\n\nDANE Z BAZY DANYCH:\nsome db data";
        List<ChatMessage> messages = List.of(
                UserMessage.from(longText),
                AiMessage.from("This is data.")
        );
        
        when(repository.findById(chatId)).thenReturn(Optional.empty());

        // Act
        chatMemoryService.updateMessages(chatId, messages);

        // Assert
        ArgumentCaptor<ChatMemoryEntity> captor = ArgumentCaptor.forClass(ChatMemoryEntity.class);
        verify(repository).save(captor.capture());
        
        ChatMemoryEntity savedEntity = captor.getValue();
        assertEquals(chatId, savedEntity.getChatId());
        
        // Verify that the JSON contains the cleaned message, not the "DANE Z BAZY DANYCH" part
        String savedJson = savedEntity.getMessages();
        assertTrue(savedJson.contains("What is this?"));
        assertFalse(savedJson.contains("DANE Z BAZY DANYCH:"));
    }

    @Test
    void shouldDeleteMessages() {
        // Act
        chatMemoryService.deleteMessages(chatId);

        // Assert
        verify(repository).deleteById(chatId);
    }
}
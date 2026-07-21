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
    void shouldStripCurrentAnswerPromptToBareUserQuestion() {
        String answerPromptBlob = ChatService.ANSWER_INSTRUCTIONS + """

                [Styl odpowiedzi]
                Jedno lub dwa krótkie zdania po polsku. Podaj żądane szczegóły; bez pewności i listy plików.

                [Instrukcja odpowiedzi]
                Odpowiedz z dokumentów.
                Odpowiedź: jedno krótkie zdanie po polsku. Podaj żądane szczegóły; bez pewności i listy plików.
                Używaj wyłącznie fragmentów dokumentów z retrieval — nie zgaduj.

                Pytanie użytkownika: Jakie jest saldo na fakturze?
                """;
        List<ChatMessage> messages = List.of(
                UserMessage.from(answerPromptBlob),
                AiMessage.from("Saldo wynosi 1200 zł.")
        );
        when(repository.findById(chatId)).thenReturn(Optional.empty());

        chatMemoryService.updateMessages(chatId, messages);

        ArgumentCaptor<ChatMemoryEntity> captor = ArgumentCaptor.forClass(ChatMemoryEntity.class);
        verify(repository).save(captor.capture());
        String savedJson = captor.getValue().getMessages();
        assertTrue(savedJson.contains("Jakie jest saldo na fakturze?"));
        assertTrue(savedJson.contains("Saldo wynosi 1200 zł."));
        assertFalse(savedJson.contains("Jesteś asystentem dokumentów"));
        assertFalse(savedJson.contains("[Styl odpowiedzi]"));
        assertFalse(savedJson.contains("[Instrukcja odpowiedzi]"));
        assertFalse(savedJson.contains("Nie znaleziono informacji w dokumentach."));
    }

    @Test
    void roundTripMemoryKeepsCleanQaForNextTurn() {
        String firstTurnBlob = """
                [Instrukcja odpowiedzi]
                Krótko.

                Pytanie użytkownika: Kto jest na pierwszym zdjęciu?
                """;
        List<ChatMessage> written = List.of(
                UserMessage.from(firstTurnBlob),
                AiMessage.from("Na zdjęciu jest Anna.")
        );
        when(repository.findById(chatId)).thenReturn(Optional.empty());

        chatMemoryService.updateMessages(chatId, written);

        ArgumentCaptor<ChatMemoryEntity> captor = ArgumentCaptor.forClass(ChatMemoryEntity.class);
        verify(repository).save(captor.capture());
        ChatMemoryEntity stored = captor.getValue();
        // Simulate next turn reading the same store JSON.
        when(repository.findById(chatId)).thenReturn(Optional.of(stored));

        List<ChatMessage> read = chatMemoryService.getMessages(chatId);

        assertEquals(2, read.size());
        assertTrue(read.get(0) instanceof UserMessage);
        assertEquals("Kto jest na pierwszym zdjęciu?", ((UserMessage) read.get(0)).singleText());
        assertTrue(read.get(1) instanceof AiMessage);
        assertEquals("Na zdjęciu jest Anna.", ((AiMessage) read.get(1)).text());
    }

    @Test
    void shouldDeleteMessages() {
        // Act
        chatMemoryService.deleteMessages(chatId);

        // Assert
        verify(repository).deleteById(chatId);
    }

    @Test
    void replaceLastAiMessageSyncsGroundedAnswerForFollowUps() {
        // Raw model greeting stored by LangChain; UI grounding rewrites to Polish presence.
        ChatMemoryEntity entity = new ChatMemoryEntity();
        entity.setChatId(chatId);
        entity.setMessages(dev.langchain4j.data.message.ChatMessageSerializer.messagesToJson(List.of(
                UserMessage.from("na których zdjęciach jest Piotrek?"),
                AiMessage.from("Hello! How can I assist you today? 😊")
        )));
        when(repository.findById(chatId)).thenReturn(Optional.of(entity));

        chatMemoryService.replaceLastAiMessage(chatId,
                "Piotrek jest na potwierdzonych zdjęciach w bibliotece.");

        ArgumentCaptor<ChatMemoryEntity> captor = ArgumentCaptor.forClass(ChatMemoryEntity.class);
        verify(repository, atLeastOnce()).save(captor.capture());
        ChatMemoryEntity saved = captor.getValue();
        when(repository.findById(chatId)).thenReturn(Optional.of(saved));
        List<ChatMessage> read = chatMemoryService.getMessages(chatId);

        assertEquals(2, read.size());
        assertEquals("na których zdjęciach jest Piotrek?", ((UserMessage) read.get(0)).singleText());
        assertEquals("Piotrek jest na potwierdzonych zdjęciach w bibliotece.",
                ((AiMessage) read.get(1)).text());
        assertFalse(saved.getMessages().contains("Hello! How can I assist"));
    }
}
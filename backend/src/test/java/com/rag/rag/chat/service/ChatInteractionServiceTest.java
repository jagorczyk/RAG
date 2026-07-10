package com.rag.rag.chat.service;

import com.rag.rag.chat.dto.MessageRequest;
import com.rag.rag.chat.dto.MessageResponse;
import com.rag.rag.chat.entity.ChatMemoryEntity;
import com.rag.rag.chat.entity.ChatMessageEntity;
import com.rag.rag.chat.repository.ChatMemoryRepository;
import com.rag.rag.chat.repository.ChatMessageRepository;
import com.rag.rag.ingestion.dto.SourceDto;
import com.rag.rag.ingestion.service.IngestionService;
import com.rag.rag.knowledge.graph.GraphQueryService;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.service.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

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

    @Mock
    private QueryRouter queryRouter;

    @Mock
    private GraphQueryService graphQueryService;

    @Mock
    private ChatEntityReferenceService chatEntityReferenceService;

    @InjectMocks
    private ChatInteractionService chatInteractionService;

    private UUID chatId;

    @BeforeEach
    void setUp() {
        chatId = UUID.randomUUID();
        when(queryRouter.classify(any())).thenReturn(QueryRouter.QueryRoute.DOCUMENT);
        lenient().when(graphQueryService.buildContextForQuestion(any())).thenReturn("");
        lenient().when(graphQueryService.findEntityNameInQuestion(any())).thenReturn(Optional.empty());
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
    void shouldStripFilePathsFromResponseWhenGraphProvidesSources() {
        MessageRequest request = new MessageRequest("Na których zdjęciach jest Olek?");

        when(queryRouter.classify(any())).thenReturn(QueryRouter.QueryRoute.ENTITY_FILES);
        String graphContext = """
                [Pliki z grafu wiedzy]
                - Olek występuje w pliku: 20230526_232902.jpg (etykieta: Olek) | plik: dir://test123/20230526_232902.jpg
                - Olek występuje w pliku: 20230505_132630.jpg (etykieta: Olek) | plik: dir://test123/20230505_132630.jpg
                """;
        when(graphQueryService.buildFileListContextForQuestion(any())).thenReturn(graphContext);

        String aiResponseContent = "Olek występuje na 2 zdjęciach.";
        Result<String> aiResult = Result.<String>builder().content(aiResponseContent).build();
        when(chatAiService.answer(eq(chatId), any())).thenReturn(aiResult);
        when(ingestionService.getSources(aiResult)).thenReturn(List.of());

        when(ingestionService.createSourceDto("dir://test123/20230526_232902.jpg", "20230526_232902.jpg", 1.0))
                .thenReturn(new SourceDto("dir://test123/20230526_232902.jpg", "20230526_232902.jpg", 1.0, null, null));
        when(ingestionService.createSourceDto("dir://test123/20230505_132630.jpg", "20230505_132630.jpg", 1.0))
                .thenReturn(new SourceDto("dir://test123/20230505_132630.jpg", "20230505_132630.jpg", 1.0, null, null));

        MessageResponse response = chatInteractionService.processChatMessage(chatId, request);

        assertEquals(2, response.sources().size());
        assertEquals("Olek występuje na 2 zdjęciach.", response.response());
    }

    @Test
    void shouldNarrowGraphSourcesWithRetrievalIntersection() {
        MessageRequest request = new MessageRequest("Gdzie Olek jest na siłowni?");

        when(queryRouter.classify(any())).thenReturn(QueryRouter.QueryRoute.ENTITY_FILES);
        String graphContext = """
                [Pliki z grafu wiedzy]
                - Olek występuje w pliku: 20230526_232902.jpg (etykieta: Olek) | plik: dir://test123/20230526_232902.jpg
                - Olek występuje w pliku: 20230505_132630.jpg (etykieta: Olek) | plik: dir://test123/20230505_132630.jpg
                - Olek występuje w pliku: 20230505_132643.jpg (etykieta: Olek) | plik: dir://test123/20230505_132643.jpg
                """;
        when(graphQueryService.buildFileListContextForQuestion(any())).thenReturn(graphContext);

        Result<String> aiResult = Result.<String>builder().content("Olek jest na siłowni na 2 zdjęciach.").build();
        when(chatAiService.answer(eq(chatId), any())).thenReturn(aiResult);

        SourceDto gymPhoto1 = new SourceDto("dir://test123/20230505_132630.jpg", "20230505_132630.jpg", 0.9, null, "IMAGE");
        SourceDto gymPhoto2 = new SourceDto("dir://test123/20230505_132643.jpg", "20230505_132643.jpg", 0.8, null, "IMAGE");
        when(ingestionService.getSources(aiResult)).thenReturn(List.of(gymPhoto1, gymPhoto2));
        when(ingestionService.createSourceDto("dir://test123/20230526_232902.jpg", "20230526_232902.jpg", 1.0))
                .thenReturn(new SourceDto("dir://test123/20230526_232902.jpg", "20230526_232902.jpg", 1.0, null, null));
        when(ingestionService.createSourceDto("dir://test123/20230505_132630.jpg", "20230505_132630.jpg", 1.0))
                .thenReturn(gymPhoto1);
        when(ingestionService.createSourceDto("dir://test123/20230505_132643.jpg", "20230505_132643.jpg", 1.0))
                .thenReturn(gymPhoto2);

        MessageResponse response = chatInteractionService.processChatMessage(chatId, request);

        assertEquals(2, response.sources().size());
        assertTrue(response.sources().stream().noneMatch(s -> s.fileName().equals("20230526_232902.jpg")));
    }

    @Test
    void shouldExcludeGraphSourcesNotMentionedInResponse() {
        MessageRequest request = new MessageRequest("Gdzie Olek jest na siłowni?");

        when(queryRouter.classify(any())).thenReturn(QueryRouter.QueryRoute.ENTITY_FILES);
        String graphContext = """
                [Pliki z grafu wiedzy]
                - Olek występuje w pliku: 20230526_232902.jpg (etykieta: Olek) | plik: dir://test123/20230526_232902.jpg
                - Olek występuje w pliku: 20230505_132630.jpg (etykieta: Olek) | plik: dir://test123/20230505_132630.jpg
                - Olek występuje w pliku: 20230505_132643.jpg (etykieta: Olek) | plik: dir://test123/20230505_132643.jpg
                """;
        when(graphQueryService.buildFileListContextForQuestion(any())).thenReturn(graphContext);

        String aiResponseContent = """
                Olek jest na siłowni na następujących zdjęciach:
                - 20230505_132643
                - 20230505_132630
                """;
        Result<String> aiResult = Result.<String>builder().content(aiResponseContent).build();
        when(chatAiService.answer(eq(chatId), any())).thenReturn(aiResult);

        SourceDto gymPhoto1 = new SourceDto("dir://test123/20230505_132630.jpg", "20230505_132630.jpg", 0.9, null, "IMAGE");
        SourceDto gymPhoto2 = new SourceDto("dir://test123/20230505_132643.jpg", "20230505_132643.jpg", 0.8, null, "IMAGE");
        when(ingestionService.getSources(aiResult)).thenReturn(List.of(gymPhoto1, gymPhoto2));
        when(ingestionService.createSourceDto("dir://test123/20230526_232902.jpg", "20230526_232902.jpg", 1.0))
                .thenReturn(new SourceDto("dir://test123/20230526_232902.jpg", "20230526_232902.jpg", 1.0, null, null));
        when(ingestionService.createSourceDto("dir://test123/20230505_132630.jpg", "20230505_132630.jpg", 1.0))
                .thenReturn(new SourceDto("dir://test123/20230505_132630.jpg", "20230505_132630.jpg", 1.0, null, null));
        when(ingestionService.createSourceDto("dir://test123/20230505_132643.jpg", "20230505_132643.jpg", 1.0))
                .thenReturn(new SourceDto("dir://test123/20230505_132643.jpg", "20230505_132643.jpg", 1.0, null, null));

        MessageResponse response = chatInteractionService.processChatMessage(chatId, request);

        assertEquals(2, response.sources().size());
        assertTrue(response.sources().stream().noneMatch(s -> s.fileName().equals("20230526_232902.jpg")));
        assertFalse(response.response().contains("20230505_132643"));
        assertFalse(response.response().contains("na następujących"));
    }

    @Test
    void shouldLimitSourcesToDeclaredPhotoCount() {
        MessageRequest request = new MessageRequest("Gdzie Olek jest na siłowni?");

        when(queryRouter.classify(any())).thenReturn(QueryRouter.QueryRoute.ENTITY_FILES);
        when(graphQueryService.findEntityNameInQuestion(any())).thenReturn(Optional.of("Olek"));
        String graphContext = """
                [Pliki z grafu wiedzy]
                - Olek występuje w pliku: 20230526_232902.jpg (etykieta: Olek) | plik: dir://test123/20230526_232902.jpg
                - Olek występuje w pliku: 20230505_132630.jpg (etykieta: Olek) | plik: dir://test123/20230505_132630.jpg
                - Olek występuje w pliku: 20230505_132643.jpg (etykieta: Olek) | plik: dir://test123/20230505_132643.jpg
                - Olek występuje w pliku: 20230502_094428.jpg (etykieta: Olek) | plik: dir://test123/20230502_094428.jpg
                """;
        when(graphQueryService.buildFileListContextForQuestion(any())).thenReturn(graphContext);

        Result<String> aiResult = Result.<String>builder()
                .content("Olek jest na siłowni na 3 zdjęciach.")
                .sources(List.of(
                        retrievalContent("dir://test123/20230526_232902.jpg", "20230526_232902.jpg", 0.95, "Olek w domu."),
                        retrievalContent("dir://test123/20230505_132630.jpg", "20230505_132630.jpg", 0.9, "Olek na siłowni."),
                        retrievalContent("dir://test123/20230505_132643.jpg", "20230505_132643.jpg", 0.85, "Olek na siłowni."),
                        retrievalContent("dir://test123/20230502_094428.jpg", "20230502_094428.jpg", 0.8, "Olek na siłowni.")
                ))
                .build();
        when(chatAiService.answer(eq(chatId), any())).thenReturn(aiResult);

        SourceDto homePhoto = new SourceDto("dir://test123/20230526_232902.jpg", "20230526_232902.jpg", 0.95, null, "IMAGE");
        SourceDto gymPhoto1 = new SourceDto("dir://test123/20230505_132630.jpg", "20230505_132630.jpg", 0.9, null, "IMAGE");
        SourceDto gymPhoto2 = new SourceDto("dir://test123/20230505_132643.jpg", "20230505_132643.jpg", 0.85, null, "IMAGE");
        SourceDto gymPhoto3 = new SourceDto("dir://test123/20230502_094428.jpg", "20230502_094428.jpg", 0.8, null, "IMAGE");
        when(ingestionService.getSources(aiResult)).thenReturn(List.of(homePhoto, gymPhoto1, gymPhoto2, gymPhoto3));
        when(ingestionService.createSourceDto(any(), any(), anyDouble())).thenAnswer(invocation -> new SourceDto(
                invocation.getArgument(0),
                invocation.getArgument(1),
                invocation.getArgument(2),
                null,
                "IMAGE"
        ));

        MessageResponse response = chatInteractionService.processChatMessage(chatId, request);

        assertEquals(3, response.sources().size());
        assertTrue(response.sources().stream().noneMatch(s -> s.fileName().equals("20230526_232902.jpg")));
    }

    private static Content retrievalContent(String path, String filename, double score, String text) {
        return Content.from(TextSegment.from(text, Metadata.from(Map.of(
                "path", path,
                "filename", filename,
                "score", String.valueOf(score)
        ))));
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
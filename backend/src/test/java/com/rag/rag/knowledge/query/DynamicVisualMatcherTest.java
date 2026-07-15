package com.rag.rag.knowledge.query;

import com.rag.rag.folder.entity.FileEntity;
import com.rag.rag.folder.repository.FileRepository;
import com.rag.rag.knowledge.graph.GraphQueryService;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DynamicVisualMatcherTest {
    @Mock private FileRepository fileRepository;
    @Mock private GraphQueryService graphQueryService;
    @Mock private ChatLanguageModel chatModel;
    @Mock private ChatLanguageModel visionModel;

    private DynamicVisualMatcher matcher;
    private FileEntity image;

    @BeforeEach
    void setUp() {
        matcher = new DynamicVisualMatcher(fileRepository, graphQueryService, chatModel, visionModel);
        image = FileEntity.builder()
                .path("dir://photos/rally.jpg")
                .fileName("rally.jpg")
                .fileType("image/jpeg")
                .build();
        lenient().when(graphQueryService.resolveImageFilePathsFromQuestion(anyString())).thenReturn(List.of());
        lenient().when(graphQueryService.findAllEntityNamesInQuestion(anyString())).thenReturn(List.of("Olek"));
        lenient().when(graphQueryService.getFilesForEntity(anyString()))
                .thenReturn(List.of(new GraphQueryService.MentionFileRow(image.getPath(), "Olek")));
        lenient().when(fileRepository.findByPath(image.getPath())).thenReturn(Optional.of(image));
        lenient().when(graphQueryService.buildFullContextForFile(image.getPath()))
                .thenReturn("Olek ma na sobie niebieski kombinezon ochronny; obok stoi samochód rajdowy.");
    }

    @Test
    void acceptsAConceptThatHasNoStaticVocabularyEntry() {
        lenient().when(chatModel.generate(anyString())).thenReturn(
                "{\"decision\":\"MATCH\",\"confidence\":0.94,"
                        + "\"reasons\":[\"strój i otoczenie wskazują na opisywaną rolę\"],"
                        + "\"missingEvidence\":[]}");

        List<VisualQueryMatch> matches = matcher.findMatches("daj mi zdjęcie Olka z rajdowcem");

        assertEquals(List.of(image.getPath()), matches.stream().map(VisualQueryMatch::filePath).toList());
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(chatModel).generate(prompt.capture());
        assertTrue(prompt.getValue().contains("rajdowcem"));
    }

    @Test
    void doesNotTreatMissingContextAsAConfirmedMatch() {
        when(graphQueryService.buildFullContextForFile(image.getPath())).thenReturn("");
        lenient().when(chatModel.generate(anyString())).thenReturn(
                "{\"decision\":\"UNCERTAIN\",\"confidence\":0.10,"
                        + "\"reasons\":[],\"missingEvidence\":[\"brak opisu\"]}");

        assertTrue(matcher.findMatches("pokaż zdjęcie Olka w nieznanym stroju").isEmpty());
    }

    @Test
    void verifiesAnUncertainStoredContextWithTheImageModel() {
        image.setImageData(new byte[]{1, 2, 3});
        when(chatModel.generate(anyString())).thenReturn(
                "{\"decision\":\"UNCERTAIN\",\"confidence\":0.45,"
                        + "\"reasons\":[],\"missingEvidence\":[\"brak pewności\"]}");
        when(visionModel.generate(any(UserMessage.class))).thenReturn(Response.from(AiMessage.from(
                "{\"decision\":\"MATCH\",\"confidence\":0.91,"
                        + "\"reasons\":[\"warunek widoczny na zdjęciu\"],\"missingEvidence\":[]}")));

        assertEquals(List.of(image.getPath()), matcher.findMatches("pokaż zdjęcie Olka w nieznanym stroju")
                .stream().map(VisualQueryMatch::filePath).toList());
        verify(visionModel).generate(any(UserMessage.class));
    }
}

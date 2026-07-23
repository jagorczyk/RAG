package com.rag.rag.chat.service;

import com.rag.rag.knowledge.graph.GraphEvidenceItem;
import com.rag.rag.knowledge.graph.GraphEvidenceResult;
import com.rag.rag.knowledge.graph.GraphPhotoEvidence;
import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GraphContextReducerTest {

    @Test
    void everyOversizedEvidenceItemReachesMapperAndLastBatchFactCanReachAnswer() {
        ChatLanguageModel control = mock(ChatLanguageModel.class);
        Tokenizer tokenizer = mock(Tokenizer.class);
        when(tokenizer.estimateTokenCountInText(anyString()))
                .thenAnswer(invocation -> Math.max(1, invocation.<String>getArgument(0).length()));
        List<String> mapperPrompts = new ArrayList<>();
        when(control.generate(anyString())).thenAnswer(invocation -> {
            String prompt = invocation.getArgument(0);
            mapperPrompts.add(prompt);
            return prompt.contains("E6.1")
                    ? "{\"selectedEvidenceIds\":[\"E6.1\"]}"
                    : "{\"selectedEvidenceIds\":[]}";
        });

        GraphContextReducer reducer = new GraphContextReducer(control, tokenizer);
        ReflectionTestUtils.setField(reducer, "contextWindowTokens", 1024);
        ReflectionTestUtils.setField(reducer, "answerMaxTokens", 64);
        ReflectionTestUtils.setField(reducer, "reducerMaxInputTokens", 700);

        List<GraphPhotoEvidence> photos = new ArrayList<>();
        List<String> paths = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            String path = "dir://photo-" + i + ".jpg";
            paths.add(path);
            String statement = (i == 6 ? "Kluczowy fakt: Olek trzyma parasol. " : "Nieistotny opis. ")
                    + "x".repeat(280);
            photos.add(new GraphPhotoEvidence(String.valueOf(i), path, List.of(
                    new GraphEvidenceItem("E" + i + ".1", GraphEvidenceItem.Kind.FACT,
                            statement, path))));
        }
        String context = photos.stream().map(GraphPhotoEvidence::render)
                .reduce("", (left, right) -> left.isBlank() ? right : left + "\n" + right);

        GraphContextReducer.Selection selection = reducer.select(
                new GraphEvidenceResult(context, paths, List.of(), photos),
                "Co trzyma Olek?", "Odpowiedz po polsku.");

        assertTrue(selection.reduced());
        assertTrue(selection.context().contains("Kluczowy fakt"));
        assertEquals(List.of("dir://photo-6.jpg"), selection.selectedPaths());
        String allMapperInput = String.join("\n", mapperPrompts);
        for (int i = 1; i <= 6; i++) {
            assertTrue(allMapperInput.contains("E" + i + ".1"), "missing mapper coverage for E" + i + ".1");
        }
    }

    @Test
    void oversizedCollectionAlwaysKeepsDeterministicInventoryEvidence() {
        ChatLanguageModel control = mock(ChatLanguageModel.class);
        Tokenizer tokenizer = mock(Tokenizer.class);
        when(tokenizer.estimateTokenCountInText(anyString()))
                .thenAnswer(invocation -> Math.max(1, invocation.<String>getArgument(0).length()));
        when(control.generate(anyString())).thenReturn("{\"selectedEvidenceIds\":[]}");
        GraphContextReducer reducer = new GraphContextReducer(control, tokenizer);
        ReflectionTestUtils.setField(reducer, "contextWindowTokens", 1024);
        ReflectionTestUtils.setField(reducer, "answerMaxTokens", 64);
        ReflectionTestUtils.setField(reducer, "reducerMaxInputTokens", 700);

        GraphPhotoEvidence inventory = new GraphPhotoEvidence("I", "", List.of(
                new GraphEvidenceItem("I.1", GraphEvidenceItem.Kind.INVENTORY,
                        "Biblioteka zawiera 120 plików.", "")));
        List<GraphPhotoEvidence> photos = new ArrayList<>();
        photos.add(inventory);
        for (int i = 1; i <= 5; i++) {
            String path = "dir://folder/" + i + ".jpg";
            photos.add(new GraphPhotoEvidence(String.valueOf(i), path, List.of(
                    new GraphEvidenceItem("C" + i + ".1", GraphEvidenceItem.Kind.DOCUMENT,
                            "opis ".repeat(90), path))));
        }
        String context = photos.stream().map(GraphPhotoEvidence::render)
                .reduce("", (left, right) -> left.isBlank() ? right : left + "\n" + right);

        GraphContextReducer.Selection selection = reducer.select(
                new GraphEvidenceResult(context, List.of(), List.of(), photos),
                "Co jest w bibliotece?", "Krótko.");

        assertTrue(selection.reduced());
        assertTrue(selection.context().contains("[I.1] Biblioteka zawiera 120 plików."));
    }
}

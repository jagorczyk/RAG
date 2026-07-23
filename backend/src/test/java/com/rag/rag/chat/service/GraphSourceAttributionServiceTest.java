package com.rag.rag.chat.service;

import com.rag.rag.knowledge.graph.GraphEvidenceItem;
import com.rag.rag.knowledge.graph.GraphEvidenceResult;
import com.rag.rag.knowledge.graph.GraphPhotoEvidence;
import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GraphSourceAttributionServiceTest {

    @Test
    void returnsOnlyThePhotoWhoseEvidenceDirectlySupportsTheAnswer() {
        ChatLanguageModel control = mock(ChatLanguageModel.class);
        Tokenizer tokenizer = tokenizer();
        when(control.generate(anyString()))
                .thenReturn("{\"claims\":[{\"claimId\":\"C1\",\"fullySupported\":true,"
                        + "\"supportingEvidenceIds\":[\"E2.2\"]}]}");
        GraphSourceAttributionService service = service(control, tokenizer);
        GraphEvidenceResult evidence = evidence();

        GraphSourceAttributionService.Attribution result = service.attribute(
                "Opisz zdjęcie, na którym Igor jest na siłowni.",
                "Igor stoi na siłowni, nagi do pasa, w szarych spodniach.", evidence);

        assertTrue(result.attempted());
        assertTrue(result.reliable());
        assertEquals(List.of("dir://gym.jpg"), result.paths());
        assertEquals(List.of("E2.2"), result.evidenceIds());
    }

    @Test
    void malformedAttributionFailsClosedInsteadOfReturningUnrelatedPaths() {
        ChatLanguageModel control = mock(ChatLanguageModel.class);
        when(control.generate(anyString())).thenReturn("not-json");
        GraphSourceAttributionService service = service(control, tokenizer());

        GraphSourceAttributionService.Attribution result = service.attribute(
                "Co robi Igor?", "Igor gra na fortepianie w sali koncertowej.", evidence());

        assertTrue(result.attempted());
        assertFalse(result.reliable());
        assertTrue(result.paths().isEmpty());
    }

    @Test
    void explicitUnsupportedClaimFailsClosedEvenWhenLexicallySimilar() {
        ChatLanguageModel control = mock(ChatLanguageModel.class);
        when(control.generate(anyString()))
                .thenReturn("{\"claims\":[{\"claimId\":\"C1\",\"fullySupported\":false,"
                        + "\"supportingEvidenceIds\":[]},{\"claimId\":\"C2\","
                        + "\"fullySupported\":false,\"supportingEvidenceIds\":[]}]}");
        GraphSourceAttributionService service = service(control, tokenizer());
        GraphPhotoEvidence formal = new GraphPhotoEvidence("1", "dir://formal.jpg", List.of(
                new GraphEvidenceItem("E1.1", GraphEvidenceItem.Kind.SCENE,
                        "Igor stoi na trawie w czarnym garniturze i białej koszuli.", "dir://formal.jpg")));
        GraphPhotoEvidence bike = new GraphPhotoEvidence("2", "dir://bike.jpg", List.of(
                new GraphEvidenceItem("E2.1", GraphEvidenceItem.Kind.SCENE,
                        "Igor stoi przed budynkiem, trzyma rower i ma biały podkoszulek.", "dir://bike.jpg")));
        GraphPhotoEvidence gym = new GraphPhotoEvidence("3", "dir://gym.jpg", List.of(
                new GraphEvidenceItem("E3.1", GraphEvidenceItem.Kind.SCENE,
                        "Podsumowanie: {\"actions\":[\"stoi\",\"uśmiecha się\",\"ma rękę nad głową\"],"
                                + "\"objects\":[\"szare spodnie do jogi\",\"pomarańczowe szafki 53 i 54\","
                                + "\"czarny stolik ze szklanką\",\"niebieska kurtka\"],"
                                + "\"scene\":\"pomieszczenie siłowni z panelami na suficie\"}", "dir://gym.jpg")));
        List<GraphPhotoEvidence> photos = List.of(formal, bike, gym);
        String context = photos.stream().map(GraphPhotoEvidence::render)
                .reduce("", (left, right) -> left.isBlank() ? right : left + "\n" + right);
        GraphEvidenceResult graph = new GraphEvidenceResult(context,
                List.of("dir://formal.jpg", "dir://bike.jpg", "dir://gym.jpg"), List.of(), photos);

        GraphSourceAttributionService.Attribution result = service.attribute(
                "Opisz zdjęcie Igora na siłowni.",
                "Igor stoi w pomieszczeniu siłowni, nagi do pasa, w szarych spodniach do jogi. "
                        + "Za nim są pomarańczowe szafki z numerami 53 i 54, czarny stolik i niebieska kurtka.",
                graph);

        assertFalse(result.reliable());
        assertTrue(result.paths().isEmpty());
    }

    @Test
    void requiresEvidenceForEveryPartOfAMultiPhotoAnswer() {
        ChatLanguageModel control = mock(ChatLanguageModel.class);
        when(control.generate(anyString())).thenReturn("""
                {"claims":[
                  {"claimId":"C1","fullySupported":true,"supportingEvidenceIds":["E1.2"]},
                  {"claimId":"C2","fullySupported":true,"supportingEvidenceIds":["E1.2"]},
                  {"claimId":"C3","fullySupported":true,"supportingEvidenceIds":["E2.2"]},
                  {"claimId":"C4","fullySupported":true,"supportingEvidenceIds":["E2.2"]}
                ]}
                """);
        GraphSourceAttributionService service = service(control, tokenizer());
        GraphEvidenceResult graph = evidence();

        GraphSourceAttributionService.Attribution result = service.attribute(
                "Jakie są zdjęcia Igora?",
                "Na pierwszym Igor stoi przy rowerze przed budynkiem. Ma przy sobie rower. "
                        + "Na drugim Igor stoi na siłowni, nagi do pasa. Ma szare spodnie.",
                graph);

        assertTrue(result.reliable());
        assertEquals(List.of("dir://bike.jpg", "dir://gym.jpg"), result.paths());
        assertEquals(List.of("E1.2", "E2.2"), result.evidenceIds());
    }

    @Test
    void rejectsEveryExplicitlyUnsupportedSentenceInACompoundAnswer() {
        ChatLanguageModel control = mock(ChatLanguageModel.class);
        when(control.generate(anyString())).thenReturn("""
                {"claims":[
                  {"claimId":"C1","fullySupported":false,"supportingEvidenceIds":[]},
                  {"claimId":"C2","fullySupported":false,"supportingEvidenceIds":[]},
                  {"claimId":"C3","fullySupported":false,"supportingEvidenceIds":[]},
                  {"claimId":"C4","fullySupported":false,"supportingEvidenceIds":[]}
                ]}
                """);
        GraphSourceAttributionService service = service(control, tokenizer());

        GraphSourceAttributionService.Attribution result = service.attribute(
                "Jakie są zdjęcia Igora?",
                "Igor stoi przy rowerze przed budynkiem. Obok niego jest rower. "
                        + "Igor stoi na siłowni nagi do pasa. Ma na sobie szare spodnie.",
                evidence());

        assertFalse(result.reliable());
        assertTrue(result.paths().isEmpty());
    }

    @Test
    void rejectsAResponseThatOmitsOneOfTheAnswerClaims() {
        ChatLanguageModel control = mock(ChatLanguageModel.class);
        when(control.generate(anyString())).thenReturn(
                "{\"claims\":[{\"claimId\":\"C1\",\"fullySupported\":true,"
                        + "\"supportingEvidenceIds\":[\"E1.2\"]}]}");
        GraphSourceAttributionService service = service(control, tokenizer());

        GraphSourceAttributionService.Attribution result = service.attribute(
                "Co robi Igor?",
                "Igor gra na fortepianie. Występuje w sali koncertowej. "
                        + "Potem odbiera nagrodę. Publiczność bije brawo.",
                evidence());

        assertTrue(result.attempted());
        assertFalse(result.reliable());
        assertTrue(result.paths().isEmpty());
    }

    @Test
    void rejectsUnsupportedAtmosphereEvenWhenThePreviousSentenceIsGrounded() {
        ChatLanguageModel control = mock(ChatLanguageModel.class);
        when(control.generate(anyString())).thenReturn("""
                {"claims":[
                  {"claimId":"C1","fullySupported":true,"supportingEvidenceIds":["E2.2"]},
                  {"claimId":"C2","fullySupported":false,"supportingEvidenceIds":[]}
                ]}
                """);
        GraphSourceAttributionService service = service(control, tokenizer());

        GraphSourceAttributionService.Attribution result = service.attribute(
                "Co robi Igor?",
                "Igor stoi na siłowni w szarych spodniach. Scena ma przyjazną atmosferę.",
                evidence());

        assertTrue(result.attempted());
        assertFalse(result.reliable());
        assertTrue(result.paths().isEmpty());
    }

    @Test
    void collectionAggregateCanBeReliableWithoutInventingAFileSource() {
        ChatLanguageModel control = mock(ChatLanguageModel.class);
        when(control.generate(anyString())).thenReturn("""
                {"claims":[{"claimId":"C1","fullySupported":true,
                "supportingEvidenceIds":["I.1"]}]}
                """);
        GraphSourceAttributionService service = service(control, tokenizer());
        GraphPhotoEvidence inventory = new GraphPhotoEvidence("I", "", List.of(
                new GraphEvidenceItem("I.1", GraphEvidenceItem.Kind.INVENTORY,
                        "Biblioteka zawiera 12 plików.", "")));
        GraphEvidenceResult evidence = new GraphEvidenceResult(
                inventory.render(), List.of("dir://a.jpg"), List.of(), List.of(inventory));

        GraphSourceAttributionService.Attribution result = service.attributeCollection(
                "Co jest w bibliotece?", "Biblioteka zawiera 12 plików.", evidence, 3);

        assertTrue(result.reliable());
        assertTrue(result.paths().isEmpty());
        assertEquals(List.of("I.1"), result.evidenceIds());
    }

    @Test
    void collectionAuditorTreatsFileNamesOnlyAsCatalogIdentifiers() {
        ChatLanguageModel control = mock(ChatLanguageModel.class);
        when(control.generate(anyString())).thenReturn("""
                {"claims":[{"claimId":"C1","fullySupported":false,
                "supportingEvidenceIds":[]}]}
                """);
        GraphSourceAttributionService service = service(control, tokenizer());
        GraphPhotoEvidence inventory = new GraphPhotoEvidence("I", "", List.of(
                new GraphEvidenceItem("I.1", GraphEvidenceItem.Kind.INVENTORY,
                        "Nazwa pliku: plaża.jpg.", "")));
        GraphEvidenceResult evidence = new GraphEvidenceResult(
                inventory.render(), List.of(), List.of(), List.of(inventory));

        GraphSourceAttributionService.Attribution result = service.attributeCollection(
                "Co jest w folderze?", "W folderze znajduje się zdjęcie plaży.", evidence, 1);

        assertFalse(result.reliable());
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(control).generate(prompt.capture());
        assertTrue(prompt.getValue().contains("tylko identyfikatorem"));
    }

    private static GraphSourceAttributionService service(ChatLanguageModel control, Tokenizer tokenizer) {
        GraphSourceAttributionService service = new GraphSourceAttributionService(control, tokenizer);
        ReflectionTestUtils.setField(service, "maxInputTokens", 8192);
        return service;
    }

    private static Tokenizer tokenizer() {
        Tokenizer tokenizer = mock(Tokenizer.class);
        when(tokenizer.estimateTokenCountInText(anyString()))
                .thenAnswer(call -> Math.max(1, call.<String>getArgument(0).length() / 4));
        return tokenizer;
    }

    private static GraphEvidenceResult evidence() {
        GraphPhotoEvidence bike = new GraphPhotoEvidence("1", "dir://bike.jpg", List.of(
                new GraphEvidenceItem("E1.1", GraphEvidenceItem.Kind.PARTICIPANTS,
                        "Uczestnicy: Igor.", "dir://bike.jpg"),
                new GraphEvidenceItem("E1.2", GraphEvidenceItem.Kind.FACT,
                        "Igor stoi przy rowerze przed budynkiem.", "dir://bike.jpg")));
        GraphPhotoEvidence gym = new GraphPhotoEvidence("2", "dir://gym.jpg", List.of(
                new GraphEvidenceItem("E2.1", GraphEvidenceItem.Kind.PARTICIPANTS,
                        "Uczestnicy: Igor.", "dir://gym.jpg"),
                new GraphEvidenceItem("E2.2", GraphEvidenceItem.Kind.FACT,
                        "Igor stoi na siłowni, nagi do pasa, w szarych spodniach.", "dir://gym.jpg")));
        List<GraphPhotoEvidence> photos = List.of(bike, gym);
        String context = photos.stream().map(GraphPhotoEvidence::render)
                .reduce("", (left, right) -> left.isBlank() ? right : left + "\n" + right);
        return new GraphEvidenceResult(context,
                List.of("dir://bike.jpg", "dir://gym.jpg"), List.of(), photos);
    }
}

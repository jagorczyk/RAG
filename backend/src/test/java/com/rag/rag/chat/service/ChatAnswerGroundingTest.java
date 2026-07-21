package com.rag.rag.chat.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChatAnswerGroundingTest {

    @Test
    void detectsPolishAndEnglishCapabilityDenials() {
        assertTrue(ChatAnswerGrounding.isCapabilityDenial(
                "Niestety, nie mogę zobaczyć zdjęć ani obrazów."));
        assertTrue(ChatAnswerGrounding.isCapabilityDenial(
                "Nie mam dostępu do konkretnych plików, zdjęć ani obrazów."));
        assertTrue(ChatAnswerGrounding.isCapabilityDenial(
                "I cannot see the image you mentioned."));
        assertTrue(ChatAnswerGrounding.isCapabilityDenial(
                "Nie jestem w stanie odpowiedzieć na to pytanie, ponieważ nie mam informacji, o kogo konkretnie pytasz."));
        assertTrue(ChatAnswerGrounding.isCapabilityDenial(
                "Nie wiem, o kogo pytasz — podaj więcej szczegółów."));
        assertTrue(ChatAnswerGrounding.isCapabilityDenial(
                "Aby odpowiedzieć na pytanie, potrzebuję więcej informacji. Czy możesz podać dostępne zdjęcia lub opisać, jak wygląda osoba?"));
        assertTrue(ChatAnswerGrounding.isCapabilityDenial(
                "Need more information — describe how they look."));
        assertTrue(ChatAnswerGrounding.isCapabilityDenial("""
                Oczywiście! Aby opisać zdjęcie, potrzebuję więcej szczegółów na jego temat. Możesz mi powiedzieć, co jest na zdjęciu? Na przykład:
                - Czy to krajobraz, portret, czy może zdjęcie przedmiotu?
                - Jakie kolory dominują?
                """));
        assertFalse(ChatAnswerGrounding.isCapabilityDenial(
                "Na zdjęciu są Igor i Anna."));
        assertFalse(ChatAnswerGrounding.isCapabilityDenial(
                "Nie znaleziono informacji w dokumentach."));
        assertFalse(ChatAnswerGrounding.isCapabilityDenial(""));
        assertFalse(ChatAnswerGrounding.isCapabilityDenial(null));
    }

    @Test
    void describePhotoAskBackIsRewrittenWhenEvidenceExists() {
        String askBack = """
                Oczywiście! Aby opisać zdjęcie, potrzebuję więcej szczegółów na jego temat. Możesz mi powiedzieć, co jest na zdjęciu?
                """;
        String resolved = ChatAnswerGrounding.resolveGroundedAnswer(
                askBack, List.of("Igor", "Anna"), true, false);
        assertEquals("Na zdjęciu są Igor i Anna.", resolved);
        assertFalse(ChatAnswerGrounding.isCapabilityDenial(resolved));
    }

    @Test
    void detectsGeneralKnowledgeEssayShape() {
        assertTrue(ChatAnswerGrounding.isGeneralKnowledgeEssay(
                "To imię męskie, które jest zdrobnieniem od imienia. Imię pochodzi z języka greckiego."));
        assertTrue(ChatAnswerGrounding.isGeneralKnowledgeEssay(
                "W kulturze popularnej istnieje wiele postaci o tym imieniu."));
        assertFalse(ChatAnswerGrounding.isGeneralKnowledgeEssay(
                "Osoba stoi w siłowni w czarnej koszulce."));
        assertFalse(ChatAnswerGrounding.isGeneralKnowledgeEssay(
                "Nie znaleziono informacji w dokumentach."));
    }

    @Test
    void detectsOffTopicDefinitionalLectureAboutSemanticConstraint() {
        // Live failure: "co wiesz o @photo.jpg" → linguistics textbook dump.
        String lecture = """
                Pełne ograniczenie semantyczne (ang. *full semantic constraint*) odnosi się do sytuacji, \
                w której znaczenie słowa lub wyrażenia jest ściśle określone przez kontekst lub reguły językowe, \
                co uniemożliwia jego dowolną interpretację. W języku polskim pełne ograniczenie semantyczne \
                może występować w różnych sytuacjach, np.:

                1. Terminy specjalistyczne: Słowa używane w konkretnych dziedzinach nauki lub techniki mają \
                ściśle określone znaczenie. Przykładem może być słowo "atom" w fizyce.
                2. Frazeologizmy: Stałe związki frazeologiczne mają jednoznaczne znaczenie, np. "bić rekordy".
                3. Kontekst kulturowy: Niektóre słowa mają ściśle określone znaczenie w danym kontekście kulturowym.
                4. Reguły gramatyczne: W niektórych przypadkach gramatyka narzuca jednoznaczne znaczenie.

                Pełne ograniczenie semantyczne jest przeciwieństwem niepełnego ograniczenia semantycznego, \
                gdzie znaczenie może być bardziej otwarte na interpretację.
                """;
        assertTrue(ChatAnswerGrounding.isGeneralKnowledgeEssay(lecture));
        assertTrue(ChatAnswerGrounding.shouldRewriteUngroundedAnswer(lecture, false));

        String resolvedWithNames = ChatAnswerGrounding.resolveGroundedAnswer(
                lecture, List.of("Igor", "Anna"), true, false);
        assertEquals("Na zdjęciu są Igor i Anna.", resolvedWithNames);

        String resolvedNoNames = ChatAnswerGrounding.resolveGroundedAnswer(
                lecture, List.of(), true, false);
        assertEquals(ChatAnswerGrounding.GROUNDED_NO_DETAIL_FALLBACK, resolvedNoNames);

        // Grounded photo answers must not be rewritten.
        assertFalse(ChatAnswerGrounding.isGeneralKnowledgeEssay(
                "Na zdjęciu stoi Igor w czarnej koszulce obok Anny."));
    }

    @Test
    void detectsEmptyAndGreetingNonAnswers() {
        assertTrue(ChatAnswerGrounding.isEmptyOrGreetingNonAnswer(
                "Hello! How can I assist you today?"));
        assertTrue(ChatAnswerGrounding.isEmptyOrGreetingNonAnswer(
                "Hi! How can I help you?"));
        assertTrue(ChatAnswerGrounding.isEmptyOrGreetingNonAnswer(
                "Cześć! W czym mogę pomóc?"));
        assertTrue(ChatAnswerGrounding.isEmptyOrGreetingNonAnswer(""));
        assertTrue(ChatAnswerGrounding.isEmptyOrGreetingNonAnswer(null));
        assertFalse(ChatAnswerGrounding.isEmptyOrGreetingNonAnswer(
                "Osoba jest na potwierdzonych zdjęciach w bibliotece."));
        assertFalse(ChatAnswerGrounding.isEmptyOrGreetingNonAnswer(
                "Na zdjęciu stoi Igor w garniturze."));
    }

    @Test
    void greetingNonAnswerWithEvidenceIsReplacedByGroundedPresence() {
        String hello = "Hello! How can I assist you today?";
        String entityScoped = ChatAnswerGrounding.resolveGroundedAnswer(
                hello, List.of("Anna"), true, true);
        assertEquals("Anna jest na potwierdzonych zdjęciach w bibliotece.", entityScoped);

        String roster = ChatAnswerGrounding.resolveGroundedAnswer(
                hello, List.of("Igor", "Anna"), true, false);
        assertEquals("Na zdjęciu są Igor i Anna.", roster);
        assertFalse(ChatAnswerGrounding.isEmptyOrGreetingNonAnswer(entityScoped));
    }

    @Test
    void resolveAgainstIdentityIgnoranceUsesRosterWhenNamesExist() {
        String denial = "Nie jestem w stanie odpowiedzieć na to pytanie, ponieważ nie mam informacji, o kogo konkretnie pytasz. Jeśli podasz więcej szczegółów lub kontekst, postaram się pomóc!";
        String resolved = ChatAnswerGrounding.resolveAgainstDenial(
                denial, List.of("Olek", "Piotrek", "Bargiel", "Dawid", "Igor"), true);
        assertTrue(resolved.contains("Olek"));
        assertTrue(resolved.contains("Igor"));
        assertFalse(resolved.contains("animal"));
        assertFalse(ChatAnswerGrounding.isCapabilityDenial(resolved));
    }

    @Test
    void formatsParticipantRosterInPolish() {
        assertEquals("Na zdjęciu jest Igor.",
                ChatAnswerGrounding.formatParticipantRoster(List.of("Igor")));
        assertEquals("Na zdjęciu są Igor i Anna.",
                ChatAnswerGrounding.formatParticipantRoster(List.of("Igor", "Anna")));
        assertEquals("Na zdjęciu są Igor, Anna i Dawid.",
                ChatAnswerGrounding.formatParticipantRoster(List.of("Igor", "Anna", "Dawid")));
        assertEquals(ChatAnswerGrounding.GROUNDED_NO_ROSTER_FALLBACK,
                ChatAnswerGrounding.formatParticipantRoster(List.of()));
    }

    @Test
    void entityScopedPresenceDoesNotListUnrelatedCoPresentPeople() {
        String denial = "Potrzebuję więcej informacji. Opisz, jak wygląda ta osoba.";
        // Recovery names are only the plan entities — not the full multi-file roster.
        String resolved = ChatAnswerGrounding.resolveGroundedAnswer(
                denial, List.of("Anna"), true, true);
        assertTrue(resolved.contains("Anna"));
        assertTrue(resolved.contains("potwierdzonych zdjęciach"));
        assertFalse(resolved.contains("Igor"));
        assertFalse(resolved.contains("Dawid"));
        assertFalse(ChatAnswerGrounding.isCapabilityDenial(resolved));
        // Full roster shape must not be used for entity-scoped recovery.
        assertFalse(resolved.startsWith("Na zdjęciu są"));
    }

    @Test
    void entityScopedGeneralKnowledgeEssayIsReplacedByPresence() {
        String essay = "To imię męskie, które jest zdrobnieniem. Imię pochodzi z języka greckiego i oznacza skałę. "
                + "W kulturze popularnej jest popularne.";
        String resolved = ChatAnswerGrounding.resolveGroundedAnswer(
                essay, List.of("Anna"), true, true);
        assertEquals("Anna jest na potwierdzonych zdjęciach w bibliotece.", resolved);
        assertFalse(ChatAnswerGrounding.isGeneralKnowledgeEssay(resolved));
    }

    @Test
    void nonEntityScopedKeepsFullRosterOnDenial() {
        String denial = "Nie mogę zobaczyć zdjęć.";
        String resolved = ChatAnswerGrounding.resolveGroundedAnswer(
                denial, List.of("Igor", "Anna", "Dawid"), true, false);
        assertEquals("Na zdjęciu są Igor, Anna i Dawid.", resolved);
    }

    @Test
    void goodEvidenceBackedAnswerUnchangedEvenWhenEntityScoped() {
        String good = "Osoba stoi w siłowni w czarnej koszulce i niebieskiej czapce.";
        assertEquals(good, ChatAnswerGrounding.resolveGroundedAnswer(
                good, List.of("Anna"), true, true));
    }

    @Test
    void resolveAgainstDenialReplacesRefusalWhenEvidenceExists() {
        String denial = "Nie mogę zobaczyć zdjęć, więc nie wiem, kto tam jest.";
        String resolved = ChatAnswerGrounding.resolveAgainstDenial(
                denial, List.of("Igor", "Piotrek", "Bargiel"), true);
        assertTrue(resolved.contains("Igor"));
        assertTrue(resolved.contains("Piotrek"));
        assertTrue(resolved.contains("Bargiel"));
        assertFalse(ChatAnswerGrounding.isCapabilityDenial(resolved));
    }

    @Test
    void resolveAgainstDenialKeepsGoodAnswerAndPreservesNoEvidencePath() {
        String good = "Na zdjęciu stoi Igor w garniturze.";
        assertEquals(good, ChatAnswerGrounding.resolveAgainstDenial(good, List.of("Igor"), true));

        String denial = "Nie mam dostępu do plików.";
        // No certain evidence → leave denial as-is (caller handles no-grounding separately).
        assertEquals(denial, ChatAnswerGrounding.resolveAgainstDenial(denial, List.of(), false));

        // Evidence without names → non-denial grounded fallback, not vision refusal.
        String fallback = ChatAnswerGrounding.resolveAgainstDenial(denial, List.of(), true);
        assertEquals(ChatAnswerGrounding.GROUNDED_NO_ROSTER_FALLBACK, fallback);
        assertFalse(ChatAnswerGrounding.isCapabilityDenial(fallback));
    }

    @Test
    void cleanEmptyQuoteArtifactsRemovesShellsAfterFilenameStrip() {
        String stripped = "Nie mam dostępu do plików, takich jak „”, więc nie mogę pomóc.";
        String cleaned = ChatAnswerGrounding.cleanEmptyQuoteArtifacts(stripped);
        assertFalse(cleaned.contains("„”"));
        assertFalse(cleaned.contains("\"\""));
        assertFalse(cleaned.toLowerCase().contains("takich jak"));

        String withEmptyDouble = "Brak pliku \"\" w bibliotece.";
        String cleaned2 = ChatAnswerGrounding.cleanEmptyQuoteArtifacts(withEmptyDouble);
        assertFalse(cleaned2.contains("\"\""));
    }

    @Test
    void formatEntityScopedPresenceHasNoFilenames() {
        String one = ChatAnswerGrounding.formatEntityScopedPresence(List.of("Igor"));
        assertEquals("Igor jest na potwierdzonych zdjęciach w bibliotece.", one);
        assertFalse(one.contains(".jpg"));
        assertFalse(one.contains("dir://"));

        String multi = ChatAnswerGrounding.formatEntityScopedPresence(List.of("Igor", "Anna"));
        assertEquals("Igor i Anna są na potwierdzonych zdjęciach w bibliotece.", multi);
    }

    @Test
    void detectsClarificationSeekingFromTestKon123() {
        String olekClarify = "It seems like you might be saying \"a olek,\" but I'm not entirely sure what you mean. "
                + "Could you clarify or provide more context? Are you referring to something specific, "
                + "like a name, a phrase, or a cultural reference? Let me know so I can assist you better! 😊";
        assertTrue(ChatAnswerGrounding.isClarificationSeekingNonAnswer(olekClarify));
        assertTrue(ChatAnswerGrounding.isEnglishAssistantNonAnswer(olekClarify));
        assertTrue(ChatAnswerGrounding.shouldRewriteUngroundedAnswer(olekClarify, true));

        String resolved = ChatAnswerGrounding.resolveGroundedAnswer(
                olekClarify, List.of("Olek"), true, true);
        assertEquals("Olek jest na potwierdzonych zdjęciach w bibliotece.", resolved);

        assertTrue(ChatAnswerGrounding.isClarificationSeekingNonAnswer(
                "Nie do końca rozumiem — czy możesz doprecyzować?"));
        assertFalse(ChatAnswerGrounding.isClarificationSeekingNonAnswer(
                "Olek jest na potwierdzonych zdjęciach w bibliotece."));
    }

    @Test
    void englishAssistantNonAnswerDoesNotFlagPolishEvidenceAnswers() {
        assertFalse(ChatAnswerGrounding.isEnglishAssistantNonAnswer(
                "Olek jest na potwierdzonych zdjęciach w bibliotece."));
        assertFalse(ChatAnswerGrounding.isEnglishAssistantNonAnswer(
                "Na zdjęciu stoi Igor w garniturze."));
        assertTrue(ChatAnswerGrounding.isEnglishAssistantNonAnswer(
                "It seems like you might be referring to something specific. Let me know so I can help!"));
    }

    @Test
    void detectsOlekStyleSpeculativeHypothesisList() {
        String olek = """
                Na zdjęciu Olek może robić różne rzeczy, w zależności od kontekstu. Może na przykład:

                1. Pozować – uśmiechać się, robić miny lub przybierać różne pozy.
                2. Wykonywać jakąś aktywność – np. grać w piłkę, jeździć na rowerze, czytać książkę.
                3. Przebywać w określonym miejscu – np. na plaży, w górach, na imprezie.
                4. Interagować z innymi – rozmawiać, śmiać się lub bawić się z przyjaciółmi.
                5. Być uchwycony w naturalnej chwili – np. podczas jedzenia, spaceru czy odpoczynku.

                Jeśli masz więcej szczegółów na temat zdjęcia, mogę spróbować bardziej precyzyjnie odpowiedzieć!
                """;
        assertTrue(ChatAnswerGrounding.isSpeculativeHypothesisList(olek));
        assertTrue(ChatAnswerGrounding.shouldRewriteUngroundedAnswer(olek, true));
        assertTrue(ChatAnswerGrounding.shouldRewriteUngroundedAnswer(olek, false));

        String good = "Olek stoi w czerwonej koszulce obok Igora.";
        assertFalse(ChatAnswerGrounding.isSpeculativeHypothesisList(good));
        assertFalse(ChatAnswerGrounding.shouldRewriteUngroundedAnswer(good, true));
    }

    @Test
    void speculativeHypothesisWithEvidenceBecomesNoDetailFallback() {
        String olek = """
                Na zdjęciu Olek może robić różne rzeczy, w zależności od kontekstu. Może na przykład:

                1. Pozować – uśmiechać się, robić miny lub przybierać różne pozy.
                2. Wykonywać jakąś aktywność – np. grać w piłkę, jeździć na rowerze, czytać książkę.
                3. Przebywać w określonym miejscu – np. na plaży, w górach, na imprezie.

                Jeśli masz więcej szczegółów na temat zdjęcia, mogę spróbować bardziej precyzyjnie odpowiedzieć!
                """;
        String resolved = ChatAnswerGrounding.resolveGroundedAnswer(
                olek, List.of("Olek"), true, true);
        assertEquals(ChatAnswerGrounding.GROUNDED_NO_DETAIL_FALLBACK, resolved);
        assertFalse(resolved.toLowerCase().contains("pozować"));
        assertFalse(resolved.toLowerCase().contains("może robić"));

        // Without certain evidence, leave prose as-is (caller handles NO_EVIDENCE separately).
        assertEquals(olek, ChatAnswerGrounding.resolveGroundedAnswer(
                olek, List.of("Olek"), false, true));
    }

    @Test
    void detectsOlekNozSafetyLecture() {
        String safety = """
                Jeśli masz na myśli sytuację, w której ktoś o imieniu Olek używa noża, ważne jest, aby zachować ostrożność i odpowiedzialność. Używanie noża może być niebezpieczne zarówno dla osoby posługującej się nim, jak i dla innych. Jeśli jest to sytuacja fikcyjna lub związana z jakimś projektem artystycznym, upewnij się, że jest ona przedstawiona w sposób odpowiedzialny i bezpieczny.

                Jeśli natomiast jest to sytuacja realna i istnieje zagrożenie dla czyjegoś bezpieczeństwa, należy natychmiast skontaktować się z odpowiednimi służbami, takimi jak policja, aby zapewnić pomoc i interwencję.

                Jeśli masz konkretne pytanie lub potrzebujesz porady w związku z tą sytuacją, daj mi znać, a postaram się pomóc.
                """;
        assertTrue(ChatAnswerGrounding.isSafetyOrOfftopicLecture(safety));
        assertTrue(ChatAnswerGrounding.shouldRewriteUngroundedAnswer(safety, false, List.of("Olek", "Bartek")));
        // "Olek" appears in the lecture — missing-names is not required when safety shape matches.

        String resolved = ChatAnswerGrounding.resolveGroundedAnswer(
                safety, List.of("Olek", "Bartek"), true, false);
        assertEquals("Na zdjęciu są Olek i Bartek.", resolved);
        assertFalse(resolved.toLowerCase().contains("policj"));
        assertFalse(resolved.toLowerCase().contains("ostrożność"));

        assertFalse(ChatAnswerGrounding.isSafetyOrOfftopicLecture(
                "Olek trzyma nóż w prawej ręce."));

        // Digression that never names co-present Bartek (only generic advice).
        String digression = """
                Jeśli masz na myśli sytuację, w której ktoś używa noża, ważne jest, aby zachować ostrożność.
                Daj mi znać, a postaram się pomóc z tą sprawą bezpieczeństwa.
                """;
        assertTrue(ChatAnswerGrounding.isAnswerMissingEvidenceNames(
                digression, List.of("Olek", "Bartek")));
    }
}

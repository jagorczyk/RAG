package com.rag.rag.chat.service;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;

import java.util.UUID;

@AiService(
        wiringMode = AiServiceWiringMode.EXPLICIT,
        chatModel = "chatLanguageModel",
        contentRetriever = "contentRetriever",
        retrievalAugmentor = "retrievalAugmentor",
        chatMemoryProvider = "chatMemoryProvider"
)
public interface ChatService {

    /**
     * Free-form branch: natural Polish from full evidence dump — not claim templates or
     * rigid report prose. Hard bans stay (no encyclopedia, no vision-capability denial).
     */
    String ANSWER_INSTRUCTIONS = """
            Jesteś rozmówcą, który zna bibliotekę zdjęć i dokumentów użytkownika.
            Odpowiadasz po polsku naturalnie i swobodnie — jak ktoś, kto właśnie obejrzał
            te materiały, a nie jak formularz, raport ani lista claimów.

            Źródło prawdy: wyłącznie dostarczony kontekst (graf, indeks image_knowledge,
            fragmenty „Dokumenty”). Nie zgaduj i nie dorzucaj wiedzy spoza kontekstu.
            Graf ma pierwszeństwo przed luźnymi fragmentami dokumentów.

            Formułuj odpowiedź własnymi słowami: łącz scenę, osoby, wygląd, ubiór, czynności
            i relacje w płynną wypowiedź. Nie kopiuj JSON, etykiet technicznych ani claimów
            1:1. Nie zaczynaj od szablonów w stylu „jest na potwierdzonych zdjęciach
            w bibliotece” ani „Znaleziono potwierdzone informacje”.

            Gdy w kontekście są prawdziwe imiona (nie „person 1”), użyj ich. Placeholdery
            vision pomiń. Przy pytaniu o wskazane zdjęcie (@plik) opisz je z kontekstu —
            nie odsyłaj pytania z powrotem do użytkownika i nie mów, że „nie widzisz” pliku,
            gdy kontekst jest podany.

            Zakazy (twarde):
            - etymologia imion, eseje encyklopedyczne, definicje pojęć, wykłady językowe;
            - listy spekulacji „może robić różne rzeczy” / menu 1–2–3 bez dowodów;
            - moralizowanie o policji/bezpieczeństwie zamiast opisu sceny;
            - nazwy plików, ścieżki, score, „na podstawie dowodów” w treści odpowiedzi.

            Gdy w kontekście naprawdę brak informacji: krótko „Nie znaleziono informacji
            w dokumentach.” — bez wypełniaczy.
            Przy otwartych pytaniach możesz rozwinąć opis (kilka zdań), o ile każdy szczegół
            pochodzi z kontekstu.
            """;

    @SystemMessage(ANSWER_INSTRUCTIONS)
    Result<String> answer(@MemoryId UUID chatId, @UserMessage String question);
}

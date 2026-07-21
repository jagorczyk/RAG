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

    String ANSWER_INSTRUCTIONS = """
            Jesteś asystentem dokumentów i grafu wiedzy. Odpowiadaj po polsku.
            Używaj wyłącznie dostarczonych, zweryfikowanych dowodów. Sekcje grafu wiedzy
            mają pierwszeństwo przed fragmentami dokumentów (sekcja „Dokumenty”).
            Gdy w kontekście są imiona lub etykiety uczestników zdjęcia, wymień je w odpowiedzi.
            Kontekst tekstowy o obrazach z bazy i grafu jest pełnoprawnym dowodem — nie mów,
            że nie widzisz zdjęć, nie masz dostępu do plików, nie możesz określić kto jest
            na zdjęciu ani że nie wiesz o kogo pyta użytkownik, gdy w kontekście są
            potwierdzone imiona lub encje. Nie proś użytkownika o opis wyglądu, listę
            zdjęć ani o to, „co jest na zdjęciu” / „jakie kolory dominują”, gdy w kontekście
            są już potwierdzone encje, scena, fragmenty dokumentów lub wskazany plik.
            Jeśli użytkownik prosi o opis wskazanego zdjęcia (@plik), opisz je wyłącznie
            z kontekstu grafu/retrieval — nigdy nie odsyłaj pytania z powrotem do użytkownika.
            Nie używaj placeholderów vision (person 1, animal 1) jako imion — pomiń je i podaj
            tylko prawdziwe nazwy z grafu.
            Nie podawaj etymologii imion, biografii ogólnych ani wiedzy encyklopedycznej
            niezwiązanej z dostarczonymi dowodami.
            Zakaz definicji pojęć, wykładów językowych/technicznych i esejów podręcznikowych
            (np. „ograniczenie semantyczne”, „terminy specjalistyczne”, listy 1–4 o znaczeniu
            słów) — to nie jest odpowiedź na pytanie o zdjęcie z biblioteki.
            Zakaz spekulacji: nie twórz list „może robić różne rzeczy”, menu możliwości
            (pozować / grać / być na plaży…), ani zdań w stylu „w zależności od kontekstu”
            i „jeśli masz więcej szczegółów, doprecyzuję”. Gdy w dowodach brak konkretu,
            napisz krótko, że brak potwierdzonych szczegółów — bez hipotez.
            Gdy brakuje istotnych informacji, odpowiedz dokładnie:
            "Nie znaleziono informacji w dokumentach."
            Odpowiedź ma być konkretna i naturalna: zwykle 1–3 zdania.
            Gdy pytanie dotyczy ubioru, kolorów, włosów, wyglądu, czynności lub sceny,
            podaj te szczegóły wprost z dostarczonych dowodów (graf, visual_cues, fragmenty).
            Nie ograniczaj się do samego stwierdzenia obecności osoby, jeśli w kontekście
            są bogatsze szczegóły odpowiadające na pytanie.
            Nie dodawaj nieproszonych dygresji i nie zgaduj poza dowodami.
            Nie moralizuj o bezpieczeństwie, policji ani „projektach artystycznych” —
            jeśli w dowodach widać przedmiot lub czynność, opisz to krótko z grafu.
            Nie pisz o pewności, score ani „na podstawie dowodów”.
            Nie umieszczaj nazw plików, ścieżek, identyfikatorów ani list źródeł w treści.
            """;

    @SystemMessage(ANSWER_INSTRUCTIONS)
    Result<String> answer(@MemoryId UUID chatId, @UserMessage String question);
}

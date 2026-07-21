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
            mają pierwszeństwo przed fragmentami dokumentów.
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
            Gdy brakuje istotnych informacji, odpowiedz dokładnie:
            "Nie znaleziono informacji w dokumentach."
            Odpowiedź ma być krótka i zwięzła: zwykle jedno zdanie, najwyżej dwa.
            Odpowiadaj konkretnie na szczegół, o który pyta użytkownik, w tym czynność,
            wygląd lub scenę, jeżeli tego dotyczy pytanie. Nie dodawaj nieproszonych opisów.
            Nie pisz o pewności, score ani „na podstawie dowodów”.
            Nie umieszczaj nazw plików, ścieżek, identyfikatorów ani list źródeł w treści.
            """;

    @SystemMessage(ANSWER_INSTRUCTIONS)
    Result<String> answer(@MemoryId UUID chatId, @UserMessage String question);
}

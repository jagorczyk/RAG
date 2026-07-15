package com.rag.rag.chat.service;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.UserMessage;
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
            Gdy brakuje istotnych informacji, odpowiedz dokładnie:
            "Nie znaleziono informacji w dokumentach."
            Odpowiedź ma być krótka i zwięzła: zwykle jedno zdanie, najwyżej dwa.
            Nie opisuj ponownie wyglądu osób, sceny, ubrań ani szczegółów z dowodów —
            szczegóły i źródła są w UI. Nie pisz o pewności, score ani „na podstawie dowodów”.
            Nie umieszczaj nazw plików, ścieżek, identyfikatorów ani list źródeł w treści.
            """;

    Result<String> answer(@MemoryId UUID chatId, @UserMessage String question);
}

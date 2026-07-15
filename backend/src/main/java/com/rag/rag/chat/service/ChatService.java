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
            mają pierwszeństwo przed fragmentami dokumentów. Jasno zaznacz niepewność.
            Gdy brakuje istotnych informacji, odpowiedz dokładnie:
            "Nie znaleziono informacji w dokumentach."
            Odpowiadaj jak najprościej i zwięźle, bez wstępu i bez listy źródeł.
            Nie umieszczaj nazw plików, ścieżek, identyfikatorów ani cytowań technicznych
            w treści odpowiedzi — źródła są prezentowane wyłącznie na liście źródeł w UI.
            """;

    Result<String> answer(@MemoryId UUID chatId, @UserMessage String question);
}

package com.rag.rag.chat.service;

import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/** Produces a short visual answer without document retrieval or evidence narration. */
@Service
@RequiredArgsConstructor
public class VerifiedVisualAnswerService {
    @Qualifier("chatLanguageModel")
    private final ChatLanguageModel chatModel;

    /**
     * One short Polish sentence for confirmed visual matches.
     * Evidence details belong in the UI sources list, not in the prose answer.
     */
    public String answer(String question, int confirmedCount) {
        if (confirmedCount <= 0) {
            return "Nie znaleziono potwierdzonych dowodów wizualnych.";
        }
        String prompt = """
                %s

                Zadanie: napisz dokładnie jedną krótką odpowiedź po polsku (najwyżej 15 słów).
                Potwierdzono %d zdjęć spełniających prośbę użytkownika.
                Styl: naturalny, jak "Oto zdjęcia przedstawiające samego Olka." albo "Oto zdjęcia z Igorem i Olkiem."
                Zasady:
                - nie opisuj wyglądu, ubrań, włosów, sceny ani tła
                - nie podawaj pewności, score ani dowodów
                - nie wymieniaj plików, ścieżek ani liczby w formie technicznej
                - nie zaczynaj od "Na podstawie dowodów"
                - nie dodawaj drugiego zdania

                Pytanie użytkownika: %s
                """.formatted(ChatService.ANSWER_INSTRUCTIONS, confirmedCount, question == null ? "" : question);
        String answer = chatModel.generate(prompt);
        if (answer == null || answer.isBlank()) {
            return "Oto potwierdzone zdjęcia.";
        }
        return answer.trim();
    }

    /** Backward-compatible entry used by tests that still pass a free-form evidence prompt. */
    public String answer(String evidencePrompt) {
        return chatModel.generate(ChatService.ANSWER_INSTRUCTIONS + "\n\n" + evidencePrompt);
    }
}

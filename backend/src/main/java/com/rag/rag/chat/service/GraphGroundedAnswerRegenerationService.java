package com.rag.rag.chat.service;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/** One bounded, memory-free regeneration after post-answer graph attribution fails. */
@Slf4j
@Service
public class GraphGroundedAnswerRegenerationService {

    private final ChatLanguageModel answerModel;

    public GraphGroundedAnswerRegenerationService(
            @Qualifier("answerLanguageModel") ChatLanguageModel answerModel) {
        this.answerModel = answerModel;
    }

    public String regenerate(String question, String evidenceContext) {
        if (answerModel == null || evidenceContext == null || evidenceContext.isBlank()) return "";
        try {
            var response = answerModel.generate(
                    SystemMessage.from(ChatService.ANSWER_INSTRUCTIONS),
                    UserMessage.from("""
                            Poprzednia próba odpowiedzi została odrzucona, ponieważ jej konkretnych
                            informacji nie dało się przypisać do dowodów. Napisz odpowiedź od nowa,
                            bez korzystania z historii rozmowy ani wiedzy spoza poniższego kontekstu.
                            Każde zdanie i każdy szczegół muszą wynikać bezpośrednio z dowodów.
                            Nie dopowiadaj brakujących scen, ubrań, napisów, czynności ani liczby zdjęć.
                            Nie dopisuj ocen, emocji, intencji, nastroju ani atmosfery.
                            Odrębne zdjęcia lub sceny opisz osobnymi zdaniami; nie łącz ich w jedną
                            równoczesną sytuację jednym spójnikiem.
                            Odpowiedz zwykłym tekstem w jednym do trzech krótkich zdań, bez list i Markdownu.
                            Jeśli kontekst nie wystarcza,
                            napisz: Nie znaleziono potwierdzonych informacji.

                            Dowody:
                            %s

                            Pytanie: %s
                            """.formatted(evidenceContext, question == null ? "" : question)));
            return response == null || response.content() == null
                    ? ""
                    : response.content().text().trim();
        } catch (Exception e) {
            log.warn("Graph-grounded answer regeneration failed: {}", e.getMessage());
            return "";
        }
    }
}

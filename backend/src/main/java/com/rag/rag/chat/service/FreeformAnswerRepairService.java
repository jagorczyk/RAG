package com.rag.rag.chat.service;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/** One bounded regeneration for a hard failure shape on an otherwise grounded GRAPH answer. */
@Slf4j
@Service
public class FreeformAnswerRepairService {

    private final ChatLanguageModel answerModel;

    public FreeformAnswerRepairService(
            @Qualifier("answerLanguageModel") ChatLanguageModel answerModel) {
        this.answerModel = answerModel;
    }

    public String repair(String question, String evidenceContext, String failedAnswer) {
        if (answerModel == null || evidenceContext == null || evidenceContext.isBlank()) return "";
        try {
            var response = answerModel.generate(
                    SystemMessage.from(ChatService.ANSWER_INSTRUCTIONS),
                    UserMessage.from("""
                            Poprzednia odpowiedź miała twardy błąd (odmowa, dygresja, spekulacja albo zły język).
                            Napisz odpowiedź od nowa. Zachowaj swobodną, naturalną polszczyznę i wykorzystaj
                            wyłącznie poniższe dowody. Nie komentuj poprzedniej próby.
                            Zwróć zwykły tekst: od jednego do trzech krótkich zdań, bez list i Markdownu.
                            Pomiń każdą ocenę, emocję, intencję lub atmosferę niewymienioną wprost w dowodach.
                            Odrębne zdjęcia lub sceny opisz osobnymi zdaniami; nie łącz ich w jedną sytuację.

                            Dowody:
                            %s

                            Pytanie: %s
                            """.formatted(evidenceContext, question == null ? "" : question)));
            return response == null || response.content() == null ? "" : response.content().text();
        } catch (Exception e) {
            log.warn("Grounded answer repair failed: {}", e.getMessage());
            return "";
        }
    }
}

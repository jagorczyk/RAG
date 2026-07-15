package com.rag.rag.chat.service;

import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/** Produces a visual answer without augmenting it with unrelated document retrieval. */
@Service
@RequiredArgsConstructor
public class VerifiedVisualAnswerService {
    @Qualifier("chatLanguageModel")
    private final ChatLanguageModel chatModel;

    public String answer(String evidencePrompt) {
        return chatModel.generate(ChatService.ANSWER_INSTRUCTIONS + "\n\n" + evidencePrompt);
    }
}

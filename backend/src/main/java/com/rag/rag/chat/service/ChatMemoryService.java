package com.rag.rag.chat.service;

import com.rag.rag.chat.entity.ChatMemoryEntity;
import com.rag.rag.chat.repository.ChatMemoryRepository;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ChatMemoryService implements ChatMemoryStore {

    private final ChatMemoryRepository repository;

    public ChatMemoryService(ChatMemoryRepository repository) {
        this.repository = repository;
    }

    @Transactional
    @Override
    public List<ChatMessage> getMessages(Object memoryId) {

        if (memoryId instanceof UUID chatId) {
            return repository.findById(chatId)
                    .map(ChatMemoryEntity::getMessages)
                    .map(ChatMessageDeserializer::messagesFromJson)
                    .map(messages -> messages.stream()
                            .filter(message -> !(message instanceof SystemMessage))
                            .toList())
                    .orElseGet(ArrayList::new);
        }
        return new ArrayList<>();
    }

    @Transactional
    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        if (memoryId instanceof UUID chatId) {
            List<ChatMessage> cleanMessages = messages.stream().map(msg -> {
                if (msg instanceof UserMessage userMsg) {
                    String text = userMsg.singleText();
                    String cleaned = ChatUserMessageNormalizer.extractOriginalQuestion(text);
                    if (text == null || !cleaned.equals(text)) {
                        return UserMessage.from(cleaned);
                    }
                }
                return msg;
            }).toList();

            String json = ChatMessageSerializer.messagesToJson(cleanMessages);
            ChatMemoryEntity entity = repository.findById(chatId)
                    .orElseGet(() -> {
                        ChatMemoryEntity newEntity = new ChatMemoryEntity();
                        newEntity.setChatId(chatId);
                        newEntity.setName(chatId.toString());
                        return newEntity;
                    });
            entity.setMessages(json);
            repository.save(entity);
        }
    }

    /**
     * Replaces the last AI turn in LangChain memory with the post-grounded UI answer so
     * follow-ups condition on the same text the user saw (not raw model denials/greetings).
     * Also re-normalizes any user blobs still present. No-op when text is blank or no AI turn.
     */
    @Transactional
    public void replaceLastAiMessage(UUID chatId, String groundedAnswer) {
        if (chatId == null || groundedAnswer == null || groundedAnswer.isBlank()) {
            return;
        }
        List<ChatMessage> current = getMessages(chatId);
        if (current.isEmpty()) {
            return;
        }
        List<ChatMessage> updated = new ArrayList<>(current.size());
        int lastAi = -1;
        for (int i = 0; i < current.size(); i++) {
            if (current.get(i) instanceof AiMessage) {
                lastAi = i;
            }
        }
        if (lastAi < 0) {
            return;
        }
        for (int i = 0; i < current.size(); i++) {
            ChatMessage msg = current.get(i);
            if (i == lastAi) {
                updated.add(AiMessage.from(groundedAnswer.trim()));
                continue;
            }
            if (msg instanceof UserMessage userMsg) {
                String text = userMsg.singleText();
                String cleaned = ChatUserMessageNormalizer.extractOriginalQuestion(text);
                updated.add(UserMessage.from(cleaned));
            } else {
                updated.add(msg);
            }
        }
        updateMessages(chatId, updated);
    }

    @Override
    public void deleteMessages(Object memoryId) {
        if (memoryId instanceof UUID chatId) {
            repository.deleteById(chatId);
        }
    }
}

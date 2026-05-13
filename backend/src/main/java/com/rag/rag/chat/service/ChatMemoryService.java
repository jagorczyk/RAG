package com.rag.rag.chat.service;

import com.rag.rag.chat.entity.ChatMemoryEntity;
import com.rag.rag.chat.repository.ChatMemoryRepository;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
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
                    if (text != null && text.contains("DANE Z BAZY DANYCH:\n") && text.contains("PYTANIE UŻYTKOWNIKA: ")) {
                        int start = text.indexOf("PYTANIE UŻYTKOWNIKA: ") + "PYTANIE UŻYTKOWNIKA: ".length();
                        int end = text.indexOf("\n\nDANE Z BAZY DANYCH:\n");
                        if (start != -1 && end != -1 && end > start) {
                            String originalQuestion = text.substring(start, end);
                            return UserMessage.from(originalQuestion);
                        }
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

    @Override
    public void deleteMessages(Object memoryId) {
        if (memoryId instanceof UUID chatId) {
            repository.deleteById(chatId);
        }
    }
}

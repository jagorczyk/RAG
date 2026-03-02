package com.rag.rag.Service;

import com.rag.rag.Entity.ChatMemoryEntity;
import com.rag.rag.Repository.ChatMemoryRepository;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        if (memoryId instanceof UUID chatId) {
            String json = ChatMessageSerializer.messagesToJson(messages);
            ChatMemoryEntity entity = new ChatMemoryEntity(chatId, json);
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

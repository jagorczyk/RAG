package com.rag.rag.chat.service;

import com.rag.rag.chat.entity.ChatMessageEntity;
import com.rag.rag.knowledge.entity.EntityMention;
import com.rag.rag.knowledge.entity.KnowledgeEntity;
import com.rag.rag.knowledge.entity.MentionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatEntityReferenceServiceTest {

    @Mock
    private com.rag.rag.chat.repository.ChatMessageRepository chatMessageRepository;
    @Mock
    private com.rag.rag.knowledge.repository.EntityMentionRepository mentionRepository;
    @Mock
    private com.rag.rag.knowledge.repository.KnowledgeEntityRepository entityRepository;

    @InjectMocks
    private ChatEntityReferenceService service;

    @Test
    void shouldResolveMalePersonFromRecentFileMentions() {
        UUID chatId = UUID.randomUUID();
        String filePath = "dir://test/received_179227088419436.jpeg";

        ChatMessageEntity aiMessage = ChatMessageEntity.builder()
                .role("AI")
                .textContext("Zdjęcie przedstawia scenę łazienkową.")
                .imagePaths(List.of(filePath))
                .build();

        when(chatMessageRepository.findAllByChatIdOrderByCreatedAtAsc(chatId))
                .thenReturn(List.of(aiMessage));

        KnowledgeEntity igor = KnowledgeEntity.builder()
                .id(UUID.randomUUID())
                .displayName("Igor")
                .type("PERSON")
                .build();

        EntityMention mention = EntityMention.builder()
                .entity(igor)
                .filePath(filePath)
                .label("mężczyzna trzymający parasol")
                .confidence(new BigDecimal("0.900"))
                .status(MentionStatus.CONFIRMED)
                .build();

        when(mentionRepository.findByFilePath(filePath)).thenReturn(List.of(mention));

        Optional<String> resolved = service.resolveReference(chatId, "co to za mężczyzna?");

        assertTrue(resolved.isPresent());
        assertEquals("Igor", resolved.get());
    }
}

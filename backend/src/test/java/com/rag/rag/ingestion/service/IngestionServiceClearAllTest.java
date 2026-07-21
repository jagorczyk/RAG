package com.rag.rag.ingestion.service;

import com.rag.rag.auth.security.CurrentUserService;
import com.rag.rag.chat.entity.ChatMemoryEntity;
import com.rag.rag.chat.repository.ChatMemoryRepository;
import com.rag.rag.chat.repository.ChatMessageRepository;
import com.rag.rag.folder.entity.FileEntity;
import com.rag.rag.folder.entity.FolderEntity;
import com.rag.rag.folder.repository.FileRepository;
import com.rag.rag.folder.repository.FolderRepository;
import com.rag.rag.ingestion.cache.ImageAnalysisCacheService;
import com.rag.rag.knowledge.entity.EntityMention;
import com.rag.rag.knowledge.entity.KnowledgeEntity;
import com.rag.rag.knowledge.extraction.StructuredVisionExtractor;
import com.rag.rag.knowledge.face.FaceIdentityService;
import com.rag.rag.knowledge.face.FaceRecognitionClient;
import com.rag.rag.knowledge.identity.IdentityResolutionService;
import com.rag.rag.knowledge.repository.EntityAliasRepository;
import com.rag.rag.knowledge.repository.EntityMentionRepository;
import com.rag.rag.knowledge.repository.FaceEmbeddingRepository;
import com.rag.rag.knowledge.repository.FaceObservationRepository;
import com.rag.rag.knowledge.repository.FactRepository;
import com.rag.rag.knowledge.repository.IdentitySuggestionRepository;
import com.rag.rag.knowledge.repository.KnowledgeEntityRepository;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IngestionServiceClearAllTest {

    @Mock private EmbeddingStoreIngestor ingestor;
    @Mock private FileRepository fileRepository;
    @Mock private FolderRepository folderRepository;
    @Mock private StructuredVisionExtractor extractor;
    @Mock private EntityMentionRepository mentionRepo;
    @Mock private FactRepository factRepo;
    @Mock private IdentitySuggestionRepository suggestionRepo;
    @Mock private FaceEmbeddingRepository faceEmbeddingRepository;
    @Mock private FaceObservationRepository faceObservationRepository;
    @Mock private FaceIdentityService faceIdentityService;
    @Mock private FaceRecognitionClient faceRecognitionClient;
    @Mock private ImageAnalysisCacheService imageAnalysisCacheService;
    @Mock private EntityAliasRepository aliasRepo;
    @Mock private KnowledgeEntityRepository knowledgeEntityRepo;
    @Mock private IdentityResolutionService identityResolutionService;
    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private CurrentUserService currentUserService;
    @Mock private ObjectProvider<com.rag.rag.ingestion.messaging.DocumentIngestPublisher> documentIngestPublisher;
    @Mock private ChatMemoryRepository chatMemoryRepository;
    @Mock private ChatMessageRepository chatMessageRepository;

    private IngestionService service;

    @BeforeEach
    void setUp() {
        service = new IngestionService(
                ingestor, fileRepository, folderRepository, extractor, mentionRepo, factRepo,
                suggestionRepo, faceEmbeddingRepository, faceObservationRepository, faceIdentityService,
                faceRecognitionClient, imageAnalysisCacheService, aliasRepo, knowledgeEntityRepo,
                identityResolutionService, jdbcTemplate, transactionManager, currentUserService,
                documentIngestPublisher, chatMemoryRepository, chatMessageRepository);
    }

    @Test
    void clearAllDataForOwnerRemovesChatsPeopleFoldersAndFiles() {
        UUID ownerId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        UUID mentionId = UUID.randomUUID();

        ChatMemoryEntity chat = ChatMemoryEntity.builder().chatId(chatId).ownerId(ownerId).name("rozmowa").build();
        when(chatMemoryRepository.findAllByOwnerIdOrderByLastMessageAtDesc(ownerId)).thenReturn(List.of(chat));

        FileEntity file = FileEntity.builder().path("dir://a/x.jpg").ownerId(ownerId).build();
        when(fileRepository.findAllByOwnerId(ownerId)).thenReturn(List.of(file));
        when(fileRepository.findByPath("dir://a/x.jpg")).thenReturn(Optional.of(file));
        when(mentionRepo.findByFilePath("dir://a/x.jpg")).thenReturn(List.of());

        KnowledgeEntity person = KnowledgeEntity.builder().id(entityId).displayName("Igor").ownerId(ownerId).build();
        when(knowledgeEntityRepo.findAllByOwnerId(ownerId)).thenReturn(List.of(person));
        EntityMention mention = EntityMention.builder().id(mentionId).entity(person).build();
        when(mentionRepo.findByEntityId(entityId)).thenReturn(List.of(mention));

        FolderEntity folder = new FolderEntity();
        folder.setId(UUID.randomUUID());
        folder.setName("wakacje");
        folder.setOwnerId(ownerId);
        when(folderRepository.findAllByOwnerIdOrderByUpdatedAtDesc(ownerId)).thenReturn(List.of(folder));

        service.clearAllDataForOwner(ownerId);

        verify(chatMessageRepository).deleteByChatId(chatId);
        verify(chatMemoryRepository).delete(chat);
        verify(fileRepository).delete(file);
        verify(jdbcTemplate).update(anyString(), eq("dir://a/x.jpg"));
        verify(suggestionRepo).deleteByMentionIds(List.of(mentionId));
        verify(factRepo).deleteByMentionIds(List.of(mentionId));
        verify(faceObservationRepository).deleteByMentionIds(List.of(mentionId));
        verify(faceEmbeddingRepository).deleteByMentionIdIn(List.of(mentionId));
        verify(faceEmbeddingRepository).deleteByEntityId(entityId);
        verify(mentionRepo).deleteByEntityId(entityId);
        verify(aliasRepo).deleteByEntityId(entityId);
        verify(knowledgeEntityRepo).delete(person);
        verify(folderRepository).delete(folder);
    }
}

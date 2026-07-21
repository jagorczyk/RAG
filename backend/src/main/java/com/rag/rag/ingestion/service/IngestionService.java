package com.rag.rag.ingestion.service;

import com.rag.rag.auth.security.CurrentUserService;
import com.rag.rag.chat.entity.ChatMemoryEntity;
import com.rag.rag.chat.repository.ChatMemoryRepository;
import com.rag.rag.chat.repository.ChatMessageRepository;
import com.rag.rag.folder.dto.UploadResultDto;
import com.rag.rag.folder.entity.FolderEntity;
import com.rag.rag.folder.entity.FileEntity;
import com.rag.rag.folder.repository.FileRepository;
import com.rag.rag.folder.repository.FolderRepository;
import com.rag.rag.ingestion.dto.SourceDto;
import com.rag.rag.ingestion.messaging.DocumentIngestPublisher;
import com.rag.rag.ingestion.messaging.DocumentUploadedEvent;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.service.Result;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.rag.folder.entity.IngestionStatus;
import com.rag.rag.ingestion.cache.ImageAnalysisAnalyzer;
import com.rag.rag.ingestion.cache.ImageAnalysisCacheService;
import com.rag.rag.ingestion.cache.ImageAnalysisStatus;
import com.rag.rag.knowledge.entity.AliasSource;
import com.rag.rag.knowledge.entity.EntityMention;
import com.rag.rag.knowledge.entity.LivingEntityTypes;
import com.rag.rag.knowledge.entity.MentionStatus;
import com.rag.rag.knowledge.entity.OrphanEntityCleanupPolicy;
import com.rag.rag.knowledge.extraction.ExtractedEntityDto;
import com.rag.rag.knowledge.extraction.FaceAnchorImageRenderer;
import com.rag.rag.knowledge.extraction.StructuredVisionExtractor;
import com.rag.rag.knowledge.extraction.StructuredVisionExtractor.ExtractionResult;
import com.rag.rag.knowledge.extraction.VisionResultDto;
import com.rag.rag.knowledge.extraction.VisibleTextDto;
import com.rag.rag.knowledge.fact.Fact;
import com.rag.rag.knowledge.face.FaceEmbedding;
import com.rag.rag.knowledge.face.FaceIdentityService;
import com.rag.rag.knowledge.face.FaceAnalyzeResponse;
import com.rag.rag.knowledge.face.DetectedFaceDto;
import com.rag.rag.knowledge.face.FaceRecognitionClient;
import com.rag.rag.knowledge.identity.IdentityResolutionService;
import com.rag.rag.knowledge.repository.EntityAliasRepository;
import com.rag.rag.knowledge.repository.EntityMentionRepository;
import com.rag.rag.knowledge.repository.FaceEmbeddingRepository;
import com.rag.rag.knowledge.repository.FaceObservationRepository;
import com.rag.rag.knowledge.repository.FactRepository;
import com.rag.rag.knowledge.repository.IdentitySuggestionRepository;
import com.rag.rag.knowledge.repository.KnowledgeEntityRepository;
import com.rag.rag.knowledge.entity.KnowledgeEntity;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class IngestionService {

    @Value("${vision.image.max-width:800}")
    private int MAX_WIDTH;

    @Value("${vision.image.max-height:800}")
    private int MAX_HEIGHT;

    @Value("${vision.structured.analyzer-version:v1}")
    private String visionAnalyzerVersion;

    @Value("${face.analyzer-version:v1}")
    private String faceAnalyzerVersion;

    @Value("${rag.ingest.async-enabled:true}")
    private boolean asyncIngestEnabled;

    private final EmbeddingStoreIngestor ingestor;
    private final FileRepository fileRepository;
    private final FolderRepository folderRepository;
    private final StructuredVisionExtractor extractor;
    private final EntityMentionRepository mentionRepo;
    private final FactRepository factRepo;
    private final IdentitySuggestionRepository suggestionRepo;
    private final FaceEmbeddingRepository faceEmbeddingRepository;
    private final FaceObservationRepository faceObservationRepository;
    private final FaceIdentityService faceIdentityService;
    private final FaceRecognitionClient faceRecognitionClient;
    private final ImageAnalysisCacheService imageAnalysisCacheService;
    private final EntityAliasRepository aliasRepo;
    private final KnowledgeEntityRepository knowledgeEntityRepo;
    private final IdentityResolutionService identityResolutionService;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final CurrentUserService currentUserService;
    private final ObjectProvider<DocumentIngestPublisher> documentIngestPublisher;
    private final ChatMemoryRepository chatMemoryRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public IngestionService(
            EmbeddingStoreIngestor ingestor,
            FileRepository fileRepository,
            FolderRepository folderRepository,
            StructuredVisionExtractor extractor,
            EntityMentionRepository mentionRepo,
            FactRepository factRepo,
            IdentitySuggestionRepository suggestionRepo,
            FaceEmbeddingRepository faceEmbeddingRepository,
            FaceObservationRepository faceObservationRepository,
            FaceIdentityService faceIdentityService,
            FaceRecognitionClient faceRecognitionClient,
            ImageAnalysisCacheService imageAnalysisCacheService,
            EntityAliasRepository aliasRepo,
            KnowledgeEntityRepository knowledgeEntityRepo,
            IdentityResolutionService identityResolutionService,
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            CurrentUserService currentUserService,
            ObjectProvider<DocumentIngestPublisher> documentIngestPublisher,
            ChatMemoryRepository chatMemoryRepository,
            ChatMessageRepository chatMessageRepository
    ) {
        this.ingestor = ingestor;
        this.fileRepository = fileRepository;
        this.folderRepository = folderRepository;
        this.extractor = extractor;
        this.mentionRepo = mentionRepo;
        this.factRepo = factRepo;
        this.suggestionRepo = suggestionRepo;
        this.faceEmbeddingRepository = faceEmbeddingRepository;
        this.faceObservationRepository = faceObservationRepository;
        this.faceIdentityService = faceIdentityService;
        this.faceRecognitionClient = faceRecognitionClient;
        this.imageAnalysisCacheService = imageAnalysisCacheService;
        this.aliasRepo = aliasRepo;
        this.knowledgeEntityRepo = knowledgeEntityRepo;
        this.identityResolutionService = identityResolutionService;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.currentUserService = currentUserService;
        this.documentIngestPublisher = documentIngestPublisher;
        this.chatMemoryRepository = chatMemoryRepository;
        this.chatMessageRepository = chatMessageRepository;
    }

    private Document chooseParser(byte[] fileData, String path, String fileName, String extension) {
        if (extension == null) return null;

        return switch (extension.toLowerCase()) {
            case "txt" -> processTextFile(fileData, path, fileName);
            case "pdf" -> processPdfFile(fileData, path, fileName);
            case "png", "jpg", "jpeg" -> processImage(fileData, path, fileName);
            default -> {
                log.warn("Skipped file: {}, unsupported file extension.", path);
                yield null;
            }
        };
    }

    private FileEntity ensureFileEntityExists(String path, String fileName, String mimeType, byte[] imageData) {
        return ensureFileEntityExists(path, fileName, mimeType, imageData, null);
    }

    private FileEntity ensureFileEntityExists(
            String path,
            String fileName,
            String mimeType,
            byte[] imageData,
            String contentHash
    ) {
        FileEntity entity = fileRepository.findByPath(path).orElseGet(() -> FileEntity.builder()
                .path(path)
                .build());
        entity.setFileName(fileName);
        entity.setFileType(mimeType);
        if (imageData != null) {
            entity.setImageData(imageData);
        }
        if (contentHash != null) {
            entity.setContentHash(contentHash);
        }
        if (entity.getOwnerId() == null) {
            currentUserService.findUserId().ifPresent(entity::setOwnerId);
        }
        return fileRepository.save(entity);
    }

    public Document processTextFile(byte[] fileData, String path, String fileName) {
        try (InputStream stream = new ByteArrayInputStream(fileData)) {
            ensureFileEntityExists(path, fileName, "text/plain", null);
            return new TextDocumentParser().parse(stream);
        } catch (Exception e) {
            throw new RuntimeException("Error while processing text file: " + path, e);
        }
    }

    public Document processPdfFile(byte[] fileData, String path, String fileName) {
        try (InputStream stream = new ByteArrayInputStream(fileData)) {
            ensureFileEntityExists(path, fileName, "application/pdf", null);
            return new ApacheTikaDocumentParser().parse(stream);
        } catch (Exception e) {
            throw new RuntimeException("Error while processing pdf file: " + path, e);
        }
    }

    public Document processImage(byte[] imageData, String path, String fileName) {
        try {
            ImagePayloadDecoder.DecodedImage decodedImage = ImagePayloadDecoder.decode(imageData, fileName);
            String format = decodedImage.format();
            String mimeType = decodedImage.mimeType();

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            Thumbnails.of(decodedImage.image())
                    .size(MAX_WIDTH, MAX_HEIGHT)
                    .outputFormat(format)
                    .toOutputStream(outputStream);

            byte[] processedBytes = outputStream.toByteArray();
            String contentHash = sha256Hex(imageData);

            FileEntity fileEntity = ensureFileEntityExists(path, fileName, mimeType, processedBytes, contentHash);
            fileEntity.setVisionAnalysisStatus(ImageAnalysisStatus.PROCESSING);
            fileEntity.setFaceAnalysisStatus(ImageAnalysisStatus.PROCESSING);
            fileRepository.save(fileEntity);

            FaceAnalyzeResponse faceResponse = loadFaceResponse(contentHash, processedBytes, fileName);
            List<DetectedFaceDto> detectedFaces = detectedFaces(faceResponse);
            FaceAnchorImageRenderer.AnchoredImage anchoredImage = FaceAnchorImageRenderer.render(
                    processedBytes, format, detectedFaces);
            String base64Image = Base64.getEncoder().encodeToString(anchoredImage.bytes());
            ExtractionResult result = loadVisionResult(contentHash, base64Image, mimeType,
                    anchoredImage.anchors().keySet());
            setAnalysisStatus(path, ImageAnalysisAnalyzer.VISION, ImageAnalysisStatus.COMPLETED);

            if (result.isStructured() && result.resultDto() != null) {
                VisionResultDto dto = result.resultDto();
                String entityTag = null;
                
                var fileOpt = fileRepository.findByPath(path);
                if (fileOpt.isPresent()) {
                    FileEntity f = fileOpt.get();
                    entityTag = f.getEntityTag();
                    f.setIngestionStatus(IngestionStatus.EXTRACTED);
                    fileRepository.save(f);
                }

                StringBuilder canonicalText = new StringBuilder();
                if (dto.getScene() != null) {
                    canonicalText.append("Scena: ").append(dto.getScene()).append(". ");
                }
                if (dto.getSceneSummary() != null && !dto.getSceneSummary().isBlank()) {
                    canonicalText.append("Kontekst sceny: ").append(dto.getSceneSummary()).append(". ");
                }
                canonicalText.append("Plik: ").append(fileName).append(". ");

                factRepo.deleteByFilePath(path);
                fileOpt.ifPresent(file -> {
                    file.setImageScene(dto.getScene());
                    file.setImageSummary(dto.getSceneSummary());
                    file.setSceneAttributes(writeSceneAttributes(dto));
                    file.setVisibleTexts(writeJson(dto.getVisibleTexts()));
                    file.setStructuredVisionContext(writeJson(dto));
                    fileRepository.save(file);
                });

                if (dto.getEntities() != null) {
                    List<ExtractedEntityDto> livingEntities = dto.getEntities().stream()
                            .filter(entity -> entity != null && LivingEntityTypes.isSupported(entity.getType()))
                            .toList();
                    Map<String, EntityMention> existingPendingByLabel = mentionRepo.findByFilePath(path).stream()
                            .filter(existing -> existing.getStatus() != MentionStatus.REJECTED
                                    && existing.getLabel() != null
                                    && !"Osoba do weryfikacji".equalsIgnoreCase(existing.getLabel()))
                            .collect(Collectors.toMap(
                                    existing -> existing.getLabel().toLowerCase(Locale.ROOT).trim(),
                                    existing -> existing,
                                    (first, ignored) -> first));
                    List<EntityMention> existingMentions = new ArrayList<>(mentionRepo.findByFilePath(path));
                    // Legacy named mentions stored their geometry on face_embeddings only. Hydrate it before
                    // matching the new face-anchored projection so facts migrate onto the confirmed mention.
                    for (FaceEmbedding embedding : faceEmbeddingRepository.findByFilePath(path)) {
                        EntityMention linked = embedding.getMention();
                        if (linked != null && embedding.getBbox() != null && embedding.getBbox().length >= 4
                                && (linked.getBbox() == null || linked.getBbox().isBlank())) {
                            linked.setBbox(writeJson(embedding.getBbox()));
                            mentionRepo.save(linked);
                        }
                    }
                    Set<UUID> assignedExistingMentions = new HashSet<>();
                    Map<String, EntityMention> mentionsByLabel = new LinkedHashMap<>();
                    int unnamedPersonIndex = 0;
                    int unnamedAnimalIndex = 0;
                    long personCount = livingEntities.stream()
                            .filter(entity -> LivingEntityTypes.PERSON.equals(LivingEntityTypes.normalize(entity.getType())))
                            .count();
                    String tagForResolve = entityTag != null && personCount <= 1 ? entityTag : null;

                    for (ExtractedEntityDto p : livingEntities) {
                        String entityType = LivingEntityTypes.normalize(p.getType());
                        String label = p.getLabel();
                        if (label == null || label.isBlank()) {
                            if (LivingEntityTypes.PERSON.equals(entityType)) {
                                unnamedPersonIndex++;
                                label = "Osoba " + unnamedPersonIndex;
                            } else {
                                unnamedAnimalIndex++;
                                label = "Zwierzę " + unnamedAnimalIndex;
                            }
                        }

                        String normalizedLabel = label.toLowerCase(Locale.ROOT).trim();
                        DetectedFaceDto anchoredFace = p.getFaceAnchorId() == null ? null
                                : anchoredImage.anchors().get(p.getFaceAnchorId());
                        if (anchoredFace != null && anchoredFace.bbox() != null) {
                            p.setBbox(anchoredFace.bbox());
                        }
                        EntityMention mention = null;
                        // Geometry is more stable than model labels such as "person 1" across re-analysis.
                        if (p.getBbox() != null && !p.getBbox().isEmpty()) {
                            mention = findMentionByBbox(existingMentions, p.getBbox(), assignedExistingMentions);
                        }
                        if (mention == null) mention = existingPendingByLabel.get(normalizedLabel);
                        boolean newMention = mention == null;
                        if (newMention) {
                            mention = EntityMention.builder()
                                .filePath(path)
                                .label(label)
                                .visionLabel(label)
                                .faceAnchorId(anchoredFace == null ? null : p.getFaceAnchorId())
                                .entityType(entityType == null ? LivingEntityTypes.PERSON : entityType)
                                .confidence(new BigDecimal("0.900"))
                                .status(MentionStatus.SUGGESTED)
                                .build();
                        }
                        mention.setVisionLabel(label);
                        mention.setFaceAnchorId(anchoredFace == null ? null : p.getFaceAnchorId());
                        
                        if (p.getVisualCues() != null) {
                            try {
                                mention.setVisualCues(objectMapper.writeValueAsString(p.getVisualCues()));
                            } catch (Exception e) {
                                log.warn("Failed to serialize visual cues for mention '{}' in {}: {}", label, path, e.getMessage());
                            }
                        }
                        List<String> heldObjects = p.getObjects() == null ? List.of() : p.getObjects().stream()
                                .filter(v -> v != null && !v.isBlank()).map(String::trim).toList();
                        List<String> nearbyObjects = p.getNearbyObjects() == null ? List.of() : p.getNearbyObjects().stream()
                                .filter(v -> v != null && !v.isBlank()).map(String::trim).toList();
                        List<String> objects = new ArrayList<>();
                        objects.addAll(heldObjects);
                        objects.addAll(nearbyObjects);
                        if (!objects.isEmpty()) mention.setContextObjects(writeJson(objects));
                        List<String> nearbyText = new ArrayList<>();
                        if (p.getNearbyText() != null) nearbyText.addAll(p.getNearbyText());
                        if (dto.getVisibleTexts() != null) {
                            for (VisibleTextDto visibleText : dto.getVisibleTexts()) {
                                if (visibleText != null && visibleText.getText() != null
                                        && label.equalsIgnoreCase(visibleText.getNearEntityLabel())) {
                                    nearbyText.add(visibleText.getText());
                                }
                            }
                        }
                        if (!nearbyText.isEmpty()) mention.setNearbyText(writeJson(nearbyText));
                        if (p.getBbox() != null && !p.getBbox().isEmpty()) {
                            try {
                                mention.setBbox(objectMapper.writeValueAsString(p.getBbox()));
                            } catch (Exception e) {
                                log.warn("Failed to serialize bbox for mention '{}' in {}: {}", label, path, e.getMessage());
                            }
                        }
                        
                        mention = mentionRepo.save(mention);
                        if (mention.getId() != null) assignedExistingMentions.add(mention.getId());
                        mentionsByLabel.put(label.toLowerCase(Locale.ROOT).trim(), mention);
                        
                        if (p.getActions() != null) {
                            for (String action : p.getActions()) {
                                if (action == null || action.isBlank()) {
                                    continue;
                                }
                                Fact fact = Fact.builder()
                                    .mention(mention)
                                    .action(action.trim())
                                    .statementPl(subjectStatement(mention, action.trim(), null))
                                    .evidenceOrigin("VISION_STRUCTURED")
                                    .filePath(path)
                                    .confidence(new BigDecimal("0.900"))
                                    .build();
                                factRepo.save(fact);
                            }
                        }
                        // Appearance cues as first-class claims (clothing/hair) — answerable without free LLM JSON parsing.
                        if (p.getVisualCues() != null) {
                            for (String cue : p.getVisualCues()) {
                                if (cue == null || cue.isBlank()) {
                                    continue;
                                }
                                String cueValue = cue.trim();
                                factRepo.save(Fact.builder()
                                        .mention(mention)
                                        .action(com.rag.rag.knowledge.fact.FactStatementRewriter.ACTION_APPEARANCE)
                                        .object(cueValue)
                                        .statementPl(subjectStatement(mention,
                                                com.rag.rag.knowledge.fact.FactStatementRewriter.ACTION_APPEARANCE,
                                                cueValue))
                                        .evidenceOrigin("VISION_STRUCTURED")
                                        .filePath(path)
                                        .confidence(new BigDecimal("0.850"))
                                        .build());
                            }
                        }

                        String tagForEntity = LivingEntityTypes.PERSON.equals(entityType) ? tagForResolve : null;
                        if (newMention) {
                            identityResolutionService.resolve(mention, tagForEntity, entityType);
                        }

                        String canonicalParticipant = mention.getEntity() != null
                                && mention.getEntity().getDisplayName() != null
                                ? mention.getEntity().getDisplayName() : label;
                        canonicalText.append("Uczestnik: ").append(canonicalParticipant)
                                .append(" (typ: ").append(entityType).append("). ");
                        if (p.getActions() != null && !p.getActions().isEmpty()) {
                            canonicalText.append("Czynności: ").append(String.join(", ", p.getActions())).append(". ");
                        }
                        if (!heldObjects.isEmpty()) {
                            canonicalText.append("Obiekty: ").append(String.join(", ", heldObjects)).append(". ");
                            for (String objectValue : heldObjects) {
                                factRepo.save(Fact.builder().mention(mention)
                                        .action(com.rag.rag.knowledge.fact.FactStatementRewriter.ACTION_RELATED_OBJECT)
                                        .object(objectValue)
                                        .statementPl(subjectStatement(mention,
                                                com.rag.rag.knowledge.fact.FactStatementRewriter.ACTION_RELATED_OBJECT,
                                                objectValue))
                                        .evidenceOrigin("VISION_STRUCTURED")
                                        .filePath(path).confidence(new BigDecimal("0.850")).build());
                            }
                        }
                        if (!nearbyObjects.isEmpty()) {
                            canonicalText.append("Obiekty w pobliżu: ").append(String.join(", ", nearbyObjects)).append(". ");
                            for (String objectValue : nearbyObjects) {
                                factRepo.save(Fact.builder().mention(mention)
                                        .action(com.rag.rag.knowledge.fact.FactStatementRewriter.ACTION_NEAR_OBJECT)
                                        .object(objectValue)
                                        .statementPl(subjectStatement(mention,
                                                com.rag.rag.knowledge.fact.FactStatementRewriter.ACTION_NEAR_OBJECT,
                                                objectValue))
                                        .evidenceOrigin("VISION_STRUCTURED")
                                        .filePath(path).confidence(new BigDecimal("0.850")).build());
                            }
                        }
                        if (!nearbyText.isEmpty()) {
                            canonicalText.append("Napisy obok: ").append(String.join(", ", nearbyText)).append(". ");
                            for (String textValue : nearbyText) {
                                factRepo.save(Fact.builder().mention(mention).action("NEAR_TEXT")
                                        .object(textValue).statementPl(subjectStatement(mention, "ma obok napis", textValue))
                                        .evidenceOrigin("VISION_STRUCTURED")
                                        .filePath(path).confidence(new BigDecimal("0.800")).build());
                            }
                        }
                        if (p.getVisualCues() != null && !p.getVisualCues().isEmpty()) {
                            canonicalText.append("Wygląd: ").append(String.join(", ", p.getVisualCues())).append(". ");
                            // Claim rows already store each cue; keep embed text for hybrid recall.
                        }
                        if (p.getBbox() != null && !p.getBbox().isEmpty()) {
                            canonicalText.append("Położenie bbox: ").append(p.getBbox()).append(". ");
                        }
                    }

                    // Mentions not present in the current structured result must never remain certain.
                    for (EntityMention existing : existingMentions) {
                        if (existing.getId() != null && !assignedExistingMentions.contains(existing.getId())) {
                            existing.setStatus(MentionStatus.REJECTED);
                            existing.setIdentitySource(null);
                            existing.setIdentityConfidence(null);
                            existing.setIdentityMargin(null);
                            mentionRepo.save(existing);
                        }
                    }

                    if (dto.getRelations() != null) {
                        for (var relation : dto.getRelations()) {
                            if (relation == null || relation.getSubjectLabel() == null || relation.getObjectLabel() == null) {
                                continue;
                            }
                            String factAction = relation.getRelation() == null ? "" : relation.getRelation().trim();
                            if (factAction.isBlank()) {
                                continue;
                            }

                            String subjectKey = relation.getSubjectLabel().toLowerCase(Locale.ROOT).trim();
                            String objectKey = relation.getObjectLabel().toLowerCase(Locale.ROOT).trim();
                            EntityMention subject = mentionsByLabel.get(subjectKey);
                            EntityMention object = mentionsByLabel.get(objectKey);
                            String objectLabel = object != null ? object.getLabel() : relation.getObjectLabel().trim();

                            // Persist spatial/participant relations even when the object is not a living mention
                            // (e.g. person 1 left of person 2, person 1 next to table).
                            if (subject != null) {
                                factRepo.save(Fact.builder()
                                        .mention(subject)
                                        .targetMention(object)
                                        .action(factAction)
                                        .object(objectLabel)
                                        .statementPl(subjectStatement(subject, factAction, objectLabel))
                                        .evidenceOrigin("VISION_STRUCTURED")
                                        .filePath(path)
                                        .confidence(new BigDecimal("0.900"))
                                        .build());
                            }

                            canonicalText.append("Relacja: ")
                                    .append(subject != null && subject.getEntity() != null
                                            ? subject.getEntity().getDisplayName()
                                            : subject != null ? subject.getLabel() : relation.getSubjectLabel())
                                    .append(" ")
                                    .append(factAction)
                                    .append(" ")
                                    .append(objectLabel)
                                    .append(". ");
                        }
                    }

                    if (dto.getVisibleTexts() != null && !dto.getVisibleTexts().isEmpty()) {
                        List<String> allTexts = dto.getVisibleTexts().stream()
                                .filter(vt -> vt != null && vt.getText() != null && !vt.getText().isBlank())
                                .map(VisibleTextDto::getText)
                                .toList();
                        if (!allTexts.isEmpty()) {
                            canonicalText.append("Widoczne napisy: ").append(String.join(", ", allTexts)).append(". ");
                        }
                    }

                    // Full structured JSON is also embedded so every detail remains searchable.
                    String structuredJson = writeJson(dto);
                    if (structuredJson != null && !structuredJson.isBlank()) {
                        canonicalText.append("Opis strukturalny JSON: ").append(structuredJson).append(" ");
                    }

                    fileOpt.ifPresent(file -> {
                        file.setGraphProjectionVersion(visionAnalyzerVersion);
                        file.setGraphProjectionStatus("CURRENT");
                        fileRepository.save(file);
                    });

                }
                // Face analysis runs after the vision/ingest transaction commits so a face
                // failure cannot mark the whole upload as rollback-only.
                return Document.from(canonicalText.toString());
            } else {
                var fileOpt = fileRepository.findByPath(path);
                if (fileOpt.isPresent()) {
                    FileEntity f = fileOpt.get();
                    f.setIngestionStatus(IngestionStatus.NEEDS_REVIEW);
                    // Mark this analyzer version as attempted so startup repair does not loop forever
                    // when the model returns unparseable JSON (embeddings still store raw text).
                    f.setGraphProjectionVersion(visionAnalyzerVersion);
                    f.setGraphProjectionStatus("FAILED");
                    fileRepository.save(f);
                }
                return Document.from(result.rawText() != null ? result.rawText() : "Brak opisu obrazu.");
            }
        } catch (InvalidImageException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Error while processing image: " + path, e);
        }
    }


    private String sha256Hex(byte[] data) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) result.append(String.format("%02x", value));
            return result.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to calculate image content hash", e);
        }
    }

    public void reanalyzeExistingImage(FileEntity file) {
        if (file == null || file.getImageData() == null || file.getImageData().length == 0) {
            return;
        }
        String path = file.getPath();
        transactionTemplate.executeWithoutResult(status -> {
            Document document = processImage(file.getImageData(), path, file.getFileName());
            document.metadata().put("path", path);
            document.metadata().put("filename", file.getFileName());
            document.metadata().put("source_type", "IMAGE");
            if (file.getOwnerId() != null) document.metadata().put("owner_id", file.getOwnerId().toString());
            String folder = path;
            int slash = folder.indexOf('/', "dir://".length());
            document.metadata().put("document_id", slash > 0 ? folder.substring("dir://".length(), slash) : "");
            jdbcTemplate.update("DELETE FROM embeddings WHERE metadata->>'path' = ?", path);
            ingestor.ingest(document);
        });
        completeFaceAnalysisForPath(path);
    }

    public void reanalyzeExistingFaces(FileEntity file) {
        if (file == null || file.getPath() == null) {
            return;
        }
        boolean completed = completeFaceAnalysisForPath(file.getPath());
        if (!completed) {
            throw new IllegalStateException("Face analysis failed for " + file.getPath());
        }
    }

    /**
     * Runs face detection/matching after vision ingest committed.
     * Image bytes are loaded in a short TX; face matching uses its own REQUIRES_NEW
     * session and reloads mentions there — never pass live JPA proxies across TX boundaries.
     */
    boolean completeFaceAnalysisForPath(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        record StoredImage(String path, String fileName, byte[] imageData) {}
        StoredImage image = transactionTemplate.execute(status -> {
            FileEntity lockedFile = fileRepository.findByPathForUpdate(path).orElse(null);
            if (lockedFile == null || lockedFile.getImageData() == null || lockedFile.getImageData().length == 0) {
                return null;
            }
            return new StoredImage(lockedFile.getPath(), lockedFile.getFileName(), lockedFile.getImageData());
        });
        if (image == null) {
            return false;
        }
        try {
            // personMentions=null → FaceIdentityService reloads in its own session
            return runFaceAnalysis(
                    sha256Hex(image.imageData()),
                    image.imageData(),
                    image.path(),
                    image.fileName(),
                    null
            );
        } catch (Exception e) {
            log.warn("Face analysis failed for {}", path, e);
            setAnalysisStatus(path, ImageAnalysisAnalyzer.FACE, ImageAnalysisStatus.FAILED);
            return false;
        }
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("Unable to serialize image context: {}", e.getMessage());
            return null;
        }
    }

    /** Open scene fields only — no closed domain dictionaries. */
    private String writeSceneAttributes(VisionResultDto dto) {
        if (dto == null) {
            return null;
        }
        boolean hasBackground = dto.getBackground() != null && !dto.getBackground().isEmpty();
        boolean hasSetting = dto.getSetting() != null && !dto.getSetting().isBlank();
        boolean hasLighting = dto.getLighting() != null && !dto.getLighting().isBlank();
        if (!hasBackground && !hasSetting && !hasLighting) {
            return null;
        }
        java.util.Map<String, Object> attrs = new java.util.LinkedHashMap<>();
        if (hasBackground) {
            attrs.put("background", dto.getBackground());
        }
        if (hasSetting) {
            attrs.put("setting", dto.getSetting().trim());
        }
        if (hasLighting) {
            attrs.put("lighting", dto.getLighting().trim());
        }
        return writeJson(attrs);
    }

    private String subjectStatement(EntityMention mention, String predicate, String value) {
        return com.rag.rag.knowledge.fact.FactStatementRewriter.buildStatement(
                com.rag.rag.knowledge.fact.FactStatementRewriter.displayName(mention),
                predicate,
                value);
    }

    private EntityMention findMentionByBbox(List<EntityMention> mentions, List<Float> target,
                                            Set<UUID> alreadyAssigned) {
        if (target == null || target.size() < 4) return null;
        float[] targetBox = toBbox(target);
        EntityMention best = null;
        double bestScore = 0.20;
        for (EntityMention candidate : mentions) {
            if (candidate.getId() == null || alreadyAssigned.contains(candidate.getId())
                    || candidate.getBbox() == null || candidate.getBbox().isBlank()) continue;
            try {
                float[] candidateBox = toBbox(objectMapper.readValue(candidate.getBbox(), float[].class));
                double score = bboxSimilarity(targetBox, candidateBox);
                if (score > bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            } catch (Exception ignored) {
                // A malformed old bbox should not prevent processing the image.
            }
        }
        return best;
    }

    private float[] toBbox(List<Float> values) {
        return new float[]{values.get(0), values.get(1), values.get(2), values.get(3)};
    }

    private float[] toBbox(float[] values) {
        return values == null || values.length < 4
                ? new float[0]
                : new float[]{values[0], values[1], values[2], values[3]};
    }

    private double bboxSimilarity(float[] left, float[] right) {
        if (left.length < 4 || right.length < 4) return 0.0;
        double x1 = Math.max(left[0], right[0]);
        double y1 = Math.max(left[1], right[1]);
        double x2 = Math.min(left[2], right[2]);
        double y2 = Math.min(left[3], right[3]);
        double intersection = Math.max(0, x2 - x1) * Math.max(0, y2 - y1);
        double leftArea = Math.max(0, left[2] - left[0]) * Math.max(0, left[3] - left[1]);
        double rightArea = Math.max(0, right[2] - right[0]) * Math.max(0, right[3] - right[1]);
        double union = leftArea + rightArea - intersection;
        return union <= 0 ? 0.0 : intersection / union;
    }

    private ExtractionResult loadVisionResult(String contentHash, String base64Image, String mimeType,
                                              Set<String> faceAnchors) {
        String payload = imageAnalysisCacheService.getOrCompute(contentHash, ImageAnalysisAnalyzer.VISION,
                visionAnalyzerVersion, () -> {
                    ExtractionResult result = extractor.extract(base64Image, mimeType, faceAnchors);
                    try {
                        return objectMapper.writeValueAsString(new CachedVisionResult(
                                result.resultDto(), result.rawText(), result.isStructured()));
                    } catch (Exception e) {
                        throw new IllegalStateException("Unable to cache vision result", e);
                    }
                });
        try {
            CachedVisionResult cached = objectMapper.readValue(payload, CachedVisionResult.class);
            return new ExtractionResult(cached.resultDto(), cached.rawText(), cached.structured());
        } catch (Exception e) {
            throw new IllegalStateException("Unable to read cached vision result", e);
        }
    }

    private boolean runFaceAnalysis(String contentHash, byte[] imageData, String path,
                                    String fileName, List<EntityMention> personMentions) {
        try {
            FaceAnalyzeResponse response = loadFaceResponse(contentHash, imageData, fileName);
            List<DetectedFaceDto> faces = detectedFaces(response);
            faceIdentityService.processDetectedFaces(faces, path, fileName, personMentions);
            setAnalysisStatus(path, ImageAnalysisAnalyzer.FACE, ImageAnalysisStatus.COMPLETED);
            return true;
        } catch (Exception e) {
            setAnalysisStatus(path, ImageAnalysisAnalyzer.FACE, ImageAnalysisStatus.FAILED);
            log.warn("Face analysis failed for {}", path, e);
            return false;
        }
    }

    private FaceAnalyzeResponse loadFaceResponse(String contentHash, byte[] imageData, String fileName) {
        try {
            String payload = imageAnalysisCacheService.getOrCompute(contentHash, ImageAnalysisAnalyzer.FACE,
                    faceAnalyzerVersion, () -> {
                        try {
                            return objectMapper.writeValueAsString(
                                    faceRecognitionClient.analyzeResponseOrThrow(imageData, fileName));
                        } catch (Exception e) {
                            throw new IllegalStateException("Unable to cache face analysis", e);
                        }
                    });
            return objectMapper.readValue(payload, FaceAnalyzeResponse.class);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to load face analysis", e);
        }
    }

    private List<DetectedFaceDto> detectedFaces(FaceAnalyzeResponse response) {
        if (response == null || response.faces() == null) return List.of();
        int width = response.imageWidth() == null ? 0 : response.imageWidth();
        int height = response.imageHeight() == null ? 0 : response.imageHeight();
        return response.faces().stream().map(face -> face.withImageDimensions(width, height)).toList();
    }

    private void setAnalysisStatus(String path, ImageAnalysisAnalyzer analyzer, ImageAnalysisStatus status) {
        fileRepository.findByPath(path).ifPresent(file -> {
            if (analyzer == ImageAnalysisAnalyzer.VISION) file.setVisionAnalysisStatus(status);
            else file.setFaceAnalysisStatus(status);
            fileRepository.save(file);
        });
    }

    private record CachedVisionResult(VisionResultDto resultDto, String rawText, boolean structured) {}
    /**
     * Cascades file delete across embeddings, graph mentions/facts, face data and orphan entities
     * that no longer have any mentions. Same path used by the data API delete endpoint.
     */
    @Transactional
    public void deleteFiles(List<String> filePaths) {
        for (String path : filePaths) {
            if (path == null || path.isBlank()) {
                continue;
            }

            List<EntityMention> mentions = mentionRepo.findByFilePath(path);
            List<UUID> mentionIds = mentions.stream()
                    .map(EntityMention::getId)
                    .filter(java.util.Objects::nonNull)
                    .toList();
            Set<UUID> touchedEntityIds = mentions.stream()
                    .map(EntityMention::getEntity)
                    .filter(java.util.Objects::nonNull)
                    .map(KnowledgeEntity::getId)
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toCollection(HashSet::new));

            if (!mentionIds.isEmpty()) {
                suggestionRepo.deleteByMentionIds(mentionIds);
                faceEmbeddingRepository.deleteByMentionIdIn(mentionIds);
                faceObservationRepository.deleteByMentionIds(mentionIds);
            }

            factRepo.deleteByFilePath(path);
            faceEmbeddingRepository.deleteByFilePath(path);
            faceObservationRepository.deleteByFilePath(path);
            mentionRepo.deleteByFilePath(path);
            jdbcTemplate.update("DELETE FROM embeddings WHERE metadata->>'path' = ?", path);

            for (UUID entityId : touchedEntityIds) {
                cleanupOrphanKnowledgeEntity(entityId);
            }

            fileRepository.findByPath(path).ifPresent(fileRepository::delete);

            log.info("Deleted file and related data for path: {}", path);
        }
    }

    /**
     * Removes a knowledge entity when it has no remaining mentions and no USER alias
     * (cleans entity-level face embeddings / aliases). Kept when the user assigned an alias.
     */
    void cleanupOrphanKnowledgeEntity(UUID entityId) {
        if (entityId == null) {
            return;
        }
        boolean hasRemainingMentions = !mentionRepo.findByEntityId(entityId).isEmpty();
        boolean hasUserAlias = aliasRepo.existsByEntityIdAndSource(entityId, AliasSource.USER);
        if (!OrphanEntityCleanupPolicy.shouldDeleteOrphan(hasRemainingMentions, hasUserAlias)) {
            if (!hasRemainingMentions && hasUserAlias) {
                log.debug("Kept knowledge entity {} — no mentions but has USER alias", entityId);
            }
            return;
        }
        faceEmbeddingRepository.deleteByEntityId(entityId);
        aliasRepo.deleteByEntityId(entityId);
        knowledgeEntityRepo.findById(entityId).ifPresent(entity -> {
            knowledgeEntityRepo.delete(entity);
            log.info("Removed orphan knowledge entity '{}' ({})", entity.getDisplayName(), entityId);
        });
    }

    @Transactional
    public void clearAllData() {
        UUID ownerId = currentUserService.requireUserId();
        clearAllDataForOwner(ownerId);
    }

    /**
     * Deletes the current owner's library and chat data: folders, files, people (entities),
     * conversations and messages — scoped to {@code ownerId} only.
     */
    @Transactional
    public void clearAllDataForOwner(UUID ownerId) {
        if (ownerId == null) {
            throw new IllegalArgumentException("ownerId is required");
        }
        clearOwnedChats(ownerId);

        List<String> paths = fileRepository.findAllByOwnerId(ownerId).stream()
                .map(FileEntity::getPath)
                .filter(path -> path != null && !path.isBlank())
                .toList();
        deleteFiles(paths);

        // Force-remove remaining people (incl. USER-aliased entities kept by orphan cleanup).
        clearOwnedKnowledgeEntities(ownerId);

        folderRepository.findAllByOwnerIdOrderByUpdatedAtDesc(ownerId)
                .forEach(folderRepository::delete);
        log.info("Cleared folders, files, people, chats and messages for owner {}", ownerId);
    }

    private void clearOwnedChats(UUID ownerId) {
        List<ChatMemoryEntity> chats = chatMemoryRepository.findAllByOwnerIdOrderByLastMessageAtDesc(ownerId);
        for (ChatMemoryEntity chat : chats) {
            if (chat.getChatId() != null) {
                chatMessageRepository.deleteByChatId(chat.getChatId());
            }
            chatMemoryRepository.delete(chat);
        }
    }

    private void clearOwnedKnowledgeEntities(UUID ownerId) {
        for (KnowledgeEntity entity : knowledgeEntityRepo.findAllByOwnerId(ownerId)) {
            UUID entityId = entity.getId();
            if (entityId == null) {
                continue;
            }
            List<UUID> mentionIds = mentionRepo.findByEntityId(entityId).stream()
                    .map(EntityMention::getId)
                    .filter(java.util.Objects::nonNull)
                    .toList();
            if (!mentionIds.isEmpty()) {
                suggestionRepo.deleteByMentionIds(mentionIds);
                factRepo.deleteByMentionIds(mentionIds);
                faceObservationRepository.deleteByMentionIds(mentionIds);
                faceEmbeddingRepository.deleteByMentionIdIn(mentionIds);
            }
            faceEmbeddingRepository.deleteByEntityId(entityId);
            mentionRepo.deleteByEntityId(entityId);
            aliasRepo.deleteByEntityId(entityId);
            knowledgeEntityRepo.delete(entity);
        }
    }

    /**
     * HTTP accept path: store bytes as PENDING and either enqueue (async) or process inline.
     */
    public String ingestMultipartFile(MultipartFile file, FolderEntity folder, String entityTag) throws IOException {
        UUID ownerId = folder.getOwnerId() != null
                ? folder.getOwnerId()
                : currentUserService.requireUserId();
        return acceptUpload(file, folder, entityTag, ownerId).path();
    }

    public String ingestMultipartFile(
            MultipartFile file,
            FolderEntity folder,
            String entityTag,
            UUID ownerId
    ) throws IOException {
        return acceptUpload(file, folder, entityTag, ownerId).path();
    }

    public UploadResultDto acceptUpload(
            MultipartFile file,
            FolderEntity folder,
            String entityTag,
            UUID ownerId
    ) throws IOException {
        String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "file";
        String extension = StringUtils.getFilenameExtension(fileName);
        String path = "dir://" + folder.getName() + "/" + fileName;
        byte[] data = file.getBytes();
        String contentHash = sha256Hex(data);
        boolean image = isImageFile(fileName, file.getContentType());
        String mimeType = resolveMimeType(file.getContentType(), extension);

        // Idempotency: same path + same hash already READY → do not re-queue heavy work.
        Optional<FileEntity> existingOpt = fileRepository.findByPath(path);
        if (existingOpt.isPresent()) {
            FileEntity existing = existingOpt.get();
            if (existing.getOwnerId() != null && !existing.getOwnerId().equals(ownerId)) {
                throw new IllegalStateException("Path already owned by another user");
            }
            if (IngestionStatus.READY.equals(existing.getIngestionStatus())
                    && contentHash.equals(existing.getContentHash())) {
                log.info("Idempotent upload skip (already READY) path={}", path);
                return new UploadResultDto(path, fileName, image, IngestionStatus.READY.name());
            }
        }

        transactionTemplate.executeWithoutResult(status -> {
            FileEntity entity = existingOpt.orElseGet(() -> FileEntity.builder().path(path).build());
            entity.setFileName(fileName);
            entity.setFileType(mimeType);
            entity.setImageData(data);
            entity.setContentHash(contentHash);
            entity.setOwnerId(ownerId);
            entity.setIngestionStatus(IngestionStatus.PENDING);
            if (entityTag != null && !entityTag.isBlank()) {
                entity.setEntityTag(entityTag.trim());
            }
            fileRepository.save(entity);
        });

        DocumentUploadedEvent event = new DocumentUploadedEvent(
                path,
                ownerId,
                contentHash,
                folder.getName(),
                fileName,
                entityTag != null && !entityTag.isBlank() ? entityTag.trim() : null
        );

        if (asyncIngestEnabled) {
            DocumentIngestPublisher publisher = documentIngestPublisher.getIfAvailable();
            if (publisher == null) {
                log.warn("Async ingest enabled but no publisher bean — processing inline for {}", path);
                processQueuedDocument(event);
                return new UploadResultDto(path, fileName, image, statusOf(path));
            }
            publisher.publish(event);
            return new UploadResultDto(path, fileName, image, IngestionStatus.PENDING.name());
        }

        processQueuedDocument(event);
        return new UploadResultDto(path, fileName, image, statusOf(path));
    }

    /**
     * Worker path (Rabbit consumer or inline fallback). Vision/embeddings then face.
     */
    public void processQueuedDocument(DocumentUploadedEvent event) {
        if (event == null || event.path() == null || event.path().isBlank()) {
            return;
        }
        String path = event.path();
        try {
            FileEntity file = fileRepository.findByPath(path)
                    .orElseThrow(() -> new IllegalStateException("Missing file for ingest path: " + path));

            if (IngestionStatus.READY.equals(file.getIngestionStatus())
                    && event.contentHash() != null
                    && event.contentHash().equals(file.getContentHash())) {
                log.info("Skip processQueuedDocument — already READY path={}", path);
                return;
            }

            byte[] data = file.getImageData();
            if (data == null || data.length == 0) {
                throw new IllegalStateException("No stored bytes for path: " + path);
            }

            String fileName = file.getFileName() != null ? file.getFileName() : event.fileName();
            String extension = StringUtils.getFilenameExtension(fileName);
            String group = event.folderName() != null ? event.folderName() : extractFolderName(path);
            boolean image = isImageExtension(extension);

            if (event.entityTag() != null && !event.entityTag().isBlank()) {
                storeEntityTag(path, fileName, event.entityTag(), event.ownerId());
            }

            transactionTemplate.executeWithoutResult(status -> {
                jdbcTemplate.update("DELETE FROM embeddings WHERE metadata->>'path' = ?", path);
                Document parsedDocument = chooseParser(data, path, fileName, extension);
                if (parsedDocument != null) {
                    parsedDocument.metadata()
                            .put("document_id", group)
                            .put("filename", fileName)
                            .put("path", path)
                            .put("source_type", sourceType(fileName));
                    UUID metadataOwner = event.ownerId() != null ? event.ownerId() : file.getOwnerId();
                    if (metadataOwner != null) parsedDocument.metadata().put("owner_id", metadataOwner.toString());
                    ingestor.ingest(parsedDocument);
                    log.info("Successfully processed queued file: {}", path);
                }
                fileRepository.findByPath(path).ifPresent(entity -> {
                    if (entity.getOwnerId() == null && event.ownerId() != null) {
                        entity.setOwnerId(event.ownerId());
                    }
                    // READY unless vision left NEEDS_REVIEW or FAILED earlier
                    if (!IngestionStatus.NEEDS_REVIEW.equals(entity.getIngestionStatus())
                            && !IngestionStatus.FAILED.equals(entity.getIngestionStatus())) {
                        if (entity.getIngestionStatus() == null
                                || IngestionStatus.PENDING.equals(entity.getIngestionStatus())
                                || IngestionStatus.EXTRACTED.equals(entity.getIngestionStatus())) {
                            entity.setIngestionStatus(IngestionStatus.READY);
                        }
                    }
                    if (IngestionStatus.EXTRACTED.equals(entity.getIngestionStatus())
                            || IngestionStatus.NEEDS_REVIEW.equals(entity.getIngestionStatus())) {
                        // Promote EXTRACTED → READY after embeddings; keep NEEDS_REVIEW for human review.
                        if (IngestionStatus.EXTRACTED.equals(entity.getIngestionStatus())) {
                            entity.setIngestionStatus(IngestionStatus.READY);
                        }
                    }
                    fileRepository.save(entity);
                });
            });

            if (image) {
                completeFaceAnalysisForPath(path);
            }

            fileRepository.findByPath(path).ifPresent(entity -> {
                if (!IngestionStatus.FAILED.equals(entity.getIngestionStatus())
                        && !IngestionStatus.NEEDS_REVIEW.equals(entity.getIngestionStatus())) {
                    entity.setIngestionStatus(IngestionStatus.READY);
                    fileRepository.save(entity);
                }
            });
        } catch (Exception e) {
            log.error("Queued ingest failed for path={}", path, e);
            try {
                fileRepository.findByPath(path).ifPresent(entity -> {
                    entity.setIngestionStatus(IngestionStatus.FAILED);
                    fileRepository.save(entity);
                });
            } catch (Exception statusError) {
                // A secondary persistence problem must not replace the ingest exception
                // that explains why parsing, vision or embedding actually failed.
                e.addSuppressed(statusError);
                log.error("Could not persist FAILED ingest status for path={}", path, statusError);
            }
            throw e instanceof RuntimeException re ? re : new RuntimeException(e);
        }
    }

    @Transactional(readOnly = true)
    public Optional<IngestionStatus> getIngestionStatus(String path, UUID ownerId) {
        return fileRepository.findByPathAndOwnerId(path, ownerId).map(FileEntity::getIngestionStatus);
    }

    private String statusOf(String path) {
        return fileRepository.findByPath(path)
                .map(FileEntity::getIngestionStatus)
                .map(Enum::name)
                .orElse(IngestionStatus.PENDING.name());
    }

    private String extractFolderName(String path) {
        if (path == null || !path.startsWith("dir://")) {
            return "";
        }
        int slash = path.indexOf('/', "dir://".length());
        return slash > 0 ? path.substring("dir://".length(), slash) : path.substring("dir://".length());
    }

    private String resolveMimeType(String contentType, String extension) {
        if (contentType != null && !contentType.isBlank()) {
            return contentType;
        }
        if (extension == null) {
            return "application/octet-stream";
        }
        return switch (extension.toLowerCase(Locale.ROOT)) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "pdf" -> "application/pdf";
            case "txt" -> "text/plain";
            default -> "application/octet-stream";
        };
    }

    private boolean isImageFile(String fileName, String contentType) {
        if (contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            return true;
        }
        String extension = StringUtils.getFilenameExtension(fileName);
        return isImageExtension(extension);
    }

    private boolean isImageExtension(String extension) {
        if (extension == null) {
            return false;
        }
        return switch (extension.toLowerCase(Locale.ROOT)) {
            case "png", "jpg", "jpeg" -> true;
            default -> false;
        };
    }

    private String sourceType(String fileName) {
        String extension = StringUtils.getFilenameExtension(fileName);
        if (extension == null) return "OTHER";
        return switch (extension.toLowerCase(Locale.ROOT)) {
            case "png", "jpg", "jpeg", "gif", "webp" -> "IMAGE";
            case "pdf" -> "PDF";
            case "txt" -> "TEXT";
            default -> "OTHER";
        };
    }

    private void storeEntityTag(String path, String fileName, String entityTag, UUID ownerId) {
        fileRepository.findByPath(path).ifPresentOrElse(
                existing -> {
                    existing.setEntityTag(entityTag);
                    if (existing.getOwnerId() == null) {
                        existing.setOwnerId(ownerId);
                    }
                    fileRepository.save(existing);
                },
                () -> fileRepository.save(FileEntity.builder()
                        .path(path)
                        .fileName(fileName)
                        .entityTag(entityTag)
                        .ownerId(ownerId)
                        .build())
        );
    }

    public SourceDto createSourceDto(String path, String metadataFileName, Double score) {
        if (path == null) {
            return new SourceDto(null, metadataFileName, score, null, "OTHER");
        }

        String base64 = null;
        String type = "OTHER";
        String finalFileName = metadataFileName != null ? metadataFileName : path;

        UUID ownerId = currentUserService.findUserId().orElse(null);
        var fileOpt = ownerId == null
                ? fileRepository.findByPath(path)
                : fileRepository.findByPathAndOwnerId(path, ownerId);
        if (fileOpt.isPresent()) {
            FileEntity file = fileOpt.get();
            finalFileName = file.getFileName();
            String mimeType = file.getFileType().toLowerCase();
            
            if (mimeType.contains("image")) {
                type = "IMAGE";
                if (file.getImageData() != null) {
                    base64 = Base64.getEncoder().encodeToString(file.getImageData());
                }
            } else if (mimeType.contains("pdf")) {
                type = "PDF";
            } else if (mimeType.contains("txt") || mimeType.equals("text/plain")) {
                type = "TEXT";
            }
        } else {
            String lowerPath = path.toLowerCase();
            if (lowerPath.endsWith(".pdf")) type = "PDF";
            else if (lowerPath.endsWith(".txt")) type = "TEXT";
            else if (lowerPath.matches(".*\\.(jpg|jpeg|png)$")) type = "IMAGE";
        }

        return new SourceDto(path, finalFileName, score, base64, type);
    }

    public SourceDto createGraphFactSourceDto(String path, String metadataFileName, Double score) {
        SourceDto source = createSourceDto(path, metadataFileName, score);
        return new SourceDto(source.path(), source.fileName(), source.score(), source.base64(), "GRAPH_FACT");
    }

    public List<SourceDto> getSources(Result<String> result) {
        if (result.sources() == null) {
            return List.of();
        }
        return result.sources().stream()
                .map(source -> {
                    var metadata = source.textSegment().metadata();
                    String path = metadata.getString("path");
                    String fileName = metadata.getString("filename");
                    
                    double score = 0.0;
                    String scoreStr = metadata.getString("score");
                    if (scoreStr != null) {
                        try {
                            score = Double.parseDouble(scoreStr);
                        } catch (NumberFormatException ignored) {}
                    }

                    return createSourceDto(path, fileName, score);
                })
                .distinct()
                .toList();
    }
}

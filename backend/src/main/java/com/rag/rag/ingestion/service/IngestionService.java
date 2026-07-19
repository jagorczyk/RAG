package com.rag.rag.ingestion.service;

import com.rag.rag.folder.entity.FolderEntity;
import com.rag.rag.folder.entity.FileEntity;
import com.rag.rag.folder.repository.FileRepository;
import com.rag.rag.folder.repository.FolderRepository;
import com.rag.rag.ingestion.dto.SourceDto;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.service.Result;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
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
import com.rag.rag.knowledge.entity.EntityMention;
import com.rag.rag.knowledge.entity.LivingEntityTypes;
import com.rag.rag.knowledge.entity.MentionStatus;
import com.rag.rag.knowledge.extraction.ExtractedEntityDto;
import com.rag.rag.knowledge.extraction.StructuredVisionExtractor;
import com.rag.rag.knowledge.extraction.StructuredVisionExtractor.ExtractionResult;
import com.rag.rag.knowledge.extraction.VisionResultDto;
import com.rag.rag.knowledge.extraction.VisibleTextDto;
import com.rag.rag.knowledge.fact.Fact;
import com.rag.rag.knowledge.face.FaceEmbedding;
import com.rag.rag.knowledge.face.FaceIdentityService;
import com.rag.rag.knowledge.face.FaceAnalyzeResponse;
import com.rag.rag.knowledge.face.FaceRecognitionClient;
import com.rag.rag.knowledge.identity.IdentityResolutionService;
import com.rag.rag.knowledge.repository.EntityAliasRepository;
import com.rag.rag.knowledge.repository.EntityMentionRepository;
import com.rag.rag.knowledge.repository.FaceEmbeddingRepository;
import com.rag.rag.knowledge.repository.FactRepository;
import com.rag.rag.knowledge.repository.IdentitySuggestionRepository;
import com.rag.rag.knowledge.repository.KnowledgeEntityRepository;

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

    private final EmbeddingStoreIngestor ingestor;
    private final FileRepository fileRepository;
    private final FolderRepository folderRepository;
    private final StructuredVisionExtractor extractor;
    private final EntityMentionRepository mentionRepo;
    private final FactRepository factRepo;
    private final IdentitySuggestionRepository suggestionRepo;
    private final FaceEmbeddingRepository faceEmbeddingRepository;
    private final FaceIdentityService faceIdentityService;
    private final FaceRecognitionClient faceRecognitionClient;
    private final ImageAnalysisCacheService imageAnalysisCacheService;
    private final EntityAliasRepository aliasRepo;
    private final KnowledgeEntityRepository knowledgeEntityRepo;
    private final IdentityResolutionService identityResolutionService;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
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
            FaceIdentityService faceIdentityService,
            FaceRecognitionClient faceRecognitionClient,
            ImageAnalysisCacheService imageAnalysisCacheService,
            EntityAliasRepository aliasRepo,
            KnowledgeEntityRepository knowledgeEntityRepo,
            IdentityResolutionService identityResolutionService,
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager
    ) {
        this.ingestor = ingestor;
        this.fileRepository = fileRepository;
        this.folderRepository = folderRepository;
        this.extractor = extractor;
        this.mentionRepo = mentionRepo;
        this.factRepo = factRepo;
        this.suggestionRepo = suggestionRepo;
        this.faceEmbeddingRepository = faceEmbeddingRepository;
        this.faceIdentityService = faceIdentityService;
        this.faceRecognitionClient = faceRecognitionClient;
        this.imageAnalysisCacheService = imageAnalysisCacheService;
        this.aliasRepo = aliasRepo;
        this.knowledgeEntityRepo = knowledgeEntityRepo;
        this.identityResolutionService = identityResolutionService;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
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
            String base64Image = Base64.getEncoder().encodeToString(processedBytes);
            String contentHash = sha256Hex(imageData);

            FileEntity fileEntity = ensureFileEntityExists(path, fileName, mimeType, processedBytes, contentHash);
            fileEntity.setVisionAnalysisStatus(ImageAnalysisStatus.PROCESSING);
            fileEntity.setFaceAnalysisStatus(ImageAnalysisStatus.PROCESSING);
            fileRepository.save(fileEntity);

            ExtractionResult result = loadVisionResult(contentHash, base64Image, mimeType);
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
                        EntityMention mention = existingPendingByLabel.get(normalizedLabel);
                        if (mention == null && p.getBbox() != null && !p.getBbox().isEmpty()) {
                            mention = findMentionByBbox(existingMentions, p.getBbox(), assignedExistingMentions);
                        }
                        boolean newMention = mention == null;
                        if (newMention) {
                            mention = EntityMention.builder()
                                .filePath(path)
                                .label(label)
                                .entityType(entityType == null ? LivingEntityTypes.PERSON : entityType)
                                .confidence(new BigDecimal("0.900"))
                                .status(MentionStatus.SUGGESTED)
                                .build();
                        }
                        
                        if (p.getVisualCues() != null) {
                            try {
                                mention.setVisualCues(objectMapper.writeValueAsString(p.getVisualCues()));
                            } catch (Exception e) {
                                log.warn("Failed to serialize visual cues for mention '{}' in {}: {}", label, path, e.getMessage());
                            }
                        }
                        List<String> objects = new ArrayList<>();
                        if (p.getObjects() != null) objects.addAll(p.getObjects());
                        if (p.getNearbyObjects() != null) objects.addAll(p.getNearbyObjects());
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
                                Fact fact = Fact.builder()
                                    .mention(mention)
                                    .action(action)
                                    .filePath(path)
                                    .confidence(new BigDecimal("0.900"))
                                    .build();
                                factRepo.save(fact);
                            }
                        }

                        String tagForEntity = LivingEntityTypes.PERSON.equals(entityType) ? tagForResolve : null;
                        if (newMention) {
                            identityResolutionService.resolve(mention, tagForEntity, entityType);
                        }

                        canonicalText.append("Uczestnik: ").append(label)
                                .append(" (typ: ").append(entityType).append("). ");
                        if (p.getActions() != null && !p.getActions().isEmpty()) {
                            canonicalText.append("Czynności: ").append(String.join(", ", p.getActions())).append(". ");
                        }
                        if (!objects.isEmpty()) {
                            canonicalText.append("Obiekty i otoczenie: ").append(String.join(", ", objects)).append(". ");
                            for (String objectValue : objects) {
                                factRepo.save(Fact.builder().mention(mention).action("RELATED_OBJECT")
                                        .object(objectValue).filePath(path).confidence(new BigDecimal("0.850")).build());
                            }
                        }
                        if (!nearbyText.isEmpty()) {
                            canonicalText.append("Napisy obok: ").append(String.join(", ", nearbyText)).append(". ");
                            for (String textValue : nearbyText) {
                                factRepo.save(Fact.builder().mention(mention).action("NEAR_TEXT")
                                        .object(textValue).filePath(path).confidence(new BigDecimal("0.800")).build());
                            }
                        }
                        if (p.getVisualCues() != null && !p.getVisualCues().isEmpty()) {
                            canonicalText.append("Wygląd: ").append(String.join(", ", p.getVisualCues())).append(". ");
                        }
                        if (p.getBbox() != null && !p.getBbox().isEmpty()) {
                            canonicalText.append("Położenie bbox: ").append(p.getBbox()).append(". ");
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
                                        .filePath(path)
                                        .confidence(new BigDecimal("0.900"))
                                        .build());
                            }

                            canonicalText.append("Relacja: ")
                                    .append(subject != null ? subject.getLabel() : relation.getSubjectLabel())
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

                }
                // Face analysis runs after the vision/ingest transaction commits so a face
                // failure cannot mark the whole upload as rollback-only.
                return Document.from(canonicalText.toString());
            } else {
                var fileOpt = fileRepository.findByPath(path);
                if (fileOpt.isPresent()) {
                    FileEntity f = fileOpt.get();
                    f.setIngestionStatus(IngestionStatus.NEEDS_REVIEW);
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
     * Runs face detection/matching in a separate transaction after vision ingest committed.
     * Failures are recorded on the file row and never roll back the parent upload.
     */
    boolean completeFaceAnalysisForPath(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
                FileEntity lockedFile = fileRepository.findByPathForUpdate(path).orElse(null);
                if (lockedFile == null || lockedFile.getImageData() == null || lockedFile.getImageData().length == 0) {
                    return false;
                }
                List<EntityMention> personMentions = mentionRepo.findByFilePath(lockedFile.getPath()).stream()
                        .filter(mention -> mention.getStatus() != MentionStatus.REJECTED)
                        .filter(mention -> LivingEntityTypes.PERSON.equals(
                                LivingEntityTypes.normalize(mention.getEntityType())))
                        .toList();
                // Touch lazy entity associations while the session is open.
                for (EntityMention mention : personMentions) {
                    if (mention.getEntity() != null) {
                        mention.getEntity().getId();
                    }
                }
                return runFaceAnalysis(
                        sha256Hex(lockedFile.getImageData()),
                        lockedFile.getImageData(),
                        lockedFile.getPath(),
                        lockedFile.getFileName(),
                        personMentions
                );
            }));
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

    private ExtractionResult loadVisionResult(String contentHash, String base64Image, String mimeType) {
        String payload = imageAnalysisCacheService.getOrCompute(contentHash, ImageAnalysisAnalyzer.VISION,
                visionAnalyzerVersion, () -> {
                    ExtractionResult result = extractor.extract(base64Image, mimeType);
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
            String payload = imageAnalysisCacheService.getOrCompute(contentHash, ImageAnalysisAnalyzer.FACE,
                    faceAnalyzerVersion, () -> {
                        try {
                            return objectMapper.writeValueAsString(
                                    faceRecognitionClient.analyzeResponseOrThrow(imageData, fileName));
                        } catch (Exception e) {
                            throw new IllegalStateException("Unable to cache face analysis", e);
                        }
                    });
            FaceAnalyzeResponse response = objectMapper.readValue(payload, FaceAnalyzeResponse.class);
            List<com.rag.rag.knowledge.face.DetectedFaceDto> faces = response.faces() == null ? List.of()
                    : response.faces().stream()
                    .map(face -> face.withImageDimensions(response.imageWidth() == null ? 0 : response.imageWidth(), response.imageHeight() == null ? 0 : response.imageHeight()))
                    .toList();
            faceIdentityService.processDetectedFaces(faces, path, fileName, personMentions);
            setAnalysisStatus(path, ImageAnalysisAnalyzer.FACE, ImageAnalysisStatus.COMPLETED);
            return true;
        } catch (Exception e) {
            setAnalysisStatus(path, ImageAnalysisAnalyzer.FACE, ImageAnalysisStatus.FAILED);
            log.warn("Face analysis failed for {}", path, e);
            return false;
        }
    }

    private void setAnalysisStatus(String path, ImageAnalysisAnalyzer analyzer, ImageAnalysisStatus status) {
        fileRepository.findByPath(path).ifPresent(file -> {
            if (analyzer == ImageAnalysisAnalyzer.VISION) file.setVisionAnalysisStatus(status);
            else file.setFaceAnalysisStatus(status);
            fileRepository.save(file);
        });
    }

    private record CachedVisionResult(VisionResultDto resultDto, String rawText, boolean structured) {}
    @Transactional
    public void deleteFiles(List<String> filePaths) {
        for (String path : filePaths) {
            if (path == null || path.isBlank()) {
                continue;
            }

            List<UUID> mentionIds = mentionRepo.findByFilePath(path).stream()
                    .map(mention -> mention.getId())
                    .toList();

            if (!mentionIds.isEmpty()) {
                suggestionRepo.deleteByMentionIds(mentionIds);
                faceEmbeddingRepository.deleteByMentionIdIn(mentionIds);
            }

            factRepo.deleteByFilePath(path);
            faceEmbeddingRepository.deleteByFilePath(path);
            mentionRepo.deleteByFilePath(path);
            jdbcTemplate.update("DELETE FROM embeddings WHERE metadata->>'path' = ?", path);
            fileRepository.findByPath(path).ifPresent(fileRepository::delete);

            log.info("Deleted file and related data for path: {}", path);
        }
    }

    @Transactional
    public void clearAllData() {
        suggestionRepo.deleteAllInBatch();
        factRepo.deleteAllInBatch();
        faceEmbeddingRepository.deleteAllInBatch();
        mentionRepo.deleteAllInBatch();
        aliasRepo.deleteAllInBatch();
        knowledgeEntityRepo.deleteAllInBatch();
        jdbcTemplate.execute("TRUNCATE TABLE embeddings");
        fileRepository.deleteAllInBatch();
        folderRepository.deleteAllInBatch();
        log.info("Cleared all folders, files, embeddings and knowledge graph data");
    }

    /**
     * Vision/embeddings commit first; face analysis runs afterwards so face errors
     * cannot force UnexpectedRollbackException on the whole upload.
     */
    public String ingestMultipartFile(MultipartFile file, FolderEntity folder, String entityTag) throws IOException {
        String fileName = file.getOriginalFilename();
        String extension = StringUtils.getFilenameExtension(fileName);
        String group = folder.getName();

        String path = "dir://" + folder.getName() + "/" + fileName;
        byte[] data = file.getBytes();
        boolean image = isImageExtension(extension);

        transactionTemplate.executeWithoutResult(status -> {
            if (entityTag != null && !entityTag.isBlank()) {
                storeEntityTag(path, fileName, entityTag.trim());
            }
            Document parsedDocument = chooseParser(data, path, fileName, extension);
            if (parsedDocument != null) {
                parsedDocument.metadata()
                        .put("document_id", group)
                        .put("filename", fileName)
                        .put("path", path)
                        .put("source_type", sourceType(fileName));
                ingestor.ingest(parsedDocument);
                log.info("Successfully ingested file: {}", path);
            }
        });

        if (image) {
            completeFaceAnalysisForPath(path);
        }
        return path;
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

    private void storeEntityTag(String path, String fileName, String entityTag) {
        fileRepository.findByPath(path).ifPresentOrElse(
                existing -> {
                    existing.setEntityTag(entityTag);
                    fileRepository.save(existing);
                },
                () -> fileRepository.save(FileEntity.builder()
                        .path(path)
                        .fileName(fileName)
                        .entityTag(entityTag)
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

        var fileOpt = fileRepository.findByPath(path);
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

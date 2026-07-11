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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.rag.folder.entity.IngestionStatus;
import com.rag.rag.knowledge.entity.EntityMention;
import com.rag.rag.knowledge.entity.MentionStatus;
import com.rag.rag.knowledge.extraction.ExtractedEntityDto;
import com.rag.rag.knowledge.extraction.StructuredVisionExtractor;
import com.rag.rag.knowledge.extraction.StructuredVisionExtractor.ExtractionResult;
import com.rag.rag.knowledge.extraction.VisionResultDto;
import com.rag.rag.knowledge.fact.Fact;
import com.rag.rag.knowledge.face.FaceIdentityService;
import com.rag.rag.knowledge.graph.RelationConstants;
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
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class IngestionService {

    @Value("${vision.image.max-width:800}")
    private int MAX_WIDTH;

    @Value("${vision.image.max-height:800}")
    private int MAX_HEIGHT;

    private final EmbeddingStoreIngestor ingestor;
    private final FileRepository fileRepository;
    private final FolderRepository folderRepository;
    private final StructuredVisionExtractor extractor;
    private final EntityMentionRepository mentionRepo;
    private final FactRepository factRepo;
    private final IdentitySuggestionRepository suggestionRepo;
    private final FaceEmbeddingRepository faceEmbeddingRepository;
    private final FaceIdentityService faceIdentityService;
    private final EntityAliasRepository aliasRepo;
    private final KnowledgeEntityRepository knowledgeEntityRepo;
    private final IdentityResolutionService identityResolutionService;
    private final JdbcTemplate jdbcTemplate;
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
            EntityAliasRepository aliasRepo,
            KnowledgeEntityRepository knowledgeEntityRepo,
            IdentityResolutionService identityResolutionService,
            JdbcTemplate jdbcTemplate
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
        this.aliasRepo = aliasRepo;
        this.knowledgeEntityRepo = knowledgeEntityRepo;
        this.identityResolutionService = identityResolutionService;
        this.jdbcTemplate = jdbcTemplate;
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

    private void ensureFileEntityExists(String path, String fileName, String mimeType, byte[] imageData) {
        if (fileRepository.findByPath(path).isEmpty()) {
            FileEntity entity = FileEntity.builder()
                    .path(path)
                    .fileName(fileName)
                    .fileType(mimeType)
                    .imageData(imageData)
                    .build();
            fileRepository.save(entity);
        }
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
            String format = fileName.toLowerCase().endsWith(".png") ? "png" : "jpg";
            String mimeType = "image/" + format;

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            Thumbnails.of(new ByteArrayInputStream(imageData))
                    .size(MAX_WIDTH, MAX_HEIGHT)
                    .outputFormat(format)
                    .toOutputStream(outputStream);

            byte[] processedBytes = outputStream.toByteArray();
            String base64Image = Base64.getEncoder().encodeToString(processedBytes);

            ensureFileEntityExists(path, fileName, mimeType, processedBytes);

            ExtractionResult result = extractor.extract(base64Image, mimeType);

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
                canonicalText.append("Plik: ").append(fileName).append(". ");

                if (dto.getEntities() != null) {
                    Map<String, EntityMention> mentionsByLabel = new LinkedHashMap<>();
                    List<EntityMention> personMentions = new ArrayList<>();
                    int unnamedPersonIndex = 0;
                    long personCount = dto.getEntities().stream()
                            .filter(entity -> "PERSON".equalsIgnoreCase(entity.getType()))
                            .count();
                    String tagForResolve = entityTag != null && personCount <= 1 ? entityTag : null;

                    for (ExtractedEntityDto p : dto.getEntities()) {
                        String label = p.getLabel();
                        if (label == null || label.isBlank()) {
                            if ("PERSON".equalsIgnoreCase(p.getType())) {
                                unnamedPersonIndex++;
                                label = "Osoba " + unnamedPersonIndex;
                            } else {
                                label = "Nieznana postać";
                            }
                        }

                        EntityMention mention = EntityMention.builder()
                            .filePath(path)
                            .label(label)
                            .confidence(new BigDecimal("0.900"))
                            .status(MentionStatus.SUGGESTED)
                            .build();
                        
                        if (p.getVisualCues() != null) {
                            try {
                                mention.setVisualCues(objectMapper.writeValueAsString(p.getVisualCues()));
                            } catch (Exception e) {}
                        }
                        
                        mention = mentionRepo.save(mention);
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

                        identityResolutionService.resolve(mention, tagForResolve);
                        if ("PERSON".equalsIgnoreCase(p.getType())) {
                            personMentions.add(mention);
                        }

                        canonicalText.append("Postać: ").append(label).append(". ");
                        if (p.getActions() != null) {
                            canonicalText.append("Czynności: ").append(String.join(", ", p.getActions())).append(". ");
                        }
                        if (p.getObjects() != null) {
                            canonicalText.append("Obiekty: ").append(String.join(", ", p.getObjects())).append(". ");
                        }
                    }

                    if (dto.getRelations() != null) {
                        for (var relation : dto.getRelations()) {
                            if (relation == null || relation.getSubjectLabel() == null || relation.getObjectLabel() == null) {
                                continue;
                            }
                            String factAction = RelationConstants.toFactAction(relation.getRelation());
                            if (factAction == null) {
                                continue;
                            }

                            EntityMention subject = mentionsByLabel.get(relation.getSubjectLabel().toLowerCase(Locale.ROOT).trim());
                            EntityMention object = mentionsByLabel.get(relation.getObjectLabel().toLowerCase(Locale.ROOT).trim());
                            if (subject == null || object == null) {
                                continue;
                            }

                            factRepo.save(Fact.builder()
                                    .mention(subject)
                                    .action(factAction)
                                    .object(object.getLabel())
                                    .filePath(path)
                                    .confidence(new BigDecimal("0.900"))
                                    .build());

                            if (RelationConstants.isSymmetric(factAction)) {
                                factRepo.save(Fact.builder()
                                        .mention(object)
                                        .action(factAction)
                                        .object(subject.getLabel())
                                        .filePath(path)
                                        .confidence(new BigDecimal("0.900"))
                                        .build());
                            }

                            canonicalText.append("Relacja: ")
                                    .append(subject.getLabel())
                                    .append(" ")
                                    .append(RelationConstants.prettyRelation(factAction))
                                    .append(" ")
                                    .append(object.getLabel())
                                    .append(". ");
                        }
                    }

                    faceIdentityService.processImageFaces(processedBytes, path, fileName, personMentions);
                }

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
        } catch (Exception e) {
            throw new RuntimeException("Error while processing image: " + path, e);
        }
    }


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

    @Transactional
    public void ingestMultipartFile(MultipartFile file, FolderEntity folder, String entityTag) throws IOException {
        String fileName = file.getOriginalFilename();
        String extension = StringUtils.getFilenameExtension(fileName);
        String group = folder.getName();

        String path = "dir://" + folder.getName() + "/" + fileName;
        if (entityTag != null && !entityTag.isBlank()) {
            storeEntityTag(path, fileName, entityTag.trim());
        }
        byte[] data = file.getBytes();

        Document parsedDocument = chooseParser(data, path, fileName, extension);
        if (parsedDocument != null) {
            parsedDocument.metadata()
                    .put("document_id", group)
                    .put("filename", fileName)
                    .put("path", path);
            ingestor.ingest(parsedDocument);
            log.info("Successfully ingested file: {}", path);
        }
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
package com.rag.rag.knowledge.graph;

import com.rag.rag.auth.security.CurrentUserService;
import com.rag.rag.chat.service.QueryPlan;
import com.rag.rag.folder.entity.FileEntity;
import com.rag.rag.folder.repository.FileRepository;
import com.rag.rag.knowledge.entity.EntityMention;
import com.rag.rag.knowledge.entity.KnowledgeEntity;
import com.rag.rag.knowledge.entity.MentionStatus;
import com.rag.rag.knowledge.fact.Fact;
import com.rag.rag.knowledge.fact.FactStatementRewriter;
import com.rag.rag.knowledge.repository.EntityMentionRepository;
import com.rag.rag.knowledge.repository.FactRepository;
import com.rag.rag.knowledge.repository.FaceEmbeddingRepository;
import com.rag.rag.knowledge.repository.KnowledgeEntityRepository;
import com.rag.rag.knowledge.identity.IdentityResolutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads graph evidence selected by the query planner.  This service never
 * interprets the wording of a user question; entities and file paths are
 * already validated planner output.
 *
 * <p>AGENTS.md: GRAPH path is for people ({@code PERSON}) and their relations;
 * only certain (CONFIRMED, confidence ≥ threshold) mentions and facts are exposed.</p>
 */
@Service
@RequiredArgsConstructor
public class GraphQueryService {
    private static final Pattern FILE_REFERENCE = Pattern.compile("@\\\"([^\\\"]+)\\\"|@([^\\s,\\]!?]+)");
    private static final String PERSON_TYPE = "PERSON";
    private final KnowledgeEntityRepository entityRepository;
    private final EntityMentionRepository mentionRepository;
    private final FileRepository fileRepository;
    private final FactRepository factRepository;
    private final FaceEmbeddingRepository faceEmbeddingRepository;
    private final MentionEvidencePolicy mentionEvidencePolicy;
    private final IdentityResolutionService identityResolutionService;
    private final CurrentUserService currentUserService;

    @Value("${rag.graph.min-fact-confidence:0.75}")
    private double minFactConfidence = 0.75;

    /** Canonical display names of certain human (PERSON) entities in the workspace. */
    @Transactional(readOnly = true)
    public List<String> availableEntityNames() {
        return ownedEntities().stream()
                .filter(this::isUsablePersonEntity)
                .map(KnowledgeEntity::getDisplayName)
                .filter(name -> name != null && !name.isBlank())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<String> validateEntityNames(List<String> requestedNames) {
        if (requestedNames == null || requestedNames.isEmpty()) return List.of();
        List<KnowledgeEntity> people = ownedEntities().stream()
                .filter(this::isUsablePersonEntity)
                .toList();
        List<String> result = new ArrayList<>();
        for (String requested : requestedNames) {
            if (requested == null || requested.isBlank()) continue;
            String match = resolvePersonDisplayName(requested.trim(), people);
            if (match != null) {
                result.add(match);
            }
        }
        return result.stream().distinct().toList();
    }

    /**
     * Resolve Polish name variants (Olka→Olek) and aliases from free text tokens.
     * Used when the planner returns empty entities or only declined forms.
     */
    @Transactional(readOnly = true)
    public List<String> resolveEntityNamesFromText(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<KnowledgeEntity> people = ownedEntities().stream()
                .filter(this::isUsablePersonEntity)
                .toList();
        if (people.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> found = new LinkedHashSet<>();
        for (String token : PolishNameMatcher.extractEntityTokens(text)) {
            String match = resolvePersonDisplayName(token, people);
            if (match != null) {
                found.add(match);
            }
        }
        return List.copyOf(found);
    }

    private String resolvePersonDisplayName(String requested, List<KnowledgeEntity> people) {
        if (requested == null || requested.isBlank() || people == null) {
            return null;
        }
        String needle = requested.trim();
        for (KnowledgeEntity entity : people) {
            String name = entity.getDisplayName();
            if (name == null || name.isBlank()) {
                continue;
            }
            if (name.equalsIgnoreCase(needle)) {
                return name;
            }
            if (PolishNameMatcher.namesMatch(needle, name)) {
                return name;
            }
        }
        return null;
    }

    @Transactional(readOnly = true)
    public List<String> validateFilePaths(List<String> requestedPaths) {
        if (requestedPaths == null || requestedPaths.isEmpty()) return List.of();
        UUID ownerId = currentUserService.findUserId().orElse(null);
        return requestedPaths.stream()
                .filter(path -> path != null && !path.isBlank())
                .filter(path -> ownerId == null
                        ? fileRepository.findByPath(path).isPresent()
                        : fileRepository.findByPathAndOwnerId(path, ownerId).isPresent())
                .distinct().toList();
    }

    /** Resolves the technical {@code @file} syntax only inside the current owner's library. */
    @Transactional(readOnly = true)
    public List<String> resolveExplicitFileScope(String question) {
        UUID ownerId = currentUserService.findUserId().orElse(null);
        if (ownerId == null || question == null || question.isBlank()) return List.of();

        LinkedHashSet<String> references = new LinkedHashSet<>();
        Matcher matcher = FILE_REFERENCE.matcher(question);
        while (matcher.find()) {
            String value = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            if (value != null && !value.isBlank()) references.add(value.trim());
        }
        if (references.isEmpty()) return List.of();

        LinkedHashSet<String> paths = new LinkedHashSet<>();
        String normalizedQuestion = question.toLowerCase(Locale.ROOT);
        for (FileEntity file : fileRepository.findAllByOwnerId(ownerId)) {
            if (file.getPath() == null) continue;
            boolean exactLibraryMention = file.getFileName() != null
                    && normalizedQuestion.contains("@" + file.getFileName().toLowerCase(Locale.ROOT));
            for (String reference : references) {
                if (exactLibraryMention || file.getPath().equalsIgnoreCase(reference)
                        || (file.getFileName() != null && file.getFileName().equalsIgnoreCase(reference))) {
                    paths.add(file.getPath());
                    break;
                }
            }
        }
        return List.copyOf(paths);
    }

    public boolean hasExplicitFileReference(String question) {
        return question != null && FILE_REFERENCE.matcher(question).find();
    }

    @Transactional(readOnly = true)
    public List<String> imagePathsForEntities(List<String> entityNames) {
        if (entityNames == null || entityNames.isEmpty()) return List.of();
        Set<String> paths = new LinkedHashSet<>();
        for (String name : validateEntityNames(entityNames)) {
            findOwnedEntityByName(name).ifPresent(entity ->
                    mentionRepository.findByEntityId(entity.getId()).stream()
                            .filter(this::isCertainMention)
                            .map(EntityMention::getFilePath)
                            .filter(this::isImagePath)
                            .forEach(paths::add));
        }
        return List.copyOf(paths);
    }

    @Transactional(readOnly = true)
    public List<String> imagePathsForAllEntities(List<String> entityNames) {
        List<String> validated = validateEntityNames(entityNames);
        if (validated.isEmpty()) return List.of();

        Set<String> intersection = null;
        for (String name : validated) {
            Optional<KnowledgeEntity> entity = findOwnedEntityByName(name);
            if (entity.isEmpty()) return List.of();
            Set<String> paths = mentionRepository.findByEntityId(entity.get().getId()).stream()
                    .filter(this::isCertainMention)
                    .map(EntityMention::getFilePath)
                    .filter(this::isImagePath)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            if (intersection == null) intersection = paths;
            else intersection.retainAll(paths);
            if (intersection.isEmpty()) return List.of();
        }
        return intersection == null ? List.of() : List.copyOf(intersection);
    }

    private List<KnowledgeEntity> ownedEntities() {
        UUID ownerId = currentUserService.findUserId().orElse(null);
        if (ownerId == null) {
            return List.of();
        }
        return entityRepository.findAllByOwnerId(ownerId);
    }

    private Optional<KnowledgeEntity> findOwnedEntityByName(String name) {
        UUID ownerId = currentUserService.findUserId().orElse(null);
        if (ownerId == null || name == null || name.isBlank()) {
            return Optional.empty();
        }
        return entityRepository.findFirstByDisplayNameIgnoreCaseAndOwnerId(name.trim(), ownerId);
    }

    @Transactional(readOnly = true)
    public GraphEvidenceResult buildEvidence(QueryPlan plan) {
        if (plan == null) {
            return new GraphEvidenceResult("", List.of());
        }
        boolean includeAllOwnedPeople = plan.retrievalMode() == QueryPlan.RetrievalMode.GRAPH
                && plan.entities().isEmpty()
                && plan.fileScope().isEmpty();
        return buildEvidence(plan.entities(), plan.fileScope(), plan.entityMatchMode(), includeAllOwnedPeople);
    }

    @Transactional(readOnly = true)
    public GraphEvidenceResult buildEvidence(List<String> entityNames, List<String> fileScope,
                                             EntityMatchMode entityMatchMode) {
        return buildEvidence(entityNames, fileScope, entityMatchMode, false);
    }

    private GraphEvidenceResult buildEvidence(List<String> entityNames, List<String> fileScope,
                                              EntityMatchMode entityMatchMode,
                                              boolean includeAllOwnedPeople) {
        List<String> validatedEntities = validateEntityNames(entityNames);
        List<String> validatedScope = validateFilePaths(fileScope);
        EntityMatchMode mode = entityMatchMode == null ? EntityMatchMode.ANY : entityMatchMode;
        LinkedHashSet<String> certainPaths = new LinkedHashSet<>();

        if (includeAllOwnedPeople) {
            certainPaths.addAll(allOwnedImagePathsWithCertainPeople());
        } else if (mode == EntityMatchMode.ALL_SAME_FILE && !validatedEntities.isEmpty()) {
            certainPaths.addAll(imagePathsForAllEntities(validatedEntities));
            if (!validatedScope.isEmpty()) certainPaths.retainAll(validatedScope);
        } else {
            if (!validatedEntities.isEmpty()) {
                certainPaths.addAll(imagePathsForEntities(validatedEntities));
                if (!validatedScope.isEmpty()) {
                    certainPaths.retainAll(validatedScope);
                }
            }
            for (String path : validatedScope) {
                if (validatedEntities.isEmpty() || hasCertainEvidenceForFile(path, validatedEntities)) {
                    certainPaths.add(path);
                }
            }
        }

        return assembleEvidence(List.copyOf(certainPaths), validatedEntities, includeAllOwnedPeople);
    }

    private List<String> allOwnedImagePathsWithCertainPeople() {
        UUID ownerId = currentUserService.findUserId().orElse(null);
        if (ownerId == null) return List.of();
        List<String> imagePaths = fileRepository.findAllByOwnerId(ownerId).stream()
                .filter(file -> file.getFileType() != null
                        && file.getFileType().toLowerCase(Locale.ROOT).startsWith("image/"))
                .map(FileEntity::getPath)
                .filter(path -> path != null && !path.isBlank())
                .sorted()
                .toList();
        if (imagePaths.isEmpty()) return List.of();
        Set<String> certain = mentionRepository.findByFilePathIn(imagePaths).stream()
                .filter(this::isCertainMention)
                .filter(mention -> isPersonEntity(mention.getEntity()))
                .map(EntityMention::getFilePath)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return imagePaths.stream().filter(certain::contains).toList();
    }

    private GraphEvidenceResult assembleEvidence(List<String> paths, List<String> requestedEntities,
                                                 boolean includeAggregates) {
        if (paths == null || paths.isEmpty()) {
            return new GraphEvidenceResult("", List.of(), List.of(), List.of());
        }
        UUID ownerId = currentUserService.findUserId().orElse(null);
        if (ownerId == null) return new GraphEvidenceResult("", List.of());

        List<FileEntity> batchFiles = fileRepository.findAllByPathInAndOwnerId(paths, ownerId);
        if (batchFiles == null || batchFiles.isEmpty()) {
            batchFiles = paths.stream()
                    .map(path -> fileRepository.findByPathAndOwnerId(path, ownerId).orElse(null))
                    .filter(java.util.Objects::nonNull)
                    .toList();
        }
        Map<String, FileEntity> filesByPath = batchFiles.stream()
                .collect(java.util.stream.Collectors.toMap(FileEntity::getPath, file -> file,
                        (first, ignored) -> first, LinkedHashMap::new));
        List<EntityMention> batchMentions = mentionRepository.findByFilePathIn(paths);
        if (batchMentions == null || batchMentions.isEmpty()) {
            batchMentions = paths.stream().flatMap(path -> mentionRepository.findByFilePath(path).stream()).toList();
        }
        Map<String, List<EntityMention>> mentionsByPath = batchMentions.stream()
                .collect(java.util.stream.Collectors.groupingBy(EntityMention::getFilePath,
                        LinkedHashMap::new, java.util.stream.Collectors.toList()));
        List<Fact> batchFacts = factRepository.findByFilePathIn(paths);
        if (batchFacts == null || batchFacts.isEmpty()) {
            batchFacts = paths.stream().flatMap(path -> factRepository.findByFilePath(path).stream()).toList();
        }
        Map<String, List<Fact>> factsByPath = batchFacts.stream()
                .collect(java.util.stream.Collectors.groupingBy(Fact::getFilePath,
                        LinkedHashMap::new, java.util.stream.Collectors.toList()));

        List<GraphPhotoEvidence> photos = new ArrayList<>();
        List<GroundedVisualClaim> claims = new ArrayList<>();
        Map<String, Integer> participantCounts = new LinkedHashMap<>();
        int photoIndex = 0;
        for (String path : paths) {
            FileEntity file = filesByPath.get(path);
            if (file == null) continue;
            photoIndex++;
            List<EntityMention> mentions = mentionsByPath.getOrDefault(path, List.of()).stream()
                    .filter(this::isCertainMention)
                    .filter(mention -> mention.getEntity() == null || isPersonEntity(mention.getEntity()))
                    .toList();
            List<Fact> facts = factsByPath.getOrDefault(path, List.of()).stream()
                    .filter(this::isCertainFact)
                    .sorted(Comparator.comparing(Fact::getCreatedAt,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();
            String rendered = buildFullContextForFile(file, mentions, facts);
            List<GraphEvidenceItem> items = evidenceItems(photoIndex, path, rendered);
            photos.add(new GraphPhotoEvidence(String.valueOf(photoIndex), path, items));

            mentions.stream().map(this::mentionDisplayName)
                    .filter(name -> name != null && !name.isBlank())
                    .filter(name -> !identityResolutionService.isGenericPersonLabel(name))
                    .distinct()
                    .forEach(name -> participantCounts.merge(name, 1, Integer::sum));
            facts.stream()
                    .filter(fact -> fact.getMention() != null && fact.getMention().getEntity() != null)
                    .filter(fact -> requestedEntities.isEmpty() || requestedEntities.stream().anyMatch(name ->
                            fact.getMention().getEntity().getDisplayName().equalsIgnoreCase(name)))
                    .map(this::toClaim)
                    .forEach(claims::add);
        }
        if (includeAggregates && !participantCounts.isEmpty()) {
            List<GraphEvidenceItem> aggregateItems = new ArrayList<>();
            int index = 0;
            for (Map.Entry<String, Integer> entry : participantCounts.entrySet()) {
                index++;
                aggregateItems.add(new GraphEvidenceItem("A." + index, GraphEvidenceItem.Kind.AGGREGATE,
                        aggregateStatement(entry.getKey(), entry.getValue()), ""));
            }
            photos.add(0, new GraphPhotoEvidence("G", "", aggregateItems));
        }
        String context = photos.stream().map(GraphPhotoEvidence::render)
                .filter(value -> !value.isBlank())
                .collect(java.util.stream.Collectors.joining("\n"));
        return new GraphEvidenceResult(context, paths, claims.stream().distinct().toList(), photos);
    }

    private static String aggregateStatement(String name, int count) {
        if (count == 1) {
            return name + " występuje na jednym pewnym zdjęciu.";
        }
        return name + " występuje na " + count + " pewnych zdjęciach.";
    }

    private List<GraphEvidenceItem> evidenceItems(int photoIndex, String path, String rendered) {
        if (rendered == null || rendered.isBlank()) return List.of();
        List<GraphEvidenceItem> items = new ArrayList<>();
        int itemIndex = 0;
        for (String rawLine : rendered.lines().toList()) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.isBlank() || "Fakty:".equals(line)) continue;
            if (line.startsWith("- ")) line = line.substring(2).trim();
            GraphEvidenceItem.Kind kind = line.startsWith("Uczestnicy:")
                    ? GraphEvidenceItem.Kind.PARTICIPANTS
                    : line.startsWith("Scena:") || line.startsWith("Otoczenie:")
                    || line.startsWith("Oświetlenie:") || line.startsWith("Tło:")
                    || line.startsWith("Podsumowanie:")
                    ? GraphEvidenceItem.Kind.SCENE
                    : line.startsWith("Widoczny tekst:")
                    ? GraphEvidenceItem.Kind.TEXT
                    : rawLine.trim().startsWith("-")
                    ? GraphEvidenceItem.Kind.FACT
                    : GraphEvidenceItem.Kind.MENTION;
            itemIndex++;
            items.add(new GraphEvidenceItem("E" + photoIndex + "." + itemIndex, kind, line, path));
        }
        return List.copyOf(items);
    }

    @Transactional(readOnly = true)
    public BigDecimal entityConfidenceForFile(List<String> entityNames, String filePath) {
        if (entityNames == null || entityNames.isEmpty() || filePath == null) return BigDecimal.ZERO;
        return mentionRepository.findByFilePath(filePath).stream()
                .filter(this::isCertainMention)
                .filter(mention -> mention.getEntity() != null)
                .filter(mention -> entityNames.stream().anyMatch(name ->
                        mention.getEntity().getDisplayName().equalsIgnoreCase(name)))
                .map(mentionEvidencePolicy::evidenceConfidence)
                .filter(value -> value != null)
                .max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
    }

    /**
     * Display names of certain (CONFIRMED) human participants on the given files.
     * Skips non-PERSON entities and generic vision labels (e.g. person 1).
     */
    @Transactional(readOnly = true)
    public List<String> certainParticipantNamesForPaths(List<String> filePaths) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        certainParticipantNamesByPath(filePaths).values().forEach(names::addAll);
        return names.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    /**
     * Bulk variant used by catalog overviews. It applies exactly the same certainty and
     * canonical-identity policy as graph answers without issuing one query per file.
     */
    @Transactional(readOnly = true)
    public Map<String, List<String>> certainParticipantNamesByPath(Collection<String> filePaths) {
        if (filePaths == null || filePaths.isEmpty()) {
            return Map.of();
        }
        List<String> paths = filePaths.stream()
                .filter(path -> path != null && !path.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        if (paths.isEmpty()) {
            return Map.of();
        }
        Map<String, LinkedHashSet<String>> namesByPath = new LinkedHashMap<>();
        for (EntityMention mention : mentionRepository.findByFilePathIn(paths)) {
            if (!isCertainMention(mention) || mention.getEntity() == null
                    || !isPersonEntity(mention.getEntity())) {
                continue;
            }
            String displayName = mention.getEntity().getDisplayName();
            if (displayName == null || displayName.isBlank()
                    || identityResolutionService.isGenericPersonLabel(displayName)) {
                continue;
            }
            namesByPath.computeIfAbsent(mention.getFilePath(), ignored -> new LinkedHashSet<>())
                    .add(displayName.trim());
        }
        Map<String, List<String>> result = new LinkedHashMap<>();
        namesByPath.forEach((path, names) -> result.put(path,
                names.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList()));
        return Map.copyOf(result);
    }

    /**
     * True when the file has confirmed graph evidence suitable as a certain source.
     */
    @Transactional(readOnly = true)
    public boolean hasCertainEvidenceForFile(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return false;
        }
        boolean hasMention = mentionRepository.findByFilePath(filePath).stream()
                .anyMatch(this::isCertainMention);
        if (hasMention) {
            return true;
        }
        return !getCertainFactsForFile(filePath).isEmpty();
    }

    @Transactional(readOnly = true)
    public boolean hasCertainEvidenceForFile(String filePath, List<String> entityNames) {
        if (entityNames != null && !entityNames.isEmpty()) {
            return entityConfidenceForFile(entityNames, filePath).compareTo(BigDecimal.ZERO) > 0;
        }
        return hasCertainEvidenceForFile(filePath);
    }

    @Transactional(readOnly = true)
    public String buildContextForEntities(List<String> entityNames) {
        return buildContextForEntities(entityNames, Set.of());
    }

    private String buildContextForEntities(List<String> entityNames, Set<String> allowedPaths) {
        StringBuilder context = new StringBuilder();
        for (String name : validateEntityNames(entityNames)) {
            findOwnedEntityByName(name)
                    .ifPresent(entity -> appendEntityContext(context, entity, allowedPaths));
        }
        return context.toString();
    }

    @Transactional(readOnly = true)
    public String buildFullContextForFile(String filePath) {
        if (filePath == null || filePath.isBlank()) return "";
        UUID ownerId = currentUserService.findUserId().orElse(null);
        if (ownerId == null) return "";
        Optional<FileEntity> ownedFile = fileRepository.findByPathAndOwnerId(filePath, ownerId);
        if (ownedFile.isEmpty()) return "";
        List<EntityMention> mentions = mentionRepository.findByFilePath(filePath).stream()
                .filter(this::isCertainMention)
                .filter(mention -> mention.getEntity() == null || isPersonEntity(mention.getEntity()))
                .toList();
        return buildFullContextForFile(ownedFile.get(), mentions, getCertainFactsForFile(filePath));
    }

    private String buildFullContextForFile(FileEntity file, List<EntityMention> mentions, List<Fact> facts) {
        StringBuilder context = new StringBuilder();
        // Certain identity-linked human mentions and facts only (AGENTS.md people path).
        List<String> participantNames = mentions.stream()
                .map(this::mentionDisplayName)
                .filter(name -> name != null && !name.isBlank())
                .filter(name -> !identityResolutionService.isGenericPersonLabel(name))
                .distinct()
                .toList();
        if (!participantNames.isEmpty()) {
            context.append("Uczestnicy: ").append(String.join(", ", participantNames)).append('\n');
        }
        for (EntityMention mention : mentions) {
            appendMention(context, mention);
        }
        if (!facts.isEmpty()) {
            context.append("Fakty:\n");
            for (Fact fact : facts) {
                appendFact(context, fact);
            }
        }
        // Scene/summary only — never dump raw structured_vision JSON (person 1 noise + token bloat).
        appendFileContext(context, file);
        return context.toString();
    }

    @Transactional(readOnly = true)
    public List<Fact> getFactsForFile(String filePath) {
        return getCertainFactsForFile(filePath);
    }

    @Transactional(readOnly = true)
    public List<Fact> getCertainFactsForFile(String filePath) {
        if (filePath == null || filePath.isBlank()) return List.of();
        return factRepository.findByFilePath(filePath).stream()
                .filter(this::isCertainFact)
                .sorted(Comparator.comparing(Fact::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    /** Claim-level evidence: the fact and its subject identity must both be certain. */
    @Transactional(readOnly = true)
    public List<GroundedVisualClaim> certainClaimsForFile(String filePath, List<String> entityNames) {
        if (filePath == null || filePath.isBlank()) return List.of();
        List<String> requested = entityNames == null ? List.of() : entityNames;
        return getCertainFactsForFile(filePath).stream()
                .filter(fact -> fact.getMention() != null && fact.getMention().getEntity() != null)
                .filter(fact -> requested.isEmpty() || requested.stream().anyMatch(name ->
                        fact.getMention().getEntity().getDisplayName().equalsIgnoreCase(name)))
                .map(this::toClaim)
                .toList();
    }

    /** Certain named face anchors for a pixel-level verifier. */
    @Transactional(readOnly = true)
    public List<EntityVisualAnchor> certainEntityAnchorsForFile(String filePath, List<String> entityNames) {
        if (faceEmbeddingRepository == null || filePath == null || filePath.isBlank()) return List.of();
        List<String> requested = entityNames == null ? List.of() : entityNames;
        return faceEmbeddingRepository.findByFilePath(filePath).stream()
                .filter(embedding -> embedding.getMention() != null && isCertainMention(embedding.getMention()))
                .filter(embedding -> embedding.getEntity() != null)
                .filter(embedding -> requested.isEmpty() || requested.stream().anyMatch(name ->
                        embedding.getEntity().getDisplayName().equalsIgnoreCase(name)))
                .filter(embedding -> embedding.getBbox() != null && embedding.getBbox().length >= 4)
                .map(embedding -> new EntityVisualAnchor(
                        embedding.getMention().getId(),
                        embedding.getEntity().getDisplayName(),
                        embedding.getMention().getFaceAnchorId(),
                        java.util.stream.IntStream.range(0, 4)
                                .mapToObj(index -> embedding.getBbox()[index]).toList(),
                        mentionEvidencePolicy.evidenceConfidence(embedding.getMention())))
                .toList();
    }

    private GroundedVisualClaim toClaim(Fact fact) {
        EntityMention mention = fact.getMention();
        String entity = mention.getEntity().getDisplayName();
        String value = fact.getObject();
        if (fact.getTargetMention() != null) {
            EntityMention target = fact.getTargetMention();
            if (isCertainMention(target) && target.getEntity() != null
                    && target.getEntity().getDisplayName() != null
                    && !identityResolutionService.isGenericPersonLabel(target.getEntity().getDisplayName())) {
                value = target.getEntity().getDisplayName();
            } else {
                value = "nierozpoznana osoba";
            }
        }
        String statement = fact.getStatementPl();
        boolean stalePlaceholder = mention.getVisionLabel() != null && statement != null
                && statement.toLowerCase(Locale.ROOT).startsWith(mention.getVisionLabel().toLowerCase(Locale.ROOT));
        boolean hasPerson1Leak = statement != null && statement.toLowerCase(Locale.ROOT).contains("person ");
        if (statement == null || statement.isBlank() || stalePlaceholder || hasPerson1Leak
                || (fact.getTargetMention() != null && !isCertainMention(fact.getTargetMention()))) {
            statement = FactStatementRewriter.buildStatement(entity, fact.getAction(), value);
        }
        return new GroundedVisualClaim("F-" + fact.getId(), mention.getId(), entity,
                fact.getAction(), value, statement, fact.getFilePath(), fact.getConfidence(),
                fact.getEvidenceOrigin() == null ? "GRAPH_FACT" : fact.getEvidenceOrigin(),
                mention.getFaceAnchorId());
    }

    private void appendEntityContext(StringBuilder context, KnowledgeEntity entity, Set<String> allowedPaths) {
        if (!isPersonEntity(entity)) {
            return;
        }
        for (EntityMention mention : mentionRepository.findByEntityId(entity.getId())) {
            if (!isCertainMention(mention)
                    || (allowedPaths != null && !allowedPaths.isEmpty()
                    && !allowedPaths.contains(mention.getFilePath()))) {
                continue;
            }
            appendMention(context, mention);
            for (Fact fact : getCertainFactsForFile(mention.getFilePath())) {
                if (fact.getMention() != null && mention.getId().equals(fact.getMention().getId())) {
                    appendFact(context, fact);
                }
            }
        }
    }

    private void appendMention(StringBuilder context, EntityMention mention) {
        String name = mentionDisplayName(mention);
        if (name == null || name.isBlank()) {
            return;
        }
        List<String> parts = new ArrayList<>();
        appendJsonStringList(parts, mention.getVisualCues());
        List<String> objects = new ArrayList<>();
        appendJsonStringList(objects, mention.getContextObjects());
        if (!objects.isEmpty()) {
            parts.add("obiekty/otoczenie: " + String.join(", ", objects));
        }
        List<String> texts = new ArrayList<>();
        appendJsonStringList(texts, mention.getNearbyText());
        if (!texts.isEmpty()) {
            parts.add("napisy: " + String.join(", ", texts));
        }
        if (parts.isEmpty()) {
            context.append(name).append('\n');
        } else {
            context.append(name).append(": ").append(String.join("; ", parts)).append('\n');
        }
    }

    private void appendFact(StringBuilder context, Fact fact) {
        if (fact == null || fact.getMention() == null) {
            return;
        }
        String name = mentionDisplayName(fact.getMention());
        String value = fact.getObject();
        if (fact.getTargetMention() != null) {
            String targetName = mentionDisplayName(fact.getTargetMention());
            if (targetName != null && !targetName.isBlank()
                    && !identityResolutionService.isGenericPersonLabel(targetName)) {
                value = targetName;
            } else if (identityResolutionService.isGenericPersonLabel(targetName)) {
                value = "nierozpoznana osoba";
            }
        }
        String action = fact.getAction() == null ? "" : fact.getAction().trim();
        // Prefer immutable Polish statement when it already uses a real name.
        String statement = fact.getStatementPl();
        if (statement != null && !statement.isBlank()
                && name != null && statement.toLowerCase(Locale.ROOT).contains(name.toLowerCase(Locale.ROOT))) {
            context.append("- ").append(ensureSentence(statement.trim())).append('\n');
            return;
        }
        StringBuilder line = new StringBuilder("- ");
        if (name != null && !name.isBlank()) {
            line.append(name).append(' ');
        }
        if (!action.isBlank()) {
            // Technical predicates → readable Polish glue; keep open NL actions as-is.
            line.append(readableTechnicalAction(action));
        }
        if (value != null && !value.isBlank()) {
            line.append(' ').append(value.trim());
        }
        context.append(ensureSentence(line.toString().trim())).append('\n');
    }

    private void appendFileContext(StringBuilder context, FileEntity file) {
        // Intentionally omit structuredVisionContext from LLM dump (placeholders + bloat).
        if (file.getImageScene() != null && !file.getImageScene().isBlank()) {
            context.append("Scena: ").append(file.getImageScene().trim()).append('\n');
        }
        appendSceneAttributes(context, file.getSceneAttributes());
        if (file.getImageSummary() != null && !file.getImageSummary().isBlank()) {
            context.append("Podsumowanie: ").append(file.getImageSummary().trim()).append('\n');
        }
        appendVisibleTexts(context, file.getVisibleTexts());
    }

    private void appendVisibleTexts(StringBuilder context, String visibleTextsJson) {
        if (visibleTextsJson == null || visibleTextsJson.isBlank()) return;
        try {
            com.fasterxml.jackson.databind.JsonNode root =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(visibleTextsJson);
            if (!root.isArray()) return;
            LinkedHashSet<String> texts = new LinkedHashSet<>();
            root.forEach(node -> {
                String value = node.isTextual()
                        ? node.asText("").trim()
                        : node.path("text").asText("").trim();
                if (!value.isBlank()) texts.add(value);
            });
            if (!texts.isEmpty()) {
                context.append("Widoczny tekst: ").append(String.join(", ", texts)).append('\n');
            }
        } catch (Exception ignored) {
            // Malformed optional text JSON must not break an otherwise certain photo graph.
        }
    }

    private void appendSceneAttributes(StringBuilder context, String sceneAttributesJson) {
        if (sceneAttributesJson == null || sceneAttributesJson.isBlank()) {
            return;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode root =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(sceneAttributesJson);
            if (root.has("setting") && !root.get("setting").asText("").isBlank()) {
                context.append("Otoczenie: ").append(root.get("setting").asText().trim()).append('\n');
            }
            if (root.has("lighting") && !root.get("lighting").asText("").isBlank()) {
                context.append("Oświetlenie: ").append(root.get("lighting").asText().trim()).append('\n');
            }
            if (root.has("background") && root.get("background").isArray()) {
                List<String> bg = new ArrayList<>();
                root.get("background").forEach(n -> {
                    String v = n.asText("").trim();
                    if (!v.isBlank()) {
                        bg.add(v);
                    }
                });
                if (!bg.isEmpty()) {
                    context.append("Tło: ").append(String.join(", ", bg)).append('\n');
                }
            }
        } catch (Exception ignored) {
            // Malformed JSON must not break graph dump.
        }
    }

    private String mentionDisplayName(EntityMention mention) {
        if (mention == null) {
            return null;
        }
        if (mention.getEntity() != null && mention.getEntity().getDisplayName() != null
                && !mention.getEntity().getDisplayName().isBlank()) {
            return mention.getEntity().getDisplayName().trim();
        }
        if (mention.getLabel() != null && !mention.getLabel().isBlank()) {
            return mention.getLabel().trim();
        }
        return null;
    }

    private static String readableTechnicalAction(String action) {
        if (action == null) {
            return "";
        }
        return FactStatementRewriter.readableAction(action);
    }

    private static String ensureSentence(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("- ")) {
            // Keep bullet; ensure trailing period on the content.
            String body = trimmed.substring(2).trim();
            if (body.isEmpty()) {
                return trimmed;
            }
            return "- " + (body.matches(".*[.!?]$") ? body : body + ".");
        }
        return trimmed.matches(".*[.!?]$") ? trimmed : trimmed + ".";
    }

    /** Parses JSON string arrays or comma-ish lists into plain display tokens. */
    private static void appendJsonStringList(List<String> target, String raw) {
        if (raw == null || raw.isBlank() || target == null) {
            return;
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            String inner = trimmed.substring(1, trimmed.length() - 1).trim();
            if (inner.isEmpty()) {
                return;
            }
            for (String part : inner.split(",")) {
                String token = part.trim();
                if ((token.startsWith("\"") && token.endsWith("\""))
                        || (token.startsWith("'") && token.endsWith("'"))) {
                    token = token.substring(1, token.length() - 1).trim();
                }
                if (!token.isEmpty()) {
                    target.add(token);
                }
            }
            return;
        }
        target.add(trimmed);
    }

    /** Certain mention: CONFIRMED status and confidence at or above threshold. */
    private boolean isCertainMention(EntityMention mention) {
        return mentionEvidencePolicy.isCertain(mention);
    }

    /**
     * Subject must be certain. Target may be uncertain (partial identity): kept as
     * „nierozpoznana osoba” so spatial layout still answers questions.
     */
    private boolean isCertainFact(Fact fact) {
        return fact != null
                && fact.getConfidence() != null
                && fact.getConfidence().compareTo(BigDecimal.valueOf(minFactConfidence)) >= 0
                && isCertainMention(fact.getMention());
    }

    /** Humans only — animals/objects are not GRAPH entity targets (AGENTS.md). */
    private boolean isPersonEntity(KnowledgeEntity entity) {
        return entity != null && PERSON_TYPE.equalsIgnoreCase(entity.getType());
    }

    private boolean isUsablePersonEntity(KnowledgeEntity entity) {
        if (!isPersonEntity(entity)) {
            return false;
        }
        if (identityResolutionService.isGenericPersonLabel(entity.getDisplayName())) {
            return false;
        }
        return mentionRepository.findByEntityId(entity.getId()).stream()
                .anyMatch(this::isCertainMention);
    }

    private boolean isImagePath(String path) {
        return fileRepository.findByPath(path).map(file -> file.getFileType() != null
                && file.getFileType().toLowerCase(Locale.ROOT).startsWith("image/")).orElse(false);
    }
}

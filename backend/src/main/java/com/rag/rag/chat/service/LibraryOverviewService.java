package com.rag.rag.chat.service;

import com.rag.rag.auth.security.CurrentUserService;
import com.rag.rag.core.text.Utf8MojibakeRepair;
import com.rag.rag.folder.entity.FileEntity;
import com.rag.rag.folder.entity.FolderEntity;
import com.rag.rag.folder.entity.IngestionStatus;
import com.rag.rag.folder.repository.FileRepository;
import com.rag.rag.folder.repository.FolderRepository;
import com.rag.rag.knowledge.graph.GraphEvidenceItem;
import com.rag.rag.knowledge.graph.GraphEvidenceResult;
import com.rag.rag.knowledge.graph.GraphPhotoEvidence;
import com.rag.rag.knowledge.graph.GraphQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Builds a closed, owner-scoped catalog inventory. It deliberately does not read embeddings,
 * image descriptions, facts, relations or visual observations.
 */
@Service
@RequiredArgsConstructor
public class LibraryOverviewService {

    private static final int EVIDENCE_LIST_CHUNK = 20;
    private static final String UNASSIGNED_GROUP = "Bez folderu";
    private static final String SELECTED_FILES_GROUP = "Wybrane pliki";

    private final FileRepository fileRepository;
    private final FolderRepository folderRepository;
    private final CurrentUserService currentUserService;
    private final GraphQueryService graphQueryService;

    @Value("${rag.overview.max-file-names:100}")
    private int maxFileNames = 100;

    @Value("${rag.overview.max-person-names:100}")
    private int maxPersonNames = 100;

    @Transactional(readOnly = true)
    public Overview build(QueryPlan plan) {
        UUID ownerId = currentUserService.findUserId().orElse(null);
        if (ownerId == null || plan == null) {
            return Overview.unavailableOverview();
        }

        List<FolderEntity> ownedFolders = folderRepository.findAllByOwnerIdOrderByUpdatedAtDesc(ownerId);
        List<FolderEntity> selectedFolders = selectedFolders(plan, ownerId, ownedFolders);
        List<FileEntity> files = scopedFiles(ownerId, plan).stream()
                .filter(file -> file != null && file.getPath() != null && !file.getPath().isBlank())
                .sorted(fileComparator(selectedFolders.isEmpty() ? ownedFolders : selectedFolders))
                .toList();
        List<InventoryGroup> groups = inventoryGroups(plan, files, selectedFolders, ownedFolders);

        List<String> paths = files.stream().map(FileEntity::getPath).distinct().toList();
        Map<String, List<String>> namesByPath = graphQueryService.certainParticipantNamesByPath(paths);
        List<PersonEntry> allPeople = peopleEntries(groups, namesByPath);
        int personCount = allPeople.size();
        List<PersonEntry> shownPeople = allPeople.stream()
                .limit(Math.max(0, maxPersonNames))
                .toList();
        int omittedPersonNames = Math.max(0, personCount - shownPeople.size());

        List<FileListEntry> allFileNames = fileEntries(groups);
        List<FileListEntry> shownFileNames = allFileNames.stream()
                .limit(Math.max(0, maxFileNames))
                .toList();
        int omittedFileNames = Math.max(0, allFileNames.size() - shownFileNames.size());

        int folderCount = plan.scopeKind() == QueryPlan.ScopeKind.FILE
                ? 0 : groups.stream().filter(group -> !group.synthetic()).toList().size();
        Map<FileKind, Long> totalKinds = countKinds(files);
        Map<IngestionStatus, Long> totalStatuses = countStatuses(files);

        List<GraphEvidenceItem> items = new ArrayList<>();
        int itemIndex = 1;
        String scopeStatement = scopeStatement(plan.scopeKind(), folderCount, files.size(),
                totalKinds, totalStatuses);
        items.add(inventoryItem(itemIndex++, scopeStatement));

        for (InventoryGroup group : groups) {
            Set<String> groupPeople = group.files().stream()
                    .map(FileEntity::getPath)
                    .map(path -> namesByPath.getOrDefault(path, List.of()))
                    .flatMap(List::stream)
                    .collect(Collectors.toCollection(() -> new java.util.TreeSet<>(
                            String.CASE_INSENSITIVE_ORDER)));
            items.add(inventoryItem(itemIndex++, groupStatement(group, groupPeople.size())));
        }

        items.add(inventoryItem(itemIndex++, peopleCountStatement(personCount,
                shownPeople.size(), omittedPersonNames)));
        for (List<PersonEntry> chunk : chunks(shownPeople, EVIDENCE_LIST_CHUNK)) {
            items.add(inventoryItem(itemIndex++, "Potwierdzone osoby: "
                    + chunk.stream().map(PersonEntry::render).collect(Collectors.joining(", ")) + "."));
        }

        items.add(inventoryItem(itemIndex++, fileNamesCountStatement(files.size(),
                shownFileNames.size(), omittedFileNames)));
        for (List<FileListEntry> chunk : chunks(shownFileNames, EVIDENCE_LIST_CHUNK)) {
            items.add(inventoryItem(itemIndex++, "Nazwy plików: "
                    + chunk.stream().map(FileListEntry::render).collect(Collectors.joining(", ")) + "."));
        }

        GraphPhotoEvidence inventory = new GraphPhotoEvidence(
                "I", "", List.copyOf(items), "Inwentarz katalogowy");
        String context = inventory.render();
        GraphEvidenceResult evidence = new GraphEvidenceResult(
                context, List.of(), List.of(), List.of(inventory));
        String fallback = fallbackAnswer(scopeStatement, groups, personCount, shownPeople,
                omittedPersonNames, files.size(), shownFileNames, omittedFileNames);

        return new Overview(evidence, fallback, files.size(), folderCount, personCount,
                omittedFileNames, omittedPersonNames, false);
    }

    private List<FolderEntity> selectedFolders(
            QueryPlan plan, UUID ownerId, List<FolderEntity> ownedFolders) {
        if (plan.scopeKind() != QueryPlan.ScopeKind.FOLDER) {
            return ownedFolders;
        }
        List<FolderEntity> selected = new ArrayList<>();
        for (UUID folderId : plan.folderScope()) {
            if (folderId == null) continue;
            folderRepository.findByIdAndOwnerId(folderId, ownerId).ifPresent(selected::add);
        }
        return List.copyOf(selected);
    }

    private List<FileEntity> scopedFiles(UUID ownerId, QueryPlan plan) {
        if (plan.scopeKind() == QueryPlan.ScopeKind.FOLDER
                || plan.scopeKind() == QueryPlan.ScopeKind.FILE) {
            if (plan.fileScope() == null || plan.fileScope().isEmpty()) return List.of();
            return fileRepository.findAllByPathInAndOwnerId(plan.fileScope(), ownerId);
        }
        return fileRepository.findAllByOwnerId(ownerId);
    }

    private List<InventoryGroup> inventoryGroups(
            QueryPlan plan, List<FileEntity> files, List<FolderEntity> selectedFolders,
            List<FolderEntity> ownedFolders) {
        if (plan.scopeKind() == QueryPlan.ScopeKind.FILE) {
            return List.of(new InventoryGroup(SELECTED_FILES_GROUP, files, true));
        }

        List<FolderEntity> folderScope = plan.scopeKind() == QueryPlan.ScopeKind.FOLDER
                ? selectedFolders : ownedFolders;
        List<GroupAccumulator> accumulators = folderScope.stream()
                .map(folder -> new GroupAccumulator(cleanFolderName(folder.getName()),
                        folderPrefix(folder.getName())))
                .toList();
        List<FileEntity> unassigned = new ArrayList<>();
        for (FileEntity file : files) {
            GroupAccumulator group = matchingGroup(file.getPath(), accumulators);
            if (group == null) {
                unassigned.add(file);
            } else {
                group.files.add(file);
            }
        }

        List<InventoryGroup> groups = new ArrayList<>();
        for (GroupAccumulator accumulator : accumulators) {
            groups.add(new InventoryGroup(accumulator.name,
                    accumulator.files.stream().sorted(fileNameComparator()).toList(), false));
        }
        if (!unassigned.isEmpty()) {
            groups.add(new InventoryGroup(UNASSIGNED_GROUP,
                    unassigned.stream().sorted(fileNameComparator()).toList(), true));
        }
        return List.copyOf(groups);
    }

    private List<PersonEntry> peopleEntries(
            List<InventoryGroup> groups, Map<String, List<String>> namesByPath) {
        Map<String, Set<String>> foldersByPerson = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (InventoryGroup group : groups) {
            for (FileEntity file : group.files()) {
                for (String name : namesByPath.getOrDefault(file.getPath(), List.of())) {
                    foldersByPerson.computeIfAbsent(name, ignored -> new LinkedHashSet<>())
                            .add(group.name());
                }
            }
        }
        boolean includeFolders = groups.size() > 1;
        return foldersByPerson.entrySet().stream()
                .map(entry -> new PersonEntry(entry.getKey(),
                        includeFolders ? entry.getValue().stream()
                                .sorted(String.CASE_INSENSITIVE_ORDER).toList() : List.of()))
                .toList();
    }

    private List<FileListEntry> fileEntries(List<InventoryGroup> groups) {
        boolean includeFolders = groups.size() > 1;
        List<FileListEntry> entries = new ArrayList<>();
        for (InventoryGroup group : groups) {
            for (FileEntity file : group.files()) {
                entries.add(new FileListEntry(fileName(file), includeFolders ? group.name() : ""));
            }
        }
        return entries.stream()
                .sorted(Comparator.comparing(FileListEntry::folder, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(FileListEntry::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private String scopeStatement(QueryPlan.ScopeKind scopeKind, int folderCount, int fileCount,
                                  Map<FileKind, Long> kinds,
                                  Map<IngestionStatus, Long> statuses) {
        String subject = scopeKind == QueryPlan.ScopeKind.FILE ? "Wybrany zakres"
                : scopeKind == QueryPlan.ScopeKind.FOLDER ? "Wybrane foldery" : "Biblioteka";
        if (fileCount == 0) {
            if (scopeKind == QueryPlan.ScopeKind.UNRESTRICTED && folderCount > 0) {
                return subject + " zawiera " + folderCount + " "
                        + polishCount(folderCount, "folder", "foldery", "folderów")
                        + ", ale nie zawiera plików.";
            }
            return switch (scopeKind) {
                case FOLDER -> "Wybrane foldery są puste.";
                case FILE -> "Wybrany zakres jest pusty.";
                case UNRESTRICTED -> "Biblioteka jest pusta.";
            };
        }

        StringBuilder statement = new StringBuilder(subject).append(" zawiera ");
        if (scopeKind != QueryPlan.ScopeKind.FILE) {
            statement.append(folderCount).append(' ')
                    .append(polishCount(folderCount, "folder", "foldery", "folderów"))
                    .append(" oraz ");
        }
        statement.append(fileCount).append(' ')
                .append(polishCount(fileCount, "plik", "pliki", "plików"));
        List<String> kindParts = kindParts(kinds);
        if (!kindParts.isEmpty()) statement.append(": ").append(String.join(", ", kindParts));
        statement.append('.');
        String statusesText = statusesStatement(statuses);
        if (!statusesText.isBlank()) statement.append(' ').append(statusesText);
        return statement.toString();
    }

    private String groupStatement(InventoryGroup group, int confirmedPeople) {
        Map<FileKind, Long> kinds = countKinds(group.files());
        Map<IngestionStatus, Long> statuses = countStatuses(group.files());
        String groupKind = group.synthetic() ? "Grupa" : "Folder";
        StringBuilder statement = new StringBuilder(groupKind).append(" „")
                .append(group.name()).append("” zawiera ")
                .append(group.files().size()).append(' ')
                .append(polishCount(group.files().size(), "plik", "pliki", "plików"));
        List<String> kindParts = kindParts(kinds);
        if (!kindParts.isEmpty()) statement.append(": ").append(String.join(", ", kindParts));
        statement.append(". Ma ").append(confirmedPeople).append(' ')
                .append(polishCount(confirmedPeople,
                        "unikalną potwierdzoną osobę",
                        "unikalne potwierdzone osoby",
                        "unikalnych potwierdzonych osób"))
                .append('.');
        String statusesText = statusesStatement(statuses);
        if (!statusesText.isBlank()) statement.append(' ').append(statusesText);
        return statement.toString();
    }

    private String peopleCountStatement(int total, int shown, int omitted) {
        if (total == 0) {
            return "W tym zakresie nie ma żadnej potwierdzonej osoby z kanonicznym imieniem.";
        }
        String statement = "Zakres zawiera " + total + " "
                + polishCount(total, "unikalną potwierdzoną osobę",
                "unikalne potwierdzone osoby", "unikalnych potwierdzonych osób") + ".";
        if (omitted > 0) {
            statement += " Pokazano " + shown + " imion, a " + omitted + " pominięto z powodu limitu.";
        }
        return statement;
    }

    private String fileNamesCountStatement(int total, int shown, int omitted) {
        if (total == 0) return "W tym zakresie nie ma nazw plików.";
        String statement = "Zakres zawiera " + total + " "
                + polishCount(total, "nazwę pliku", "nazwy plików", "nazw plików") + ".";
        if (omitted > 0) {
            statement += " Pokazano " + shown + " nazw, a " + omitted + " pominięto z powodu limitu.";
        }
        return statement;
    }

    private String fallbackAnswer(
            String scopeStatement, List<InventoryGroup> groups,
            int personCount, List<PersonEntry> people, int omittedPeople,
            int fileCount, List<FileListEntry> fileNames, int omittedFiles) {
        String groupDetails = groups.isEmpty() ? "" : " "
                + groups.stream().map(group -> (group.synthetic() ? "Grupa" : "Folder")
                        + " „" + group.name() + "”: "
                        + group.files().size() + " "
                        + polishCount(group.files().size(), "plik", "pliki", "plików"))
                .collect(Collectors.joining("; ")) + ".";
        String peopleText = personCount == 0
                ? "Nie ma jeszcze żadnej potwierdzonej osoby z kanonicznym imieniem."
                : "Potwierdzone osoby (" + personCount + "): "
                + people.stream().map(PersonEntry::render).collect(Collectors.joining(", "))
                + (omittedPeople > 0 ? "; pominięto " + omittedPeople + " imion" : "") + ".";
        String filesText = fileCount == 0
                ? "Nie ma plików do wymienienia."
                : "Nazwy plików (" + fileCount + "): "
                + fileNames.stream().map(FileListEntry::render).collect(Collectors.joining(", "))
                + (omittedFiles > 0 ? "; pominięto " + omittedFiles + " nazw" : "") + ".";
        return (scopeStatement + groupDetails + " " + peopleText + " " + filesText)
                .replaceAll("\\s+", " ").trim();
    }

    private Map<FileKind, Long> countKinds(List<FileEntity> files) {
        return files.stream().collect(Collectors.groupingBy(this::fileKind,
                () -> new EnumMap<>(FileKind.class), Collectors.counting()));
    }

    private Map<IngestionStatus, Long> countStatuses(List<FileEntity> files) {
        return files.stream().filter(file -> file.getIngestionStatus() != null)
                .collect(Collectors.groupingBy(FileEntity::getIngestionStatus,
                        () -> new EnumMap<>(IngestionStatus.class), Collectors.counting()));
    }

    private List<String> kindParts(Map<FileKind, Long> kinds) {
        List<String> parts = new ArrayList<>();
        appendCount(parts, kinds.getOrDefault(FileKind.IMAGE, 0L),
                "zdjęcie", "zdjęcia", "zdjęć");
        appendCount(parts, kinds.getOrDefault(FileKind.PDF, 0L),
                "plik PDF", "pliki PDF", "plików PDF");
        appendCount(parts, kinds.getOrDefault(FileKind.TEXT, 0L),
                "plik tekstowy", "pliki tekstowe", "plików tekstowych");
        appendCount(parts, kinds.getOrDefault(FileKind.OTHER, 0L),
                "inny plik", "inne pliki", "innych plików");
        return parts;
    }

    private String statusesStatement(Map<IngestionStatus, Long> statuses) {
        long ready = statuses.getOrDefault(IngestionStatus.READY, 0L);
        long processing = statuses.getOrDefault(IngestionStatus.PENDING, 0L)
                + statuses.getOrDefault(IngestionStatus.EXTRACTED, 0L);
        long review = statuses.getOrDefault(IngestionStatus.NEEDS_REVIEW, 0L);
        long failed = statuses.getOrDefault(IngestionStatus.FAILED, 0L);
        List<String> parts = new ArrayList<>();
        if (ready > 0) parts.add(ready + " gotowych");
        if (processing > 0) parts.add(processing + " w trakcie analizy");
        if (review > 0) parts.add(review + " wymagających przeglądu");
        if (failed > 0) parts.add(failed + " zakończonych błędem");
        return parts.isEmpty() ? "" : "Status analizy: " + String.join(", ", parts) + ".";
    }

    private FileKind fileKind(FileEntity file) {
        String type = file == null || file.getFileType() == null
                ? "" : file.getFileType().toLowerCase(Locale.ROOT);
        if (type.startsWith("image/")) return FileKind.IMAGE;
        if (type.contains("pdf")) return FileKind.PDF;
        if (type.startsWith("text/") || type.contains("word") || type.contains("document")) {
            return FileKind.TEXT;
        }
        return FileKind.OTHER;
    }

    private Comparator<FileEntity> fileComparator(List<FolderEntity> folders) {
        return Comparator.comparing(
                        (FileEntity file) -> folderNameForPath(file.getPath(), folders),
                        String.CASE_INSENSITIVE_ORDER)
                .thenComparing(this::fileName, String.CASE_INSENSITIVE_ORDER);
    }

    private Comparator<FileEntity> fileNameComparator() {
        return Comparator.comparing(this::fileName, String.CASE_INSENSITIVE_ORDER);
    }

    private String folderNameForPath(String path, List<FolderEntity> folders) {
        return folders.stream()
                .filter(folder -> path != null && path.startsWith(folderPrefix(folder.getName())))
                .map(FolderEntity::getName)
                .findFirst()
                .map(LibraryOverviewService::cleanFolderName)
                .orElse(UNASSIGNED_GROUP);
    }

    private GroupAccumulator matchingGroup(String path, List<GroupAccumulator> groups) {
        return groups.stream()
                .filter(group -> path != null && path.startsWith(group.prefix))
                .findFirst()
                .orElse(null);
    }

    private String fileName(FileEntity file) {
        String value = file == null ? "" : file.getFileName();
        if (value == null || value.isBlank()) value = file == null ? "" : file.getPath();
        value = Utf8MojibakeRepair.repair(value == null ? "" : value)
                .replace('\r', ' ').replace('\n', ' ').trim();
        int slash = Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\'));
        return slash >= 0 ? value.substring(slash + 1).trim() : value;
    }

    private static String cleanFolderName(String value) {
        String cleaned = Utf8MojibakeRepair.repair(value == null ? "" : value)
                .replace('\r', ' ').replace('\n', ' ').trim();
        return cleaned.isBlank() ? UNASSIGNED_GROUP : cleaned;
    }

    private static String folderPrefix(String folderName) {
        return "dir://" + (folderName == null ? "" : folderName) + "/";
    }

    private static GraphEvidenceItem inventoryItem(int index, String statement) {
        return new GraphEvidenceItem("I." + index, GraphEvidenceItem.Kind.INVENTORY, statement, "");
    }

    private static <T> List<List<T>> chunks(List<T> values, int size) {
        if (values == null || values.isEmpty()) return List.of();
        List<List<T>> result = new ArrayList<>();
        for (int start = 0; start < values.size(); start += size) {
            result.add(List.copyOf(values.subList(start, Math.min(values.size(), start + size))));
        }
        return List.copyOf(result);
    }

    private static void appendCount(
            List<String> parts, long count, String one, String few, String many) {
        if (count > 0) parts.add(count + " " + polishCount(count, one, few, many));
    }

    private static String polishCount(long count, String one, String few, String many) {
        long mod10 = Math.abs(count) % 10;
        long mod100 = Math.abs(count) % 100;
        if (count == 1) return one;
        if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) return few;
        return many;
    }

    private enum FileKind { IMAGE, PDF, TEXT, OTHER }

    private static final class GroupAccumulator {
        private final String name;
        private final String prefix;
        private final List<FileEntity> files = new ArrayList<>();

        private GroupAccumulator(String name, String prefix) {
            this.name = name;
            this.prefix = prefix;
        }
    }

    private record InventoryGroup(String name, List<FileEntity> files, boolean synthetic) {
        private InventoryGroup {
            name = cleanFolderName(name);
            files = files == null ? List.of() : List.copyOf(files);
        }
    }

    private record PersonEntry(String name, List<String> folders) {
        private String render() {
            return folders == null || folders.isEmpty()
                    ? name : name + " (foldery: " + String.join(", ", folders) + ")";
        }
    }

    private record FileListEntry(String name, String folder) {
        private String render() {
            return folder == null || folder.isBlank()
                    ? name : name + " (folder „" + folder + "”)";
        }
    }

    public record Overview(
            GraphEvidenceResult evidence,
            String inventoryAnswer,
            int fileCount,
            int folderCount,
            int confirmedPersonCount,
            int omittedFileNameCount,
            int omittedPersonNameCount,
            boolean unavailable
    ) {
        public Overview {
            evidence = evidence == null ? new GraphEvidenceResult("", List.of()) : evidence;
            inventoryAnswer = inventoryAnswer == null ? "" : inventoryAnswer.trim();
        }

        public boolean empty() {
            return fileCount == 0;
        }

        public static Overview unavailableOverview() {
            return new Overview(new GraphEvidenceResult("", List.of()),
                    "Nie udało się odczytać biblioteki.", 0, 0, 0, 0, 0, true);
        }
    }
}

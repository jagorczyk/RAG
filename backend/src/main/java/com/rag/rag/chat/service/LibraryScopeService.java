package com.rag.rag.chat.service;

import com.rag.rag.auth.security.CurrentUserService;
import com.rag.rag.folder.entity.FileEntity;
import com.rag.rag.folder.entity.FolderEntity;
import com.rag.rag.folder.repository.FileRepository;
import com.rag.rag.folder.repository.FolderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Resolves planner/UI folder scope against the current owner's catalog.
 * It performs only technical validation and set operations; it never infers
 * question intent from words or phrases.
 */
@Service
@RequiredArgsConstructor
public class LibraryScopeService {

    private final FolderRepository folderRepository;
    private final FileRepository fileRepository;
    private final CurrentUserService currentUserService;

    @Transactional(readOnly = true)
    public List<String> availableFolderNames() {
        UUID ownerId = currentUserService.findUserId().orElse(null);
        if (ownerId == null) return List.of();
        return folderRepository.findAllByOwnerIdOrderByUpdatedAtDesc(ownerId).stream()
                .map(FolderEntity::getName)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .toList();
    }

    @Transactional(readOnly = true)
    public FolderResolution resolveFolderNames(List<String> requestedNames) {
        UUID ownerId = currentUserService.findUserId().orElse(null);
        if (ownerId == null || requestedNames == null || requestedNames.isEmpty()) {
            return FolderResolution.empty();
        }
        List<FolderEntity> owned = folderRepository.findAllByOwnerIdOrderByUpdatedAtDesc(ownerId);
        LinkedHashSet<UUID> ids = new LinkedHashSet<>();
        boolean ambiguous = false;
        boolean unresolved = false;
        for (String requested : requestedNames) {
            if (requested == null || requested.isBlank()) continue;
            String wanted = normalized(requested);
            List<FolderEntity> matches = owned.stream()
                    .filter(folder -> normalized(folder.getName()).equals(wanted))
                    .toList();
            if (matches.size() == 1) {
                ids.add(matches.get(0).getId());
            } else if (matches.size() > 1) {
                ambiguous = true;
            } else {
                unresolved = true;
            }
        }
        return new FolderResolution(List.copyOf(ids), ambiguous, unresolved);
    }

    @Transactional(readOnly = true)
    public List<UUID> validateFolderIds(List<UUID> requestedIds) {
        UUID ownerId = currentUserService.findUserId().orElse(null);
        if (ownerId == null || requestedIds == null || requestedIds.isEmpty()) return List.of();
        LinkedHashSet<UUID> result = new LinkedHashSet<>();
        for (UUID id : requestedIds) {
            if (id != null && folderRepository.findByIdAndOwnerId(id, ownerId).isPresent()) {
                result.add(id);
            }
        }
        return List.copyOf(result);
    }

    @Transactional(readOnly = true)
    public List<String> filePathsForFolders(List<UUID> folderIds) {
        UUID ownerId = currentUserService.findUserId().orElse(null);
        if (ownerId == null || folderIds == null || folderIds.isEmpty()) return List.of();
        List<FolderEntity> folders = validateFolderIds(folderIds).stream()
                .map(id -> folderRepository.findByIdAndOwnerId(id, ownerId).orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
        if (folders.isEmpty()) return List.of();
        List<String> prefixes = folders.stream()
                .map(folder -> "dir://" + folder.getName() + "/")
                .toList();
        return fileRepository.findAllByOwnerId(ownerId).stream()
                .map(FileEntity::getPath)
                .filter(path -> path != null && prefixes.stream().anyMatch(path::startsWith))
                .sorted()
                .toList();
    }

    @Transactional(readOnly = true)
    public List<String> folderNames(List<UUID> folderIds) {
        UUID ownerId = currentUserService.findUserId().orElse(null);
        if (ownerId == null || folderIds == null || folderIds.isEmpty()) return List.of();
        List<String> names = new ArrayList<>();
        for (UUID id : validateFolderIds(folderIds)) {
            folderRepository.findByIdAndOwnerId(id, ownerId)
                    .map(FolderEntity::getName)
                    .filter(name -> name != null && !name.isBlank())
                    .ifPresent(names::add);
        }
        return List.copyOf(names);
    }

    private static String normalized(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value.trim(), Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
    }

    public record FolderResolution(List<UUID> ids, boolean ambiguous, boolean unresolved) {
        public FolderResolution {
            ids = ids == null ? List.of() : List.copyOf(ids);
        }

        public static FolderResolution empty() {
            return new FolderResolution(List.of(), false, false);
        }
    }
}

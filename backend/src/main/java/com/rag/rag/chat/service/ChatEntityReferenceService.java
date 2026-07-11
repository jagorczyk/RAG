package com.rag.rag.chat.service;

import com.rag.rag.chat.entity.ChatMessageEntity;
import com.rag.rag.chat.repository.ChatMessageRepository;
import com.rag.rag.knowledge.entity.EntityMention;
import com.rag.rag.knowledge.entity.KnowledgeEntity;
import com.rag.rag.knowledge.repository.EntityMentionRepository;
import com.rag.rag.knowledge.repository.KnowledgeEntityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ChatEntityReferenceService {

    private static final Pattern REFERENCE_PATTERN = Pattern.compile(
            "(?i).*(ta kobieta|ta kobietę|ta kobiete|ten mężczyzna|ten mezczyzna|ten mężczyznę|ten mezczyzne|ta osoba|ta postać|ta postac|ta dziewczyna|ten chłopak|ten chlopak|ta pani|ten pan|ten gość|ten gosc|ta twarz|ta osoba na zdjęciu|ta osoba na zdjeciu|ten na zdjęciu|ten na zdjeciu|ta po lewej|ten po prawej).*"
    );
    private static final Pattern FEMALE_HINT = Pattern.compile("(?i).*(kobieta|kobietę|kobiete|dziewczyna|pani).*");
    private static final Pattern MALE_HINT = Pattern.compile("(?i).*(mężczyzna|mezczyzna|mężczyznę|mezczyzne|chłopak|chlopak|pan|gość|gosc).*");

    private final ChatMessageRepository chatMessageRepository;
    private final EntityMentionRepository mentionRepository;
    private final KnowledgeEntityRepository entityRepository;

    public boolean isReferenceQuestion(String question) {
        return REFERENCE_PATTERN.matcher(question).matches();
    }

    public Optional<String> resolveReference(UUID chatId, String question) {
        if (!isReferenceQuestion(question)) {
            return Optional.empty();
        }

        List<String> recentTexts = loadRecentMessageTexts(chatId, 12);
        Set<String> knownNames = loadKnownEntityNames();
        List<String> recentlyMentioned = findRecentlyMentionedNames(recentTexts, knownNames);

        if (recentlyMentioned.isEmpty()) {
            recentlyMentioned = findNamesFromRecentSources(chatId, knownNames);
        }

        if (recentlyMentioned.isEmpty()) {
            return Optional.empty();
        }

        boolean femaleQuestion = FEMALE_HINT.matcher(question).matches();
        boolean maleQuestion = MALE_HINT.matcher(question).matches();

        if (femaleQuestion || maleQuestion) {
            for (String name : recentlyMentioned) {
                if (femaleQuestion && looksFemale(name)) {
                    return Optional.of(name);
                }
                if (maleQuestion && looksMale(name)) {
                    return Optional.of(name);
                }
            }
            for (String name : recentlyMentioned) {
                if (femaleQuestion && !looksMale(name)) {
                    return Optional.of(name);
                }
                if (maleQuestion && !looksFemale(name)) {
                    return Optional.of(name);
                }
            }
        }

        return Optional.of(recentlyMentioned.get(0));
    }

    private List<String> loadRecentMessageTexts(UUID chatId, int limit) {
        List<ChatMessageEntity> messages = chatMessageRepository.findAllByChatIdOrderByCreatedAtAsc(chatId);
        int start = Math.max(0, messages.size() - limit);
        return messages.subList(start, messages.size()).stream()
                .map(ChatMessageEntity::getTextContext)
                .filter(text -> text != null && !text.isBlank())
                .toList();
    }

    private Set<String> loadKnownEntityNames() {
        Set<String> names = new LinkedHashSet<>();
        for (KnowledgeEntity entity : entityRepository.findAll()) {
            if (entity.getDisplayName() != null) {
                names.add(entity.getDisplayName());
            }
        }
        for (EntityMention mention : mentionRepository.findAll()) {
            if (mention.getLabel() != null) {
                names.add(mention.getLabel());
            }
        }
        return names;
    }

    private List<String> findRecentlyMentionedNames(List<String> recentTexts, Set<String> knownNames) {
        List<String> ordered = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        for (int i = recentTexts.size() - 1; i >= 0; i--) {
            String text = recentTexts.get(i).toLowerCase(Locale.ROOT);
            for (String name : knownNames) {
                if (name.length() < 3) {
                    continue;
                }
                if (text.contains(name.toLowerCase(Locale.ROOT)) && seen.add(name)) {
                    ordered.add(name);
                }
            }
        }
        return ordered;
    }

    private List<String> findNamesFromRecentSources(UUID chatId, Set<String> knownNames) {
        List<ChatMessageEntity> messages = chatMessageRepository.findAllByChatIdOrderByCreatedAtAsc(chatId);
        List<String> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessageEntity message = messages.get(i);
            if (message.getImagePaths() == null) {
                continue;
            }
            for (String path : message.getImagePaths()) {
                for (EntityMention mention : mentionRepository.findAll()) {
                    if (!path.equals(mention.getFilePath()) || mention.getLabel() == null) {
                        continue;
                    }
                    if (knownNames.contains(mention.getLabel()) && seen.add(mention.getLabel())) {
                        result.add(mention.getLabel());
                    }
                }
            }
        }

        result.sort(Comparator.naturalOrder());
        return result;
    }

    private boolean looksFemale(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.contains("kobieta")
                || lower.contains("woman")
                || lower.contains("female")
                || lower.contains("dziewczyna");
    }

    private boolean looksMale(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.contains("mężczyzna")
                || lower.contains("mezczyzna")
                || lower.contains("man")
                || lower.contains("male")
                || lower.contains("chłopak")
                || lower.contains("chlopak");
    }
}

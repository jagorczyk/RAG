package com.rag.rag.knowledge.graph;

import com.rag.rag.knowledge.entity.EntityAlias;
import com.rag.rag.knowledge.entity.KnowledgeEntity;
import com.rag.rag.knowledge.fact.Fact;
import com.rag.rag.knowledge.repository.EntityAliasRepository;
import com.rag.rag.knowledge.repository.KnowledgeEntityRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class GraphQueryService {

    private static final Pattern NEXT_TO_ENTITY_PATTERN = Pattern.compile("(?i).*obok\\s+([\\p{L}0-9_-]+).*");
    private static final Pattern SPATIAL_ENTITY_PATTERN = Pattern.compile(
            "(?i).*(?:po lewej|po lewej stronie|po prawej|po prawej stronie)\\s+(?:od|strony)?\\s*([\\p{L}0-9_-]+).*"
    );

    private final EntityManager entityManager;
    private final KnowledgeEntityRepository entityRepository;
    private final EntityAliasRepository aliasRepository;

    @Transactional(readOnly = true)
    public List<Fact> getActivitiesForEntity(String entityNameOrAlias) {
        String sql = """
            SELECT f.* FROM facts f
            JOIN entity_mentions em ON f.mention_id = em.id
            LEFT JOIN entities e ON em.entity_id = e.id
            LEFT JOIN entity_aliases ea ON ea.entity_id = e.id
            WHERE (e.display_name ILIKE :name OR ea.alias ILIKE :name OR em.label ILIKE :name)
              AND em.status IN ('CONFIRMED', 'SUGGESTED')
              AND f.action NOT LIKE 'REL_%'
            ORDER BY f.created_at
            """;

        return entityManager.createNativeQuery(sql, Fact.class)
                .setParameter("name", "%" + entityNameOrAlias + "%")
                .getResultList();
    }

    @Transactional(readOnly = true)
    public List<Fact> getRelationFactsForEntity(String entityNameOrAlias, String relationAction) {
        String sql = """
            SELECT f.* FROM facts f
            JOIN entity_mentions em ON f.mention_id = em.id
            LEFT JOIN entities e ON em.entity_id = e.id
            LEFT JOIN entity_aliases ea ON ea.entity_id = e.id
            WHERE f.action = :relationAction
              AND em.status IN ('CONFIRMED', 'SUGGESTED')
              AND (e.display_name ILIKE :name OR ea.alias ILIKE :name OR em.label ILIKE :name)
            ORDER BY f.created_at
            """;

        return entityManager.createNativeQuery(sql, Fact.class)
                .setParameter("relationAction", relationAction)
                .setParameter("name", "%" + entityNameOrAlias + "%")
                .getResultList();
    }

    @Transactional(readOnly = true)
    public List<MentionFileRow> getFilesForEntity(String entityNameOrAlias) {
        String sql = """
            SELECT DISTINCT em.file_path, em.label
            FROM entity_mentions em
            LEFT JOIN entities e ON em.entity_id = e.id
            LEFT JOIN entity_aliases ea ON ea.entity_id = e.id
            WHERE (e.display_name ILIKE :name OR ea.alias ILIKE :name OR em.label ILIKE :name)
              AND em.status IN ('CONFIRMED', 'SUGGESTED')
            ORDER BY em.file_path
            """;

        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery(sql)
                .setParameter("name", "%" + entityNameOrAlias + "%")
                .getResultList();

        List<MentionFileRow> result = new ArrayList<>();
        for (Object[] row : rows) {
            result.add(new MentionFileRow((String) row[0], (String) row[1]));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public Optional<KnowledgeEntity> resolveEntityByAlias(String name) {
        String sql = """
            SELECT e.* FROM entities e
            LEFT JOIN entity_aliases ea ON ea.entity_id = e.id
            WHERE e.display_name ILIKE :name OR ea.alias ILIKE :name
            LIMIT 1
            """;

        List<KnowledgeEntity> results = entityManager.createNativeQuery(sql, KnowledgeEntity.class)
                .setParameter("name", "%" + name + "%")
                .getResultList();

        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Transactional(readOnly = true)
    public String buildContextForEntity(String entityName) {
        StringBuilder contextBuilder = new StringBuilder("[Fakty z grafu wiedzy]\n");
        boolean foundFacts = false;

        for (String variant : PolishNameMatcher.generateVariants(entityName)) {
            List<Fact> facts = getActivitiesForEntity(variant);
            for (Fact fact : facts) {
                foundFacts = true;
                appendFactLine(contextBuilder, fact);
            }
        }

        return foundFacts ? contextBuilder.toString() : "";
    }

    @Transactional(readOnly = true)
    public String buildContextForQuestion(String question) {
        Optional<String> resolvedEntity = findEntityNameInQuestion(question);
        if (resolvedEntity.isPresent()) {
            return buildContextForEntity(resolvedEntity.get());
        }

        StringBuilder contextBuilder = new StringBuilder("[Fakty z grafu wiedzy]\n");
        boolean foundFacts = false;
        Set<String> processedVariants = new LinkedHashSet<>();

        for (String token : PolishNameMatcher.extractEntityTokens(question)) {
            for (String variant : PolishNameMatcher.generateVariants(token)) {
                if (!processedVariants.add(variant)) {
                    continue;
                }
                List<Fact> facts = getActivitiesForEntity(variant);
                for (Fact fact : facts) {
                    foundFacts = true;
                    appendFactLine(contextBuilder, fact);
                }
            }
        }

        return foundFacts ? contextBuilder.toString() : "";
    }

    @Transactional(readOnly = true)
    public Optional<String> findEntityNameInQuestion(String question) {
        if (question == null || question.isBlank()) {
            return Optional.empty();
        }

        for (KnowledgeEntity entity : entityRepository.findAll()) {
            if (questionContainsName(question, entity.getDisplayName())) {
                return Optional.of(entity.getDisplayName());
            }
        }

        for (EntityAlias alias : aliasRepository.findAll()) {
            if (alias.getEntity() == null) {
                continue;
            }
            if (questionContainsName(question, alias.getAlias())) {
                return Optional.of(alias.getEntity().getDisplayName());
            }
        }

        return resolveEntityNameFromQuestion(question);
    }

    @Transactional(readOnly = true)
    public String buildNeighborContextForQuestion(String question) {
        return buildDirectedRelationContext(question, NEXT_TO_ENTITY_PATTERN, RelationConstants.NEXT_TO, "siedzi obok");
    }

    @Transactional(readOnly = true)
    public String buildSpatialLeftContextForQuestion(String question) {
        Matcher matcher = SPATIAL_ENTITY_PATTERN.matcher(question);
        if (!matcher.matches() || !question.toLowerCase(Locale.ROOT).contains("lewej")) {
            return "";
        }
        return buildSpatialSideContext(matcher.group(1), true);
    }

    @Transactional(readOnly = true)
    public String buildSpatialRightContextForQuestion(String question) {
        Matcher matcher = SPATIAL_ENTITY_PATTERN.matcher(question);
        if (!matcher.matches() || !question.toLowerCase(Locale.ROOT).contains("prawej")) {
            return "";
        }
        return buildSpatialSideContext(matcher.group(1), false);
    }

    @Transactional(readOnly = true)
    public String buildFileListContextForQuestion(String question) {
        Optional<String> entityName = resolveEntityNameFromQuestion(question);
        if (entityName.isEmpty()) {
            return "";
        }

        StringBuilder contextBuilder = new StringBuilder("[Pliki z grafu wiedzy]\n");
        boolean found = false;
        Map<String, Set<String>> filesByPath = new LinkedHashMap<>();

        for (String variant : PolishNameMatcher.generateVariants(entityName.get())) {
            for (MentionFileRow row : getFilesForEntity(variant)) {
                found = true;
                filesByPath.computeIfAbsent(row.filePath(), ignored -> new LinkedHashSet<>()).add(row.label());
            }
        }

        for (Map.Entry<String, Set<String>> entry : filesByPath.entrySet()) {
            contextBuilder.append("- ")
                    .append(entityName.get())
                    .append(" występuje w pliku: ")
                    .append(fileNameFromPath(entry.getKey()))
                    .append(" (etykieta: ")
                    .append(String.join(", ", entry.getValue()))
                    .append(") | plik: ")
                    .append(entry.getKey())
                    .append("\n");
        }

        return found ? contextBuilder.toString() : "";
    }

    @Transactional(readOnly = true)
    public String buildCoOccurrenceContextForQuestion(String question) {
        Optional<String> entityName = findEntityNameInQuestion(question);
        if (entityName.isEmpty()) {
            entityName = resolveEntityNameFromQuestion(question);
        }
        if (entityName.isEmpty()) {
            return "";
        }

        StringBuilder contextBuilder = new StringBuilder("[Współwystępowania z grafu wiedzy]\n");
        Map<String, Set<String>> peopleByFile = new LinkedHashMap<>();
        Set<String> allCoOccurringPeople = new LinkedHashSet<>();
        Set<String> processedRows = new LinkedHashSet<>();

        for (String variant : PolishNameMatcher.generateVariants(entityName.get())) {
            for (CoOccurrenceRow row : getCoOccurringPeopleForEntity(variant)) {
                String personName = resolveCoOccurrenceName(row);
                if (isSamePersonAsAnchor(entityName.get(), personName, row.label())) {
                    continue;
                }

                String rowKey = row.filePath() + "|" + personName.toLowerCase(Locale.ROOT);
                if (!processedRows.add(rowKey)) {
                    continue;
                }

                peopleByFile.computeIfAbsent(row.filePath(), ignored -> new LinkedHashSet<>()).add(personName);
                allCoOccurringPeople.add(personName);
            }
        }

        if (peopleByFile.isEmpty()) {
            return "";
        }

        for (Map.Entry<String, Set<String>> entry : peopleByFile.entrySet()) {
            contextBuilder.append("- ")
                    .append(entityName.get())
                    .append(" występuje w pliku ")
                    .append(fileNameFromPath(entry.getKey()))
                    .append(" razem z: ")
                    .append(String.join(", ", entry.getValue()))
                    .append(" | plik: ")
                    .append(entry.getKey())
                    .append("\n");
        }

        contextBuilder.append("- Wszystkie osoby współwystępujące z ")
                .append(entityName.get())
                .append(": ")
                .append(String.join(", ", allCoOccurringPeople))
                .append("\n");

        return contextBuilder.toString();
    }

    @Transactional(readOnly = true)
    public List<CoOccurrenceRow> getCoOccurringPeopleForEntity(String entityNameOrAlias) {
        String sql = """
            SELECT DISTINCT em2.file_path, em2.label, e2.display_name
            FROM entity_mentions em1
            LEFT JOIN entities e1 ON em1.entity_id = e1.id
            LEFT JOIN entity_aliases ea1 ON ea1.entity_id = e1.id
            JOIN entity_mentions em2 ON em2.file_path = em1.file_path AND em2.id <> em1.id
            LEFT JOIN entities e2 ON em2.entity_id = e2.id
            WHERE em1.status IN ('CONFIRMED', 'SUGGESTED')
              AND em2.status IN ('CONFIRMED', 'SUGGESTED')
              AND (e1.display_name ILIKE :name OR ea1.alias ILIKE :name OR em1.label ILIKE :name)
            ORDER BY em2.file_path, COALESCE(e2.display_name, em2.label)
            """;

        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery(sql)
                .setParameter("name", "%" + entityNameOrAlias + "%")
                .getResultList();

        List<CoOccurrenceRow> result = new ArrayList<>();
        for (Object[] row : rows) {
            result.add(new CoOccurrenceRow(
                    (String) row[0],
                    (String) row[1],
                    row[2] != null ? (String) row[2] : null
            ));
        }
        return result;
    }

    private String resolveCoOccurrenceName(CoOccurrenceRow row) {
        if (row.displayName() != null && !row.displayName().isBlank()) {
            return row.displayName();
        }
        return row.label();
    }

    private boolean isSamePersonAsAnchor(String anchorName, String personName, String label) {
        Set<String> anchorVariants = PolishNameMatcher.generateVariants(anchorName);
        for (String anchorVariant : anchorVariants) {
            if (matchesNameVariant(anchorVariant, personName) || matchesNameVariant(anchorVariant, label)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesNameVariant(String variant, String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        for (String nameVariant : PolishNameMatcher.generateVariants(name)) {
            if (variant.equalsIgnoreCase(nameVariant)) {
                return true;
            }
        }
        return false;
    }

    private String buildDirectedRelationContext(
            String question,
            Pattern entityPattern,
            String relationAction,
            String relationPhrase
    ) {
        Matcher matcher = entityPattern.matcher(question);
        if (!matcher.matches()) {
            return "";
        }

        String token = matcher.group(1);
        StringBuilder contextBuilder = new StringBuilder("[Relacje z grafu wiedzy]\n");
        boolean foundFacts = false;

        for (String variant : PolishNameMatcher.generateVariants(token)) {
            List<Fact> relationFacts = getRelationFactsForEntity(variant, relationAction);
            for (Fact fact : relationFacts) {
                foundFacts = true;
                String subjectName = resolveMentionName(fact.getMention());
                contextBuilder.append("- ")
                        .append(subjectName)
                        .append(" ")
                        .append(relationPhrase)
                        .append(" ")
                        .append(fact.getObject())
                        .append(" | plik: ")
                        .append(fact.getFilePath())
                        .append("\n");
            }
        }

        return foundFacts ? contextBuilder.toString() : "";
    }

    private String buildSpatialSideContext(String anchorName, boolean leftSideQuestion) {
        StringBuilder contextBuilder = new StringBuilder("[Relacje z grafu wiedzy]\n");
        boolean foundFacts = false;

        for (String variant : PolishNameMatcher.generateVariants(anchorName)) {
            if (leftSideQuestion) {
                foundFacts |= appendLeftSideAnswers(contextBuilder, variant);
            } else {
                foundFacts |= appendRightSideAnswers(contextBuilder, variant);
            }
        }

        return foundFacts ? contextBuilder.toString() : "";
    }

    private boolean appendLeftSideAnswers(StringBuilder contextBuilder, String anchorVariant) {
        boolean found = false;

        List<Fact> leftFacts = getRelationFactsWhereObjectMatches(anchorVariant, RelationConstants.LEFT_OF);
        for (Fact fact : leftFacts) {
            found = true;
            contextBuilder.append("- ")
                    .append(resolveMentionName(fact.getMention()))
                    .append(" jest po lewej od ")
                    .append(anchorVariant)
                    .append(" | plik: ")
                    .append(fact.getFilePath())
                    .append("\n");
        }

        List<Fact> rightFacts = getRelationFactsForEntity(anchorVariant, RelationConstants.RIGHT_OF);
        for (Fact fact : rightFacts) {
            found = true;
            contextBuilder.append("- ")
                    .append(fact.getObject())
                    .append(" jest po lewej od ")
                    .append(anchorVariant)
                    .append(" | plik: ")
                    .append(fact.getFilePath())
                    .append("\n");
        }

        return found;
    }

    private boolean appendRightSideAnswers(StringBuilder contextBuilder, String anchorVariant) {
        boolean found = false;

        List<Fact> leftFacts = getRelationFactsForEntity(anchorVariant, RelationConstants.LEFT_OF);
        for (Fact fact : leftFacts) {
            found = true;
            contextBuilder.append("- ")
                    .append(fact.getObject())
                    .append(" jest po prawej od ")
                    .append(anchorVariant)
                    .append(" | plik: ")
                    .append(fact.getFilePath())
                    .append("\n");
        }

        List<Fact> rightFacts = getRelationFactsWhereObjectMatches(anchorVariant, RelationConstants.RIGHT_OF);
        for (Fact fact : rightFacts) {
            found = true;
            contextBuilder.append("- ")
                    .append(resolveMentionName(fact.getMention()))
                    .append(" jest po prawej od ")
                    .append(anchorVariant)
                    .append(" | plik: ")
                    .append(fact.getFilePath())
                    .append("\n");
        }

        return found;
    }

    @Transactional(readOnly = true)
    public List<Fact> getRelationFactsWhereObjectMatches(String objectName, String relationAction) {
        String sql = """
            SELECT f.* FROM facts f
            JOIN entity_mentions em ON f.mention_id = em.id
            WHERE f.action = :relationAction
              AND em.status IN ('CONFIRMED', 'SUGGESTED')
              AND f.object ILIKE :objectName
            ORDER BY f.created_at
            """;

        return entityManager.createNativeQuery(sql, Fact.class)
                .setParameter("relationAction", relationAction)
                .setParameter("objectName", "%" + objectName + "%")
                .getResultList();
    }

    private Optional<String> resolveEntityNameFromQuestion(String question) {
        for (String token : PolishNameMatcher.extractEntityTokens(question)) {
            for (String variant : PolishNameMatcher.generateVariants(token)) {
                if (!getFilesForEntity(variant).isEmpty()) {
                    return Optional.of(variant);
                }
                if (!getActivitiesForEntity(variant).isEmpty()) {
                    return Optional.of(variant);
                }
            }
        }
        return Optional.empty();
    }

    private boolean questionContainsName(String question, String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        for (String variant : PolishNameMatcher.generateVariants(name)) {
            if (variant.length() < 3) {
                continue;
            }
            Pattern pattern = Pattern.compile(
                    "(?i)(?<![\\p{L}])" + Pattern.quote(variant) + "(?![\\p{L}])"
            );
            if (pattern.matcher(question).find()) {
                return true;
            }
        }
        return false;
    }

    private void appendFactLine(StringBuilder contextBuilder, Fact fact) {
        KnowledgeEntity entity = fact.getMention().getEntity();
        String entityName = entity != null ? entity.getDisplayName() : fact.getMention().getLabel();
        contextBuilder.append("- Postać: ")
                .append(entityName)
                .append(" (pewność: ")
                .append(fact.getConfidence())
                .append(") | ")
                .append(RelationConstants.prettyAction(fact.getAction()));
        if (fact.getObject() != null && !fact.getObject().isBlank()) {
            contextBuilder.append(" ").append(fact.getObject());
        }
        contextBuilder.append(" | plik: ").append(fact.getFilePath()).append("\n");
    }

    private String resolveMentionName(com.rag.rag.knowledge.entity.EntityMention mention) {
        if (mention.getEntity() != null) {
            return mention.getEntity().getDisplayName();
        }
        return mention.getLabel();
    }

    private String fileNameFromPath(String path) {
        if (path == null) {
            return "";
        }
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    public record MentionFileRow(String filePath, String label) {}

    public record CoOccurrenceRow(String filePath, String label, String displayName) {}
}

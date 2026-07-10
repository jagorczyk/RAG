package com.rag.rag.chat.service;

import com.rag.rag.knowledge.graph.GraphQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class QueryRouter {

    private static final Pattern NEIGHBOR_PATTERN = Pattern.compile("(?i).*(kto|jaka osoba|co to za osoba).*(obok|siedzi obok|stoi obok).*");
    private static final Pattern SPATIAL_LEFT_PATTERN = Pattern.compile("(?i).*(po lewej|po lewej stronie).*(od|strony).*");
    private static final Pattern SPATIAL_RIGHT_PATTERN = Pattern.compile("(?i).*(po prawej|po prawej stronie).*(od|strony).*");
    private static final Pattern CO_OCCURRENCE_PATTERN = Pattern.compile(
            "(?i).*("
                    + "z\\s+kim"
                    + "|z\\s+którymi|z\\s+ktorymi"
                    + "|osob(y|ami)\\s+z\\s+którymi|osob(y|ami)\\s+z\\s+ktorymi"
                    + "|kto\\s+jest.*na\\s+zdj"
                    + "|wspólnie|wspolnie"
                    + "|razem\\s+(na|z)"
                    + "|jak\\s+(mają|ma)\\s+na\\s+imi"
                    + ").*"
    );
    private static final Pattern ENTITY_FILES_PATTERN = Pattern.compile(
            "(?i).*((na których|w jakich).*(zdjęci|zdjeciach|plik|plikach|obraz|obrazach|foto|fotografiach)"
                    + "|(gdzie jest|gdzie występuje|w których plikach)).*"
    );
    private static final Pattern ENTITY_ACTIVITY_PATTERN = Pattern.compile(
            "(?i).*(co robi|co robił|co robiła|jakie czynności|czym się zajmuje).*(postać|osoba).*"
    );
    private static final Pattern NAMED_ACTIVITY_PATTERN = Pattern.compile(
            "(?i).*(co robi|co robił|co robiła|jakie czynności|czym się zajmuje).*"
    );
    private static final Pattern ENTITY_LIST_PATTERN = Pattern.compile("(?i).*(na których|gdzie jest).*(postać|osoba).*");
    private static final Pattern DESCRIPTIVE_ENTITY_PATTERN = Pattern.compile(
            "(?i).*(co to za|kim jest).*(kobieta|mężczyzna|mezczyzna|osoba|postać|postac).*"
    );
    private static final Pattern NAMED_IDENTITY_PATTERN = Pattern.compile(
            "(?i).*(kim jest|kto to jest|opisz|powiedz o|co wiesz o).*"
    );
    private static final Pattern REFERENCE_PATTERN = Pattern.compile(
            "(?i).*(ta kobieta|ta kobietę|ten mężczyzna|ten mezczyzna|ta osoba|ta postać|ta postac).*"
    );

    private final GraphQueryService graphQueryService;

    public enum QueryRoute {
        ENTITY_NEIGHBOR,
        ENTITY_SPATIAL_LEFT,
        ENTITY_SPATIAL_RIGHT,
        ENTITY_CO_OCCURRENCE,
        ENTITY_FILES,
        ENTITY_DESCRIPTION,
        ENTITY_ACTIVITY,
        ENTITY_LIST,
        DOCUMENT,
        HYBRID
    }

    public QueryRoute classify(String question) {
        if (NEIGHBOR_PATTERN.matcher(question).matches()) {
            return QueryRoute.ENTITY_NEIGHBOR;
        }
        if (SPATIAL_LEFT_PATTERN.matcher(question).matches()) {
            return QueryRoute.ENTITY_SPATIAL_LEFT;
        }
        if (SPATIAL_RIGHT_PATTERN.matcher(question).matches()) {
            return QueryRoute.ENTITY_SPATIAL_RIGHT;
        }
        if (CO_OCCURRENCE_PATTERN.matcher(question).matches()) {
            return QueryRoute.ENTITY_CO_OCCURRENCE;
        }
        if (ENTITY_FILES_PATTERN.matcher(question).matches()) {
            return QueryRoute.ENTITY_FILES;
        }
        if (REFERENCE_PATTERN.matcher(question).matches()) {
            return QueryRoute.ENTITY_DESCRIPTION;
        }
        if (DESCRIPTIVE_ENTITY_PATTERN.matcher(question).matches()) {
            return QueryRoute.ENTITY_DESCRIPTION;
        }
        if (NAMED_IDENTITY_PATTERN.matcher(question).matches()) {
            return QueryRoute.ENTITY_DESCRIPTION;
        }
        if (NAMED_ACTIVITY_PATTERN.matcher(question).matches()) {
            return QueryRoute.ENTITY_ACTIVITY;
        }
        if (ENTITY_ACTIVITY_PATTERN.matcher(question).matches()) {
            return QueryRoute.ENTITY_ACTIVITY;
        }
        if (ENTITY_LIST_PATTERN.matcher(question).matches()) {
            return QueryRoute.ENTITY_LIST;
        }
        if (graphQueryService.findEntityNameInQuestion(question).isPresent()) {
            return QueryRoute.HYBRID;
        }
        if (question.toLowerCase().contains("postać") || question.toLowerCase().contains("osoba")) {
            return QueryRoute.HYBRID;
        }
        return QueryRoute.DOCUMENT;
    }
}

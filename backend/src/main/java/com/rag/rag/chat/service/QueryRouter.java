package com.rag.rag.chat.service;

import com.rag.rag.knowledge.graph.GraphQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;
import java.util.Locale;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import com.rag.rag.knowledge.graph.PolishNameMatcher;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueryRouter {

    private static final Pattern NEIGHBOR_PATTERN = Pattern.compile(
            "(?i).*("
                    + "(kto|jaka osoba|co to za osoba|która osoba|ktora osoba)"
                    + ".*(obok|siedzi obok|stoi obok|siedzi przy|stoi przy|jest przy|znajduje się obok|znajduje sie obok|tuż obok|tuz obok)"
                    + "|(kto|jaka osoba).*(sąsiad|sasiad|najbliżej|najblizej|w pobliżu|w poblizu)"
                    + ").*"
    );

    private static final Pattern SPATIAL_LEFT_PATTERN = Pattern.compile(
            "(?i).*("
                    + "(po lewej|po lewej stronie|z lewej|z lewej strony|na lewo|lewa strona)"
                    + "|kto.*(z lewej|na lewo)"
                    + ").*"
    );

    private static final Pattern SPATIAL_RIGHT_PATTERN = Pattern.compile(
            "(?i).*("
                    + "(po prawej|po prawej stronie|z prawej|z prawej strony|na prawo|prawa strona)"
                    + "|kto.*(z prawej|na prawo)"
                    + ").*"
    );

    private static final Pattern CO_OCCURRENCE_PATTERN = Pattern.compile(
            "(?i).*("
                    + "z\\s+kim"
                    + "|z\\s+którymi|z\\s+ktorymi"
                    + "|osob(y|ami)\\s+z\\s+którymi|osob(y|ami)\\s+z\\s+ktorymi"
                    + "|kto\\s+jest.*na\\s+zdj.*\\s+z\\s"
                    + "|kto\\s+(jeszcze|poza|oprócz|oprocz)"
                    + "|wspólnie|wspolnie"
                    + "|wspólnie\\s+z|wspolnie\\s+z"
                    + "|razem\\s+(na|z)"
                    + "|razem\\s+z"
                    + "|jak\\s+(mają|ma|mieli|miały|miała|nazywają się|nazywaja sie)\\s+na\\s+imi"
                    + "|z\\s+kim\\s+(był|była|byli|były|jest|są|sa|będzie|bedzie|znajduje|pojawia|występuje|wystepuje|widzian)"
                    + "|w\\s+towarzystwie"
                    + "|towarzyszy|towarzyszą|towarzysza"
                    + "|na\\s+zdj(ęciu|eciu)\\s+z|na\\s+foto\\s+z|na\\s+fotce\\s+z|na\\s+obrazie\\s+z"
                    + "|z\\s+kim\\s+(na|w)"
                    + "|kto.*razem.*z"
                    + "|z\\s+kim\\s+(fotografuje|pozuje|widać|widac)"
                    + "|(ilu|ile\\s+osób|ile\\s+osob).*(jest|widać|widac).*z"
                    + "|współwystępuje|wspolwystepuje|współwystępują|wspolwystepuja"
                    + "|obok\\s+kogo"
                    + "|w\\s+(parze|parach)\\s+z"
                    + "|kto\\s+towarzyszy"
                    + "|imi(a|ona).*osób.*z|imi(a|ona).*osob.*z"
                    + ").*"
    );

    private static final Pattern ENTITY_FILES_PATTERN = Pattern.compile(
            "(?i).*("
                    + "(na których|w jakich|na jakich).*(zdjęci|zdjeciach|zdj|plik|plikach|obraz|obrazach|foto|fotografiach|materiał|material|dokument|dokumentach|folder|folderach|katalog|katalogach)"
                    + "|(gdzie jest|gdzie występuje|gdzie widać|gdzie widac|gdzie się pojawia|gdzie sie pojawia|gdzie można znaleźć|gdzie mozna znalezc|gdzie go znajdę|gdzie go znajde|gdzie ją znajdę|gdzie ja znajde)"
                    + "|w których plikach|w jakich plikach"
                    + "|na ilu zdjęciach|na ilu zdjeciach|ile zdjęć|ile zdjec"
                    + "|pokaż zdjęcia|pokaz zdjecia|pokaż pliki|pokaz pliki|wyświetl zdjęcia|wyswietl zdjecia"
                    + "|lista (zdjęć|zdjec|plików|plikow)"
                    + "|w których materiałach|w jakich materiałach|w jakich dokumentach"
                    + "|na jakich (zdjęciach|zdjeciach|fotografiach|obrazach)"
                    + "|w jakich (folderach|katalogach)"
                    + "|które zdjęcia|ktore zdjecia|jakie zdjęcia|jakie zdjecia"
                    + ").*"
    );

    private static final Pattern PHOTO_SEARCH_PATTERN = Pattern.compile(
            "(?i).*(daj|podaj|zwróc|zwroc|pokaz|pokaż|znajdz|znajdź|wyswietl|wyświetl|znaleźć).*"
                    + "(zdj|foto|fotkę|fotke|obraz).*"
    );

    private static final Pattern PHOTO_RELATION_SEARCH_PATTERN = Pattern.compile(
            "(?i).*(zdjecie|zdjęcie|zdjecia|zdjęcia|foto|obraz).*(\\bz\\b|\\bze\\b|obok|razem).*"
    );

    private static final Pattern SINGULAR_ENTITY_FILE_PATTERN = Pattern.compile(
            "(?i).*(na ktorym|na którym|na jakim)\\s+(zdjeciu|zdjęciu|foto|obrazie|pliku).*"
    );

    private static final Pattern ENTITY_ACTIVITY_PATTERN = Pattern.compile(
            "(?i).*(co robi|co robił|co robiła|co robią|co robia|co robiło|co robilo|jakie czynności|jakie aktywności|jakie aktywnosci|czym się zajmuje|czym sie zajmuje|czym się zajmował|czym sie zajmowal|czym się zajmowała|czym sie zajmowala|co porabia|co wykonuje|co wykonywał|co wykonywala|nad czym pracuje|jaką czynność|jaka czynnosc|co robi na zdjęciu|co robi na zdjeciu|jaką aktywność|jaka aktywnosc|co aktualnie robi|jakie czynności wykonuje).*(postać|osoba).*"
    );

    private static final Pattern NAMED_ACTIVITY_PATTERN = Pattern.compile(
            "(?i).*(co robi|co robił|co robiła|co robią|co robia|co robiło|co robilo|jakie czynności|jakie aktywności|jakie aktywnosci|czym się zajmuje|czym sie zajmuje|czym się zajmował|czym sie zajmowal|czym się zajmowała|czym sie zajmowala|co porabia|co wykonuje|co wykonywał|co wykonywala|nad czym pracuje|jaką czynność|jaka czynnosc|co robi na zdjęciu|co robi na zdjeciu|jaką aktywność|jaka aktywnosc|co aktualnie robi|jakie czynności wykonuje).*"
    );

    private static final Pattern ENTITY_LIST_PATTERN = Pattern.compile(
            "(?i).*("
                    + "(na których|gdzie jest|gdzie są|gdzie sa).*(postać|osoba|osoby|osób|osob)"
                    + "|(kto jest|kto występuje|kto wystepuje|kto się pojawia|kto sie pojawia).*(na|w).*(zdjęci|zdjeciach|pliku|foto|obraz)"
                    + "|lista (osób|osob|postaci)"
                    + "|wymień (osoby|osob|postacie)|wymien (osoby|osob|postacie)"
                    + "|jakie (osoby|osob|postacie) (są|sa|występują|wystepuja)"
                    + "|kto (jest|występuje|wystepuje|pojawia się|pojawia sie) (na|w)"
                    + "|ilu (ludzi|osób|osob) (jest|widać|widac)"
                    + "|kogo (widać|widac|można zobaczyć|mozna zobaczyc) (na|w)"
                    + "|wszyscy (na|w)"
                    + ").*"
    );

    private static final Pattern DESCRIPTIVE_ENTITY_PATTERN = Pattern.compile(
            "(?i).*(co to za|kim jest|kim ona jest|kim on jest).*(kobieta|mężczyzna|mezczyzna|osoba|postać|postac|człowiek|czlowiek|dziewczyna|chłopak|chlopak|pan|pani|gość|gosc|twarz).*"
    );

    private static final Pattern NAMED_IDENTITY_PATTERN = Pattern.compile(
            "(?i).*(kim jest|kto to jest|kto to|opisz|powiedz o|co wiesz o|powiedz coś o|powiedz cos o|opowiedz o|opowiedz mi o|przedstaw|scharakteryzuj|charakterystyka|jak wygląda|jak wyglada|opis (postaci|osoby)|dane o|informacje o|info o|czy znasz|co potrafisz powiedzieć o|co potrafisz powiedziec o|kim jest ta|kim jest ten|co to za).*"
    );

    private static final Pattern REFERENCE_PATTERN = Pattern.compile(
            "(?i).*(ta kobieta|ta kobietę|ta kobiete|ten mężczyzna|ten mezczyzna|ten mężczyznę|ten mezczyzne|ta osoba|ta postać|ta postac|ta dziewczyna|ten chłopak|ten chlopak|ta pani|ten pan|ten gość|ten gosc|ta twarz|ta osoba na zdjęciu|ta osoba na zdjeciu|ten na zdjęciu|ten na zdjeciu|ta po lewej|ten po prawej).*"
    );

    private static final Pattern GRAPH_RELATED_PATTERN = Pattern.compile(
            "(?i).*(postać|postac|postaci|osoba|osoby|osób|osob|ludzie|twarz|twarze|człowiek|czlowiek|znajom|koleg|koleżank|gość|gosc|goście|gosci|imię|imie|imiona|nazwisko|nazwiska|rozpoznaj|zidentyfikuj|identyfikuj|współwystępuj|wspolwystepuj|sąsiad|sasiad|relacj|powiązan|powiazan).*"
    );

    private final GraphQueryService graphQueryService;

    private static final Pattern FILE_REFERENCE_PATTERN = Pattern.compile(
            "(?i)@([\\w\\-]+(?:\\.[a-zA-Z0-9]+)?)"
    );

    private boolean isCombinedDocAndGraphQuestion(String question) {
        if (question == null || question.isBlank()) {
            return false;
        }
        String lower = question.toLowerCase(Locale.ROOT);
        boolean describesDoc = lower.contains("opisz")
                || lower.contains("opowiedz")
                || lower.contains("powiedz o")
                || lower.contains("wytłumacz")
                || lower.contains("wytlumacz")
                || lower.contains("wyjaśnij")
                || lower.contains("wyjasnij");
        boolean asksPeopleOnPhoto = (lower.contains("kto")
                || lower.contains("wskaż")
                || lower.contains("wskaz")
                || lower.contains("znajduje"))
                && (lower.contains("zdjęci")
                || lower.contains("zdjeciu")
                || lower.contains("foto")
                || lower.contains("obrazie")
                || lower.contains("pliku"));
        return describesDoc && asksPeopleOnPhoto;
    }

    public enum QueryRoute {
        ENTITY_NEIGHBOR,
        ENTITY_SPATIAL_LEFT,
        ENTITY_SPATIAL_RIGHT,
        ENTITY_CO_OCCURRENCE,
        ENTITY_PHOTO_SEARCH,
        ENTITY_FILES,
        ENTITY_DESCRIPTION,
        ENTITY_ACTIVITY,
        ENTITY_LIST,
        FILE_SCOPED,
        DOCUMENT,
        HYBRID
    }

    public QueryRoute classify(String question) {
        if (NEIGHBOR_PATTERN.matcher(question).matches()) {
            return QueryRoute.ENTITY_NEIGHBOR;
        }
        if (REFERENCE_PATTERN.matcher(question).matches()) {
            return QueryRoute.ENTITY_DESCRIPTION;
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
        if ((PHOTO_SEARCH_PATTERN.matcher(question).matches()
                || PHOTO_RELATION_SEARCH_PATTERN.matcher(question).matches())
                && graphQueryService.findEntityNameInQuestion(question).isPresent()) {
            return QueryRoute.ENTITY_PHOTO_SEARCH;
        }
        if (ENTITY_FILES_PATTERN.matcher(question).matches()
                || SINGULAR_ENTITY_FILE_PATTERN.matcher(question).matches()) {
            return QueryRoute.ENTITY_FILES;
        }
        if (isCombinedDocAndGraphQuestion(question)
                || graphQueryService.countResolvedFileReferences(question) >= 2) {
            return QueryRoute.HYBRID;
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
        if (FILE_REFERENCE_PATTERN.matcher(question).find()
                || graphQueryService.resolveFilePathFromQuestion(question).isPresent()) {
            return QueryRoute.FILE_SCOPED;
        }
        if (graphQueryService.findEntityNameInQuestion(question).isPresent()) {
            return QueryRoute.HYBRID;
        }
        if (GRAPH_RELATED_PATTERN.matcher(question).matches()) {
            return QueryRoute.HYBRID;
        }
        return QueryRoute.DOCUMENT;
    }

    /**
     * Exact photo-list queries are answered from the graph.  The test is
     * intentionally based on resolved entities and remaining vocabulary, not
     * on a hard-coded list of person names.
     */
    public boolean isExactPhotoListQuestion(String question, QueryRoute route) {
        if (question == null || question.isBlank()) return false;
        if (route != QueryRoute.HYBRID
                && route != QueryRoute.ENTITY_FILES
                && route != QueryRoute.ENTITY_LIST
                && route != QueryRoute.ENTITY_PHOTO_SEARCH) {
            return false;
        }
        List<String> entities = graphQueryService.findAllEntityNamesInQuestion(question);
        log.debug("PHOTO LIST CHECK: route={}, entities={}, question={}", route, entities, question);
        if (entities.isEmpty() || !normalize(question).matches(".*(zdjec|foto|obraz|fotograf|plik).*")) {
            return false;
        }

        String remainder = normalize(question);
        for (String entity : entities) {
            for (String variant : PolishNameMatcher.generateVariants(entity)) {
                remainder = remainder.replaceAll("(?<![a-z0-9])" + Pattern.quote(normalize(variant)
                        .replace(" ", "")) + "(?![a-z0-9])", " ");
            }
        }

        log.debug("PHOTO LIST CANDIDATE: entities={}, remainder={}", entities, remainder);

        Set<String> boilerplate = new HashSet<>(Set.of(
                "zdjec", "zdjecia", "zdjecie", "zdjeciach", "zdjeciu", "foto", "fotografie",
                "fotografia", "obraz", "obrazy", "plik", "pliki", "daj", "podaj", "pokaz",
                "znajdz", "wyszukaj", "szukaj", "odnajdz", "wyswietl", "wszystkie", "wszystkich",
                "na", "ktorych", "ktorym", "ktore", "wystepuje", "wystepuja", "znajduje", "sie",
                "osoba", "osoby", "i", "oraz", "z", "ze", "w", "kto", "jest", "gdzie"
        ));
        for (String token : remainder.split("[^a-z0-9]+")) {
            if (!token.isBlank() && !boilerplate.contains(token)) {
                return false;
            }
        }
        return true;
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replace("ą", "a").replace("ć", "c").replace("ę", "e")
                .replace("ł", "l").replace("ń", "n").replace("ó", "o")
                .replace("ś", "s").replace("ź", "z").replace("ż", "z");
    }
}

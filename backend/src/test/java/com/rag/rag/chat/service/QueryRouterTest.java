package com.rag.rag.chat.service;

import com.rag.rag.knowledge.graph.GraphQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueryRouterTest {

    @Mock
    private GraphQueryService graphQueryService;

    private QueryRouter queryRouter;

    @BeforeEach
    void setUp() {
        queryRouter = new QueryRouter(graphQueryService);
        lenient().when(graphQueryService.findEntityNameInQuestion(any())).thenReturn(Optional.empty());
        lenient().when(graphQueryService.resolveFilePathFromQuestion(any())).thenReturn(Optional.empty());
        lenient().when(graphQueryService.countResolvedFileReferences(any())).thenReturn(0);
    }

    @ParameterizedTest
    @CsvSource({
            "kto siedzi obok Igora?, ENTITY_NEIGHBOR",
            "kto siedzi obok Bartka?, ENTITY_NEIGHBOR",
            "kto stoi przy Bartku?, ENTITY_NEIGHBOR",
            "kto jest po lewej od Pati?, ENTITY_SPATIAL_LEFT",
            "kto jest z lewej Igora?, ENTITY_SPATIAL_LEFT",
            "kto na lewo od Bartka?, ENTITY_SPATIAL_LEFT",
            "kto jest po prawej od Igora?, ENTITY_SPATIAL_RIGHT",
            "kto jest z prawej Bartka?, ENTITY_SPATIAL_RIGHT",
            "kto na prawo od Pati?, ENTITY_SPATIAL_RIGHT",
            "z kim Bartek jest na zdjęciach?, ENTITY_CO_OCCURRENCE",
            "z kim Bartek znajduje się na zdjęciach?, ENTITY_CO_OCCURRENCE",
            "jak mają na imie osoby z którymi Bartek jest na zdjęciach?, ENTITY_CO_OCCURRENCE",
            "kto jeszcze jest na zdjęciu z Igorem?, ENTITY_CO_OCCURRENCE",
            "w towarzystwie kogo jest Olek?, ENTITY_CO_OCCURRENCE",
            "razem z kim występuje Pati?, ENTITY_CO_OCCURRENCE",
            "obok kogo jest Bartek na zdjęciach?, ENTITY_CO_OCCURRENCE",
            "na których zdjęciach jest Igor?, ENTITY_FILES",
            "gdzie się pojawia Bartek?, ENTITY_FILES",
            "w jakich dokumentach jest Pati?, ENTITY_FILES",
            "na ilu zdjęciach jest Olek?, ENTITY_FILES",
            "co to za kobieta?, ENTITY_DESCRIPTION",
            "kim jest Olek?, ENTITY_DESCRIPTION",
            "opowiedz mi o Igorze, ENTITY_DESCRIPTION",
            "scharakteryzuj postać Bartka, ENTITY_DESCRIPTION",
            "ta dziewczyna po lewej, ENTITY_DESCRIPTION",
            "co robi Igor?, ENTITY_ACTIVITY",
            "czym się zajmował Bartek?, ENTITY_ACTIVITY",
            "co porabia Pati?, ENTITY_ACTIVITY",
            "kto jest na zdjęciach?, ENTITY_LIST",
            "wymień osoby na zdjęciu, ENTITY_LIST",
            "ilu ludzi widać na foto?, ENTITY_LIST",
            "jakie osoby występują na zdjęciach?, ENTITY_LIST",
            "@received_20230526.jpg kto na nim jest?, FILE_SCOPED",
            "@router.txt jakie ma IP?, FILE_SCOPED"
    })
    void shouldClassifyExpandedPhrases(String question, QueryRouter.QueryRoute expectedRoute) {
        assertEquals(expectedRoute, queryRouter.classify(question));
    }

    @Test
    void shouldClassifyKnownEntityQuestionAsHybrid() {
        when(graphQueryService.findEntityNameInQuestion("gdzie był Pati?"))
                .thenReturn(Optional.of("Pati"));

        assertEquals(
                QueryRouter.QueryRoute.HYBRID,
                queryRouter.classify("gdzie był Pati?")
        );
    }

    @Test
    void shouldClassifySingularPhotoQuestionAsEntityFiles() {
        assertEquals(QueryRouter.QueryRoute.ENTITY_FILES,
                queryRouter.classify("na ktorym zdjeciu jest osoba A"));
    }

    @Test
    void shouldClassifyGraphRelatedQuestionAsHybrid() {
        assertEquals(
                QueryRouter.QueryRoute.HYBRID,
                queryRouter.classify("rozpoznaj twarze na zdjęciu")
        );
    }

    @Test
    void shouldClassifyPhotoSearchQuestion() {
        when(graphQueryService.findEntityNameInQuestion("daj mi zdjęcie Igora w słuchawkach"))
                .thenReturn(Optional.of("Igor"));

        assertEquals(
                QueryRouter.QueryRoute.ENTITY_PHOTO_SEARCH,
                queryRouter.classify("daj mi zdjęcie Igora w słuchawkach")
        );
    }

    @Test
    void shouldClassifyNounPhotoRelationAsPhotoSearch() {
        when(graphQueryService.findEntityNameInQuestion("zdjecie Olka z rajdowcem"))
                .thenReturn(Optional.of("Olek"));

        assertEquals(
                QueryRouter.QueryRoute.ENTITY_PHOTO_SEARCH,
                queryRouter.classify("zdjecie Olka z rajdowcem")
        );
    }

    @Test
    void shouldClassifyUnqualifiedPhotoListQuestion() {
        when(graphQueryService.findEntityNameInQuestion("podaj wszystkie zdjęcia na których występuje Olek"))
                .thenReturn(Optional.of("Olek"));

        assertEquals(
                QueryRouter.QueryRoute.ENTITY_PHOTO_SEARCH,
                queryRouter.classify("podaj wszystkie zdjęcia na których występuje Olek")
        );
    }

    @Test
    void shouldClassifyCombinedDocAndPhotoQuestionAsHybrid() {
        assertEquals(
                QueryRouter.QueryRoute.HYBRID,
                queryRouter.classify(
                        "opisz @dupen i wskaż kto znajduje się na zdjęciu @20230505_132630.jpg"
                )
        );
    }

    @Test
    void shouldClassifyMultipleFileReferencesAsHybrid() {
        when(graphQueryService.countResolvedFileReferences(
                "opisz @dupen i @20230505_132630.jpg"
        )).thenReturn(2);

        assertEquals(
                QueryRouter.QueryRoute.HYBRID,
                queryRouter.classify("opisz @dupen i @20230505_132630.jpg")
        );
    }

    @Test
    void recognizesAnUnqualifiedPhotoListWithoutACommandVerb() {
        when(graphQueryService.findAllEntityNamesInQuestion("Zdjęcia Igora"))
                .thenReturn(java.util.List.of("Igor"));
        when(graphQueryService.findEntityNameInQuestion("Zdjęcia Igora"))
                .thenReturn(Optional.of("Igor"));

        assertEquals(QueryRouter.QueryRoute.HYBRID, queryRouter.classify("Zdjęcia Igora"));
        assertTrue(queryRouter.isExactPhotoListQuestion("Zdjęcia Igora", QueryRouter.QueryRoute.HYBRID));
    }

    @Test
    void keepsAVisualQualifierOutOfTheExactGraphPath() {
        when(graphQueryService.findAllEntityNamesInQuestion("Zdjęcia Igora w słuchawkach"))
                .thenReturn(java.util.List.of("Igor"));

        assertFalse(queryRouter.isExactPhotoListQuestion(
                "Zdjęcia Igora w słuchawkach", QueryRouter.QueryRoute.HYBRID));
    }
}

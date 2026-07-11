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
    void shouldClassifyGraphRelatedQuestionAsHybrid() {
        assertEquals(
                QueryRouter.QueryRoute.HYBRID,
                queryRouter.classify("rozpoznaj twarze na zdjęciu")
        );
    }
}

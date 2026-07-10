package com.rag.rag.chat.service;

import com.rag.rag.knowledge.graph.GraphQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    }

    @Test
    void shouldClassifyNeighborQuestion() {
        assertEquals(
                QueryRouter.QueryRoute.ENTITY_NEIGHBOR,
                queryRouter.classify("kto siedzi obok Igora?")
        );
    }

    @Test
    void shouldClassifySpatialLeftQuestion() {
        assertEquals(
                QueryRouter.QueryRoute.ENTITY_SPATIAL_LEFT,
                queryRouter.classify("kto jest po lewej od Pati?")
        );
    }

    @Test
    void shouldClassifySpatialRightQuestion() {
        assertEquals(
                QueryRouter.QueryRoute.ENTITY_SPATIAL_RIGHT,
                queryRouter.classify("kto jest po prawej od Igora?")
        );
    }

    @Test
    void shouldClassifyFileListQuestion() {
        assertEquals(
                QueryRouter.QueryRoute.ENTITY_FILES,
                queryRouter.classify("na których zdjęciach jest Igor?")
        );
    }

    @Test
    void shouldClassifyReferenceQuestion() {
        assertEquals(
                QueryRouter.QueryRoute.ENTITY_DESCRIPTION,
                queryRouter.classify("co to za kobieta?")
        );
    }

    @Test
    void shouldClassifyNamedActivityQuestion() {
        assertEquals(
                QueryRouter.QueryRoute.ENTITY_ACTIVITY,
                queryRouter.classify("co robi Igor?")
        );
    }

    @Test
    void shouldClassifyNamedIdentityQuestion() {
        assertEquals(
                QueryRouter.QueryRoute.ENTITY_DESCRIPTION,
                queryRouter.classify("kim jest Olek?")
        );
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
}

package com.rag.rag.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.rag.core.config.ObservedChatLanguageModel;
import com.rag.rag.knowledge.graph.GraphQueryService;
import com.rag.rag.knowledge.graph.GraphEvidenceItem;
import com.rag.rag.knowledge.graph.GraphEvidenceResult;
import com.rag.rag.knowledge.graph.GraphPhotoEvidence;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.model.openai.OpenAiChatModel;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Opt-in release gate against the real chat provider. Run with:
 * mvn -P live-llm-quality verify
 */
@Tag("live-llm")
class GraphRagLiveQualityGateIT {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private SimpleMeterRegistry liveMetrics;

    @AfterEach
    void printLiveMetrics() {
        if (liveMetrics == null) {
            return;
        }
        for (Timer timer : liveMetrics.find(ObservedChatLanguageModel.METRIC_NAME).timers()) {
            System.out.printf(
                    "LIVE_LLM_METRIC role=%s model=%s outcome=%s calls=%d total_ms=%.1f mean_ms=%.1f max_ms=%.1f%n",
                    timer.getId().getTag("role"),
                    timer.getId().getTag("model"),
                    timer.getId().getTag("outcome"),
                    timer.count(),
                    timer.totalTime(TimeUnit.MILLISECONDS),
                    timer.mean(TimeUnit.MILLISECONDS),
                    timer.max(TimeUnit.MILLISECONDS));
        }
    }

    @Test
    void livePlannerAndFreeformGraphAnswersMeetTheProductContract() throws Exception {
        String apiKey = System.getenv("DEEPINFRA_API_KEY");
        assertFalse(apiKey == null || apiKey.isBlank(),
                "DEEPINFRA_API_KEY is required for the live LLM quality gate");

        Models models = models(apiKey);
        ChatLanguageModel controlModel = models.structuredControl();
        ChatLanguageModel answerModel = models.answer();

        assertRouting(controlModel);
        assertSourceAttribution(models.attribution());

        assertAnswerPair(answerModel, models.attribution(), new AnswerScenario(
                "Co robi Igor i jak jest ubrany?",
                """
                        === Zdjęcie 1 ===
                        [E1.1] Uczestnicy: Igor, Anna.
                        [E1.2] Igor siedzi przy drewnianym stole.
                        [E1.3] Igor ma na sobie czerwoną koszulę z długim rękawem.
                        [E1.4] Anna stoi obok Igora.
                        [E1.5] Scena: jasna kuchnia w ciągu dnia.
                        """));
        assertAnswerPair(answerModel, models.attribution(), new AnswerScenario(
                "Czy Igor i Anna są razem na zdjęciu?",
                """
                        === Zdjęcie 1 ===
                        [E1.1] Uczestnicy: Igor, Anna.
                        [E1.2] Igor stoi po lewej stronie Anny.
                        [E1.3] Anna trzyma kubek i rozmawia z Igorem.
                        [E1.4] Scena: oboje znajdują się w tym samym salonie.
                        """));
    }

    @Test
    void liveGroundedRegenerationIsAttributable() {
        String apiKey = System.getenv("DEEPINFRA_API_KEY");
        assertFalse(apiKey == null || apiKey.isBlank(),
                "DEEPINFRA_API_KEY is required for the live LLM quality gate");
        Models models = models(apiKey);
        int repetitions = envInt("LLM_STABILITY_RUNS", 3);
        for (int run = 1; run <= repetitions; run++) {
            assertGroundedRegeneration(models.answer(), models.attribution(), run);
        }
    }

    private void assertSourceAttribution(ChatLanguageModel controlModel) {
        Tokenizer tokenizer = mock(Tokenizer.class);
        when(tokenizer.estimateTokenCountInText(anyString()))
                .thenAnswer(call -> Math.max(1, call.<String>getArgument(0).length() / 4));
        GraphSourceAttributionService attributionService =
                new GraphSourceAttributionService(controlModel, tokenizer);
        ReflectionTestUtils.setField(attributionService, "maxInputTokens", 8192);

        GraphPhotoEvidence bike = new GraphPhotoEvidence("1", "dir://bike.jpg", List.of(
                new GraphEvidenceItem("E1.1", GraphEvidenceItem.Kind.FACT,
                        "Igor stoi na zewnątrz przy wejściu do budynku i trzyma rower.", "dir://bike.jpg")));
        GraphPhotoEvidence gym = new GraphPhotoEvidence("2", "dir://gym.jpg", List.of(
                new GraphEvidenceItem("E2.1", GraphEvidenceItem.Kind.FACT,
                        "Igor stoi w pomieszczeniu z szafkami na siłowni, nagi do pasa, w szarych spodniach.",
                        "dir://gym.jpg")));
        List<GraphPhotoEvidence> photos = List.of(bike, gym);
        GraphEvidenceResult evidence = new GraphEvidenceResult(
                bike.render() + "\n" + gym.render(),
                List.of("dir://bike.jpg", "dir://gym.jpg"), List.of(), photos);

        GraphSourceAttributionService.Attribution attribution = attributionService.attribute(
                "Jakie są zdjęcia Igora?",
                "Na pierwszym zdjęciu Igor stoi na zewnątrz przy wejściu do budynku i trzyma rower. "
                        + "Na drugim stoi na siłowni w pomieszczeniu z szafkami, jest nagi do pasa "
                        + "i ma szare spodnie.",
                evidence);

        assertTrue(attribution.reliable(), "Live source attribution must return valid structured output");
        assertEquals(List.of("dir://bike.jpg", "dir://gym.jpg"), attribution.paths());
    }

    private void assertRouting(ChatLanguageModel controlModel) {
        GraphQueryService graph = mock(GraphQueryService.class);
        when(graph.availableEntityNames()).thenReturn(List.of("Igor", "Anna"));
        when(graph.validateEntityNames(anyList())).thenAnswer(call -> List.copyOf(call.getArgument(0)));
        when(graph.validateFilePaths(anyList())).thenAnswer(call -> List.copyOf(call.getArgument(0)));
        when(graph.resolveEntityNamesFromText(anyString())).thenReturn(List.of());
        QueryPlanner planner = new QueryPlanner(graph, controlModel);

        assertEquals(QueryPlan.RetrievalMode.GRAPH,
                planner.plan("Na których zdjęciach jest Igor?", "").retrievalMode());
        assertEquals(QueryPlan.RetrievalMode.GRAPH,
                planner.plan("Które osoby pojawiają się w mojej bibliotece?", "").retrievalMode());
        assertEquals(QueryPlan.RetrievalMode.HYBRID,
                planner.plan("Jakie przedmioty znajdują się w kuchni?", "").retrievalMode());
        QueryPlan.RetrievalMode animalMode =
                planner.plan("Co robi pies na zdjęciu?", "").retrievalMode();
        assertTrue(animalMode == QueryPlan.RetrievalMode.HYBRID
                        || animalMode == QueryPlan.RetrievalMode.VISUAL_VALIDATION,
                "Animal activity may use visual validation or non-person hybrid, but never GRAPH");
    }

    private void assertGroundedRegeneration(ChatLanguageModel answerModel,
                                            ChatLanguageModel attributionModel,
                                            int run) {
        Tokenizer tokenizer = mock(Tokenizer.class);
        when(tokenizer.estimateTokenCountInText(anyString()))
                .thenAnswer(call -> Math.max(1, call.<String>getArgument(0).length() / 4));
        GraphSourceAttributionService attributionService =
                new GraphSourceAttributionService(attributionModel, tokenizer);
        ReflectionTestUtils.setField(attributionService, "maxInputTokens", 8192);
        GraphGroundedAnswerRegenerationService regenerationService =
                new GraphGroundedAnswerRegenerationService(answerModel);

        GraphPhotoEvidence gym = new GraphPhotoEvidence("1", "dir://olek-gym.jpg", List.of(
                new GraphEvidenceItem("E1.1", GraphEvidenceItem.Kind.FACT,
                        "Olek stoi na siłowni w ciemnej koszulce z napisem MUSTANG.",
                        "dir://olek-gym.jpg")));
        GraphPhotoEvidence group = new GraphPhotoEvidence("2", "dir://olek-group.jpg", List.of(
                new GraphEvidenceItem("E2.1", GraphEvidenceItem.Kind.FACT,
                        "Olek stoi na trawie w grupie osób, w szarym płaszczu i czarnych spodniach.",
                        "dir://olek-group.jpg")));
        List<GraphPhotoEvidence> photos = List.of(gym, group);
        GraphEvidenceResult evidence = new GraphEvidenceResult(
                gym.render() + "\n" + group.render(),
                List.of(gym.sourcePath(), group.sourcePath()), List.of(), photos);

        String regenerated = regenerationService.regenerate(
                "A jakie są zdjęcia Olka?", evidence.context());
        GraphSourceAttributionService.Attribution attribution = attributionService.attribute(
                "A jakie są zdjęcia Olka?", regenerated, evidence);

        assertFalse(regenerated.isBlank());
        assertFalse(ChatAnswerGrounding.hasInvalidAnswerStructure(regenerated),
                () -> "Regenerated answer has invalid structure in run " + run + ": " + regenerated);
        assertTrue(attribution.reliable(),
                () -> "Regenerated answer was not attributable in run " + run + ": " + regenerated);
        assertFalse(attribution.paths().isEmpty());
    }

    private void assertAnswerPair(ChatLanguageModel answerModel, ChatLanguageModel judgeModel,
                                  AnswerScenario scenario) throws Exception {
        String first = generateAnswer(answerModel, scenario);
        String second = generateAnswer(answerModel, scenario);
        assertFalse(first.isBlank());
        assertFalse(second.isBlank());

        String judgement = judgeModel.generate("""
                Oceń dwie odpowiedzi asystenta wyłącznie względem podanych dowodów i pytania.
                Zwróć tylko JSON:
                {"grounded":true,"naturalPolish":true,"sameMeaning":true,"brief":true,"noTechnicalReferences":true}
                grounded=true tylko jeśli każda konkretna informacja w obu odpowiedziach wynika z dowodów.
                naturalPolish=true tylko dla naturalnej, poprawnej polszczyzny.
                sameMeaning=true jeśli obie odpowiedzi przekazują zgodny sens mimo możliwie innego brzmienia.
                brief=true jeśli każda odpowiedź ma najwyżej trzy krótkie zdania.
                noTechnicalReferences=true jeśli nie ma nazw plików, ścieżek ani identyfikatorów E1.1/A.1.

                Pytanie: %s
                Dowody:
                %s
                Odpowiedź A: %s
                Odpowiedź B: %s
                """.formatted(scenario.question(), scenario.evidence(), first, second));
        JsonNode root = objectMapper.readTree(extractJson(judgement));
        for (String field : List.of("grounded", "naturalPolish", "sameMeaning", "brief", "noTechnicalReferences")) {
            assertTrue(root.path(field).asBoolean(false),
                    () -> "Live quality gate failed for " + field + "; judgement=" + judgement
                            + "; answers=[" + first + "] / [" + second + "]");
        }
    }

    private String generateAnswer(ChatLanguageModel answerModel, AnswerScenario scenario) {
        var response = answerModel.generate(
                SystemMessage.from(ChatService.ANSWER_INSTRUCTIONS),
                UserMessage.from("""
                        Odpowiedz na pytanie na podstawie całego poniższego grafu.
                        Nie wymieniaj identyfikatorów dowodów.

                        Graf:
                        %s

                        Pytanie użytkownika: %s
                        """.formatted(scenario.evidence(), scenario.question())));
        return response == null || response.content() == null ? "" : response.content().text().trim();
    }

    private ChatLanguageModel model(String baseUrl, String apiKey, String modelName,
                                    double temperature, int maxTokens, boolean structuredJson) {
        var builder = OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .timeout(Duration.ofSeconds(90));
        if (structuredJson) {
            builder.responseFormat("json_object");
        }
        return builder.build();
    }

    private Models models(String apiKey) {
        String baseUrl = env("DEEPINFRA_BASE_URL", "https://api.deepinfra.com/v1/openai");
        String legacyModel = env("DEEPINFRA_CHAT_MODEL", "deepseek-ai/DeepSeek-V3");
        String controlName = env("LLM_CONTROL_MODEL", legacyModel);
        String answerName = env("LLM_ANSWER_MODEL", legacyModel);
        String attributionName = env("LLM_ATTRIBUTION_MODEL", answerName);
        int controlMaxTokens = envInt("LLM_CONTROL_MAX_TOKENS", 512);
        int answerMaxTokens = envInt("LLM_ANSWER_MAX_TOKENS", 384);
        int attributionMaxTokens = envInt("LLM_ATTRIBUTION_MAX_TOKENS", 512);
        double answerTemperature = envDouble("LLM_ANSWER_TEMPERATURE", 0.0);
        liveMetrics = new SimpleMeterRegistry();
        ChatLanguageModel control = new ObservedChatLanguageModel(
                model(baseUrl, apiKey, controlName, 0.0, controlMaxTokens, true),
                liveMetrics, "deepinfra", "control_json", controlName);
        ChatLanguageModel answer = new ObservedChatLanguageModel(
                model(baseUrl, apiKey, answerName, answerTemperature, answerMaxTokens, false),
                liveMetrics, "deepinfra", "answer", answerName);
        ChatLanguageModel attribution = new ObservedChatLanguageModel(
                model(baseUrl, apiKey, attributionName, 0.0, attributionMaxTokens, true),
                liveMetrics, "deepinfra", "attribution", attributionName);
        return new Models(control, answer, attribution);
    }

    private static String extractJson(String value) {
        if (value == null) return "{}";
        int start = value.indexOf('{');
        int end = value.lastIndexOf('}');
        return start >= 0 && end > start ? value.substring(start, end + 1) : value;
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static int envInt(String name, int fallback) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static double envDouble(String name, double fallback) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private record Models(ChatLanguageModel structuredControl,
                          ChatLanguageModel answer,
                          ChatLanguageModel attribution) {
    }

    private record AnswerScenario(String question, String evidence) {
    }
}

package com.rag.rag.Service;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

@Component
public class ToolsService {

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;

    public ToolsService(EmbeddingStore<TextSegment> embeddingStore, EmbeddingModel embeddingModel) {
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
    }

    @Tool("Szuka informacji w bazie wiedzy. Używaj tego ZAWSZE, gdy potrzebujesz faktów. " +
          "Jeśli 'pliki' są puste lub równe 'ALL', przeszukuje CAŁĄ bazę danych. " +
          "Jeśli 'pliki' NIE są puste, przeszukuje wskazane pliki (można podać kilka oddzielając je przecinkiem).")
    public String findRelevantInformation(
            @P("Pytanie/zapytanie semantyczne do bazy wiedzy") String zapytanie,
            @P("Lista plików/folderów (oddzielona przecinkami) lub 'ALL'.") String pliki
    ) {

        var queryEmbedding = embeddingModel.embed(zapytanie).content();

        EmbeddingSearchRequest.EmbeddingSearchRequestBuilder requestBuilder = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(3)
                .minScore(0.7);

        if (pliki != null && !pliki.isEmpty() && !pliki.equalsIgnoreCase("ALL")) {
            List<String> cleanPaths = Arrays.stream(pliki.split(","))
                    .map(p -> p.replace("@", "").trim())
                    .filter(p -> !p.isEmpty())
                    .collect(Collectors.toList());

            if (!cleanPaths.isEmpty()) {
                Filter pathFilter = metadataKey("path").isIn(cleanPaths);
                Filter filenameFilter = metadataKey("filename").isIn(cleanPaths);
                requestBuilder.filter(pathFilter.or(filenameFilter));
            }
        }

        EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(requestBuilder.build());

        if (searchResult.matches().isEmpty()) {
            return "Nie znaleziono żadnych informacji pasujących do zapytania.";
        }

        StringBuilder results = new StringBuilder();
        results.append("Znalezione fragmenty:\n\n");
        searchResult.matches().forEach(match -> {
            results.append("--- [ŹRÓDŁO: ").append(match.embedded().metadata().getString("path")).append("] ---\n");
            results.append(match.embedded().text()).append("\n\n");
        });

        return results.toString();
    }
}
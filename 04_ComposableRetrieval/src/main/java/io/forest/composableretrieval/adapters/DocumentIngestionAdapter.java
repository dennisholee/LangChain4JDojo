package io.forest.composableretrieval.adapters;

import io.forest.composableretrieval.core.port.DocumentIngestionPort;
import io.forest.composableretrieval.core.port.TokenEmbeddingPort;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingStore;

import java.util.Map;

/**
 * Adapter — implements {@link DocumentIngestionPort} by embedding text with a LangChain4J
 * {@link EmbeddingModel} and persisting it in an {@link EmbeddingStore}.
 */
public class DocumentIngestionAdapter implements DocumentIngestionPort {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> store;
    private final TokenEmbeddingPort tokenEmbeddingPort;
    private final DocumentTokenVectorStore tokenVectorStore;

    public DocumentIngestionAdapter(EmbeddingModel embeddingModel, EmbeddingStore<TextSegment> store) {
        this(
                embeddingModel,
                store,
                new LangChainTokenEmbeddingAdapter(embeddingModel),
                DocumentTokenVectorStore.defaultStore()
        );
    }

    public DocumentIngestionAdapter(
            EmbeddingModel embeddingModel,
            EmbeddingStore<TextSegment> store,
            TokenEmbeddingPort tokenEmbeddingPort,
            DocumentTokenVectorStore tokenVectorStore) {
        this.embeddingModel = embeddingModel;
        this.store = store;
        this.tokenEmbeddingPort = tokenEmbeddingPort;
        this.tokenVectorStore = tokenVectorStore;
    }

    @Override
    public void ingest(String id, String text) throws Exception {
        Response<Embedding> response = embeddingModel.embed(text);
        // Store text segment with custom id in metadata so retrieval returns non-null embedded()
        TextSegment segment = TextSegment.from(text, new Metadata(Map.of("id", id)));
        store.add(response.content(), segment);

        // Precompute and persist token vectors for ColBERT-inspired reranking.
        String cacheKey = tokenVectorStore.cacheKey(id, text);
        tokenVectorStore.put(
                cacheKey,
                tokenEmbeddingPort.embedTokenUnits(ColbertTokenizationUtils.buildTokenUnits(text))
        );
    }
}

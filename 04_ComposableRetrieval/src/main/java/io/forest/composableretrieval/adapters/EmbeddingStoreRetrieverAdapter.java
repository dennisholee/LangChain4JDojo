package io.forest.composableretrieval.adapters;

import io.forest.composableretrieval.core.port.RetrieverPort;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingStore;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter — bridges LangChain4J {@link EmbeddingStoreContentRetriever} to our clean-architecture
 * {@link RetrieverPort} port, and also exposes itself as a {@link ContentRetriever} so it can be
 * passed directly to {@link dev.langchain4j.rag.DefaultRetrievalAugmentor}.
 *
 * <p>This is the primary outbound adapter for semantic search: it embeds the query with the
 * provided {@link EmbeddingModel}, searches the {@link EmbeddingStore}, and maps LangChain4J
 * {@link Content} objects back to our {@link RetrieverPort.RetrievalResult} records.
 */
public class EmbeddingStoreRetrieverAdapter implements RetrieverPort, ContentRetriever {

    private final EmbeddingStoreContentRetriever delegate;

    public EmbeddingStoreRetrieverAdapter(
            EmbeddingStore<TextSegment> store,
            EmbeddingModel embeddingModel,
            int maxResults) {

        this.delegate = EmbeddingStoreContentRetriever.builder()
            .embeddingStore(store)
            .embeddingModel(embeddingModel)
            .maxResults(maxResults)
            .minScore(0.0)
            .build();
    }

    // ─────────────────────── RetrieverPort (domain side) ────────────────────────

    @Override
    public List<RetrievalResult> retrieve(String query, int topK) {
        List<Content> contents = delegate.retrieve(Query.from(query));
        List<RetrievalResult> results = new ArrayList<>();
        for (int i = 0; i < Math.min(topK, contents.size()); i++) {
            Content c = contents.get(i);
            if (c.textSegment() == null) continue;
            String text = c.textSegment().text();
            // Rank-based score: rank 0 → 1.0, rank 1 → 0.5, rank k → 1/(1+k)
            results.add(new RetrievalResult(text, text, 1.0 / (1.0 + i)));
        }
        return results;
    }

    // ─────────────────────── ContentRetriever (LangChain4J side) ────────────────

    @Override
    public List<Content> retrieve(Query query) {
        return delegate.retrieve(query);
    }
}

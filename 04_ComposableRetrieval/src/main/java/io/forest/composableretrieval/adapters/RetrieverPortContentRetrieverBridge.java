package io.forest.composableretrieval.adapters;

import io.forest.composableretrieval.core.port.RetrieverPort;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;

import java.util.List;
import java.util.Map;

/**
 * Adapter bridge — wraps any {@link RetrieverPort} so it can be used as a LangChain4J
 * {@link ContentRetriever} inside {@link dev.langchain4j.rag.DefaultRetrievalAugmentor}.
 *
 * <p>This lets the domain-layer {@link io.forest.composableretrieval.core.ComposableRetriever} (which composes
 * multiple {@link RetrieverPort}s) participate in the LangChain4J RAG pipeline without any
 * direct framework dependency inside the core layer.
 */
public class RetrieverPortContentRetrieverBridge implements ContentRetriever {

    private final RetrieverPort port;
    private final int topK;

    public RetrieverPortContentRetrieverBridge(RetrieverPort port, int topK) {
        this.port = port;
        this.topK = topK;
    }

    @Override
    public List<Content> retrieve(Query query) {
        try {
            return port.retrieve(query.text(), topK).stream()
                .map(r -> Content.from(
                    TextSegment.from(r.text(), new Metadata(Map.of("id", r.id())))))
                .toList();
        } catch (Exception e) {
            throw new RuntimeException("RetrieverPort.retrieve failed", e);
        }
    }
}

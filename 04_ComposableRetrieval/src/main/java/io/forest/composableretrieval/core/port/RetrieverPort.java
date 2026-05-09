package io.forest.composableretrieval.core.port;

import java.util.List;

/**
 * Inbound port — retrieve relevant document snippets for a natural-language query.
 * Implementations are free to use any backing store; callers depend only on this interface.
 */
public interface RetrieverPort {

    List<RetrievalResult> retrieve(String query, int topK) throws Exception;

    /** Immutable result carrying the document id, its text content, and a relevance score. */
    record RetrievalResult(String id, String text, double score) {}
}

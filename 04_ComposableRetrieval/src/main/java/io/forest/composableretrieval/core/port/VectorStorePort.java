package io.forest.composableretrieval.core.port;

import java.util.List;

public interface VectorStorePort {
    /**
     * Index a document (id + text + optional metadata) with its vector.
     */
    void upsert(String id, String text, double[] vector) throws Exception;

    /**
     * Find top-k nearest document ids and scores for a query vector.
     */
    List<SearchResult> search(double[] queryVector, int topK) throws Exception;

    class SearchResult {
        public final String id;
        public final double score;

        public SearchResult(String id, double score) {
            this.id = id;
            this.score = score;
        }
    }
}

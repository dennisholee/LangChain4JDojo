package io.forest.composableretrieval.core.port;

import java.util.List;

/**
 * Outbound port — re-rank retrieval results based on multiple scoring strategies.
 * Implementations may apply semantic re-scoring, diversity penalties, LLM judging, or other strategies.
 */
public interface RerankerPort {

    /**
     * Re-rank a list of retrieval results based on the query.
     *
     * @param query the search query (used for semantic and LLM-based re-ranking)
     * @param results the retrieval results from composable retriever
     * @return re-ranked results (may have updated scores, reordered, or filtered)
     * @throws Exception if re-ranking fails (e.g., embedding model, LLM service unavailable)
     */
    List<RetrieverPort.RetrievalResult> rerank(String query, List<RetrieverPort.RetrievalResult> results) throws Exception;
}

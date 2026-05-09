package io.forest.composableretrieval.core;

import io.forest.composableretrieval.core.port.RetrieverPort;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ComposableRetriever}.
 * Uses lambda stubs in place of real adapters — no I/O, no LangChain4J runtime needed.
 */
class ComposableRetrieverTest {

    @Test
    void mergesResultsFromMultipleRetrievers() throws Exception {
        RetrieverPort r1 = (q, k) -> List.of(new RetrieverPort.RetrievalResult("id1", "text from r1", 0.9));
        RetrieverPort r2 = (q, k) -> List.of(new RetrieverPort.RetrievalResult("id2", "text from r2", 0.7));

        ComposableRetriever retriever = new ComposableRetriever(List.of(r1, r2));
        List<RetrieverPort.RetrievalResult> results = retriever.retrieve("any query", 5);

        assertEquals(2, results.size(), "Should have one result per retriever");
        assertEquals("id1", results.get(0).id(), "Highest-score result should be first");
    }

    @Test
    void deduplicatesResultsByIdKeepingHighestScore() throws Exception {
        RetrieverPort r1 = (q, k) -> List.of(new RetrieverPort.RetrievalResult("shared", "from r1", 0.8));
        RetrieverPort r2 = (q, k) -> List.of(new RetrieverPort.RetrievalResult("shared", "from r2", 0.95));

        ComposableRetriever retriever = new ComposableRetriever(List.of(r1, r2));
        List<RetrieverPort.RetrievalResult> results = retriever.retrieve("q", 5);

        assertEquals(1, results.size(), "Duplicate id should be deduplicated");
        assertEquals(0.95, results.get(0).score(), 1e-9, "Higher score wins deduplication");
    }

    @Test
    void respectsTopKLimit() throws Exception {
        RetrieverPort r1 = (q, k) -> List.of(
            new RetrieverPort.RetrievalResult("a", "a", 0.9),
            new RetrieverPort.RetrievalResult("b", "b", 0.8),
            new RetrieverPort.RetrievalResult("c", "c", 0.7)
        );

        ComposableRetriever retriever = new ComposableRetriever(List.of(r1));
        List<RetrieverPort.RetrievalResult> results = retriever.retrieve("q", 2);

        assertEquals(2, results.size(), "Should honour topK=2");
    }

    @Test
    void rejectsEmptyRetrieverList() {
        assertThrows(IllegalArgumentException.class, () -> new ComposableRetriever(List.of()));
    }
}

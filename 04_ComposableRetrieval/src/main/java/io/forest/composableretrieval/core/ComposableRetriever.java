package io.forest.composableretrieval.core;

import io.forest.composableretrieval.core.port.RetrieverPort;

import java.util.*;

/**
 * Core domain component — composes N {@link RetrieverPort} adapters into a single retriever.
 *
 * <p>This class lives in the <em>core</em> layer and has <strong>no dependency</strong> on
 * LangChain4J or any infrastructure library.  It relies solely on the {@link RetrieverPort}
 * abstraction; the LangChain4J wiring happens in the adapter and application layers.
 *
 * <p>Combination strategy: results from all adapters are merged, duplicates (same id) are
 * resolved by keeping the highest score, and the unified list is sorted descending by score.
 */
public class ComposableRetriever implements RetrieverPort {

    private final List<RetrieverPort> retrievers;

    public ComposableRetriever(List<RetrieverPort> retrievers) {
        if (retrievers == null || retrievers.isEmpty()) {
            throw new IllegalArgumentException("At least one RetrieverPort is required.");
        }
        this.retrievers = List.copyOf(retrievers);
    }

    @Override
    public List<RetrievalResult> retrieve(String query, int topK) throws Exception {
        // Collect from every adapter and merge by id, keeping the max score per document.
        Map<String, RetrievalResult> merged = new LinkedHashMap<>();
        for (RetrieverPort r : retrievers) {
            for (RetrievalResult res : r.retrieve(query, topK)) {
                merged.merge(res.id(), res,
                    (existing, incoming) ->
                        incoming.score() > existing.score() ? incoming : existing);
            }
        }

        return merged.values().stream()
            .sorted(Comparator.comparingDouble(RetrievalResult::score).reversed())
            .limit(topK)
            .toList();
    }
}

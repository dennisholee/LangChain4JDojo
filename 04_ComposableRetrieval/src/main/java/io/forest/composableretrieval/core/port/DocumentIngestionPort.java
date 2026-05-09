package io.forest.composableretrieval.core.port;

/**
 * Outbound port — persist a document so it can later be retrieved.
 * Implementations embed the text and store it in the underlying vector store.
 */
public interface DocumentIngestionPort {

    /**
     * Embed {@code text} and store it under the given {@code id}.
     * Re-ingesting with the same id replaces the previous entry.
     */
    void ingest(String id, String text) throws Exception;
}

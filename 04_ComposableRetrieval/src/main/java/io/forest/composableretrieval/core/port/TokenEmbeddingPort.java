package io.forest.composableretrieval.core.port;

import java.util.List;

/**
 * Abstraction for token-level embedding used by ColBERT-style late interaction.
 */
public interface TokenEmbeddingPort {

    /**
     * Embed token units (unigrams, bigrams, etc.) into vectors.
     */
    List<float[]> embedTokenUnits(List<String> tokenUnits) throws Exception;
}

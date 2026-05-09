package io.forest.composableretrieval.core.port;

import java.util.List;

public interface EmbeddingsPort {
    /**
     * Convert input text into an embedding vector (double[]).
     */
    double[] embed(String text) throws Exception;

    /** Batch version. */
    List<double[]> embed(List<String> texts) throws Exception;
}

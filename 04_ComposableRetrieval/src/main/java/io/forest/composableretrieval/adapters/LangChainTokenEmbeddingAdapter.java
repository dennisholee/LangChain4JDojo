package io.forest.composableretrieval.adapters;

import io.forest.composableretrieval.core.port.TokenEmbeddingPort;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Token embedding adapter backed by a LangChain4J EmbeddingModel.
 */
public class LangChainTokenEmbeddingAdapter implements TokenEmbeddingPort {

    private final EmbeddingModel embeddingModel;

    public LangChainTokenEmbeddingAdapter(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @Override
    public List<float[]> embedTokenUnits(List<String> tokenUnits) throws Exception {
        List<float[]> vectors = new ArrayList<>(tokenUnits.size());
        for (String tokenUnit : tokenUnits) {
            Embedding embedding = embeddingModel.embed(tokenUnit).content();
            vectors.add(embedding.vector());
        }
        return vectors;
    }
}

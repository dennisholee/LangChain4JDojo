package io.forest.composableretrieval.adapters;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared in-memory store for document token vectors used by ColBERT-inspired reranking.
 */
public class DocumentTokenVectorStore {

    private static final DocumentTokenVectorStore DEFAULT = new DocumentTokenVectorStore();

    private final Map<String, List<float[]>> cache = new ConcurrentHashMap<>();

    public static DocumentTokenVectorStore defaultStore() {
        return DEFAULT;
    }

    public String cacheKey(String id, String text) {
        return id + "::" + text.hashCode();
    }

    public List<float[]> get(String key) {
        return cache.get(key);
    }

    public void put(String key, List<float[]> vectors) {
        cache.put(key, vectors);
    }
}

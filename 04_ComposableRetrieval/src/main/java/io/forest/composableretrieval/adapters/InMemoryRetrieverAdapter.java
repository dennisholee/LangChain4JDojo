package io.forest.composableretrieval.adapters;

import io.forest.composableretrieval.core.port.RetrieverPort;
import java.util.*;

/**
 * Adapter — implements {@link RetrieverPort} with in-memory keyword-based search.
 *
 * <p>Simple example for demonstrating composable retrieval. In production, replace with
 * a real search engine (Elasticsearch, Solr, etc.).
 */
public class InMemoryRetrieverAdapter implements RetrieverPort {

    private final List<Document> documents;

    public static class Document {
        public final String id;
        public final String text;

        public Document(String id, String text) {
            this.id = id;
            this.text = text;
        }
    }

    public InMemoryRetrieverAdapter(List<Document> documents) {
        this.documents = new ArrayList<>(documents);
    }

    @Override
    public List<RetrievalResult> retrieve(String query, int topK) {
        String[] keywords = query.toLowerCase().split("\\s+");

        List<RetrievalResult> results = new ArrayList<>();

        for (Document doc : documents) {
            String lowerText = doc.text.toLowerCase();
            int matchCount = 0;

            // Count keyword matches (simple TF-like scoring)
            for (String keyword : keywords) {
                if (lowerText.contains(keyword)) {
                    matchCount++;
                }
            }

            if (matchCount > 0) {
                // Score: matched keywords / total keywords
                double score = (double) matchCount / keywords.length;
                results.add(new RetrievalResult(doc.id, doc.text, score));
            }
        }

        // Sort by score descending
        results.sort(Comparator.comparingDouble(RetrievalResult::score).reversed());

        return results.stream().limit(topK).toList();
    }
}

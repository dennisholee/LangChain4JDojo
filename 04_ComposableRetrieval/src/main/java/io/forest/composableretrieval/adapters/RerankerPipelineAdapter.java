package io.forest.composableretrieval.adapters;

import io.forest.composableretrieval.core.port.RetrieverPort;
import io.forest.composableretrieval.core.port.RerankerPort;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.data.embedding.Embedding;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Three-stage reranking pipeline:
 * <ol>
 *   <li><b>ColBERT Semantic Re-Scoring:</b> Re-score each result using embedding similarity to query.
 *   <li><b>Diversity Penalty:</b> Penalize results similar to higher-ranked results.
 *   <li><b>LLM-as-a-Judge:</b> Use LLM to perform final relevance ranking.
 * </ol>
 *
 * <p>This adapter bridges the domain port {@link RerankerPort} to LangChain4J models.
 */
public class RerankerPipelineAdapter implements RerankerPort {

    private final EmbeddingModel embeddingModel;
    private final ChatLanguageModel chatModel;

    /** Similarity threshold above which diversity penalty is applied (0.0–1.0). */
    private final double diversitySimilarityThreshold;

    /** Penalty multiplier for diverse results (0.0–1.0; lower = stronger penalty). */
    private final double diversityPenaltyFactor;

    /** Whether to enable LLM-as-a-Judge reranking (final stage). */
    private final boolean enableLlmJudge;

    public RerankerPipelineAdapter(
            EmbeddingModel embeddingModel,
            ChatLanguageModel chatModel) {
        this(embeddingModel, chatModel, 0.8, 0.7, true);
    }

    public RerankerPipelineAdapter(
            EmbeddingModel embeddingModel,
            ChatLanguageModel chatModel,
            double diversitySimilarityThreshold,
            double diversityPenaltyFactor,
            boolean enableLlmJudge) {
        this.embeddingModel = embeddingModel;
        this.chatModel = chatModel;
        this.diversitySimilarityThreshold = diversitySimilarityThreshold;
        this.diversityPenaltyFactor = diversityPenaltyFactor;
        this.enableLlmJudge = enableLlmJudge;
    }

    @Override
    public List<RetrieverPort.RetrievalResult> rerank(String query, List<RetrieverPort.RetrievalResult> results)
            throws Exception {
        if (results.isEmpty()) {
            return results;
        }

        System.out.println("\n[Reranker] Stage 1/3: ColBERT Semantic Re-Scoring…");
        List<RetrieverPort.RetrievalResult> stage1Results = stage1ColBertReScore(query, results);

        System.out.println("[Reranker] Stage 2/3: Diversity Penalty…");
        List<RetrieverPort.RetrievalResult> stage2Results = stage2DiversityPenalty(stage1Results);

        if (enableLlmJudge) {
            System.out.println("[Reranker] Stage 3/3: LLM-as-a-Judge…");
            return stage3LlmJudge(query, stage2Results);
        } else {
            System.out.println("[Reranker] Stage 3/3: LLM-as-a-Judge (skipped)");
            return stage2Results;
        }
    }

    /**
     * Stage 1: Re-score each result using ColBERT semantic similarity (embedding-based).
     * ColBERT uses fine-grained token-level embeddings; we approximate using document embeddings.
     */
    private List<RetrieverPort.RetrievalResult> stage1ColBertReScore(
            String query, List<RetrieverPort.RetrievalResult> results) throws Exception {
        // Embed the query
        Embedding queryEmbedding = embeddingModel.embed(query).content();
        float[] queryVector = queryEmbedding.vector();

        List<RetrieverPort.RetrievalResult> rescored = new ArrayList<>();
        for (RetrieverPort.RetrievalResult result : results) {
            // Embed result text
            Embedding resultEmbedding = embeddingModel.embed(result.text()).content();
            float[] resultVector = resultEmbedding.vector();

            // Compute cosine similarity
            double similarity = cosineSimilarity(queryVector, resultVector);

            // Use embedding similarity as new score (replaces original retriever score)
            // Normalize to [0.0, 1.0] range (cosine similarity is already in [-1, 1], map to [0, 1])
            double newScore = (similarity + 1.0) / 2.0;

            rescored.add(new RetrieverPort.RetrievalResult(result.id(), result.text(), newScore));
        }

        // Sort by new score descending
        rescored.sort(Comparator.comparingDouble(RetrieverPort.RetrievalResult::score).reversed());

        // Log stage 1 results
        for (int i = 0; i < Math.min(3, rescored.size()); i++) {
            RetrieverPort.RetrievalResult r = rescored.get(i);
            System.out.printf("  [%d] %.3f: %s%n", i + 1, r.score(),
                    r.text().substring(0, Math.min(60, r.text().length())) + "…");
        }

        return rescored;
    }

    /**
     * Stage 2: Apply diversity penalty to reduce scores of redundant results.
     * For each result, compute similarity to all higher-ranked results.
     * If similarity > threshold, multiply score by penalty factor.
     */
    private List<RetrieverPort.RetrievalResult> stage2DiversityPenalty(
            List<RetrieverPort.RetrievalResult> results) throws Exception {
        if (results.size() <= 1) {
            return results;
        }

        // Pre-embed all documents for efficiency
        Map<String, float[]> docEmbeddings = new HashMap<>();
        for (RetrieverPort.RetrievalResult result : results) {
            Embedding embedding = embeddingModel.embed(result.text()).content();
            docEmbeddings.put(result.id(), embedding.vector());
        }

        List<RetrieverPort.RetrievalResult> penalized = new ArrayList<>();

        for (int i = 0; i < results.size(); i++) {
            RetrieverPort.RetrievalResult current = results.get(i);
            float[] currentVector = docEmbeddings.get(current.id());
            double score = current.score();

            // Check similarity to higher-ranked results (0..i-1)
            for (int j = 0; j < i; j++) {
                RetrieverPort.RetrievalResult higher = results.get(j);
                float[] higherVector = docEmbeddings.get(higher.id());

                double similarity = cosineSimilarity(currentVector, higherVector);
                // Normalize similarity to [0, 1]
                double normalizedSimilarity = (similarity + 1.0) / 2.0;

                // Apply penalty if similar to higher-ranked result
                if (normalizedSimilarity > diversitySimilarityThreshold) {
                    score *= diversityPenaltyFactor;
                }
            }

            penalized.add(new RetrieverPort.RetrievalResult(current.id(), current.text(), score));
        }

        // Re-sort after penalties
        penalized.sort(Comparator.comparingDouble(RetrieverPort.RetrievalResult::score).reversed());

        // Log stage 2 results
        for (int i = 0; i < Math.min(3, penalized.size()); i++) {
            RetrieverPort.RetrievalResult r = penalized.get(i);
            System.out.printf("  [%d] %.3f: %s%n", i + 1, r.score(),
                    r.text().substring(0, Math.min(60, r.text().length())) + "…");
        }

        return penalized;
    }

    /**
     * Stage 3: Use LLM to rank documents by relevance (LLM-as-a-Judge).
     * Asks the LLM which documents are most relevant to the query.
     */
    private List<RetrieverPort.RetrievalResult> stage3LlmJudge(
            String query, List<RetrieverPort.RetrievalResult> results) throws Exception {
        // Build prompt for LLM to rank results
        StringBuilder prompt = new StringBuilder();
        prompt.append("Rank the following documents by relevance to the query. Return only the document IDs in order, one per line, most relevant first.\n\n");
        prompt.append("Query: ").append(query).append("\n\n");
        prompt.append("Documents:\n");

        for (int i = 0; i < results.size(); i++) {
            RetrieverPort.RetrievalResult r = results.get(i);
            prompt.append(String.format("[%s] %s\n", r.id(), r.text().substring(0, Math.min(150, r.text().length()))));
        }

        prompt.append("\nReturn only the document IDs in order of relevance, one per line:");

        // Call LLM
        String llmResponse = chatModel.generate(prompt.toString());

        // Parse LLM response to extract document IDs in order
        List<String> rankedIds = new ArrayList<>();
        for (String line : llmResponse.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.contains(":")) {
                // Try to extract ID (may be surrounded by brackets or quotes)
                trimmed = trimmed.replaceAll("[\\[\\]'\"]", "").trim();
                if (!trimmed.isEmpty()) {
                    rankedIds.add(trimmed);
                }
            }
        }

        // Create map of results by ID for quick lookup
        Map<String, RetrieverPort.RetrievalResult> resultMap = new HashMap<>();
        for (RetrieverPort.RetrievalResult r : results) {
            resultMap.put(r.id(), r);
        }

        // Rebuild results list in LLM-judged order with scores based on rank position
        List<RetrieverPort.RetrievalResult> judged = new ArrayList<>();
        for (int rank = 0; rank < rankedIds.size(); rank++) {
            String id = rankedIds.get(rank);
            if (resultMap.containsKey(id)) {
                RetrieverPort.RetrievalResult original = resultMap.get(id);
                // Score based on rank: first = 1.0, then 0.9, 0.8, ...
                double judgedScore = Math.max(0.1, 1.0 - (rank * 0.1));
                judged.add(new RetrieverPort.RetrievalResult(original.id(), original.text(), judgedScore));
            }
        }

        // Add any results not ranked by LLM at the end
        for (RetrieverPort.RetrievalResult r : results) {
            if (!rankedIds.contains(r.id())) {
                judged.add(new RetrieverPort.RetrievalResult(r.id(), r.text(), 0.1));
            }
        }

        // Log stage 3 results
        for (int i = 0; i < Math.min(3, judged.size()); i++) {
            RetrieverPort.RetrievalResult r = judged.get(i);
            System.out.printf("  [%d] %.3f: %s%n", i + 1, r.score(),
                    r.text().substring(0, Math.min(60, r.text().length())) + "…");
        }

        return judged;
    }

    /**
     * Compute cosine similarity between two vectors.
     * Returns a value in [-1.0, 1.0]; 1.0 = identical, -1.0 = opposite, 0.0 = orthogonal.
     */
    private static double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("Vector dimensions must match");
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        if (normA == 0.0 || normB == 0.0) {
            return 0.0; // One or both vectors are zero
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}

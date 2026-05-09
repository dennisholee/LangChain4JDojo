package io.forest.composableretrieval.adapters;

import io.forest.composableretrieval.core.port.RetrieverPort;
import io.forest.composableretrieval.core.port.RerankerPort;
import io.forest.composableretrieval.core.port.TokenEmbeddingPort;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.data.embedding.Embedding;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private static final Pattern BULLET_PREFIX = Pattern.compile("^\\s*(?:[-*]|\\d+[.)])\\s*");

    private final EmbeddingModel embeddingModel;
    private final TokenEmbeddingPort tokenEmbeddingPort;
    private final ChatLanguageModel chatModel;
    private final DocumentTokenVectorStore documentTokenVectorStore;

    // Cross-query cache for document token vectors to reduce repeated token embedding work.
    private final Map<String, List<float[]>> documentTokenVectorCache;

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
        this(
                embeddingModel,
                new LangChainTokenEmbeddingAdapter(embeddingModel),
                chatModel,
                diversitySimilarityThreshold,
                diversityPenaltyFactor,
                enableLlmJudge
        );
    }

    public RerankerPipelineAdapter(
            EmbeddingModel embeddingModel,
            TokenEmbeddingPort tokenEmbeddingPort,
            ChatLanguageModel chatModel,
            double diversitySimilarityThreshold,
            double diversityPenaltyFactor,
            boolean enableLlmJudge) {
        this(
                embeddingModel,
                tokenEmbeddingPort,
                chatModel,
                diversitySimilarityThreshold,
                diversityPenaltyFactor,
                enableLlmJudge,
                DocumentTokenVectorStore.defaultStore()
        );
    }

    public RerankerPipelineAdapter(
            EmbeddingModel embeddingModel,
            TokenEmbeddingPort tokenEmbeddingPort,
            ChatLanguageModel chatModel,
            double diversitySimilarityThreshold,
            double diversityPenaltyFactor,
            boolean enableLlmJudge,
            DocumentTokenVectorStore documentTokenVectorStore) {
        this.embeddingModel = embeddingModel;
        this.tokenEmbeddingPort = tokenEmbeddingPort;
        this.chatModel = chatModel;
        this.diversitySimilarityThreshold = diversitySimilarityThreshold;
        this.diversityPenaltyFactor = diversityPenaltyFactor;
        this.enableLlmJudge = enableLlmJudge;
        this.documentTokenVectorStore = documentTokenVectorStore;
        this.documentTokenVectorCache = new ConcurrentHashMap<>();
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
        // Embed the query once for bi-encoder fallback/stability blending.
        Embedding queryEmbedding = embeddingModel.embed(query).content();
        float[] queryVector = queryEmbedding.vector();

        // Build query token vectors (unigrams + bigrams) to approximate ColBERT late interaction.
        List<float[]> queryTokenVectors = buildTokenVectors(query);

        List<RetrieverPort.RetrievalResult> rescored = new ArrayList<>();
        for (RetrieverPort.RetrievalResult result : results) {
            // Embed result text for bi-encoder component.
            Embedding resultEmbedding = embeddingModel.embed(result.text()).content();
            float[] resultVector = resultEmbedding.vector();
            double biEncoderSimilarity = cosineSimilarity(queryVector, resultVector);

            // ColBERT-inspired MaxSim score (token-level late interaction approximation).
            String cacheKey = documentTokenVectorStore.cacheKey(result.id(), result.text());
            List<float[]> docTokenVectors = documentTokenVectorCache.computeIfAbsent(
                    cacheKey,
                    unused -> {
                        List<float[]> precomputed = documentTokenVectorStore.get(cacheKey);
                        if (precomputed != null) {
                            return precomputed;
                        }

                        try {
                            List<float[]> computed = buildTokenVectors(result.text());
                            documentTokenVectorStore.put(cacheKey, computed);
                            return computed;
                        } catch (Exception e) {
                            return List.of();
                        }
                    });

            double colbertScore = computeColbertInspiredScore(queryTokenVectors, docTokenVectors);

            // Blend with bi-encoder signal for stability while increasing ColBERT-like behavior.
            double normalizedBiEncoder = (biEncoderSimilarity + 1.0) / 2.0;
            double newScore = (0.7 * colbertScore) + (0.3 * normalizedBiEncoder);

            // Use stage-1 semantic score as new score (replaces original retriever score).

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
            double maxSimilarity = 0.0;
            for (int j = 0; j < i; j++) {
                RetrieverPort.RetrievalResult higher = results.get(j);
                float[] higherVector = docEmbeddings.get(higher.id());

                double similarity = cosineSimilarity(currentVector, higherVector);
                // Normalize similarity to [0, 1]
                double normalizedSimilarity = (similarity + 1.0) / 2.0;
                maxSimilarity = Math.max(maxSimilarity, normalizedSimilarity);
            }

            // Apply at most one penalty per result to avoid over-penalizing dense topical clusters.
            if (maxSimilarity > diversitySimilarityThreshold) {
                score *= diversityPenaltyFactor;
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
        Set<String> validIds = new HashSet<>();
        for (RetrieverPort.RetrievalResult r : results) {
            validIds.add(r.id());
        }

        for (String line : llmResponse.split("\n")) {
            String candidateId = extractDocumentIdFromLine(line, validIds);
            if (candidateId != null) {
                rankedIds.add(candidateId);
            }
        }

        // Create map of results by ID for quick lookup
        Map<String, RetrieverPort.RetrievalResult> resultMap = new HashMap<>();
        for (RetrieverPort.RetrievalResult r : results) {
            resultMap.put(r.id(), r);
        }

        // Rebuild results list in LLM-judged order with scores based on rank position
        List<RetrieverPort.RetrievalResult> judged = new ArrayList<>();
        Set<String> usedIds = new HashSet<>();
        int totalResults = results.size();
        for (int rank = 0; rank < rankedIds.size(); rank++) {
            String id = rankedIds.get(rank);
            if (resultMap.containsKey(id) && !usedIds.contains(id)) {
                RetrieverPort.RetrievalResult original = resultMap.get(id);
                // Score based on rank with smooth decay (avoids large tie bands for bigger result sets).
                double judgedScore = 1.0 - (rank / (double) (totalResults + 1));
                judged.add(new RetrieverPort.RetrievalResult(original.id(), original.text(), judgedScore));
                usedIds.add(id);
            }
        }

        // Add any results not ranked by LLM at the end with distinct low confidence scores.
        int fallbackRank = 0;
        for (RetrieverPort.RetrievalResult r : results) {
            if (!usedIds.contains(r.id())) {
                double fallbackScore = Math.max(0.001, 0.05 - (fallbackRank * 0.001));
                judged.add(new RetrieverPort.RetrievalResult(r.id(), r.text(), fallbackScore));
                fallbackRank++;
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

    private static String extractDocumentIdFromLine(String line, Set<String> validIds) {
        if (line == null) {
            return null;
        }

        String trimmed = BULLET_PREFIX.matcher(line.trim()).replaceFirst("");
        if (trimmed.isEmpty()) {
            return null;
        }

        // Strip wrappers and trailing explanation, e.g. "[doc1]: reason" or "doc1 - why".
        String cleaned = trimmed.replaceAll("^[\\[('\\\"]+", "")
                .replaceAll("[\\])'\\\"]+$", "")
                .trim();
        int sep = firstSeparatorIndex(cleaned);
        if (sep > 0) {
            cleaned = cleaned.substring(0, sep).trim();
        }

        if (validIds.contains(cleaned)) {
            return cleaned;
        }

        // Fallback: search for any known ID token inside the line.
        for (String id : validIds) {
            Pattern tokenPattern = Pattern.compile("(^|[^a-zA-Z0-9_-])" + Pattern.quote(id) + "([^a-zA-Z0-9_-]|$)");
            Matcher matcher = tokenPattern.matcher(trimmed);
            if (matcher.find()) {
                return id;
            }
        }

        return null;
    }

    private static int firstSeparatorIndex(String value) {
        int idx = -1;
        for (String sep : new String[]{":", " - ", " — ", " – "}) {
            int candidate = value.indexOf(sep);
            if (candidate >= 0 && (idx < 0 || candidate < idx)) {
                idx = candidate;
            }
        }
        return idx;
    }

    private List<float[]> buildTokenVectors(String text) throws Exception {
        return tokenEmbeddingPort.embedTokenUnits(ColbertTokenizationUtils.buildTokenUnits(text));
    }

    private static double computeColbertInspiredScore(List<float[]> queryTokenVectors, List<float[]> docTokenVectors) {
        if (queryTokenVectors.isEmpty() || docTokenVectors.isEmpty()) {
            return 0.0;
        }

        // ColBERT-style late interaction approximation:
        // score(query, doc) = average over query tokens of max similarity to any doc token.
        double sumMaxSim = 0.0;
        for (float[] queryToken : queryTokenVectors) {
            double maxSim = -1.0;
            for (float[] docToken : docTokenVectors) {
                double sim = cosineSimilarity(queryToken, docToken);
                if (sim > maxSim) {
                    maxSim = sim;
                }
            }

            // Normalize each token max similarity from [-1,1] to [0,1].
            sumMaxSim += (maxSim + 1.0) / 2.0;
        }

        return sumMaxSim / queryTokenVectors.size();
    }
}

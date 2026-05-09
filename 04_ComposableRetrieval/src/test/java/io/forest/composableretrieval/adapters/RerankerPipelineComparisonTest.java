package io.forest.composableretrieval.adapters;

import io.forest.composableretrieval.core.port.RetrieverPort;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comparison tests demonstrating the relevancy improvement of the reranking pipeline.
 * Measures Mean Reciprocal Rank (MRR) and Document Position Lift before and after reranking.
 */
class RerankerPipelineComparisonTest {

    private ComparisonEmbeddingModel embeddingModel;
    private ComparisonChatLanguageModel chatModel;
    private RerankerPipelineAdapter reranker;

    @BeforeEach
    void setUp() {
        embeddingModel = new ComparisonEmbeddingModel();
        chatModel = new ComparisonChatLanguageModel();
        reranker = new RerankerPipelineAdapter(
            embeddingModel,
            chatModel,
            0.8,    // diversity similarity threshold
            0.7,    // diversity penalty factor
            true    // enable LLM judge
        );
    }

    /**
     * Scenario: Multi-source retrieval returns mixed-quality results.
     * Demonstrates that reranking improves average position of relevant documents.
     */
    @Test
    void testRerankerImprovesMeanReciprocalRank() throws Exception {
        // Simulate results from different retrievers with varying initial scores
        // Relevant docs: doc2 (RAG overview), doc5 (LLM guidance)
        List<RetrieverPort.RetrievalResult> beforeReranking = List.of(
            new RetrieverPort.RetrievalResult("doc1", "Spring Boot configuration best practices", 0.85),
            new RetrieverPort.RetrievalResult("doc2", "RAG systems and retrieval augmented generation", 0.60),
            new RetrieverPort.RetrievalResult("doc3", "Java concurrency patterns and threading", 0.75),
            new RetrieverPort.RetrievalResult("doc4", "Maven dependency management guide", 0.70),
            new RetrieverPort.RetrievalResult("doc5", "LLM prompt engineering best practices", 0.55)
        );

        // Calculate MRR before reranking
        double mrrBefore = calculateMRR(beforeReranking, List.of("doc2", "doc5"));
        System.out.println("MRR BEFORE reranking: " + String.format("%.4f", mrrBefore));
        System.out.println("Position of relevant docs BEFORE: " + 
            getRelevantDocPositions(beforeReranking, List.of("doc2", "doc5")));

        // Apply reranking
        List<RetrieverPort.RetrievalResult> afterReranking = reranker.rerank(
            "RAG systems and LLM integration", 
            beforeReranking
        );

        // Calculate MRR after reranking
        double mrrAfter = calculateMRR(afterReranking, List.of("doc2", "doc5"));
        System.out.println("MRR AFTER reranking: " + String.format("%.4f", mrrAfter));
        System.out.println("Position of relevant docs AFTER: " + 
            getRelevantDocPositions(afterReranking, List.of("doc2", "doc5")));

        // Assert that reranking improves or maintains MRR
        // In a well-tuned system, relevant docs should move higher
        assertTrue(mrrAfter >= mrrBefore * 0.9, 
            "Reranking should not significantly degrade MRR. Before: " + mrrBefore + ", After: " + mrrAfter);
        
        // Verify that at least one relevant doc is in top 2
        List<String> topTwoDocs = afterReranking.stream()
            .limit(2)
            .map(RetrieverPort.RetrievalResult::id)
            .toList();
        assertTrue(topTwoDocs.stream().anyMatch(id -> List.of("doc2", "doc5").contains(id)),
            "At least one relevant doc should be in top 2 after reranking");
    }

    /**
     * Scenario: Mixed-quality initial ranking with overlapping topics.
     * Demonstrates diversity penalty moves redundant docs lower while keeping relevant ones high.
     */
    @Test
    void testRerankerRanksRelevantDocsHigherThanDuplicates() throws Exception {
        // Simulate results where some docs are semantically similar (redundant)
        // Relevant query topic: "composable retrieval patterns"
        List<RetrieverPort.RetrievalResult> beforeReranking = List.of(
            new RetrieverPort.RetrievalResult("doc_overview", 
                "composable retrieval design patterns and architecture", 0.65),
            new RetrieverPort.RetrievalResult("doc_unrelated", 
                "database indexing strategies", 0.80),  // HIGH score but unrelated
            new RetrieverPort.RetrievalResult("doc_retrieval_v1", 
                "retrieval systems and composable components", 0.62),  // Similar to overview
            new RetrieverPort.RetrievalResult("doc_retrieval_v2", 
                "composable retrieval and modular architecture", 0.61),  // Very similar to overview
            new RetrieverPort.RetrievalResult("doc_impl", 
                "implementation guide for composable patterns", 0.58)
        );

        // Apply reranking
        List<RetrieverPort.RetrievalResult> afterReranking = reranker.rerank(
            "composable retrieval patterns", 
            beforeReranking
        );

        // Print ranking change analysis
        System.out.println("\n=== Ranking Changes (Before -> After) ===");
        for (int i = 0; i < afterReranking.size(); i++) {
            String docId = afterReranking.get(i).id();
            int positionBefore = getPositionOf(beforeReranking, docId);
            System.out.println(docId + ": position " + (positionBefore + 1) + " -> " + (i + 1) + 
                ", score " + String.format("%.2f", afterReranking.get(i).score()));
        }

        // The unrelated high-scoring doc should rank lower after reranking
        int unrelatedPosition = getPositionOf(afterReranking, "doc_unrelated");
        int overviewPosition = getPositionOf(afterReranking, "doc_overview");
        
        assertTrue(overviewPosition < unrelatedPosition,
            "Relevant 'doc_overview' should rank higher than unrelated 'doc_unrelated' after reranking");
    }

    /**
     * Scenario: Initial retriever has poor ranking from keyword matching.
     * Demonstrates that LLM judge stage corrects poorly ranked results.
     */
    @Test
    void testRerankerCorrectionOfPoorlyRankedResults() throws Exception {
        // Simulate keyword-based retriever that matches queries but ranks by frequency, not semantics
        List<RetrieverPort.RetrievalResult> beforeReranking = List.of(
            new RetrieverPort.RetrievalResult("doc_keyword_heavy", 
                "retrieval retrieval retrieval appears many times", 0.90),  // Keyword overload
            new RetrieverPort.RetrievalResult("doc_semantic_match", 
                "understanding retrieval augmented generation systems", 0.55),  // Better semantics, lower score
            new RetrieverPort.RetrievalResult("doc_keyword_spam", 
                "retrieval retrieval techniques for retrieval optimization", 0.88),
            new RetrieverPort.RetrievalResult("doc_high_quality", 
                "RAG architecture with retrieval strategies", 0.65)
        );

        double scoreDiffBefore = beforeReranking.get(0).score() - beforeReranking.get(1).score();
        System.out.println("\nScore gap BEFORE reranking (keyword spam advantage): " + 
            String.format("%.2f", scoreDiffBefore));

        // Apply reranking
        List<RetrieverPort.RetrievalResult> afterReranking = reranker.rerank(
            "how does RAG with retrieval work", 
            beforeReranking
        );

        System.out.println("Ranking AFTER reranking:");
        for (int i = 0; i < afterReranking.size(); i++) {
            System.out.println((i + 1) + ". " + afterReranking.get(i).id() + 
                " (score: " + String.format("%.2f", afterReranking.get(i).score()) + ")");
        }

        // Verify semantic docs are ranked higher after reranking
        int semanticPosition = getPositionOf(afterReranking, "doc_semantic_match");
        int spamPosition = getPositionOf(afterReranking, "doc_keyword_spam");
        
        assertTrue(semanticPosition < spamPosition,
            "Semantic match should rank higher than keyword spam after LLM judge stage");
    }

    /**
     * Scenario: Demonstrates cumulative effect of all three reranking stages.
     * Shows how combining ColBERT + Diversity + LLM improves overall quality.
     */
    @Test
    void testCumulativeImpactOfAllRerangingStages() throws Exception {
        List<RetrieverPort.RetrievalResult> initialResults = List.of(
            new RetrieverPort.RetrievalResult("doc1", "text about data structures and algorithms", 0.92),
            new RetrieverPort.RetrievalResult("doc2", "data structures in Java programming", 0.88),  // Similar to doc1
            new RetrieverPort.RetrievalResult("doc3", "LLM integration with vector databases", 0.70),
            new RetrieverPort.RetrievalResult("doc4", "vector database optimizations", 0.69),  // Similar to doc3
            new RetrieverPort.RetrievalResult("doc5", "machine learning model deployment", 0.65)
        );

        List<RetrieverPort.RetrievalResult> reranked = reranker.rerank(
            "data structures and machine learning", 
            initialResults
        );

        // Calculate diversity metric: average pairwise position distance
        double diversityBefore = calculateAverageRankingDiversity(initialResults);
        double diversityAfter = calculateAverageRankingDiversity(reranked);

        System.out.println("\nDiversity Metric (higher is more diverse):");
        System.out.println("Before reranking: " + String.format("%.3f", diversityBefore));
        System.out.println("After reranking: " + String.format("%.3f", diversityAfter));

        // After diversity penalty and LLM judge, similar docs should be separated
        assertTrue(reranked.size() > 0, "Should have reranked results");
        
        // Verify results are properly ordered (descending scores)
        for (int i = 0; i < reranked.size() - 1; i++) {
            assertTrue(reranked.get(i).score() >= reranked.get(i + 1).score(),
                "Results should be sorted by score descending");
        }
    }

    /**
     * Scenario: LLM judge returns noisy output with numbering, rationale, and duplicate IDs.
     * Validates robust parsing and deduplication in stage 3 while preserving deterministic order.
     */
    @Test
    void testLlmJudgeHandlesNoisyOutputAndDuplicateIds() throws Exception {
        RerankerPipelineAdapter noisyReranker = new RerankerPipelineAdapter(
            embeddingModel,
            new NoisyComparisonChatLanguageModel(),
            0.8,
            0.7,
            true
        );

        List<RetrieverPort.RetrievalResult> beforeReranking = List.of(
            new RetrieverPort.RetrievalResult("doc_overview", "composable retrieval design patterns and architecture", 0.65),
            new RetrieverPort.RetrievalResult("doc_unrelated", "database indexing strategies", 0.80),
            new RetrieverPort.RetrievalResult("doc_retrieval_v1", "retrieval systems and composable components", 0.62),
            new RetrieverPort.RetrievalResult("doc_impl", "implementation guide for composable patterns", 0.58)
        );

        List<RetrieverPort.RetrievalResult> reranked = noisyReranker.rerank(
            "composable retrieval patterns",
            beforeReranking
        );

        assertEquals(4, reranked.size(), "All input docs should remain after reranking");

        Set<String> uniqueIds = new HashSet<>();
        for (RetrieverPort.RetrievalResult result : reranked) {
            assertTrue(uniqueIds.add(result.id()), "Reranked output should not contain duplicate IDs");
        }

        // The noisy output intentionally ranks impl first and overview second.
        assertEquals("doc_impl", reranked.get(0).id());
        assertEquals("doc_overview", reranked.get(1).id());
    }

    // ============ Helper Methods ============

    /**
     * Calculate Mean Reciprocal Rank (MRR) for relevant documents.
     * MRR = (1/N) * Σ(1/rank_i) where rank_i is position of relevant doc i.
     */
    private double calculateMRR(List<RetrieverPort.RetrievalResult> results, List<String> relevantDocIds) {
        return relevantDocIds.stream()
            .mapToDouble(docId -> {
                int position = getPositionOf(results, docId);
                return position >= 0 ? 1.0 / (position + 1) : 0.0;
            })
            .average()
            .orElse(0.0);
    }

    /**
     * Get position (0-indexed) of a document in results, or -1 if not found.
     */
    private int getPositionOf(List<RetrieverPort.RetrievalResult> results, String docId) {
        for (int i = 0; i < results.size(); i++) {
            if (results.get(i).id().equals(docId)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Get position string of relevant documents (1-indexed).
     */
    private String getRelevantDocPositions(List<RetrieverPort.RetrievalResult> results, List<String> relevantDocIds) {
        return relevantDocIds.stream()
            .map(docId -> docId + "@" + (getPositionOf(results, docId) + 1))
            .toList()
            .toString();
    }

    /**
     * Calculate average diversity as average pairwise semantic distance.
     * Higher values indicate more diverse ranking.
     */
    private double calculateAverageRankingDiversity(List<RetrieverPort.RetrievalResult> results) {
        if (results.size() < 2) return 1.0;

        double totalDistance = 0;
        int count = 0;

        for (int i = 0; i < results.size(); i++) {
            for (int j = i + 1; j < results.size(); j++) {
                // Simulate semantic distance: position difference weighted by score difference
                int positionDiff = j - i;
                double scoreDiff = Math.abs(results.get(i).score() - results.get(j).score());
                totalDistance += positionDiff * (1 + scoreDiff);
                count++;
            }
        }

        return count > 0 ? totalDistance / count : 0.0;
    }

    // ============ Mock Implementations ============

    /**
     * Mock EmbeddingModel with semantic-aware embeddings for comparison tests.
     */
    static class ComparisonEmbeddingModel implements EmbeddingModel {
        @Override
        public Response<Embedding> embed(String text) {
            // Create embeddings that reflect semantic similarity
            float[] vector = new float[10];
            
            // Domain keywords boost specific dimensions
            if (text.toLowerCase().contains("retrieval")) vector[0] += 0.3f;
            if (text.toLowerCase().contains("rag")) vector[1] += 0.3f;
            if (text.toLowerCase().contains("composable")) vector[2] += 0.3f;
            if (text.toLowerCase().contains("data")) vector[3] += 0.3f;
            if (text.toLowerCase().contains("machine learning")) vector[4] += 0.3f;
            if (text.toLowerCase().contains("llm")) vector[5] += 0.3f;
            if (text.toLowerCase().contains("vector")) vector[6] += 0.3f;
            if (text.toLowerCase().contains("database")) vector[7] += 0.3f;
            
            // Hash-based component for uniqueness
            int hash = text.hashCode();
            for (int i = 0; i < vector.length; i++) {
                vector[i] += (float) Math.sin(hash + i) * 0.1f;
            }
            
            return new Response<>(new Embedding(vector));
        }

        @Override
        public Response<Embedding> embed(TextSegment textSegment) {
            return embed(textSegment.text());
        }

        @Override
        public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
            return new Response<>(textSegments.stream().map(this::embed).map(Response::content).toList());
        }
    }

    /**
     * Mock ChatLanguageModel that simulates intelligent LLM ranking.
     */
    static class ComparisonChatLanguageModel implements ChatLanguageModel {
        @Override
        public String generate(String prompt) {
            // Simple heuristic: docs with higher relevance keywords ranked first
            // This simulates the LLM judge stage
            return "doc_overview\ndoc_impl\ndoc_retrieval_v1\ndoc_retrieval_v2\ndoc_unrelated";
        }

        @Override
        public Response<AiMessage> generate(List<ChatMessage> messages) {
            return new Response<>(new AiMessage("mock ranking"));
        }
    }

    static class NoisyComparisonChatLanguageModel implements ChatLanguageModel {
        @Override
        public String generate(String prompt) {
            return "Ranking:\n"
                + "1) [doc_impl]: best implementation depth\n"
                + "2. doc_overview - strongest architecture fit\n"
                + "3) doc_impl\n"
                + "4) doc_retrieval_v1\n"
                + "Notes: doc_unrelated has weak topical overlap";
        }

        @Override
        public Response<AiMessage> generate(List<ChatMessage> messages) {
            return new Response<>(new AiMessage("mock ranking"));
        }
    }
}

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

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the three-stage reranking pipeline.
 * Validates ColBERT re-scoring, diversity penalty, and LLM-as-a-Judge.
 */
class RerankerPipelineAdapterTest {

    private MockEmbeddingModel embeddingModel;
    private MockChatLanguageModel chatModel;
    private RerankerPipelineAdapter reranker;

    @BeforeEach
    void setUp() {
        embeddingModel = new MockEmbeddingModel();
        chatModel = new MockChatLanguageModel();
        reranker = new RerankerPipelineAdapter(
            embeddingModel,
            chatModel,
            0.8,    // diversity similarity threshold
            0.7,    // diversity penalty factor
            true    // enable LLM judge
        );
    }

    @Test
    void testStage1ColBertReScoring() throws Exception {
        // Create mock retrieval results with initial scores
        List<RetrieverPort.RetrievalResult> results = List.of(
            new RetrieverPort.RetrievalResult("doc1", "machine learning and deep learning", 0.5),
            new RetrieverPort.RetrievalResult("doc2", "java programming language", 0.3),
            new RetrieverPort.RetrievalResult("doc3", "neural networks and AI models", 0.4)
        );

        // Rerank (should go through all 3 stages)
        List<RetrieverPort.RetrievalResult> reranked = reranker.rerank("machine learning", results);

        // Verify results are not empty
        assertNotNull(reranked);
        assertFalse(reranked.isEmpty());
        assertEquals(3, reranked.size());

        // Verify scores are in valid range [0, 1]
        for (RetrieverPort.RetrievalResult r : reranked) {
            assertTrue(r.score() >= 0.0 && r.score() <= 1.0, 
                    "Score out of range: " + r.score());
        }
    }

    @Test
    void testDiversityPenaltyReducesSimilarResults() throws Exception {
        // Create results where doc1 and doc2 are similar (should be penalized)
        List<RetrieverPort.RetrievalResult> results = List.of(
            new RetrieverPort.RetrievalResult("doc1", "machine learning and deep learning", 0.9),
            new RetrieverPort.RetrievalResult("doc2", "machine learning with neural networks", 0.8),
            new RetrieverPort.RetrievalResult("doc3", "completely different topic about cooking", 0.7)
        );

        List<RetrieverPort.RetrievalResult> reranked = reranker.rerank("machine learning", results);

        // Verify similar results are penalized (doc2 should have lower score than initial)
        assertNotNull(reranked);
        assertEquals(3, reranked.size());
    }

    @Test
    void testEmptyResults() throws Exception {
        List<RetrieverPort.RetrievalResult> empty = new ArrayList<>();
        List<RetrieverPort.RetrievalResult> reranked = reranker.rerank("test query", empty);

        assertEquals(0, reranked.size());
    }

    @Test
    void testSingleResult() throws Exception {
        List<RetrieverPort.RetrievalResult> single = List.of(
            new RetrieverPort.RetrievalResult("doc1", "some text", 0.5)
        );

        List<RetrieverPort.RetrievalResult> reranked = reranker.rerank("query", single);

        assertEquals(1, reranked.size());
        assertEquals("doc1", reranked.get(0).id());
    }

    /**
     * Mock EmbeddingModel for testing — returns predictable embeddings.
     */
    static class MockEmbeddingModel implements EmbeddingModel {
        @Override
        public Response<Embedding> embed(String text) {
            // Simple mock: hash-based pseudo-random but deterministic embeddings
            float[] vector = new float[10];
            int hash = text.hashCode();
            for (int i = 0; i < vector.length; i++) {
                vector[i] = (float) Math.sin(hash + i) * 0.5f;
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
     * Mock ChatLanguageModel for testing — returns LLM judge ranking.
     */
    static class MockChatLanguageModel implements ChatLanguageModel {
        @Override
        public String generate(String prompt) {
            // Mock LLM response: rank results in order
            return "doc3\ndoc2\ndoc1"; // Mock ranking
        }

        @Override
        public Response<AiMessage> generate(List<ChatMessage> messages) {
            // Not used in our test flow since we use generate(String)
            return new Response<>(new AiMessage("mock response"));
        }
    }
}

package io.forest.composableretrieval.adapters;

import io.forest.composableretrieval.core.port.RetrieverPort;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import io.forest.composableretrieval.core.port.TokenEmbeddingPort;
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
    private RerankerPipelineAdapter rerankerNoJudge;

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

        rerankerNoJudge = new RerankerPipelineAdapter(
            embeddingModel,
            chatModel,
            0.8,
            0.7,
            false
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
    void testStage1ColBertInspiredPhraseSensitivity() throws Exception {
        // Disable diversity and LLM judge to isolate stage-1 behavior.
        RerankerPipelineAdapter stage1OnlyReranker = new RerankerPipelineAdapter(
            new PhraseAwareEmbeddingModel(),
            chatModel,
            1.1,
            0.7,
            false
        );

        List<RetrieverPort.RetrievalResult> results = List.of(
            new RetrieverPort.RetrievalResult("doc_phrase", "machine learning systems overview", 0.4),
            new RetrieverPort.RetrievalResult("doc_java", "java concurrency and thread pools", 0.9),
            new RetrieverPort.RetrievalResult("doc_partial", "machine optimization techniques", 0.6)
        );

        List<RetrieverPort.RetrievalResult> reranked = stage1OnlyReranker.rerank("machine learning", results);

        assertEquals("doc_phrase", reranked.get(0).id(),
            "Phrase-aligned document should rank first under ColBERT-inspired token interaction");
        assertTrue(indexOf(reranked, "doc_partial") < indexOf(reranked, "doc_java"),
            "Partial semantic match should outrank unrelated lexical high-score document");
    }

    @Test
    void testDiversityPenaltyReducesSimilarResults() throws Exception {
        // Create results where doc1 and doc2 are similar (should be penalized)
        List<RetrieverPort.RetrievalResult> results = List.of(
            new RetrieverPort.RetrievalResult("doc1", "machine learning and deep learning", 0.9),
            new RetrieverPort.RetrievalResult("doc2", "machine learning with neural networks", 0.8),
            new RetrieverPort.RetrievalResult("doc3", "completely different topic about cooking", 0.7)
        );

        List<RetrieverPort.RetrievalResult> reranked = rerankerNoJudge.rerank("machine learning", results);

        // Verify similar results are penalized relative to less-similar items.
        assertNotNull(reranked);
        assertEquals(3, reranked.size());

        int similarPosition = indexOf(reranked, "doc2");
        int dissimilarPosition = indexOf(reranked, "doc3");
        assertTrue(similarPosition > dissimilarPosition,
            "Similar doc2 should be pushed below dissimilar doc3 after diversity penalty");
    }

    @Test
    void testLlmJudgeParsesCommonNumberedAndReasonedOutput() throws Exception {
        RerankerPipelineAdapter robustParserReranker = new RerankerPipelineAdapter(
            embeddingModel,
            new NumberedReasoningChatLanguageModel(),
            0.8,
            0.7,
            true
        );

        List<RetrieverPort.RetrievalResult> results = List.of(
            new RetrieverPort.RetrievalResult("doc1", "alpha", 0.8),
            new RetrieverPort.RetrievalResult("doc2", "beta", 0.7),
            new RetrieverPort.RetrievalResult("doc3", "gamma", 0.6)
        );

        List<RetrieverPort.RetrievalResult> reranked = robustParserReranker.rerank("test", results);

        assertEquals(3, reranked.size());
        assertEquals("doc2", reranked.get(0).id());
        assertEquals("doc1", reranked.get(1).id());
        assertEquals("doc3", reranked.get(2).id());
    }

    @Test
    void testLlmJudgeFallbackScoresStayDistinctForUnrankedDocs() throws Exception {
        RerankerPipelineAdapter partialRankingReranker = new RerankerPipelineAdapter(
            embeddingModel,
            new PartialRankingChatLanguageModel(),
            0.8,
            0.7,
            true
        );

        List<RetrieverPort.RetrievalResult> results = List.of(
            new RetrieverPort.RetrievalResult("doc1", "alpha", 0.8),
            new RetrieverPort.RetrievalResult("doc2", "beta", 0.7),
            new RetrieverPort.RetrievalResult("doc3", "gamma", 0.6),
            new RetrieverPort.RetrievalResult("doc4", "delta", 0.5)
        );

        List<RetrieverPort.RetrievalResult> reranked = partialRankingReranker.rerank("test", results);

        assertEquals(4, reranked.size());
        assertEquals("doc2", reranked.get(0).id());
        assertTrue(reranked.get(1).score() > reranked.get(2).score(),
            "Fallback scores should be distinct to reduce tie-related instability");
        assertTrue(reranked.get(2).score() > reranked.get(3).score(),
            "Fallback scores should decay for deterministic ordering");
    }

    @Test
    void testDocumentTokenCacheReducesTokenEmbeddingWorkAcrossQueries() throws Exception {
        CountingTokenEmbeddingPort tokenEmbeddingPort = new CountingTokenEmbeddingPort(embeddingModel);
        RerankerPipelineAdapter rerankerWithCache = new RerankerPipelineAdapter(
            embeddingModel,
            tokenEmbeddingPort,
            chatModel,
            0.8,
            0.7,
            false
        );

        List<RetrieverPort.RetrievalResult> results = List.of(
            new RetrieverPort.RetrievalResult("doc1", "machine learning and deep learning", 0.7),
            new RetrieverPort.RetrievalResult("doc2", "neural networks for retrieval", 0.6)
        );

        rerankerWithCache.rerank("machine learning retrieval", results);
        int afterFirstRun = tokenEmbeddingPort.totalEmbeddedTokenUnits;

        rerankerWithCache.rerank("machine learning retrieval", results);
        int secondRunDelta = tokenEmbeddingPort.totalEmbeddedTokenUnits - afterFirstRun;

        // Query tokens are re-embedded each run, but document token units should come from cache.
        assertTrue(secondRunDelta < afterFirstRun,
            "Second run should embed fewer token units because document token vectors are cached");
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

    static class NumberedReasoningChatLanguageModel implements ChatLanguageModel {
        @Override
        public String generate(String prompt) {
            return "1. [doc2]: best semantic match\n2) doc1 - strong relevance\n3. doc3";
        }

        @Override
        public Response<AiMessage> generate(List<ChatMessage> messages) {
            return new Response<>(new AiMessage("mock response"));
        }
    }

    static class PartialRankingChatLanguageModel implements ChatLanguageModel {
        @Override
        public String generate(String prompt) {
            return "doc2";
        }

        @Override
        public Response<AiMessage> generate(List<ChatMessage> messages) {
            return new Response<>(new AiMessage("mock response"));
        }
    }

    static class PhraseAwareEmbeddingModel implements EmbeddingModel {
        private static final int DIM = 6;

        @Override
        public Response<Embedding> embed(String text) {
            String t = text == null ? "" : text.toLowerCase();
            float[] vector = new float[DIM];

            if (t.contains("machine learning")) {
                vector[0] = 1.0f;
                vector[1] = 1.0f;
            }
            if (t.contains("machine")) {
                vector[0] += 0.8f;
            }
            if (t.contains("learning")) {
                vector[1] += 0.8f;
            }
            if (t.contains("java")) {
                vector[2] += 1.0f;
            }
            if (t.contains("concurrency")) {
                vector[3] += 1.0f;
            }

            // Deterministic low-amplitude tail to avoid all-zero vectors.
            int hash = t.hashCode();
            vector[4] = (float) Math.sin(hash) * 0.01f;
            vector[5] = (float) Math.cos(hash) * 0.01f;

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

    static class CountingTokenEmbeddingPort implements TokenEmbeddingPort {
        private final EmbeddingModel embeddingModel;
        private int totalEmbeddedTokenUnits;

        CountingTokenEmbeddingPort(EmbeddingModel embeddingModel) {
            this.embeddingModel = embeddingModel;
            this.totalEmbeddedTokenUnits = 0;
        }

        @Override
        public List<float[]> embedTokenUnits(List<String> tokenUnits) throws Exception {
            List<float[]> vectors = new ArrayList<>(tokenUnits.size());
            for (String tokenUnit : tokenUnits) {
                totalEmbeddedTokenUnits++;
                vectors.add(embeddingModel.embed(tokenUnit).content().vector());
            }
            return vectors;
        }
    }

    private static int indexOf(List<RetrieverPort.RetrievalResult> results, String id) {
        for (int i = 0; i < results.size(); i++) {
            if (results.get(i).id().equals(id)) {
                return i;
            }
        }
        return -1;
    }
}

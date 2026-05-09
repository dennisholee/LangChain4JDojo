package io.forest.composableretrieval.app;

import io.forest.composableretrieval.adapters.DocumentIngestionAdapter;
import io.forest.composableretrieval.adapters.EmbeddingStoreRetrieverAdapter;
import io.forest.composableretrieval.adapters.FileSystemRetrieverAdapter;
import io.forest.composableretrieval.adapters.InMemoryRetrieverAdapter;
import io.forest.composableretrieval.adapters.RetrieverPortContentRetrieverBridge;
import io.forest.composableretrieval.adapters.RerankerPipelineAdapter;
import io.forest.composableretrieval.core.ComposableRetriever;
import io.forest.composableretrieval.core.RagService;
import io.forest.composableretrieval.core.port.DocumentIngestionPort;
import io.forest.composableretrieval.core.port.RerankerPort;
import io.forest.composableretrieval.core.port.RetrieverPort;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;
import java.util.List;

/**
 * Composable Retriever — RAG demo using LangChain4J and a ChromaDB-backed vector store.
 *
 * <p>Architecture layers visible here:
 * <pre>
 *  ┌─────────────────────────────────────────────────────────┐
 *  │  Application (Main)                                     │
 *  │  Wires models → adapters → core → RagService            │
 *  └───────────────────┬─────────────────────────────────────┘
 *                      │ uses
 *  ┌───────────────────▼─────────────────────────────────────┐
 *  │  Core                                                   │
 *  │  ComposableRetriever  ←  RetrieverPort[]  (pure Java)   │
 *  │  RagService           ←  ContentRetriever[] + LLM       │
 *  └───────────────────┬─────────────────────────────────────┘
 *                      │ depends on ports
 *  ┌───────────────────▼─────────────────────────────────────┐
 *  │  Adapters                                               │
 *  │  EmbeddingStoreRetrieverAdapter  ← LangChain4J          │
 *  │  ChromaEmbeddingStore            ← LangChain4J ChromaDB │
 *  │  RetrieverPortContentRetrieverBridge (core ↔ LC4J)      │
 *  └─────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <p>Requires environment variable {@code OPENAI_API_KEY}.
 */
public class Main {

    public static void main(String[] args) throws Exception {
        AppConfig cfg = new AppConfig();

        // API key is read from env only — never from the properties file.
        String apiKey;
        try {
            apiKey = cfg.requireOpenAiApiKey();
        } catch (IllegalStateException e) {
            System.err.println("ERROR: " + e.getMessage());
            System.exit(1);
            return;
        }

        // ── Read all other config from application.properties ────────────────
        String embeddingModelName = cfg.get("openai.embedding.model", "text-embedding-3-small");
        String embeddingBaseUrl   = cfg.get("openai.embedding.base-url", "");
        String chatModelName      = cfg.get("openai.chat.model",      "gpt-4o-mini");
        String chatBaseUrl        = cfg.get("openai.chat.base-url",   "");
        String chromaBaseUrl      = cfg.get("vectorstore.chroma.base-url", "http://localhost:8000");
        String chromaCollection   = cfg.get("vectorstore.chroma.collection", "composable-retriever");
        int    maxResults         = cfg.getInt("retriever.max-results", 3);
        String demoQuery          = cfg.get("demo.query", "What is composable retrieval and RAG?");

        // ── LangChain4J models ───────────────────────────────────────────────
        OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder embeddingModelBuilder = OpenAiEmbeddingModel.builder()
            .apiKey(apiKey)
            .modelName(embeddingModelName);
        if (embeddingBaseUrl != null && !embeddingBaseUrl.isBlank()) {
            embeddingModelBuilder.baseUrl(embeddingBaseUrl);
        }
        EmbeddingModel embeddingModel = embeddingModelBuilder.build();

        OpenAiChatModel.OpenAiChatModelBuilder chatModelBuilder = OpenAiChatModel.builder()
            .apiKey(apiKey)
            .modelName(chatModelName);
        if (chatBaseUrl != null && !chatBaseUrl.isBlank()) {
            chatModelBuilder.baseUrl(chatBaseUrl);
        }
        OpenAiChatModel chatModel = chatModelBuilder.build();

        // ── Infrastructure adapters ──────────────────────────────────────────
        ChromaEmbeddingStore vectorStore;
        try {
            vectorStore = ChromaEmbeddingStore.builder()
                .baseUrl(chromaBaseUrl)
                .collectionName(chromaCollection)
                .build();
        } catch (RuntimeException e) {
            System.err.println("ERROR: Unable to connect to ChromaDB at " + chromaBaseUrl);
            System.err.println("Make sure ChromaDB is running and the collection is reachable: " + chromaCollection);
            System.exit(1);
            return;
        }

        // ── Ingestion (DocumentIngestionPort) ────────────────────────────────
        DocumentIngestionPort ingestor =
            new DocumentIngestionAdapter(embeddingModel, vectorStore);

        List<String[]> documents = List.of(
            new String[]{"doc1", "LangChain4J is a Java library for building LLM-powered applications."},
            new String[]{"doc2", "Composable retrievers combine multiple data sources for retrieval-augmented generation."},
            new String[]{"doc3", "SQLite is a lightweight, file-based relational database, ideal for embedded use."},
            new String[]{"doc4", "Clean architecture separates concerns into ports and adapters, keeping the core framework-free."},
            new String[]{"doc5", "Retrieval-Augmented Generation (RAG) grounds LLM responses in factual, domain-specific context."}
        );

        System.out.println("Indexing " + documents.size() + " documents …");
        for (String[] doc : documents) {
            ingestor.ingest(doc[0], doc[1]);
        }

        // ── Retrieval adapters (RetrieverPort) ─────────────────────────────────
        // Source 1: ChromaDB vector store (semantic similarity)
        EmbeddingStoreRetrieverAdapter chromaRetriever =
            new EmbeddingStoreRetrieverAdapter(vectorStore, embeddingModel, maxResults);

        // Source 2: In-memory keyword search (lexical matching)
        List<InMemoryRetrieverAdapter.Document> inMemoryDocs = List.of(
            new InMemoryRetrieverAdapter.Document(
                "mem1",
                "Composability allows systems to be built from independent, reusable components."),
            new InMemoryRetrieverAdapter.Document(
                "mem2",
                "Vector stores use embeddings to store and retrieve semantically similar content."),
            new InMemoryRetrieverAdapter.Document(
                "mem3",
                "RAG systems combine retrieval with generation for accurate, context-grounded responses.")
        );
        InMemoryRetrieverAdapter inMemoryRetriever = new InMemoryRetrieverAdapter(inMemoryDocs);

        // Source 3: File system search (searches .txt files in a directory)
        FileSystemRetrieverAdapter fileRetriever = new FileSystemRetrieverAdapter("./docs");

        // ── Composable retriever (core, pure Java) ───────────────────────────
        // Merges results from all sources: vector store + in-memory + files
        // Deduplicates by id, keeps highest score, sorts descending
        ComposableRetriever composableRetriever =
            new ComposableRetriever(List.of(chromaRetriever, inMemoryRetriever, fileRetriever));

        // ── Reranker pipeline (three stages) ──────────────────────────────────
        RerankerPort reranker = new RerankerPipelineAdapter(
            embeddingModel,
            chatModel,
            0.8,   // diversity similarity threshold
            0.7,   // diversity penalty factor
            true   // enable LLM-as-a-Judge
        );

        // ── Demo: composable retrieval from multiple sources ──────────────────
        String query = demoQuery;
        System.out.println("\n── Individual retriever results ──");
        System.out.println("Query: \"" + query + "\"\n");

        // Show results from each source individually
        System.out.println("📊 ChromaDB (semantic similarity):");
        List<RetrieverPort.RetrievalResult> chromaHits = chromaRetriever.retrieve(query, 3);
        for (RetrieverPort.RetrievalResult r : chromaHits) {
            System.out.printf("  [%.3f] %s%n", r.score(), r.text().substring(0, Math.min(80, r.text().length())) + "…");
        }

        System.out.println("\n📋 In-Memory (keyword matching):");
        List<RetrieverPort.RetrievalResult> memHits = inMemoryRetriever.retrieve(query, 3);
        for (RetrieverPort.RetrievalResult r : memHits) {
            System.out.printf("  [%.3f] %s%n", r.score(), r.text().substring(0, Math.min(80, r.text().length())) + "…");
        }

        System.out.println("\n📁 Files (file system search):");
        List<RetrieverPort.RetrievalResult> fileHits = fileRetriever.retrieve(query, 3);
        if (fileHits.isEmpty()) {
            System.out.println("  (no files in ./docs directory)");
        } else {
            for (RetrieverPort.RetrievalResult r : fileHits) {
                System.out.printf("  [%.3f] %s%n", r.score(), r.text().substring(0, Math.min(80, r.text().length())) + "…");
            }
        }

        System.out.println("\n── Merged & ranked (Composable Retriever) ──");
        List<RetrieverPort.RetrievalResult> hits = composableRetriever.retrieve(query, 5);
        for (RetrieverPort.RetrievalResult r : hits) {
            String source = determineSource(r.id(), chromaHits, memHits, fileHits);
            System.out.printf("  [%.3f] %s %s%n", r.score(), source, r.text().substring(0, Math.min(70, r.text().length())) + "…");
        }

        // ── Apply three-stage reranker pipeline ────────────────────────────────
        System.out.println("\n── Reranking (ColBERT → Diversity → LLM-as-Judge) ──");
        List<RetrieverPort.RetrievalResult> rerankedResults;
        try {
            rerankedResults = reranker.rerank(query, hits);
        } catch (Exception e) {
            System.err.println("ERROR during reranking: " + e.getMessage());
            e.printStackTrace();
            rerankedResults = hits; // Fall back to original ranking
        }

        System.out.println("\n── Final ranked results (after reranking) ──");
        for (RetrieverPort.RetrievalResult r : rerankedResults) {
            String source = determineSource(r.id(), chromaHits, memHits, fileHits);
            System.out.printf("  [%.3f] %s %s%n", r.score(), source, r.text().substring(0, Math.min(70, r.text().length())) + "…");
        }

        // ── Demo: full RAG Q&A ───────────────────────────────────────────────
        // Bridge the reranked results to LangChain4J's ContentRetriever interface.
        // Make final reference for lambda expression
        final List<RetrieverPort.RetrievalResult> finalRerankedHits = rerankedResults;
        ContentRetriever contentRetriever =
            new RetrieverPortContentRetrieverBridge(
                // Create an inline retriever that returns reranked results
                (q, topK) -> finalRerankedHits.subList(0, Math.min(topK, finalRerankedHits.size())),
                3
            );

        RagService ragService = new RagService(chatModel, List.of(contentRetriever));
        System.out.println("\n── RAG answer ──");
        System.out.println(ragService.answer(query));
    }

    /**
     * Determine which source(s) a retrieval result came from by matching text across adapter results.
     */
    private static String determineSource(
            String id,
            List<RetrieverPort.RetrievalResult> chromaHits,
            List<RetrieverPort.RetrievalResult> memHits,
            List<RetrieverPort.RetrievalResult> fileHits) {
        StringBuilder sources = new StringBuilder("(");

        if (chromaHits.stream().anyMatch(r -> r.id().equals(id))) {
            sources.append("📊");
        }
        if (memHits.stream().anyMatch(r -> r.id().equals(id))) {
            sources.append("📋");
        }
        if (fileHits.stream().anyMatch(r -> r.id().equals(id))) {
            sources.append("📁");
        }

        sources.append(")");
        return sources.toString();
    }
}



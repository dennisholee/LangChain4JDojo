# Composable Retriever — RAG with LangChain4J + ChromaDB

A clean-architecture Java 21 / Maven application that implements **Retrieval-Augmented Generation (RAG)** using [LangChain4J](https://github.com/langchain4j/langchain4j) and a **ChromaDB-backed vector store**.

---

## Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│  Application (app/Main.java)                                     │
│  Wires models → adapters → core → RagService                     │
└────────────────────────┬─────────────────────────────────────────┘
                         │ uses
┌────────────────────────▼─────────────────────────────────────────┐
│  Core (zero framework deps)                                      │
│  ComposableRetriever  — merges N RetrieverPort adapters          │
│  RagService           — DefaultRetrievalAugmentor + AiServices   │
└────────────────────────┬─────────────────────────────────────────┘
                         │ depends on ports
┌────────────────────────▼─────────────────────────────────────────┐
│  Ports  (core/port/)                                             │
│  RetrieverPort         — retrieve(query, topK)                   │
│  EmbeddingsPort        — embed(text)                             │
│  VectorStorePort       — upsert / search                         │
│  DocumentIngestionPort — ingest(id, text)                        │
└────────────────────────┬─────────────────────────────────────────┘
                         │ implemented by
┌────────────────────────▼─────────────────────────────────────────┐
│  Adapters  (adapters/)                                           │
│  EmbeddingStoreRetrieverAdapter — RetrieverPort + ContentRetriever│
│    ↳ wraps EmbeddingStoreContentRetriever (LangChain4J)          │
│  RetrieverPortContentRetrieverBridge — RetrieverPort→ContentRet. │
│  ChromaEmbeddingStore — EmbeddingStore<TextSegment> via ChromaDB │
│  DocumentIngestionAdapter — DocumentIngestionPort → embed+store  │
└──────────────────────────────────────────────────────────────────┘
```

### Key design decisions

| Concern | Solution |
|---|---|
| **Multi-retriever composition** | `ComposableRetriever` merges N `RetrieverPort`s in pure Java — no framework in the core |
| **LangChain4J RAG pipeline** | `RagService` uses `DefaultQueryRouter` (broadcasts to all `ContentRetriever`s) + `DefaultRetrievalAugmentor` + `AiServices` |
| **Vector store** | `ChromaEmbeddingStore` implements `EmbeddingStore<TextSegment>`; vectors are stored in ChromaDB and queried via Chroma's vector search API |
| **Bridge** | `RetrieverPortContentRetrieverBridge` adapts any `RetrieverPort` (core domain) to a LangChain4J `ContentRetriever` (framework) without coupling the core layer |
| **Result reranking** | `RerankerPipelineAdapter` implements three-stage reranking: ColBERT semantic re-scoring, diversity penalty, LLM-as-a-Judge |

---

## Reranking Pipeline

After composable retrieval merges results from multiple sources, a three-stage **reranker pipeline** refines the ranking before passing to the LLM:

```
┌─────────────────────────────────────────────────────────────────┐
│  Merged Results (from ComposableRetriever)                      │
│  [1.0] doc_semantic_best   [0.8] doc_file_match   [0.5] doc_mem│
└──────────────────────┬──────────────────────────────────────────┘
                       │
          ┌────────────▼────────────┐
          │  Stage 1: ColBERT       │
          │  Semantic Re-Scoring   │
          │ (embedding similarity) │
          └────────────┬────────────┘
                       │ (updated scores)
          ┌────────────▼────────────┐
          │  Stage 2: Diversity     │
          │  Penalty                │
          │ (penalize redundant)    │
          └────────────┬────────────┘
                       │ (penalized scores)
          ┌────────────▼────────────┐
          │  Stage 3: LLM Judge     │
          │  Final Ranking          │
          │ (LLM relevance scoring) │
          └────────────┬────────────┘
                       │
┌──────────────────────▼──────────────────────────────────────────┐
│  Final Ranked Results (to RAG LLM)                              │
│  [1.0] doc_most_relevant  [0.9] doc_secondary  [0.8] doc_third │
└─────────────────────────────────────────────────────────────────┘
```

### Stage 1: ColBERT Semantic Re-Scoring
- Embeds both the query and each result document using the configured embedding model
- Computes cosine similarity between query and document embeddings
- Replaces original retriever scores with semantic similarity scores
- Results normalized to [0.0, 1.0] range

### Stage 2: Diversity Penalty
- Reduces scores for documents that are similar to higher-ranked results
- Uses embedding similarity to detect redundant content
- Configurable similarity threshold (default: 0.8) and penalty factor (default: 0.7)
- Prevents the final context from being dominated by similar documents

### Stage 3: LLM-as-a-Judge
- Uses the configured chat model to rank documents by relevance to the query
- Sends a prompt asking the LLM which documents are most relevant
- Parses LLM response to extract document IDs in ranked order
- Assigns final scores based on LLM-judged rank position (1.0 → 0.9 → 0.8 → …)
- Provides fine-grained relevance assessment beyond pure embeddings

### Configuration

The reranker pipeline is configured in `Main.java`:

```java
RerankerPort reranker = new RerankerPipelineAdapter(
    embeddingModel,           // for ColBERT embeddings
    chatModel,                // for LLM judge
    0.8,                      // diversity similarity threshold
    0.7,                      // diversity penalty multiplier
    true                      // enable LLM judge (set false to skip stage 3)
);

List<RetrievalResult> reranked = reranker.rerank(query, composableResults);
```

---

## Prerequisites

| Requirement | Version |
|---|---|
| Java (JDK) | 21+ |
| Maven | 3.9+ |
| OpenAI API key | `text-embedding-3-small` + `gpt-4o-mini` |

---

## Quick start

### 1 — Set your API key

```bash
export OPENAI_API_KEY=sk-...
```

### 2 — Build

```bash
mvn package -DskipTests
```

### 3 — Start ChromaDB

```bash
docker compose up -d
```

The app is configured to connect to `http://localhost:8000` by default via `vectorstore.chroma.base-url`.

### 4 — Run the demo

```bash
mvn -q compile exec:java -Dexec.mainClass=io.forest.composableretrieval.app.Main
```

Expected output (abbreviated):

```
Indexing 5 documents …

── Retrieval results for: "What is composable retrieval and RAG?" ──
  [1.000] Retrieval-Augmented Generation (RAG) grounds LLM responses …
  [0.500] Composable retrievers combine multiple data sources …
  [0.333] LangChain4J is a Java library for building LLM-powered …

── RAG answer ──
Composable retrieval refers to … [LLM answer using the retrieved context]
```

### 4 — Run unit tests (no API key needed)

```bash
mvn test
```

---

## Project structure

```
src/
├── main/java/com/example/
│   ├── app/
│   │   └── Main.java                          # Demo entry point
│   ├── core/
│   │   ├── port/
│   │   │   ├── RetrieverPort.java             # Inbound port
│   │   │   ├── RerankerPort.java              # Outbound port (reranking)
│   │   │   ├── EmbeddingsPort.java            # Outbound port
│   │   │   ├── VectorStorePort.java           # Outbound port
│   │   │   └── DocumentIngestionPort.java     # Outbound port
│   │   ├── ComposableRetriever.java           # Core domain (pure Java)
│   │   └── RagService.java                    # Application service (LC4J RAG)
│   └── adapters/
│       ├── ChromaEmbeddingStore               # EmbeddingStore<TextSegment> via ChromaDB
│       ├── EmbeddingStoreRetrieverAdapter.java# RetrieverPort + ContentRetriever
│       ├── RetrieverPortContentRetrieverBridge.java # Core ↔ LC4J bridge
│       ├── RerankerPipelineAdapter.java       # Three-stage reranking (ColBERT + Diversity + LLM Judge)
│       ├── DocumentIngestionAdapter.java      # DocumentIngestionPort impl
│       ├── InMemoryRetrieverAdapter.java      # Example lexical retriever
│       └── FileSystemRetrieverAdapter.java    # Example file retriever
└── test/java/com/example/
    ├── core/ComposableRetrieverTest.java          # Pure-Java unit tests (no I/O)
    ├── app/AppConfigTest.java                     # Config loading tests
    └── adapters/RerankerPipelineAdapterTest.java  # Reranker stage tests
```

---

## Extending

### Add a second retriever (e.g. a second Chroma collection or file store)

```java
RetrieverPort fileRetriever = new MyFileRetrieverAdapter("path/to/docs");
ComposableRetriever composable = new ComposableRetriever(
    List.of(chromaRetriever, fileRetriever));
```

### Swap in a different embedding model

```java
EmbeddingModel model = OllamaEmbeddingModel.builder()
    .baseUrl("http://localhost:11434")
    .modelName("nomic-embed-text")
    .build();
LangChain4JEmbeddingsAdapter embedder = new LangChain4JEmbeddingsAdapter(model);
```

### Customize reranking parameters

The `RerankerPipelineAdapter` supports configurable reranking:

```java
// Disable LLM judge, use only ColBERT + Diversity
RerankerPort reranker = new RerankerPipelineAdapter(
    embeddingModel,
    chatModel,
    0.75,   // lower threshold = stricter diversity penalty
    0.5,    // lower factor = stronger penalty for similar docs
    false   // disable LLM judge
);
```

Or use your own `RerankerPort` implementation:

```java
public class CustomReranker implements RerankerPort {
    @Override
    public List<RetrievalResult> rerank(String query, List<RetrievalResult> results) {
        // Your custom ranking logic here
        return results.stream()
            .sorted(/* custom comparator */)
            .toList();
    }
}
    .build();
```

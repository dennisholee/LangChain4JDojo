package io.forest.langchain4j.hybridrag;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.github.GitHubDocumentLoader;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.http.client.jdk.JdkHttpClient;
import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.opensearch.OpenSearchEmbeddingStore;
import org.apache.hc.core5.http.HttpHost;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5Transport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.util.List;

public class Application {

    final static Logger log = LoggerFactory.getLogger(Application.class);

    public static void main(String[] args) throws Exception {

        log.info("Setup GitHub documentloader");

        GitHubDocumentLoader gitHubDocumentLoader = GitHubDocumentLoader.builder()
            .gitHubToken(System.getenv("GITHUB_TOKEN"))
            .build();

        GitHubRepo gitHubRepo = new GitHubRepo("dennisholee", "ArchitectureDojo", "main");

        List<Document> documents = gitHubDocumentLoader.loadDocuments(
            gitHubRepo.owner(),
            gitHubRepo.repo(),
            gitHubRepo.branch(),
            "DDD",
            new TextDocumentParser()
        );

        log.info("Setup Open Search connection.");

        OpenSearchConnection openSearchConnection = new OpenSearchConnection("http", "localhost", 9200);

        HttpHost httpHost = new HttpHost(
            openSearchConnection.scheme(),
            openSearchConnection.hostname(),
            openSearchConnection.port());

        ApacheHttpClient5Transport apacheHttpClient5Transport = ApacheHttpClient5TransportBuilder.builder(httpHost)
            .setMapper(new JacksonJsonpMapper())
            .build();

        log.info("Create Open Search index.");

        OpenSearchClient openSearchClient = new OpenSearchClient(apacheHttpClient5Transport);

        String index = "sample-index";

        // https://github.com/opensearch-project/opensearch-java/blob/main/guides/plugins/knn.md
        // The all-MiniLM-L6-v2 embedding model produces vectors with 384 dimensions
        int dimensions = 384;

        /**
         * PUT http://localhost:9200/sample-index
         * {
         *   "settings": {
         *     "index": {
         *         "number_of_shards": 1,
         *         "number_of_replicas": 0,
         *         "knn": true,
         *         "knn.algo_param.ef_search": 100
         *     }
         *   },
         *   "mappings": {
         *     "properties": {
         *       "vector": {
         *         "type": "knn_vector",
         *         "dimension": 384,
         *         "method": {
         *           "name": "hnsw",
         *           "engine": "faiss",
         *           "space_type": "cosinesimil",
         *           "parameters": {
         *             "ef_construction": 100,
         *             "m": 16
         *           }
         *         }
         *       },
         *       "text": { "type": "text" },
         *       "metadata": { "type": "object" }
         *     }
         *   }
         * }
         */
        openSearchClient.indices().create(r -> r
            .index(index)
            .settings(s -> s.knn(true)
                .numberOfShards(1)
                .numberOfReplicas(0)
                .knnAlgoParamEfSearch(100))
            .mappings(m -> m
                .properties("vector", p -> p
                    .knnVector(k -> k.dimension(dimensions)
                        .method(a -> a.name("hnsw")
                            .engine("faiss"))
                    ))
                .properties("text", p -> p
                    .text(t -> t
                        .analyzer("standard")))
            ));

        /**
         * GET http://localhost:9200/sample-index/_mapping
         *
         * {
         *     "sample-index": {
         *         "mappings": {
         *             "properties": {
         *                 "metadata": {
         *                     "type": "object"
         *                 },
         *                 "text": {
         *                     "type": "text"
         *                 },
         *                 "vector": {
         *                     "type": "knn_vector",
         *                     "dimension": 384,
         *                     "method": {
         *                         "engine": "faiss",
         *                         "space_type": "cosinesimil",
         *                         "name": "hnsw",
         *                         "parameters": {
         *                             "ef_construction": 100,
         *                             "m": 16
         *                         }
         *                     }
         *                 }
         *             }
         *         }
         *     }
         * }
         */
        log.info("Create Embedding store.");

        EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();

        EmbeddingStore<TextSegment> embeddingStore = OpenSearchEmbeddingStore.builder()
            .serverUrl("http://localhost:9200")
            .indexName(index)
            .build();

        EmbeddingStoreIngestor embeddingStoreIngestor = EmbeddingStoreIngestor.builder()
            .embeddingModel(embeddingModel)
            .embeddingStore(embeddingStore)
            .build();

        embeddingStoreIngestor.ingest(documents);

        log.info("Setup LM Studio connection.");

        HttpClient.Builder clientBuilder = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1); // Force HTTP/1.1

        JdkHttpClientBuilder jdkHttpClientBuilder = JdkHttpClient.builder()
            .httpClientBuilder(clientBuilder);

        ChatModel chatModel = OpenAiChatModel.builder()
            .apiKey("lm-studio")
            .baseUrl("http://192.168.1.17:1234/v1")
            .apiKey("lm-studio")
            .modelName("phi-3-mini-4k-instruct")
            .httpClientBuilder(jdkHttpClientBuilder)
            .build();

        /**
         * PUT http://localhost:9200/_search/pipeline/rrf-pipeline
         *
         * {
         *   "description": "Post processor for hybrid RRF search",
         *   "phase_results_processors": [
         *     {
         *       "score-ranker-processor": {
         *         "combination": {
         *           "technique": "rrf"
         *         }
         *       }
         *     }
         *   ]
         * }
         */
        HybridContentRetriever hybridContentRetriever = new HybridContentRetriever(
            openSearchClient,
            embeddingModel,
            index
        );

        Assistant assistant = AiServices.builder(Assistant.class)
            .chatModel(chatModel)
            .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
            .contentRetriever(hybridContentRetriever)
            .build();

        String answer = assistant.chat("Recommend a DDD java folder structure?");

        System.out.println(answer);
    }
}

interface Assistant {
    String chat(String userMessage);
}
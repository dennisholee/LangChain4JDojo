package io.forest.langchain4j.hybridrag;

import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.query_dsl.MatchQuery;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public record HybridContentRetriever(OpenSearchClient client, EmbeddingModel embeddingModel,
                                     String indexName) implements ContentRetriever {

    private final static Logger log = LoggerFactory.getLogger(HybridContentRetriever.class);

    @Override
    public List<Content> retrieve(final Query query) {
        try {
            List<org.opensearch.client.opensearch._types.query_dsl.Query> queries = new ArrayList<>();

            MatchQuery matchQuery = new MatchQuery.Builder()
                .field("text")
                .query(FieldValue.of(query.text()))
                .build();
            queries.add(org.opensearch.client.opensearch._types.query_dsl.Query.of(q -> q.match(matchQuery)));

            SearchResponse<ObjectNode> searchResponse = client.search(s -> s
                    .index(this.indexName)
                    .query(q -> q.hybrid(h -> h
                        .queries(
                            textQuery -> textQuery.match(m -> m
                                .field("text")
                                .query(FieldValue.of(query.text()))
                                .queryName("textQuery"))
                        )
                        .queries(
                            knnQuery -> knnQuery.knn(kn -> kn
                                .field("vector")
                                .vector(this.embeddingModel.embed(query.text()).content().vectorAsList())
                                .k(10)
                                .queryName("knnQuery"))
                        )
                    ))
                , ObjectNode.class);

            return searchResponse.hits()
                .hits()
                .stream()
                //.map(Document::toTextSegment)
                .map(it -> {
                    ObjectNode source = it.source();
                    String text = source.has("text") ? source.get("text").asText() : "";

                    List<String> strings = it.matchedQueries();
                    log.info("---- textQuery={} knnQuery={}", strings.contains("textQuery"), strings.contains("knnQuery"));

                    return Content.from(text);
                })

                .toList();
        } catch (IOException e) {
            throw new RuntimeException("Failed to retrieve content from OpenSearch", e);
        }
    }
}

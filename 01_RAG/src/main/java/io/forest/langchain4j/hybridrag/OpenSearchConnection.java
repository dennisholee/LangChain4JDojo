package io.forest.langchain4j.hybridrag;

public record OpenSearchConnection(
    String scheme,
    String hostname,
    int port
) {
}

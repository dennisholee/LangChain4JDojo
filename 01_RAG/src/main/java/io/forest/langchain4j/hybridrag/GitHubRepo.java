package io.forest.langchain4j.hybridrag;

public record GitHubRepo(
    String owner,
    String repo,
    String branch) {
}

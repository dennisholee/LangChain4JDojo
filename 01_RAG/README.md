# langchain4j-rag-demo

A small Java (Maven) demo that shows a hybrid RAG pipeline built with langchain4j components: it loads GitHub documents, indexes them into OpenSearch with embeddings, and runs a hybrid (text + k-NN) retrieval combined with a chat model.

Repository layout

- `pom.xml` — Maven build configuration
- `src/main/java/io/forest/langchain4j/rag/` — demo sources
  - `Application.java` — demo main program
  - `GitHubRepo.java` — lightweight record for GitHub repo coordinates
  - `HybridContentRetriever.java` — hybrid retrieval against OpenSearch
  - `OpenSearchConnection.java` — simple OpenSearch connection record
- `target/` — Maven build output (created after running Maven)

Purpose

This repository demonstrates how to:
- Load documents from a GitHub repository using `GitHubDocumentLoader`.
- Create an OpenSearch index suitable for vector search.
- Produce embeddings with an EmbeddingModel and ingest them into an OpenSearch embedding store.
- Perform a hybrid search (text match + vector k-NN) and feed results to a chat model.

Prerequisites

- JDK 21 installed and JAVA_HOME set to JDK 21 (required to compile with project settings).
  - Check with: `java --version` and `mvn -v`.
- Apache Maven (3.8.x or later recommended).
- Local OpenSearch instance reachable at `localhost:9200` (assumed) with knn plugin enabled if using the OpenSearch k-NN mapping shown in `Application.java`.
- A GitHub token with permission to read the target repo, exported as `GITHUB_TOKEN` environment variable.
- Optional: LM Studio or equivalent LLM endpoint if you want to run the chat part. The demo currently uses a hard-coded LM Studio base URL and a placeholder API key value `lm-studio` inside the code; see Assumptions section.

Environment variables

- `GITHUB_TOKEN` (required for GitHub document loader): set to a personal access token with repo read access.
- `JAVA_HOME` (should point to JDK 21).
- (Optional) `LM_STUDIO_BASE_URL` / `LM_STUDIO_API_KEY` — suggested variables for a real LM Studio endpoint; the current demo contains a hard-coded base URL and API key string. To use environment-driven configuration you will need to edit `Application.java` to read these environment variables.

How to build

1. From the project root, compile the project:

```bash
mvn -q -DskipTests package
```

This creates classes under `target/classes` and a JAR under `target/` (note: dependencies are not assembled into a fat JAR by default).

How to run the demo (recommended via Maven exec plugin)

Run the `Application` main class using the exec plugin (no changes to `pom.xml` needed):

```bash
# macOS (zsh) example, set GITHUB_TOKEN inline (or `export` it first):
GITHUB_TOKEN=YOUR_GITHUB_TOKEN mvn -q org.codehaus.mojo:exec-maven-plugin:3.1.0:java -Dexec.mainClass=io.forest.langchain4j.hybridrag.Application
```

Notes:
- The application expects an OpenSearch instance at `http://localhost:9200` by default. If your OpenSearch instance is elsewhere, either run it on localhost:9200 or modify `Application.java` to supply a different `OpenSearchConnection`.
- The code uses an embedding model (`AllMiniLmL6V2EmbeddingModel`) which may require native dependencies or models to be available locally. Ensure the runtime environment can load this model as per `langchain4j` docs.

How to generate Javadoc HTML

Method A — using Maven (recommended):

```bash
# Ensure JDK 21 is active
mvn -q javadoc:javadoc
# The generated site will be at:
#   target/site/apidocs/index.html
# If you want to copy it into the repository for GitHub Pages:
cp -R target/site/apidocs docs/apidocs
```

Method B — using the `javadoc` tool directly:

```bash
# Generate into docs/apidocs (requires JDK 21 in PATH/JAVA_HOME)
javadoc --release 21 -d docs/apidocs -sourcepath src/main/java -subpackages io.forest.langchain4j.hybridrag -encoding UTF-8
# Open docs/apidocs/index.html to view
```

Troubleshooting

- If you see compilation or runtime errors around Java version, ensure `JAVA_HOME` is set to a JDK 21 installation and that `mvn -v` shows Java 21.
- If OpenSearch fails when creating the index, verify OpenSearch is running and that any required k-NN plugin is enabled for vector mapping.
- If embeddings fail to initialize, check `langchain4j` embedding provider documentation for required model files/runtime steps.

License

This repository is provided as a demo. Check repository-level license or add one if you intend to publish.

RalphLoop — Planning + Review Loop (LangChain4J/AI Agents)

Overview
--------
RalphLoop is a small Java demo that wires together an AI-driven planning and review loop using LangChain4J-style services and a state-graph workflow (org.bsc.langgraph4j). The loop consists of:

- PlannerAgent: Generates or refines a `Plan` from textual requirements.
- PlanReviewerAgent: Audits the plan for architecture, build configuration, and execution readiness, returning a `PlanReview`.
- (Optional) CoderAgent: Requests code artifacts for tasks and can write files using `CodeWriterTool`.

The `Application` class demonstrates how to build, compile, and invoke the workflow with a sample requirement payload and prints the resulting `Plan` as pretty JSON.

Project structure
-----------------
- src/main/java/io/forest/ralphloop — core runtime (Application, RalphLoopAgent, State)
- src/main/java/io/forest/ralphloop/agent — agent implementations (PlannerAgent, PlanReviewerAgent, CoderAgent)
- src/main/java/io/forest/ralphloop/model — simple data records (Plan, Task, PlanReview)
- src/main/java/io/forest/ralphloop/tool — helper tools (CodeWriterTool)

Note: Inline Javadoc comments were added to the Java source files; use your IDE or `mvn javadoc:javadoc` to generate HTML Javadoc if desired.

Requirements
------------
- Java 17+ (records used)
- Maven (pom.xml present in repository root)
- An OpenAI-compatible local or remote endpoint (the code points to http://localhost:1234/v1 by default and uses apiKey "lm-studio").

Build & Run
-----------
1. Build with Maven:

```bash
mvn -DskipTests package
```

2. Run the sample Application (replace the jar and classpath if you changed artifact ids):

```bash
mvn -q -DskipTests exec:java -Dexec.mainClass="io.forest.ralphloop.Application"
```

Notes on AI endpoint
--------------------
- The agents are configured to contact a local LLM server at http://localhost:1234/v1 with the API key "lm-studio" and use model names such as "phi-3-mini-4k-instruct".
- You can change the endpoint, apiKey, or modelName inside `PlannerAgent`, `PlanReviewerAgent`, and `CoderAgent` constructors.
- The `UserMessage` prompts enforce strict JSON output shapes — the downstream code deserializes that output into records (e.g., `Plan`, `PlanReview`). If the LLM returns invalid JSON, the agents may fail at runtime.

Safety & Caution
----------------
- `CodeWriterTool` writes files to the filesystem and will overwrite files at the same path. Use it carefully, especially when running the coder agent on a working repo.

Extending the project
---------------------
- Add a real AI key and endpoint or mock/stub the AiServices for unit tests.
- Implement unit tests under `src/test/java` to exercise agents and the graph.
- Add integration tests that spin up a local LLM stub (or WireMock) to simulate responses.

License
-------
No license file included. Add one if you plan to open-source this repository.

package io.forest.ralphloop;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.StateGraph;

import java.util.Map;

/**
 * Simple entry-point demonstrating how to construct and run the RalphLoop state graph.
 *
 * <p>The `main` method builds the {@link RalphLoopAgent} workflow, compiles the graph,
 * and invokes it with a sample multiline requirement text. When the graph completes
 * successfully the generated {@code Plan} (if present) is printed as pretty JSON to stdout.
 */
public class Application {

    /**
     * Sample program entry point.
     *
     * @param args command-line arguments (ignored)
     * @throws Exception on graph construction or invocation errors
     */
    public static void main(String[] args) throws Exception {

        RalphLoopAgent loopAgent = new RalphLoopAgent();

        StateGraph<State> stateGraph = loopAgent.buildLoop();

        CompiledGraph<State> compiledGraph = stateGraph.compile();

        compiledGraph.invoke(Map.of(
            "requirement",
            """
                Java Test Harness Requirements: Integration Testing
                1. Parameterized Input & Execution
                External Data Binding: Support for JSON, YAML, and CSV sources to decouple test logic from input values.
                Junit 5 / TestNG Native Support: Use of @ParameterizedTest or @DataProvider for running single test cases across multiple datasets.
                Dynamic Context Injection: Ability to override environment-specific variables (URLs, API keys) at runtime.
                Scenario-Based Flows: Support for multi-step test sequences where output from step A becomes input for step B.
                2. Service Stubbing & Mocking
                Out-of-Process Stubbing: Integration with WireMock or MockServer to simulate downstream REST/gRPC dependencies.
                Stateful Behaviors: Ability to configure stubs that change responses based on previous calls (e.g., simulating a "Created" state).
                Fault Injection: Simulation of network latency, connection timeouts, and 5xx errors to test circuit breakers.
                Message Broker Emulation: Support for mocking asynchronous events (Kafka, RabbitMQ) using Testcontainers.
                3. Data Synthesis & Management
                Faker Integration: Use of libraries like Datafaker to generate realistic, non-repeating PII (names, addresses, IDs).
                Schema-Aware Generation: Automated payload creation based on OpenAPI, Swagger, or Avro/Protobuf definitions.
                Database Containerization: Lifecycle management of ephemeral databases (PostgreSQL, MongoDB) via Testcontainers.
                Referential Integrity: Logic to maintain consistent IDs across different synthesized datasets and service calls.
                4. Observability & Reporting
                Trace Propagation: Support for passing trace-id headers to ensure stubs appear in distributed tracing (Jaeger/Zipkin).
                Payload Logging: Automatic capture of request/response bodies for failed assertions to speed up debugging.
                Standardized Output: Generation of Allure or Surefire reports for CI/CD integration.               
                """
        )).ifPresentOrElse(
            it -> {
                ObjectMapper mapper = new ObjectMapper();

                it.getPlan().ifPresent(plan -> {
                    try {
                        String prettyJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(plan);

                        System.out.println(prettyJson);
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                });
            },
            () -> System.out.println("err")
        );

    }
}

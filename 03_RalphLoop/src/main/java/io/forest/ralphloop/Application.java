package io.forest.ralphloop;

import io.forest.ralphloop.agent.BuiltInAgentProviders;
import io.forest.ralphloop.builder.WorkflowBuilder;
import io.forest.ralphloop.config.WorkflowConfig;
import io.forest.ralphloop.config.WorkflowLoader;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.StateGraph;

import java.util.HashMap;
import java.util.Map;

/**
 * Entry point demonstrating how to load a workflow from YAML configuration and execute it.
 *
 * <p>The `main` method:
 * <ol>
 *   <li>Loads a workflow definition from YAML (architect-loop.yaml by default)</li>
 *   <li>Builds the StateGraph using WorkflowBuilder and the agent factory</li>
 *   <li>Compiles the graph</li>
 *   <li>Invokes it with a sample requirement</li>
 * </ol>
 *
 * <p>Users can override the workflow path via the RALPHLOOP_WORKFLOW_PATH environment variable.
 */
public class Application {

    private static final String DEFAULT_WORKFLOW = "workflows/architect-loop.yaml";

    /**
     * Program entry point demonstrating YAML-driven workflow execution.
     *
     * @param args command-line arguments (optional: workflow path as first argument)
     * @throws Exception on workflow loading, graph construction, or invocation errors
     */
    public static void main(String[] args) throws Exception {

        // Determine workflow path from args, environment, or default
        String workflowPath = args.length > 0 
            ? args[0] 
            : System.getenv().getOrDefault("RALPHLOOP_WORKFLOW_PATH", DEFAULT_WORKFLOW);

        System.out.println("Loading workflow from: " + workflowPath);

        // Load workflow configuration from YAML
        WorkflowConfig config = WorkflowLoader.loadFromClasspath(workflowPath);
        System.out.println("✓ Loaded workflow: " + config.name());

        // Build StateGraph from configuration
        WorkflowBuilder builder = new WorkflowBuilder(config, new BuiltInAgentProviders());
        StateGraph<State> stateGraph = builder.build();
        System.out.println("✓ Built state graph with " + config.nodes().size() + " nodes");

        // Compile the graph
        CompiledGraph<State> compiledGraph = stateGraph.compile();
        System.out.println("✓ Compiled graph for execution");

        // Invoke with sample requirement
        String requirement = """
            * Author a Comprehensive API Design Guideline that serves as the "North Star" for engineering teams. 
            * This document must define how services interact across these layers to ensure data integrity, speed, and compliance.
            """;

        System.out.println("\n" + "=".repeat(70));
        System.out.println("Invoking workflow with requirement:");
        System.out.println("=".repeat(70));
        System.out.println(requirement);
        System.out.println("=".repeat(70) + "\n");

        compiledGraph.invoke(Map.of(
            "requirement", requirement,
            "maxIterations", config.getMaxIterationsOrDefault()
        )).ifPresentOrElse(
            state -> {
                System.out.println("\n" + "=".repeat(70));
                System.out.println("Workflow completed successfully");
                System.out.println("=".repeat(70));
                
                state.getDocument().ifPresent(doc -> {
                    System.out.println("\nGenerated Document:");
                    System.out.println("-".repeat(70));
                    System.out.println(doc);
                });
                
                state.getPlanReview().ifPresent(review -> {
                    System.out.println("\nFinal Review:");
                    System.out.println("-".repeat(70));
                    System.out.println("Valid: " + review.isValid());
                    System.out.println("Feedback: " + review.feedback());
                    if (!review.missingElements().isEmpty()) {
                        System.out.println("Missing Elements: " + review.missingElements());
                    }
                });

                printFlowMetricsSummary(state);
                printTokenMetricsSummary(state);
            },
            () -> System.out.println("Workflow invocation failed or returned no result")
        );
    }

    @SuppressWarnings("unchecked")
    private static void printFlowMetricsSummary(State state) {
        Object rawFlowMetrics = state.value("flowMetrics").orElse(null);
        if (!(rawFlowMetrics instanceof Map<?, ?> rawMap)) {
            return;
        }

        Map<String, Object> flowMetrics = (Map<String, Object>) rawMap;
        if (flowMetrics.isEmpty()) {
            return;
        }

        long totalSteps = longValue(flowMetrics.get("totalSteps"));
        long successfulSteps = longValue(flowMetrics.get("successfulSteps"));
        long failedSteps = longValue(flowMetrics.get("failedSteps"));
        long totalDurationMs = longValue(flowMetrics.get("totalDurationMs"));
        long iterationCount = longValue(state.data().get("iterationCount"));
        long maxIterations = longValue(state.data().get("maxIterations"));
        boolean iterationLimitReached = maxIterations > 0 && iterationCount >= maxIterations;

        System.out.println("\nFlow Summary:");
        System.out.println("-".repeat(70));
        System.out.println("Total steps:      " + totalSteps);
        System.out.println("Successful steps: " + successfulSteps);
        System.out.println("Failed steps:     " + failedSteps);
        System.out.println("Total duration:   " + totalDurationMs + " ms");
        System.out.println("Iteration count:  " + iterationCount);
        System.out.println("Iteration limit:  " + (maxIterations > 0 ? maxIterations : "N/A"));
        System.out.println("Limit reached:    " + iterationLimitReached);

        Object rawSteps = flowMetrics.get("steps");
        if (rawSteps instanceof Iterable<?> iterable) {
            System.out.println("\nFlow Steps:");
            for (Object item : iterable) {
                if (!(item instanceof Map<?, ?> stepRaw)) {
                    continue;
                }
                Map<String, Object> step = (Map<String, Object>) stepRaw;
                String line = "- #" + longValue(step.get("index"))
                    + " node=" + String.valueOf(step.getOrDefault("nodeId", "?"))
                    + " status=" + String.valueOf(step.getOrDefault("status", "unknown"))
                    + " iter=" + longValue(step.get("iterationBefore")) + "->" + longValue(step.get("iterationAfter"))
                    + " duration=" + longValue(step.get("durationMs")) + "ms";
                if (step.containsKey("error")) {
                    line += " error=\"" + String.valueOf(step.get("error")) + "\"";
                }
                System.out.println(line);
            }
        }

        Object rawByNode = flowMetrics.get("byNode");
        if (rawByNode instanceof Map<?, ?> byNodeRaw && !byNodeRaw.isEmpty()) {
            System.out.println("\nExecution Summary by Node:");
            for (Map.Entry<?, ?> entry : byNodeRaw.entrySet()) {
                if (!(entry.getValue() instanceof Map<?, ?> summaryRaw)) {
                    continue;
                }
                Map<String, Object> summary = (Map<String, Object>) summaryRaw;
                System.out.println("- " + String.valueOf(entry.getKey())
                    + " | executions=" + longValue(summary.get("executions"))
                    + ", success=" + longValue(summary.get("successes"))
                    + ", errors=" + longValue(summary.get("errors"))
                    + ", avgDuration=" + longValue(summary.get("avgDurationMs")) + "ms");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void printTokenMetricsSummary(State state) {
        Object rawMetrics = state.value("tokenMetrics").orElse(null);
        if (!(rawMetrics instanceof Map<?, ?> rawMap)) {
            return;
        }

        Map<String, Object> metrics = (Map<String, Object>) rawMap;
        if (metrics == null || metrics.isEmpty()) {
            return;
        }

        long inputBefore = longValue(metrics.get("totalEstimatedInputTokensBefore"));
        long inputAfter = longValue(metrics.get("totalEstimatedInputTokensAfter"));
        long inputSaved = longValue(metrics.get("totalEstimatedInputTokensSaved"));
        long summaryCalls = longValue(metrics.get("totalSummaryCalls"));
        long summaryInput = longValue(metrics.get("totalSummaryInputTokens"));
        long summaryOutput = longValue(metrics.get("totalSummaryOutputTokens"));
        long promptTokens = longValue(metrics.get("totalPromptTokens"));
        long outputTokens = longValue(metrics.get("totalModelOutputTokens"));
        long compressedFields = longValue(metrics.get("totalCompressedFields"));
        long fallbackTruncations = longValue(metrics.get("totalFallbackTruncations"));

        System.out.println("\nToken Metrics Summary:");
        System.out.println("-".repeat(70));
        System.out.println("Estimated input tokens before compression: " + inputBefore);
        System.out.println("Estimated input tokens after compression:  " + inputAfter);
        System.out.println("Estimated token savings:                  " + inputSaved);
        System.out.println("Prompt tokens sent to primary model:      " + promptTokens);
        System.out.println("Estimated primary model output tokens:    " + outputTokens);
        System.out.println("Summary model calls:                      " + summaryCalls);
        System.out.println("Estimated summary input tokens:           " + summaryInput);
        System.out.println("Estimated summary output tokens:          " + summaryOutput);
        System.out.println("Compressed fields:                        " + compressedFields);
        System.out.println("Fallback truncations:                     " + fallbackTruncations);

        Map<String, Object> byAgent = (Map<String, Object>) metrics.getOrDefault("byAgent", new HashMap<>());
        if (!byAgent.isEmpty()) {
            System.out.println("\nBy Agent:");
            for (Map.Entry<String, Object> entry : byAgent.entrySet()) {
                Map<String, Object> agentMetrics = entry.getValue() instanceof Map<?, ?> m
                    ? (Map<String, Object>) m
                    : Map.of();
                System.out.println("- " + entry.getKey()
                    + " | invocations=" + longValue(agentMetrics.get("invocations"))
                    + ", savedTokens=" + longValue(agentMetrics.get("estimatedInputTokensSaved"))
                    + ", summaryCalls=" + longValue(agentMetrics.get("summaryCalls")));
            }
        }
    }

    private static long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }
}

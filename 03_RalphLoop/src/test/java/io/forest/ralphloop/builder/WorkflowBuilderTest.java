package io.forest.ralphloop.builder;

import io.forest.ralphloop.State;
import io.forest.ralphloop.agent.AgentFactory;
import io.forest.ralphloop.agent.AgentProvider;
import io.forest.ralphloop.agent.BuiltInAgentProviders;
import io.forest.ralphloop.config.AgentProfileConfig;
import io.forest.ralphloop.config.WorkflowConfig;
import io.forest.ralphloop.config.WorkflowLoader;
import io.forest.ralphloop.model.PlanReview;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.NodeAction;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for YAML-driven workflow builder.
 * Validates that workflows can be loaded from YAML config and executed correctly.
 */
class WorkflowBuilderTest {

    private static final String SIMPLE_LOOP_YAML = """
        name: test-loop
        max_iterations: 3
        entry_node: planner
        nodes:
          - id: planner
            agent_type: planner
          - id: reviewer
            agent_type: reviewer
        edges:
          - source: START
            target: planner
          - source: planner
            target: END
            condition:
              type: error
          - source: planner
            target: reviewer
            condition:
              type: not_error
          - source: reviewer
            target: END
            condition:
              type: isValid
          - source: reviewer
            target: planner
            condition:
              type: not_isValid_and_within_iteration_limit
          - source: reviewer
            target: END
            condition:
              type: not_isValid_and_exceed_iteration_limit
        state_channels:
          - name: iterationCount
            type: base
            defaultValue: 0
            mergeStrategy: overwrite
        """;

    private static final String HITL_LOOP_YAML = String.join("\n",
        "name: hitl-loop",
        "max_iterations: 3",
        "entry_node: planner",
        "nodes:",
        "  - id: planner",
        "    agent_type: planner",
        "  - id: reviewer",
        "    agent_type: reviewer",
        "  - id: human_gate",
        "    agent_type: human_approver",
        "edges:",
        "  - source: START",
        "    target: planner",
        "  - source: planner",
        "    target: END",
        "    condition:",
        "      type: error",
        "  - source: planner",
        "    target: reviewer",
        "    condition:",
        "      type: not_error",
        "  - source: reviewer",
        "    target: human_gate",
        "    condition:",
        "      type: isValid",
        "  - source: reviewer",
        "    target: planner",
        "    condition:",
        "      type: not_isValid_and_within_iteration_limit",
        "  - source: reviewer",
        "    target: END",
        "    condition:",
        "      type: not_isValid_and_exceed_iteration_limit",
        "  - source: human_gate",
        "    target: END",
        "    condition:",
        "      type: human_approved",
        "  - source: human_gate",
        "    target: planner",
        "    condition:",
        "      type: human_rework_and_within_iteration_limit",
        "  - source: human_gate",
        "    target: END",
        "    condition:",
        "      type: human_rework_and_exceed_iteration_limit",
        "  - source: human_gate",
        "    target: END",
        "    condition:",
        "      type: human_rejected",
        "state_channels:",
        "  - name: iterationCount",
        "    type: base",
        "    defaultValue: 0",
        "    mergeStrategy: overwrite",
        "  - name: humanDecision",
        "    type: base",
        "    defaultValue: \"\"",
        "    mergeStrategy: overwrite",
        "  - name: humanFeedback",
        "    type: base",
        "    defaultValue: \"\"",
        "    mergeStrategy: overwrite"
    );

    @Test
    void canBuildGraphFromYamlConfig() throws Exception {
        WorkflowConfig config = WorkflowLoader.loadFromClasspath("workflows/architect-loop.yaml");

        assertNotNull(config);
        assertEquals("architect-loop", config.name());
        assertEquals(3, config.getMaxIterationsOrDefault());
        assertEquals(3, config.nodes().size());
        assertEquals(10, config.edges().size());

        WorkflowBuilder builder = new WorkflowBuilder(config, new BuiltInAgentProviders());
        StateGraph<State> graph = builder.build();

        assertNotNull(graph);
    }

    @Test
    void validatesNodeReferences() throws Exception {
        String invalidYaml = """
            name: invalid-workflow
            max_iterations: 3
            entry_node: start
            nodes:
              - id: node1
                agent_type: architect
            edges:
              - source: START
                target: nonexistent_node
            state_channels: []
            """;

        WorkflowConfig config = WorkflowLoader.loadFromString(invalidYaml);
        assertThrows(IllegalArgumentException.class, config::validate);
    }

    @Test
    void requiresAtLeastOneNode() {
        String invalidYaml = """
            name: no-nodes
            nodes: []
            edges: []
            """;

        assertThrows(Exception.class, () -> WorkflowLoader.loadFromString(invalidYaml));
    }

    @Test
    void canCompileAndInvokeWorkflow() throws Exception {
        WorkflowConfig config = WorkflowLoader.loadFromClasspath("workflows/architect-loop.yaml");

        WorkflowBuilder builder = new WorkflowBuilder(config, new BuiltInAgentProviders());
        StateGraph<State> graph = builder.build();

        CompiledGraph<State> compiled = graph.compile();
        assertNotNull(compiled);

        Map<String, Object> initialData = new HashMap<>();
        initialData.put("requirement", "Design an API for user management");
        State state = new State(initialData);
        assertNotNull(state);
    }

    @Test
    void canBuildMultipleWorkflows() throws Exception {
        WorkflowConfig config1 = WorkflowLoader.loadFromClasspath("workflows/architect-loop.yaml");

        WorkflowBuilder builder1 = new WorkflowBuilder(config1, new BuiltInAgentProviders());
        StateGraph<State> graph1 = builder1.build();

        WorkflowBuilder builder2 = new WorkflowBuilder(config1, new BuiltInAgentProviders());
        StateGraph<State> graph2 = builder2.build();

        assertNotNull(graph1);
        assertNotNull(graph2);

        CompiledGraph<State> compiled1 = graph1.compile();
        CompiledGraph<State> compiled2 = graph2.compile();
        assertNotNull(compiled1);
        assertNotNull(compiled2);
    }

    @Test
    void declarativeLoopStopsWhenReviewerAcceptsTheDraft() throws Exception {
        AgentFactory factory = new TestAgentFactory(
            state -> {
                Map<String, Object> data = new HashMap<>(state.data());
                data.put("document", "draft-v1");
                return data;
            },
            state -> {
                Map<String, Object> data = new HashMap<>(state.data());
                data.put("planReview", new PlanReview(true, "approved", List.of(), List.of()));
                return data;
            }
        );

        WorkflowConfig config = WorkflowLoader.loadFromString(SIMPLE_LOOP_YAML);
        State result = new WorkflowBuilder(config, factory)
            .build()
            .compile()
            .invoke(Map.of("requirement", "build a guideline"))
            .orElseThrow();

        assertEquals("draft-v1", result.data().get("document"));
        assertEquals(1, ((Number) result.data().get("iterationCount")).intValue());
        assertTrue(((PlanReview) result.data().get("planReview")).isValid());
    }

    @Test
    void declarativeLoopStopsAfterInvalidReviewsExceedIterationLimit() throws Exception {
        AtomicInteger plannerCalls = new AtomicInteger();

        AgentFactory factory = new TestAgentFactory(
            state -> {
                plannerCalls.incrementAndGet();
                Map<String, Object> data = new HashMap<>(state.data());
                data.put("document", "draft-" + plannerCalls.get());
                return data;
            },
            state -> {
                Map<String, Object> data = new HashMap<>(state.data());
                data.put("planReview", new PlanReview(false, "needs work", List.of("missing section"), List.of("add missing section")));
                return data;
            }
        );

        WorkflowConfig config = WorkflowLoader.loadFromString(SIMPLE_LOOP_YAML.replace("max_iterations: 3", "max_iterations: 2"));
        State result = new WorkflowBuilder(config, factory)
            .build()
            .compile()
            .invoke(Map.of("requirement", "build a guideline"))
            .orElseThrow();

        assertEquals(2, ((Number) result.data().get("iterationCount")).intValue());
        assertEquals(2, plannerCalls.get());
        assertEquals("draft-2", result.data().get("document"));
        assertFalse(((PlanReview) result.data().get("planReview")).isValid());
    }

    @Test
    void declarativeLoopRoutesPlannerExceptionsToFailedState() throws Exception {
        AgentFactory factory = new TestAgentFactory(
            state -> {
                throw new IllegalStateException("boom");
            },
            state -> {
                throw new AssertionError("reviewer should not be called when planner fails");
            }
        );

        WorkflowConfig config = WorkflowLoader.loadFromString(SIMPLE_LOOP_YAML);
        State result = new WorkflowBuilder(config, factory)
            .build()
            .compile()
            .invoke(Map.of("requirement", "build a guideline"))
            .orElseThrow();

        assertEquals(0, ((Number) result.data().get("iterationCount")).intValue());
        assertEquals("boom", result.data().get("error"));
        assertTrue(Boolean.TRUE.equals(result.data().get("terminated")));
    }

    @Test
    void architectWorkflowShapeUsesSameLoopSemantics() throws Exception {
        AgentFactory factory = new TestAgentFactory(
            state -> {
                Map<String, Object> data = new HashMap<>(state.data());
                data.put("document", "architect-draft-v1");
                return data;
            },
            state -> {
                Map<String, Object> data = new HashMap<>(state.data());
                data.put("planReview", new PlanReview(true, "approved", List.of(), List.of()));
                return data;
            },
            state -> {
                Map<String, Object> data = new HashMap<>(state.data());
                data.put("humanDecision", "approve");
                return data;
            }
        );

        String architectLoopYaml = SIMPLE_LOOP_YAML
            .replace("name: test-loop", "name: architect-loop-test")
            .replace("entry_node: planner", "entry_node: drafter")
            .replace("- id: planner", "- id: drafter")
            .replace("agent_type: planner", "agent_type: architect")
            .replace("source: planner", "source: drafter")
            .replace("target: planner", "target: drafter")
            .replace("agent_type: reviewer", "agent_type: architect_reviewer");

        WorkflowConfig config = WorkflowLoader.loadFromString(architectLoopYaml);
        State result = new WorkflowBuilder(config, factory)
            .build()
            .compile()
            .invoke(Map.of("requirement", "author API guideline"))
            .orElseThrow();

        assertEquals("architect-draft-v1", result.data().get("document"));
        assertEquals(1, ((Number) result.data().get("iterationCount")).intValue());
        assertTrue(((PlanReview) result.data().get("planReview")).isValid());
    }

    @Test
    void hitlLoopRoutesToEndWhenHumanApproves() throws Exception {
        AgentFactory factory = new TestAgentFactory(
            state -> {
                Map<String, Object> data = new HashMap<>(state.data());
                data.put("document", "draft-v1");
                return data;
            },
            state -> {
                Map<String, Object> data = new HashMap<>(state.data());
                data.put("planReview", new PlanReview(true, "approved", List.of(), List.of()));
                return data;
            },
            state -> {
                Map<String, Object> data = new HashMap<>(state.data());
                data.put("humanDecision", "approve");
                data.put("humanFeedback", "looks good");
                return data;
            }
        );

        WorkflowConfig config = WorkflowLoader.loadFromString(HITL_LOOP_YAML);
        State result = new WorkflowBuilder(config, factory)
            .build()
            .compile()
            .invoke(Map.of("requirement", "build a guideline"))
            .orElseThrow();

        assertEquals("approve", result.data().get("humanDecision"));
        assertEquals("looks good", result.data().get("humanFeedback"));
        assertEquals(2, ((Number) result.data().get("iterationCount")).intValue());
    }

    @Test
    void hitlLoopRoutesBackToPlannerWhenHumanRequestsRework() throws Exception {
        AtomicInteger plannerCalls = new AtomicInteger();
        AtomicInteger humanCalls = new AtomicInteger();

        AgentFactory factory = new TestAgentFactory(
            state -> {
                plannerCalls.incrementAndGet();
                Map<String, Object> data = new HashMap<>(state.data());
                data.put("document", "draft-" + plannerCalls.get());
                return data;
            },
            state -> {
                Map<String, Object> data = new HashMap<>(state.data());
                data.put("planReview", new PlanReview(true, "approved", List.of(), List.of()));
                return data;
            },
            state -> {
                humanCalls.incrementAndGet();
                Map<String, Object> data = new HashMap<>(state.data());
                if (humanCalls.get() == 1) {
                    data.put("humanDecision", "rework");
                    data.put("humanFeedback", "add security section");
                } else {
                    data.put("humanDecision", "approve");
                    data.put("humanFeedback", "ready");
                }
                return data;
            }
        );

        WorkflowConfig config = WorkflowLoader.loadFromString(HITL_LOOP_YAML);
        State result = new WorkflowBuilder(config, factory)
            .build()
            .compile()
            .invoke(Map.of("requirement", "build a guideline"))
            .orElseThrow();

        assertEquals(2, plannerCalls.get());
        assertEquals(2, humanCalls.get());
        assertEquals("approve", result.data().get("humanDecision"));
        assertEquals("draft-2", result.data().get("document"));
        assertEquals(4, ((Number) result.data().get("iterationCount")).intValue());
    }

    @Test
    void hitlLoopRoutesToEndWhenHumanRejects() throws Exception {
        AgentFactory factory = new TestAgentFactory(
            state -> {
                Map<String, Object> data = new HashMap<>(state.data());
                data.put("document", "draft-v1");
                return data;
            },
            state -> {
                Map<String, Object> data = new HashMap<>(state.data());
                data.put("planReview", new PlanReview(true, "approved", List.of(), List.of()));
                return data;
            },
            state -> {
                Map<String, Object> data = new HashMap<>(state.data());
                data.put("humanDecision", "reject");
                data.put("humanFeedback", "not aligned");
                return data;
            }
        );

        WorkflowConfig config = WorkflowLoader.loadFromString(HITL_LOOP_YAML);
        State result = new WorkflowBuilder(config, factory)
            .build()
            .compile()
            .invoke(Map.of("requirement", "build a guideline"))
            .orElseThrow();

        assertEquals("reject", result.data().get("humanDecision"));
        assertEquals("not aligned", result.data().get("humanFeedback"));
        assertEquals(2, ((Number) result.data().get("iterationCount")).intValue());
    }

    private static final class TestAgentFactory implements AgentFactory {

        private final NodeAction<State> planner;
        private final NodeAction<State> reviewer;
        private final NodeAction<State> humanApprover;

        private TestAgentFactory(NodeAction<State> planner, NodeAction<State> reviewer) {
            this(planner, reviewer, state -> {
                Map<String, Object> data = new HashMap<>(state.data());
                data.put("humanDecision", "approve");
                return data;
            });
        }

        private TestAgentFactory(NodeAction<State> planner, NodeAction<State> reviewer, NodeAction<State> humanApprover) {
            this.planner = planner;
            this.reviewer = reviewer;
            this.humanApprover = humanApprover;
        }

        @Override
        public NodeAction<State> createAgent(String agentType, AgentProfileConfig config) {
            return switch (agentType) {
                case "planner", "architect" -> planner;
                case "reviewer", "architect_reviewer" -> reviewer;
                case "human_approver" -> humanApprover;
                default -> throw new IllegalArgumentException("Unsupported agent type: " + agentType);
            };
        }

        @Override
        public boolean supports(String agentType) {
            return "planner".equals(agentType)
                || "reviewer".equals(agentType)
                || "architect".equals(agentType)
                || "architect_reviewer".equals(agentType)
                || "human_approver".equals(agentType);
        }

        @Override
        public void registerProvider(String agentType, AgentProvider provider) {
            throw new UnsupportedOperationException("Test factory does not support dynamic registration");
        }
    }
}

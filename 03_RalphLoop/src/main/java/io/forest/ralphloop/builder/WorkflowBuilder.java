package io.forest.ralphloop.builder;

import io.forest.ralphloop.State;
import io.forest.ralphloop.agent.AgentFactory;
import io.forest.ralphloop.config.EdgeConfig;
import io.forest.ralphloop.config.NodeConfig;
import io.forest.ralphloop.config.StateChannelConfig;
import io.forest.ralphloop.config.WorkflowConfig;
import io.forest.ralphloop.model.PlanReview;
import org.bsc.langgraph4j.GraphDefinition;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * Builds a LangGraph4j StateGraph from a WorkflowConfig.
 * Converts declarative workflow configuration into executable graph topology with nodes, edges, and state channels.
 */
public class WorkflowBuilder {

    private static final String FLOW_METRICS_KEY = "flowMetrics";
    private static final String FLOW_STEPS_KEY = "steps";
    private static final String FLOW_BY_NODE_KEY = "byNode";
    
    private final WorkflowConfig config;
    private final AgentFactory agentFactory;
    
    public WorkflowBuilder(WorkflowConfig config, AgentFactory agentFactory) {
        if (config == null) {
            throw new IllegalArgumentException("WorkflowConfig cannot be null");
        }
        if (agentFactory == null) {
            throw new IllegalArgumentException("AgentFactory cannot be null");
        }
        
        this.config = config;
        this.agentFactory = agentFactory;
    }
    
    /**
     * Builds the StateGraph from configuration.
     */
    public StateGraph<State> build() throws Exception {
        // Validate config before building
        config.validate();
        
        // Build channels map from config
        Map<String, Channel<?>> channels = buildChannels();
        
        // Create state graph
        StateGraph<State> graph = new StateGraph<>(channels, State::new);
        
        // Add all configured nodes to the graph
        addNodesToGraph(graph);
        
        // Add all configured edges to the graph
        addEdgesToGraph(graph);
        
        return graph;
    }
    
    /**
     * Builds the channels map from state channel configuration.
     */
    private Map<String, Channel<?>> buildChannels() {
        Map<String, Channel<?>> channels = new HashMap<>();
        
        if (config.stateChannels() != null) {
            for (StateChannelConfig channelConfig : config.stateChannels()) {
                Channel<?> channel = createChannel(channelConfig);
                channels.put(channelConfig.name(), channel);
            }
        }
        
        // Ensure iterationCount channel exists (used for loop control)
        if (!channels.containsKey("iterationCount")) {
            channels.put("iterationCount", Channels.base(() -> 0));
        }
        
        return channels;
    }
    
    /**
     * Creates a LangGraph4j Channel from configuration.
     */
    private Channel<?> createChannel(StateChannelConfig channelConfig) {
        return switch (channelConfig.type()) {
            case "base", "append", "reduce" -> 
                // All channel types default to base behavior in LangGraph4j 1.8.4
                Channels.base(() -> channelConfig.defaultValue());
            default -> throw new IllegalArgumentException("Unsupported channel type: " + channelConfig.type());
        };
    }
    
    /**
     * Adds nodes from config to the state graph.
     */
    private void addNodesToGraph(StateGraph<State> graph) throws Exception {
        for (NodeConfig nodeConfig : config.nodes()) {
            // Create agent action for this node
            NodeAction<State> agentAction = createAgentAction(nodeConfig);
            
            // Add to graph with iteration count tracking wrapper
            NodeAction<State> wrappedAction = wrapWithIterationTracking(nodeConfig.id(), agentAction, shouldTrackIteration(nodeConfig.id()));
            graph.addNode(nodeConfig.id(), node_async(wrappedAction));
        }
    }
    
    /**
     * Creates a NodeAction for a configured node.
     */
    private NodeAction<State> createAgentAction(NodeConfig nodeConfig) {
        String agentType = nodeConfig.agentType();
        
        if (!agentFactory.supports(agentType)) {
            throw new IllegalArgumentException("Unsupported agent type: " + agentType);
        }
        
        return agentFactory.createAgent(agentType, nodeConfig.agentProfile());
    }
    
    /**
     * Wraps a node action to track iteration count.
     */
    private NodeAction<State> wrapWithIterationTracking(String nodeId, NodeAction<State> action, boolean trackIteration) {
        return state -> {
            long startedAt = System.nanoTime();
            int currentIteration = ((Number) state.data().getOrDefault("iterationCount", 0)).intValue();
            try {
                // Execute the action
                Map<String, Object> result = action.apply(state);
                int nextIteration = trackIteration ? currentIteration + 1 : currentIteration;

                // Preserve iteration count in result and only advance it on loop-control nodes.
                result.put("iterationCount", nextIteration);
                appendFlowStep(result, nodeId, "success", trackIteration, currentIteration, nextIteration, startedAt, null);
                
                return result;
            } catch (Exception e) {
                int nextIteration = trackIteration ? currentIteration + 1 : currentIteration;
                Map<String, Object> result = new HashMap<>(state.data());
                result.put("iterationCount", nextIteration);
                result.put("error", e.getMessage());
                result.put("terminated", true);
                appendFlowStep(result, nodeId, "error", trackIteration, currentIteration, nextIteration, startedAt, e.getMessage());
                return result;
            }
        };
    }

    @SuppressWarnings("unchecked")
    private void appendFlowStep(
        Map<String, Object> result,
        String nodeId,
        String status,
        boolean trackedIteration,
        int iterationBefore,
        int iterationAfter,
        long startedAtNano,
        String errorMessage
    ) {
        long durationMs = (System.nanoTime() - startedAtNano) / 1_000_000;

        Map<String, Object> flowMetrics = (Map<String, Object>) result.get(FLOW_METRICS_KEY);
        if (flowMetrics == null) {
            flowMetrics = new HashMap<>();
        }

        List<Map<String, Object>> steps = (List<Map<String, Object>>) flowMetrics.get(FLOW_STEPS_KEY);
        if (steps == null) {
            steps = new java.util.ArrayList<>();
        }

        Map<String, Object> step = new HashMap<>();
        step.put("index", steps.size() + 1);
        step.put("nodeId", nodeId);
        step.put("status", status);
        step.put("trackedIteration", trackedIteration);
        step.put("iterationBefore", iterationBefore);
        step.put("iterationAfter", iterationAfter);
        step.put("durationMs", durationMs);
        if (errorMessage != null && !errorMessage.isBlank()) {
            step.put("error", errorMessage);
        }
        steps.add(step);

        Map<String, Object> byNode = (Map<String, Object>) flowMetrics.get(FLOW_BY_NODE_KEY);
        if (byNode == null) {
            byNode = new HashMap<>();
        }
        Map<String, Object> nodeSummary = (Map<String, Object>) byNode.get(nodeId);
        if (nodeSummary == null) {
            nodeSummary = new HashMap<>();
        }

        long executions = longValue(nodeSummary.get("executions")) + 1;
        long successes = longValue(nodeSummary.get("successes")) + ("success".equals(status) ? 1 : 0);
        long errors = longValue(nodeSummary.get("errors")) + ("error".equals(status) ? 1 : 0);
        long totalDurationMs = longValue(nodeSummary.get("totalDurationMs")) + durationMs;

        nodeSummary.put("executions", executions);
        nodeSummary.put("successes", successes);
        nodeSummary.put("errors", errors);
        nodeSummary.put("totalDurationMs", totalDurationMs);
        nodeSummary.put("avgDurationMs", executions > 0 ? totalDurationMs / executions : 0);
        byNode.put(nodeId, nodeSummary);

        flowMetrics.put(FLOW_STEPS_KEY, steps);
        flowMetrics.put(FLOW_BY_NODE_KEY, byNode);
        flowMetrics.put("totalSteps", steps.size());
        flowMetrics.put("totalDurationMs", longValue(flowMetrics.get("totalDurationMs")) + durationMs);
        flowMetrics.put("successfulSteps", longValue(flowMetrics.get("successfulSteps")) + ("success".equals(status) ? 1 : 0));
        flowMetrics.put("failedSteps", longValue(flowMetrics.get("failedSteps")) + ("error".equals(status) ? 1 : 0));
        flowMetrics.put("lastNode", nodeId);
        flowMetrics.put("lastStatus", status);

        result.put(FLOW_METRICS_KEY, flowMetrics);
    }

    private long longValue(Object value) {
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

    /**
     * Tracks iterations on nodes that control loop validity, so max_iterations maps to review cycles.
     */
    private boolean shouldTrackIteration(String nodeId) {
        return config.getEdgesFrom(nodeId).stream()
            .filter(EdgeConfig::isConditional)
            .map(edge -> edge.condition().type())
            .anyMatch(conditionType -> conditionType.equals("isValid")
                || conditionType.equals("not_isValid_and_within_iteration_limit")
                || conditionType.equals("not_isValid_and_exceed_iteration_limit")
                || conditionType.equals("human_rework_and_within_iteration_limit")
                || conditionType.equals("human_rework_and_exceed_iteration_limit"));
    }
    
    /**
     * Adds edges from config to the state graph.
     */
    private void addEdgesToGraph(StateGraph<State> graph) throws Exception {
        // Group conditional edges by source node
        Map<String, List<EdgeConfig>> conditionalEdgesBySource = new HashMap<>();
        List<EdgeConfig> unconditionalEdges = new java.util.ArrayList<>();
        
        for (EdgeConfig edgeConfig : config.edges()) {
            if (edgeConfig.isConditional()) {
                conditionalEdgesBySource.computeIfAbsent(edgeConfig.source(), k -> new java.util.ArrayList<>())
                    .add(edgeConfig);
            } else {
                unconditionalEdges.add(edgeConfig);
            }
        }
        
        // Add unconditional edges
        for (EdgeConfig edge : unconditionalEdges) {
            String target = edge.target().equals("END") ? GraphDefinition.END : edge.target();
            
            // Handle START edges
            if (edge.source().equals("START")) {
                graph.addEdge(GraphDefinition.START, target);
            } else {
                graph.addEdge(edge.source(), target);
            }
        }
        
        // Add conditional edges
        for (Map.Entry<String, List<EdgeConfig>> entry : conditionalEdgesBySource.entrySet()) {
            String source = entry.getKey();
            List<EdgeConfig> edges = entry.getValue();
            
            // Build router and targets map
            Map<String, String> targetsMap = new HashMap<>();
            for (EdgeConfig edge : edges) {
                String target = edge.target().equals("END") ? GraphDefinition.END : edge.target();
                targetsMap.put(edge.condition().type(), target);
            }
            
            // Create the router function
            graph.addConditionalEdges(source,
                edge_async(state -> routeEdge(state, source, edges, config.getMaxIterationsOrDefault())),
                targetsMap);
        }
    }
    
    /**
     * Routes an edge based on conditions and current state.
     */
    private String routeEdge(State state, String source, List<EdgeConfig> edges, int maxIterations) {
        for (EdgeConfig edge : edges) {
            String conditionType = edge.condition().type();
            boolean matches = evaluateCondition(state, conditionType, maxIterations);
            if (matches) {
                return conditionType;
            }
        }
        return null; // No condition matched
    }
    
    /**
     * Evaluates a single condition against the current state.
     */
    private boolean evaluateCondition(State state, String conditionType, int maxIterations) {
        return switch (conditionType) {
            case "error" -> state.data().containsKey("error");
            
            case "not_error" -> !state.data().containsKey("error");
            
            case "isValid" -> {
                PlanReview review = (PlanReview) state.data().get("planReview");
                yield review != null && review.isValid();
            }
            
            case "not_isValid_and_within_iteration_limit" -> {
                PlanReview review = (PlanReview) state.data().get("planReview");
                boolean isValid = review != null && review.isValid();
                int iterationCount = ((Number) state.data().getOrDefault("iterationCount", 0)).intValue();
                boolean withinLimit = iterationCount < maxIterations;
                yield !isValid && withinLimit;
            }
            
            case "not_isValid_and_exceed_iteration_limit" -> {
                PlanReview review = (PlanReview) state.data().get("planReview");
                boolean isValid = review != null && review.isValid();
                int iterationCount = ((Number) state.data().getOrDefault("iterationCount", 0)).intValue();
                boolean exceedsLimit = iterationCount >= maxIterations;
                yield !isValid && exceedsLimit;
            }

            case "human_approved" -> "approve".equalsIgnoreCase(String.valueOf(state.data().getOrDefault("humanDecision", "")));

            case "human_rejected" -> "reject".equalsIgnoreCase(String.valueOf(state.data().getOrDefault("humanDecision", "")));

            case "human_rework_and_within_iteration_limit" -> {
                String decision = String.valueOf(state.data().getOrDefault("humanDecision", ""));
                int iterationCount = ((Number) state.data().getOrDefault("iterationCount", 0)).intValue();
                yield "rework".equalsIgnoreCase(decision) && iterationCount < maxIterations;
            }

            case "human_rework_and_exceed_iteration_limit" -> {
                String decision = String.valueOf(state.data().getOrDefault("humanDecision", ""));
                int iterationCount = ((Number) state.data().getOrDefault("iterationCount", 0)).intValue();
                yield "rework".equalsIgnoreCase(decision) && iterationCount >= maxIterations;
            }
            
            default -> throw new IllegalArgumentException("Unsupported condition type: " + conditionType);
        };
    }
}


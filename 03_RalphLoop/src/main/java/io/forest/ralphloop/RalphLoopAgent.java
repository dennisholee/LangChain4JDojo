package io.forest.ralphloop;


import io.forest.ralphloop.agent.PlanReviewerAgent;
import io.forest.ralphloop.agent.PlannerAgent;
import io.forest.ralphloop.model.PlanReview;
import org.bsc.langgraph4j.GraphDefinition;
import org.bsc.langgraph4j.StateGraph;

import java.util.Map;

import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * Orchestrates the planning + review state-machine workflow used by the demo application.
 *
 * <p>This class builds a {@link StateGraph} consisting of:
 * <ul>
 *   <li>a planner node that invokes {@link PlannerAgent} to create or refine a {@code Plan},</li>
 *   <li>a plan reviewer node that invokes {@link PlanReviewerAgent} to validate the plan,</li>
 *   <li>a conditional edge that loops back to the planner if the {@link PlanReview} is invalid
 *       or ends the workflow when the plan is valid.</li>
 * </ul>
 *
 * <p>The graph uses asynchronous node and edge actions (via the langgraph4j helpers) so agents
 * may perform network calls (for example, to an LLM service) without blocking the caller thread.
 */
public class RalphLoopAgent {

    /**
     * Build the state-machine workflow for the planning loop.
     *
     * @return a configured {@link StateGraph} ready to be compiled and invoked
     * @throws Exception when graph construction fails
     */
    public StateGraph<State> buildLoop() throws Exception {

        StateGraph<State> workflow = new StateGraph<>(State::new);

        workflow.addNode("planner", node_async(state -> {
                try {
                    return new PlannerAgent().apply(state);
                } catch (Exception e) {
                    state.incrementIterationCount();
                    return Map.of("error", e.getMessage());
                }
            }
        ));
        workflow.addNode("planReviewer", node_async(new PlanReviewerAgent()));

        workflow.addEdge("planner", "planReviewer");
        workflow.addConditionalEdges("planReviewer",
            edge_async(state ->
                ((PlanReview) state.data().get("planReview")).isValid() ? "stop" : "planner"),
            Map.of(
                "planner", "planner",
                "stop", GraphDefinition.END
            ));
        workflow.addEdge(GraphDefinition.START, "planner");
        //       workflow.addEdge("planReviewer", GraphDefinition.END);

        return workflow;
    }
}

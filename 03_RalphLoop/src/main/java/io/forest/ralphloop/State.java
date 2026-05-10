package io.forest.ralphloop;

import io.forest.ralphloop.model.Plan;
import io.forest.ralphloop.model.PlanReview;
import org.bsc.langgraph4j.state.AgentState;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Represents the execution state shared between agents in the RalphLoop workflow.
 *
 * <p>Extends {@link AgentState} and stores domain-specific values such as the original
 * requirement text, the current {@link Plan}, and the {@link PlanReview} produced by the
 * reviewer agent. The state also retains execution history and an iteration counter
 * that can be used to prevent infinite loops.
 */
public class State extends AgentState {

    String requirement;

    Plan plan;

    PlanReview planReview;

    String document;

//    String functionalCode;
//
//    String unitTestCode;
//
//    String judgeFeedback;
//
    List<String> executionHistory;

    int iterationCount;

    boolean isComplete;

    public State(Map<String, Object> initData) {
        super(initData);

        if (iterationCount < 0) iterationCount = 0;
        if (executionHistory == null) executionHistory = new java.util.ArrayList<>();
    }

    /**
     * Returns the original requirement text provided to the workflow, if present.
     *
     * @return optional requirement string
     */
    public Optional<String> getRequirement() {
        return this.value("requirement");
    }

    /**
     * Returns the current {@link Plan} stored in the state, if present.
     *
     * @return optional plan
     */
    public Optional<Plan> getPlan() {
        return this.value("plan");
    }

    /**
     * Returns the most recent {@link PlanReview} produced by the reviewer agent, if present.
     *
     * @return optional plan review
     */
    public Optional<PlanReview> getPlanReview() {
        return this.value("planReview");
    }

    /**
     * Returns the current API design guideline document, if present.
     *
     * @return optional document string
     */
    public Optional<String> getDocument() {
        return this.value("document");
    }

    /**
     * Increment the internal iteration counter; useful for tracking loop iterations or
     * applying back-off logic when retrying after failures.
     */
    public void incrementIterationCount() {
        this.iterationCount++;
    }
}

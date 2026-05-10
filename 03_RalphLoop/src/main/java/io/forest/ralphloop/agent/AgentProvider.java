package io.forest.ralphloop.agent;

import io.forest.ralphloop.State;
import io.forest.ralphloop.config.AgentProfileConfig;
import org.bsc.langgraph4j.action.NodeAction;

/**
 * Provider for creating a specific type of agent.
 * Implementations handle the instantiation and configuration of agent logic.
 */
public interface AgentProvider {
    
    /**
     * Creates a NodeAction for this agent type with the given configuration.
     */
    NodeAction<State> create(AgentProfileConfig config);
}

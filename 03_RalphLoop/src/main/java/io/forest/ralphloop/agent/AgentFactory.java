package io.forest.ralphloop.agent;

import io.forest.ralphloop.State;
import io.forest.ralphloop.config.AgentProfileConfig;
import org.bsc.langgraph4j.action.NodeAction;

/**
 * Factory for creating agent instances from configuration.
 * Implementations provide pluggable agent providers that can be referenced by workflow configs.
 */
public interface AgentFactory {
    
    /**
     * Creates a NodeAction for the given agent type and configuration.
     *
     * @param agentType the type of agent (e.g., "architect", "architect_reviewer")
    * @param config    the agent configuration (model settings, prompt template)
     * @return a NodeAction that can be executed in the workflow graph
     * @throws IllegalArgumentException if the agent type is not supported
     */
    NodeAction<State> createAgent(String agentType, AgentProfileConfig config);
    
    /**
     * Checks if this factory supports the given agent type.
     */
    boolean supports(String agentType);
    
    /**
     * Registers a custom agent provider.
     */
    void registerProvider(String agentType, AgentProvider provider);
}

package io.forest.ralphloop.config;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Configuration for a node in the workflow graph.
 * A node represents an execution step that runs an agent to transform state.
 */
public record NodeConfig(
    String id,                           // unique node identifier
    
    @JsonProperty("agent_type")
    String agentType,                   // "architect", "architect_reviewer", "custom_llm", etc.
    
    @JsonProperty("agent_profile")
    AgentProfileConfig agentProfile,    // model settings and prompt template
    
    String description                  // optional description
) {
    
    public NodeConfig {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Node id cannot be null or blank");
        }
        if (agentType == null || agentType.isBlank()) {
            throw new IllegalArgumentException("Node agentType cannot be null or blank");
        }
    }
}

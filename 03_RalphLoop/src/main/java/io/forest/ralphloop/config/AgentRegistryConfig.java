package io.forest.ralphloop.config;

import java.util.List;

/**
 * Root agent registry configuration.
 */
public record AgentRegistryConfig(
    List<AgentRegistrationConfig> agents
) {
    public AgentRegistryConfig {
        if (agents == null || agents.isEmpty()) {
            throw new IllegalArgumentException("Agent registry must contain at least one agent");
        }
    }
}
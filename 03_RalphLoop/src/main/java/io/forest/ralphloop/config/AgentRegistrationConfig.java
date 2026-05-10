package io.forest.ralphloop.config;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Single agent registration entry loaded from agent registry configuration.
 */
public record AgentRegistrationConfig(
    String type,

    @JsonProperty("class_name")
    String className,

    String description,
    Boolean enabled,

    @JsonProperty("agent_profile")
    AgentProfileConfig agentProfile,

    @JsonProperty("system_prompt")
    String systemPrompt,

    @JsonProperty("user_prompt")
    String userPrompt
) {

    public AgentRegistrationConfig {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Agent registration type cannot be null or blank");
        }
        if (className == null || className.isBlank()) {
            throw new IllegalArgumentException("Agent registration className cannot be null or blank");
        }
    }

    public boolean isEnabled() {
        return enabled == null || enabled;
    }
}
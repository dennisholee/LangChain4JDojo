package io.forest.ralphloop.config;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Configuration for a state channel.
 * Channels persist state across node executions in the workflow graph.
 */
public record StateChannelConfig(
    String name,                         // channel identifier
    String type,                         // "base", "append", "reduce", etc.
    
    @JsonProperty("defaultValue")
    Object defaultValue,                 // default initial value
    
    @JsonProperty("mergeStrategy")
    String mergeStrategy,                // how to merge values: "overwrite", "append", "reduce", etc.
    
    String description                   // optional description
) {
    
    public StateChannelConfig {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Channel name cannot be null or blank");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Channel type cannot be null or blank");
        }
    }
    
    /**
     * Creates a simple base channel with a default value.
     */
    public static StateChannelConfig base(String name, Object defaultValue) {
        return new StateChannelConfig(name, "base", defaultValue, "overwrite", null);
    }
    
    /**
     * Creates an append channel for accumulating values.
     */
    public static StateChannelConfig append(String name) {
        return new StateChannelConfig(name, "append", null, "append", null);
    }
}

package io.forest.ralphloop.config;

/**
 * Configuration for an edge in the workflow graph.
 * An edge connects two nodes with optional conditional routing logic.
 */
public record EdgeConfig(
    String source,               // source node id or "START"
    String target,               // target node id or "END"
    ConditionConfig condition,   // optional routing condition
    String description           // optional description
) {
    
    public EdgeConfig {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("Edge source cannot be null or blank");
        }
        if (target == null || target.isBlank()) {
            throw new IllegalArgumentException("Edge target cannot be null or blank");
        }
    }
    
    /**
     * Creates an unconditional edge (always taken).
     */
    public static EdgeConfig unconditional(String source, String target) {
        return new EdgeConfig(source, target, null, null);
    }
    
    /**
     * Creates a conditional edge with a condition type.
     */
    public static EdgeConfig conditional(String source, String target, ConditionConfig condition) {
        return new EdgeConfig(source, target, condition, null);
    }
    
    public boolean isConditional() {
        return condition != null;
    }
}

package io.forest.ralphloop.config;

/**
 * Represents a condition that can be evaluated for edge routing.
 * Conditions are used to determine which edge to take from a node.
 * 
 * Examples:
 *  - "isValid": check if review.isValid() is true
 *  - "iterationLimit": check if iterationCount >= maxIterations
 *  - "error": check if error flag is set
 */
public record ConditionConfig(
    String type,           // "isValid", "iterationLimit", "error", "spel", etc.
    String expression      // optional custom expression
) {
    
    public ConditionConfig {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Condition type cannot be null or blank");
        }
    }
    
    public static ConditionConfig simple(String type) {
        return new ConditionConfig(type, null);
    }
    
    public static ConditionConfig withExpression(String type, String expression) {
        return new ConditionConfig(type, expression);
    }
}

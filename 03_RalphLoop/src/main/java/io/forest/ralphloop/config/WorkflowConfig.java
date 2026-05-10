package io.forest.ralphloop.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Root configuration for a workflow.
 * Defines the complete structure of a feedback-loop workflow including nodes, edges, and state channels.
 */
public record WorkflowConfig(
    String name,                              // workflow identifier
    String description,                       // workflow purpose and behavior
    
    @JsonProperty("max_iterations")
    Integer maxIterations,                   // max loop iterations (default 3)
    
    @JsonProperty("entry_node")
    String entryNode,                        // starting node id
    
    List<NodeConfig> nodes,                  // workflow nodes
    List<EdgeConfig> edges,                  // edges connecting nodes
    
    @JsonProperty("state_channels")
    List<StateChannelConfig> stateChannels   // persisted state channels
) {
    
    public WorkflowConfig {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Workflow name cannot be null or blank");
        }
        if (nodes == null || nodes.isEmpty()) {
            throw new IllegalArgumentException("Workflow must have at least one node");
        }
        if (edges == null || edges.isEmpty()) {
            throw new IllegalArgumentException("Workflow must have at least one edge");
        }
    }
    
    /**
     * Returns effective max iterations, defaulting to 3 if not specified.
     */
    public int getMaxIterationsOrDefault() {
        return maxIterations != null ? maxIterations : 3;
    }
    
    /**
     * Finds a node by id.
     */
    public NodeConfig findNode(String nodeId) {
        return nodes.stream()
            .filter(n -> n.id().equals(nodeId))
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Gets all edges from a source node.
     */
    public List<EdgeConfig> getEdgesFrom(String sourceNodeId) {
        return edges.stream()
            .filter(e -> e.source().equals(sourceNodeId))
            .toList();
    }
    
    /**
     * Validates workflow configuration for consistency.
     * Checks that all referenced nodes exist, entry point is valid, etc.
     */
    public void validate() {
        // Validate entry node exists
        if (entryNode != null && findNode(entryNode) == null) {
            throw new IllegalArgumentException("Entry node '" + entryNode + "' does not exist");
        }
        
        // Validate all edge sources and targets reference valid nodes or START/END
        for (EdgeConfig edge : edges) {
            if (!edge.source().equals("START") && findNode(edge.source()) == null) {
                throw new IllegalArgumentException("Edge source '" + edge.source() + "' does not reference a valid node");
            }
            if (!edge.target().equals("END") && findNode(edge.target()) == null) {
                throw new IllegalArgumentException("Edge target '" + edge.target() + "' does not reference a valid node");
            }
        }
    }
}

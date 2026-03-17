package io.forest.ralphloop.model;

import java.io.Serializable;
import java.util.List;

/**
 * High-level plan produced by the {@code PlannerAgent}.
 *
 * @param phase feature lifecycle phase (e.g., backlog, development, release)
 * @param feature short name or description of the feature being planned
 * @param tasks ordered list of atomic {@link Task} items required to implement the feature
 */
public record Plan(String phase,
                   String feature,
                   List<Task> tasks) implements Serializable {
}

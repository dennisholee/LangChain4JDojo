package io.forest.ralphloop.model;

import java.io.Serializable;
import java.util.List;

/**
 * An atomic task representing a single actionable step in a {@link Plan}.
 *
 * @param action short, imperative action/title for the task (e.g., "Implement UserRepo")
 * @param description a longer human-readable description of the work
 * @param dependencies other task IDs or names that must be completed prior to this task
 * @param acceptanceCriteria concrete checks that define when the task is considered done
 */
public record Task(String action,
                   String description,
                   List<String> dependencies,
                   List<String> acceptanceCriteria) implements Serializable {
}

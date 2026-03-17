package io.forest.ralphloop.model;

import java.io.Serializable;
import java.util.List;

/**
 * Result produced by the {@code PlanReviewerAgent} after auditing a {@link Plan}.
 *
 * @param isValid true when the plan meets required checks and can proceed; false otherwise
 * @param feedback human-readable feedback describing findings and rationale
 * @param missingElements short labels for identified gaps or missing infrastructure elements
 * @param actions recommended corrective actions to bring the plan into compliance
 */
public record PlanReview(boolean isValid,
                         String feedback,
                         List<String> missingElements,
                         List<String> actions) implements Serializable {
}

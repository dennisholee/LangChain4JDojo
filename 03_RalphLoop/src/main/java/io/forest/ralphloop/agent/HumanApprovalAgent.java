package io.forest.ralphloop.agent;

import io.forest.ralphloop.State;
import io.forest.ralphloop.model.PlanReview;
import org.bsc.langgraph4j.action.NodeAction;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

/**
 * Human-in-the-loop approval checkpoint.
 *
 * <p>Prompts for a decision: approve, rework, or reject, and captures optional feedback.
 */
public class HumanApprovalAgent implements NodeAction<State> {

    @Override
    public Map<String, Object> apply(State state) throws Exception {
        Map<String, Object> result = new HashMap<>(state.data());

        printContext(state);

        String decision = promptDecision();
        String feedback = promptLine("Enter optional feedback (press Enter to skip): ");

        result.put("humanDecision", decision);
        result.put("humanFeedback", feedback == null ? "" : feedback.trim());
        return result;
    }

    private void printContext(State state) {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("Human Approval Gate");
        System.out.println("=".repeat(70));

        Object document = state.data().get("document");
        if (document != null) {
            System.out.println("Draft document preview:");
            String text = String.valueOf(document);
            int previewLength = Math.min(text.length(), 600);
            System.out.println(text.substring(0, previewLength));
            if (text.length() > previewLength) {
                System.out.println("... (truncated)");
            }
        }

        PlanReview review = (PlanReview) state.data().get("planReview");
        if (review != null) {
            System.out.println("\nLatest reviewer feedback:");
            System.out.println("- isValid: " + review.isValid());
            System.out.println("- feedback: " + review.feedback());
            if (!review.missingElements().isEmpty()) {
                System.out.println("- missingElements: " + review.missingElements());
            }
            if (!review.actions().isEmpty()) {
                System.out.println("- actions: " + review.actions());
            }
        }

        System.out.println("\nDecision options: approve | rework | reject");
    }

    private String promptDecision() throws IOException {
        while (true) {
            String input = promptLine("Enter decision: ");
            if (input == null) {
                continue;
            }
            String normalized = input.trim().toLowerCase();
            if ("approve".equals(normalized) || "rework".equals(normalized) || "reject".equals(normalized)) {
                return normalized;
            }
            System.out.println("Invalid decision. Please enter one of: approve, rework, reject.");
        }
    }

    private String promptLine(String prompt) throws IOException {
        System.out.print(prompt);
        System.out.flush();

        if (System.console() != null) {
            return System.console().readLine();
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        return reader.readLine();
    }
}

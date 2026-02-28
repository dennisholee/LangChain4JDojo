package io.forest.langchain4j.guardrails;

import dev.langchain4j.service.UserMessage;

/**
 * A small contract for services that detect prompt-injection attempts in
 * user-supplied text.
 *
 * <p>The implementation should return a short, human-readable verdict.
 * The calling guardrail treats a response that starts with the word
 * "unsafe" (case-insensitive, leading/trailing whitespace ignored) as
 * an indication that the input is malicious.
 */
public interface InjectionDetector {


    /**
     * Analyze the provided user message text and return a verdict string.
     *
     * @param text the user message to analyze (annotated with {@link UserMessage})
     * @return a short verdict such as "unsafe: reason" or "safe"
     */
    String isInjection(@UserMessage String text);
}

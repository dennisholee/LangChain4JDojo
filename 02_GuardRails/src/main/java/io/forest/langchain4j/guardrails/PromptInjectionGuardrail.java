package io.forest.langchain4j.guardrails;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An input guardrail that delegates to an {@link InjectionDetector} to
 * determine whether the user's message contains a prompt injection.
 *
 * <p>If the detector returns a string that starts with "unsafe" (case
 * insensitive), the guardrail treats the input as malicious and returns
 * a failure result; otherwise the input is accepted.
 */
public record PromptInjectionGuardrail (InjectionDetector injectionDetector) implements InputGuardrail {

    private static final Logger log = LoggerFactory.getLogger(PromptInjectionGuardrail.class);

    /**
     * Validate the user message by asking the injected {@link InjectionDetector}
     * whether the text looks like an injection. The method implements the
     * simple "startsWith('unsafe')" convention to decide failure vs success.
     *
     * @param userMessage the incoming user message
     * @return an {@link InputGuardrailResult} indicating acceptance or rejection
     */
    @Override
    public InputGuardrailResult validate(UserMessage userMessage) {

        String injection = this.injectionDetector.isInjection(userMessage.singleText());

        log.debug("Injection detector verdict for message: {}", injection);

        return injection.trim().toLowerCase().startsWith("unsafe")
            ? failure("failed: %s".formatted(injection))
            : success();
    }

}

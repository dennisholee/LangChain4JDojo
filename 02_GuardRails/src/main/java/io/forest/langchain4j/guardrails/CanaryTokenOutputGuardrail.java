package io.forest.langchain4j.guardrails;

import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An {@link OutputGuardrail} that checks LLM responses for the presence
 * of the per-request CANARY token stored in {@link CanaryContext}.
 *
 * <p>If the token appears in the model output, the guardrail returns a
 * failure result instructing the model to rewrite the answer without
 * the secret. The guardrail always clears the thread-local token in a
 * finally block to avoid leaking the token across requests.
 */
public class CanaryTokenOutputGuardrail implements OutputGuardrail {

    private static final Logger log = LoggerFactory.getLogger(CanaryTokenOutputGuardrail.class);

    /**
     * Validate the model response. If the response contains the current
     * thread's CANARY token, return a failure with a helpful message.
     * Always clears the token after inspection.
     *
     * @param request the guardrail request providing access to the LLM response
     * @return an {@link OutputGuardrailResult} indicating success or failure
     */
    @Override
    public OutputGuardrailResult validate(OutputGuardrailRequest request) {
        try {
            String token = CanaryContext.get();
            String responseText = request.responseFromLLM().aiMessage().text();

            log.debug("Checking response for canary token: {}", token);

            if (token != null && responseText.contains(token)) {
                log.warn("Response contained canary token {}", token);
                return failure(
                    "Your previous response contained the secret token %s. Please rewrite your answer without mentioning it."
                        .formatted(token));
            }

            return success();
        } finally {
            CanaryContext.clear();
        }
    }

}

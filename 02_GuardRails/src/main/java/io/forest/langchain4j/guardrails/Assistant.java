package io.forest.langchain4j.guardrails;

/**
 * A minimal service interface representing an AI assistant capable of
 * responding to chat messages.
 *
 * <p>Implementations are expected to forward the provided message to an
 * LLM and return the assistant's reply as a plain string. Guardrails
 * (input/output) may be applied by the service builder.</p>
 */
public interface Assistant {

    /**
     * Send a chat message to the assistant and return its reply.
     *
     * @param message the user-visible input; implementations should not
     *                modify the message semantics but may augment it with
     *                guardrail prompts if configured
     * @return the assistant's response as a String
     */
    String chat(String message);
}

package io.forest.langchain4j.guardrails;

import java.util.UUID;

/**
 * Holds a thread-local canary token used to detect whether an LLM leaked
 * a secret token that was injected into the prompt at request time.
 *
 * <p>Typical usage:
 * <ol>
 *   <li>Call {@link #generate()} before sending a request to produce and store
 *       a unique CANARY token for the current thread.</li>
 *   <li>After the LLM responds, call {@link #get()} to retrieve the token
 *       and check whether it appears in the model output.</li>
 *   <li>Always call {@link #clear()} to remove the token from thread-local
 *       storage when finished.</li>
 * </ol>
 */
public class CanaryContext {

    private static final ThreadLocal<String> CURRENT_TOKEN = new ThreadLocal<>();

    /**
     * Generate and store a new short CANARY token for the current thread.
     *
     * @return the generated token (prefix 'CANARY-')
     */
    public static String generate() {
        String token = "CANARY-" + UUID.randomUUID().toString().substring(0, 8);
        CURRENT_TOKEN.set(token);
        return token;
    }

    /**
     * Retrieve the current thread's CANARY token, or {@code null} if none
     * was generated for this thread.
     *
     * @return the token or {@code null}
     */
    public static String get() {
        return CURRENT_TOKEN.get();
    }

    /**
     * Clear the stored CANARY token for the current thread.
     */
    public static void clear() {
        CURRENT_TOKEN.remove();
    }
}
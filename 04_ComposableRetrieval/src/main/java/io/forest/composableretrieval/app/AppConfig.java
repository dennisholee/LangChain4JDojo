package io.forest.composableretrieval.app;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Loads application configuration from {@code application.properties} on the classpath.
 *
 * <p>Lookup order for each key:
 * <ol>
 *   <li>System property ({@code -Dkey=value} on the JVM command line)</li>
 *   <li>{@code application.properties} on the classpath</li>
 *   <li>The supplied default value</li>
 * </ol>
 *
 * <p>The OpenAI API key is <strong>never</strong> stored in properties files.
 * It is read exclusively from the {@code OPENAI_API_KEY} environment variable.
 */
public class AppConfig {

    private static final String PROPS_FILE = "/application.properties";
    private final Properties props = new Properties();

    public AppConfig() {
        try (InputStream is = AppConfig.class.getResourceAsStream(PROPS_FILE)) {
            if (is != null) {
                props.load(is);
            } else {
                System.err.println("WARNING: " + PROPS_FILE + " not found on classpath — using defaults.");
            }
        } catch (IOException e) {
            System.err.println("WARNING: Failed to load " + PROPS_FILE + ": " + e.getMessage());
        }
    }

    /** Return value; system property overrides the properties file. */
    public String get(String key, String defaultValue) {
        return System.getProperty(key, props.getProperty(key, defaultValue));
    }

    /** Convenience: parse an int property. */
    public int getInt(String key, int defaultValue) {
        String v = get(key, null);
        if (v == null) return defaultValue;
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            System.err.printf("WARNING: '%s' = '%s' is not a valid integer, using %d%n", key, v, defaultValue);
            return defaultValue;
        }
    }

    /** Convenience: parse a double property. */
    public double getDouble(String key, double defaultValue) {
        String v = get(key, null);
        if (v == null) return defaultValue;
        try {
            return Double.parseDouble(v.trim());
        } catch (NumberFormatException e) {
            System.err.printf("WARNING: '%s' = '%s' is not a valid double, using %f%n", key, v, defaultValue);
            return defaultValue;
        }
    }

    /**
     * Resolve the OpenAI API key.
     *
     * <p>Lookup order:
     * <ol>
     *   <li>{@code OPENAI_API_KEY} environment variable — use this for real OpenAI keys.</li>
     *   <li>{@code openai.api-key} property in {@code application.properties} — safe for local
     *       servers (e.g. LM Studio) that accept any non-empty string as the key.</li>
     * </ol>
     *
     * @throws IllegalStateException if neither source provides a non-blank value
     */
    public String requireOpenAiApiKey() {
        // 1. Real key from environment (takes priority)
        String envKey = System.getenv("OPENAI_API_KEY");
        if (envKey != null && !envKey.isBlank()) {
            return envKey;
        }
        // 2. Fallback to properties file (e.g. "lmstudio" for local servers)
        String propKey = get("openai.api-key", "");
        if (!propKey.isBlank()) {
            return propKey;
        }
        throw new IllegalStateException(
            "No API key found. Either set the OPENAI_API_KEY environment variable " +
            "or set 'openai.api-key' in application.properties (e.g. 'lmstudio' for local servers).");
    }
}

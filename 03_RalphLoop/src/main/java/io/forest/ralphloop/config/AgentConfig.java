package io.forest.ralphloop.config;

public final class AgentConfig {

    private static final String DEFAULT_BASE_URL = "http://localhost:1234/v1";
    private static final String DEFAULT_MODEL_NAME = "local-model";

    private AgentConfig() {
    }

    public static String baseUrl() {
        return resolve("RALPHLOOP_BASE_URL", "ralphloop.baseUrl", DEFAULT_BASE_URL);
    }

    public static String apiKey() {
        return resolve("RALPHLOOP_API_KEY", "ralphloop.apiKey", "");
    }

    public static String modelName() {
        return resolve("RALPHLOOP_MODEL_NAME", "ralphloop.modelName", DEFAULT_MODEL_NAME);
    }

    public static int connectTimeoutSeconds() {
        return resolveInt("RALPHLOOP_CONNECT_TIMEOUT_SECONDS", "ralphloop.connectTimeoutSeconds", 10);
    }

    public static int readTimeoutSeconds() {
        return resolveInt("RALPHLOOP_READ_TIMEOUT_SECONDS", "ralphloop.readTimeoutSeconds", 300);
    }

    public static int maxIterations() {
        return resolveInt("RALPHLOOP_MAX_ITERATIONS", "ralphloop.maxIterations", 3);
    }

    private static String resolve(String envName, String propertyName, String defaultValue) {
        String value = System.getenv(envName);
        if (isBlank(value)) {
            value = System.getProperty(propertyName, defaultValue);
        }
        return isBlank(value) ? defaultValue : value;
    }

    private static int resolveInt(String envName, String propertyName, int defaultValue) {
        String value = resolve(envName, propertyName, Integer.toString(defaultValue));
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
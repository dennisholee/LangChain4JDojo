package io.forest.composableretrieval.app;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link AppConfig}.
 * Verifies that properties are loaded from the classpath and that defaults work correctly.
 */
class AppConfigTest {

    @Test
    void loadsKnownProperties() {
        AppConfig cfg = new AppConfig();
        assertEquals("text-embedding-3-small", cfg.get("openai.embedding.model", "fallback"));
        assertEquals("gpt-4o-mini", cfg.get("openai.chat.model", "fallback"));
    }

    @Test
    void returnsDefaultForMissingKey() {
        AppConfig cfg = new AppConfig();
        assertEquals("my-default", cfg.get("nonexistent.key", "my-default"));
    }

    @Test
    void parsesIntProperty() {
        AppConfig cfg = new AppConfig();
        assertEquals(3, cfg.getInt("retriever.max-results", 99));
    }

    @Test
    void parsesDoubleProperty() {
        AppConfig cfg = new AppConfig();
        assertEquals(0.0, cfg.getDouble("retriever.min-score", 0.5), 1e-9);
    }

    @Test
    void systemPropertyOverridesFile() {
        System.setProperty("openai.chat.model", "gpt-override");
        try {
            AppConfig cfg = new AppConfig();
            assertEquals("gpt-override", cfg.get("openai.chat.model", "fallback"));
        } finally {
            System.clearProperty("openai.chat.model");
        }
    }
}

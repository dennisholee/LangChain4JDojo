package io.forest.ralphloop.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads and parses workflow configuration from YAML files.
 */
public class WorkflowLoader {
    
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());
    
    static {
        // Configure Jackson to handle YAML properly
        YAML_MAPPER.findAndRegisterModules();
    }
    
    /**
     * Loads a workflow configuration from a YAML file path.
     */
    public static WorkflowConfig loadFromPath(String filePath) throws IOException {
        return loadFromPath(Path.of(filePath));
    }
    
    /**
     * Loads a workflow configuration from a YAML Path.
     */
    public static WorkflowConfig loadFromPath(Path path) throws IOException {
        if (!Files.exists(path)) {
            throw new IOException("Workflow config file not found: " + path);
        }
        String content = Files.readString(path);
        return loadFromString(content);
    }
    
    /**
     * Loads a workflow configuration from a YAML file.
     */
    public static WorkflowConfig loadFromFile(File file) throws IOException {
        if (!file.exists()) {
            throw new IOException("Workflow config file not found: " + file.getAbsolutePath());
        }
        return YAML_MAPPER.readValue(file, WorkflowConfig.class);
    }
    
    /**
     * Loads a workflow configuration from a YAML string.
     */
    public static WorkflowConfig loadFromString(String yaml) throws IOException {
        return YAML_MAPPER.readValue(yaml, WorkflowConfig.class);
    }
    
    /**
     * Loads a workflow configuration from a classpath resource.
     */
    public static WorkflowConfig loadFromClasspath(String resourcePath) throws IOException {
        try (InputStream is = WorkflowLoader.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Classpath resource not found: " + resourcePath);
            }
            return YAML_MAPPER.readValue(is, WorkflowConfig.class);
        }
    }
    
    /**
     * Loads and validates a workflow configuration.
     */
    public static WorkflowConfig loadAndValidate(String filePath) throws IOException {
        WorkflowConfig config = loadFromPath(filePath);
        config.validate();
        return config;
    }
}

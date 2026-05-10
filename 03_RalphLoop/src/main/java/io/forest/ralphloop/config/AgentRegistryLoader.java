package io.forest.ralphloop.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Loads agent registry configuration from YAML resources.
 */
public final class AgentRegistryLoader {

    public static final String DEFAULT_REGISTRY_PATH = "agents/registry.yaml";

    private static final ObjectMapper MAPPER = new ObjectMapper(new YAMLFactory())
        .findAndRegisterModules();

    private AgentRegistryLoader() {
    }

    public static AgentRegistryConfig loadFromClasspath(String classpathResource) {
        Objects.requireNonNull(classpathResource, "classpathResource cannot be null");

        InputStream input = AgentRegistryLoader.class.getClassLoader().getResourceAsStream(classpathResource);
        if (input == null) {
            throw new IllegalArgumentException("Agent registry resource not found: " + classpathResource);
        }

        try {
            JsonNode root = MAPPER.readTree(input);

            if (isFileIndexedRegistry(root)) {
                AgentRegistryIndexConfig index = MAPPER.treeToValue(root, AgentRegistryIndexConfig.class);
                List<AgentRegistrationConfig> agents = new ArrayList<>();
                for (AgentRegistryIndexEntry entry : index.agents()) {
                    String agentPath = resolveClasspathResource(classpathResource, entry.file());
                    agents.add(loadAgentRegistrationFromClasspath(agentPath));
                }
                return new AgentRegistryConfig(agents);
            }

            return MAPPER.treeToValue(root, AgentRegistryConfig.class);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Failed to load agent registry from: " + classpathResource, ex);
        }
    }

    private static boolean isFileIndexedRegistry(JsonNode root) {
        JsonNode agentsNode = root.get("agents");
        if (agentsNode == null || !agentsNode.isArray() || agentsNode.isEmpty()) {
            return false;
        }
        JsonNode first = agentsNode.get(0);
        return first != null && first.has("file");
    }

    private static AgentRegistrationConfig loadAgentRegistrationFromClasspath(String classpathResource) {
        InputStream input = AgentRegistryLoader.class.getClassLoader().getResourceAsStream(classpathResource);
        if (input == null) {
            throw new IllegalArgumentException("Agent definition resource not found: " + classpathResource);
        }

        try {
            return MAPPER.readValue(input, AgentRegistrationConfig.class);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Failed to load agent definition from: " + classpathResource, ex);
        }
    }

    private static String resolveClasspathResource(String registryPath, String referencedPath) {
        if (referencedPath == null || referencedPath.isBlank()) {
            throw new IllegalArgumentException("Agent registry file entry cannot be null or blank");
        }

        String normalized = referencedPath.startsWith("/") ? referencedPath.substring(1) : referencedPath;

        if (normalized.contains("/")) {
            return normalized;
        }

        int lastSlash = registryPath.lastIndexOf('/');
        if (lastSlash < 0) {
            return normalized;
        }

        return registryPath.substring(0, lastSlash + 1) + normalized;
    }

    private record AgentRegistryIndexConfig(List<AgentRegistryIndexEntry> agents) {
    }

    private record AgentRegistryIndexEntry(
        @JsonProperty("file")
        String file
    ) {
    }
}
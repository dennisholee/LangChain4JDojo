package io.forest.ralphloop.agent;

import io.forest.ralphloop.State;
import io.forest.ralphloop.config.AgentProfileConfig;
import io.forest.ralphloop.config.AgentRegistryConfig;
import io.forest.ralphloop.config.AgentRegistryLoader;
import io.forest.ralphloop.config.AgentRegistrationConfig;
import org.bsc.langgraph4j.action.NodeAction;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry of built-in agent providers and factory for creating agents from configuration.
 * Provides standard agents like "architect" and "architect_reviewer" and supports custom agent registration.
 */
public class BuiltInAgentProviders implements AgentFactory {
    
    private final Map<String, AgentProvider> providers = new HashMap<>();
    
    public BuiltInAgentProviders() {
        this(AgentRegistryLoader.DEFAULT_REGISTRY_PATH);
    }

    public BuiltInAgentProviders(String registryClasspathResource) {
        AgentRegistryConfig registry = AgentRegistryLoader.loadFromClasspath(registryClasspathResource);
        for (AgentRegistrationConfig registration : registry.agents()) {
            if (!registration.isEnabled()) {
                continue;
            }
            registerProvider(
                registration.type(),
                runtimeProfile -> instantiate(registration.className(), registration, mergeProfiles(registration.agentProfile(), runtimeProfile))
            );
        }
    }
    
    @Override
    public NodeAction<State> createAgent(String agentType, AgentProfileConfig config) {
        if (!supports(agentType)) {
            throw new IllegalArgumentException("Unsupported agent type: " + agentType);
        }
        AgentProvider provider = providers.get(agentType);
        return provider.create(config);
    }
    
    @Override
    public boolean supports(String agentType) {
        return providers.containsKey(agentType);
    }
    
    @Override
    public void registerProvider(String agentType, AgentProvider provider) {
        providers.put(agentType, provider);
    }

    private NodeAction<State> instantiate(String className, AgentRegistrationConfig registration, AgentProfileConfig profile) {
        try {
            Class<?> agentClass = Class.forName(className);
            if (!NodeAction.class.isAssignableFrom(agentClass)) {
                throw new IllegalArgumentException("Configured class is not a NodeAction: " + className);
            }

            @SuppressWarnings("unchecked")
            Class<? extends NodeAction<State>> typedClass = (Class<? extends NodeAction<State>>) agentClass;
            try {
                return typedClass.getDeclaredConstructor(AgentRegistrationConfig.class, AgentProfileConfig.class)
                    .newInstance(registration, profile);
            } catch (NoSuchMethodException ignored) {
            }
            try {
                return typedClass.getDeclaredConstructor(AgentProfileConfig.class).newInstance(profile);
            } catch (NoSuchMethodException ignored) {
                return typedClass.getDeclaredConstructor().newInstance();
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to instantiate configured agent: " + className, ex);
        }
    }

    private AgentProfileConfig mergeProfiles(AgentProfileConfig base, AgentProfileConfig override) {
        if (base == null) {
            return override;
        }
        if (override == null) {
            return base;
        }

        return new AgentProfileConfig(
            override.modelName() != null ? override.modelName() : base.modelName(),
            override.baseUrl() != null ? override.baseUrl() : base.baseUrl(),
            override.connectTimeoutMinutes() != null ? override.connectTimeoutMinutes() : base.connectTimeoutMinutes(),
            override.readTimeoutMinutes() != null ? override.readTimeoutMinutes() : base.readTimeoutMinutes(),
            override.promptTemplate() != null ? override.promptTemplate() : base.promptTemplate(),
            override.outputType() != null ? override.outputType() : base.outputType(),
            override.maxOutputTokens() != null ? override.maxOutputTokens() : base.maxOutputTokens(),
            override.summaryModelName() != null ? override.summaryModelName() : base.summaryModelName(),
            override.summaryBaseUrl() != null ? override.summaryBaseUrl() : base.summaryBaseUrl(),
            override.summaryMaxOutputTokens() != null ? override.summaryMaxOutputTokens() : base.summaryMaxOutputTokens()
        );
    }
}

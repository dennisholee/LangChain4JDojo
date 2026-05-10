package io.forest.ralphloop.config;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Configuration for an agent's model settings and prompt template.
 * Agents load their LLM endpoints and prompt logic from this config.
 */
public record AgentProfileConfig(
    @JsonProperty("model_name")
    String modelName,
    
    @JsonProperty("base_url")
    String baseUrl,
    
    @JsonProperty("connect_timeout_minutes")
    Integer connectTimeoutMinutes,
    
    @JsonProperty("read_timeout_minutes")
    Integer readTimeoutMinutes,
    
    @JsonProperty("prompt_template")
    String promptTemplate,  // path or inline prompt text
    
    @JsonProperty("output_type")
    String outputType,      // "json", "text", etc.

    @JsonProperty("max_output_tokens")
    Integer maxOutputTokens,

    @JsonProperty("summary_model_name")
    String summaryModelName,

    @JsonProperty("summary_base_url")
    String summaryBaseUrl,

    @JsonProperty("summary_max_output_tokens")
    Integer summaryMaxOutputTokens
) {
    
    public AgentProfileConfig {
        // All fields optional; use defaults from AgentConfig if not provided
    }
    
    public String getModelNameOrDefault(String defaultValue) {
        return modelName != null ? modelName : defaultValue;
    }
    
    public String getBaseUrlOrDefault(String defaultValue) {
        return baseUrl != null ? baseUrl : defaultValue;
    }
    
    public Integer getConnectTimeoutOrDefault(Integer defaultValue) {
        return connectTimeoutMinutes != null ? connectTimeoutMinutes : defaultValue;
    }
    
    public Integer getReadTimeoutOrDefault(Integer defaultValue) {
        return readTimeoutMinutes != null ? readTimeoutMinutes : defaultValue;
    }

    public Integer getMaxOutputTokensOrDefault(Integer defaultValue) {
        return maxOutputTokens != null ? maxOutputTokens : defaultValue;
    }

    public String getSummaryModelNameOrDefault(String defaultValue) {
        return summaryModelName != null ? summaryModelName : defaultValue;
    }

    public String getSummaryBaseUrlOrDefault(String defaultValue) {
        return summaryBaseUrl != null ? summaryBaseUrl : defaultValue;
    }

    public Integer getSummaryMaxOutputTokensOrDefault(Integer defaultValue) {
        return summaryMaxOutputTokens != null ? summaryMaxOutputTokens : defaultValue;
    }
}

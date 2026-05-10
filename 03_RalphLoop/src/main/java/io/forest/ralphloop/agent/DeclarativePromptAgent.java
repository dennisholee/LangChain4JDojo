package io.forest.ralphloop.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.http.client.jdk.JdkHttpClient;
import dev.langchain4j.model.openai.OpenAiChatModel;
import io.forest.ralphloop.State;
import io.forest.ralphloop.config.AgentConfig;
import io.forest.ralphloop.config.AgentProfileConfig;
import io.forest.ralphloop.config.AgentRegistrationConfig;
import io.forest.ralphloop.model.Plan;
import io.forest.ralphloop.model.PlanReview;
import org.bsc.langgraph4j.action.NodeAction;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generic declarative prompt-based agent runtime.
 */
public class DeclarativePromptAgent implements NodeAction<State> {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final java.util.regex.Pattern PLACEHOLDER_PATTERN = java.util.regex.Pattern.compile("^\\$\\{([A-Z0-9_]+)(?::([^}]*))?}$");
    private static final int MAX_REQUIREMENT_CHARS = 1600;
    private static final int MAX_DOCUMENT_CHARS = 5000;
    private static final int MAX_FEEDBACK_CHARS = 900;
    private static final int MAX_HUMAN_FEEDBACK_CHARS = 400;
    private static final int MAX_LIST_ITEMS = 6;
    private static final int MAX_LIST_ITEM_CHARS = 180;
    private static final int SUMMARY_CHUNK_CHARS = 1800;
    private static final int SUMMARY_MAX_CHUNKS = 6;
    private static final int SUMMARY_STAGE_MULTIPLIER = 2;
    private static final String SUMMARY_MODEL_ENV = "RALPHLOOP_SUMMARY_MODEL_NAME";
    private static final String SUMMARY_BASE_URL_ENV = "RALPHLOOP_SUMMARY_BASE_URL";
    private static final String SUMMARY_MAX_OUTPUT_TOKENS_ENV = "RALPHLOOP_SUMMARY_MAX_OUTPUT_TOKENS";
    private static final String DEFAULT_SUMMARY_MODEL = "openai/gpt-4o-mini";
    private static final int DEFAULT_SUMMARY_MAX_OUTPUT_TOKENS = 220;
    private static final String TOKEN_METRICS_KEY = "tokenMetrics";
    private static final String TOKEN_METRICS_BY_AGENT_KEY = "byAgent";

    private final AgentRegistrationConfig registration;
    private final OpenAiChatModel chatModel;
    private final OpenAiChatModel summaryModel;

    public DeclarativePromptAgent(AgentRegistrationConfig registration, AgentProfileConfig profile) {
        if (registration == null) {
            throw new IllegalArgumentException("registration cannot be null");
        }

        this.registration = registration;

        String baseUrl = resolvePlaceholder(profile != null ? profile.getBaseUrlOrDefault(AgentConfig.baseUrl()) : AgentConfig.baseUrl());
        String modelName = resolvePlaceholder(profile != null ? profile.getModelNameOrDefault(AgentConfig.modelName()) : AgentConfig.modelName());
        int connectTimeoutSeconds = profile != null && profile.connectTimeoutMinutes() != null
            ? profile.connectTimeoutMinutes() * 60
            : AgentConfig.connectTimeoutSeconds();
        int readTimeoutSeconds = profile != null && profile.readTimeoutMinutes() != null
            ? profile.readTimeoutMinutes() * 60
            : AgentConfig.readTimeoutSeconds();

        OpenAiChatModel.OpenAiChatModelBuilder modelBuilder = OpenAiChatModel.builder()
            .baseUrl(baseUrl)
            .apiKey(AgentConfig.apiKey())
            .modelName(modelName)
            .httpClientBuilder(JdkHttpClient.builder()
                .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                .readTimeout(Duration.ofSeconds(readTimeoutSeconds))
                .httpClientBuilder(HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)));

        if (profile != null && profile.maxOutputTokens() != null && profile.maxOutputTokens() > 0) {
            modelBuilder.maxTokens(profile.maxOutputTokens());
        }

        this.chatModel = modelBuilder.build();
        this.summaryModel = buildSummaryModel(baseUrl, connectTimeoutSeconds, readTimeoutSeconds, profile);
    }

    @Override
    public Map<String, Object> apply(State state) throws Exception {
        Map<String, Object> result = new HashMap<>(state.data());
        CompressionStats stats = new CompressionStats();

        String systemPrompt = renderTemplate(defaultString(registration.systemPrompt()), state, "system", stats);
        String userPrompt = renderTemplate(defaultString(registration.userPrompt()), state, "user", stats);

        List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();
        if (!systemPrompt.isBlank()) {
            messages.add(SystemMessage.from(systemPrompt));
        }
        messages.add(UserMessage.from(userPrompt));

        // Track final prompt footprint after all substitutions.
        stats.finalPromptTokens = estimateTokens(systemPrompt) + estimateTokens(userPrompt);

        String output = chatModel.chat(messages).aiMessage().text();
        stats.modelOutputTokens = estimateTokens(output);
        recordTokenMetrics(result, stats);

        switch (registration.type()) {
            case "architect" -> {
                result.put("requirement", state.data().get("requirement"));
                result.put("document", output);
            }
            case "architect_reviewer" -> {
                PlanReview review = parsePlanReview(output);
                result.put("requirement", state.data().get("requirement"));
                result.put("document", state.data().get("document"));
                result.put("planReview", review);
            }
            case "planner" -> {
                Plan plan = parsePlan(output);
                result.put("plan", plan);
            }
            case "plan_reviewer" -> {
                PlanReview review = parsePlanReview(output);
                result.put("plan", state.data().get("plan"));
                result.put("planReview", review);
            }
            default -> result.put("output", output);
        }

        return result;
    }

    private String renderTemplate(String template, State state, String templateRole, CompressionStats stats) {
        String rendered = template;

        String requirement = stringValue(state.data().get("requirement"));
        rendered = replaceCompressedPlaceholder(rendered, "{{requirement}}", "requirement", requirement, MAX_REQUIREMENT_CHARS, templateRole, stats);
        rendered = replaceCompressedPlaceholder(rendered, "{{requirements}}", "requirements", requirement, MAX_REQUIREMENT_CHARS, templateRole, stats);
        rendered = replaceCompressedPlaceholder(rendered, "{{document}}", "document", stringValue(state.data().get("document")), MAX_DOCUMENT_CHARS, templateRole, stats);
        rendered = replaceCompressedPlaceholder(rendered, "{{prevDocument}}", "prevDocument", stringValueOrDefault(state.data().get("document"), "None"), MAX_DOCUMENT_CHARS, templateRole, stats);

        Object plan = state.data().get("plan");
        rendered = replaceCompressedPlaceholder(rendered, "{{plan}}", "plan", stringValueOrDefault(plan, "None"), MAX_DOCUMENT_CHARS, templateRole, stats);
        rendered = replaceCompressedPlaceholder(rendered, "{{prevPlan}}", "prevPlan", stringValueOrDefault(plan, "None"), MAX_DOCUMENT_CHARS, templateRole, stats);

        PlanReview review = (PlanReview) state.data().get("planReview");
        rendered = replaceCompressedPlaceholder(rendered, "{{feedback}}", "feedback", review != null ? defaultString(review.feedback()) : "None", MAX_FEEDBACK_CHARS, templateRole, stats);
        rendered = rendered.replace("{{missingElements}}", review != null ? compactList(review.missingElements()) : "[]");
        rendered = rendered.replace("{{actions}}", review != null ? compactList(review.actions()) : "[]");
        rendered = rendered.replace("{{humanDecision}}", stringValueOrDefault(state.data().get("humanDecision"), "None"));
        rendered = replaceCompressedPlaceholder(rendered, "{{humanFeedback}}", "humanFeedback", stringValueOrDefault(state.data().get("humanFeedback"), "None"), MAX_HUMAN_FEEDBACK_CHARS, templateRole, stats);

        return rendered;
    }

    private String replaceCompressedPlaceholder(
        String template,
        String placeholder,
        String label,
        String value,
        int maxChars,
        String templateRole,
        CompressionStats stats
    ) {
        if (!template.contains(placeholder)) {
            return template;
        }
        CompressionResult compressed = compressForPrompt(label, value, maxChars, stats);
        stats.recordField(templateRole + "." + label, compressed);
        return template.replace(placeholder, compressed.text());
    }

    private OpenAiChatModel buildSummaryModel(String defaultBaseUrl, int connectTimeoutSeconds, int readTimeoutSeconds, AgentProfileConfig profile) {
        String summaryBaseUrl = profile != null ? resolvePlaceholder(defaultString(profile.summaryBaseUrl())) : "";
        if (summaryBaseUrl.isBlank()) {
            summaryBaseUrl = resolvePlaceholder(defaultString(System.getenv(SUMMARY_BASE_URL_ENV)));
        }
        if (summaryBaseUrl.isBlank()) {
            summaryBaseUrl = defaultBaseUrl;
        }

        String summaryModelName = profile != null ? resolvePlaceholder(defaultString(profile.summaryModelName())) : "";
        if (summaryModelName.isBlank()) {
            summaryModelName = resolvePlaceholder(defaultString(System.getenv(SUMMARY_MODEL_ENV)));
        }
        if (summaryModelName.isBlank()) {
            summaryModelName = DEFAULT_SUMMARY_MODEL;
        }

        Integer summaryMaxOutputTokens = profile != null ? profile.summaryMaxOutputTokens() : null;
        if (summaryMaxOutputTokens == null || summaryMaxOutputTokens <= 0) {
            summaryMaxOutputTokens = parsePositiveInt(System.getenv(SUMMARY_MAX_OUTPUT_TOKENS_ENV));
        }
        if (summaryMaxOutputTokens == null || summaryMaxOutputTokens <= 0) {
            summaryMaxOutputTokens = DEFAULT_SUMMARY_MAX_OUTPUT_TOKENS;
        }

        return OpenAiChatModel.builder()
            .baseUrl(summaryBaseUrl)
            .apiKey(AgentConfig.apiKey())
            .modelName(summaryModelName)
            .maxTokens(summaryMaxOutputTokens)
            .httpClientBuilder(JdkHttpClient.builder()
                .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                .readTimeout(Duration.ofSeconds(readTimeoutSeconds))
                .httpClientBuilder(HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)))
            .build();
    }

    private CompressionResult compressForPrompt(String label, String value, int maxChars, CompressionStats stats) {
        String safe = defaultString(value);
        long originalTokens = estimateTokens(safe);
        if (safe.length() <= maxChars) {
            return new CompressionResult(safe, safe.length(), safe.length(), originalTokens, originalTokens, false, false);
        }

        try {
            String summarized = hierarchicalSummary(label, safe, maxChars, stats);
            String compressed = truncate(summarized, maxChars);
            return new CompressionResult(compressed, safe.length(), compressed.length(), originalTokens, estimateTokens(compressed), true, false);
        } catch (Exception ignored) {
            // Fall back to deterministic truncation to keep prompt rendering reliable.
            String compressed = truncate(safe, maxChars);
            return new CompressionResult(compressed, safe.length(), compressed.length(), originalTokens, estimateTokens(compressed), true, true);
        }
    }

    private String hierarchicalSummary(String label, String text, int maxChars, CompressionStats stats) {
        String current = text;
        int stageTarget = Math.max(200, maxChars * SUMMARY_STAGE_MULTIPLIER);

        for (int pass = 0; pass < 3 && current.length() > stageTarget; pass++) {
            List<String> chunks = chunkText(current, SUMMARY_CHUNK_CHARS, SUMMARY_MAX_CHUNKS);
            List<String> chunkSummaries = new ArrayList<>();
            int perChunkBudget = Math.max(120, stageTarget / Math.max(1, chunks.size()));

            for (int i = 0; i < chunks.size(); i++) {
                chunkSummaries.add(summarizeChunk(label, i + 1, chunks.size(), chunks.get(i), perChunkBudget, stats));
            }

            current = String.join("\n", chunkSummaries);
            stageTarget = Math.max(maxChars, stageTarget / 2);
        }

        if (current.length() > maxChars) {
            current = summarizeChunk(label, 1, 1, current, maxChars, stats);
        }
        return current;
    }

    private List<String> chunkText(String text, int chunkSize, int maxChunks) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }

        for (int start = 0; start < text.length() && chunks.size() < maxChunks; start += chunkSize) {
            int end = Math.min(text.length(), start + chunkSize);
            chunks.add(text.substring(start, end));
        }

        if (chunks.isEmpty()) {
            chunks.add(text);
        }
        return chunks;
    }

    private String summarizeChunk(String label, int index, int total, String chunk, int targetChars, CompressionStats stats) {
        String prompt = "Summarize the following " + label + " content for an LLM prompt. "
            + "Keep mandatory constraints, reviewer actions, compliance terms, and unresolved gaps. "
            + "Return plain text only under " + targetChars + " characters."
            + "\nChunk " + index + " of " + total + ":\n" + chunk;

        List<ChatMessage> messages = List.of(
            SystemMessage.from("You compress workflow context while preserving implementation-critical intent."),
            UserMessage.from(prompt)
        );

        stats.summaryCalls++;
        stats.summaryInputTokens += estimateTokens(prompt);
        String summarized = summaryModel.chat(messages).aiMessage().text();
        String compressed = truncate(defaultString(summarized), targetChars);
        stats.summaryOutputTokens += estimateTokens(compressed);
        return compressed;
    }

    @SuppressWarnings("unchecked")
    private void recordTokenMetrics(Map<String, Object> result, CompressionStats stats) {
        Map<String, Object> metrics = (Map<String, Object>) result.get(TOKEN_METRICS_KEY);
        if (metrics == null) {
            metrics = new HashMap<>();
        }

        long totalBefore = longValue(metrics.get("totalEstimatedInputTokensBefore")) + stats.inputTokensBefore;
        long totalAfter = longValue(metrics.get("totalEstimatedInputTokensAfter")) + stats.inputTokensAfter;
        long totalSaved = Math.max(0L, totalBefore - totalAfter);

        metrics.put("totalEstimatedInputTokensBefore", totalBefore);
        metrics.put("totalEstimatedInputTokensAfter", totalAfter);
        metrics.put("totalEstimatedInputTokensSaved", totalSaved);
        metrics.put("totalSummaryCalls", longValue(metrics.get("totalSummaryCalls")) + stats.summaryCalls);
        metrics.put("totalSummaryInputTokens", longValue(metrics.get("totalSummaryInputTokens")) + stats.summaryInputTokens);
        metrics.put("totalSummaryOutputTokens", longValue(metrics.get("totalSummaryOutputTokens")) + stats.summaryOutputTokens);
        metrics.put("totalPromptTokens", longValue(metrics.get("totalPromptTokens")) + stats.finalPromptTokens);
        metrics.put("totalModelOutputTokens", longValue(metrics.get("totalModelOutputTokens")) + stats.modelOutputTokens);
        metrics.put("totalCompressedFields", longValue(metrics.get("totalCompressedFields")) + stats.compressedFields);
        metrics.put("totalFallbackTruncations", longValue(metrics.get("totalFallbackTruncations")) + stats.fallbackTruncations);
        metrics.put("lastInvocation", stats.toMap());

        Map<String, Object> byAgent = (Map<String, Object>) metrics.get(TOKEN_METRICS_BY_AGENT_KEY);
        if (byAgent == null) {
            byAgent = new HashMap<>();
        }
        Map<String, Object> agentMetrics = (Map<String, Object>) byAgent.get(registration.type());
        if (agentMetrics == null) {
            agentMetrics = new HashMap<>();
        }
        agentMetrics.put("invocations", longValue(agentMetrics.get("invocations")) + 1);
        agentMetrics.put("estimatedInputTokensBefore", longValue(agentMetrics.get("estimatedInputTokensBefore")) + stats.inputTokensBefore);
        agentMetrics.put("estimatedInputTokensAfter", longValue(agentMetrics.get("estimatedInputTokensAfter")) + stats.inputTokensAfter);
        agentMetrics.put("estimatedInputTokensSaved", longValue(agentMetrics.get("estimatedInputTokensSaved")) + Math.max(0L, stats.inputTokensBefore - stats.inputTokensAfter));
        agentMetrics.put("summaryCalls", longValue(agentMetrics.get("summaryCalls")) + stats.summaryCalls);
        byAgent.put(registration.type(), agentMetrics);

        metrics.put(TOKEN_METRICS_BY_AGENT_KEY, byAgent);
        result.put(TOKEN_METRICS_KEY, metrics);
    }

    private long estimateTokens(String text) {
        String safe = defaultString(text);
        if (safe.isBlank()) {
            return 0L;
        }
        return Math.max(1L, (safe.length() + 3L) / 4L);
    }

    private long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private record CompressionResult(
        String text,
        long originalChars,
        long compressedChars,
        long originalTokens,
        long compressedTokens,
        boolean compressed,
        boolean fallbackTruncated
    ) {
    }

    private static final class CompressionStats {
        private long inputTokensBefore;
        private long inputTokensAfter;
        private long summaryCalls;
        private long summaryInputTokens;
        private long summaryOutputTokens;
        private long compressedFields;
        private long fallbackTruncations;
        private long finalPromptTokens;
        private long modelOutputTokens;
        private final Map<String, Object> fields = new HashMap<>();

        private void recordField(String fieldName, CompressionResult result) {
            inputTokensBefore += result.originalTokens();
            inputTokensAfter += result.compressedTokens();
            if (result.compressed()) {
                compressedFields++;
            }
            if (result.fallbackTruncated()) {
                fallbackTruncations++;
            }

            Map<String, Object> details = new HashMap<>();
            details.put("originalChars", result.originalChars());
            details.put("compressedChars", result.compressedChars());
            details.put("originalTokens", result.originalTokens());
            details.put("compressedTokens", result.compressedTokens());
            details.put("savedTokens", Math.max(0L, result.originalTokens() - result.compressedTokens()));
            details.put("compressed", result.compressed());
            details.put("fallbackTruncated", result.fallbackTruncated());
            fields.put(fieldName, details);
        }

        private Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("estimatedInputTokensBefore", inputTokensBefore);
            map.put("estimatedInputTokensAfter", inputTokensAfter);
            map.put("estimatedInputTokensSaved", Math.max(0L, inputTokensBefore - inputTokensAfter));
            map.put("summaryCalls", summaryCalls);
            map.put("summaryInputTokens", summaryInputTokens);
            map.put("summaryOutputTokens", summaryOutputTokens);
            map.put("compressedFields", compressedFields);
            map.put("fallbackTruncations", fallbackTruncations);
            map.put("promptTokens", finalPromptTokens);
            map.put("modelOutputTokens", modelOutputTokens);
            map.put("fields", fields);
            return map;
        }
    }

    private String compactList(List<String> items) {
        if (items == null || items.isEmpty()) {
            return "[]";
        }
        List<String> compact = new ArrayList<>();
        int limit = Math.min(items.size(), MAX_LIST_ITEMS);
        for (int i = 0; i < limit; i++) {
            compact.add(truncate(defaultString(items.get(i)), MAX_LIST_ITEM_CHARS));
        }
        return compact.toString();
    }

    private Plan parsePlan(String output) {
        try {
            String json = extractJsonObject(output);
            return MAPPER.readValue(json, Plan.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse planner output as Plan JSON", ex);
        }
    }

    private PlanReview parsePlanReview(String output) {
        try {
            String json = extractJsonObject(output);
            return MAPPER.readValue(json, PlanReview.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse reviewer output as PlanReview JSON", ex);
        }
    }

    private String extractJsonObject(String text) {
        if (text == null) {
            return "{}";
        }
        String trimmed = text.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String stringValueOrDefault(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private String truncate(String value, int maxChars) {
        String safe = defaultString(value);
        if (safe.length() <= maxChars) {
            return safe;
        }
        return safe.substring(0, Math.max(0, maxChars - 14)) + "... [truncated]";
    }

    private String resolvePlaceholder(String value) {
        if (value == null) {
            return null;
        }

        java.util.regex.Matcher matcher = PLACEHOLDER_PATTERN.matcher(value.trim());
        if (!matcher.matches()) {
            return value;
        }

        String envName = matcher.group(1);
        String defaultValue = matcher.group(2);
        String resolved = System.getenv(envName);
        return (resolved == null || resolved.isBlank()) ? defaultString(defaultValue) : resolved;
    }

    private Integer parsePositiveInt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}

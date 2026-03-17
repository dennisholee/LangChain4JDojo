package io.forest.ralphloop.agent;

import dev.langchain4j.http.client.jdk.JdkHttpClient;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.forest.ralphloop.State;
import io.forest.ralphloop.model.Plan;
import io.forest.ralphloop.tool.CodeWriterTool;
import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.util.Map;

/**
 * Node action that requests code artifacts from an AI-backed {@code CoderService} and
 * delegates file writes to {@link CodeWriterTool}.
 *
 * <p>The {@code CoderService} is expected to return a JSON object where keys are relative
 * file paths and values are the file contents. The tool will write those files to disk.
 * Use with caution: generated files may overwrite existing repository files.
 */
public class CoderAgent implements NodeAction<State> {

    final static Logger log = LoggerFactory.getLogger(CoderAgent.class);


    interface CoderService {
        @UserMessage("""
            Role: You are a Senior Java Developer and Build Engineer specializing in Production-Grade Implementation.
            
            Context: You have been assigned a specific Task from a verified Software Architecture Plan.
            
            Project Requirements: {{requirements}}
            Full Architecture Plan: {{plan}}
            
            Current Task to Execute: {{task}}
            
            Task: Generate the actual code artifacts (Java classes, interfaces, build files, or configurations) required to complete the Current Task.
            
            Instructions:
            1. Directory Structure Enforcement: You must determine the correct relative file path for the artifact based on the Standard Maven/Gradle Layout (e.g., Java classes go in src/main/java/..., Tests in src/test/java/..., Resources in src/main/resources).
            2. Package Declaration: Ensure the package statement at the top of every Java file strictly matches the directory structure and the Plan's specifications.
            3. Strict Specification Adherence: Implement Classes, Records, and Interfaces exactly as defined in the Plan. Use the precise names, method signatures (modifiers, return types, parameters), and field types provided.
            4. Functional Implementation: Do not generate empty stubs. Write functional logic inside the methods based on the method descriptions in the Plan. If the logic is complex, implement a robust, best-practice solution.
            5. Build & Config Validity: If the task involves pom.xml, build.gradle, or application.properties, ensure valid syntax, correct dependency versions, and proper structure.
            6. Code Quality: Include standard imports, basic Javadoc for public methods, and adhere to standard Java naming conventions (CamelCase for classes, camelCase for methods/variables).
            
            Constraints:
            1. Output Format: You must output a single valid JSON object.
            2. Key-Value Pair: The JSON key must be the relative file path (e.g., src/main/java/com/app/service/UserService.java). The value must be the raw file content as a string.
            3. Escaping: You must correctly escape double quotes (") and newlines (\\n) within the JSON string value to ensure the JSON is parsable.
            4. No Markdown: Do not use json, java, or any backticks. Start the response with { and end with }.
            5. No Prose: Do not provide explanations, chatter, or comments outside the JSON structure.
            
            Output Format: Provide the results in a structured JSON object where keys are file paths and values are the file contents.
            """)
        String code(@V("requirements") String requirement, @V("plan") Plan plan, @V("task") String task );
    }

    final CoderService coderService;

    public CoderAgent() {
        this.coderService = AiServices.builder(CoderAgent.CoderService.class)
            .chatModel(OpenAiChatModel.builder()
                .baseUrl("http://127.0.0.1:1234/v1")
                .apiKey("lm-studio")
                .modelName("phi-3-mini-4k-instruct")

                .httpClientBuilder(JdkHttpClient.builder()
                    .httpClientBuilder(HttpClient.newBuilder()
                        .version(HttpClient.Version.HTTP_1_1)))
                .build())
            .tools(new CodeWriterTool())
            .build();
    }

    public Map<String, Object> apply(State state) {

        log.info("Create plan of action");

        String requirement = state.getRequirement().orElseThrow();
        Plan plan = (Plan) state.data().get("plan");

        this.coderService.code(
            requirement,
            plan,
            ""
        );

        return Map.of();
    }
}

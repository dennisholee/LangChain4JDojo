package io.forest.ralphloop.agent;

import dev.langchain4j.http.client.jdk.JdkHttpClient;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.forest.ralphloop.State;
import io.forest.ralphloop.model.Plan;
import io.forest.ralphloop.model.PlanReview;
import org.apache.commons.lang3.ObjectUtils;
import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Node action that generates or refines a {@link Plan} by invoking an AI-backed {@code PlannerService}.
 *
 * <p>The embedded {@link PlannerService} interface defines the prompt and expected JSON output
 * structure that the AI model should return. The class wires up an OpenAI-compatible client
 * and uses the service to create a {@link Plan} from the requirement text and prior context.
 */
public class PlannerAgent implements NodeAction<State> {

    final static Logger log = LoggerFactory.getLogger(PlannerAgent.class);


    interface PlannerService {
        @UserMessage("""
            Role: You are an expert Technical Project Manager and Systems Architect specializing in Java Enterprise and Spring Boot ecosystems.
            
            Context: I am building [brief description of project/feature]. The current high-level requirements are: {{requirements}}.
            
            Feedback Loop (Revision Context):
            - Previous Draft: {{prevPlan}}
            - Feedback to Incorporate: {{feedback}}
            - Missing Elements: {{missingElements}}
            - Actions: {{actions}}
            
            Instructions:
            1. Atomic Execution: Ensure "Primitive Tasks" are so specific that an agent can execute them without asking for further clarification.
            2. Technical Context: Each task must specify the exact Java components. You must define the fully qualified package name, specific Class, Record, or Interface names, and precise method signatures (modifiers, return types, parameters) with a brief functional description for each method.
            3. Structure: Use a 3-level hierarchy: Phase > Feature > Task.
            4. Actionability: Start every task with a precise action verb (e.g., "Define," "Implement," "Refactor," "Unit Test").
            5. Dependency Mapping: Explicitly list task IDs or names that must be completed before a task can start.
            6. Acceptance Criteria: Define "Done" via a verifiable technical outcome (e.g., "Interface compiles and method 'uploadFile' returns a valid UUID string").
            
            Constraints:
            1. Ensure no task takes more than [e.g., 4 hours] of human effort to complete.
            2. No Markdown: Do not use json or any backticks. Start the response with { and end with }. 3. Output ONLY the raw JSON string. Do not include introductory text, markdown formatting (like json), or concluding remarks.
            3. Standard Encoding: Use only standard straight double quotes (") for keys and string values. Do not use curly "smart" quotes.
            4. No Prose: Do not include any introductory text, explanations, or concluding remarks. The output must be 100% valid, parsable JSON.
            
            Output Format:
            1. Provide the results in a structured JSON object containing an array of Phases, where each Phase contains Features, and each Feature contains Tasks with detailed Java specifications (package, type, signatures, and criteria).
            2. Do not include any conversational filler, markdown formatting (like ` ` `json), or explanatory text outside the JSON structure.
            3. Ensure all keys and string values use double quotes.
            4. Follow this sample schema: {
                                           "phase": "development",
                                           "feature": "user-authentication",
                                           "tasks": [
                                             {
                                               "action": "design login API",
                                               "description": "Define endpoints and request/response models",
                                               "dependencies": [],
                                               "acceptanceCriteria": []
                                             },
                                             {
                                               "title": "implement JWT",
                                               "description": "Add token generation and validation",
                                               "dependencies": ["design login API"],
                                               "acceptanceCriteria": ["JWT is generated"]
                                             }
                                           ]
                                         }.
            """)
        Plan createPlan(@V("requirements") String objective,
                        @V("prevPlan") String plan,
                        @V("feedback") String feedback,
                        @V("missingElements") List<String> missingElements,
                        @V("actions") List<String> actions);
    }

    final PlannerService plannerService;

    public PlannerAgent() {
        this.plannerService = AiServices.create(
            PlannerService.class,
            OpenAiChatModel.builder()
                .baseUrl("http://localhost:1234/v1")
                .apiKey("lm-studio")
                .modelName("phi-3-mini-4k-instruct")
                .httpClientBuilder(JdkHttpClient.builder()
                    .httpClientBuilder(HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(300))
                        .version(HttpClient.Version.HTTP_1_1)))
                .logRequests(true)
                .logResponses(true)
                .build());
    }

    public Map<String, Object> apply(State state) {

        log.info("Create plan of action");

        String requirement = state.getRequirement().orElseThrow();
        PlanReview planReview = (PlanReview) state.data().get("planReview");
        Plan prevPlan = (Plan) state.data().get("plan");

        Plan plan = this.plannerService.createPlan(
            requirement,
            prevPlan != null ? ObjectUtils.toString(prevPlan) : "None",
            planReview != null ? planReview.feedback() : "None",
            planReview != null ? planReview.missingElements() : Collections.emptyList(),
            planReview != null ? planReview.actions() : Collections.emptyList()
        );

        //log.info("plan: {}", plan);

        return Map.of(
            "plan", plan
        );
    }
}

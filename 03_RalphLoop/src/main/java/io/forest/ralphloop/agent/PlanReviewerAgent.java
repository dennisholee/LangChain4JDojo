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
import java.util.Map;

/**
 * Node action that audits a {@link Plan} and produces a {@link PlanReview}.
 *
 * <p>The internal {@link PlanReviewService} defines the audit prompt used by the AI model and
 * the expected structured output. The reviewer checks project scaffolding, build configuration,
 * package naming, resource placement, and component-level specifications to ensure the plan
 * is implementation-ready.
 */
public class PlanReviewerAgent implements NodeAction<State> {

    final static Logger log = LoggerFactory.getLogger(PlanReviewerAgent.class);

    interface PlanReviewService {

        @UserMessage("""
            Role: You are a Senior Software Architect and Quality Assurance Lead specializing in Java Application Design and Build Engineering (Maven/Gradle).
            
            Context: I have a list of high-level Requirements and a generated Java-specific Task List (Planner Output) intended to fulfill them.
            Original Requirements: {{requirements}}
            Proposed Task List: {{plan}}
            
            Tasks: Perform a deep-dive architectural audit of the Task List to ensure it adheres to the Standard Directory Layout, defines necessary build configurations, and provides implementation-ready specifications for a developer agent.
            
            Instructions:
            1. Project Scaffolding Verification: Verify the plan explicitly includes tasks to initialize the project with the standard directory structure (e.g., src/main/java, src/test/java, src/main/resources). Flag any missing infrastructure setup.
            2. Build Configuration Audit: Check for a mandatory task defining the Project Object Model (e.g., pom.xml or build.gradle). Ensure it specifies necessary dependencies, plugins, and Java version properties.
            3. Package & Namespace Validation: specific check that all classes and interfaces are assigned to valid, convention-compliant packages (e.g., com.company.module.service). Flag any flat or ambiguous package structures.
            4. Resource & Configuration Check: Ensure tasks exist for creating non-Java assets, such as application.properties/yml, logging configs (e.g., logback.xml), or SQL migration scripts, and that they are targeted to the correct resources folder.
            5. Component Specification Review: (Detailed Code Check): Validate that every Java task includes specific Class/Interface names, proper method signatures (return types, params), and strictly typed fields.
            6. Logical Dependency Flow: Confirm that infrastructure and build configuration tasks are scheduled before implementation tasks, and Interfaces/DTOs are defined before their implementing Services/Controllers.
            
            Constraints:
            1. Standard Compliance: Strictly enforce Maven/Gradle standard directory layouts and Java naming conventions.
            2. Implementation Readiness: Reject any task that describes what to do (e.g., "Create a User class") without describing how (e.g., "Create record UserDTO in package com.app.dto with fields UUID id, String email").
            3. Zero Scope Creep: Do not suggest features unrelated to the requirements, but DO insist on mandatory technical enablers (like build files and folder structures) even if not explicitly requested by the business user.
            
            Output Format: 
            1. Provide the assessment in a structured format consisting of two sections:
                a. Gap Report: A bulleted list of missing architectural elements (e.g., "Missing pom.xml definition", "Undefined package for UserService", "No resource folder setup").
                b. Actions: A list of specific, actionable corrections to the Task List (e.g., "Initialize Maven project structure," "Move UserEntity to package com.domain.model").
            2. Do not include any conversational filler, markdown formatting (like ` ` `json), or explanatory text outside the JSON structure.
            3. Ensure all keys and string values use double quotes.
           
           Sample schema: {
                            "isValid": false,
                            "feedback": "Missing integration tests and rollback strategy.", 
                            "missingElements": [
                                "integration-tests", 
                                "rollback-strategy"
                            ],
                            "actions": [
                                "Add pom.xml initialization task with dependencies and Java version property.",
                                "Define package structure for UserService in com.app.service and create a corresponding interface.",
                                "Initialize resources/config folder with application properties, logging configs, SQL migration scripts."
                              ]
                          }
            """)
        PlanReview verifyPlan(@V("requirements") String objective, @V("plan") String plan);
    }

    final PlanReviewService planReviewService;

    public PlanReviewerAgent() {
        this.planReviewService = AiServices.create(
            PlanReviewerAgent.PlanReviewService.class,
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

    @Override
    public Map<String, Object> apply(State state) throws Exception {

        String requirement = state.getRequirement().orElseThrow();
        Plan plan = (Plan) state.data().get("plan");

        PlanReview planReview = this.planReviewService.verifyPlan(
            requirement,
            plan != null ? ObjectUtils.toString(plan) : "None");

        return Map.of(
            "plan", plan,
            "planReview", planReview);
    }
}

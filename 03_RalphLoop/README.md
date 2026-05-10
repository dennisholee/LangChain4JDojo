# RalphLoop

RalphLoop is a YAML-driven AI workflow demo that composes drafting, review, and human approval steps using LangGraph4j and LangChain4j.

The current implementation is declarative-first:
- Workflow topology lives in YAML.
- Agent definitions and prompts live in YAML.
- Runtime execution is handled by generic agent providers and a configurable workflow builder.

## What It Does

The default workflow in `workflows/architect-loop.yaml`:
1. Drafts an API design guideline.
2. Reviews it for contract quality.
3. Routes valid output to a human approval gate.
4. Loops for rework when needed, with iteration limits.

This gives you a controllable review loop with bounded retries and explicit approval semantics.

## Architecture

Core runtime components:
- `src/main/java/io/forest/ralphloop/Application.java`: entry point that loads and invokes a workflow.
- `src/main/java/io/forest/ralphloop/builder/WorkflowBuilder.java`: compiles declarative workflow config into an executable state graph.
- `src/main/java/io/forest/ralphloop/agent/BuiltInAgentProviders.java`: loads agent registry and instantiates agent implementations.
- `src/main/java/io/forest/ralphloop/agent/DeclarativePromptAgent.java`: generic OpenAI-compatible prompt agent runtime.
- `src/main/java/io/forest/ralphloop/agent/HumanApprovalAgent.java`: interactive human gate (approve/rework/reject).
- `src/main/java/io/forest/ralphloop/State.java`: workflow state wrapper.

Configuration assets:
- `src/main/resources/workflows/architect-loop.yaml`: workflow graph, nodes, edges, conditions, channels.
- `src/main/resources/agents/registry.yaml`: registry index for agent definition files.
- `src/main/resources/agents/*.yaml`: per-agent prompt + model profile config.

## Workflow Semantics

The default flow uses these major conditions:
- `error`
- `not_error`
- `isValid`
- `not_isValid_and_within_iteration_limit`
- `not_isValid_and_exceed_iteration_limit`
- `human_approved`
- `human_rework_and_within_iteration_limit`
- `human_rework_and_exceed_iteration_limit`
- `human_rejected`

Iteration behavior:
- `iterationCount` is tracked via state channels.
- Iterations are bounded by `max_iterations` in workflow YAML.

## Requirements

- Java 21 (project currently executed with JDK 21 in local runs)
- Maven
- OpenAI-compatible endpoint (for example OpenRouter or LM Studio)

## Configuration

### Environment variables

Common runtime settings:
- `RALPHLOOP_BASE_URL`
- `RALPHLOOP_API_KEY`
- `RALPHLOOP_MODEL_NAME`
- `RALPHLOOP_CONNECT_TIMEOUT_SECONDS`
- `RALPHLOOP_READ_TIMEOUT_SECONDS`
- `RALPHLOOP_WORKFLOW_PATH` (optional override of default workflow path)
- `RALPHLOOP_SUMMARY_MODEL_NAME` (optional fallback summary model)
- `RALPHLOOP_SUMMARY_BASE_URL` (optional fallback summary endpoint)
- `RALPHLOOP_SUMMARY_MAX_OUTPUT_TOKENS` (optional fallback summary output cap)

### Agent profile fields

Supported YAML fields under `agent_profile`:
- `model_name`
- `base_url`
- `connect_timeout_minutes`
- `read_timeout_minutes`
- `output_type`
- `max_output_tokens`
- `summary_model_name`
- `summary_base_url`
- `summary_max_output_tokens`

Environment placeholders are supported in YAML values using `${ENV:default}` syntax.

Summary model precedence:
1. `agent_profile.summary_*` fields in agent YAML
2. `RALPHLOOP_SUMMARY_*` environment variables
3. Built-in defaults in `DeclarativePromptAgent`

## Build and Test

Build package:

```bash
mvn -DskipTests package
```

Run tests:

```bash
mvn test
```

Current project test suite includes workflow-builder coverage for loop routing and human-gate branches.

## Run the Application

Minimal run:

```bash
mvn -q -DskipTests exec:java -Dexec.mainClass="io.forest.ralphloop.Application"
```

Example with explicit endpoint/model:

```bash
/usr/bin/env \
	RALPHLOOP_BASE_URL=https://openrouter.ai/api/v1 \
	RALPHLOOP_MODEL_NAME=nvidia/nemotron-3-nano-30b-a3b:free \
	RALPHLOOP_API_KEY=<your-key> \
	RALPHLOOP_READ_TIMEOUT_SECONDS=300 \
	RALPHLOOP_CONNECT_TIMEOUT_SECONDS=10 \
	mvn -q exec:java -Dexec.mainClass="io.forest.ralphloop.Application" -DskipTests
```

What you should see at startup:
- workflow loaded
- graph built
- graph compiled
- invocation started

## Human-in-the-Loop Behavior

When the reviewer marks output valid, the workflow routes to `human_gate`.

`HumanApprovalAgent` supports:
- `approve`: finish successfully
- `rework`: loop back to drafter (within iteration budget)
- `reject`: terminate

Optional free-text human feedback is stored in state and can be injected into subsequent drafting prompts.

## Token and Context Controls

To reduce token spend and context growth, the current runtime includes:
- per-agent `max_output_tokens`
- hierarchical prompt compression in `DeclarativePromptAgent`
	- large context fields are chunked and summarized with a smaller summary model
	- multi-pass reduction is applied until target size is reached
	- fallback truncation is used if summarization fails
- compact reviewer output contract
- compressed architect/reviewer prompts

These controls help keep iterative runs stable and cost-aware.

## Troubleshooting

### Placeholder in URL errors

If you see URI parsing errors with `${...}` text in URL fields, verify env vars are set and placeholders are valid.

### JSON parse failures from reviewer

Reviewer output must be valid JSON matching expected shape. Tighten prompt or lower output tokens if malformed output appears.

### Long/hanging runs

Check:
- endpoint reachability
- API key validity
- timeout settings
- model responsiveness

### Loop exits earlier than expected

Review:
- `max_iterations`
- condition routing in workflow YAML
- reviewer `isValid` behavior

## Project Layout

```text
src/main/java/io/forest/ralphloop/
	Application.java
	State.java
	agent/
	builder/
	config/
	model/

src/main/resources/
	workflows/
	agents/

src/test/java/io/forest/ralphloop/
	builder/
```

## Next Extensions

- Add runtime telemetry for token/input-output size per node.
- Add integration tests with a mock OpenAI-compatible endpoint.
- Add a license file if publishing externally.

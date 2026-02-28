LangChain4J Guardrails Example

This small example demonstrates applying input and output guardrails to an
AI service built with LangChain4J. It shows two common techniques for
hardening prompts and responses against prompt injection and accidental
secret leakage:

- Prompt-injection detection: an `InjectionDetector` service evaluates
  incoming user text and returns a short verdict. The `PromptInjectionGuardrail`
  treats verdicts that start with `unsafe` as malicious and blocks the request.

- Canary tokens: `CanaryTokenInputGuardrail` injects a per-request `CANARY-...`
  token into the prompt and stores it in `CanaryContext`. After the model
  responds, `CanaryTokenOutputGuardrail` checks whether the response contains
  the token and rejects the output if it does, forcing a rewrite.

Files

- `Application.java` - example runnable that wires an `InjectionDetector`
  and an `Assistant` and demonstrates sending a message through the guardrails.
- `Assistant.java` - minimal service interface used by the example.
- `InjectionDetector.java` - contract for a service that detects prompt
  injection; implementations should return a short verdict string.
- `PromptInjectionGuardrail.java` - an input guardrail delegating to the
  `InjectionDetector`.
- `CanaryContext.java` - thread-local storage for per-request canary tokens.
- `CanaryTokenInputGuardrail.java` - input guardrail that inserts a canary
  token and security boundary into the prompt.
- `CanaryTokenOutputGuardrail.java` - output guardrail that inspects responses
  for canary tokens and clears the token afterwards.

Running the example

1. Update `Application.java` with your model endpoint, API key and desired
   model name (the file contains placeholder values).

2. Build with Maven (from project root):

```bash
mvn -DskipTests package
```

3. Run the example:

```bash
java -cp target/classes:$(mvn -q -DskipTests dependency:build-classpath -DincludeScope=runtime -Dsilent -DoutputFile=/dev/stdout) \
  io.forest.langchain4j.guardrails.Application
```

Notes

- This example is for demonstration purposes only. Do not log sensitive
  API keys or production secrets. The canary tokens are logged at INFO
  primarily for debugging — adjust logging according to your security
  policy.

- Consider adding unit tests for the guardrails and an actual
  implementation of `InjectionDetector` tailored to your threat model.

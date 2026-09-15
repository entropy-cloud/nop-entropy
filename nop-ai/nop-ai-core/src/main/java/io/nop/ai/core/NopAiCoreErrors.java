package io.nop.ai.core;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface NopAiCoreErrors {
    String ARG_DETAIL = "detail";

    ErrorCode ERR_AI_CORE_INVALID_ARGUMENT = define("nop.err.ai.core.invalid-argument",
            "Invalid argument: {detail}", ARG_DETAIL);

    ErrorCode ERR_AI_CORE_INVALID_STATE = define("nop.err.ai.core.invalid-state",
            "Invalid state: {detail}", ARG_DETAIL);

    String ARG_LLM_NAME = "llmName";
    String ARG_OPTION_NAME = "optionName";
    String ARG_HTTP_STATUS = "httpStatus";

    String ARG_PROP_PATH = "propPath";

    String ARG_EXPECTED = "expected";
    String ARG_LINE = "line";

    String ARG_NAME = "name";
    String ARG_VALUE = "value";

    String ARG_CONTENT = "content";

    String ARG_INPUT_NAME = "inputName";
    String ARG_OUTPUT_NAME = "outputName";

    String ARG_BLOCK_BEGIN = "blockBegin";
    String ARG_BLOCK_END = "blockEnd";

    String ARG_CONFIG_VAR = "configVar";

    String ARG_PROMPT_NAME = "promptName";
    String ARG_VAR_NAME = "varName";

    String ARG_DEFINED_VARS = "definedVars";

    String ARG_PREFIX = "prefix";

    String ARG_INPUT = "input";

    String ARG_TOOL_NAME = "toolName";

    String ARG_COMMAND = "command";
    String ARG_NODE_NAME = "nodeName";
    String ARG_FILE_PATH = "filePath";

    String ARG_PARSE_MODEL = "parseModel";

    String ARG_ROLE = "role";

    String ARG_SESSION_ID = "sessionId";

    String ARG_MODEL_CLASS = "modelClass";

    /**
     * Migrated verbatim from {@code io.nop.ai.agent.NopAiAgentErrors}
     * (W2 reliability sink-down, plan 2026-08-15-0604-2). The error code ID
     * {@code nop.err.ai.agent.invalid-arg} is deliberately preserved: it is a
     * persisted/logged error-code ID, renaming it would change behavior. Only
     * the holder class moves to nop-ai-core; the remaining agent-specific
     * codes stay in {@code NopAiAgentErrors}.
     */
    String ARG_MSG = "msg";

    ErrorCode ERR_AI_SERVICE_NO_DEFAULT_LLMS =
            define("nop.err.ai.service.no-default-llms",
                    "No LLM is specified for the call and no default LLM is configured via nop.ai.service.default-llm");

    /**
     * Migrated verbatim from {@code io.nop.ai.agent.NopAiAgentErrors} (W2
     * reliability sink-down, plan 2026-08-15-0604-2): used by the migrated
     * reliability classes (ThresholdBreaker / ProviderFailoverQueue /
     * StandardRetryPolicy / RetryContext / RetryOutcome). Error code ID
     * preserved; only the holder class moves to nop-ai-core.
     */
    ErrorCode ERR_AI_AGENT_INVALID_ARG =
            define("nop.err.ai.agent.invalid-arg", "invalid argument: {msg}", ARG_MSG);

    ErrorCode ERR_AI_SERVICE_NO_BASE_URL =
            define("nop.err.ai.service.no-base-url", "LLM {llmName} has no baseUrl configured", ARG_LLM_NAME);

    ErrorCode ERR_AI_SERVICE_OPTION_NOT_SET =
            define("nop.err.ai.service.option-not-set", "Option {optionName} is not set for LLM {llmName}",
                    ARG_LLM_NAME, ARG_OPTION_NAME);

    ErrorCode ERR_AI_SERVICE_HTTP_ERROR =
            define("nop.err.ai.service.http-error", "LLM {llmName} call failed, HTTP status={httpStatus}",
                    ARG_LLM_NAME, ARG_HTTP_STATUS);

    /**
     * Local rate-limit rejection (MA6.3-AR-6): thrown by
     * {@code ChatServiceImpl.checkRateLimit} when the in-memory token bucket
     * cannot grant a permit within the configured acquire timeout
     * ({@code nop.ai.service.rate-limit-acquire-timeout}).
     *
     * <p>Deliberately carries {@code ARG_HTTP_STATUS = 429} so
     * {@code LlmErrorClassifier} classifies it as RATE_LIMITED (retryable with
     * backoff) — a local quota rejection is exactly the 429 semantic. It must
     * NEVER carry any other 4xx status: a non-429 4xx would classify as
     * NON_TRANSIENT and wrongly fail fast (MA6.3-AR-6 adjudication).
     */
    ErrorCode ERR_AI_RATE_LIMITED =
            define("nop.err.ai.service.rate-limited", "LLM {llmName} call rate-limited (local quota exhausted)",
                    ARG_LLM_NAME);

    ErrorCode ERR_AI_RESULT_IS_EMPTY =
            define("nop.err.ai.service.result-is-empty", "The result returned by the LLM is empty");

    ErrorCode ERR_AI_RESULT_INVALID_END_LINE =
            define("nop.err.ai.service.result-invalid-end-line",
                    "The result line returned by the LLM does not match the expected pattern", ARG_EXPECTED, ARG_LINE);

    ErrorCode ERR_AI_RESULT_NO_EXPECTED_PART =
            define("nop.err.ai.service.result-no-expected-part",
                    "The result returned by the LLM does not match the expected pattern, missing content: {expected}",
                    ARG_EXPECTED);

    ErrorCode ERR_AI_RESULT_INVALID_NUMBER =
            define("nop.err.ai.service.result-invalid-number",
                    "The result returned by the LLM is not a number: name={name},value={value}", ARG_NAME, ARG_VALUE);

    ErrorCode ERR_AI_TOOLS_INVALID_THOUGHT =
            define("nop.err.ai.tools.invalid-thought", "Invalid thought request: {value}", ARG_VALUE);

    /**
     * Converted from bare {@code IllegalArgumentException} throws (plan
     * 2026-08-01-0936-3): English descriptions preserve the historical
     * message semantics verbatim (AGENTS.md English error-message
     * convention for newly added codes).
     */
    ErrorCode ERR_AI_TOOLS_INVALID_PROJECT_NAME =
            define("nop.err.ai.tools.invalid-project-name",
                    "projectName must be valid file directory name:{value}", ARG_VALUE);

    ErrorCode ERR_AI_TOOLS_THOUGHT_EMPTY =
            define("nop.err.ai.tools.thought-empty", "Thought cannot be empty");

    ErrorCode ERR_AI_TOOLS_INVALID_THOUGHT_NUMBER =
            define("nop.err.ai.tools.invalid-thought-number", "Thought number must be positive");

    ErrorCode ERR_AI_TOOLS_INVALID_TOTAL_THOUGHTS =
            define("nop.err.ai.tools.invalid-total-thoughts", "Total thoughts must be positive");

    ErrorCode ERR_AI_TOOLS_TOTAL_THOUGHTS_LESS_THAN_NUMBER =
            define("nop.err.ai.tools.total-thoughts-less-than-number",
                    "Total thoughts must be >= thought number");

    ErrorCode ERR_AI_TOOLS_INVALID_STAGE =
            define("nop.err.ai.tools.invalid-stage", "Invalid ThoughtStage: {value}", ARG_VALUE);

    ErrorCode ERR_AI_TOOLS_INVALID_MAX_RESULTS =
            define("nop.err.ai.tools.invalid-max-results", "maxResults must be positive");

    ErrorCode ERR_AI_INVALID_RESPONSE =
            define("nop.err.ai.service.invalid-response", "The response returned by the LLM is invalid");

    ErrorCode ERR_AI_MANDATORY_INPUT_IS_EMPTY = define("nop.err.ai.mandatory-input-is-empty",
            "Input parameter {inputName} must not be empty", ARG_INPUT_NAME);

    ErrorCode ERR_AI_MANDATORY_OUTPUT_IS_EMPTY = define("nop.err.ai.mandatory-output-is-empty",
            "Output parameter {outputName} must not be empty", ARG_OUTPUT_NAME);

    ErrorCode ERR_AI_PROMPT_USE_UNDEFINED_VAR = define("nop.err.ai.prompt-var-not-defined",
            "The prompt uses undefined variable {varName}", ARG_PROMPT_NAME, ARG_VAR_NAME);

    ErrorCode ERR_AI_UNKNOWN_PROMPT_EXPR_PREFIX = define("nop.err.ai.prompt-expr-prefix-unknown",
            "Unknown prompt expression prefix: {prefix}", ARG_PREFIX);

    ErrorCode ERR_AI_NO_VAR_IN_SCOPE = define("nop.err.ai.no-var-in-scope",
            "No corresponding variable {varName} exists in the context", ARG_VAR_NAME);

    ErrorCode ERR_AI_PROMPT_UNCLOSED_EXPR =
            define("nop.err.ai.prompt-unclosed-expr", "The brackets of the prompt expression are not properly matched");

    ErrorCode ERR_AI_PROMPT_EMPTY_EXPR =
            define("nop.err.ai.prompt-empty-expr", "The prompt expression is empty");

    ErrorCode ERR_AI_INVALID_EXPR_VAR_NAME =
            define("nop.err.ai.invalid-expr-var-name", "Invalid variable name in prompt expression: {varName}",
                    ARG_VAR_NAME);

    ErrorCode ERR_AI_UNKNOWN_TOOL_CALL =
            define("nop.err.ai.unknown-tool-call", "The tool being called is not registered: {toolName}", ARG_TOOL_NAME);

    ErrorCode ERR_AI_FILE_CONTENT_NO_PATH = define("nop.err.ai.file-content.no-path",
            "The file object has no path attribute specified");

    ErrorCode ERR_AI_COMMAND_NOT_FOUND =
            define("nop.err.ai.command.not-found", "Command not found: {command}", ARG_COMMAND);

    ErrorCode ERR_AI_EMPTY_TOOLS_NODE =
            define("nop.err.ai.command.empty-tools-node", "The call-tools node is empty");

    ErrorCode ERR_AI_TOOLS_NODE_PARSE_FAILED =
            define("nop.err.ai.command.tools-node-parse-failed", "Failed to parse the call-tools node");

    ErrorCode ERR_AI_FILE_PATH_IS_EMPTY =
            define("nop.err.ai.command.file-path-empty", "File path must not be empty", ARG_NODE_NAME);

    /**
     * Deprecated {@code IAiChatService.getSession} entry point (plan
     * 2026-07-31-2248-2 scan-hollow baseline clearance): the method is
     * deprecated in favour of {@code IChatService}; the English description
     * preserves the historical UOE message semantics (AGENTS.md English
     * error-message convention for newly added codes).
     */
    ErrorCode ERR_AI_CHAT_GET_SESSION_DEPRECATED =
            define("nop.err.ai.service.get-session-deprecated", "Deprecated: use IChatService instead");

    /**
     * Converted from bare {@code IllegalArgumentException} throws (plan
     * 2026-08-01-0936-2): English descriptions preserve the historical
     * message semantics verbatim (AGENTS.md English error-message
     * convention for newly added codes).
     */
    ErrorCode ERR_AI_FILE_INVALID_EDIT_TYPE =
            define("nop.err.ai.file.invalid-edit-type", "Invalid edit type");

    ErrorCode ERR_AI_PROMPT_TEMPLATE_NULL =
            define("nop.err.ai.prompt-template-null", "prompt template is null");

    ErrorCode ERR_AI_UNSUPPORTED_PARSE_FROM_RESPONSE =
            define("nop.err.ai.prompt-unsupported-parse-from-response", "unsupported parseFromResponse: {parseModel}", ARG_PARSE_MODEL);

    ErrorCode ERR_AI_UNKNOWN_ROLE =
            define("nop.err.ai.unknown-role", "unknown role:{role}", ARG_ROLE);

    ErrorCode ERR_AI_VECTOR_LENGTH_MISMATCH =
            define("nop.err.ai.vector-length-mismatch", "Length of vector a  must be equal to the length of vector b");

    ErrorCode ERR_AI_SESSION_ID_IS_EMPTY =
            define("nop.err.ai.session-id-empty", "sessionId must not be null or empty (path-traversal guard)");

    ErrorCode ERR_AI_SESSION_ID_INVALID =
            define("nop.err.ai.session-id-invalid",
                    "sessionId contains invalid characters; only [A-Za-z0-9_-] are allowed (path-traversal guard): sessionId={sessionId}",
                    ARG_SESSION_ID);

    /**
     * ThoughtStorage export/import path-containment guard (plan
     * 2026-09-14-1937-1 Phase 1): the caller-supplied filePath is used
     * verbatim for read/write, so it must resolve inside the configured
     * storage directory. English description by plan adjudication (fail-fast
     * guard, mirrors {@link #ERR_AI_SESSION_ID_INVALID}).
     */
    ErrorCode ERR_AI_TOOLS_SESSION_FILE_PATH_INVALID =
            define("nop.err.ai.tools.session-file-path-invalid",
                    "filePath must be located inside the thought-storage directory (path-traversal guard): filePath={filePath}",
                    ARG_FILE_PATH);

    /**
     * Model-class routing pool saturation (plan 2026-08-15-0849-2, design §3.3
     * Q8/Q10 adjudication, Phase 1): thrown by {@code ModelClassRouter} when no
     * candidate in the current model class is available. The saturation
     * semantic covers BOTH concurrency saturation (every candidate has reached
     * its concurrency limit) and health saturation (every candidate's circuit
     * is OPEN / otherwise unavailable) — one code, semantic = "no available
     * candidate", with the triggering model class carried as a parameter.
     * English description by plan adjudication (cross-module consumed by
     * W6/W7 orchestration; not subject to i18n rewriting).
     */
    ErrorCode ERR_AI_MODEL_CLASS_SATURATED =
            define("nop.err.ai.model-class.saturated",
                    "No candidate is currently available for model class: {modelClass}", ARG_MODEL_CLASS);
}

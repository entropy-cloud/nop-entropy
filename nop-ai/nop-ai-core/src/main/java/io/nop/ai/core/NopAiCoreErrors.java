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

    /**
     * 通用期限预算参数（毫秒）。3.1-C7-001 流式全程期限/inactivity 终态使用。
     */
    String ARG_TIMEOUT = "timeout";

    String ARG_PROP_PATH = "propPath";


    String ARG_NAME = "name";

    String ARG_EXPECTED = "expected";
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


    String ARG_FILE_PATH = "filePath";

    String ARG_PARSE_MODEL = "parseModel";

    String ARG_ROLE = "role";

    String ARG_SESSION_ID = "sessionId";

    String ARG_MODEL_CLASS = "modelClass";

    /**
     * Migrated verbatim from {@code io.nop.ai.agent.NopAiAgentErrors}
     * (W2 reliability sink-down, plan 2026-08-15-0604-2). The error code ID
     * {@code nop.err.ai.agent.invalid-arg} is deliberately preserved: it is a
     * persisted/logged error-code ID, renaming it would change behavior.
     *
     * <p><b>WI9 re-adjudication (plan 2438, 2026-10-11)</b>: the holder stays
     * in nop-ai-core. Consumers span three modules (core reliability +
     * LlmConfigHelper, gateway routing/failover, agent), and nop-ai-core
     * cannot depend on nop-ai-agent (that would invert the agent→core layering),
     * so the roadmap's literal "return to NopAiAgentErrors" is not reachable.
     * The code is therefore classified as core-owned with a historical
     * agent-prefixed ID (the owner classes — the reliability set — live in
     * core). Restart condition: if the reliability classes ever move back to
     * nop-ai-agent, this code moves back to {@code NopAiAgentErrors} with
     * them. See also docs-for-ai/02-core-guides/error-handling.md (the
     * cross-module {@code nop.err.ai.agent.*} namespace is adjudicated as
     * intentional there).
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

    /**
     * 3.1-C7-001（plan 2505，两侧同批消费面）：流式调用超过全程总期限。超时经
     * subscription.cancel + future.completeExceptionally 显式终态（R21 纪律，非静默）。
     */
    ErrorCode ERR_AI_STREAM_TOTAL_TIMEOUT =
            define("nop.err.ai.stream.total-timeout",
                    "LLM {llmName} stream did not finish within the total deadline (timeout={timeout} ms)",
                    ARG_LLM_NAME, ARG_TIMEOUT);

    /**
     * 3.1-C7-001（plan 2505）：流式 chunk 间静默超过 inactivity 上界。与传输层 idle
     * 读超时构成两侧同批双层预算（4.3 merge-adjudication §七-6）。
     */
    ErrorCode ERR_AI_STREAM_IDLE_TIMEOUT =
            define("nop.err.ai.stream.idle-timeout",
                    "LLM {llmName} stream inactive for {timeout} ms (no chunk within the inactivity budget)",
                    ARG_LLM_NAME, ARG_TIMEOUT);

    /**
     * 3.1-C7-002（plan 2505，R11 错误以成功交付族纪律）：流式中途干净半关闭
     * （无 finish_reason/[DONE] 终止信号）不得以成功响应交付——按传输级错误
     * completeExceptionally（带截断字符数诊断），fail-loud 不静默成功（rule 24）。
     */
    ErrorCode ERR_AI_STREAM_TERMINATION_SIGNAL_MISSING =
            define("nop.err.ai.stream.termination-signal-missing",
                    "LLM {llmName} stream closed without a termination signal (finishReason/DONE missing); "
                            + "truncated content is not delivered as success, accumulatedChars={detail}",
                    ARG_LLM_NAME, ARG_DETAIL);

    /**
     * 3.1-C3-001 ②（plan 2505，背压恢复）：流式桥接 publisher 缓冲满载且下游停滞——
     * fail-loud 终态化（替代 SubmissionPublisher.submit 的不可中断阻塞，慢下游显式可见）。
     */
    ErrorCode ERR_AI_STREAM_BUFFER_OVERFLOW =
            define("nop.err.ai.stream.buffer-overflow",
                    "LLM {llmName} stream buffer overflow: downstream subscriber stalled, "
                            + "stream terminated fail-loud (no silent drop)",
                    ARG_LLM_NAME);

    /**
     * 3.1-C3-001 ⑤（plan 2505，聚合上界）：单次流式调用累积超配置上界
     * （字符总量或 tool-call 条目数）。超界 fail-fast 终态化流。
     */
    ErrorCode ERR_AI_STREAM_ACCUMULATION_EXCEEDED =
            define("nop.err.ai.stream.accumulation-exceeded",
                    "LLM stream accumulation exceeded the configured limit: {detail}",
                    ARG_DETAIL);

    ErrorCode ERR_AI_RESULT_IS_EMPTY =
            define("nop.err.ai.service.result-is-empty", "The result returned by the LLM is empty");

    ErrorCode ERR_AI_RESULT_NO_EXPECTED_PART =
            define("nop.err.ai.service.result-no-expected-part",
                    "The result returned by the LLM does not match the expected pattern, missing content: {expected}",
                    ARG_EXPECTED);

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

    ErrorCode ERR_AI_FILE_CONTENT_NO_PATH = define("nop.err.ai.file-content.no-path",
            "The file object has no path attribute specified");

    ErrorCode ERR_AI_PROMPT_TEMPLATE_NULL =
            define("nop.err.ai.prompt-template-null", "prompt template is null");

    ErrorCode ERR_AI_UNSUPPORTED_PARSE_FROM_RESPONSE =
            define("nop.err.ai.prompt-unsupported-parse-from-response", "unsupported parseFromResponse: {parseModel}", ARG_PARSE_MODEL);

    ErrorCode ERR_AI_SESSION_ID_IS_EMPTY =
            define("nop.err.ai.session-id-empty", "sessionId must not be null or empty (path-traversal guard)");

    ErrorCode ERR_AI_SESSION_ID_INVALID =
            define("nop.err.ai.session-id-invalid",
                    "sessionId contains invalid characters; only [A-Za-z0-9_-] are allowed (path-traversal guard): sessionId={sessionId}",
                    ARG_SESSION_ID);

    String ARG_API_STYLE = "apiStyle";

    /**
     * K1 embedding client (plan knowledge-rag/01): the resolved provider's
     * llm.xml declares no {@code <embedUrl>}, so embedding calls cannot be
     * routed. Fail-loud (no silent skip).
     */
    ErrorCode ERR_AI_EMBEDDING_NO_EMBED_URL =
            define("nop.err.ai.embedding.no-embed-url",
                    "LLM {llmName} has no embedUrl configured for embedding calls", ARG_LLM_NAME);

    /**
     * K1: neither {@code EmbeddingOptions.provider} nor the
     * {@code nop.ai.embedding.default-llm} config variable resolved a provider.
     */
    ErrorCode ERR_AI_EMBEDDING_NO_PROVIDER =
            define("nop.err.ai.embedding.no-provider",
                    "No embedding provider specified and no default configured via nop.ai.embedding.default-llm");

    /**
     * K1: the provider responded 200 but the OpenAI-compatible payload carried
     * no {@code data} entries (or fewer than requested).
     */
    ErrorCode ERR_AI_EMBEDDING_EMPTY_RESPONSE =
            define("nop.err.ai.embedding.empty-response",
                    "Embedding response from LLM {llmName} contains no data entries", ARG_LLM_NAME);

    /**
     * K1: QUOTA_EXCEEDED/AUTH_INVALID falled through the whole
     * {@code <accounts>} backup chain without a successful attempt.
     */
    ErrorCode ERR_AI_EMBEDDING_ACCOUNT_CHAIN_EXHAUSTED =
            define("nop.err.ai.embedding.account-chain-exhausted",
                    "LLM {llmName} backup account chain exhausted for embedding call", ARG_LLM_NAME);

    /**
     * K1: v1 embedding client implements the OpenAI-compatible protocol only;
     * other apiStyle values fail loud instead of silently sending a
     * wrong-shaped request.
     */
    ErrorCode ERR_AI_EMBEDDING_UNSUPPORTED_API_STYLE =
            define("nop.err.ai.embedding.unsupported-api-style",
                    "Embedding client supports apiStyle=openai only, LLM {llmName} has apiStyle={apiStyle}",
                    ARG_LLM_NAME, ARG_API_STYLE);
}

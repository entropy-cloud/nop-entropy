package io.nop.ai.tools;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

/**
 * Error codes owned by the nop-ai-tools module.
 *
 * <p>Relocated verbatim from {@code io.nop.ai.core.NopAiCoreErrors} (WI9
 * error-code relocation, plan 2438): every main consumer of these codes lives
 * in nop-ai-tools, so the holder class moves here. Error code IDs and English
 * descriptions are deliberately preserved verbatim — they are
 * persisted/logged contracts; renaming would change behavior. The matching
 * zh-CN i18n yaml keys in nop-runner/nop-cli-core are keyed by ID and are
 * unaffected.
 */
public interface NopAiToolsErrors {
    String ARG_VALUE = "value";

    String ARG_FILE_PATH = "filePath";

    ErrorCode ERR_AI_TOOLS_INVALID_THOUGHT =
            define("nop.err.ai.tools.invalid-thought", "Invalid thought request: {value}", ARG_VALUE);

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

    /**
     * ThoughtStorage export/import path-containment guard (plan
     * 2026-09-14-1937-1 Phase 1): the caller-supplied filePath is used
     * verbatim for read/write, so it must resolve inside the configured
     * storage directory. English description by plan adjudication (fail-fast
     * guard, mirrors the sessionId path-traversal guards in
     * {@code NopAiCoreErrors}).
     */
    ErrorCode ERR_AI_TOOLS_SESSION_FILE_PATH_INVALID =
            define("nop.err.ai.tools.session-file-path-invalid",
                    "filePath must be located inside the thought-storage directory (path-traversal guard): filePath={filePath}",
                    ARG_FILE_PATH);
}

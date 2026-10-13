package io.nop.ai.code_analyzer;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

/**
 * Error codes for the nop-ai-code-analyzer module.
 * <p>
 * P2-MA3-4: bare {@code RuntimeException} throws were converted to
 * {@code NopException} with these codes (2026-07-31).
 */
public interface NopAiCodeAnalyzerErrors {
    String ARG_MSG = "msg";

    ErrorCode ERR_STATS_IO_FAILED =
            define("nop.err.ai.code-analyzer.stats-io-failed", "statistics collection failed: {msg}", ARG_MSG);

    ErrorCode ERR_MAVEN_PARSE_INVALID_ARG =
            define("nop.err.ai.code-analyzer.maven-parse-invalid-arg", "invalid maven dependency input: {msg}", ARG_MSG);
}

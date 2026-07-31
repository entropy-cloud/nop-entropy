package io.nop.ai.shell;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

/**
 * Error codes for the nop-ai-shell module.
 *
 * <p>Created by plan 2026-07-31-2248-2 (scan-hollow baseline clearance):
 * shell I/O type-mismatch guards that historically failed fast with
 * {@code UnsupportedOperationException} now fail fast with
 * {@link io.nop.api.core.exceptions.NopException} carrying these codes.
 * All descriptions are English (AGENTS.md error-message convention; the
 * historical UOE messages were English, so {@code getMessage()} semantics are
 * unchanged).
 */
public interface NopAiShellErrors {

    ErrorCode ERR_AI_SHELL_OUTPUT_NOT_INPUT =
            define("nop.err.ai.shell.output-not-input", "PrintStreamOutputAdapter cannot be used as input");

    ErrorCode ERR_AI_SHELL_CHUNK_NOT_TEXT =
            define("nop.err.ai.shell.chunk-not-text", "Not a text chunk");

    ErrorCode ERR_AI_SHELL_TEE_NO_OUTPUT =
            define("nop.err.ai.shell.tee-no-output", "No output available to convert to input");
}

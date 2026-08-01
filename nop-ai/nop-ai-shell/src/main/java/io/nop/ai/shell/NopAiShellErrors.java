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

    String ARG_MSG = "msg";
    String ARG_COMMAND_NAME = "commandName";

    ErrorCode ERR_AI_SHELL_OUTPUT_NOT_INPUT =
            define("nop.err.ai.shell.output-not-input", "PrintStreamOutputAdapter cannot be used as input");

    ErrorCode ERR_AI_SHELL_CHUNK_NOT_TEXT =
            define("nop.err.ai.shell.chunk-not-text", "Not a text chunk");

    ErrorCode ERR_AI_SHELL_TEE_NO_OUTPUT =
            define("nop.err.ai.shell.tee-no-output", "No output available to convert to input");

    // ========================================================================
    // P3-MA3-1: bare IllegalArgumentException validation guards (plan
    // 2026-08-01-0936-1). All 12 historical IAE throw sites converted to
    // NopException carrying these codes; verbatim English messages are carried
    // via {msg}/{commandName} so getMessage() semantics are unchanged.
    // ========================================================================

    ErrorCode ERR_AI_SHELL_INVALID_ARG =
            define("nop.err.ai.shell.invalid-arg", "invalid argument: {msg}", ARG_MSG);

    ErrorCode ERR_AI_SHELL_COMMAND_NOT_FOUND =
            define("nop.err.ai.shell.command-not-found", "Command not found: {commandName}", ARG_COMMAND_NAME);

    ErrorCode ERR_AI_SHELL_EMPTY_COMMAND =
            define("nop.err.ai.shell.empty-command", "empty command: {msg}", ARG_MSG);

    ErrorCode ERR_AI_SHELL_INVALID_REDIRECT =
            define("nop.err.ai.shell.invalid-redirect", "invalid redirect: {msg}", ARG_MSG);

    ErrorCode ERR_AI_SHELL_UNKNOWN_SYMBOL =
            define("nop.err.ai.shell.unknown-symbol", "unknown symbol: {msg}", ARG_MSG);
}

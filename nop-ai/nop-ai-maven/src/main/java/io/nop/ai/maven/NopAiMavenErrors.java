package io.nop.ai.maven;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

/**
 * Error codes for the nop-ai-maven module.
 * <p>
 * P2-MA3-2: all bare {@code IllegalArgumentException}/{@code RuntimeException}
 * throws in this module were converted to {@code NopException} with these codes
 * (2026-07-31) so that the Nop framework's global exception handler can process
 * them.
 */
public interface NopAiMavenErrors {
    String ARG_MSG = "msg";
    String ARG_PATH = "path";

    ErrorCode ERR_VFS_INVALID_ARG =
            define("nop.err.ai.maven.vfs-invalid-arg", "invalid argument: {msg}", ARG_MSG);

    ErrorCode ERR_VFS_IO_FAILED =
            define("nop.err.ai.maven.vfs-io-failed", "VFS operation failed: {msg}", ARG_MSG);
}

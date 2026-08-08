package io.nop.ai.toolkit.tools.sandbox;

import java.util.Objects;

/**
 * Immutable result of a single {@link IBashSandbox#execute} call (plan 335 DR-3a). Mirrors the
 * observable output shape (exit code + stdout + stderr) with a {@code timedOut} flag set when the
 * wall-time budget was exhausted and the backend forcibly terminated the process/container.
 *
 * <p>{@code stdout}/{@code stderr} have already been truncated to
 * {@link BashSandboxConfig#getMaxOutputBytes()} by the backend.
 */
public final class BashSandboxResult {

    private final int exitCode;
    private final String stdout;
    private final String stderr;
    private final boolean timedOut;

    public BashSandboxResult(int exitCode, String stdout, String stderr, boolean timedOut) {
        this.exitCode = exitCode;
        this.stdout = Objects.requireNonNull(stdout, "stdout must not be null (use \"\")");
        this.stderr = Objects.requireNonNull(stderr, "stderr must not be null (use \"\")");
        this.timedOut = timedOut;
    }

    public int getExitCode() {
        return exitCode;
    }

    public String getStdout() {
        return stdout;
    }

    public String getStderr() {
        return stderr;
    }

    public boolean isTimedOut() {
        return timedOut;
    }

    @Override
    public String toString() {
        return "BashSandboxResult{exitCode=" + exitCode + ", timedOut=" + timedOut
                + ", stdoutLen=" + stdout.length() + ", stderrLen=" + stderr.length() + '}';
    }
}

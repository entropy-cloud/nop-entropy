package io.nop.ai.agent.security;

import java.util.Objects;

/**
 * Immutable result of a single {@link ISandboxBackend#execute} call (plan
 * 219 Phase 1). Mirrors the ProcessBuilder / Docker CLI observable output
 * shape (exit code + stdout + stderr) with two additional
 * sandbox-specific fields: {@code executionTimeMs} (wall clock the
 * backend observed) and {@code timedOut} (the wall-time budget was
 * exhausted and the backend forcibly terminated the process/container).
 *
 * <p>The {@code stdout}/{@code stderr} payloads have already been
 * truncated to {@link SandboxConfig#getMaxOutputBytes()} by the backend —
 * callers cannot exceed the configured ceiling regardless of how chatty
 * the command is. Truncation is silent (the run still completes with the
 * captured prefix); a caller that wants to detect it can compare the
 * captured length to {@link SandboxConfig#getMaxOutputBytes()} or observe
 * that {@code timedOut} is {@code false} while the payload ends mid-line.
 *
 * <p>Note on the relationship to nop-ai-shell's {@code ExecutionResult}:
 * the shapes are intentionally similar (exitCode/stdout/stderr) but live
 * in different modules and carry different semantic fields — they are
 * NOT aliased or reused across modules (plan 219 Phase 1 Decision).
 */
public final class SandboxResult {

    private final int exitCode;
    private final String stdout;
    private final String stderr;
    private final long executionTimeMs;
    private final boolean timedOut;

    public SandboxResult(int exitCode, String stdout, String stderr,
                         long executionTimeMs, boolean timedOut) {
        this.exitCode = exitCode;
        this.stdout = Objects.requireNonNull(stdout, "stdout must not be null (use \"\")");
        this.stderr = Objects.requireNonNull(stderr, "stderr must not be null (use \"\")");
        this.executionTimeMs = executionTimeMs;
        this.timedOut = timedOut;
    }

    /** Convenience for the common successful-completion case. */
    public static SandboxResult success(int exitCode, String stdout, String stderr,
                                        long executionTimeMs) {
        return new SandboxResult(exitCode, stdout, stderr, executionTimeMs, false);
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

    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    public boolean isTimedOut() {
        return timedOut;
    }

    @Override
    public String toString() {
        return "SandboxResult{exitCode=" + exitCode
                + ", executionTimeMs=" + executionTimeMs
                + ", timedOut=" + timedOut
                + ", stdoutLen=" + stdout.length()
                + ", stderrLen=" + stderr.length() + '}';
    }
}

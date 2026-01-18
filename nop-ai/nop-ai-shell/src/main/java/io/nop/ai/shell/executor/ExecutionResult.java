package io.nop.ai.shell.executor;

public class ExecutionResult {
    private final String stdout;
    private final String stderr;
    private final int exitCode;
    private final Exception exception;

    public ExecutionResult(String stdout, String stderr, int exitCode) {
        this(stdout, stderr, exitCode, null);
    }

    public ExecutionResult(String stdout, String stderr, int exitCode, Exception exception) {
        this.stdout = stdout;
        this.stderr = stderr;
        this.exitCode = exitCode;
        this.exception = exception;
    }

    public String stdout() {
        return stdout;
    }

    public String stderr() {
        return stderr;
    }

    public int exitCode() {
        return exitCode;
    }

    public Exception exception() {
        return exception;
    }

    public boolean isSuccess() {
        return exception == null && exitCode == 0;
    }
}

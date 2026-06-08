package io.nop.ai.shell.executor;

public class ExecutionResult {
    private final int exitCode;
    private final String stdout;
    private final String stderr;

    public ExecutionResult(int exitCode, String stdout, String stderr) {
        this.exitCode = exitCode;
        this.stdout = stdout;
        this.stderr = stderr;
    }

    public int exitCode() {
        return exitCode;
    }

    public String stdout() {
        return stdout;
    }

    public String stderr() {
        return stderr;
    }
}

package io.nop.ai.shell.executor;

import java.util.concurrent.Callable;

/**
 * Plan 2301 (WI25): the sink that gives shell {@code &} background jobs an
 * addressable lifecycle. {@code cmd &} historically started the job and lost
 * it (instance-counted jobId, no query/wait/cancel/output recovery — the
 * start-and-lose defect). With a sink wired, {@code &} submits through it and
 * returns the sink's taskId; the sink owner (the agent engine's background
 * task registry, via the test-wiring adapter) provides snapshot / stop /
 * completion-notification closure. Without a sink, {@code &} fails loud
 * (non-zero exit) instead of silently losing the job.
 *
 * <p><b>Module direction</b>: shell must not depend on the agent module, so
 * the sink is an SPI implemented on the agent side and injected here
 * (setter injection on {@code ShellCommandExecutor}).
 *
 * <p><b>Lifecycle ownership</b>: a submitted job is owned by the sink — the
 * executor's {@code close()} no longer cancels {@code &} jobs (they are
 * addressable and cancellable through the sink's taskId channel instead).
 */
public interface IBackgroundJobSink {

    /**
     * Fork the job body off the caller's thread and return its addressable id
     * immediately. The body must be invoked exactly once; its ExecutionResult
     * (exit code / stdout / stderr) is delivered through the sink's own
     * completion channel, never back to this call.
     *
     * @param commandText the backgrounded command text (observability only —
     *                    the body is the pre-parsed execution, not a re-parse)
     * @param body        the execution body; may throw — the sink maps the
     *                    throwable to a FAILED completion
     * @return the addressable task id (e.g. a registry taskId)
     */
    String submit(String commandText, Callable<ExecutionResult> body);
}

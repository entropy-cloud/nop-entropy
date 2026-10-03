package io.nop.ai.agent.tool;

import io.nop.ai.shell.executor.ExecutionResult;
import io.nop.ai.shell.executor.IBackgroundJobSink;
import io.nop.ai.toolkit.api.IBackgroundTaskRegistry;

import java.util.concurrent.Callable;

/**
 * Plan 2301 (WI25): bridges shell {@code &} background jobs to the background
 * task registry — the closure that replaces the start-and-lose defect. The
 * shell's {@code &} submits here; the returned id is a registry taskId, so
 * snapshot / task-output / task-stop / completion notification all work
 * unchanged.
 *
 * <p><b>Test-wiring only</b> (plan 2301): nop-ai-shell has no production
 * consumer (orphan module — Known Residual); this adapter lives in the agent
 * test sources and the {@code nop-ai-shell} dependency is test-scoped. A
 * production embedding wires the same one-liner at its engine assembly point.
 */
public class BackgroundRegistryJobSink implements IBackgroundJobSink {

    /** {@code &} jobs get the same 1h-class budget as background bash. */
    public static final long JOB_TIMEOUT_MS = 3_600_000L;

    private final IBackgroundTaskRegistry registry;
    private final String sessionId;

    public BackgroundRegistryJobSink(IBackgroundTaskRegistry registry, String sessionId) {
        this.registry = registry;
        this.sessionId = sessionId;
    }

    @Override
    public String submit(String commandText, Callable<ExecutionResult> body) {
        String taskId = registry.submit("shell-&", sessionId, null, JOB_TIMEOUT_MS);
        registry.run(taskId, () -> {
            try {
                ExecutionResult result = body.call();
                String output = (result.stdout() == null ? "" : result.stdout())
                        + (result.stderr() == null ? "" : result.stderr());
                registry.complete(taskId,
                        result.exitCode() == 0 ? IBackgroundTaskRegistry.STATUS_COMPLETED
                                : IBackgroundTaskRegistry.STATUS_FAILED,
                        output);
            } catch (Exception e) {
                registry.complete(taskId, IBackgroundTaskRegistry.STATUS_FAILED,
                        "background job failed: " + e);
            }
        });
        return taskId;
    }
}

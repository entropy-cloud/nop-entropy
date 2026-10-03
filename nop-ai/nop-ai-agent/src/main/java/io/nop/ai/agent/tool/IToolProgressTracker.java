package io.nop.ai.agent.tool;

/**
 * Plan 2285 (WI4): agent-side seam for addressing a running tool execution by
 * its provider callId — heartbeat progress evidence and abandon-time process
 * cancellation (design nop-ai-agent-async-tool-execution.md §三.6 进度证据接缝).
 *
 * <p>The shipped production adapter wraps the toolkit {@code IBashSandbox}
 * seam (the tracker assembly MUST share the same sandbox instance the
 * executing {@code BashExecutor} holds, or callId lookups resolve to empty).
 * The default {@link NoOpToolProgressTracker} keeps the engine honest when no
 * tracker is wired: snapshots are explicitly empty and cancellation reports
 * false — capability absent, never silently pretended.
 *
 * <p>WI25 (tool-level background mode) reuses this same tracker for its
 * background-task registry evidence source.
 */
public interface IToolProgressTracker {

    /**
     * Snapshot of the in-flight execution registered under the callId.
     * Returns {@link ToolProgressSnapshot#empty()} when unavailable — an
     * explicit value, never null.
     */
    ToolProgressSnapshot progressSnapshot(String callId);

    /**
     * Cancels the in-flight execution (future-level cancel is the caller's
     * job; this is the process-level kill channel). Returns false when the
     * callId has no cancellable registration.
     */
    boolean cancelExecution(String callId);
}

package io.nop.ai.toolkit.tools.sandbox;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Plan 2285 (WI4): callId-addressed registry of in-flight bash executions.
 * Sandbox implementations register on {@code execute} entry (process handle +
 * incremental output buffer reference) and unregister in the {@code finally}
 * path, so heartbeat progress snapshots and abandon-time process cancellation
 * can address a running command by its tool callId (design
 * nop-ai-agent-async-tool-execution.md §三.6 进度证据接缝).
 *
 * <p>Thread safety: concurrent executions register/lookup from different
 * threads — the map is concurrent; each entry's buffer is an atomic
 * reference maintained by the sandbox's reader thread.
 */
public final class BashExecutionTracker {

    private static final int TAIL_CHARS = 2000;

    private static final class InFlight {
        final Process process;
        final AtomicReference<StringBuilder> capturedRef;
        /** extra cancellation hook (e.g. docker container name) — nullable */
        final Runnable cancelAction;

        InFlight(Process process, AtomicReference<StringBuilder> capturedRef, Runnable cancelAction) {
            this.process = process;
            this.capturedRef = capturedRef;
            this.cancelAction = cancelAction;
        }
    }

    private final Map<String, InFlight> inFlight = new ConcurrentHashMap<>();

    /**
     * Registers an in-flight execution. Later registrations for the same
     * callId replace earlier ones (callIds are provider-unique per call, so
     * replacement only occurs on caller misuse — last-writer-wins keeps the
     * map bounded).
     */
    public void register(String callId, Process process, AtomicReference<StringBuilder> capturedRef,
                         Runnable cancelAction) {
        if (callId == null || process == null) {
            return;
        }
        inFlight.put(callId, new InFlight(process, capturedRef, cancelAction));
    }

    public void unregister(String callId) {
        if (callId != null) {
            inFlight.remove(callId);
        }
    }

    /**
     * Snapshot of the registered execution's incremental output. Returns
     * {@link BashProgressSnapshot#empty()} for unknown/finished callIds —
     * an explicit value, not null.
     */
    public BashProgressSnapshot progressSnapshot(String callId) {
        InFlight execution = callId == null ? null : inFlight.get(callId);
        if (execution == null) {
            return BashProgressSnapshot.empty();
        }
        StringBuilder captured = execution.capturedRef.get();
        if (captured == null) {
            return new BashProgressSnapshot("", 0L, true);
        }
        String full = captured.toString();
        String tail = full.length() <= TAIL_CHARS ? full : full.substring(full.length() - TAIL_CHARS);
        return new BashProgressSnapshot(tail, full.length(), true);
    }

    /**
     * Cancels the registered execution: destroys the process (and runs the
     * extra cancellation hook, e.g. {@code docker kill}). Returns false when
     * the callId has no in-flight registration.
     */
    public boolean cancelExecution(String callId) {
        InFlight execution = callId == null ? null : inFlight.remove(callId);
        if (execution == null) {
            return false;
        }
        execution.process.descendants().forEach(ProcessHandle::destroyForcibly);
        execution.process.destroyForcibly();
        if (execution.cancelAction != null) {
            try {
                execution.cancelAction.run();
            } catch (RuntimeException e) {
                // cancellation must surface its failure, not silently drop it
                throw e;
            }
        }
        return true;
    }
}

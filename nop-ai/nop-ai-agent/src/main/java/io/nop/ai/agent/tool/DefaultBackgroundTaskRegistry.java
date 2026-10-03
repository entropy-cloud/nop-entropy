package io.nop.ai.agent.tool;

import io.nop.ai.api.secure.SecureDefault;
import io.nop.ai.toolkit.api.IBackgroundTaskRegistry;
import io.nop.api.core.time.CoreMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Plan 2301 (WI25): process-wide background task registry — the addressable
 * state machine behind {@code runInBackground}. One instance per engine
 * (Builder-owned, flowed into every AgentToolExecuteContext).
 *
 * <p><b>State machine</b>: RUNNING → COMPLETED / FAILED / TIMEOUT (via
 * {@link #complete}, idempotent — first terminal wins). After a terminal
 * state the bridge attempts the completion notification exactly once
 * ({@code notificationSent}); a failed bridge attempt moves the task to
 * NOTIFICATION_FAILED and the next {@link #snapshot}/{@link #readOutput}
 * retries the bridge lazily (no dangling retry timer — round 1 review M2).
 *
 * <p><b>Output capture</b>: bounded in-memory tail ({@value #OUTPUT_TAIL_LIMIT}
 * chars) + total byte count — the nop simplification of zcode's output-file
 * route (file offload registered as a Known Residual, plan 2301).
 *
 * <p><b>Stop channel</b>: prefers the progress-tracker process-kill channel
 * ({@code cancelExecution(callId)}, design §三.6) when a tracker hook is
 * installed; falls back to cancelling the registered future. Idempotent for
 * terminal tasks.
 *
 * <p>Secure-by-default posture: bounded output capture (8KB tail), fail-loud
 * on unknown taskIds (null snapshot, callers surface the error), exactly-once
 * notification claim, idempotent terminal transitions.
 */
@SecureDefault
public class DefaultBackgroundTaskRegistry implements IBackgroundTaskRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultBackgroundTaskRegistry.class);

    public static final int OUTPUT_TAIL_LIMIT = 8 * 1024;

    /**
     * Terminal-state bridge hook: invoked with (task entry, final status) after
     * every state transition to a terminal value. The engine installs a bridge
     * that injects the completion notification into the session input channel
     * (FOLLOWUP). Registry-owned state updates (notificationSent /
     * NOTIFICATION_FAILED) happen inside {@link #complete} around this hook.
     */
    private final BiConsumer<Entry, String> bridge;

    private final ConcurrentHashMap<String, Entry> tasks = new ConcurrentHashMap<>();
    private final AtomicLong idCounter = new AtomicLong();

    /** Progress-tracker kill channel (optional — installed by the engine wiring). */
    private volatile BiConsumer<String, String> trackerCancelHook;

    /**
     * Background fork executor (plan 2301 WI25): owned by the engine wiring
     * (a dedicated pool — background forks must not occupy the foreground
     * dispatch pool). Null = {@link #run} fails loud.
     */
    private volatile ExecutorService backgroundExecutor;

    /**
     * Live-tail evidence hook for RUNNING tasks (optional — wired from the
     * engine's IToolProgressTracker; explicit null = live evidence
     * unavailable, snapshots fall back to the captured-at-completion tail).
     */
    private volatile Function<String, ToolProgressSnapshot> progressSnapshotHook;

    public DefaultBackgroundTaskRegistry(BiConsumer<Entry, String> bridge) {
        this.bridge = bridge;
    }

    public void setTrackerCancelHook(BiConsumer<String, String> trackerCancelHook) {
        this.trackerCancelHook = trackerCancelHook;
    }

    /** Engine wiring: install the dedicated background fork executor. */
    public void setBackgroundExecutor(ExecutorService executor) {
        this.backgroundExecutor = executor;
    }

    /** Engine wiring: install the live progress-evidence hook. */
    public void setProgressSnapshotHook(Function<String, ToolProgressSnapshot> hook) {
        this.progressSnapshotHook = hook;
    }

    @Override
    public String submit(String toolName, String sessionId, String callId, long timeoutMs) {
        String taskId = "bg-" + idCounter.incrementAndGet();
        tasks.put(taskId, new Entry(taskId, toolName, sessionId, callId, timeoutMs));
        return taskId;
    }

    @Override
    public void run(String taskId, Runnable body) {
        Entry entry = tasks.get(taskId);
        if (entry == null) {
            throw new IllegalArgumentException("Unknown background taskId: " + taskId);
        }
        ExecutorService exec = backgroundExecutor;
        if (exec == null) {
            throw new IllegalStateException(
                    "Background task executor is not wired (runInBackground is unavailable in this runtime)");
        }
        try {
            Future<?> future = exec.submit(() -> {
                try {
                    body.run();
                } catch (Exception e) {
                    // Safety net: a body that throws without completing must
                    // not leave the task RUNNING forever. Idempotent — the
                    // body's own terminal complete() wins when it ran first.
                    LOG.warn("DefaultBackgroundTaskRegistry: task {} body failed without completing", taskId, e);
                    complete(taskId, STATUS_FAILED, "background body failed: " + e);
                }
            });
            entry.future = future;
        } catch (RejectedExecutionException e) {
            throw new IllegalStateException("Background task executor rejected the fork for " + taskId, e);
        }
    }

    @Override
    public void complete(String taskId, String status, String outputText) {
        Entry entry = tasks.get(taskId);
        if (entry == null) {
            LOG.warn("DefaultBackgroundTaskRegistry.complete: unknown taskId={}", taskId);
            return;
        }
        String finalStatus;
        synchronized (entry) {
            if (!STATUS_RUNNING.equals(entry.status)) {
                return; // idempotent: first terminal state wins
            }
            entry.status = status;
            captureOutput(entry, outputText);
            // Bridge exactly once: claim BEFORE invoking so a re-entrant
            // complete() cannot double-inject; on bridge failure the task
            // moves to NOTIFICATION_FAILED (fail-visible) and lazy redelivery
            // happens on the next snapshot/readOutput.
            if (bridge != null && !entry.notificationSent) {
                try {
                    bridge.accept(entry, status);
                    entry.notificationSent = true;
                } catch (Exception e) {
                    entry.status = STATUS_NOTIFICATION_FAILED;
                    LOG.warn("DefaultBackgroundTaskRegistry: completion notification failed for task {} "
                            + "(will retry on next snapshot/readOutput)", taskId, e);
                }
            }
            finalStatus = entry.status;
        }
        entry.done.countDown();
        LOG.debug("DefaultBackgroundTaskRegistry: task {} -> {} ({} bytes)", taskId, finalStatus,
                entry.totalBytes);
    }

    private void captureOutput(Entry entry, String outputText) {
        if (outputText == null) {
            return;
        }
        entry.totalBytes = outputText.length();
        entry.outputTail = outputText.length() <= OUTPUT_TAIL_LIMIT
                ? outputText
                : outputText.substring(outputText.length() - OUTPUT_TAIL_LIMIT);
    }

    @Override
    public TaskSnapshot snapshot(String taskId) {
        Entry entry = tasks.get(taskId);
        if (entry == null) {
            return null;
        }
        retryBridgeIfNeeded(entry);
        synchronized (entry) {
            String tail = entry.outputTail;
            long bytes = entry.totalBytes;
            if (STATUS_RUNNING.equals(entry.status)) {
                // live evidence from the progress tracker while the task is
                // in flight (design §三.6 seam); explicit null hook or
                // not-in-flight snapshot falls back to the captured tail
                ToolProgressSnapshot live = liveSnapshot(entry);
                if (live != null && live.isInFlight()) {
                    tail = live.getOutputTail();
                    bytes = live.getOutputBytes();
                }
            }
            return new TaskSnapshot(entry.taskId, entry.toolName, entry.sessionId, entry.status,
                    entry.submitTime, entry.timeoutMs, tail, bytes,
                    entry.notificationSent);
        }
    }

    private ToolProgressSnapshot liveSnapshot(Entry entry) {
        Function<String, ToolProgressSnapshot> hook = progressSnapshotHook;
        if (hook == null || entry.callId == null) {
            return null;
        }
        try {
            return hook.apply(entry.callId);
        } catch (Exception e) {
            LOG.debug("DefaultBackgroundTaskRegistry: live progress snapshot failed for task {}",
                    entry.taskId, e);
            return null;
        }
    }

    @Override
    public String readOutput(String taskId) {
        Entry entry = tasks.get(taskId);
        if (entry == null) {
            return null;
        }
        retryBridgeIfNeeded(entry);
        synchronized (entry) {
            return entry.outputTail;
        }
    }

    /** Lazy redelivery: a NOTIFICATION_FAILED task retries its bridge on read. */
    private void retryBridgeIfNeeded(Entry entry) {
        synchronized (entry) {
            if (!STATUS_NOTIFICATION_FAILED.equals(entry.status) || bridge == null || entry.notificationSent) {
                return;
            }
            try {
                bridge.accept(entry, entry.status);
                entry.notificationSent = true;
                entry.status = STATUS_COMPLETED;
                LOG.debug("DefaultBackgroundTaskRegistry: lazy notification redelivery succeeded for {}",
                        entry.taskId);
            } catch (Exception e) {
                LOG.debug("DefaultBackgroundTaskRegistry: lazy notification redelivery still failing for {}",
                        entry.taskId, e);
            }
        }
    }

    @Override
    public String stop(String taskId) {
        Entry entry = tasks.get(taskId);
        if (entry == null) {
            return null;
        }
        synchronized (entry) {
            if (!STATUS_RUNNING.equals(entry.status)) {
                return entry.status; // idempotent: terminal tasks report their status
            }
        }
        // Prefer the progress-tracker process-kill channel (design §三.6):
        // future.cancel may not kill a sandbox child process.
        BiConsumer<String, String> hook = trackerCancelHook;
        if (hook != null && entry.callId != null) {
            try {
                hook.accept(entry.sessionId, entry.callId);
            } catch (Exception e) {
                LOG.warn("DefaultBackgroundTaskRegistry: tracker cancel failed for task {} — "
                        + "falling back to future cancel", taskId, e);
                cancelFuture(entry);
            }
        } else {
            cancelFuture(entry);
        }
        complete(taskId, STATUS_FAILED, entry.outputTail);
        return STATUS_FAILED;
    }

    private void cancelFuture(Entry entry) {
        if (entry.future != null) {
            entry.future.cancel(true);
        }
    }

    /**
     * Register the running future so {@link #stop} can cancel it (fallback
     * channel when no tracker hook is installed).
     */
    public void attachFuture(String taskId, java.util.concurrent.Future<?> future) {
        Entry entry = tasks.get(taskId);
        if (entry != null) {
            entry.future = future;
        }
    }

    public int size() {
        return tasks.size();
    }

    /**
     * Raw status observation without the lazy bridge-redelivery side effect —
     * the ops/test view of the fail-visible NOTIFICATION_FAILED state.
     */
    public String rawStatus(String taskId) {
        Entry entry = tasks.get(taskId);
        return entry == null ? null : entry.status;
    }

    /**
     * Mutable entry — guarded by synchronized(entry) for status/output/claim.
     * Public: the engine's completion bridge reads identity fields (taskId/
     * toolName/sessionId) directly.
     */
    public static final class Entry {
        public final String taskId;
        public final String toolName;
        public final String sessionId;
        public final String callId;
        public final long submitTime = CoreMetrics.currentTimeMillis();
        public final long timeoutMs;
        volatile String status = STATUS_RUNNING;
        /**
         * Public: the engine's completion bridge reads the captured tail
         * directly (via registry.snapshot would re-enter the lazy
         * redelivery path). Guarded by synchronized(entry) for writes.
         */
        public volatile String outputTail;
        public volatile long totalBytes;
        volatile boolean notificationSent;
        volatile java.util.concurrent.Future<?> future;
        final java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);

        Entry(String taskId, String toolName, String sessionId, String callId, long timeoutMs) {
            this.taskId = taskId;
            this.toolName = toolName;
            this.sessionId = sessionId;
            this.callId = callId;
            this.timeoutMs = timeoutMs;
        }
    }
}

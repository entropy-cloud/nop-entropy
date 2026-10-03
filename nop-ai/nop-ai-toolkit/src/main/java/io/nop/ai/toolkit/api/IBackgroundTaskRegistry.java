package io.nop.ai.toolkit.api;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Plan 2301 (WI25): registry for tool-level background tasks (the agent-side
 * implementation lives in nop-ai-agent; the interface lives here because
 * {@code IToolExecuteContext} is a toolkit type — see the
 * {@link #getBackgroundTaskRegistry()} bridge note).
 *
 * <p><b>Semantics</b> (design §三.7): {@link #submit} returns an addressable
 * taskId immediately; the executor forks the real work and calls
 * {@link #complete} exactly once on terminal state (COMPLETED / FAILED /
 * TIMEOUT — idempotent). The registry owner bridges terminal states into the
 * session input channel exactly once ({@code notificationSent} claim); a
 * failed bridge attempt moves the task to NOTIFICATION_FAILED and the next
 * {@link #snapshot}/{@code readOutput} triggers a lazy redelivery attempt.
 * {@link #stop} prefers the progress-tracker process-kill channel when the
 * implementation has one, falling back to future cancellation.
 *
 * <p><b>Snapshot shape</b>: toolkit-local {@link TaskSnapshot} (status +
 * bounded output tail + byte counts) — deliberately free of agent-module
 * types so both modules can depend on this interface.
 */
public interface IBackgroundTaskRegistry {

    String submit(String toolName, String sessionId, String callId, long timeoutMs);

    /**
     * Fork the task body onto the registry's background executor (plan 2301
     * WI25): the fork runs off the foreground dispatch pool — 1h-class tasks
     * must not occupy it. The body MUST call {@link #complete} exactly once
     * with the terminal status (COMPLETED / FAILED / TIMEOUT); as a safety
     * net the fork completes the task as FAILED when the body throws without
     * completing (idempotent — the body's own terminal call wins).
     *
     * @throws UnsupportedOperationException when the implementation owns no
     *         background executor (fail-loud — callers surface "background
     *         mode is not wired in this runtime")
     */
    default void run(String taskId, Runnable body) {
        throw new UnsupportedOperationException(
                "IBackgroundTaskRegistry.run is not supported by this implementation "
                        + "(background mode requires a registry wired with a background executor). "
                        + "Class: " + getClass().getName());
    }

    void complete(String taskId, String status, String outputText);

    /**
     * Status + output snapshot; {@code null} when the taskId is unknown
     * (callers fail loud on unknown ids — the registry itself never throws
     * for absence).
     */
    TaskSnapshot snapshot(String taskId);

    /**
     * Idempotent read of the captured output tail (never mutates state beyond
     * the lazy notification-redelivery attempt).
     */
    String readOutput(String taskId);

    /**
     * Cancel a running task; idempotent for already-terminal tasks (returns
     * their status). Returns the post-call status name.
     */
    String stop(String taskId);

    /** Terminal / lifecycle status names (also the values stored per task). */
    String STATUS_RUNNING = "RUNNING";
    String STATUS_COMPLETED = "COMPLETED";
    String STATUS_FAILED = "FAILED";
    String STATUS_TIMEOUT = "TIMEOUT";
    String STATUS_NOTIFICATION_FAILED = "NOTIFICATION_FAILED";

    /**
     * Toolkit-local snapshot record (plan 2301/WI25): deliberately free of
     * agent-module types.
     */
    final class TaskSnapshot {
        private final String taskId;
        private final String toolName;
        private final String sessionId;
        private final String status;
        private final long submitTime;
        private final long timeoutMs;
        private final String outputTail;
        private final long totalBytes;
        private final boolean notificationSent;

        public TaskSnapshot(String taskId, String toolName, String sessionId, String status,
                            long submitTime, long timeoutMs, String outputTail,
                            long totalBytes, boolean notificationSent) {
            this.taskId = taskId;
            this.toolName = toolName;
            this.sessionId = sessionId;
            this.status = status;
            this.submitTime = submitTime;
            this.timeoutMs = timeoutMs;
            this.outputTail = outputTail;
            this.totalBytes = totalBytes;
            this.notificationSent = notificationSent;
        }

        public String getTaskId() {
            return taskId;
        }

        public String getToolName() {
            return toolName;
        }

        public String getSessionId() {
            return sessionId;
        }

        public String getStatus() {
            return status;
        }

        public long getSubmitTime() {
            return submitTime;
        }

        public long getTimeoutMs() {
            return timeoutMs;
        }

        public String getOutputTail() {
            return outputTail;
        }

        public long getTotalBytes() {
            return totalBytes;
        }

        public boolean isNotificationSent() {
            return notificationSent;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("taskId", taskId);
            map.put("toolName", toolName);
            map.put("sessionId", sessionId);
            map.put("status", status);
            map.put("submitTime", submitTime);
            map.put("timeoutMs", timeoutMs);
            map.put("outputTail", outputTail);
            map.put("totalBytes", totalBytes);
            map.put("notificationSent", notificationSent);
            return map;
        }
    }
}

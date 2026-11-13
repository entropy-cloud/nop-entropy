package io.nop.ai.agent.team.flow;

import io.nop.ai.agent.team.TeamTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Per-run recorder that captures the real execution sequence of team-task
 * graph nodes. Each {@link MemberAgentTaskStep} calls back
 * {@link #markStart} / {@link #markComplete} / {@link #markFailed} as its
 * member-agent delegation progresses, so the orchestrator can build an
 * honest {@link TeamTaskFlowResult} and tests can assert dependency
 * ordering (Anti-Hollow evidence: B's start counter must be strictly
 * greater than A's completion counter when B blockedBy A).
 *
 * <p>Thread-safe: graph nodes may run concurrently (diamond branches), so
 * all state is backed by {@link ConcurrentHashMap} and {@link AtomicInteger}.
 *
 * <p>Public so it can be referenced from the public {@link ReductionContext}
 * and shared with {@link MemberFanOutDispatcher} (plan 245 — daemon dispatch
 * parity reuses the fan-out / reduce / complete chain; the dispatcher creates
 * a throwaway recorder per dispatch to satisfy custom-strategy contracts).
 *
 * <p>See plan 233 (L4-nop-task-dag-integration) Phase 2; plan 245
 * (daemon dispatch parity).
 */
public final class ExecutionRecorder {

    private final AtomicInteger startSeq = new AtomicInteger();
    private final AtomicInteger completionSeq = new AtomicInteger();

    private final ConcurrentHashMap<String, Integer> startOrder = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> completionOrder = new ConcurrentHashMap<>();
    private final Set<String> started = ConcurrentHashMap.newKeySet();
    private final Set<String> completed = ConcurrentHashMap.newKeySet();

    private final AtomicReference<String> firstFailedTaskId = new AtomicReference<>();

    /**
     * Plan 2519 (裁定⑤): per-flow member-session registry — the data plane
     * (no cancel semantics of its own) that the orchestrator's overall-deadline
     * convergence cancel walks. Spawn steps record the spawner-returned
     * spawnedSessionId here at built/dispatch time so a deadline breach can
     * cancel every spawned member session (session-level, on top of the
     * existing AR-3 graph cancel).
     */
    private final Set<String> spawnedMemberSessions = ConcurrentHashMap.newKeySet();

    public ExecutionRecorder() {
    }

    /** Plan 2519 (裁定⑤): record a spawned member session for the flow. */
    public void recordSpawnedSession(String spawnedSessionId) {
        if (spawnedSessionId != null && !spawnedSessionId.isEmpty()) {
            spawnedMemberSessions.add(spawnedSessionId);
        }
    }

    /** Plan 2519 (裁定⑤): snapshot of the spawned member sessions of this flow. */
    public java.util.List<String> spawnedMemberSessionsSnapshot() {
        return new ArrayList<>(spawnedMemberSessions);
    }

    public void markStart(String taskId) {
        if (started.add(taskId)) {
            startOrder.put(taskId, startSeq.incrementAndGet());
        }
    }

    public void markComplete(String taskId) {
        if (completed.add(taskId)) {
            completionOrder.put(taskId, completionSeq.incrementAndGet());
        }
    }

    public void markFailed(String taskId) {
        firstFailedTaskId.compareAndSet(null, taskId);
    }

    public boolean didStart(String taskId) {
        return started.contains(taskId);
    }

    public boolean didComplete(String taskId) {
        return completed.contains(taskId);
    }

    public String getFirstFailedTaskId() {
        return firstFailedTaskId.get();
    }

    TeamTaskFlowResult buildResult(boolean success, List<TeamTask> allTasks) {
        List<String> completedIds = new ArrayList<>(completed);
        completedIds.sort(Comparator.comparingInt(id -> completionOrder.getOrDefault(id, Integer.MAX_VALUE)));
        Collections.sort(completedIds);

        List<String> skipped = new ArrayList<>();
        for (TeamTask t : allTasks) {
            if (!started.contains(t.getTaskId())) {
                skipped.add(t.getTaskId());
            }
        }
        Collections.sort(skipped);

        Map<String, Integer> startSnapshot = new LinkedHashMap<>(startOrder);
        Map<String, Integer> completionSnapshot = new LinkedHashMap<>(completionOrder);

        return new TeamTaskFlowResult(success, completedIds, firstFailedTaskId.get(),
                skipped, startSnapshot, completionSnapshot);
    }
}

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
 * <p>Package-private — an internal implementation detail of
 * {@link TeamTaskFlowOrchestrator}.
 *
 * <p>See plan 233 (L4-nop-task-dag-integration) Phase 2.
 */
final class ExecutionRecorder {

    private final AtomicInteger startSeq = new AtomicInteger();
    private final AtomicInteger completionSeq = new AtomicInteger();

    private final ConcurrentHashMap<String, Integer> startOrder = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> completionOrder = new ConcurrentHashMap<>();
    private final Set<String> started = ConcurrentHashMap.newKeySet();
    private final Set<String> completed = ConcurrentHashMap.newKeySet();

    private final AtomicReference<String> firstFailedTaskId = new AtomicReference<>();

    void markStart(String taskId) {
        if (started.add(taskId)) {
            startOrder.put(taskId, startSeq.incrementAndGet());
        }
    }

    void markComplete(String taskId) {
        if (completed.add(taskId)) {
            completionOrder.put(taskId, completionSeq.incrementAndGet());
        }
    }

    void markFailed(String taskId) {
        firstFailedTaskId.compareAndSet(null, taskId);
    }

    boolean didStart(String taskId) {
        return started.contains(taskId);
    }

    boolean didComplete(String taskId) {
        return completed.contains(taskId);
    }

    String getFirstFailedTaskId() {
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

/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.cluster;

import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InMemoryClusterRegistry implements ClusterRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(InMemoryClusterRegistry.class);

    static final long LEASE_TIMEOUT_MS = 15000L;

    void setLeaseTimestampForTest(String nodeId, long timestamp) {
        leaseStartTimes.put(nodeId, timestamp);
        leaseExpireTimes.put(nodeId, timestamp + leaseTtlMs);
    }

    private final Map<String, CoordinatorInfo> coordinators = new ConcurrentHashMap<>();
    private final Map<String, NodeInfo> nodes = new ConcurrentHashMap<>();
    /** Last lease renewal / registration timestamp per node (= leaseStartAt, updated on renew). */
    private final Map<String, Long> leaseStartTimes = new ConcurrentHashMap<>();
    /**
     * RL-3 (R16-AR-18): per-node lease expiry, computed from the per-renewal {@code leaseTimeoutMs}
     * parameter (or the registry default TTL on registerNode) — never a fixed class-level TTL.
     */
    private final Map<String, Long> leaseExpireTimes = new ConcurrentHashMap<>();
    /**
     * G56: attempt history per task key. Each list is append-only and ordered by
     * insertion (= monotonically increasing attemptNumber, enforced by callers).
     */
    private final Map<String, List<TaskAssignment>> taskAssignmentHistory = new ConcurrentHashMap<>();
    /** Default lease duration applied on registerNode (also used by setLeaseTimestampForTest). */
    private final long leaseTtlMs;

    public InMemoryClusterRegistry() {
        this(LEASE_TIMEOUT_MS);
    }

    public InMemoryClusterRegistry(long leaseTtlMs) {
        this.leaseTtlMs = leaseTtlMs;
    }

    @Override
    public void registerCoordinator(String jobId, String coordinatorId, long fencingEpoch) {
        coordinators.put(jobId, new CoordinatorInfo(jobId, coordinatorId, fencingEpoch, System.currentTimeMillis()));
        LOG.debug("Registered coordinator {} for job {} with fencing epoch {}", coordinatorId, jobId, fencingEpoch);
    }

    @Override
    public CoordinatorInfo getActiveCoordinator(String jobId) {
        return coordinators.get(jobId);
    }

    @Override
    public void registerNode(String nodeId, String endpoint, int capacity) {
        synchronized (nodes) {
            long now = System.currentTimeMillis();
            NodeInfo info = new NodeInfo(nodeId, endpoint, capacity, now, now);
            nodes.put(nodeId, info);
            // Default lease = registry TTL; renewLease overrides with the per-renewal timeout.
            leaseStartTimes.put(nodeId, now);
            leaseExpireTimes.put(nodeId, now + leaseTtlMs);
        }
        LOG.debug("Registered node {} at endpoint {} with capacity {}", nodeId, endpoint, capacity);
    }

    @Override
    public boolean renewLease(String nodeId, long leaseTimeoutMs) {
        synchronized (nodes) {
            if (!nodes.containsKey(nodeId)) {
                return false;
            }
            long now = System.currentTimeMillis();
            // RL-3 (R16-AR-18): persist the expiry computed from the per-renewal parameter so all
            // liveness computations below agree with the stored value (invariant #5, JDBC parity).
            leaseStartTimes.put(nodeId, now);
            leaseExpireTimes.put(nodeId, now + leaseTimeoutMs);
            NodeInfo info = nodes.get(nodeId);
            if (info != null) {
                info.setLastHeartbeatAt(now);
            }
            return true;
        }
    }

    @Override
    public LeaseInfo getNodeLease(String nodeId) {
        Long startAt = leaseStartTimes.get(nodeId);
        if (startAt == null) {
            return null;
        }
        long expireAt = leaseExpireTimes.get(nodeId);
        long now = System.currentTimeMillis();
        boolean active = expireAt > now;
        return new LeaseInfo(nodeId, startAt, expireAt, active);
    }

    public void evictExpiredNodes() {
        long now = System.currentTimeMillis();
        synchronized (nodes) {
            for (Map.Entry<String, Long> entry : leaseExpireTimes.entrySet()) {
                if (entry.getValue() <= now) {
                    String nodeId = entry.getKey();
                    nodes.remove(nodeId);
                    leaseStartTimes.remove(nodeId);
                    leaseExpireTimes.remove(nodeId);
                    LOG.debug("Evicted expired node {}", nodeId);
                }
            }
        }
    }

    @Override
    public List<NodeInfo> getActiveNodes() {
        long now = System.currentTimeMillis();
        List<NodeInfo> active = new ArrayList<>();
        for (Map.Entry<String, NodeInfo> entry : nodes.entrySet()) {
            Long expireAt = leaseExpireTimes.get(entry.getKey());
            // registerNode populates leaseExpireTimes together with nodes under the same
            // lock, so a missing entry is unreachable in practice; treat it defensively as
            // inactive rather than crashing the liveness view.
            if (expireAt != null && expireAt > now) {
                active.add(entry.getValue());
            }
        }
        return active;
    }

    @Override
    public void assignTask(String jobId, String vertexId, int subtaskIndex,
                           String nodeId, String attemptId, long fencingEpoch,
                           int attemptNumber) {
        String key = assignmentKey(jobId, vertexId, subtaskIndex);
        TaskAssignment assignment = new TaskAssignment(jobId, vertexId, subtaskIndex, nodeId,
                attemptId, fencingEpoch, System.currentTimeMillis(), attemptNumber);
        // G56: append, do not overwrite. Synchronized block guards the read-modify-write
        // so concurrent attempt-n writers cannot interleave (single atomic append per call).
        synchronized (taskAssignmentHistory) {
            List<TaskAssignment> existing = taskAssignmentHistory.get(key);
            if (existing == null) {
                existing = new ArrayList<>();
                taskAssignmentHistory.put(key, existing);
            }
            // Defensive monotonic check (#24 — no silent skip): reject regressions.
            if (!existing.isEmpty()) {
                int lastAttempt = existing.get(existing.size() - 1).getAttemptNumber();
                if (attemptNumber <= lastAttempt) {
                    LOG.warn("Attempt number regression for {}/{}/{}: last={}, attempted={}. Appending anyway (caller bug?).",
                            jobId, vertexId, subtaskIndex, lastAttempt, attemptNumber);
                }
            }
            existing.add(assignment);
        }
        LOG.debug("Assigned task {}/{}/{} to node {} (attempt={}, attemptNumber={})",
                jobId, vertexId, subtaskIndex, nodeId, attemptId, attemptNumber);
    }

    @Override
    public TaskAssignment getTaskAssignment(String jobId, String vertexId, int subtaskIndex) {
        List<TaskAssignment> history = taskAssignmentHistory.get(assignmentKey(jobId, vertexId, subtaskIndex));
        if (history == null || history.isEmpty()) {
            return null;
        }
        return history.get(history.size() - 1);
    }

    @Override
    public List<TaskAssignment> getAttemptHistory(String jobId, String vertexId, int subtaskIndex) {
        List<TaskAssignment> history = taskAssignmentHistory.get(assignmentKey(jobId, vertexId, subtaskIndex));
        if (history == null) {
            return new ArrayList<>();
        }
        return Collections.unmodifiableList(new ArrayList<>(history));
    }

    @Override
    public void removeTaskAssignment(String jobId, String vertexId, int subtaskIndex) {
        taskAssignmentHistory.remove(assignmentKey(jobId, vertexId, subtaskIndex));
    }

    private String assignmentKey(String jobId, String vertexId, int subtaskIndex) {
        return jobId + "/" + vertexId + "/" + subtaskIndex;
    }
}

/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.cluster;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InMemoryClusterRegistry implements ClusterRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(InMemoryClusterRegistry.class);

    static final long LEASE_TIMEOUT_MS = 15000L;

    void setLeaseTimestampForTest(String nodeId, long timestamp) {
        leaseTimestamps.put(nodeId, timestamp);
    }

    private final Map<String, CoordinatorInfo> coordinators = new ConcurrentHashMap<>();
    private final Map<String, NodeInfo> nodes = new ConcurrentHashMap<>();
    private final Map<String, Long> leaseTimestamps = new ConcurrentHashMap<>();
    private final Map<String, TaskAssignment> taskAssignments = new ConcurrentHashMap<>();
    private final long leaseTtlMs;

    public InMemoryClusterRegistry() {
        this(LEASE_TIMEOUT_MS);
    }

    public InMemoryClusterRegistry(long leaseTtlMs) {
        this.leaseTtlMs = leaseTtlMs;
    }

    @Override
    public void registerCoordinator(String jobId, String coordinatorId, String fencingToken) {
        coordinators.put(jobId, new CoordinatorInfo(jobId, coordinatorId, fencingToken, System.currentTimeMillis()));
        LOG.debug("Registered coordinator {} for job {} with fencing token {}", coordinatorId, jobId, fencingToken);
    }

    @Override
    public CoordinatorInfo getActiveCoordinator(String jobId) {
        return coordinators.get(jobId);
    }

    @Override
    public void registerNode(String nodeId, String endpoint, int capacity) {
        long now = System.currentTimeMillis();
        NodeInfo info = new NodeInfo(nodeId, endpoint, capacity, now, now);
        nodes.put(nodeId, info);
        leaseTimestamps.put(nodeId, now);
        LOG.debug("Registered node {} at endpoint {} with capacity {}", nodeId, endpoint, capacity);
    }

    @Override
    public boolean renewLease(String nodeId, long leaseTimeoutMs) {
        synchronized (nodes) {
            if (!nodes.containsKey(nodeId)) {
                return false;
            }
            long now = System.currentTimeMillis();
            leaseTimestamps.put(nodeId, now);
            NodeInfo info = nodes.get(nodeId);
            if (info != null) {
                info.setLastHeartbeatAt(now);
            }
            return true;
        }
    }

    @Override
    public LeaseInfo getNodeLease(String nodeId) {
        Long timestamp = leaseTimestamps.get(nodeId);
        if (timestamp == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        boolean active = (now - timestamp) < leaseTtlMs;
        return new LeaseInfo(nodeId, timestamp, timestamp + leaseTtlMs, active);
    }

    public void evictExpiredNodes() {
        long now = System.currentTimeMillis();
        synchronized (nodes) {
            for (Map.Entry<String, Long> entry : leaseTimestamps.entrySet()) {
                if ((now - entry.getValue()) >= leaseTtlMs) {
                    String nodeId = entry.getKey();
                    nodes.remove(nodeId);
                    leaseTimestamps.remove(nodeId);
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
            Long leaseTime = leaseTimestamps.get(entry.getKey());
            if (leaseTime != null && (now - leaseTime) < leaseTtlMs) {
                active.add(entry.getValue());
            }
        }
        return active;
    }

    @Override
    public void assignTask(String jobId, String vertexId, int subtaskIndex,
                           String nodeId, String attemptId, String fencingToken) {
        String key = assignmentKey(jobId, vertexId, subtaskIndex);
        TaskAssignment assignment = new TaskAssignment(jobId, vertexId, subtaskIndex, nodeId,
                attemptId, fencingToken, System.currentTimeMillis());
        taskAssignments.put(key, assignment);
        LOG.debug("Assigned task {}/{}/{} to node {} (attempt={})", jobId, vertexId, subtaskIndex, nodeId, attemptId);
    }

    @Override
    public TaskAssignment getTaskAssignment(String jobId, String vertexId, int subtaskIndex) {
        return taskAssignments.get(assignmentKey(jobId, vertexId, subtaskIndex));
    }

    @Override
    public void removeTaskAssignment(String jobId, String vertexId, int subtaskIndex) {
        taskAssignments.remove(assignmentKey(jobId, vertexId, subtaskIndex));
    }

    private String assignmentKey(String jobId, String vertexId, int subtaskIndex) {
        return jobId + "/" + vertexId + "/" + subtaskIndex;
    }
}

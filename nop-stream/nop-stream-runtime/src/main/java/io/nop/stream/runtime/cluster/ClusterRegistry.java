/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.cluster;

import java.util.List;

/**
 * Registry for tracking cluster state: active coordinator, registered nodes,
 * node leases, and task assignments. Backed by JDBC for persistence.
 */
public interface ClusterRegistry {

    /**
     * Register or update the active coordinator for a job.
     *
     * @param jobId        the job identifier
     * @param coordinatorId unique coordinator identifier
     * @param fencingToken fencing token for leader election
     */
    void registerCoordinator(String jobId, String coordinatorId, String fencingToken);

    /**
     * Get the active coordinator info for a job.
     *
     * @param jobId the job identifier
     * @return coordinator info, or null if none registered
     */
    CoordinatorInfo getActiveCoordinator(String jobId);

    /**
     * Register a node in the cluster.
     *
     * @param nodeId   unique node identifier
     * @param endpoint node endpoint (host:port)
     * @param capacity max number of tasks the node can handle
     */
    void registerNode(String nodeId, String endpoint, int capacity);

    /**
     * Renew the lease for a node.
     *
     * @param nodeId        the node identifier
     * @param leaseTimeoutMs lease timeout in milliseconds from now
     * @return true if the lease was successfully renewed
     */
    boolean renewLease(String nodeId, long leaseTimeoutMs);

    /**
     * Get lease information for a node.
     *
     * @param nodeId the node identifier
     * @return lease info, or null if the node is not registered
     */
    LeaseInfo getNodeLease(String nodeId);

    /**
     * Get all active nodes (nodes with valid leases).
     *
     * @return list of active node info
     */
    List<NodeInfo> getActiveNodes();

    /**
     * Assign a task to a node.
     *
     * <p>G56: assignTask <strong>preserves attempt history</strong> — successive calls
     * for the same (jobId, vertexId, subtaskIndex) with monotonically increasing
     * {@code attemptNumber} do NOT overwrite prior records. Use
     * {@link #getAttemptHistory(String, String, int)} to read the full history; use
     * {@link #getTaskAssignment(String, String, int)} to read only the latest attempt.
     *
     * @param jobId        the job identifier
     * @param vertexId     the vertex identifier within the job
     * @param subtaskIndex the subtask index
     * @param nodeId       the node to assign to
     * @param attemptId    execution attempt identifier (UUID)
     * @param fencingToken fencing token for this assignment
     * @param attemptNumber monotonic attempt number per (job, vertex, subtask); starts at 1
     */
    void assignTask(String jobId, String vertexId, int subtaskIndex,
                    String nodeId, String attemptId, String fencingToken, int attemptNumber);

    /**
     * Backward-compatible overload defaulting {@code attemptNumber = 1}. Existing
     * callers that have not been migrated to attempt tracking still work; new
     * callers (JobCoordinator) must pass an explicit attempt number.
     */
    default void assignTask(String jobId, String vertexId, int subtaskIndex,
                            String nodeId, String attemptId, String fencingToken) {
        assignTask(jobId, vertexId, subtaskIndex, nodeId, attemptId, fencingToken, 1);
    }

    /**
     * Get the task assignment for a specific task. Returns the latest attempt
     * (highest {@code attemptNumber}) or null if never assigned.
     *
     * @param jobId        the job identifier
     * @param vertexId     the vertex identifier
     * @param subtaskIndex the subtask index
     * @return latest task assignment, or null if not assigned
     */
    TaskAssignment getTaskAssignment(String jobId, String vertexId, int subtaskIndex);

    /**
     * G56: Returns the full attempt history for a specific task, ordered by
     * {@code attemptNumber} monotonically increasing. Empty list if no attempts.
     *
     * @param jobId        the job identifier
     * @param vertexId     the vertex identifier
     * @param subtaskIndex the subtask index
     * @return unmodifiable list of all attempts in monotonic order
     */
    List<TaskAssignment> getAttemptHistory(String jobId, String vertexId, int subtaskIndex);

    /**
     * Remove a task assignment.
     *
     * @param jobId        the job identifier
     * @param vertexId     the vertex identifier
     * @param subtaskIndex the subtask index
     */
    void removeTaskAssignment(String jobId, String vertexId, int subtaskIndex);
}

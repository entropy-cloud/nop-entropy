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
     * @param jobId        the job identifier
     * @param vertexId     the vertex identifier within the job
     * @param subtaskIndex the subtask index
     * @param nodeId       the node to assign to
     * @param attemptId    execution attempt identifier
     * @param fencingToken fencing token for this assignment
     */
    void assignTask(String jobId, String vertexId, int subtaskIndex,
                    String nodeId, String attemptId, String fencingToken);

    /**
     * Get the task assignment for a specific task.
     *
     * @param jobId        the job identifier
     * @param vertexId     the vertex identifier
     * @param subtaskIndex the subtask index
     * @return task assignment, or null if not assigned
     */
    TaskAssignment getTaskAssignment(String jobId, String vertexId, int subtaskIndex);

    /**
     * Remove a task assignment.
     *
     * @param jobId        the job identifier
     * @param vertexId     the vertex identifier
     * @param subtaskIndex the subtask index
     */
    void removeTaskAssignment(String jobId, String vertexId, int subtaskIndex);
}

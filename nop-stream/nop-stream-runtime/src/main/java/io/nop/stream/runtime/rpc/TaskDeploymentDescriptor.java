/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.rpc;

import java.io.Serializable;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.stream.core.execution.plan.DeploymentPlan;
import io.nop.stream.core.jobgraph.JobGraph;

/**
 * Stage 42 Phase 0: serializable task deployment descriptor sent from the
 * coordinator to a TaskManager via the {@link IStreamTaskRpcService#deployTask}
 * RPC method when <em>remote-deploy mode</em> is active.
 *
 * <p>The descriptor carries the serializable model metadata a TaskManager JVM
 * needs to <strong>build its own {@link io.nop.stream.core.execution.StreamTaskInvokable}
 * locally</strong> — it does NOT carry live runtime objects. Because every
 * TaskManager JVM shares the same classpath (same JARs), the receiving
 * TaskManager reconstructs its operators from the {@link JobGraph} (which is
 * {@link Serializable} and carries the per-vertex {@link io.nop.stream.core.jobgraph.OperatorChain}
 * templates) and wires its input/output channels using the deterministic topic
 * naming derived from {@code jobId + edgeId + subtaskIndex} (see
 * {@link io.nop.stream.runtime.transport.StreamTopicNaming}). The resulting
 * topic names match the coordinator's view exactly, so a subtask built locally
 * on the TaskManager connects to the same data-plane topics the coordinator
 * would have wired.
 *
 * <p>This resolves the cross-JVM task deployment gap documented in the Stage 42
 * plan's Current Baseline: the in-process path calls
 * {@code TaskManager.installInvokable(StreamTaskInvokable)} as a direct Java
 * method call, but a {@code StreamTaskInvokable} is a non-serializable runtime
 * object holding live {@code OperatorChain}/{@code RecordWriter}/{@code InputGate}.
 * The remote-deploy path replaces that direct call with a descriptor that the
 * TaskManager turns into a local invokable.
 *
 * <p>The descriptor is self-contained: in remote-deploy mode
 * {@link io.nop.stream.runtime.coordinator.JobCoordinator#assignTasks()} calls
 * {@code deployTask(descriptor, epoch)} instead of
 * {@link IStreamTaskRpcService#receiveAssignment} + a separate direct
 * {@code installInvokable} call. The in-process executors
 * ({@code EmbeddedDistributedExecutor}/{@code RpcDistributedExecutor}) keep the
 * legacy path unchanged.
 */
@DataBean
public class TaskDeploymentDescriptor implements Serializable {

    private static final long serialVersionUID = 1L;

    private String jobId;
    private String vertexId;
    private int subtaskIndex;

    /**
     * The node that should run this subtask. Mirrors
     * {@link io.nop.stream.runtime.cluster.TaskAssignment#getNodeId()} so the
     * TaskManager can verify it is the intended target.
     */
    private String nodeId;

    private String attemptId;
    private int attemptNumber;

    /**
     * Monotonic fencing epoch this deployment is valid under. The TaskManager
     * rejects a {@code deployTask} call whose epoch does not match its current
     * fencing epoch (consistent with {@code receiveAssignment} / {@code triggerCheckpoint}
     * fencing checks).
     */
    private long fencingEpoch;

    /**
     * The full {@link JobGraph}. Carried (not a live operator object reference)
     * so the TaskManager can rebuild its own subtask view locally. The graph is
     * {@link Serializable} and carries per-vertex {@link io.nop.stream.core.jobgraph.OperatorChain}
     * templates that are deep-copied per subtask. Edge wiring (topics, partitioning)
     * is derived deterministically from the graph + deployment plan.
     */
    private JobGraph jobGraph;

    /**
     * The {@link DeploymentPlan} carrying parallelism + edge configuration. May
     * be null when the job graph's own parallelism / edge config is sufficient.
     */
    private DeploymentPlan deploymentPlan;

    /**
     * Filesystem path to the shared {@code LocalFileCheckpointStorage} directory
     * for this job. All TaskManager JVMs on the same machine read this path
     * during recovery to restore operator state. Null for a fresh job (no
     * restore). Cross-machine recovery requires JDBC checkpoint storage (Stage 46,
     * out of scope for this plan).
     */
    private String checkpointRestorePath;

    public TaskDeploymentDescriptor() {
    }

    public TaskDeploymentDescriptor(String jobId, String vertexId, int subtaskIndex, String nodeId,
                                    String attemptId, int attemptNumber, long fencingEpoch,
                                    JobGraph jobGraph, DeploymentPlan deploymentPlan,
                                    String checkpointRestorePath) {
        this.jobId = jobId;
        this.vertexId = vertexId;
        this.subtaskIndex = subtaskIndex;
        this.nodeId = nodeId;
        this.attemptId = attemptId;
        this.attemptNumber = attemptNumber;
        this.fencingEpoch = fencingEpoch;
        this.jobGraph = jobGraph;
        this.deploymentPlan = deploymentPlan;
        this.checkpointRestorePath = checkpointRestorePath;
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getVertexId() {
        return vertexId;
    }

    public void setVertexId(String vertexId) {
        this.vertexId = vertexId;
    }

    public int getSubtaskIndex() {
        return subtaskIndex;
    }

    public void setSubtaskIndex(int subtaskIndex) {
        this.subtaskIndex = subtaskIndex;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getAttemptId() {
        return attemptId;
    }

    public void setAttemptId(String attemptId) {
        this.attemptId = attemptId;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public void setAttemptNumber(int attemptNumber) {
        this.attemptNumber = attemptNumber;
    }

    public long getFencingEpoch() {
        return fencingEpoch;
    }

    public void setFencingEpoch(long fencingEpoch) {
        this.fencingEpoch = fencingEpoch;
    }

    public JobGraph getJobGraph() {
        return jobGraph;
    }

    public void setJobGraph(JobGraph jobGraph) {
        this.jobGraph = jobGraph;
    }

    public DeploymentPlan getDeploymentPlan() {
        return deploymentPlan;
    }

    public void setDeploymentPlan(DeploymentPlan deploymentPlan) {
        this.deploymentPlan = deploymentPlan;
    }

    public String getCheckpointRestorePath() {
        return checkpointRestorePath;
    }

    public void setCheckpointRestorePath(String checkpointRestorePath) {
        this.checkpointRestorePath = checkpointRestorePath;
    }
}

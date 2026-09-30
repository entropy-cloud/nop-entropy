/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:   https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.coordinator;

import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.execution.plan.DeploymentPlan;
import io.nop.stream.core.execution.plan.PartitionPolicy;
import io.nop.stream.core.execution.plan.PartitionedPlan;
import io.nop.stream.runtime.cluster.TaskAssignment;
import io.nop.stream.runtime.rpc.IStreamTaskRpcService;
import io.nop.stream.runtime.rpc.TaskDeploymentDescriptor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Shared scaffolding for the JobCoordinator test family: one recording
 * {@link IStreamTaskRpcService} stub plus the standard checkpoint config and
 * two-vertex deployment plan factories. Individual tests assert only the
 * recorded state their scenario needs.
 */
public final class CoordinatorTestSupport {

    private CoordinatorTestSupport() {
    }

    /**
     * Standard test checkpoint config: enabled, 1000ms interval, 10000ms
     * timeout, 1 concurrent / 3 retained checkpoints.
     */
    public static CheckpointConfig defaultCheckpointConfig() {
        return CheckpointConfig.builder()
                .checkpointEnabled(true)
                .checkpointInterval(1000L)
                .checkpointTimeout(10000L)
                .maxConcurrentCheckpoints(1)
                .maxRetainedCheckpoints(3)
                .build();
    }

    /**
     * The family-standard two-vertex plan: source and sink, parallelism 1,
     * connected FORWARD under pipeline-0.
     */
    public static DeploymentPlan twoVertexForwardPlan(String jobId) {
        Map<String, PartitionedPlan.VertexPlan> vertexPlans = new LinkedHashMap<>();
        vertexPlans.put("source", new PartitionedPlan.VertexPlan("source", 1, null));
        vertexPlans.put("sink", new PartitionedPlan.VertexPlan("sink", 1, null));
        List<PartitionedPlan.EdgePlan> edges = new ArrayList<>();
        edges.add(new PartitionedPlan.EdgePlan("source", "sink", PartitionPolicy.FORWARD));
        PartitionedPlan partitionedPlan = new PartitionedPlan(
                jobId, "pipeline-0", vertexPlans, edges, null, null);
        return new DeploymentPlan(jobId, "pipeline-0", partitionedPlan,
                "local", "memory", "local", null, null);
    }

    /**
     * Recording task-side RPC double shared by the JobCoordinator tests:
     * captures every control-plane call (assignments, deploys, checkpoint
     * barriers, cancels, fencing updates) so the individual scenarios can
     * assert on exactly the facets they need.
     */
    public static class RecordingTaskRpcService implements IStreamTaskRpcService {
        public final List<TaskAssignment> assignments = new CopyOnWriteArrayList<>();
        public final List<TaskDeploymentDescriptor> deployDescriptors = new CopyOnWriteArrayList<>();
        public final List<String> cancelTaskKeys = new CopyOnWriteArrayList<>();
        public final List<Long> cancelTaskEpochs = new CopyOnWriteArrayList<>();
        public final AtomicReference<CheckpointBarrier> lastBarrier = new AtomicReference<>();
        public final AtomicLong lastFencingEpoch = new AtomicLong();
        public final AtomicLong receiveAssignmentCount = new AtomicLong();
        public final AtomicLong triggerCount = new AtomicLong();
        public final AtomicLong cancelTaskCount = new AtomicLong();
        /** When true, the next {@link #receiveAssignment} throws (unreachable node). */
        public volatile boolean failReceiveAssignment;
        /**
         * When true, every {@link #updateFencingToken} throws (CC-01 injection:
         * the node is unreachable while the coordinator rotates the fencing
         * epoch during recovery).
         */
        public volatile boolean failUpdateFencingToken;
        /** When true, every {@link #cancelTask} throws (CC-03 injection: cancel fan-out RPC failure). */
        public volatile boolean failCancelTask;

        @Override
        public void receiveAssignment(TaskAssignment assignment) {
            receiveAssignmentCount.incrementAndGet();
            if (failReceiveAssignment) {
                throw new IllegalStateException("simulated unreachable node (audit-fix R-6)");
            }
            assignments.add(assignment);
            lastFencingEpoch.set(assignment.getFencingEpoch());
        }

        @Override
        public void triggerCheckpoint(CheckpointBarrier barrier, long fencingEpoch) {
            triggerCount.incrementAndGet();
            lastBarrier.set(barrier);
            lastFencingEpoch.set(fencingEpoch);
        }

        @Override
        public void deployTask(TaskDeploymentDescriptor descriptor, long fencingEpoch) {
            deployDescriptors.add(descriptor);
            lastFencingEpoch.set(fencingEpoch);
        }

        @Override
        public void cancelTask(String jobId, String vertexId, int subtaskIndex, long fencingEpoch) {
            cancelTaskCount.incrementAndGet();
            cancelTaskKeys.add(vertexId + "/" + subtaskIndex);
            cancelTaskEpochs.add(fencingEpoch);
            if (failCancelTask) {
                throw new IllegalStateException("simulated cancelTask RPC failure (CC-03)");
            }
        }

        @Override
        public void updateFencingToken(long fencingEpoch) {
            if (failUpdateFencingToken) {
                throw new IllegalStateException("simulated unreachable node during fencing rotation (CC-01)");
            }
            lastFencingEpoch.set(fencingEpoch);
        }

        public long getLastFencingEpoch() {
            return lastFencingEpoch.get();
        }

        public CheckpointBarrier getLastBarrier() {
            return lastBarrier.get();
        }
    }
}

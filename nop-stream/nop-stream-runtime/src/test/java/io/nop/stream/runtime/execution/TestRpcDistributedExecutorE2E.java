/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.execution;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;

import io.nop.api.core.message.IMessageService;
import io.nop.message.core.local.LocalMessageService;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.environment.StreamExecutionResult;
import io.nop.stream.core.execution.DeploymentMode;
import io.nop.stream.core.execution.task.Subtask;
import io.nop.stream.core.execution.plan.DeploymentPlan;
import io.nop.stream.core.execution.plan.PartitionedPlan;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.runtime.cluster.TaskAssignment;
import io.nop.stream.runtime.coordinator.JobCoordinator;
import io.nop.stream.runtime.coordinator.JobStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stage 39 Phase 2 end-to-end verification (plan guide #22): a full streaming
 * pipeline runs through {@link RpcDistributedExecutor}, whose control plane
 * (assignment / fencing / coordinator↔task uplink) traverses the real platform RPC
 * transport ({@code StreamControlRpcServer} + {@code StreamControlRpcProxyFactory}
 * over {@link IMessageService}).
 *
 * <p>The data plane stays in-JVM (Stage 40 wires cross-JVM data transport); this test
 * proves the RPC control plane is genuinely exercised: the coordinator assigns tasks
 * over RPC, the remote TaskManagers receive them, install invokables, run the
 * pipeline, and the sink collects the correct results. A correct result set is
 * only possible if the control calls crossed the RPC boundary.
 */
class TestRpcDistributedExecutorE2E {

    @Test
    void fullPipelineRunsOverRpcControlPlane() throws Exception {
        List<String> results = new CopyOnWriteArrayList<>();
        LocalMessageService messageService = new LocalMessageService();

        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        env.setParallelism(2);
        env.setDeploymentMode(DeploymentMode.DISTRIBUTED);
        env.setExecutionDispatcher(new RpcDistributedExecutor(messageService, 2, 30));

        env.fromElements("a", "b", "c", "d", "e", "f")
                .map(String::toUpperCase)
                .sink(results::add);

        env.execute("rpc-distributed-all-data");

        // A correct result set is only reachable if coordinator→task assignment and
        // the task→coordinator uplink both crossed the RPC layer.
        assertTrue(results.size() >= 6,
                "Expected at least 6 results over the RPC control plane, got " + results.size() + ": " + results);
        assertTrue(results.containsAll(Arrays.asList("A", "B", "C", "D", "E", "F")),
                "All mapped values should be present: " + results);
    }

    /**
     * AR-01 end-to-end regression (plan guide #22): a distributed job whose
     * source emits nothing (a healthy but idle job) must stay RUNNING — it must
     * NOT be killed by per-task stall detection.
     *
     * <p>Pre-fix the TM heartbeat reported the invokable's frozen data-progress
     * timestamp, so the idle source aged past taskTimeoutMs every cycle → repeated
     * global recovery → restart cap exceeded → job FAILED within ~4 minutes (60s
     * default timeout × 4 restarts). Post-fix the heartbeat reports task
     * aliveness (TM wall clock for SOURCE roles), so the idle job never stalls.
     *
     * <p>Timing: taskTimeoutMs is shrunk to 8s (above the 5s heartbeat interval)
     * and maxRestarts to 1 so a single false stall FAILs the job within one
     * detect cycle. detectFailures() is driven manually (in addition to the
     * automatic 5s detector) for deterministic assertions.
     */
    @Test
    void idleJobWithNoDataIsNotKilledByStallDetection() throws Exception {
        LocalMessageService messageService = new LocalMessageService();
        IdleProbeDispatcher dispatcher = new IdleProbeDispatcher(messageService);

        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        env.setParallelism(1);
        env.setDeploymentMode(DeploymentMode.DISTRIBUTED);
        env.setExecutionDispatcher(dispatcher);

        List<String> results = new CopyOnWriteArrayList<>();
        env.addSource(new IdleBlockingSource(), "idle-source").sink(results::add);

        // Returns as soon as the RPC topology is started and invokables installed
        // (the probe dispatcher does not wait for completion — the job is idle).
        env.execute("rpc-distributed-idle-not-stalled");

        RpcDistributedExecutor.DistributedJobHandle handle = dispatcher.handle;
        assertNotNull(handle, "probe dispatcher must have started the job");
        JobCoordinator coordinator = handle.getCoordinator();
        // Post-start configuration (values are volatile, read live by the
        // failure detector): shrink the stall window below the 60s default and
        // set the restart cap to 1 so one false stall FAILs the job immediately.
        coordinator.setTaskTimeoutMs(8_000L);
        coordinator.setMaxRestarts(1);

        try {
            // t≈9s: manual detect tick. Pre-fix the idle source's frozen
            // data-progress (9s old) exceeds the 8s cutoff → recovery #1
            // (restartCount=1). Post-fix the heartbeat-refreshed aliveness is
            // fresh → no stall.
            // 时间语义：stall 检测需要 data-progress 年龄超过 8s 截止值，
            // 9s 老化必须真实流逝（循环形式等待）
            io.nop.stream.runtime.testsupport.TestAwait.elapsed("progress ages past 8s stall cutoff", 9_000);
            coordinator.detectFailures();
            assertEquals(0, coordinator.getRestartCount(),
                    "idle job must not trigger stall-driven recovery (restartCount stays 0)");

            // t≈18s: second manual detect tick. Pre-fix the redeployed attempt's
            // frozen progress ages past the cutoff again → recovery #2 → restart
            // cap (1) exceeded → failJob. Post-fix: still healthy.
            // 时间语义：第二个 9s 老化窗（循环形式等待）
            io.nop.stream.runtime.testsupport.TestAwait.elapsed("second 9s aging window", 9_000);
            coordinator.detectFailures();
            assertEquals(0, coordinator.getRestartCount(),
                    "idle job must not trigger repeated stall-driven recovery");
            assertNotEquals(JobStatus.FAILED, coordinator.getJobStatus().getJobStatus(),
                    "healthy idle job must stay RUNNING (pre-fix it was FAILED by stall detection)");
        } finally {
            handle.close();
        }
    }

    /**
     * RpcDistributedExecutor probe that starts the job (keeping the handle) and
     * installs the data-plane invokables, but returns without waiting for
     * completion (the idle job never completes).
     */
    static class IdleProbeDispatcher extends RpcDistributedExecutor {
        volatile RpcDistributedExecutor.DistributedJobHandle handle;

        IdleProbeDispatcher(IMessageService messageService) {
            super(messageService, 1, 60);
        }

        @Override
        public StreamExecutionResult execute(JobGraph jobGraph, PartitionedPlan partitionedPlan,
                                             DeploymentPlan deploymentPlan) throws Exception {
            handle = startJob(jobGraph, partitionedPlan, deploymentPlan);
            installInvokables(handle);
            return new StreamExecutionResult(partitionedPlan.getJobId(), 0L);
        }

        private static void installInvokables(RpcDistributedExecutor.DistributedJobHandle handle) {
            Map<String, List<TaskAssignment>> assignments = handle.getCoordinator().getTaskAssignments();
            for (String vertexId : handle.getPlan().getSortedVertexIds()) {
                List<Subtask> subtasks = handle.getPlan().getSubtasks(vertexId);
                List<TaskAssignment> vertexAssignments = assignments.get(vertexId);
                for (Subtask subtask : subtasks) {
                    TaskAssignment ta = findAssignment(vertexAssignments, subtask.getTaskIndex());
                    if (ta == null) {
                        throw new io.nop.stream.core.exceptions.StreamException(
                                io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_STATE)
                                .param(io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL,
                                        "No assignment found for vertex=" + vertexId
                                                + " subtaskIndex=" + subtask.getTaskIndex());
                    }
                    handle.getTaskManagers().stream()
                            .filter(tm -> tm.getNodeId().equals(ta.getNodeId()))
                            .findFirst()
                            .orElseThrow(() -> new IllegalStateException(
                                    "No TaskManager for node " + ta.getNodeId()))
                            .installInvokable(handle.getJobId(), vertexId, subtask.getTaskIndex(),
                                    subtask.getInvokable());
                }
            }
        }

        private static TaskAssignment findAssignment(List<TaskAssignment> assignments, int subtaskIndex) {
            if (assignments == null) {
                return null;
            }
            for (TaskAssignment ta : assignments) {
                if (ta.getSubtaskIndex() == subtaskIndex) {
                    return ta;
                }
            }
            return null;
        }
    }

    /**
     * A source that never emits: the job stays healthy but data-idle forever.
     */
    static class IdleBlockingSource implements SourceFunction<String> {
        private static final long serialVersionUID = 1L;
        private transient volatile boolean running = true;

        @Override
        public void run(SourceContext<String> ctx) throws Exception {
            while (running) {
                Thread.sleep(100);
            }
        }

        @Override
        public void cancel() {
            running = false;
        }
    }
}

/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.alert;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.nop.api.core.message.IMessageService;
import io.nop.message.core.local.LocalMessageService;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.execution.task.StreamTaskInvokable;
import io.nop.stream.core.testsupport.TestAwait;
import io.nop.stream.core.execution.plan.DeploymentPlan;
import io.nop.stream.core.execution.plan.PartitionPolicy;
import io.nop.stream.core.execution.plan.PartitionedPlan;
import io.nop.stream.core.jobgraph.JobEdge;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.jobgraph.ResultPartitionType;
import io.nop.stream.core.operators.StreamSinkOperator;
import io.nop.stream.core.operators.StreamSourceOperator;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import io.nop.stream.runtime.cluster.InMemoryClusterRegistry;
import io.nop.stream.runtime.coordinator.JobCoordinator;
import io.nop.stream.runtime.health.StreamJobHealth;
import io.nop.stream.runtime.launch.ClusterLaunchConfig;
import io.nop.stream.runtime.launch.ClusterPipelineFactory;
import io.nop.stream.runtime.ops.JobSubmissionSpec;
import io.nop.stream.runtime.ops.OpsJobManager;
import io.nop.stream.runtime.ops.StreamGovernanceConfig;
import io.nop.stream.runtime.taskmanager.TaskManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Item 16 (P-REQ-12) fault-injection proof (acceptance: 故障注入测试断言事件外发):
 * a REAL pipeline (source → sink) is deployed on a REAL TaskManager through
 * the ops submit path; the source is gated until the harness finishes wiring
 * (coordinator RPC backlink + restart cap), then the sink function is
 * injected to fail on its first record, so the REAL failure chain fires —
 * task FAILED report → auto globalRecovery (RECOVERY_STARTED) → restart cap
 * exhausted → failJob (JOB_FAILED) — and the alerts are asserted at the
 * recording channel.
 */
class TestAlertFaultInjectionE2E {

    /** Gate released by the harness after wiring; the source idles until then. */
    static volatile CountDownLatch gate = new CountDownLatch(1);

    /**
     * Reset per test: opened by the sink fixture the moment a record ARRIVES
     * (records sent before the sink's subscription is live are silently dropped
     * by the in-process LocalMessageService — the source's emit loop keys off
     * this latch instead of a fixed emission window).
     */
    static volatile CountDownLatch firstRecordSeen = new CountDownLatch(1);

    /**
     * Source idling until the gate opens, then feeding records until one ARRIVES
     * at the failing sink (the failure chain under test starts exactly there),
     * then staying alive until the job's cancel path ends the task.
     * 时间确定化（plan 2510）：不再用固定 20ms 发射循环——发射节奏由「sink 已
     * 观察到记录」这一确定性条件驱动（重发间隔 = 可中断条件等待轮询，上限仅
     * 挂死保护），存活期由可中断阻塞点承载，结束信号 = 被测系统的真实取消路径
     * （RunningTask.cancel → future.cancel(true) 线程中断），测试不依赖真实
     * 时间流逝。
     */
    public static final class GatedSourceFunction implements SourceFunction<Long> {
        private static final long serialVersionUID = 1L;
        private volatile boolean running = true;

        @Override
        public void run(SourceContext<Long> ctx) throws Exception {
            gate.await();
            for (int i = 0; i < 500 && firstRecordSeen.getCount() != 0; i++) {
                ctx.collect(0L);
                try {
                    firstRecordSeen.await(10L, java.util.concurrent.TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            firstRecordSeen.await();
            // 驻留到取消路径打断（abort → task cancel → interrupt）；此阻塞点
            // 永不自行返回，唯一出口是线程中断。
            new CountDownLatch(1).await();
        }

        @Override
        public void cancel() {
            running = false;
        }
    }

    /** Sink injected to fail on its first record (the fault injection). */
    public static final class InjectedFailureSinkFunction implements SinkFunction<Long> {
        private static final long serialVersionUID = 1L;

        @Override
        public void consume(Long value) {
            firstRecordSeen.countDown();
            throw new IllegalStateException("injected sink failure (alert fault-injection test)");
        }
    }

    /** Pipeline factory: gated source → failing sink. */
    public static final class FaultPipelineFactory implements ClusterPipelineFactory {
        @Override
        public PipelineArtifacts buildPipeline(String jobId, ClusterLaunchConfig config) {
            StreamSourceOperator<Long> sourceOp =
                    new StreamSourceOperator<>(new GatedSourceFunction());
            StreamSinkOperator<Long> sinkOp =
                    new StreamSinkOperator<>(new InjectedFailureSinkFunction());

            OperatorChain sourceChain = new OperatorChain(Collections.singletonList(sourceOp));
            OperatorChain sinkChain = new OperatorChain(Collections.singletonList(sinkOp));
            StreamTaskInvokable sourceInv = new StreamTaskInvokable(sourceChain);
            StreamTaskInvokable sinkInv = new StreamTaskInvokable(sinkChain);

            JobGraph jobGraph = new JobGraph(jobId);
            jobGraph.addVertex(new JobVertex("source", "Source", 1,
                    Collections.singletonList(sourceChain), sourceInv));
            jobGraph.addVertex(new JobVertex("sink", "Sink", 1,
                    Collections.singletonList(sinkChain), sinkInv));
            jobGraph.addEdge(new JobEdge("source", "sink", ResultPartitionType.PIPELINED));

            Map<String, PartitionedPlan.VertexPlan> vertexPlans = new java.util.LinkedHashMap<>();
            vertexPlans.put("source", new PartitionedPlan.VertexPlan("source", 1, null));
            vertexPlans.put("sink", new PartitionedPlan.VertexPlan("sink", 1, null));
            PartitionedPlan partitionedPlan = new PartitionedPlan(jobId, "pipeline-0", vertexPlans,
                    List.of(new PartitionedPlan.EdgePlan("source", "sink", PartitionPolicy.FORWARD)),
                    null, null);
            DeploymentPlan deploymentPlan = new DeploymentPlan(
                    jobId, "pipeline-0", partitionedPlan, "local", "memory", "local", null, null);
            return new PipelineArtifacts(jobGraph, deploymentPlan, 500L, 10_000L, 3);
        }
    }

    private static final String FACTORY_FQCN =
            "io.nop.stream.runtime.alert.TestAlertFaultInjectionE2E$FaultPipelineFactory";
    private static final String JOB_ID = "alert-injection-job";

    @TempDir
    Path tempDir;

    private TaskManager taskManager;
    private OpsJobManager jobManager;
    private TestAlertChannels.RecordingChannel recordingChannel;
    private final List<String> transitionTrail = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() {
        gate = new CountDownLatch(1);
        firstRecordSeen = new CountDownLatch(1);
        InMemoryClusterRegistry registry = new InMemoryClusterRegistry();
        IMessageService messageService = new LocalMessageService();
        taskManager = new TaskManager("node-1", "localhost:0", 4, messageService,
                registry, "control-topic");
        taskManager.start();

        recordingChannel = new TestAlertChannels.RecordingChannel(false);
        jobManager = new OpsJobManager(messageService, registry,
                new LocalFileCheckpointStorage(tempDir.toString()),
                Map.of("node-1", taskManager),
                new StreamGovernanceConfig(),
                new AlertService(List.of(recordingChannel)));
    }

    @AfterEach
    void tearDown() {
        jobManager.close();
        taskManager.stop();
    }

    @Test
    void injectedTaskFailureDeliversRecoveryAndFailureAlerts() throws Exception {
        JobSubmissionSpec spec = new JobSubmissionSpec();
        spec.setJobId(JOB_ID);
        spec.setPipelineFactoryClass(FACTORY_FQCN);
        JobCoordinator coordinator = jobManager.submit(spec);

        // complete the wiring BEFORE releasing the data gate: the TM reports
        // task failures to the coordinator over this backlink, and the zero
        // restart cap makes the first auto-recovery exhaust the cap.
        taskManager.setCoordinatorRpcService(coordinator);
        coordinator.setMaxRestarts(0);
        coordinator.addHealthListener((jobId, from, to, cause) ->
                transitionTrail.add(from + "->" + to));

        // release the gate: records flow, the injected sink failure fires the
        // REAL async failure chain (FAILED report → recovery → cap → failJob)
        gate.countDown();

        // 条件等待（上限仅挂死保护）：job 进入 FAILED 且两条告警都已送达。
        TestAwait.until("job FAILED with RECOVERY_STARTED + JOB_FAILED alerts delivered",
                () -> coordinator.getHealth() == StreamJobHealth.FAILED
                        && recordingChannel.received.size() >= 2,
                30_000L);

        assertEquals(StreamJobHealth.FAILED, coordinator.getHealth(),
                "injected sink failure must drive the job to FAILED (health=" + coordinator.getHealth()
                        + ", transitions=" + transitionTrail + ")");
        assertTrue(transitionTrail.contains("RUNNING->RECOVERING")
                        || transitionTrail.contains("DEGRADED->RECOVERING"),
                "the failure chain went through RECOVERING: " + transitionTrail);
        assertTrue(transitionTrail.contains("RECOVERING->FAILED"),
                "restart-cap exhaustion failed the job from RECOVERING: " + transitionTrail);

        // fault-semantic alerts were delivered out-of-band (P-REQ-12 acceptance)
        assertTrue(recordingChannel.received.size() >= 2,
                "at least RECOVERY_STARTED + JOB_FAILED alerts: " + recordingChannel.received);
        assertTrue(recordingChannel.received.stream().anyMatch(
                        a -> a.getEventType().equals("RECOVERY_STARTED")
                                && a.getSeverity() == AlertEvent.Severity.WARN),
                "RECOVERY_STARTED WARN alert delivered: " + recordingChannel.received);
        assertTrue(recordingChannel.received.stream().anyMatch(
                        a -> a.getEventType().equals("JOB_FAILED")
                                && a.getSeverity() == AlertEvent.Severity.ERROR
                                && a.getMessage().contains(JOB_ID)),
                "JOB_FAILED ERROR alert delivered with job identity: " + recordingChannel.received);
    }
}

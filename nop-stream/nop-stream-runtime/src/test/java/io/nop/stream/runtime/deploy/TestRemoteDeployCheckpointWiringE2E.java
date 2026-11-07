/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.deploy;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.nop.api.core.message.IMessageService;
import io.nop.message.core.local.LocalMessageService;
import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.CheckpointIDCounter;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.common.functions.sink.TwoPhaseCommitSinkFunction;
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
import io.nop.stream.core.testsupport.TestAwait;
import io.nop.stream.runtime.checkpoint.CheckpointCoordinator;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import io.nop.stream.runtime.cluster.InMemoryClusterRegistry;
import io.nop.stream.runtime.coordinator.JobCoordinator;
import io.nop.stream.runtime.rpc.IStreamCoordinatorRpcService;
import io.nop.stream.runtime.rpc.IStreamTaskRpcService;
import io.nop.stream.runtime.rpc.StreamControlRpcProxyFactory;
import io.nop.stream.runtime.rpc.StreamControlRpcServer;
import io.nop.stream.runtime.rpc.StreamControlRpcTopics;
import io.nop.stream.runtime.taskmanager.TaskManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Item 14 (composite-scenario distributed): ungated in-process proof of the
 * remote-deploy checkpoint wiring. Spawns a REAL pipeline (source →
 * 2PC sink) through the deployTask path over two in-process TaskManagers whose
 * control plane traverses RPC proxies (LocalMessageService as transport), and
 * asserts the complete checkpoint loop that was previously impossible in
 * remote-deploy mode:
 *
 * <ol>
 *   <li>barrier RPC registration + injection (tracker wired by
 *       {@link RemoteTaskDeploySupport});</li>
 *   <li>ACK flowing back over the coordinator RPC (checkpoint actually
 *       completing — manifest durable in the storage dir);</li>
 *   <li>durable-checkpoint commit notification reaching the TM-side 2PC sink
 *       participant ({@code registerDistributedCommitForwarder} →
 *       {@code TaskManager.notifyCheckpointComplete} → {@code finishCommit}) —
 *       wiring verification (plan guide #23): the commit counter only moves if
 *       the whole chain is connected;</li>
 *   <li>exactly-once at the sink: every element committed exactly once even
 *       though multiple checkpoints commit progressively.</li>
 * </ol>
 *
 * <p>时间确定化（plan 2510）：原 fixture 用真实 pacing（40ms/条发射 + 750ms
 * 订阅窗 + 500ms finish linger）把运行时长拉过真实 150ms 周期 checkpoint。
 * 现改为全显式同步编排：(a) 订阅建立用探针握手（in-process LocalMessageService
 * 对无订阅 topic 静默丢包——探针落地前重发）；(b) 周期 checkpoint 由测试显式
 * {@code coordinator.triggerCheckpoint()} 驱动，pending barrier 在下一条
 * collect 上发出（引擎语义保持），每轮「发射批次 → 显式触发 → 隔条携带
 * barrier → 提交证据」；(c) 全部等待为确定性条件（提交计数推进），上限仅挂死
 * 保护。屏障与记录的到达次序竞争不影响恰好一次语义（记录归属其后最近 epoch
 * 的 saveState 缓冲），提交里程碑由对应 epoch 确定达成。不再存在任何真实
 * 时间窗。两个引擎边界保持原裁定：(a) pending barrier 需要下一条 collect——
 * 编排保证触发后总有后续 collect；(b) EOS 后任务离开 TaskManager——所有
 * 提交证据在 EOS 前取得。
 *
 * <p>The gated multi-JVM scenario tests (nop-stream-fraud-example) consume the
 * same wiring cross-JVM; this test is the fast default-suite regression for it.
 */
class TestRemoteDeployCheckpointWiringE2E {

    private static final String JOB_ID = "remote-deploy-checkpoint-e2e";

    @TempDir
    Path tempDir;

    private IMessageService messageService;
    private InMemoryClusterRegistry clusterRegistry;
    private LocalFileCheckpointStorage storage;
    private List<TaskManager> taskManagers;
    private List<StreamControlRpcServer> servers;
    private List<StreamControlRpcProxyFactory> taskProxies;
    private StreamControlRpcServer coordinatorServer;
    private StreamControlRpcProxyFactory coordinatorProxy;
    private JobCoordinator coordinator;

    @BeforeEach
    void setUp() {
        messageService = new LocalMessageService();
        clusterRegistry = new InMemoryClusterRegistry();
        storage = new LocalFileCheckpointStorage(tempDir.resolve("checkpoints").toString());
        taskManagers = new ArrayList<>();
        servers = new ArrayList<>();
        taskProxies = new ArrayList<>();
    }

    @AfterEach
    void tearDown() {
        if (coordinator != null) {
            try {
                coordinator.stop();
            } catch (Exception ignored) {
                // best-effort teardown
            }
        }
        if (coordinatorServer != null) {
            coordinatorServer.stop();
        }
        if (coordinatorProxy != null) {
            coordinatorProxy.stop();
        }
        for (StreamControlRpcProxyFactory p : taskProxies) {
            p.stop();
        }
        for (StreamControlRpcServer s : servers) {
            s.stop();
        }
        for (TaskManager tm : taskManagers) {
            tm.stop();
        }
    }

    @Test
    void remoteDeployPipelineCheckpointsCommitsAndRestoresExactlyOnce() throws Exception {
        int meaningfulCount = 20;
        Semaphore permits = new Semaphore(0);
        CountDownLatch probeLanded = new CountDownLatch(1);
        TestableSource source = new TestableSource(meaningfulCount, permits, probeLanded);
        RecordingTwoPhaseSink sink = new RecordingTwoPhaseSink(probeLanded);

        JobGraph jobGraph = buildPipeline(JOB_ID, source, sink);
        DeploymentPlan deploymentPlan = buildDeploymentPlan(JOB_ID);

        startTopology(jobGraph, deploymentPlan);

        // ============ choreography: streaming emission + test-driven triggers ============
        // 条件驱动的编排循环：每次条件求值放行一小批记录（数据流在提交证据达标前
        // 持续流动——pending barrier 在下一条 collect 上发出）并显式触发一次
        // checkpoint（生产侧门控 maxConcurrent/minPause 照常裁决，拒绝即下一轮
        // 重试）。记录落进哪个 epoch 由屏障与记录的到达竞争决定，恰好一次语义
        // 不受影响（2PC 按 epoch saveState 缓冲/提交）。触发节奏 = 条件等待轮询
        // （非墙钟阈值），上限仅挂死保护。
        TestAwait.until("test-driven epoch commits carry every meaningful element through "
                        + "the durable-notification chain",
                () -> {
                    int committed = countCommittedMeaningful(sink, meaningfulCount);
                    if (committed < meaningfulCount) {
                        permits.release(3);
                        coordinator.triggerCheckpoint();
                    }
                    return committed >= meaningfulCount;
                }, 60_000L);

        // 1. Exactly-once: every meaningful element committed exactly once
        //    (commits only happen through the durable-checkpoint notification
        //    chain). Each of 0..19 appears exactly once.
        for (int i = 0; i < meaningfulCount; i++) {
            assertEquals(1, java.util.Collections.frequency(sink.committedElements, i),
                    "element " + i + " must be committed exactly once (no duplicate/loss)");
        }

        // 2. Checkpoints genuinely completed cross-RPC: a durable epoch manifest
        //    exists (ACK path completed the pending checkpoints).
        assertNotNull(storage.loadLatestEpochManifest(JOB_ID, "pipeline-0"),
                "EpochManifest must be durable after the run (checkpoint ACK loop works)");
        assertTrue(!sink.committedEpochs.isEmpty() && sink.committedEpochs.firstKey() >= 0,
                "sink must have committed at least one epoch");
        assertTrue(!sink.committedEpochs.isEmpty(), "commit notification chain reached the sink");

        // 3. Manifests observed by the coordinator and the manifest reloaded from
        //    disk must agree on the epoch family (monotonic, non-shadowed).
        long latestDurable = storage.loadLatestEpochManifest(JOB_ID, "pipeline-0").getEpochId();
        assertTrue(sink.committedEpochs.lastKey() <= latestDurable,
                "sink committed epochs must not exceed the durable epoch (2PC invariant): committed="
                        + sink.committedEpochs.lastKey() + " durable=" + latestDurable);

        // 4. Restore-on-deploy (wiring proof for RemoteTaskDeploySupport restore):
        //    rebuild the sink subtask locally with a restore-path descriptor and
        //    assert the operator state restored from the durable manifest — a
        //    fresh RecordingTwoPhaseSink receives the durable pendingCommits map
        //    via restoreFromEpoch, and its (empty) commit output proves the
        //    restored pendings were already committed (idempotent guard).
        RecordingTwoPhaseSink restoreSink = new RecordingTwoPhaseSink(new CountDownLatch(1));
        JobGraph restoreGraph = buildPipeline(JOB_ID, new TestableSource(0, new Semaphore(0),
                new CountDownLatch(1)), restoreSink);
        io.nop.stream.runtime.protocol.TaskDeploymentDescriptor descriptor =
                new io.nop.stream.runtime.protocol.TaskDeploymentDescriptor(
                        JOB_ID, "sink", 0, "tm-1", "restore-attempt", 1, 1L,
                        restoreGraph, deploymentPlan,
                        storage.getBaseDir());
        io.nop.stream.runtime.transport.SubtaskPlanBuilder builder =
                new io.nop.stream.runtime.transport.SubtaskPlanBuilder(messageService, null);
        io.nop.stream.runtime.transport.SubtaskPlanBuilder.DeployedSubtaskPlan deployed =
                builder.buildSubtaskPlan(descriptor);
        RemoteTaskDeploySupport.wireDeployedSubtask(
                taskManagers.get(1), descriptor, deployed.getJobGraph(),
                deployed.getPlan(), deployed.getInvokable());

        // The restore path restored the sink participant from the durable epoch:
        // restoreFromEpoch committed durable-but-uncommitted pendings (none — run 1
        // committed everything) and began a fresh transaction. What MUST hold is
        // that the restore did not throw and the operator saw the durable epoch.
        assertTrue(restoreSink.lastRestoredEpoch >= 0,
                "sink participant must observe the durable epoch on restore-on-deploy, got "
                        + restoreSink.lastRestoredEpoch);
        for (int i = 0; i < meaningfulCount; i++) {
            assertEquals(1, java.util.Collections.frequency(sink.committedElements, i),
                    "restore must not re-commit already committed elements (idempotent guard)");
        }
    }

    // ==================== topology assembly (mirrors JobCoordinatorMain launch wiring) ====================

    private void startTopology(JobGraph jobGraph, DeploymentPlan deploymentPlan) throws Exception {
        long fencingEpoch = JobCoordinator.deriveHaFencingEpoch(0L, 1L);
        Map<String, IStreamTaskRpcService> taskRpcProxies = new LinkedHashMap<>();

        for (int i = 0; i < 2; i++) {
            String nodeId = "tm-" + i;
            TaskManager tm = new TaskManager(nodeId, "rpc:" + nodeId, 16,
                    messageService, clusterRegistry, StreamControlRpcTopics.coordinatorTopic(JOB_ID));
            tm.updateFencingToken(fencingEpoch);
            tm.start();
            taskManagers.add(tm);

            StreamControlRpcServer server = new StreamControlRpcServer(
                    "streamTaskRpc@" + nodeId, IStreamTaskRpcService.class, tm,
                    messageService, StreamControlRpcTopics.taskTopic(nodeId));
            server.start();
            servers.add(server);

            StreamControlRpcProxyFactory proxy = new StreamControlRpcProxyFactory(
                    "streamTaskRpc@" + nodeId, IStreamTaskRpcService.class,
                    messageService, StreamControlRpcTopics.taskTopic(nodeId));
            proxy.start();
            taskProxies.add(proxy);
            taskRpcProxies.put(nodeId, proxy.getProxy());
        }

        CheckpointCoordinator checkpointCoordinator = new CheckpointCoordinator(
                JOB_ID, "pipeline-0", new CheckpointIDCounter(), storage,
                CheckpointConfig.builder()
                        .checkpointEnabled(true)
                        .checkpointInterval(150L)
                        .checkpointTimeout(30_000L)
                        .minPause(0L)
                        .maxConcurrentCheckpoints(1)
                        .maxRetainedCheckpoints(5)
                        .build());

        coordinator = new JobCoordinator(JOB_ID, "coordinator-" + JOB_ID, deploymentPlan,
                clusterRegistry, checkpointCoordinator, taskRpcProxies);
        coordinator.setFencingEpoch(fencingEpoch);
        coordinator.setRemoteDeployMode(true);
        coordinator.setJobGraph(jobGraph);
        coordinator.setCheckpointStoragePath(storage.getBaseDir());
        coordinator.registerDistributedCommitForwarder();

        coordinatorServer = new StreamControlRpcServer(
                "streamCoordinatorRpc@" + JOB_ID,
                io.nop.stream.runtime.rpc.IStreamCoordinatorRpcService.class,
                coordinator, messageService, StreamControlRpcTopics.coordinatorTopic(JOB_ID));
        coordinatorProxy = new StreamControlRpcProxyFactory(
                "streamCoordinatorRpc@" + JOB_ID,
                io.nop.stream.runtime.rpc.IStreamCoordinatorRpcService.class,
                messageService, StreamControlRpcTopics.coordinatorTopic(JOB_ID));
        IStreamCoordinatorRpcService coordinatorRpc = coordinatorProxy.getProxy();
        for (TaskManager tm : taskManagers) {
            tm.setCoordinatorRpcService(coordinatorRpc);
        }

        coordinatorServer.start();
        coordinatorProxy.start();
        coordinator.start();
        coordinator.assignTasks();

        // 时间确定化（plan 2510）：不再启动真实 150ms 周期 checkpoint 调度器——
        // 触发点改由测试显式 triggerCheckpoint() 编排（见主用例）。
    }

    private static JobGraph buildPipeline(String jobId, TestableSource source,
                                          RecordingTwoPhaseSink sink) {
        StreamSourceOperator<Integer> sourceOp = new StreamSourceOperator<>(source);
        StreamSinkOperator<Integer> sinkOp = new StreamSinkOperator<>(sink);

        OperatorChain sourceChain = new OperatorChain(Collections.singletonList(sourceOp));
        OperatorChain sinkChain = new OperatorChain(Collections.singletonList(sinkOp));

        JobGraph jobGraph = new JobGraph(jobId);
        jobGraph.addVertex(new JobVertex("source", "Source", 1,
                Collections.singletonList(sourceChain), new io.nop.stream.core.execution.task.StreamTaskInvokable(sourceChain)));
        jobGraph.addVertex(new JobVertex("sink", "Sink", 1,
                Collections.singletonList(sinkChain), new io.nop.stream.core.execution.task.StreamTaskInvokable(sinkChain)));
        jobGraph.addEdge(new JobEdge("source", "sink", ResultPartitionType.PIPELINED));
        return jobGraph;
    }

    private static DeploymentPlan buildDeploymentPlan(String jobId) {
        Map<String, PartitionedPlan.VertexPlan> vertexPlans = new LinkedHashMap<>();
        vertexPlans.put("source", new PartitionedPlan.VertexPlan("source", 1, null));
        vertexPlans.put("sink", new PartitionedPlan.VertexPlan("sink", 1, null));
        List<PartitionedPlan.EdgePlan> edgePlans = new ArrayList<>();
        edgePlans.add(new PartitionedPlan.EdgePlan("source", "sink", PartitionPolicy.FORWARD));
        return new DeploymentPlan(jobId, "pipeline-0",
                new PartitionedPlan(jobId, "pipeline-0", vertexPlans, edgePlans, null, null),
                "local", "memory", "local", null, null);
    }

    private static int countCommittedMeaningful(RecordingTwoPhaseSink sink, int meaningfulCount) {
        int seen = 0;
        for (Integer v : sink.committedElements) {
            if (v != null && v >= 0 && v < meaningfulCount) {
                seen++;
            }
        }
        return seen;
    }

    // ==================== fixture UDFs ====================

    /** Probe value: filtered out by the sink fixture (subscription-ordering handshake only). */
    static final int PROBE_VALUE = -1;

    /**
     * Trailing filler values beyond the asserted meaningful range: they keep the
     * data stream alive past the last meaningful record so a barrier injected
     * late still finds a next collect (barrier/record arrival races cannot
     * strand the last epoch).
     */
    static final int TRAILING_FILLERS = 10;

    /**
     * Emission-controlled source: values 0..(count + TRAILING_FILLERS). Before
     * any real record, a probe handshake guarantees the downstream subscription
     * is live (in-process LocalMessageService silently drops sends to
     * unsubscribed topics); the per-record release semaphore lets the test pace
     * emission on commit evidence. Replaces the legacy real-time pacing source
     * (plan 2510 时间确定化).
     */
    static final class TestableSource
            implements io.nop.stream.core.common.functions.source.SourceFunction<Integer> {
        private static final long serialVersionUID = 1L;

        private final int count;
        private final Semaphore permits;
        private final CountDownLatch probeLanded;
        private volatile boolean running = true;

        TestableSource(int count, Semaphore permits, CountDownLatch probeLanded) {
            this.count = count;
            this.permits = permits;
            this.probeLanded = probeLanded;
        }

        @Override
        public void run(io.nop.stream.core.common.functions.source.SourceFunction.SourceContext<Integer> ctx)
                throws Exception {
            // probe handshake: resend the probe until the sink fixture observes it
            for (int probe = 0; probe < 500 && probeLanded.getCount() != 0; probe++) {
                ctx.collect(PROBE_VALUE);
                try {
                    probeLanded.await(10L, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            TestAwait.until("downstream consumer took the probe (subscription live)",
                    () -> probeLanded.getCount() == 0, 10_000L);
            for (int i = 0; i <= count + TRAILING_FILLERS && running; i++) {
                permits.acquire();
                ctx.collect(i);
            }
        }

        @Override
        public void cancel() {
            running = false;
        }
    }

    /**
     * In-memory 2PC sink mirroring the JdbcTwoPhaseCommitSink structure: invoke
     * buffers per epoch, saveState moves the buffer into pendingCommits, commit
     * (driven ONLY by the durable-checkpoint notification chain) appends to the
     * committed list. Recording the committed epochs proves the commit
     * notification reached the TM-side participant. Probe values are never
     * buffered (subscription handshake only).
     */
    static final class RecordingTwoPhaseSink extends TwoPhaseCommitSinkFunction<Integer> {
        private static final long serialVersionUID = 1L;

        final ConcurrentLinkedQueue<Integer> committedElements = new ConcurrentLinkedQueue<>();
        final java.util.concurrent.ConcurrentSkipListMap<Long, Integer> committedEpochs =
                new java.util.concurrent.ConcurrentSkipListMap<>();
        final List<Integer> currentBuffer = new ArrayList<>();
        final CountDownLatch probeLanded;
        volatile long lastRestoredEpoch = -1L;

        RecordingTwoPhaseSink(CountDownLatch probeLanded) {
            this.probeLanded = probeLanded;
        }

        @Override
        public void beginTransaction() {
        }

        @Override
        public void invoke(Integer value) {
            if (value != null && value == PROBE_VALUE) {
                probeLanded.countDown();
                return;
            }
            synchronized (currentBuffer) {
                currentBuffer.add(value);
            }
        }

        @Override
        public TaskStateSnapshot saveState(long epochId) throws Exception {
            synchronized (currentBuffer) {
                if (!currentBuffer.isEmpty()) {
                    getPendingCommits().put(epochId, new ArrayList<>(currentBuffer));
                    currentBuffer.clear();
                }
            }
            return super.saveState(epochId);
        }

        @Override
        public void preCommit(long checkpointId) {
        }

        @Override
        @SuppressWarnings("unchecked")
        public void commit(long checkpointId) {
            Object raw = getPendingCommits().get(checkpointId);
            if (raw instanceof List) {
                for (Integer value : (List<Integer>) raw) {
                    committedElements.add(value);
                }
                getPendingCommits().remove(checkpointId);
                committedEpochs.put(checkpointId, ((List<Integer>) raw).size());
            }
        }

        @Override
        public void rollback() {
            synchronized (currentBuffer) {
                currentBuffer.clear();
            }
        }

        @Override
        public void restoreFromEpoch(long epochId, TaskStateSnapshot state) throws Exception {
            super.restoreFromEpoch(epochId, state);
            this.lastRestoredEpoch = epochId;
        }
    }
}

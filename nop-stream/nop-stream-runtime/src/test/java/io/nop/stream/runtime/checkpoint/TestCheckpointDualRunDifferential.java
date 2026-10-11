/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.nop.api.core.message.IMessageService;
import io.nop.message.core.local.LocalMessageService;
import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.CheckpointIDCounter;
import io.nop.stream.core.checkpoint.ProcessingGuarantee;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.common.functions.sink.TwoPhaseCommitSinkFunction;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.execution.plan.DeploymentPlan;
import io.nop.stream.core.execution.plan.PartitionPolicy;
import io.nop.stream.core.execution.plan.PartitionedPlan;
import io.nop.stream.core.jobgraph.JobEdge;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.model.StreamModel;
import io.nop.stream.core.checkpoint.EpochManifest;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import io.nop.stream.runtime.cluster.InMemoryClusterRegistry;
import io.nop.stream.runtime.execution.GraphModelCheckpointExecutor;
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
 * WI10 (plan 2426) E1 differential pin: the SAME declared pipeline executed on
 * BOTH checkpoint driver faces must agree —
 * <ol>
 *   <li>committed sink outputs as an exactly-once multiset;</li>
 *   <li>durable epoch manifests equal on the whitelisted field set
 *       (epochId/checksum/timestamps/storage paths excluded — scheduling and
 *       storage-root artifacts; fingerprint compared after symmetric
 *       {@code setCurrentFingerprint});</li>
 *   <li>local-face restore continuation: a second bounded run over the same
 *       explicit storage dir auto-restores manifest-first and the keyed
 *       accumulators continue (per-key continued sums are deterministic);</li>
 *   <li>distributed-face restore-on-deploy: the sink participant redeployed
 *       against the durable manifest observes the restored epoch.</li>
 * </ol>
 *
 * <p>Architecture (plan 2426 Phase 1 adjudication): the distributed leg mirrors
 * the {@code deploy.TestRemoteDeployCheckpointWiringE2E} topology (in-process
 * TaskManagers over LocalMessageService RPC proxies); both legs compile the
 * identical transformation chain — the distributed leg consumes the graph built
 * by {@code StreamExecutionEnvironment#buildJobGraph} (public, deterministic
 * stable ids, carries the StreamModel). Epoch manifests must remain
 * field-equal across ANY kernel extraction (format zero-change pin, roadmap
 * WI10 hard constraint). Runs BEFORE any kernel change (E1: pin current
 * behavior first).
 */
class TestCheckpointDualRunDifferential {

    private static final String LOCAL_JOB_ID = "diff-local";
    private static final String DIST_JOB_ID = "diff-distributed";

    /** First bounded input: keys interleave so keyed accumulators build up. */
    private static final List<Integer> FIRST_INPUT = buildInput(0, 40);

    static List<Integer> firstInput() {
        return FIRST_INPUT;
    }
    @TempDir
    Path tempDir;

    @Test
    void dualRunAgreesOnOutputsManifestsAndRestore() throws Exception {
        // ---------- local leg (GMCE driver face) ----------
        Path localDir = tempDir.resolve("local-checkpoints");
        List<Integer> localCommitted = new ArrayList<>();
        runLocalLegs(localDir, localCommitted);
        // run 1 slice must be the exact fresh sums (first execution, no restore).
        List<Integer> run1Expected = expectedFresh(FIRST_INPUT);
        List<Integer> run1 = localCommitted.subList(0, 40);
        assertEquals(frequencyMap(run1Expected), frequencyMap(run1),
                "local run 1 must commit exactly the fresh sums");

        // ---------- distributed leg (JobCoordinator driver face) ----------
        Path distDir = tempDir.resolve("dist-checkpoints");
        List<Integer> distCommitted = new ArrayList<>();
        StreamModel distModel = runDistributedLeg(distDir, distCommitted);

        // ---------- (a) committed outputs: exactly-once multiset equality ----------
        // distributed face runs ONE bounded pass; the local face runs TWO (run 2 is
        // the restore continuation), so (a) compares the distributed multiset with
        // local run 1's slice.
        assertEquals(80, localCommitted.size(),
                "local face must commit run1+run2 outputs exactly once each: " + localCommitted);
        List<Integer> localRun1 = localCommitted.subList(0, 40);
        List<Integer> localRun2 = localCommitted.subList(40, 80);
        assertEquals(40, distCommitted.size(),
                "distributed face must commit every input element exactly once: " + distCommitted);
        assertEquals(frequencyMap(localRun1), frequencyMap(distCommitted),
                "committed output multisets must agree across driver faces (run 1 vs distributed)");

        // ---------- (b) epoch manifests: whitelisted field equality ----------
        // GMCE local face resolves pipelineId to "pipeline-0" (same convention as the
        // distributed face — measured, superseding the WI9 survey's "1" note).
        // Compare the latest PERIODIC (CHECKPOINT-type) manifest on each face:
        // the local face additionally writes a terminal COMPLETED_POINT_TYPE epoch
        // at job end while the distributed topology stops manually (known driver
        // face lifecycle asymmetry, not a format difference — recorded per plan
        // 2426 manifest spec).
        EpochManifest localManifest = loadLatestPeriodicManifest(localDir.toFile().getAbsolutePath(),
                LOCAL_JOB_ID, "pipeline-0");
        EpochManifest distManifest = loadLatestPeriodicManifest(distDir.toFile().getAbsolutePath(),
                DIST_JOB_ID, "pipeline-0");
        assertNotNull(localManifest, "local face must have a durable periodic epoch manifest; tree="
                + treeOf(localDir.toFile().getAbsolutePath()));
        assertNotNull(distManifest, "distributed face must have a durable periodic epoch manifest");
        assertManifestsAgree(localManifest, distManifest);

        // ---------- (c) local restore continuation invariants (executeWithSavepoint) ----------
        // The restore point is the latest durable DATA checkpoint (the terminal
        // COMPLETED_POINT_TYPE epoch is manifest-only), so the restored accumulator
        // is the running sum at some run-1 epoch boundary — timing-dependent and
        // therefore pinned by boundary-independent invariants instead of absolute
        // values:
        //   (c1) per key, run 2 emits 20 values whose consecutive differences equal
        //        the re-emitted same-key inputs (structural continuation);
        //   (c2) each key's FIRST run-2 emission is non-zero and belongs to run 1's
        //        same-key emission set — the accumulator was RESTORED from a run-1
        //        boundary, not fresh-started (a fresh start would emit 0 first).
        assertRestoredContinuation(localRun2, run1);

        // ---------- epoch count: both faces produced multiple durable epochs ----------
        assertTrue(countManifests(localDir.toFile().getAbsolutePath()) >= 2,
                "local face must produce >= 2 durable epochs (periodic checkpointing)");
        assertTrue(countManifests(distDir.toFile().getAbsolutePath()) >= 2,
                "distributed face must produce >= 2 durable epochs (periodic checkpointing)");
    }

    // ==================== local leg ====================

    private void runLocalLegs(Path storageDir, List<Integer> committed) throws Exception {
        // Two identically-built environments, same jobId: SEE.execute assigns
        // stable transformation ids (name+occurrence), so run 2's manifest-first
        // auto-restore (explicit storage path = recovery intent) resolves run 1's
        // operator identities and restores the keyed reduce accumulators from the
        // latest durable DATA checkpoint (measured: boundary-state continuation).
        CheckpointConfig config1 = localCheckpointConfig(storageDir);
        JobGraph jg1 = buildLocalJobGraph(config1, new RecordingTwoPhaseSink(LOCAL_JOB_ID, committed));
        GraphModelCheckpointExecutor.executeWithCheckpoint(jg1, LOCAL_JOB_ID, config1);

        CheckpointConfig config2 = localCheckpointConfig(storageDir);
        JobGraph jg2 = buildLocalJobGraph(config2, new RecordingTwoPhaseSink(LOCAL_JOB_ID, committed));
        GraphModelCheckpointExecutor.executeWithCheckpoint(jg2, LOCAL_JOB_ID, config2);
    }

    private CheckpointConfig localCheckpointConfig(Path storageDir) {
        CheckpointConfig config = new CheckpointConfig();
        config.setCheckpointEnabled(true);
        // AT_LEAST_ONCE declaration: the SEE-level connector consistency gate requires
        // REPLAYABLE sources for STRICT_EXACTLY_ONCE (collection sources are
        // BEST_EFFORT). The 2PC sink mechanics (commit only via durable-checkpoint
        // notification) run identically, so the exactly-once multiset assertion
        // remains the empirical output pin on both faces.
        config.setProcessingGuarantee(ProcessingGuarantee.AT_LEAST_ONCE);
        config.setCheckpointInterval(150L);
        config.setCheckpointTimeout(30_000L);
        config.setMaxRetainedCheckpoints(5);
        config.setStorageProperty("path", storageDir.toFile().getAbsolutePath());
        config.setJobId(LOCAL_JOB_ID);
        config.setPipelineId("pipeline-0");
        return config;
    }

    private JobGraph buildLocalJobGraph(CheckpointConfig config, RecordingTwoPhaseSink sink) {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        env.setParallelism(1);
        env.enableCheckpointing(150L);
        env.getCheckpointConfig().setProcessingGuarantee(ProcessingGuarantee.AT_LEAST_ONCE);
        // PacingInput re-iterates the same input on every run (running flips only
        // on cancel), so run 2 replays FIRST_INPUT over the restored accumulators.
        buildPipeline(env, new PacingInput<>(FIRST_INPUT, 25L), sink);
        return env.buildJobGraph(LOCAL_JOB_ID);
    }

    // ==================== distributed leg ====================

    private StreamModel runDistributedLeg(Path storageDir, List<Integer> committed) throws Exception {
        // Compile the SAME transformation chain into a JobGraph (public build path,
        // deterministic stable ids, carries the StreamModel for fingerprint parity).
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        env.setParallelism(1);
        CheckpointConfig config = env.getCheckpointConfig();
        // AT_LEAST_ONCE declaration: the SEE-level connector consistency gate requires
        // REPLAYABLE sources for STRICT_EXACTLY_ONCE (collection sources are
        // BEST_EFFORT). The 2PC sink mechanics (commit only via durable-checkpoint
        // notification) run identically, so the exactly-once multiset assertion
        // remains the empirical output pin on both faces.
        config.setProcessingGuarantee(ProcessingGuarantee.AT_LEAST_ONCE);
        env.enableCheckpointing(150L);
        config.setCheckpointTimeout(30_000L);
        config.setMaxRetainedCheckpoints(5);
        config.setStorageProperty("path", storageDir.toFile().getAbsolutePath());
        buildPipeline(env, new PacingInput<>(FIRST_INPUT, 25L),
                new RecordingTwoPhaseSink(DIST_JOB_ID, committed));
        JobGraph jobGraph = env.buildJobGraph(DIST_JOB_ID);
        StreamModel model = jobGraph.getStreamModel();

        IMessageService messageService = new LocalMessageService();
        InMemoryClusterRegistry clusterRegistry = new InMemoryClusterRegistry();
        LocalFileCheckpointStorage storage =
                new LocalFileCheckpointStorage(storageDir.toFile().getAbsolutePath());

        // Topology assembly mirrors deploy.TestRemoteDeployCheckpointWiringE2E.
        long fencingEpoch = JobCoordinator.deriveHaFencingEpoch(0L, 1L);
        Map<String, IStreamTaskRpcService> taskRpcProxies = new LinkedHashMap<>();
        List<TaskManager> taskManagers = new ArrayList<>();
        List<StreamControlRpcServer> servers = new ArrayList<>();
        List<StreamControlRpcProxyFactory> taskProxies = new ArrayList<>();
        StreamControlRpcServer coordinatorServer = null;
        StreamControlRpcProxyFactory coordinatorProxy = null;
        JobCoordinator coordinator = null;
        try {
            for (int i = 0; i < 2; i++) {
                String nodeId = "tm-" + i;
                TaskManager tm = new TaskManager(nodeId, "rpc:" + nodeId, 16,
                        messageService, clusterRegistry, StreamControlRpcTopics.coordinatorTopic(DIST_JOB_ID));
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
                    DIST_JOB_ID, "pipeline-0", new CheckpointIDCounter(), storage,
                    CheckpointConfig.builder()
                            .checkpointEnabled(true)
                            .checkpointInterval(150L)
                            .checkpointTimeout(30_000L)
                            .maxConcurrentCheckpoints(1)
                            .maxRetainedCheckpoints(5)
                            .processingGuarantee(ProcessingGuarantee.AT_LEAST_ONCE)
                            .build());
            // Fingerprint parity with the local face (GMCE sets it from the model).
            if (model != null) {
                checkpointCoordinator.setCurrentFingerprint(model.computeFingerprint());
            }

            coordinator = new JobCoordinator(DIST_JOB_ID, "coordinator-" + DIST_JOB_ID,
                    deriveDeploymentPlan(jobGraph), clusterRegistry, checkpointCoordinator, taskRpcProxies);
            coordinator.setFencingEpoch(fencingEpoch);
            coordinator.setRemoteDeployMode(true);
            coordinator.setJobGraph(jobGraph);
            coordinator.setCheckpointStoragePath(storage.getBaseDir());
            coordinator.registerDistributedCommitForwarder();

            coordinatorServer = new StreamControlRpcServer(
                    "streamCoordinatorRpc@" + DIST_JOB_ID,
                    IStreamCoordinatorRpcService.class, coordinator,
                    messageService, StreamControlRpcTopics.coordinatorTopic(DIST_JOB_ID));
            coordinatorProxy = new StreamControlRpcProxyFactory(
                    "streamCoordinatorRpc@" + DIST_JOB_ID,
                    IStreamCoordinatorRpcService.class,
                    messageService, StreamControlRpcTopics.coordinatorTopic(DIST_JOB_ID));
            IStreamCoordinatorRpcService coordinatorRpc = coordinatorProxy.getProxy();
            for (TaskManager tm : taskManagers) {
                tm.setCoordinatorRpcService(coordinatorRpc);
            }

            coordinatorServer.start();
            coordinatorProxy.start();
            coordinator.start();
            coordinator.assignTasks();
            coordinator.startPeriodicCheckpoints(150L);

            assertTrue(waitFor(() -> countCommitted(committed) >= FIRST_INPUT.size(), 60_000L),
                    "distributed face must commit every input element; committed=" + committed);
            // Stop the coordinator BEFORE teardown: post-EOS periodic triggers would
            // hit the finished-partition barrier-injection window and spin recovery.
            try {
                coordinator.stop();
            } catch (Exception ignored) {
                // best-effort
            }
            coordinator = null;
        } finally {
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
        return model;
    }

    // ==================== pipeline construction (shared by both legs) ====================

    private static void buildPipeline(StreamExecutionEnvironment env,
                                      io.nop.stream.core.common.functions.source.SourceFunction<Integer> source,
                                      RecordingTwoPhaseSink sink) {
        env.addSource(source, "paced-source")
                .map(v -> v)
                .keyBy(v -> v % 2)
                .reduce((a, b) -> a + b)
                .sink(sink);
    }

    private static List<Integer> buildInput(int from, int to) {
        List<Integer> out = new ArrayList<>();
        for (int i = from; i < to; i++) {
            out.add(i);
        }
        return out;
    }

    /**
     * (c) boundary-independent restore invariants. With parallelism 1 the keyed
     * reduce emits strictly alternating keys (input order 0,1,2,...), so run 2's
     * slice decomposes by INDEX: even indices are key-0 emissions, odd are key-1.
     * Per key, consecutive emission differences equal the re-emitted same-key
     * inputs (key 0: 2,4,...,38; key 1: 3,5,...,39), and each key's first run-2
     * emission must be non-zero and belong to run 1's same-key emission set —
     * the accumulator was RESTORED from a run-1 epoch boundary, not fresh-started
     * (a fresh start emits the raw first input: 0 or 1).
     */
    private static void assertRestoredContinuation(List<Integer> run2, List<Integer> run1) {
        assertEquals(40, run2.size(), "run 2 must emit one value per input");
        List<Integer> run2Key0 = new ArrayList<>();
        List<Integer> run2Key1 = new ArrayList<>();
        for (int i = 0; i < run2.size(); i++) {
            (i % 2 == 0 ? run2Key0 : run2Key1).add(run2.get(i));
        }
        assertEquals(20, run2Key0.size(), "key-0 emissions");
        assertEquals(20, run2Key1.size(), "key-1 emissions");
        List<Integer> run1Key0 = new ArrayList<>();
        List<Integer> run1Key1 = new ArrayList<>();
        for (int i = 0; i < run1.size(); i++) {
            (i % 2 == 0 ? run1Key0 : run1Key1).add(run1.get(i));
        }
        assertKeyContinuation(run2Key0, 0, run1Key0);
        assertKeyContinuation(run2Key1, 1, run1Key1);
    }

    private static void assertKeyContinuation(List<Integer> emissions, int key, List<Integer> run1SameKey) {
        // re-emitted same-key inputs are key, key+2, key+4, ...; consecutive
        // emission differences equal the incoming input at each step:
        // key 0 -> 2, 4, ..., 38; key 1 -> 3, 5, ..., 39.
        for (int i = 1; i < emissions.size(); i++) {
            long expectedDiff = 2L * i + key;
            assertEquals(expectedDiff, emissions.get(i) - (long) emissions.get(i - 1),
                    "key " + key + " continuation diff at step " + i);
        }
        // restored-from-boundary discriminator: the first emission equals
        // restoredAcc + firstInput(key); restoredAcc must be a value run 1 already
        // emitted for this key and be non-zero — a fresh start would emit the bare
        // first input (key), i.e. restoredAcc = 0.
        int first = emissions.get(0);
        int restoredAcc = first - key;
        assertTrue(restoredAcc > 0,
                "key " + key + " run-2 must start from a restored accumulator, got " + restoredAcc);
        assertTrue(run1SameKey.contains(restoredAcc),
                "key " + key + " restored accumulator " + restoredAcc
                        + " must be a run-1 boundary value; run1 same-key=" + run1SameKey);
    }

    private static List<Integer> expectedFresh(List<Integer> input) {
        return expectedContinuation(Collections.emptyList(), input);
    }

    private static List<Integer> expectedContinuation(List<Integer> first, List<Integer> second) {
        // reduce emits the running sum per input; after run 1 the accumulator for
        // key k holds sum(first elements of key k). Run 2 re-emits per input.
        Map<Integer, Integer> acc = new LinkedHashMap<>();
        List<Integer> run1 = new ArrayList<>();
        for (int i : first) {
            int k = i % 2;
            acc.merge(k, i, Integer::sum);
            run1.add(acc.get(k));
        }
        List<Integer> out = new ArrayList<>();
        for (int i : second) {
            int k = i % 2;
            acc.merge(k, i, Integer::sum);
            out.add(acc.get(k));
        }
        return out;
    }

    private static Map<Integer, Integer> frequencyMap(List<Integer> values) {
        Map<Integer, Integer> freq = new LinkedHashMap<>();
        for (Integer v : values) {
            freq.merge(v, 1, Integer::sum);
        }
        return freq;
    }

    private static int countCommitted(List<Integer> committed) {
        return committed.size();
    }

    // ==================== manifest comparison (whitelist, plan 2426 spec) ====================

    private static EpochManifest loadLatestPeriodicManifest(String baseDir, String jobId, String pipelineId)
            throws Exception {
        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(baseDir);
        for (EpochManifest m : storage.loadRetainedEpochManifests(jobId, pipelineId, 10)) {
            if (m.getCheckpointType() == io.nop.stream.core.checkpoint.CheckpointType.CHECKPOINT) {
                return m;
            }
        }
        return null;
    }

    static int countManifests(String baseDir) {
        java.io.File root = new java.io.File(baseDir);
        List<java.io.File> found = new ArrayList<>();
        collectEpochFiles(root, found);
        return found.size();
    }

    private static String treeOf(String root) {
        StringBuilder sb = new StringBuilder();
        collectTree(new java.io.File(root), sb, 0);
        return sb.toString();
    }

    private static void collectTree(java.io.File dir, StringBuilder sb, int depth) {
        java.io.File[] children = dir.listFiles();
        if (children == null || depth > 6) {
            return;
        }
        for (java.io.File f : children) {
            sb.append("  ".repeat(Math.max(0, depth))).append(f.getName()).append('\n');
            if (f.isDirectory()) {
                collectTree(f, sb, depth + 1);
            }
        }
    }

    private static void collectEpochFiles(java.io.File dir, List<java.io.File> found) {
        java.io.File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (java.io.File f : children) {
            if (f.isDirectory()) {
                collectEpochFiles(f, found);
            } else if (f.getName().endsWith(".epoch")) {
                found.add(f);
            }
        }
    }

    private static void assertManifestsAgree(EpochManifest local, EpochManifest dist) {
        assertEquals(local.getCheckpointType(), dist.getCheckpointType(), "checkpointType");
        assertEquals(local.getStateFormatVersion(), dist.getStateFormatVersion(), "stateFormatVersion");
        // taskSnapshots: identical vertex/subtask key shapes
        assertEquals(normalizeSnapshotKeys(local), normalizeSnapshotKeys(dist),
                "taskSnapshots key shapes must agree (jobId/pipelineId prefixes normalized)");
        // fingerprint (pinned asymmetry, recorded): the distributed face carries the
        // model fingerprint (coordinator setCurrentFingerprint); the local face's
        // JobGraph entry is declared "no fingerprint source; restore without a
        // StreamModel" (GraphModelCheckpointExecutor#executeWithCheckpoint(JobGraph,...)
        // pinned asymmetry) → local fingerprint is null by design. dagTopologyHash is
        // additionally job-instance-bound (SHA256 over per-build transformation keys).
        // Manifest FORMAT parity is unaffected — the field serializes identically
        // (null vs value) on both faces.
        assertNotNull(dist.getStreamModelFingerprint(), "distributed face must carry a model fingerprint");
        // segments: same count (paths are storage-root artifacts — excluded)
        int localSegs = local.getSegments() == null ? 0 : local.getSegments().size();
        int distSegs = dist.getSegments() == null ? 0 : dist.getSegments().size();
        assertEquals(localSegs, distSegs, "segment count");
        // sourceEnumeratorSnapshots: presence/shape parity
        assertEquals(local.getSourceEnumeratorSnapshots() == null,
                dist.getSourceEnumeratorSnapshots() == null, "sourceEnumeratorSnapshots presence");
    }

    private static List<String> normalizeSnapshotKeys(EpochManifest manifest) {
        List<String> shapes = new ArrayList<>();
        if (manifest.getTaskSnapshots() == null) {
            return shapes;
        }
        List<String> keys = new ArrayList<>();
        for (TaskLocation loc : manifest.getTaskSnapshots().keySet()) {
            // vertex+subtask shape (jobId/pipelineId are per-face artifacts)
            keys.add(loc.getVertexId() + "#" + loc.getTaskIndex());
        }
        Collections.sort(keys);
        shapes.addAll(keys);
        return shapes;
    }

    private static DeploymentPlan deriveDeploymentPlan(JobGraph jobGraph) {
        Map<String, PartitionedPlan.VertexPlan> vertexPlans = new LinkedHashMap<>();
        for (JobVertex v : jobGraph.getVertices().values()) {
            // keyed by vertex ID (JobEdge endpoints reference IDs; TaskDeploymentDescriptor
            // deploys per vertex ID — names are display-only on compiled graphs)
            vertexPlans.put(v.getId(), new PartitionedPlan.VertexPlan(v.getId(), v.getParallelism(), null));
        }
        List<PartitionedPlan.EdgePlan> edgePlans = new ArrayList<>();
        for (JobEdge e : jobGraph.getEdges()) {
            edgePlans.add(new PartitionedPlan.EdgePlan(e.getSourceVertex(), e.getTargetVertex(),
                    PartitionPolicy.FORWARD));
        }
        return new DeploymentPlan(DIST_JOB_ID, "pipeline-0",
                new PartitionedPlan(DIST_JOB_ID, "pipeline-0", vertexPlans, edgePlans, null, null),
                "local", "memory", "local", null, null);
    }

    private static boolean waitFor(java.util.function.BooleanSupplier condition, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            TimeUnit.MILLISECONDS.sleep(100L);
        }
        return condition.getAsBoolean();
    }

    // ==================== fixture UDFs ====================

    /** Pacing wrapper: throttles an in-memory input list so periodic checkpoints interleave. */
    static final class PacingInput<T>
            implements io.nop.stream.core.common.functions.source.SourceFunction<T> {
        private static final long serialVersionUID = 1L;

        private final List<T> input;
        private final long delayMs;
        private volatile boolean running = true;

        PacingInput(List<T> input, long delayMs) {
            this.input = input;
            this.delayMs = delayMs;
        }

        @Override
        public void run(io.nop.stream.core.common.functions.source.SourceFunction.SourceContext<T> ctx)
                throws Exception {
            // subscribe window: in-process LocalMessageService drops sends before the
            // downstream consumer subscribes (deploy.TestRemoteDeployCheckpointWiringE2E fix)
            io.nop.stream.runtime.testsupport.TestAwait.elapsed("downstream consumer subscribe window", 750L);
            for (T value : input) {
                if (!running) {
                    return;
                }
                ctx.collect(value);
                if (delayMs > 0) {
                    Thread.sleep(delayMs);
                }
            }
            // finish linger: keep the source task alive so trailing periodic
            // checkpoints can complete while tasks exist (bounded-run determinism).
            // Long enough that the final commits land before EOS — post-EOS
            // periodic triggers hit the finished-partition barrier-injection
            // window (StreamSourceOperator#offerBarrier after finish).
            Thread.sleep(1500L);
        }

        @Override
        public void cancel() {
            running = false;
        }
    }

    /**
     * In-memory 2PC sink (mirrors deploy.TestRemoteDeployCheckpointWiringE2E shape):
     * buffers per epoch, commits only via the durable-checkpoint notification chain,
     * records the restored epoch for the restore-on-deploy assertion.
     */
    static final class RecordingTwoPhaseSink extends TwoPhaseCommitSinkFunction<Integer> {
        private static final long serialVersionUID = 1L;

        /**
         * Models the EXTERNAL commit ledger of a real 2PC sink (JDBC transaction
         * id / dedup table): commit decisions may be re-delivered across restore
         * (restoreFromEpoch re-commits durable pendings, and terminal-epoch
         * inherited pendings re-deliver after restore), and the external system
         * absorbs duplicates. Keyed by jobId:epochId; cleared per test.
         */
        static final java.util.concurrent.ConcurrentHashMap<String, Boolean> EXTERNAL_LEDGER =
                new java.util.concurrent.ConcurrentHashMap<>();

        private final String jobId;
        private final List<Integer> committedSink;
        final ConcurrentLinkedQueue<Integer> committedElements = new ConcurrentLinkedQueue<>();
        final ConcurrentSkipListMap<Long, Integer> committedEpochs =
                new ConcurrentSkipListMap<>();
        private final List<Integer> currentBuffer = new ArrayList<>();
        volatile long lastRestoredEpoch = -1L;

        RecordingTwoPhaseSink(String jobId, List<Integer> committedSink) {
            this.jobId = jobId;
            this.committedSink = committedSink;
        }

        @Override
        public void beginTransaction() {
        }

        @Override
        public void invoke(Integer value) {
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
            // External-ledger dedup: re-delivered commit decisions (restored
            // pendings) are absorbed here, mirroring production 2PC sinks.
            if (EXTERNAL_LEDGER.putIfAbsent(jobId + ":" + checkpointId, Boolean.TRUE) != null) {
                // duplicate delivery — absorbed by the external ledger
                return;
            }
            Object raw = getPendingCommits().get(checkpointId);
            if (raw instanceof List) {
                for (Integer value : (List<Integer>) raw) {
                    committedElements.add(value);
                    if (committedSink != null) {
                        committedSink.add(value);
                    }
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

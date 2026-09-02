/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.fraud.scenario;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import io.nop.core.initialize.CoreInitialization;
import io.nop.stream.runtime.multijvm.MiniStreamCluster;

import static io.nop.stream.fraud.scenario.MultiJvmTestSupport.COORDINATOR_LABEL;
import static io.nop.stream.fraud.scenario.MultiJvmTestSupport.readLatestFencingEpoch;
import static io.nop.stream.fraud.scenario.MultiJvmTestSupport.waitFor;
import static io.nop.stream.fraud.scenario.MultiJvmTestSupport.waitForDurableManifest;
import static io.nop.stream.fraud.scenario.MultiJvmTestSupport.waitForEpochRotation;
import static io.nop.stream.fraud.scenario.MultiJvmTestSupport.waitForExactlyOnceOutput;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.S2_STREAM_PATH;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.T0;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Item 14 (composite-scenario distributed, matrix C2 restore-rescale — S2 mandatory
 * cell): the distributed restore-rescale DRILL. Run 1 deploys the S2 scenario
 * pipeline on 2 TaskManagers and is STOPPED (graceful cluster shutdown) after a
 * durable checkpoint exists with partial committed output while the source is
 * still emitting; run 2 relaunches the SAME job identity (jobId + checkpoint
 * storage directory) on 3 TaskManagers with a strictly greater fencing epoch.
 *
 * <p>Acceptance (design §3.3 C2): after the TM-topology change the run converges
 * to the FULL expected output set — the file source's byte cursors, the keyed
 * window state and the sink's manifest idempotency guard all restore from the
 * shared checkpoint storage across real JVM boundaries (no duplicate rows from
 * cursor replay, no lost windows, manifest keys == committed epoch files).
 *
 * <p><b>Matrix adjudication note</b>: the keyed-PARALLELISM rescale form
 * (P&gt;1 together with the exactly-once 2PC sink) is NOT expressible today — the
 * engine fail-fasts a 2PC sink at effective parallelism &gt; 1
 * ({@code ERR_STREAM_2PC_SINK_PARALLELISM_NOT_SUPPORTED}, CONN-01 P1 deliberate
 * deferral) and supports only stream-level uniform parallelism
 * ({@code Transformation.parallelism} final). That form is explicitly routed to
 * the CONN-01 successor / per-transform parallelism consumer (roadmap items +
 * checkpoint-design §6.4.1); keyed-state rerouting under a changed key-group
 * layout is covered at the executor level (LOCAL
 * {@code TestKeyGroupRescaleDispatchE2E}) and by the offline-reshard restore
 * (plan 2 A2-5). This drill covers the TM-topology-change restore form named by
 * the plan item（「如 TM 数/并行度变更后恢复」）.
 *
 * <p>Gated by {@code -Dnop.stream.test.multi-jvm.enabled=true}.
 */
@EnabledIfSystemProperty(named = "nop.stream.test.multi-jvm.enabled", matches = "true")
class TestS2RestoreRescaleMultiJvmE2E {

    private static final long RECOVERY_TIMEOUT_MS = 120_000L;
    private static final long OVERALL_TIMEOUT_MS = 180_000L;
    private static final long LINE_DELAY_MS = 250L;

    /** Same fixture as the C1 test (bounded core + watermark pump + keep-alive tail). */
    private static java.util.List<String> fixtureLines(int extraKeepAliveLines) {
        java.util.List<String> lines = new java.util.ArrayList<>(java.util.Arrays.asList(
                "u2,30," + (T0 + 1000),
                "u1,70," + (T0 + 2000),
                "u1,50," + (T0 + 0),
                "user-bob,100," + (T0 + 3000),
                "u3,80," + (T0 + 4000),
                "u2,20," + (T0 + 1500),
                "u1,60," + (T0 + 8000),
                "user-eve,90," + (T0 + 9000),
                "u3,40," + (T0 + 12000),
                "u1,90," + (T0 + 11000),
                "user-bob,110," + (T0 + 13000),
                "u9,1," + (T0 + 25000),
                "u9,2," + (T0 + 26000),
                "u9,3," + (T0 + 27000),
                "u9,4," + (T0 + 32000),
                "u-keep,1," + (T0 + 40_000),
                "u-keep,1," + (T0 + 41_000),
                "u-keep,1," + (T0 + 42_000)));
        for (int i = 0; i < extraKeepAliveLines; i++) {
            lines.add("u-keep,10," + (T0 + 40_000 + i * 1_000L));
        }
        return lines;
    }

    private static void configureScenarioArgs(MiniStreamCluster cluster, String jobId,
                                              Path inputDir, Path outputDir) {
        cluster.withJobId(jobId);
        cluster.withCheckpointDir(outputDir.getParent().resolve("checkpoints"));
        cluster.withCoordinatorArg("pipelineFactoryClass=" + S2ScenarioPipelineFactory.class.getName());
        cluster.withCoordinatorArg(DistributedScenarioSupport.KEY_STREAM_PATH + "=" + S2_STREAM_PATH);
        cluster.withCoordinatorArg(DistributedScenarioSupport.KEY_S2_INPUT_DIR + "=" + inputDir);
        cluster.withCoordinatorArg(DistributedScenarioSupport.KEY_S2_OUTPUT_DIR + "=" + outputDir);
        cluster.withCoordinatorArg(DistributedScenarioSupport.KEY_S2_LINE_DELAY + "=" + LINE_DELAY_MS);
        cluster.withCoordinatorArg(DistributedScenarioSupport.KEY_S2_LINGER + "=800");
        cluster.withCoordinatorArg(DistributedScenarioSupport.KEY_CHECKPOINT_INTERVAL + "=400");
        cluster.withCoordinatorArg(DistributedScenarioSupport.KEY_CHECKPOINT_TIMEOUT + "=30000");
        cluster.withCoordinatorArg(DistributedScenarioSupport.KEY_MAX_RETAINED + "=5");
    }

    @Test
    void s2RestoreRescaleAcrossTmTopology2To3ConvergesExactlyOnce() throws Exception {
        CoreInitialization.initialize();

        // Shared sibling dir OUTSIDE any cluster runDir so it survives run 1's close().
        MiniStreamCluster run1 = new MiniStreamCluster(2,
                /* healthTimeoutMs */ 120_000L, /* killGraceMs */ 5_000L, /* pollIntervalMs */ 50L);
        Path sharedBase = run1.getRunDir().getParent()
                .resolve(run1.getRunId() + "-rescale-shared");
        Path inputDir = sharedBase.resolve("input");
        Path outputDir = sharedBase.resolve("output");
        Files.createDirectories(inputDir);
        Files.write(inputDir.resolve("part-001.txt"),
                String.join("\n", fixtureLines(/* extraKeepAliveLines */ 40)).getBytes());

        String jobId = "job-rescale-drill-" + run1.getRunId();
        configureScenarioArgs(run1, jobId, inputDir, outputDir);

        long run1FencingEpoch;
        try (MiniStreamCluster cluster = run1) {
            cluster.start();

            // Restore precondition (plan guard): a durable manifest must exist BEFORE
            // the stop, and partial output must already be committed mid-run.
            long durableEpoch = waitForDurableManifest(cluster, jobId, OVERALL_TIMEOUT_MS);
            assertTrue(durableEpoch >= 0, "durable manifest must exist before stopping run 1");
            waitFor(() -> !TestS2FileAggregationE2E.readOutputRows(outputDir).isEmpty(),
                    OVERALL_TIMEOUT_MS, "partial output must be committed before stopping run 1");

            run1FencingEpoch = readLatestFencingEpoch(cluster);
            assertTrue(run1FencingEpoch > 0, "run 1 fencing epoch must be positive");
            // Graceful shutdown (SIGTERM to coordinator + TMs); the shared dirs are
            // outside runDir and survive the harness cleanup.
        }

        // ---- Run 2: same job identity, 3 TaskManagers, strictly greater fencing ----
        try (MiniStreamCluster cluster = new MiniStreamCluster(3,
                /* healthTimeoutMs */ 120_000L, /* killGraceMs */ 5_000L, /* pollIntervalMs */ 50L)) {
            configureScenarioArgs(cluster, jobId, inputDir, outputDir);
            // The fresh coordinator generation must fence strictly above run 1's epoch.
            cluster.withCoordinatorArg("fencingEpoch=" + (run1FencingEpoch + 1000));
            cluster.start();

            // C2 form: the job now spans 3 TMs.
            assertEquals(3, cluster.expectedNodeIds().size());
            assertEquals(3, cluster.registeredNodeIds().size(),
                    "all 3 replacement TaskManagers must register");

            // Fencing observable: assignments under the new generation carry a
            // strictly greater epoch (run 2's coordinator fenced run 1's generation).
            long run2Epoch = waitForEpochRotation(cluster, run1FencingEpoch, RECOVERY_TIMEOUT_MS);
            assertTrue(run2Epoch > run1FencingEpoch,
                    "run 2 fencing epoch must strictly exceed run 1's ("
                            + run1FencingEpoch + " vs " + run2Epoch + ")");

            // Restore convergence: the final output == FULL expected set — the file
            // cursors resume from the durable epoch (no re-read duplicates), the
            // keyed window state continues, and the sink manifest skips
            // durable-but-already-committed epochs (exactly-once under restore).
            waitForExactlyOnceOutput(outputDir, MultiJvmTestSupport.s2BaseExpectedRows(), OVERALL_TIMEOUT_MS,
                    "S2 restore-rescale run 2 (TM 2->3)");

            // D6 assertion surface (distributed): manifest keys == committed epoch files.
            long epochFileCount;
            try (var files = Files.list(outputDir)) {
                epochFileCount = files.filter(p -> p.getFileName().toString().startsWith("epoch-")).count();
            }
            assertEquals(TestS2FileAggregationE2E.readManifestKeys(outputDir).size(), (int) epochFileCount,
                    "manifest key set must equal the committed epoch file set after the rescale restore");
            assertTrue(cluster.coordinatorAlive(),
                    "coordinator must stay alive; log: "
                            + MultiJvmTestSupport.readLogTail(cluster, COORDINATOR_LABEL));
        }
    }
}

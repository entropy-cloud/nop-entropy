/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.fraud.scenario;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import io.nop.core.initialize.CoreInitialization;
import io.nop.stream.runtime.multijvm.MiniStreamCluster;

import static io.nop.stream.fraud.scenario.MultiJvmTestSupport.COORDINATOR_LABEL;
import static io.nop.stream.fraud.scenario.MultiJvmTestSupport.assertRecoveryLogged;
import static io.nop.stream.fraud.scenario.MultiJvmTestSupport.probeStaleEpochRejection;
import static io.nop.stream.fraud.scenario.MultiJvmTestSupport.readLatestFencingEpoch;
import static io.nop.stream.fraud.scenario.MultiJvmTestSupport.waitFor;
import static io.nop.stream.fraud.scenario.MultiJvmTestSupport.waitForDurableManifest;
import static io.nop.stream.fraud.scenario.MultiJvmTestSupport.waitForEpochRotation;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.T0;
import static io.nop.stream.fraud.scenario.TestS2FileAggregationE2E.assertExactlyOnceOutput;
import static io.nop.stream.fraud.scenario.TestS2FileAggregationE2E.readManifestKeys;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Item 14 (composite-scenario distributed): S2 (file source → keyBy + window
 * aggregation → exactly-once file sink) in REAL multi-JVM mode. Matrix cells:
 *
 * <ul>
 *   <li><b>C0 baseline</b> — base XDSL and the DELTA variant both deploy and produce
 *       the exact pre-computed expected row sets (A2-1/A2-2/A2-7 distributed forms:
 *       multi-subtask epoch files never conflict, manifest keys == epoch files,
 *       no {@code .tmp} residue; the Delta customization is proven EFFECTIVE in
 *       DISTRIBUTED mode — blacklist rows absent).</li>
 *   <li><b>C1 kill TM + recover + fencing</b> — kill AFTER a durable manifest exists,
 *       restart, assert fencing epoch strictly rotated, stale-epoch mutation
 *       observably rejected (R-14 pattern), and the recovered output converges to
 *       the full expected set (file cursors + keyed window state restored; manifest
 *       idempotency — no duplicate epoch files/rows, A2-4's recovery semantics).</li>
 * </ul>
 *
 * <p>Gated by {@code -Dnop.stream.test.multi-jvm.enabled=true} (same run command as
 * {@code TestS1MultiJvmE2E}, substituting {@code -Dtest=TestS2MultiJvmE2E}).
 */
@EnabledIfSystemProperty(named = "nop.stream.test.multi-jvm.enabled", matches = "true")
class TestS2MultiJvmE2E {

    private static final long RECOVERY_TIMEOUT_MS = 120_000L;
    private static final long OVERALL_TIMEOUT_MS = 180_000L;

    /** S2 pacing tuned for the JDBC-polled multi-JVM data plane. */
    private static final long LINE_DELAY_MS = 250L;

    private static Set<TxSummaryRow> baseExpected() {
        return MultiJvmTestSupport.s2BaseExpectedRows();
    }

    private static Set<TxSummaryRow> deltaExpected() {
        Set<TxSummaryRow> rows = new LinkedHashSet<>();
        for (TxSummaryRow r : baseExpected()) {
            if (!"user-bob".equals(r.getUserId()) && !"user-eve".equals(r.getUserId())) {
                rows.add(r);
            }
        }
        return rows;
    }

    private MiniStreamCluster startS2Cluster(String streamVfsPath) throws Exception {
        return startS2Cluster(streamVfsPath, 0);
    }

    /**
     * Spawns the S2 cluster. {@code extraKeepAliveLines} appends that many
     * noise-user lines (1s apart, far-window user "u-keep") AFTER the base
     * fixture — used by the kill/recover test so the bounded source is STILL
     * EMITTING when the SIGTERM lands (killing after the source completed
     * produces no FAILED report and no recovery: same determinism lesson as the
     * legacy {@code TestMultiJvmExactlyOnceRecovery}). The keep-alive user's
     * windows stay in-flight at EOS (never in the expected set), so the
     * assertions are unchanged.
     */
    private MiniStreamCluster startS2Cluster(String streamVfsPath, int extraKeepAliveLines) throws Exception {
        CoreInitialization.initialize();
        MiniStreamCluster cluster = new MiniStreamCluster(2,
                /* healthTimeoutMs */ 120_000L,
                /* killGraceMs */ 5_000L,
                /* pollIntervalMs */ 50L);
        Path runDir = cluster.getCheckpointDir().getParent();
        Path inputDir = runDir.resolve("input");
        Path outputDir = runDir.resolve("output");
        Files.createDirectories(inputDir);

        // Fixture identical to the LOCAL S2 E2E (plan-2 asset): intra-file
        // out-of-order W0 events, cross-key mixing, and the watermark-flush tail.
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
                // Watermark pump (design A.0 note 6 — bounded-source watermark mechanics):
                // a far-future noise event so the W2 window [20s,30s) closes MID-RUN
                // (wm >= 30s) and commits via a periodic checkpoint — in the REMOTE
                // launch no checkpoint can complete after every task completes and
                // deregisters, so a window that only fires at EOS-MAX_WATERMARK would
                // never commit. The u-keep windows ([40s+) stay in-flight at EOS and
                // never enter the expected set. The three post-pump lines give the
                // committing periodic checkpoint ~750ms of margin before EOS.
                "u-keep,1," + (T0 + 40_000),
                "u-keep,1," + (T0 + 41_000),
                "u-keep,1," + (T0 + 42_000)));
        for (int i = 0; i < extraKeepAliveLines; i++) {
            // 1s apart, far beyond the expected windows (T0+40s..): pure emission
            // keep-alive; the u-keep windows never close below the final watermark.
            lines.add("u-keep,10," + (T0 + 40_000 + i * 1_000L));
        }
        Files.write(inputDir.resolve("part-001.txt"), String.join("\n", lines).getBytes());

        cluster.withCoordinatorArg("pipelineFactoryClass=" + S2ScenarioPipelineFactory.class.getName());
        cluster.withCoordinatorArg(DistributedScenarioSupport.KEY_STREAM_PATH + "=" + streamVfsPath);
        cluster.withCoordinatorArg(DistributedScenarioSupport.KEY_S2_INPUT_DIR + "=" + inputDir);
        cluster.withCoordinatorArg(DistributedScenarioSupport.KEY_S2_OUTPUT_DIR + "=" + outputDir);
        cluster.withCoordinatorArg(DistributedScenarioSupport.KEY_S2_LINE_DELAY + "=" + LINE_DELAY_MS);
        cluster.withCoordinatorArg(DistributedScenarioSupport.KEY_S2_LINGER + "=800");
        cluster.withCoordinatorArg(DistributedScenarioSupport.KEY_CHECKPOINT_INTERVAL + "=400");
        cluster.withCoordinatorArg(DistributedScenarioSupport.KEY_CHECKPOINT_TIMEOUT + "=30000");
        cluster.withCoordinatorArg(DistributedScenarioSupport.KEY_MAX_RETAINED + "=5");

        cluster.start();
        return cluster;
    }

    private Path outputDirOf(MiniStreamCluster cluster) {
        return cluster.getCheckpointDir().getParent().resolve("output");
    }

    @SuppressWarnings("BusyWait")
    private void waitForExactlyOnceOutput(Path outputDir, Set<TxSummaryRow> expected,
                                          long timeoutMs, String context) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        AssertionError last = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                assertExactlyOnceOutput(outputDir, expected);
                return;
            } catch (AssertionError e) {
                last = e;
            } catch (java.nio.file.NoSuchFileException e) {
                // The output directory only appears when the sink first writes
                // (constructor-time dir creation does not cross the JVM boundary:
                // the running sink is the deserialized bean). A missing directory
                // means "no committed output yet" — keep waiting, not a fatal error.
                last = new AssertionError("output directory not created yet: " + outputDir, e);
            }
            Thread.sleep(500L);
        }
        throw new AssertionError(context + " — output did not converge to the expected set within "
                + timeoutMs + "ms. Last assertion: " + last
                + " outputRows=" + TestS2FileAggregationE2E.readOutputRows(outputDir));
    }

    @Test
    void s2MultiJvmBaselineBaseAndDeltaProduceExactlyOnceOutput() throws Exception {
        try (MiniStreamCluster cluster = startS2Cluster(ScenarioTestSupport.S2_STREAM_PATH)) {
            String jobId = "job-" + cluster.getRunId();
            Path outputDir = outputDirOf(cluster);

            waitForExactlyOnceOutput(outputDir, baseExpected(), OVERALL_TIMEOUT_MS,
                    "S2 base multi-JVM run");
            assertTrue(MultiJvmTestSupport.currentDurableManifestEpoch(cluster, jobId) >= 0,
                    "durable manifest must exist (checkpoint loop works multi-JVM)");
        }

        // A2-2 distributed: the DELTA customization is effective in DISTRIBUTED mode.
        try (MiniStreamCluster cluster = startS2Cluster(ScenarioTestSupport.S2_DELTA_STREAM_PATH)) {
            Path outputDir = outputDirOf(cluster);
            waitForExactlyOnceOutput(outputDir, deltaExpected(), OVERALL_TIMEOUT_MS,
                    "S2 delta multi-JVM run (blacklisted rows must be absent)");
            assertEquals(baseExpected().size() - deltaExpected().size(),
                    baseExpected().stream().filter(r -> "user-bob".equals(r.getUserId())
                            || "user-eve".equals(r.getUserId())).count(),
                    "delta removes exactly the blacklisted users' rows");
        }
    }

    @Test
    void s2MultiJvmKillRecoverFencingExactlyOnce() throws Exception {
        // Keep-alive tail keeps the bounded source emitting for ~15 extra seconds
        // so the kill lands on RUNNING tasks. NOTE: unlike S1's replayable CDC
        // source (whose emitter thread throws InterruptedException on cancel →
        // FAILED report), the S2 bounded file source cancels GRACEFULLY, so the
        // kill itself produces a COMPLETED report, not a failure. The recovery
        // here is driven by the coordinator's LIVENESS DETECTOR: the killed node's
        // lease (15s) expires and the failure detector fires global recovery —
        // exercising the OTHER production recovery trigger (complementary to S1's
        // FAILED-report path). The replacement TM restarts only AFTER the rotation
        // so its registration cannot mask the expired lease.
        try (MiniStreamCluster cluster = startS2Cluster(ScenarioTestSupport.S2_STREAM_PATH, 25)) {
            String jobId = "job-" + cluster.getRunId();
            Path outputDir = outputDirOf(cluster);

            // Kill precondition: durable manifest + partial committed output.
            long initialEpoch = waitForDurableManifest(cluster, jobId, OVERALL_TIMEOUT_MS);
            waitFor(() -> !TestS2FileAggregationE2E.readOutputRows(outputDir).isEmpty(),
                    OVERALL_TIMEOUT_MS, "partial output must be committed before the kill");

            long initialFencingEpoch = readLatestFencingEpoch(cluster);
            assertTrue(initialFencingEpoch > 0, "initial fencing epoch must be positive");
            long coordLogSizeBeforeKill = Files.size(cluster.logFileFor(COORDINATOR_LABEL));

            // C1: kill a REAL TaskManager process (SIGTERM) and LEAVE it dead: the
            // coordinator's failure detector (5s tick / 15s lease) must notice the
            // expired lease and fire global recovery.
            assertTrue(cluster.killTaskManager("tm-1"), "killTaskManager(tm-1) must succeed");

            long recoveredEpoch = waitForEpochRotation(cluster, initialFencingEpoch, RECOVERY_TIMEOUT_MS);
            assertTrue(recoveredEpoch > initialFencingEpoch,
                    "liveness-detector recovery must rotate the fencing epoch: initial=" + initialFencingEpoch
                            + " recovered=" + recoveredEpoch);
            assertRecoveryLogged(cluster, coordLogSizeBeforeKill);

            // Now restart the replacement TM; the recovery redeployment (or the
            // replacement's registration re-sync) must converge the job.
            cluster.restartTaskManager("tm-1");

            String rejection = probeStaleEpochRejection(cluster, "tm-0", initialFencingEpoch);
            assertTrue(rejection.contains("FENCING_TOKEN_MISMATCH")
                            || rejection.contains("fencing-token-mismatch")
                            || rejection.contains("process-request-fail"),
                    "stale-epoch mutation must be observably rejected, log delta: "
                            + rejection.substring(0, Math.min(800, rejection.length())));

            // Recovery converges to the FULL expected set (cursor + keyed window
            // state restored; manifest idempotency — no duplicates/losses).
            waitForExactlyOnceOutput(outputDir, baseExpected(), OVERALL_TIMEOUT_MS,
                    "S2 multi-JVM recovery");

            // Manifest keys == committed epoch files (D6 assertion surface, distributed).
            long epochFileCount;
            try (var files = Files.list(outputDir)) {
                epochFileCount = files.filter(p -> p.getFileName().toString().startsWith("epoch-")).count();
            }
            assertEquals(readManifestKeys(outputDir).size(), (int) epochFileCount,
                    "manifest key set must equal the committed epoch file set");
        }
    }
}

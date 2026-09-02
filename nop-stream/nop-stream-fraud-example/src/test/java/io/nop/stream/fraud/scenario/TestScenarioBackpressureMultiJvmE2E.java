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
import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import io.nop.core.initialize.CoreInitialization;
import io.nop.stream.runtime.multijvm.MiniStreamCluster;

import static io.nop.stream.fraud.scenario.MultiJvmTestSupport.COORDINATOR_LABEL;
import static io.nop.stream.fraud.scenario.MultiJvmTestSupport.currentDurableManifestEpoch;
import static io.nop.stream.fraud.scenario.MultiJvmTestSupport.resetS1Tables;
import static io.nop.stream.fraud.scenario.MultiJvmTestSupport.waitFor;
import static io.nop.stream.fraud.scenario.MultiJvmTestSupport.waitForDurableEpochAdvanceUnderThrottle;
import static io.nop.stream.fraud.scenario.MultiJvmTestSupport.waitForExactlyOnceOutput;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.PATTERN_GEO;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.PATTERN_RAPID;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.PATTERN_TAKEOVER;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.PATTERN_UNUSUAL;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.T0;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.alertRow;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.readAlertRows;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.readLedgerRows;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.sameRows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Item 14 (composite-scenario distributed, matrix C3 backpressure trigger — S1 +
 * S2 cells): bounded in-sink throttling (design §3.3 C3「限速 sink：sink bean 内节流」)
 * via the throttled 2PC sink variants ({@link ThrottledScenarioSinks}).
 *
 * <p>Acceptance (design's observation proxy — no direct backpressure metrics until
 * the observability item lands): <b>while the throttle is engaged</b> the durable
 * checkpoint epochs keep STRICTLY ADVANCING (checkpoint loop alive, no deadlock),
 * and <b>after the release</b> the run converges to the exact expected results
 * (S1: A1-5 alert table + ledgers; S2: A2-7 exactly-once file output). The
 * backpressure-behavior stability drill (long soak, quantification) is explicitly
 * item 15's scope — this test only covers the scenario-acceptance trigger
 * combination.
 *
 * <p>Gated by {@code -Dnop.stream.test.multi-jvm.enabled=true}.
 */
@EnabledIfSystemProperty(named = "nop.stream.test.multi-jvm.enabled", matches = "true")
class TestScenarioBackpressureMultiJvmE2E {

    private static final long OVERALL_TIMEOUT_MS = 180_000L;

    private static Set<AlertSummaryRow> expectedAlerts() {
        Set<AlertSummaryRow> expected = new LinkedHashSet<>();
        expected.add(alertRow(T0 + 2000, "alice", PATTERN_RAPID, 1, "2700"));
        expected.add(alertRow(T0 + 6000, "bob", PATTERN_UNUSUAL, 1, "2500"));
        expected.add(alertRow(T0 + 13000, "dave", PATTERN_TAKEOVER, 1, "3000"));
        expected.add(alertRow(T0 + 16000, "erin", PATTERN_GEO, 1, "350"));
        return expected;
    }

    /**
     * C3/S2: throttled file sink — durable epochs strictly advance while the
     * throttle is engaged, then (after release) the output converges to the full
     * expected set with the complete D6 exactly-once surface.
     */
    @Test
    void s2ThrottledSinkKeepsCheckpointsAdvancingThenConvergesExactlyOnce() throws Exception {
        CoreInitialization.initialize();
        MiniStreamCluster cluster = new MiniStreamCluster(2,
                /* healthTimeoutMs */ 120_000L, /* killGraceMs */ 5_000L, /* pollIntervalMs */ 50L);
        Path runDir = cluster.getRunDir();
        Path inputDir = runDir.resolve("input");
        Path outputDir = runDir.resolve("output");
        Path releaseMarker = runDir.resolve("throttle-release.marker");
        Files.createDirectories(inputDir);

        // Same fixture as the S2 C1/C2 tests: bounded core + watermark pump +
        // keep-alive tail so the run is still EMITTING while the throttle is engaged
        // and after the release (the release must land mid-run).
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
        for (int i = 0; i < 45; i++) {
            lines.add("u-keep,10," + (T0 + 40_000 + i * 1_000L));
        }
        Files.write(inputDir.resolve("part-001.txt"), String.join("\n", lines).getBytes());

        cluster.withCoordinatorArg("pipelineFactoryClass=" + S2ScenarioPipelineFactory.class.getName());
        cluster.withCoordinatorArg(DistributedScenarioSupport.KEY_STREAM_PATH + "="
                + ScenarioTestSupport.S2_STREAM_PATH);
        cluster.withCoordinatorArg(DistributedScenarioSupport.KEY_S2_INPUT_DIR + "=" + inputDir);
        cluster.withCoordinatorArg(DistributedScenarioSupport.KEY_S2_OUTPUT_DIR + "=" + outputDir);
        cluster.withCoordinatorArg(DistributedScenarioSupport.KEY_S2_LINE_DELAY + "=250");
        cluster.withCoordinatorArg(DistributedScenarioSupport.KEY_S2_LINGER + "=800");
        cluster.withCoordinatorArg(DistributedScenarioSupport.KEY_SINK_THROTTLE_MS + "=400");
        cluster.withCoordinatorArg(DistributedScenarioSupport.KEY_THROTTLE_RELEASE_FILE + "=" + releaseMarker);
        cluster.withCoordinatorArg(DistributedScenarioSupport.KEY_CHECKPOINT_INTERVAL + "=400");
        cluster.withCoordinatorArg(DistributedScenarioSupport.KEY_CHECKPOINT_TIMEOUT + "=30000");
        cluster.withCoordinatorArg(DistributedScenarioSupport.KEY_MAX_RETAINED + "=5");

        try (MiniStreamCluster c = cluster) {
            c.start();
            String jobId = "job-" + c.getRunId();

            // C3 acceptance ①: while the throttle marker is ABSENT (every sink record
            // sleeps 400ms before buffering), the durable checkpoint epochs still
            // STRICTLY advance — the checkpoint loop is alive under backpressure.
            long advancedEpoch = waitForDurableEpochAdvanceUnderThrottle(c, jobId,
                    () -> !Files.exists(releaseMarker), OVERALL_TIMEOUT_MS);
            assertTrue(advancedEpoch >= 0, "durable epochs must advance while the throttle is engaged");

            // C3 acceptance ②: release the throttle mid-run; the final output
            // converges to the FULL expected set (A2-7 exactly-once surface).
            Files.writeString(releaseMarker, "released");
            waitForExactlyOnceOutput(outputDir, MultiJvmTestSupport.s2BaseExpectedRows(),
                    OVERALL_TIMEOUT_MS, "S2 throttled-sink run after release");
        }
    }

    /**
     * C3/S1: throttled JDBC 2PC sinks (all 4 chains) — durable epochs strictly
     * advance while the throttle is engaged, then (after release) the alert table
     * converges to the exact expected set and every chain's ledger records its
     * committed epochs (A1-5).
     */
    @Test
    void s1ThrottledJdbcSinksKeepCheckpointsAdvancingThenResultsComplete() throws Exception {
        CoreInitialization.initialize();
        MiniStreamCluster cluster = new MiniStreamCluster(2,
                /* healthTimeoutMs */ 120_000L, /* killGraceMs */ 5_000L, /* pollIntervalMs */ 50L);
        Path runDir = cluster.getRunDir();
        Path eventsFile = runDir.resolve("s1-events.json");
        Path releaseMarker = runDir.resolve("throttle-release.marker");
        Files.createDirectories(runDir);

        // Same extended fixture as the S1 baseline (frank flush tail keeps the run
        // alive; expected outputs unchanged).
        java.util.List<java.util.Map<String, Object>> events =
                new java.util.ArrayList<>(ScenarioTestSupport.s1FullFixture());
        int lastTsOffset = 35_000;
        for (int i = 0; i < 45; i++) {
            lastTsOffset += 1_000;
            events.add(ReplayableCdcSourceFunction.CdcEventFixtures.spec("c",
                    ScenarioTestSupport.T0 + lastTsOffset,
                    "tx-flush-" + i, "frank", "50", "NYC", "PURCHASE"));
        }
        DistributedScenarioSupport.writeS1EventsFile(eventsFile, events);

        cluster.withCoordinatorArg("pipelineFactoryClass=" + S1ScenarioPipelineFactory.class.getName());
        cluster.withCoordinatorArg(DistributedScenarioSupport.KEY_S1_EVENTS_FILE + "=" + eventsFile);
        cluster.withCoordinatorArg(DistributedScenarioSupport.KEY_S1_EMIT_DELAY + "=200");
        cluster.withCoordinatorArg(DistributedScenarioSupport.KEY_S1_LINGER + "=800");
        cluster.withCoordinatorArg(DistributedScenarioSupport.KEY_SINK_THROTTLE_MS + "=1000");
        cluster.withCoordinatorArg(DistributedScenarioSupport.KEY_THROTTLE_RELEASE_FILE + "=" + releaseMarker);
        cluster.withCoordinatorArg(DistributedScenarioSupport.KEY_CHECKPOINT_INTERVAL + "=400");
        cluster.withCoordinatorArg(DistributedScenarioSupport.KEY_CHECKPOINT_TIMEOUT + "=30000");
        cluster.withCoordinatorArg(DistributedScenarioSupport.KEY_MAX_RETAINED + "=5");

        resetS1Tables(cluster);
        cluster.start();
        try (MiniStreamCluster c = cluster) {
            String jobId = "job-" + c.getRunId();

            // C3 ①: durable epochs strictly advance while every 2PC sink chain is
            // throttled (1s per alert row).
            long advancedEpoch = waitForDurableEpochAdvanceUnderThrottle(c, jobId,
                    () -> !Files.exists(releaseMarker), OVERALL_TIMEOUT_MS);
            assertTrue(advancedEpoch >= 0, "durable epochs must advance while the JDBC sinks are throttled");

            // C3 ②: release; the final table == FULL expected set (A1-5) and every
            // chain ledger recorded committed epochs.
            Files.writeString(releaseMarker, "released");
            waitFor(() -> sameRows(readAlertRows(c.getHarnessJdbcTemplate()), expectedAlerts()),
                    OVERALL_TIMEOUT_MS,
                    "S1 throttled-sink run must converge to the exact expected alert set after release.\nexpected="
                            + expectedAlerts() + "\nactual=" + readAlertRows(c.getHarnessJdbcTemplate())
                            + "\ncoordinator log tail: " + MultiJvmTestSupport.readLogTail(c, COORDINATOR_LABEL));
            for (String ledger : new String[]{"fraud_ledger_rapid", "fraud_ledger_unusual",
                    "fraud_ledger_geo", "fraud_ledger_takeover"}) {
                assertTrue(!readLedgerRows(c.getHarnessJdbcTemplate(), ledger).isEmpty(),
                        "ledger " + ledger + " must contain committed epochs");
            }
            assertTrue(currentDurableManifestEpoch(c, jobId) >= 0,
                    "durable manifest must exist at the end of the throttled run");
        }
    }
}

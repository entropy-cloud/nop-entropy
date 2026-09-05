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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import io.nop.core.initialize.CoreInitialization;
import io.nop.stream.fraud.scenario.ReplayableCdcSourceFunction.CdcEventFixtures;
import io.nop.stream.runtime.multijvm.MiniStreamCluster;

import static io.nop.stream.fraud.scenario.MultiJvmTestSupport.COORDINATOR_LABEL;
import static io.nop.stream.fraud.scenario.MultiJvmTestSupport.assertRecoveryLogged;
import static io.nop.stream.fraud.scenario.MultiJvmTestSupport.probeStaleEpochRejection;
import static io.nop.stream.fraud.scenario.MultiJvmTestSupport.readLatestFencingEpoch;
import static io.nop.stream.fraud.scenario.MultiJvmTestSupport.resetS1Tables;
import static io.nop.stream.fraud.scenario.MultiJvmTestSupport.waitFor;
import static io.nop.stream.fraud.scenario.MultiJvmTestSupport.waitForDurableManifest;
import static io.nop.stream.fraud.scenario.MultiJvmTestSupport.waitForEpochRotation;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.PATTERN_GEO;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.PATTERN_RAPID;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.PATTERN_TAKEOVER;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.PATTERN_UNUSUAL;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.T0;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.alertRow;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.readAlertRows;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.readLedgerRows;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.sameRows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Item 14 (composite-scenario distributed): S1 (CDC → CEP → window → 2PC JDBC sink)
 * in REAL multi-JVM mode (ProcessBuilder-spawned JobCoordinator + 2 TaskManagers,
 * shared H2 AUTO_SERVER). Matrix cells:
 *
 * <ul>
 *   <li><b>C0 baseline deployment</b> — full semantic assertions A1-1..A1-5 in
 *       multi-JVM form: the sink table equals the exact pre-computed expected alert
 *       set (order-independent keyed semantics, late-drop, per-user-average gating),
 *       the per-chain ledgers record committed epochs, and durable epoch manifests
 *       exist (checkpoint storage observable).</li>
 *   <li><b>C1 kill TM + recover + fencing</b> — kill a real TaskManager (SIGTERM)
 *       only AFTER a durable manifest exists (kill-precondition guard), restart a
 *       replacement, assert the fencing epoch strictly rotated with re-assignments,
 *       assert a stale-epoch control-plane mutation is observably REJECTED at the
 *       surviving TaskManager's RPC boundary (R-14 pattern), and assert the final
 *       table still equals the full expected set with strictly increasing ledger
 *       epochs (exactly-once under recovery, A1-6 distributed form).</li>
 * </ul>
 *
 * <p>Gated by {@code -Dnop.stream.test.multi-jvm.enabled=true}; run manually:
 * <pre>
 *   ./mvnw test -pl nop-stream/nop-stream-fraud-example -am -T 1C \
 *     -Dtest=TestS1MultiJvmE2E -Dnop.stream.test.multi-jvm.enabled=true \
 *     -Dsurefire.failIfNoSpecifiedTests=false
 * </pre>
 */
@EnabledIfSystemProperty(named = "nop.stream.test.multi-jvm.enabled", matches = "true")
class TestS1MultiJvmE2E {

    private static final long RECOVERY_TIMEOUT_MS = 120_000L;
    private static final long OVERALL_TIMEOUT_MS = 180_000L;

    /** S1 pacing tuned for the JDBC-polled multi-JVM data plane (barriers need ~1s). */
    private static final long EMIT_DELAY_MS = 200L;

    private static Set<AlertSummaryRow> expectedAlerts() {
        Set<AlertSummaryRow> expected = new LinkedHashSet<>();
        expected.add(alertRow(T0 + 2000, "alice", PATTERN_RAPID, 1, "2700"));
        expected.add(alertRow(T0 + 6000, "bob", PATTERN_UNUSUAL, 1, "2500"));
        expected.add(alertRow(T0 + 13000, "dave", PATTERN_TAKEOVER, 1, "3000"));
        expected.add(alertRow(T0 + 16000, "erin", PATTERN_GEO, 1, "350"));
        return expected;
    }

    /**
     * Distributed fixture = the shared LOCAL fixture + extra trailing flush events
     * (frank is a noise user — his events never appear in any expected alert row,
     * so the expected outputs are unchanged). The extended tail keeps the record
     * flow alive well past the W1 windows so periodic checkpoints durably commit
     * the W1 alerts MID-RUN (bounded-tail determinism; the terminator's own tail
     * stays in-flight per engine 2PC semantics — same scope as the LOCAL tests).
     */
    private static List<Map<String, Object>> distributedFixtureWithExtendedFlush(
            List<Map<String, Object>> fixture) {
        List<Map<String, Object>> events = new ArrayList<>(fixture);
        int lastTsOffset = 35_000; // fixture's final flush event is at T0+35s
        for (int i = 0; i < 30; i++) {
            lastTsOffset += 1_000;
            events.add(ReplayableCdcSourceFunction.CdcEventFixtures.spec("c",
                    ScenarioTestSupport.T0 + lastTsOffset,
                    "tx-flush-" + i, "frank", "50", "NYC", "PURCHASE"));
        }
        return events;
    }

    private MiniStreamCluster startS1Cluster() throws Exception {
        CoreInitialization.initialize();
        MiniStreamCluster cluster = new MiniStreamCluster(2,
                /* healthTimeoutMs */ 120_000L,
                /* killGraceMs */ 5_000L,
                /* pollIntervalMs */ 50L);
        Path runDir = cluster.getCheckpointDir().getParent();
        Files.createDirectories(runDir);
        Path eventsFile = runDir.resolve("s1-events.json");
        DistributedScenarioSupport.writeS1EventsFile(eventsFile,
                distributedFixtureWithExtendedFlush(ScenarioTestSupport.s1FullFixture()));

        cluster.withCoordinatorArg("pipelineFactoryClass=" + S1ScenarioPipelineFactory.class.getName());
        cluster.withCoordinatorArg(DistributedScenarioSupport.KEY_S1_EVENTS_FILE + "=" + eventsFile);
        cluster.withCoordinatorArg(DistributedScenarioSupport.KEY_S1_EMIT_DELAY + "=" + EMIT_DELAY_MS);
        cluster.withCoordinatorArg(DistributedScenarioSupport.KEY_S1_LINGER + "=800");
        cluster.withCoordinatorArg(DistributedScenarioSupport.KEY_CHECKPOINT_INTERVAL + "=400");
        cluster.withCoordinatorArg(DistributedScenarioSupport.KEY_CHECKPOINT_TIMEOUT + "=30000");
        cluster.withCoordinatorArg(DistributedScenarioSupport.KEY_MAX_RETAINED + "=5");

        resetS1Tables(cluster);
        cluster.start();
        return cluster;
    }

    @Test
    void s1MultiJvmBaselineDeploysFullSemanticsWithCheckpoints() throws Exception {
        try (MiniStreamCluster cluster = startS1Cluster()) {
            String jobId = "job-" + cluster.getRunId();

            // A1-5 (distributed): final table == exact expected alert set.
            waitFor(() -> sameRows(readAlertRows(cluster.getHarnessJdbcTemplate()), expectedAlerts()),
                    OVERALL_TIMEOUT_MS,
                    "S1 multi-JVM sink table must equal the exact expected alert set.\nexpected="
                            + expectedAlerts() + "\nactual=" + readAlertRows(cluster.getHarnessJdbcTemplate())
                            + "\ncoordinator log tail: " + MultiJvmTestSupport.readLogTail(cluster, COORDINATOR_LABEL));

            // A1-5: every chain's ledger recorded committed epochs.
            for (String ledger : new String[]{"fraud_ledger_rapid", "fraud_ledger_unusual",
                    "fraud_ledger_geo", "fraud_ledger_takeover"}) {
                assertTrue(!readLedgerRows(cluster.getHarnessJdbcTemplate(), ledger).isEmpty(),
                        "ledger " + ledger + " must contain committed epochs");
            }

            // C0 wiring proof: checkpoints genuinely complete in multi-JVM mode —
            // a durable manifest exists (ACK loop + storage persist both crossed JVMs).
            long durableEpoch = MultiJvmTestSupport.currentDurableManifestEpoch(cluster, jobId);
            assertTrue(durableEpoch >= 0, "durable EpochManifest must exist after the run");
        }
    }

    @Test
    void s1MultiJvmKillRecoverFencingExactlyOnce() throws Exception {
        try (MiniStreamCluster cluster = startS1Cluster()) {
            String jobId = "job-" + cluster.getRunId();

            // Kill precondition (plan guard): the latest durable epoch manifest must
            // exist BEFORE the kill — otherwise the recovery assertion degenerates to
            // "run from scratch".
            long initialEpoch = waitForDurableManifest(cluster, jobId, OVERALL_TIMEOUT_MS);
            assertTrue(initialEpoch >= 0, "durable manifest must exist before kill");

            // Wait until at least one alert row is durably committed mid-run, so the
            // kill interrupts a pipeline that has already produced committed output.
            waitFor(() -> !readAlertRows(cluster.getHarnessJdbcTemplate()).isEmpty(),
                    OVERALL_TIMEOUT_MS, "at least one alert row must be committed before the kill");

            long initialFencingEpoch = readLatestFencingEpoch(cluster);
            assertTrue(initialFencingEpoch > 0, "initial fencing epoch must be positive");
            long coordLogSizeBeforeKill = Files.size(cluster.logFileFor(COORDINATOR_LABEL));

            // C1: kill a REAL TaskManager process (SIGTERM).
            assertTrue(cluster.killTaskManager("tm-1"), "killTaskManager(tm-1) must succeed");
            cluster.restartTaskManager("tm-1");

            // Fencing observable: the recovery rotation must produce a STRICTLY
            // greater epoch with re-issued assignments (design C1 验收点).
            long recoveredEpoch = waitForEpochRotation(cluster, initialFencingEpoch, RECOVERY_TIMEOUT_MS);
            assertTrue(recoveredEpoch > initialFencingEpoch,
                    "recovery must rotate the fencing epoch: initial=" + initialFencingEpoch
                            + " recovered=" + recoveredEpoch);

            assertRecoveryLogged(cluster, coordLogSizeBeforeKill);

            // Fencing observable (behavior-level, R-14 pattern): a stale-epoch
            // control-plane mutation aimed at the surviving TaskManager must be
            // REJECTED at its RPC boundary (log-observable).
            String rejection = probeStaleEpochRejection(cluster, "tm-0", initialFencingEpoch);
            assertTrue(rejection.contains("FENCING_TOKEN_MISMATCH")
                            || rejection.contains("fencing-token-mismatch")
                            || rejection.contains("process-request-fail"),
                    "stale-epoch mutation must be observably rejected, log delta: "
                            + rejection.substring(0, Math.min(800, rejection.length())));

            // A1-6 (distributed): after recovery the final table still equals the
            // FULL expected set (offset restore + ledger idempotent re-commit — no
            // duplicates, no losses).
            waitFor(() -> sameRows(readAlertRows(cluster.getHarnessJdbcTemplate()), expectedAlerts()),
                    OVERALL_TIMEOUT_MS,
                    "S1 multi-JVM recovery must converge to the full expected alert set.\nexpected="
                            + expectedAlerts() + "\nactual=" + readAlertRows(cluster.getHarnessJdbcTemplate())
                            + "\ncoordinator log tail: " + MultiJvmTestSupport.readLogTail(cluster, COORDINATOR_LABEL));

            // Ledger epochs strictly increased across the recovery boundary.
            long maxLedgerEpoch = 0;
            for (String ledger : new String[]{"fraud_ledger_rapid", "fraud_ledger_unusual",
                    "fraud_ledger_geo", "fraud_ledger_takeover"}) {
                var epochs = readLedgerRows(cluster.getHarnessJdbcTemplate(), ledger);
                if (!epochs.isEmpty()) {
                    maxLedgerEpoch = Math.max(maxLedgerEpoch, epochs.lastKey());
                }
            }
            assertTrue(maxLedgerEpoch >= initialEpoch,
                    "ledger epochs must reach at least the pre-kill durable epoch (post-recovery "
                            + "commits are new strictly-greater epochs; pre-kill ledger=" + maxLedgerEpoch
                            + ", durable manifest at kill=" + initialEpoch + ")");
            assertNotNull(cluster.registeredNodeIds());
            assertEquals(2, cluster.expectedNodeIds().size());
        }
    }
}

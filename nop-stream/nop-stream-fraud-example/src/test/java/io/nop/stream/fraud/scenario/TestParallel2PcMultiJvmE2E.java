/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.fraud.scenario;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import io.nop.core.initialize.CoreInitialization;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.stream.runtime.multijvm.MiniStreamCluster;

import static io.nop.stream.fraud.scenario.MultiJvmTestSupport.COORDINATOR_LABEL;
import static io.nop.stream.fraud.scenario.MultiJvmTestSupport.readLatestFencingEpoch;
import static io.nop.stream.fraud.scenario.MultiJvmTestSupport.waitFor;
import static io.nop.stream.fraud.scenario.MultiJvmTestSupport.waitForDurableManifest;
import static io.nop.stream.fraud.scenario.MultiJvmTestSupport.waitForEpochRotation;
import static io.nop.stream.fraud.scenario.ParallelTwoPhaseCommitScenarios.DATA_TABLE;
import static io.nop.stream.fraud.scenario.ParallelTwoPhaseCommitScenarios.LEDGER_TABLE;
import static io.nop.stream.fraud.scenario.ParallelTwoPhaseCommitScenarios.TOTAL_KEYS;
import static io.nop.stream.fraud.scenario.ParallelTwoPhaseCommitScenarios.createDataTable;
import static io.nop.stream.fraud.scenario.ParallelTwoPhaseCommitScenarios.createLedgerTable;
import static io.nop.stream.fraud.scenario.ParallelTwoPhaseCommitScenarios.expectedValueCounts;
import static io.nop.stream.fraud.scenario.ParallelTwoPhaseCommitScenarios.hasEpochWithMultipleSubtasks;
import static io.nop.stream.fraud.scenario.ParallelTwoPhaseCommitScenarios.readValueCounts;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.execute;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CONN-01 successor (roadmap item 35) — <strong>real multi-JVM proof</strong> of
 * the parallel 2PC sink: the keyed-P&gt;1 + {@code JdbcTwoPhaseCommitSink} P=2
 * scenario ({@code fraud-parallel-2pc-jdbc.stream.xml}) deployed via
 * {@code MiniStreamCluster} on 2 real TaskManager JVMs against the shared cluster
 * H2 (AUTO_SERVER), with a REAL TaskManager kill (SIGTERM) mid-run.
 *
 * <p><b>Coverage matrix (sink family × execution form, plan Phase 3)</b>:
 * <table border="1">
 *   <tr><th>sink family</th><th>LOCAL</th><th>real multi-JVM</th></tr>
 *   <tr><td>JDBC 2PC (jdbc-2pc)</td><td>{@code TestParallel2PcJdbcE2E} (incl. restore + D1 rejection)</td>
 *       <td><b>this test</b> (kill TM → recover → fencing → exactly-once)</td></tr>
 *   <tr><td>File 2PC (file)</td><td>{@code TestParallel2PcFileE2E} (incl. restore)</td>
 *       <td>LOCAL-only (explicitly marked): a multi-JVM file sink would need a shared
 *           output-dir filesystem contract, out of this plan's scope; the JDBC family
 *           carries the multi-JVM obligation (per-subtask ledger key works across JVMs
 *           because the ledger lives in the shared DB).</td></tr>
 * </table>
 * Family-selection rationale: the JDBC family reuses the S1 pattern (MiniStreamCluster
 * H2 AUTO_SERVER shared ledger/data tables carry the cross-JVM 2PC evidence) at the
 * lowest cost; the file family adds no new cross-JVM mechanism (its per-subtask
 * isolation is filesystem-local, fully provable in LOCAL form).
 *
 * <p>Acceptance (kill TM → 恢复 → fencing 严格递增 → exactly-once):
 * <ol>
 *   <li>kill precondition: a durable epoch manifest + committed output rows exist
 *       BEFORE the kill (the recovery must not degenerate to a from-scratch run);</li>
 *   <li>after killing tm-1 and starting a replacement, the fencing epoch strictly
 *       increases with re-assignments (recovery redeploy under a new generation);</li>
 *   <li>the final data table equals the full expected multiset (exactly-once under
 *       real multi-JVM recovery: cursor restore + ledger idempotent guard);</li>
 *   <li>the shared ledger contains an epoch committed by 2 DISTINCT subtask ids —
 *       the per-subtask commit key (epoch_id, subtask_id) working across real JVM
 *       boundaries (each TM's sink subtask copy commits its own batch).</li>
 * </ol>
 *
 * <p>Gated by {@code -Dnop.stream.test.multi-jvm.enabled=true}; run manually:
 * <pre>
 *   ./mvnw test -pl nop-stream/nop-stream-fraud-example -am -T 1C \
 *     -Dtest=TestParallel2PcMultiJvmE2E -Dnop.stream.test.multi-jvm.enabled=true \
 *     -Dsurefire.failIfNoSpecifiedTests=false
 * </pre>
 */
@EnabledIfSystemProperty(named = "nop.stream.test.multi-jvm.enabled", matches = "true")
class TestParallel2PcMultiJvmE2E {

    private static final long RECOVERY_TIMEOUT_MS = 120_000L;
    private static final long OVERALL_TIMEOUT_MS = 180_000L;

    @Test
    void parallel2PcJdbcKillTmRecoversExactlyOnceWithPerSubtaskLedger() throws Exception {
        CoreInitialization.initialize();

        try (MiniStreamCluster cluster = new MiniStreamCluster(2,
                /* healthTimeoutMs */ 120_000L, /* killGraceMs */ 5_000L, /* pollIntervalMs */ 50L)) {
            Path runDir = cluster.getCheckpointDir().getParent();
            Files.createDirectories(runDir);
            Path inputDir = runDir.resolve("p2pc-input");
            Files.createDirectories(inputDir);
            StringBuilder lines = new StringBuilder();
            for (int i = 0; i < TOTAL_KEYS; i++) {
                lines.append("key-").append(i).append('\n');
            }
            Files.write(inputDir.resolve("part-1.txt"), lines.toString().getBytes());

            cluster.withCoordinatorArg(
                    "pipelineFactoryClass=" + Parallel2PcScenarioPipelineFactory.class.getName());
            cluster.withCoordinatorArg(Parallel2PcScenarioPipelineFactory.KEY_INPUT_DIR + "=" + inputDir);
            cluster.withCoordinatorArg(Parallel2PcScenarioPipelineFactory.KEY_LINE_DELAY + "=250");
            cluster.withCoordinatorArg(Parallel2PcScenarioPipelineFactory.KEY_LINGER + "=800");
            cluster.withCoordinatorArg(DistributedScenarioSupport.KEY_CHECKPOINT_INTERVAL + "=400");
            cluster.withCoordinatorArg(DistributedScenarioSupport.KEY_CHECKPOINT_TIMEOUT + "=30000");
            cluster.withCoordinatorArg(DistributedScenarioSupport.KEY_MAX_RETAINED + "=5");

            // Shared-table preparation BEFORE start(): data table (no PK — duplicates
            // must be observable) + ledger (the sink's own composite-PK DDL).
            IJdbcTemplate shared = MultiJvmTestSupport.openSharedJdbc(cluster);
            execute(shared, "DROP TABLE IF EXISTS " + DATA_TABLE);
            execute(shared, "DROP TABLE IF EXISTS " + LEDGER_TABLE);
            createDataTable(shared);
            createLedgerTable(shared);

            cluster.start();
            String jobId = "job-" + cluster.getRunId();

            // (1) kill precondition: durable manifest + committed rows BEFORE the kill.
            long initialEpoch = waitForDurableManifest(cluster, jobId, OVERALL_TIMEOUT_MS);
            assertTrue(initialEpoch >= 0, "durable manifest must exist before the kill");
            waitFor(() -> !readValueCounts(shared).isEmpty(),
                    OVERALL_TIMEOUT_MS, "rows must be committed before the kill");

            long initialFencingEpoch = readLatestFencingEpoch(cluster);
            assertTrue(initialFencingEpoch > 0, "initial fencing epoch must be positive");

            // (2) REAL kill: tm-1 (OS-level SIGTERM), then a replacement TM. Recovery
            // restores the P=2 sink at the SAME parallelism (the supported path —
            // cross-parallelism restore is rejected per D1, §8.5.2).
            assertTrue(cluster.killTaskManager("tm-1"), "killTaskManager(tm-1) must succeed");
            cluster.restartTaskManager("tm-1");

            long recoveredEpoch = waitForEpochRotation(cluster, initialFencingEpoch, RECOVERY_TIMEOUT_MS);
            assertTrue(recoveredEpoch > initialFencingEpoch,
                    "recovery must rotate the fencing epoch strictly upward: initial="
                            + initialFencingEpoch + " recovered=" + recoveredEpoch);

            // (3) exactly-once convergence after the recovery: full expected multiset.
            waitFor(() -> expectedValueCounts().equals(readValueCounts(shared)),
                    OVERALL_TIMEOUT_MS,
                    "parallel-2PC multi-JVM recovery must converge to the full expected "
                            + "multiset.\nexpected=" + expectedValueCounts().size() + " keys, actual="
                            + readValueCounts(shared) + "\ncoordinator log tail: "
                            + MultiJvmTestSupport.readLogTail(cluster, COORDINATOR_LABEL));

            // (4) per-subtask commit key across JVMs: some epoch committed by 2
            // distinct subtask ids in the ONE shared ledger.
            assertTrue(hasEpochWithMultipleSubtasks(shared),
                    "shared ledger must contain an epoch committed by 2 distinct subtask "
                            + "ids (per-subtask commit key across real JVM boundaries)");
            assertEquals(2, cluster.expectedNodeIds().size());
        }
    }
}

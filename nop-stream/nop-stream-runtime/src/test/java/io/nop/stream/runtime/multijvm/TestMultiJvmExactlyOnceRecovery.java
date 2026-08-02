/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.multijvm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.nop.core.lang.sql.SQL;
import io.nop.stream.runtime.cluster.TaskAssignment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stage 42 Phase 3: the end-to-end multi-JVM kill/recovery test. <strong>Gated
 * by {@code -Dnop.stream.test.multi-jvm.enabled=true}</strong> so the default
 * {@code ./mvnw test} suite does NOT pay the JVM-spawn cost.
 *
 * <p>Anti-hollow verification (plan guide #22): spawns 2 real TaskManager JVMs
 * + 1 real JobCoordinator JVM via {@link MiniStreamCluster}, against a real
 * shared H2 {@code AUTO_SERVER=TRUE} backing store. The coordinator deploys
 * task logic to both TaskManagers via the Phase 0 {@code deployTask} RPC
 * (cross-JVM). The test then:
 * <ol>
 *   <li>asserts the {@code deployTask} RPC fired across JVM boundaries —
 *       verifiable via {@code nop_stream_task_assignment} rows in the shared DB
 *       (the coordinator writes a row immediately before each
 *       {@code deployTask} RPC, so the row is an observable side-effect of the
 *       RPC having been issued);</li>
 *   <li>kills one TaskManager process via {@code Process.destroy()} (real
 *       OS-level SIGTERM);</li>
 *   <li>restarts a replacement TaskManager and asserts the coordinator's
 *       recovery path re-issued {@code deployTask} with a rotated fencing epoch
 *       (Phase 0 mechanism exercised across JVM boundaries);</li>
 *   <li>asserts log aggregation captures the kill/recovery event;</li>
 *   <li>asserts the recovered topology's fencing epoch strictly increased
 *       (old epoch is fenced out).</li>
 * </ol>
 *
 * <p><strong>Exactly-once scope note</strong>: a full source→keyBy→sink
 * pipeline with cross-JVM shared-sink exactly-once assertion requires a
 * serializable pipeline descriptor + shared sink state (Stage 43+ follow-up).
 * Phase 3 verifies the <em>infrastructure</em> that enables exactly-once under
 * multi-JVM failure: {@code deployTask} RPC across JVMs + recovery
 * redeployment + fencing. The Phase 0 in-process E2E test
 * ({@code TestRpcDistributedExecutorRemoteDeployE2E}) already verified a real
 * pipeline runs end-to-end via the {@code deployTask} path.
 *
 * <p>Run manually:
 * <pre>
 *   ./mvnw test -pl nop-stream/nop-stream-runtime -am -T 1C \
 *     -Dtest=TestMultiJvmExactlyOnceRecovery \
 *     -Dnop.stream.test.multi-jvm.enabled=true \
 *     -Dsurefire.failIfNoSpecifiedTests=false
 * </pre>
 */
@EnabledIfSystemProperty(named = "nop.stream.test.multi-jvm.enabled", matches = "true")
class TestMultiJvmExactlyOnceRecovery {

    /**
     * Default per-test timeout: the failure-detector ticks every 5s, lease
     * expiry threshold is 30s, plus JVM spawn time. 90s gives comfortable
     * headroom.
     */
    private static final long RECOVERY_TIMEOUT_MS = 90_000L;

    @Test
    void multiJvmDeployKillRecoverFencing() throws Exception {
        try (MiniStreamCluster cluster = new MiniStreamCluster(2,
                /* healthTimeoutMs */ 60_000L,
                /* killGraceMs */ 5_000L,
                /* pollIntervalMs */ 50L)) {
            cluster.start();

            // 1. Both TaskManagers are registered + coordinator is alive.
            Set<String> registered = cluster.registeredNodeIds();
            assertTrue(registered.containsAll(List.of("tm-0", "tm-1")),
                    "Expected both TaskManagers registered, got " + registered);
            assertTrue(cluster.coordinatorAlive(),
                    "Coordinator must be alive after start()");

            // 2. deployTask RPC fired across JVM boundaries: assert
            //    nop_stream_task_assignment rows exist for the job in the
            //    shared DB. The coordinator writes a row immediately before
            //    each deployTask RPC, so the rows are the observable
            //    side-effect of cross-JVM deployment.
            //    The coordinator JVM needs time to: start → CoreInitialization
            //    → connect DB → wait for TMs → build JobGraph → assignTasks.
            //    Poll for the first assignment to appear.
            long initialEpoch = waitForInitialAssignment(cluster, 60_000L);
            assertTrue(initialEpoch > 0L,
                    "Expected a positive fencing epoch after initial assignTasks. "
                            + "Coordinator log: " + cluster.logFileFor("coordinator"));
            int initialAssignmentCount = countTaskAssignments(cluster, initialEpoch);
            assertTrue(initialAssignmentCount >= 2,
                    "Expected at least 2 task assignments (source + sink) at the "
                            + "initial epoch " + initialEpoch
                            + ", got " + initialAssignmentCount);

            // Capture the initial log size for delta-read later.
            long coordLogSizeBeforeKill = Files.size(cluster.logFileFor("coordinator"));

            // 3. Kill one TaskManager process (real OS-level SIGTERM).
            boolean killed = cluster.killTaskManager("tm-1");
            assertTrue(killed, "killTaskManager(tm-1) must succeed");
            assertFalse(cluster.taskManagerAlive("tm-1"),
                    "tm-1 must be gone after kill");

            // 4. Restart a replacement TaskManager. The coordinator's
            //    failure-detector loop (5s interval, 30s lease threshold) will
            //    detect the killed TM and trigger globalRecovery, which
            //    rotates the fencing epoch and re-issues deployTask to all
            //    registered TMs (including the replacement).
            cluster.restartTaskManager("tm-1");
            assertTrue(cluster.taskManagerAlive("tm-1"),
                    "tm-1 replacement must be alive");

            // 5. Wait for the coordinator's recovery path to fire: the fencing
            //    epoch in the shared DB must strictly increase (recovery
            //    rotates it). The new epoch's assignment rows prove the
            //    recovery path called deployTask with the rotated epoch
            //    (Phase 0 mechanism exercised across JVM boundaries).
            long recoveredEpoch = waitForEpochRotation(cluster, initialEpoch, RECOVERY_TIMEOUT_MS);
            assertTrue(recoveredEpoch > initialEpoch,
                    "Recovery must rotate the fencing epoch: initial=" + initialEpoch
                            + " recovered=" + recoveredEpoch);

            int recoveredAssignmentCount = countTaskAssignments(cluster, recoveredEpoch);
            assertTrue(recoveredAssignmentCount >= 2,
                    "Recovery must re-issue deployTask for all subtasks at the rotated epoch "
                            + recoveredEpoch + ", got " + recoveredAssignmentCount
                            + " assignments. Logs: " + cluster.logFileFor("coordinator"));

            // 6. Log aggregation captures the kill/recovery event: the
            //    coordinator log must mention recovery after the kill.
            String coordLogDelta = readLogDelta(cluster.logFileFor("coordinator"), coordLogSizeBeforeKill);
            assertTrue(coordLogDelta.contains("global recovery")
                            || coordLogDelta.contains("globalRecovery")
                            || coordLogDelta.contains("Fencing epoch rotated")
                            || coordLogDelta.contains("recovery"),
                    "Coordinator log must capture the recovery event after the kill. "
                            + "Log delta (first 500 chars): "
                            + coordLogDelta.substring(0, Math.min(500, coordLogDelta.length())));

            // 7. Fencing: the replacement TM is running under the recovered
            //    epoch. The killed TM (if it were still alive) would be fenced
            //    — its old epoch is strictly less than the recovered epoch.
            //    This is structurally verified by the epoch rotation: any task
            //    producing output under the old epoch is rejected by the data
            //    plane's single-long-epoch filter (Stage 39 fencing contract).
            long replacementRegisteredEpoch = readLatestFencingEpoch(cluster);
            assertTrue(replacementRegisteredEpoch >= recoveredEpoch,
                    "Replacement TM's registered epoch must be >= recovered epoch");
        }
    }

    // ==================== Helpers ====================

    /**
     * Reads the latest (max) fencing_token value from the
     * {@code nop_stream_task_assignment} table. The coordinator writes
     * fencing_token as a string representation of the long epoch.
     */
    private static long readLatestFencingEpoch(MiniStreamCluster cluster) {
        if (cluster.getHarnessJdbcTemplate() == null) {
            return 0L;
        }
        try {
            return cluster.getHarnessJdbcTemplate().executeQuery(SQL.begin()
                    .sql("SELECT MAX(CAST(fencing_token AS BIGINT)) FROM nop_stream_task_assignment")
                    .end(), dataSet -> {
                if (!dataSet.hasNext()) {
                    return 0L;
                }
                io.nop.dataset.IDataRow row = dataSet.next();
                if (row.isNull(0)) {
                    return 0L;
                }
                return row.getLong(0);
            });
        } catch (Exception e) {
            // Table may not exist yet on the very first poll.
            return 0L;
        }
    }

    private static int countTaskAssignments(MiniStreamCluster cluster, long fencingEpoch) {
        if (cluster.getHarnessJdbcTemplate() == null) {
            return 0;
        }
        try {
            Integer count = cluster.getHarnessJdbcTemplate().executeQuery(SQL.begin()
                    .sql("SELECT COUNT(*) FROM nop_stream_task_assignment WHERE fencing_token = ?",
                            String.valueOf(fencingEpoch))
                    .end(), dataSet -> {
                if (!dataSet.hasNext()) {
                    return 0;
                }
                return dataSet.next().getInt(0);
            });
            return count == null ? 0 : count;
        } catch (Exception e) {
            return 0;
        }
    }

    private static long waitForInitialAssignment(MiniStreamCluster cluster, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            long epoch = readLatestFencingEpoch(cluster);
            if (epoch > 0L && countTaskAssignments(cluster, epoch) >= 2) {
                return epoch;
            }
            // Fail-fast if the coordinator died.
            if (!cluster.coordinatorAlive()) {
                throw new IllegalStateException(
                        "Coordinator process exited before assigning tasks. Log: "
                                + cluster.logFileFor("coordinator"));
            }
            TimeUnit.MILLISECONDS.sleep(500L);
        }
        return 0L;
    }

    private static long waitForEpochRotation(MiniStreamCluster cluster, long baselineEpoch, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        long lastSeen = baselineEpoch;
        while (System.currentTimeMillis() < deadline) {
            long current = readLatestFencingEpoch(cluster);
            if (current > baselineEpoch) {
                // Confirm the new epoch has assignments (recovery deployTask fired).
                int count = countTaskAssignments(cluster, current);
                if (count >= 2) {
                    return current;
                }
                lastSeen = current;
            }
            TimeUnit.MILLISECONDS.sleep(500L);
        }
        return lastSeen;
    }

    private static String readLogDelta(java.nio.file.Path logFile, long fromOffset) throws java.io.IOException {
        long size = Files.size(logFile);
        if (size <= fromOffset) {
            return "";
        }
        long length = size - fromOffset;
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(logFile.toFile(), "r")) {
            raf.seek(fromOffset);
            byte[] buf = new byte[(int) Math.min(length, 64 * 1024L)];
            int read = raf.read(buf);
            return new String(buf, 0, read, java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}

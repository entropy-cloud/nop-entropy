/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.multijvm;

import io.nop.core.lang.sql.SQL;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stage 46 Phase 4 — multi-JVM coordinator HA failover proof.
 *
 * <p>Spawns two real coordinator JVMs sharing one JDBC lease table (via
 * {@link MiniStreamCluster}), kills the active leader, and asserts that the standby
 * takes over with a rotated epoch. This is the true end-to-end verification of the
 * G32 failover-safe rebuild (Phase 1): the new coordinator JVM rebuilds its checkpoint
 * view from durable storage on leadership grant.
 *
 * <p>Gated by {@code -Dnop.stream.test.multi-jvm.enabled=true} (does not run by default;
 * Stage 42 precedent). Spawning real JVMs is slow and environment-sensitive, so it is
 * opt-in. The default suite's HA coverage comes from {@code TestJobCoordinatorFailoverRestore}
 * and {@code TestJobCoordinatorJdbcHaIntegration} (in-process).
 */
@EnabledIfSystemProperty(named = "nop.stream.test.multi-jvm.enabled", matches = "true")
class TestMultiJvmCoordinatorFailover {

    private static final long POLL_MS = 500L;
    private static final long FAILOVER_TIMEOUT_MS = 30_000L;

    /**
     * Core scenario: kill the active coordinator, assert the standby takes over.
     *
     * <ol>
     *   <li>Start the cluster in HA mode: TMs + coordinator-0.</li>
     *   <li>Spawn coordinator-1 (standby, sharing the lease table).</li>
     *   <li>Wait for coordinator-0 to win leadership and issue assignments.</li>
     *   <li>Kill coordinator-0 (SIGTERM).</li>
     *   <li>Wait for coordinator-1 to take over (lease expires → epoch rotates).</li>
     *   <li>Assert: coordinator-0 is dead, coordinator-1 is alive, the lease row's
     *       leader_id flipped to coordinator-1 with a strictly greater epoch.</li>
     * </ol>
     */
    @Test
    void testCoordinatorKillTriggersStandbyTakeover() throws Exception {
        try (MiniStreamCluster cluster = new MiniStreamCluster(2,
                60_000L, 10_000L, 100L)) {
            cluster.start(true); // HA mode: coordinator-0 runs leader-gated.

            // Wait for coordinator-0 to win leadership BEFORE spawning the standby.
            // Without this ordering, coordinator-1 can win the initial INSERT race
            // (both JVMs start near-simultaneously), making the test non-deterministic.
            long initialEpoch = waitForLeaderAndAssignments(cluster, 0);
            assertTrue(initialEpoch > 0L, "coordinator-0 must win leadership and assign tasks");

            // Spawn a second coordinator (standby) sharing the same lease table.
            cluster.spawnJobCoordinator(1);

            // Confirm the lease row currently names coordinator-0 as leader.
            LeaseRow row0 = readLeaseRow(cluster);
            assertNotNull(row0);
            assertTrue(row0.leaderId.contains("coordinator-0"),
                    "coordinator-0 must hold the lease initially (got " + row0.leaderId + ")");

            // Kill coordinator-0.
            assertTrue(cluster.killCoordinator(0), "must kill coordinator-0");
            assertFalse(cluster.coordinatorAlive(0), "coordinator-0 must be dead after kill");

            // Wait for coordinator-1 to take over: lease leader flips + epoch increases.
            long takeoverDeadline = System.currentTimeMillis() + FAILOVER_TIMEOUT_MS;
            boolean tookOver = false;
            while (System.currentTimeMillis() < takeoverDeadline) {
                if (!cluster.coordinatorAlive(1)) {
                    throw new IllegalStateException("coordinator-1 died during failover. Log: "
                            + cluster.logFileFor("coordinator-1"));
                }
                LeaseRow row = readLeaseRow(cluster);
                if (row != null && row.leaderId.contains("coordinator-1") && row.leaderEpoch > row0.leaderEpoch) {
                    tookOver = true;
                    break;
                }
                TimeUnit.MILLISECONDS.sleep(POLL_MS);
            }
            assertTrue(tookOver,
                    "coordinator-1 must take over after coordinator-0 is killed (lease flip + epoch rotation)");

            // Coordinator-1 is alive and is now the leader.
            assertTrue(cluster.coordinatorAlive(1), "coordinator-1 must remain alive after takeover");
        }
    }

    /**
     * Brain-split fencing boundary: both coordinators share the lease table; only the
     * lease holder can commit. This test verifies that after takeover, the old leader's
     * epoch is strictly less than the new leader's (so any in-flight control messages
     * from the old leader are rejected by the data-plane fencing filter — invariant #8).
     */
    @Test
    void testBrainSplitFencingBoundary() throws Exception {
        try (MiniStreamCluster cluster = new MiniStreamCluster(1,
                60_000L, 10_000L, 100L)) {
            cluster.start(true);

            // Wait for coordinator-0 to win leadership BEFORE spawning the standby.
            // Without this ordering, coordinator-1 can win the initial INSERT race,
            // making the rest of the test meaningless (coordinator-0 would be the
            // standby, and killing it would not trigger any takeover).
            long epoch0 = waitForLeaderAndAssignments(cluster, 0);
            assertTrue(epoch0 > 0L, "coordinator-0 must win leadership before standby is spawned");

            cluster.spawnJobCoordinator(1);

            LeaseRow row0 = readLeaseRow(cluster);
            assertNotNull(row0);

            cluster.killCoordinator(0);

            // Wait for coordinator-1 takeover.
            long takeoverDeadline = System.currentTimeMillis() + FAILOVER_TIMEOUT_MS;
            long epoch1 = -1L;
            while (System.currentTimeMillis() < takeoverDeadline) {
                LeaseRow row = readLeaseRow(cluster);
                if (row != null && row.leaderId.contains("coordinator-1") && row.leaderEpoch > row0.leaderEpoch) {
                    epoch1 = row.leaderEpoch;
                    break;
                }
                TimeUnit.MILLISECONDS.sleep(POLL_MS);
            }
            assertTrue(epoch1 > 0L, "coordinator-1 must take over");
            assertTrue(epoch1 > row0.leaderEpoch,
                    "new leader epoch (" + epoch1 + ") must be strictly greater than old ("
                            + row0.leaderEpoch + ") — fencing invariant #8");
        }
    }

    // ==================== Helpers ====================

    /**
     * R-14 (runtime audit 2026-09-01, distributed-path mandatory verification):
     * a zombie coordinator's epoch-rollback attempt must be rejected at the REAL
     * cross-JVM RPC boundary. The test JVM plays the zombie: it builds its own
     * control-plane RPC proxy to a spawned TaskManager JVM and sends
     * {@code updateFencingToken(currentEpoch - 1)} — before the fix this rolled
     * the TM's epoch backward (canceling the active generation and mismatch-
     * rejecting every subsequent active-epoch call); after the fix the rollback
     * is rejected and a subsequent legitimate update proves the epoch never moved.
     */
    @Test
    void testZombieCoordinatorEpochRollbackRejectedAtRpcBoundary() throws Exception {
        try (MiniStreamCluster cluster = new MiniStreamCluster(1,
                60_000L, 10_000L, 100L)) {
            cluster.start();

            String nodeId = cluster.expectedNodeIds().get(0);

            // Wait until the coordinator has assigned tasks — the assignment rows
            // then carry the fencing epoch the TMs are running under.
            long currentEpoch = waitForAssignmentEpoch(cluster);
            assertTrue(currentEpoch > 0L, "assignments must exist before the zombie attempt");

            // Harness-side "zombie coordinator": same shared JDBC transport, own
            // RPC proxy to the TM's task topic.
            io.nop.stream.runtime.launch.ClusterLaunchConfig cfg = io.nop.stream.runtime.launch.ClusterLaunchConfig
                    .parse(new String[]{"jdbcUrl=" + cluster.getJdbcUrl()});
            try (io.nop.stream.runtime.launch.SharedJdbcInfrastructure zombieJdbc =
                         new io.nop.stream.runtime.launch.SharedJdbcInfrastructure(cfg);
                 io.nop.stream.runtime.launch.PollingJdbcMessageService zombieMs =
                         new io.nop.stream.runtime.launch.PollingJdbcMessageService(
                                 zombieJdbc.getJdbcTemplate(), 100L)) {
                zombieMs.initialize();

                String taskTopic = io.nop.stream.runtime.launch.TaskManagerMain
                        .taskRpcTopic(cluster.getTopicNamespace(), nodeId);
                io.nop.stream.runtime.rpc.StreamControlRpcProxyFactory proxy =
                        new io.nop.stream.runtime.rpc.StreamControlRpcProxyFactory(
                                "zombie@" + nodeId,
                                io.nop.stream.runtime.rpc.IStreamTaskRpcService.class,
                                zombieMs, taskTopic);
                proxy.start();
                try {
                    io.nop.stream.runtime.rpc.IStreamTaskRpcService taskRpc = proxy.getProxy();

                    // The zombie rollback: one-way fire-and-forget, so the assertion
                    // is on the observable side effects in the TM's log.
                    taskRpc.updateFencingToken(currentEpoch - 1);

                    // Give the TM's poller time to process the zombie message.
                    TimeUnit.MILLISECONDS.sleep(2_000L);
                    String log = java.nio.file.Files.readString(cluster.logFileFor(nodeId));
                    assertFalse(log.contains("Fencing epoch updated from " + currentEpoch + " to "
                                    + (currentEpoch - 1)),
                            "the TM must NOT roll its epoch back for a zombie coordinator (pre-R-14 behavior)");

                    // Positive proof the epoch never moved: a legitimate update from
                    // the current epoch logs "from <E> to <E+100>". Had the rollback
                    // succeeded, this line would read "from <E-1> to ...".
                    taskRpc.updateFencingToken(currentEpoch + 100L);
                    long deadline = System.currentTimeMillis() + 10_000L;
                    boolean advanced = false;
                    while (System.currentTimeMillis() < deadline) {
                        log = java.nio.file.Files.readString(cluster.logFileFor(nodeId));
                        if (log.contains("Fencing epoch updated from " + currentEpoch + " to "
                                + (currentEpoch + 100L))) {
                            advanced = true;
                            break;
                        }
                        TimeUnit.MILLISECONDS.sleep(POLL_MS);
                    }
                    assertTrue(advanced, "a legitimate update must advance the epoch from the ORIGINAL "
                            + currentEpoch + " (i.e. the zombie rollback never landed). Log: "
                            + cluster.logFileFor(nodeId));
                } finally {
                    proxy.stop();
                }
            }
        }
    }

    /** Reads the max fencing epoch across all assignment rows (Java-side numeric max). */
    private static long waitForAssignmentEpoch(MiniStreamCluster cluster) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000L;
        long epoch = -1L;
        while (System.currentTimeMillis() < deadline) {
            final long[] best = {-1L};
            try {
                cluster.getHarnessJdbcTemplate().executeQuery(SQL.begin()
                        .sql("SELECT fencing_token FROM nop_stream_task_assignment")
                        .end(), dataSet -> {
                    for (io.nop.dataset.IDataRow row : dataSet) {
                        try {
                            best[0] = Math.max(best[0], Long.parseLong(row.getString(0).trim()));
                        } catch (NumberFormatException ignored) {
                            // legacy composite tokens — skip
                        }
                    }
                    return null;
                });
            } catch (Exception ignored) {
                // table may not exist yet
            }
            if (best[0] > 0L) {
                return best[0];
            }
            TimeUnit.MILLISECONDS.sleep(POLL_MS);
        }
        return epoch;
    }

    private static long waitForLeaderAndAssignments(MiniStreamCluster cluster, int coordIndex)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 40_000L;
        while (System.currentTimeMillis() < deadline) {
            if (!cluster.coordinatorAlive(coordIndex)) {
                throw new IllegalStateException("coordinator-" + coordIndex
                        + " died before becoming leader. Log: " + cluster.logFileFor("coordinator-" + coordIndex));
            }
            LeaseRow row = readLeaseRow(cluster);
            if (row != null && row.leaderId.contains("coordinator-" + coordIndex) && row.leaderEpoch >= 1) {
                return row.leaderEpoch;
            }
            TimeUnit.MILLISECONDS.sleep(POLL_MS);
        }
        return 0L;
    }

    private static LeaseRow readLeaseRow(MiniStreamCluster cluster) {
        if (cluster.getHarnessJdbcTemplate() == null) {
            return null;
        }
        try {
            return cluster.getHarnessJdbcTemplate().executeQuery(SQL.begin()
                    .sql("SELECT leader_id, leader_epoch FROM nop_stream_leader WHERE cluster_id = ?",
                            "job-" + cluster.getRunId())
                    .end(), dataSet -> {
                if (!dataSet.hasNext()) {
                    return null;
                }
                io.nop.dataset.IDataRow row = dataSet.next();
                return new LeaseRow(row.getString(0), row.getLong(1));
            });
        } catch (Exception e) {
            return null;
        }
    }

    static final class LeaseRow {
        final String leaderId;
        final long leaderEpoch;

        LeaseRow(String leaderId, long leaderEpoch) {
            this.leaderId = leaderId;
            this.leaderEpoch = leaderEpoch;
        }
    }
}

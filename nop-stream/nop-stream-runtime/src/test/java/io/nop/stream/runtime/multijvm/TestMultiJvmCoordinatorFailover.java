/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.multijvm;

import io.nop.core.lang.sql.SQL;
import io.nop.stream.core.testsupport.TestAwait;
import io.nop.stream.runtime.coordinator.JobCoordinator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

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
 *
 * <p>时间确定化（plan 2510，gated multijvm 边界）：虚拟时钟无法跨越 spawned-JVM 边界，
 * 本类的确定性化 = 消灭任意固定窗口、全部改为对外部可观测状态（租约行 / 分配行 / 进程
 * 存活 / 子进程日志）的条件等待（{@link TestAwait#until}），超时上限仅作挂死保护；
 * 租约到期（leaderLeaseMs=3000）与进程启动等真实时长语义保留为条件的自然等待时长。
 */
@EnabledIfSystemProperty(named = "nop.stream.test.multi-jvm.enabled", matches = "true")
class TestMultiJvmCoordinatorFailover {

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
     *
     * <p>Item 34 (takeover-COMPLETION assertion surface): additionally waits for the
     * old leader's assignment rows to be persisted BEFORE the kill (the defect window
     * requires them), and after the lease flip asserts the new leader's assignment rows
     * exist in the new fencing band with attempt numbers strictly continuing the killed
     * leader's per-subtask max — plus a clean coordinator-1 log (no duplicate-key, no
     * swallowed become-leader-listener abort). Pre-fix this test is red: the fresh JVM
     * re-issues attempt 1, collides on the (job_id, vertex_id, subtask_index,
     * attempt_number) PK, and no new-band row ever lands.
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

            // Item 34: waitForLeaderAndAssignments only observes the lease row — the defect
            // window needs the assignment ROWS persisted, so observe the assignment table
            // explicitly before killing (a kill before any row lands would not exercise
            // the duplicate-key path at all).
            waitForAssignmentRows(cluster);
            Map<String, Integer> preKillMaxAttempts = readPerSubtaskMaxAttempts(cluster);
            assertFalse(preKillMaxAttempts.isEmpty(),
                    "pre-kill per-subtask attempt map must not be empty");

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
            // 条件等待外部可观测状态（租约行 + 进程存活），超时上限仅挂死保护。
            AtomicReference<Long> newLeaderEpochRef = new AtomicReference<>(-1L);
            TestAwait.until("coordinator-1 must take over after coordinator-0 is killed "
                    + "(lease flip + epoch rotation)", () -> {
                if (!cluster.coordinatorAlive(1)) {
                    throw new IllegalStateException("coordinator-1 died during failover. Log: "
                            + cluster.logFileFor("coordinator-1"));
                }
                LeaseRow row = readLeaseRow(cluster);
                if (row != null && row.leaderId.contains("coordinator-1")
                        && row.leaderEpoch > row0.leaderEpoch) {
                    newLeaderEpochRef.set(row.leaderEpoch);
                    return true;
                }
                return false;
            }, FAILOVER_TIMEOUT_MS);
            long newLeaderEpoch = newLeaderEpochRef.get();

            // Item 34: takeover COMPLETION (not just lease flip) — new-epoch assignment
            // rows strictly continuing the killed leader's per-subtask attempt history.
            waitForTakeoverAssignmentContinuation(cluster, newLeaderEpoch, preKillMaxAttempts);

            // Item 34: no abort signature in the new leader's log — the pre-fix
            // duplicate-key abort left exactly these two signatures.
            String log1 = java.nio.file.Files.readString(cluster.logFileFor("coordinator-1"));
            assertFalse(log1.contains("duplicate-key")
                            || log1.contains("Unique index or primary key violation"),
                    "coordinator-1 log must contain no duplicate-key abort (takeover completed). Log: "
                            + cluster.logFileFor("coordinator-1"));
            assertFalse(log1.contains("invoke-become-leader-listener-error"),
                    "coordinator-1 log must contain no swallowed become-leader-listener abort. Log: "
                            + cluster.logFileFor("coordinator-1"));

            // Coordinator-1 is alive and is now the leader.
            assertTrue(cluster.coordinatorAlive(1), "coordinator-1 must remain alive after takeover");
        }
    }

    /**
     * Brain-split fencing boundary: both coordinators share the lease table; only the
     * lease holder can commit. This test verifies that after takeover, the old leader's
     * epoch is strictly less than the new leader's (so any in-flight control messages
     * from the old leader are rejected by the data-plane fencing filter — invariant #8).
     *
     * <p>Item 34: also asserts takeover completion (new-band assignment rows with
     * strictly continuing attempt numbers); the strictly-increasing epoch assertions
     * remain the test's primary subject.
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

            // Item 34: exercise the defect window (persisted rows before the kill).
            waitForAssignmentRows(cluster);
            Map<String, Integer> preKillMaxAttempts = readPerSubtaskMaxAttempts(cluster);
            assertFalse(preKillMaxAttempts.isEmpty(),
                    "pre-kill per-subtask attempt map must not be empty");

            cluster.spawnJobCoordinator(1);

            LeaseRow row0 = readLeaseRow(cluster);
            assertNotNull(row0);

            cluster.killCoordinator(0);

            // Wait for coordinator-1 takeover (条件等待租约行翻转 + epoch 递增)。
            AtomicReference<Long> epoch1Ref = new AtomicReference<>(-1L);
            TestAwait.until("coordinator-1 must take over after coordinator-0 is killed",
                    () -> {
                        LeaseRow row = readLeaseRow(cluster);
                        if (row != null && row.leaderId.contains("coordinator-1")
                                && row.leaderEpoch > row0.leaderEpoch) {
                            epoch1Ref.set(row.leaderEpoch);
                            return true;
                        }
                        return false;
                    }, FAILOVER_TIMEOUT_MS);
            long epoch1 = epoch1Ref.get();
            assertTrue(epoch1 > 0L, "coordinator-1 must take over");
            assertTrue(epoch1 > row0.leaderEpoch,
                    "new leader epoch (" + epoch1 + ") must be strictly greater than old ("
                            + row0.leaderEpoch + ") — fencing invariant #8");

            // Item 34: takeover completion — new-band rows strictly continue the
            // killed leader's per-subtask attempt history.
            waitForTakeoverAssignmentContinuation(cluster, epoch1, preKillMaxAttempts);
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

                    // 确定性证据替代固定 2s 观察窗：TM 反射调用失败时的
                    // nop.commons.reflect.invoke-method-fail INFO 行（携带
                    // fencing-token-mismatch 类型化错误的堆栈）——该日志出现即结构性
                    // 证明 TM 已实际接收并处理了僵尸消息且拒绝了回滚（而非"还没处理到"）。
                    TestAwait.until("TM received and rejected the zombie rollback "
                            + "(invoke-method-fail logged with the fencing-mismatch error)",
                            () -> {
                                try {
                                    String log = java.nio.file.Files.readString(cluster.logFileFor(nodeId));
                                    return log.contains("nop.commons.reflect.invoke-method-fail")
                                            && log.contains("nop.err.stream.fencing-token-mismatch");
                                } catch (java.io.IOException e) {
                                    return false;
                                }
                            });
                    String log = java.nio.file.Files.readString(cluster.logFileFor(nodeId));
                    assertFalse(log.contains("Fencing epoch updated from " + currentEpoch + " to "
                                    + (currentEpoch - 1)),
                            "the TM must NOT roll its epoch back for a zombie coordinator (pre-R-14 behavior)");

                    // Positive proof the epoch never moved: a legitimate update from
                    // the current epoch logs "from <E> to <E+100>". Had the rollback
                    // succeeded, this line would read "from <E-1> to ...".
                    taskRpc.updateFencingToken(currentEpoch + 100L);
                    TestAwait.until("a legitimate update must advance the epoch from the ORIGINAL "
                            + currentEpoch + " (i.e. the zombie rollback never landed)",
                            () -> {
                                try {
                                    return java.nio.file.Files.readString(cluster.logFileFor(nodeId))
                                            .contains("Fencing epoch updated from " + currentEpoch + " to "
                                                    + (currentEpoch + 100L));
                                } catch (java.io.IOException e) {
                                    return false;
                                }
                            });
                } finally {
                    proxy.stop();
                }
            }
        }
    }

    /** Reads the max fencing epoch across all assignment rows (Java-side numeric max). */
    private static long readMaxAssignmentEpoch(MiniStreamCluster cluster) {
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
        return best[0];
    }

    private static long waitForAssignmentEpoch(MiniStreamCluster cluster) {
        // 条件等待共享 DB 中出现分配行（外部可观测状态），超时上限仅挂死保护。
        TestAwait.until("assignments must exist before the zombie attempt "
                + "(max fencing token across assignment rows > 0)",
                () -> readMaxAssignmentEpoch(cluster) > 0L, 30_000L);
        return readMaxAssignmentEpoch(cluster);
    }

    private static long waitForLeaderAndAssignments(MiniStreamCluster cluster, int coordIndex) {
        // 条件等待租约行：coordinator-<i> 成为 leader 且 epoch >= 1；进程死亡即抛错
        // （fail-fast 携带日志路径），超时上限仅挂死保护。
        TestAwait.until("coordinator-" + coordIndex + " must win leadership and assign tasks "
                + "(lease row names it with epoch >= 1)", () -> {
            if (!cluster.coordinatorAlive(coordIndex)) {
                throw new IllegalStateException("coordinator-" + coordIndex
                        + " died before becoming leader. Log: " + cluster.logFileFor("coordinator-" + coordIndex));
            }
            LeaseRow row = readLeaseRow(cluster);
            return row != null && row.leaderId.contains("coordinator-" + coordIndex)
                    && row.leaderEpoch >= 1;
        }, 40_000L);
        return readLeaseRow(cluster).leaderEpoch;
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

    // ==================== Item 34 helpers: takeover-completion assertion surface ====================

    /** One assignment row: subtask key + attempt number + numeric fencing token. */
    private static final class AssignmentRow {
        final String key;
        final int attempt;
        final long token;

        AssignmentRow(String key, int attempt, long token) {
            this.key = key;
            this.attempt = attempt;
            this.token = token;
        }
    }

    /**
     * Item 34: waits (bounded, condition wait on the shared assignment table) until at
     * least one persisted row exists — the precondition for the duplicate-key defect
     * window. Timeout = hang-protection cap; the failure message carries the defect-window
     * context.
     */
    private static void waitForAssignmentRows(MiniStreamCluster cluster) {
        TestAwait.until("coordinator-0 must persist assignment rows before the kill (defect window)",
                () -> !scanAssignmentRows(cluster).isEmpty(), 30_000L);
    }

    /**
     * Item 34: per-subtask MAX(attempt_number) map (key = "vertexId/subtaskIndex"),
     * read from ALL persisted rows. The per-subtask granularity is deliberately pinned:
     * this test's fresh tables + uniform globalRecovery increments make global and
     * per-subtask views equivalent today, but a future topology with uneven histories
     * would silently fake-fail under a global comparison.
     */
    private static Map<String, Integer> readPerSubtaskMaxAttempts(MiniStreamCluster cluster) {
        Map<String, Integer> max = new LinkedHashMap<>();
        for (AssignmentRow r : scanAssignmentRows(cluster)) {
            max.merge(r.key, r.attempt, Math::max);
        }
        return max;
    }

    /**
     * Item 34: waits (bounded condition wait, pattern mirrors
     * {@link #waitForAssignmentEpoch}) until EVERY subtask that had a persisted row
     * before the kill has an assignment row in the new leader's fencing band whose
     * attempt number STRICTLY exceeds the pre-kill per-subtask max. The band check uses
     * the {@code fencing_token} column (note the cross-table convention: the lease
     * table's {@code leader_epoch} is a bare integer, the assignment table's
     * {@code fencing_token} = leaderEpoch × 1_000_000 + recoveryGen — same-leader
     * recoveries stay inside the band, a later leadership switch moves past its top).
     */
    private static void waitForTakeoverAssignmentContinuation(MiniStreamCluster cluster,
                                                              long newLeaderEpoch,
                                                              Map<String, Integer> preKillMaxAttempts) {
        long bandBase = JobCoordinator.deriveHaFencingEpoch(newLeaderEpoch, 0L);
        long bandTop = JobCoordinator.deriveHaFencingEpoch(newLeaderEpoch + 1L, 0L);
        TestAwait.until("new leader (epoch " + newLeaderEpoch + ", fencing band [" + bandBase + ","
                + bandTop + ") must COMPLETE takeover: every pre-kill subtask needs a new-band "
                + "assignment row with attempt_number strictly continuing the pre-kill max "
                + preKillMaxAttempts + ". Pre-fix signature: fresh JVM re-issues attempt 1, hits "
                + "the assignment PK, and no new-band row ever lands.",
                () -> {
                    Map<String, Integer> continued = new LinkedHashMap<>();
                    for (AssignmentRow r : scanAssignmentRows(cluster)) {
                        if (r.token >= bandBase && r.token < bandTop) {
                            continued.merge(r.key, r.attempt, Math::max);
                        }
                    }
                    for (Map.Entry<String, Integer> e : preKillMaxAttempts.entrySet()) {
                        Integer n = continued.get(e.getKey());
                        if (n == null || n <= e.getValue()) {
                            return false;
                        }
                    }
                    return !preKillMaxAttempts.isEmpty();
                }, FAILOVER_TIMEOUT_MS);
    }

    /** Scans all assignment rows (vertex, subtask, attempt, fencing token). */
    private static List<AssignmentRow> scanAssignmentRows(MiniStreamCluster cluster) {
        List<AssignmentRow> rows = new ArrayList<>();
        if (cluster.getHarnessJdbcTemplate() == null) {
            return rows;
        }
        try {
            cluster.getHarnessJdbcTemplate().executeQuery(SQL.begin()
                    .sql("SELECT vertex_id, subtask_index, attempt_number, fencing_token"
                            + " FROM nop_stream_task_assignment")
                    .end(), dataSet -> {
                for (io.nop.dataset.IDataRow row : dataSet) {
                    try {
                        rows.add(new AssignmentRow(
                                row.getString(0) + "/" + row.getInt(1),
                                row.getInt(2),
                                Long.parseLong(row.getString(3).trim())));
                    } catch (NumberFormatException ignored) {
                        // legacy composite tokens — skip
                    }
                }
                return null;
            });
        } catch (Exception ignored) {
            // table may not exist yet
        }
        return rows;
    }
}

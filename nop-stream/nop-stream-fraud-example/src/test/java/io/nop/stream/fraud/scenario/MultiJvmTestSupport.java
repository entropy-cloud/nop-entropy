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
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import io.nop.core.lang.sql.SQL;
import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.testsupport.TestAwait;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import io.nop.stream.runtime.launch.PollingJdbcMessageService;
import io.nop.stream.runtime.multijvm.MiniStreamCluster;
import io.nop.stream.runtime.rpc.IStreamTaskRpcService;
import io.nop.stream.runtime.rpc.StreamControlRpcProxyFactory;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Item 14 (composite-scenario distributed): shared helpers for the gated multi-JVM
 * scenario tests — fencing observability (task_assignment epochs), durable-manifest
 * waiting (the kill precondition), stale-epoch mutation probing (R-14 style zombie
 * rejection at the RPC boundary), and S1 shared-DB table preparation.
 */
final class MultiJvmTestSupport {

    /** The single coordinator spawned by {@code start()} logs under this label. */
    static final String COORDINATOR_LABEL = "coordinator-0";

    /**
     * Item 14 (shared): the S2 distributed expected row set (same constants the
     * C1 kill/recover, C2 restore-rescale and C3 backpressure tests assert against —
     * one expected-set definition for the whole distributed matrix).
     */
    static Set<TxSummaryRow> s2BaseExpectedRows() {
        Set<TxSummaryRow> rows = new java.util.LinkedHashSet<>();
        // W0
        rows.add(s2Row("u1", ScenarioTestSupport.T0, 3, "180"));
        rows.add(s2Row("u2", ScenarioTestSupport.T0, 2, "50"));
        rows.add(s2Row("user-bob", ScenarioTestSupport.T0, 1, "100"));
        rows.add(s2Row("u3", ScenarioTestSupport.T0, 1, "80"));
        rows.add(s2Row("user-eve", ScenarioTestSupport.T0, 1, "90"));
        // W1
        rows.add(s2Row("u3", ScenarioTestSupport.T0 + 10_000, 1, "40"));
        rows.add(s2Row("u1", ScenarioTestSupport.T0 + 10_000, 1, "90"));
        rows.add(s2Row("user-bob", ScenarioTestSupport.T0 + 10_000, 1, "110"));
        // W2 flush rows (u9@25-27s)
        rows.add(s2Row("u9", ScenarioTestSupport.T0 + 20_000, 3, "6"));
        // W3 terminator window (u9@32s) — closes deterministically in the
        // distributed runs (see TestS2MultiJvmE2E for the barrier-ordering rationale).
        rows.add(s2Row("u9", ScenarioTestSupport.T0 + 30_000, 1, "4"));
        return rows;
    }

    private static TxSummaryRow s2Row(String userId, long anyEventTime, long count, String total) {
        long start = ScenarioTestSupport.windowStart(anyEventTime);
        return new TxSummaryRow(userId, start, start + ScenarioTestSupport.WINDOW_SIZE_MS,
                count, new java.math.BigDecimal(total));
    }

    private MultiJvmTestSupport() {
    }

    // ----------------------------------------------------------------
    // Fencing observability (design C1 验收点: task_assignment epoch strictly
    // increasing after recovery redeploy)
    // ----------------------------------------------------------------

    static long readLatestFencingEpoch(MiniStreamCluster cluster) {
        Long value = cluster.getHarnessJdbcTemplate().executeQuery(SQL.begin()
                .sql("SELECT MAX(CAST(fencing_token AS BIGINT)) FROM nop_stream_task_assignment")
                .end(), ds -> {
            if (!ds.hasNext()) {
                return 0L;
            }
            var row = ds.next();
            return row.isNull(0) ? 0L : row.getLong(0);
        });
        return value == null ? 0L : value;
    }

    static int countAssignmentsAtEpoch(MiniStreamCluster cluster, long fencingEpoch) {
        Integer count = cluster.getHarnessJdbcTemplate().executeQuery(SQL.begin()
                .sql("SELECT COUNT(*) FROM nop_stream_task_assignment WHERE fencing_token = ?",
                        String.valueOf(fencingEpoch))
                .end(), ds -> {
            if (!ds.hasNext()) {
                return 0;
            }
            return ds.next().getInt(0);
        });
        return count == null ? 0 : count;
    }

    /**
     * Waits until the fencing epoch strictly exceeds {@code baseline} with assignments.
     *
     * <p>RESIDUAL（gated multi-JVM 面，plan 2510 具名裁定）：fencing epoch 由 spawned
     * coordinator JVM 写入共享 DB——虚拟时钟不可跨越进程边界，轮询间隔（300ms）是对
     * 跨 JVM 收敛节奏的主动节流而非等待手段。超时返回 lastSeen（不抛）是演练语义：
     * 轮次结果按 recorded-evidence 记入事件序列，由调用方在循环后裁决。
     */
    static long waitForEpochRotation(MiniStreamCluster cluster, long baselineEpoch, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        long lastSeen = baselineEpoch;
        while (System.currentTimeMillis() < deadline) {
            long current = readLatestFencingEpoch(cluster);
            if (current > baselineEpoch && countAssignmentsAtEpoch(cluster, current) >= 2) {
                return current;
            }
            lastSeen = Math.max(lastSeen, current);
            assertTrue(cluster.coordinatorAlive(),
                    "coordinator must stay alive while waiting for epoch rotation; log: "
                            + cluster.logFileFor(COORDINATOR_LABEL));
            TimeUnit.MILLISECONDS.sleep(300L);
        }
        return lastSeen;
    }

    // ----------------------------------------------------------------
    // Kill precondition: latest durable epoch manifest exists (checkpoint
    // storage observable) — the plan's "恢复断言不得退化为从零重跑" guard
    // ----------------------------------------------------------------

    static long waitForDurableManifest(MiniStreamCluster cluster, String jobId, long timeoutMs) {
        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(
                cluster.getCheckpointDir().toString());
        AtomicLong epoch = new AtomicLong(-1L);
        try {
            TestAwait.until("durable EpochManifest appeared for job " + jobId, () -> {
                try {
                    var manifest = storage.loadLatestEpochManifest(jobId, "pipeline-0");
                    if (manifest != null) {
                        epoch.set(manifest.getEpochId());
                        return true;
                    }
                } catch (Exception ignored) {
                    // storage may not exist yet on the very first poll
                }
                return false;
            }, timeoutMs);
        } catch (AssertionError e) {
            throw new AssertionError("No durable EpochManifest appeared within " + timeoutMs
                    + "ms for job " + jobId + " at " + cluster.getCheckpointDir()
                    + " — checkpoints are not completing in multi-JVM mode. Coordinator log: "
                    + readLogTail(cluster, COORDINATOR_LABEL), e);
        }
        return epoch.get();
    }

    static long currentDurableManifestEpoch(MiniStreamCluster cluster, String jobId) {
        try {
            var manifest = new LocalFileCheckpointStorage(cluster.getCheckpointDir().toString())
                    .loadLatestEpochManifest(jobId, "pipeline-0");
            return manifest != null ? manifest.getEpochId() : -1L;
        } catch (Exception e) {
            return -1L;
        }
    }

    // ----------------------------------------------------------------
    // Stale-epoch mutation probe (R-14 pattern: an old-epoch control-plane
    // mutation must be REJECTED at the TaskManager's RPC boundary, observable
    // in its log — behavior-level fencing, not process liveness)
    // ----------------------------------------------------------------

    /**
     * Sends a checkpoint-trigger with a stale fencing epoch to {@code nodeId} from the
     * TEST JVM (a genuine third-party control-plane caller over the shared message
     * backbone), then asserts the target TaskManager's log records the rejection.
     *
     * @return the rejections-observed log marker text
     */
    static String probeStaleEpochRejection(MiniStreamCluster cluster, String nodeId, long staleEpoch)
            throws Exception {
        long logSizeBefore = Files.size(cluster.logFileFor(nodeId));

        PollingJdbcMessageService messageService = new PollingJdbcMessageService(
                cluster.getHarnessJdbcTemplate(), 50L);
        messageService.initialize();
        StreamControlRpcProxyFactory proxy = new StreamControlRpcProxyFactory(
                "streamTaskRpc-probe@" + nodeId, IStreamTaskRpcService.class,
                messageService, io.nop.stream.runtime.launch.TaskManagerMain.taskRpcTopic(
                        cluster.getTopicNamespace(), nodeId));
        try {
            proxy.start();
            IStreamTaskRpcService rpc = proxy.getProxy();
            CheckpointBarrier barrier = new CheckpointBarrier(
                    9_999_999L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);
            try {
                rpc.triggerCheckpoint(barrier, staleEpoch);
            } catch (Exception e) {
                // The proxy is one-way for void calls; an exception here would be a
                // transport failure, not the fencing rejection itself.
                throw new AssertionError("stale-epoch probe transport failed: " + e, e);
            }
            // The rejection is processed asynchronously by the target TM's poller —
            // 条件等待 log 增量出现拒绝标记（上限仅挂死保护）。
            AtomicReference<String> observed = new AtomicReference<>();
            try {
                TestAwait.until("target TaskManager's log records the stale-epoch rejection",
                        () -> {
                            String delta;
                            try {
                                delta = readLogDelta(cluster.logFileFor(nodeId), logSizeBefore);
                            } catch (java.io.IOException e) {
                                // log file not flushed/readable yet — keep waiting
                                return false;
                            }
                            if (delta.contains("ERR_STREAM_FENCING_TOKEN_MISMATCH")
                                    || delta.contains("fencing-token-mismatch")
                                    || delta.contains("process-request-fail")) {
                                observed.set(delta);
                                return true;
                            }
                            return false;
                        }, 15_000L);
            } catch (AssertionError e) {
                throw new AssertionError("Stale-epoch mutation (epoch=" + staleEpoch + ") to " + nodeId
                        + " was not observably rejected within 15s. Log delta: "
                        + readLogDelta(cluster.logFileFor(nodeId), logSizeBefore).substring(0,
                        (int) Math.min(2000, readLogDelta(cluster.logFileFor(nodeId), logSizeBefore).length())), e);
            }
            return observed.get();
        } finally {
            proxy.stop();
            messageService.close();
        }
    }

    // ----------------------------------------------------------------
    // Log helpers
    // ----------------------------------------------------------------

    static String readLogDelta(Path logFile, long fromOffset) throws java.io.IOException {
        long size = Files.size(logFile);
        if (size <= fromOffset) {
            return "";
        }
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(logFile.toFile(), "r")) {
            raf.seek(fromOffset);
            byte[] buf = new byte[(int) Math.min(size - fromOffset, 8 * 1024 * 1024L)];
            int read = raf.read(buf);
            return new String(buf, 0, read, java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    static String readLogTail(MiniStreamCluster cluster, String label) {
        try {
            Path log = cluster.logFileFor(label);
            if (!Files.exists(log)) {
                return "(no log file " + log + ")";
            }
            String all = Files.readString(log, java.nio.charset.StandardCharsets.UTF_8);
            return all.substring(Math.max(0, all.length() - 3000));
        } catch (Exception e) {
            return "(failed to read log: " + e + ")";
        }
    }

    // ----------------------------------------------------------------
    // S1 shared-DB preparation
    // ----------------------------------------------------------------

    static void resetS1Tables(MiniStreamCluster cluster) throws Exception {
        var jdbc = openSharedJdbc(cluster);
        ScenarioTestSupport.execute(jdbc, "DROP TABLE IF EXISTS " + ScenarioTestSupport.ALERT_TABLE);
        for (String ledger : new String[]{"fraud_ledger_rapid", "fraud_ledger_unusual",
                "fraud_ledger_geo", "fraud_ledger_takeover"}) {
            ScenarioTestSupport.execute(jdbc, "DROP TABLE IF EXISTS " + ledger);
        }
        ScenarioTestSupport.createS1Tables(jdbc);
        // Ledger DDL comes from the SINK's own getLedgerTableDDL (committed_at is
        // TIMESTAMP — a hand-written BIGINT variant breaks the sink's INSERT).
        for (String ledger : new String[]{"fraud_ledger_rapid", "fraud_ledger_unusual",
                "fraud_ledger_geo", "fraud_ledger_takeover"}) {
            DistributedScenarioSupport.alertSink(cluster.getJdbcUrl(), "sa", "", ledger)
                    .initializeLedgerTable();
        }
    }

    /**
     * Opens a JDBC template to the cluster's shared H2 (AUTO_SERVER file URL) from
     * the TEST JVM — usable BEFORE {@code cluster.start()} (the harness template only
     * exists after start). The S1 table DDL must exist before the coordinator child
     * starts committing.
     */
    static io.nop.dao.jdbc.IJdbcTemplate openSharedJdbc(MiniStreamCluster cluster) {
        org.h2.jdbcx.JdbcDataSource ds = new org.h2.jdbcx.JdbcDataSource();
        ds.setURL(cluster.getJdbcUrl());
        ds.setUser("sa");
        ds.setPassword("");
        return ScenarioTestSupport.newJdbcTemplate(ds);
    }

    static void assertRecoveryLogged(MiniStreamCluster cluster, long logSizeBeforeKill) throws Exception {
        String delta = readLogDelta(cluster.logFileFor(COORDINATOR_LABEL), logSizeBeforeKill);
        assertTrue(delta.contains("global recovery") || delta.contains("globalRecovery")
                        || delta.contains("Fencing epoch rotated")
                        || delta.contains("Starting global recovery"),
                "Coordinator log must capture the recovery event after the kill");
    }

    /** 条件等待（上限仅挂死保护）：条件异常按「尚未成立」处理并在超时原样抛出。 */
    static void waitFor(BooleanCondition condition, long timeoutMs, String message)
            throws Exception {
        AtomicReference<Exception> lastError = new AtomicReference<>();
        try {
            TestAwait.until(message, () -> {
                try {
                    return condition.eval();
                } catch (Exception e) {
                    lastError.set(e);
                    return false;
                }
            }, timeoutMs);
        } catch (AssertionError e) {
            if (lastError.get() != null) {
                throw lastError.get();
            }
            throw e;
        }
    }

    /**
     * Item 14 (shared by the S2 multi-JVM tests): waits for the file sink's output
     * convergence — {@code assertExactlyOnceOutput} must pass (条件等待，上限仅挂死
     * 保护；收敛节奏由 spawned TM JVM 的真实落盘决定). A not-yet-created output
     * directory means "no committed output yet" (the running sink is the deserialized
     * bean in a TM JVM — directory creation does not cross the JVM boundary) and
     * keeps waiting.
     */
    static void waitForExactlyOnceOutput(java.nio.file.Path outputDir,
                                         Set<TxSummaryRow> expected,
                                         long timeoutMs, String context) {
        AtomicReference<AssertionError> last = new AtomicReference<>();
        try {
            TestAwait.until("exactly-once output converged to the expected set", () -> {
                try {
                    TestS2FileAggregationE2E.assertExactlyOnceOutput(outputDir, expected);
                    return true;
                } catch (AssertionError e) {
                    last.set(e);
                } catch (Exception e) {
                    // NoSuchFileException 等 IO 未就绪态：输出目录尚未创建/尚未落盘——继续等待
                    last.set(new AssertionError("output not readable/converged yet: " + outputDir, e));
                }
                return false;
            }, timeoutMs);
        } catch (AssertionError timeout) {
            String outputRows;
            try {
                outputRows = String.valueOf(TestS2FileAggregationE2E.readOutputRows(outputDir));
            } catch (Exception e) {
                outputRows = "(unavailable: " + e + ")";
            }
            throw new AssertionError(context + " — output did not converge to the expected set within "
                    + timeoutMs + "ms. Last assertion: " + last.get()
                    + " outputRows=" + outputRows, timeout);
        }
    }

    /**
     * Item 14 (matrix C3): observes the durable-manifest epoch until it STRICTLY
     * ADVANCES at least once while {@code throttleStillEngaged} holds — the C3
     * acceptance「背压期间 checkpoint 仍推进（无死锁）」via the design's observation
     * proxy (checkpoint storage progress). Returns the last observed epoch.
     * 条件等待（上限仅挂死保护；推进节奏由 spawned JVM 的真实 checkpoint 决定）。
     */
    static long waitForDurableEpochAdvanceUnderThrottle(MiniStreamCluster cluster, String jobId,
                                                        java.util.function.BooleanSupplier throttleStillEngaged,
                                                        long timeoutMs) {
        long firstObserved = currentDurableManifestEpoch(cluster, jobId);
        if (firstObserved < 0) {
            // First durable epoch must also appear within the window.
            firstObserved = waitForDurableManifest(cluster, jobId, timeoutMs);
        }
        final long first = firstObserved;
        AtomicLong advanced = new AtomicLong(-1L);
        AtomicLong lastSeen = new AtomicLong(first);
        try {
            TestAwait.until("durable checkpoint epoch advanced while the sink throttle was engaged",
                    () -> {
                        long current = currentDurableManifestEpoch(cluster, jobId);
                        if (current > first && throttleStillEngaged.getAsBoolean()) {
                            advanced.set(current);
                            return true;
                        }
                        lastSeen.accumulateAndGet(current, Math::max);
                        return false;
                    }, timeoutMs);
        } catch (AssertionError e) {
            throw new AssertionError("Durable checkpoint epoch did not advance while the sink throttle "
                    + "was engaged within " + timeoutMs + "ms (first=" + first + " lastSeen=" + lastSeen.get()
                    + ") — checkpoint loop stalled under backpressure. Coordinator log: "
                    + readLogTail(cluster, COORDINATOR_LABEL), e);
        }
        return advanced.get();
    }

    @FunctionalInterface
    interface BooleanCondition {
        boolean eval() throws Exception;
    }

}

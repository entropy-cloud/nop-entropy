/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.fraud.scenario;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import io.nop.core.initialize.CoreInitialization;
import io.nop.stream.core.common.state.backend.IStateBackend;
import io.nop.stream.core.common.state.backend.memory.MemoryStateBackend;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.flow.model.StreamModel;
import io.nop.stream.rocksdb.RocksDBStateBackend;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static io.nop.stream.fraud.scenario.ScenarioTestSupport.S2_STREAM_PATH;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.T0;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.buildEnv;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.parseStreamXml;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.registerCheckpointExecutorFactory;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.s2Resolver;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.unregisterCheckpointExecutorFactory;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.windowStart;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * S2 recovery / state-backend-switch E2E (design §3.2.4): plain recovery
 * consistency (checkpoint -> stop -> restore from the latest durable manifest ->
 * remaining input -> merged exactly-once output) on BOTH the memory and RocksDB
 * backends (A2-6). Window W1 deliberately spans the restore boundary, so the
 * keyed window-state continuation across the job restart is proven.
 *
 * <p><b>A2-4 routing note (restore-time parallelism rescale)</b>（supersession
 * 2026-09-04：CONN-01 successor 已落地——2PC sink 并行门禁解除，keyed-P&gt;1 + 2PC
 * sink 复合形态的 LOCAL 端到端证明由 {@code TestParallel2PcJdbcE2E}/
 * {@code TestParallel2PcFileE2E} 承载；跨并行度<strong>恢复</strong>被 typed 拒绝
 * （{@code ERR_STREAM_2PC_SINK_PARALLELISM_CHANGE_UNSUPPORTED}，D1 裁定
 * checkpoint-design.md §8.5.2），故 restore-time rescale 的 P&gt;1 形态仍不在本测试
 * 范围。原路由理由留档：引擎曾对 2PC sink 在有效并行度 &gt; 1 规划期 fail-fast）。
 * Keyed-state re-routing under a changed key-group layout IS still
 * covered locally by the offline-reshard restore test (A2-5, new maxParallelism).
 */
public class TestS2RecoveryAndRescaleE2E {

    @TempDir
    Path tempDir;

    private Path inputDir;
    private Path outputDir;
    private Path storageDir;

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
        registerCheckpointExecutorFactory();
    }

    @AfterAll
    public static void destroy() {
        unregisterCheckpointExecutorFactory();
        CoreInitialization.destroy();
    }

    private void setUp() throws IOException {
        inputDir = tempDir.resolve("input");
        Files.createDirectories(inputDir);
        outputDir = tempDir.resolve("out");
        storageDir = tempDir.resolve("checkpoints");

        // slice A (run 1): W0 + the head of W1 (W1 stays in-flight across the
        // restore boundary — the last periodic checkpoint carries its partial
        // accumulation, exercising keyed window-state restore)
        writeLines("part-a1.txt",
                "u1,50," + (T0 + 0),
                "u2,30," + (T0 + 1000),
                "u1,70," + (T0 + 2000),
                "u3,20," + (T0 + 3000),
                "u2,10," + (T0 + 1500));      // out-of-order within the delay bound
        writeLines("part-a2.txt",
                "u1,30," + (T0 + 8000),
                "u3,40," + (T0 + 9000),
                "u2,60," + (T0 + 11000),     // W1 head (in-flight across runs)
                "u1,20," + (T0 + 12000));    // pushes wm to 10000 -> W0 fires mid-run

        // slice B (run 2): W1 tail + W2 + terminator tail
        writeLines("part-b1.txt",
                "u3,80," + (T0 + 16000),
                "u2,50," + (T0 + 17000),
                "u1,90," + (T0 + 21000),
                "u3,70," + (T0 + 22000),
                "u9,1," + (T0 + 25000),      // wm 23000 -> W1 fires mid-run
                "u9,2," + (T0 + 26000),
                "u9,3," + (T0 + 32000),      // terminator tail: wm > W2 end fires W2;
                "u9,4," + (T0 + 33000),      // trailing collects drain barrier mails so
                "u9,5," + (T0 + 34000),      // the committing checkpoints complete
                "u9,6," + (T0 + 35000),
                "u9,7," + (T0 + 36000),
                "u9,8," + (T0 + 37000),
                "u9,9," + (T0 + 38000));
    }

    private void writeLines(String fileName, String... lines) throws IOException {
        Files.write(inputDir.resolve(fileName), String.join("\n", lines).getBytes());
    }

    private void run(IStateBackend backend) throws Exception {
        StreamModel model = parseStreamXml(S2_STREAM_PATH);
        StreamExecutionEnvironment env = buildEnv(
                model,
                s2Resolver(inputDir.toString(), outputDir.toString(), 100L, 600L),
                storageDir.toString(), backend);
        env.execute("fraud-s2-recovery");
    }

    /** Expected per-window rows over ALL closed windows of the full input. */
    static Map<String, double[]> fullExpected() {
        Map<String, double[]> expected = new TreeMap<>();
        // W0
        expected.put(rowKey("u1", T0), new double[]{3, 150});        // 50+70+30
        expected.put(rowKey("u2", T0), new double[]{2, 40});         // 30+10
        expected.put(rowKey("u3", T0), new double[]{2, 60});         // 20+40
        // W1 (continued across the restore boundary)
        expected.put(rowKey("u2", T0 + 10000), new double[]{2, 110}); // 60+50
        expected.put(rowKey("u1", T0 + 10000), new double[]{1, 20});
        expected.put(rowKey("u3", T0 + 10000), new double[]{1, 80});
        // W2 (u9 terminator-tail lines land in W3, in-flight at EOS by design)
        expected.put(rowKey("u1", T0 + 20000), new double[]{1, 90});
        expected.put(rowKey("u3", T0 + 20000), new double[]{1, 70});
        expected.put(rowKey("u9", T0 + 20000), new double[]{2, 3});   // 1+2 (flush lines)
        return expected;
    }

    private static String rowKey(String userId, long anyEventTime) {
        return userId + "|" + windowStart(anyEventTime);
    }

    /**
     * Groups the union of all committed epoch-file lines by (user, windowStart)
     * and sums count/amount — the exactly-once read of the output directory
     * across both runs.
     */
    private Map<String, double[]> groupedOutput() throws IOException {
        Map<String, double[]> grouped = new TreeMap<>();
        List<TxSummaryRow> rows = TestS2FileAggregationE2E.readOutputRows(outputDir);
        for (TxSummaryRow row : rows) {
            String key = row.getUserId() + "|" + row.getWindowStart();
            double[] agg = grouped.computeIfAbsent(key, k -> new double[2]);
            agg[0] += row.getCount();
            agg[1] += row.getTotalAmount().doubleValue();
        }
        return grouped;
    }

    private void assertMergedExactlyOnce() throws IOException {
        Map<String, double[]> actual = groupedOutput();
        Map<String, double[]> expected = fullExpected();
        assertEquals(expected.keySet(), actual.keySet(),
                "grouped output windows must equal the full expected set.\nexpected="
                        + expected.keySet() + "\nactual=" + actual.keySet());
        for (Map.Entry<String, double[]> e : expected.entrySet()) {
            double[] act = actual.get(e.getKey());
            assertEquals(e.getValue()[0], act[0], 0.0001, "count for " + e.getKey());
            assertEquals(e.getValue()[1], act[1], 0.0001, "amount for " + e.getKey());
        }
        // exactly-once directory contract
        TestS2FileAggregationE2E.assertNoTempResidue(outputDir);
    }

    @Test
    public void s2Recovery_memoryBackend() throws Exception {
        setUp();
        run(new MemoryStateBackend());
        run(new MemoryStateBackend());
        assertMergedExactlyOnce();
    }

    @Test
    public void s2Recovery_rocksdbBackend() throws Exception {
        setUp();
        // fresh RocksDB scratch dirs per run: the DB is scratch storage rebuilt
        // from the JSON checkpoint snapshot on restore (the shared CHECKPOINT
        // storage carries the state across the restart)
        run(new RocksDBStateBackend(tempDir.resolve("rdb-run1").toString(), 128));
        run(new RocksDBStateBackend(tempDir.resolve("rdb-run2").toString(), 128));
        assertMergedExactlyOnce();
    }
}

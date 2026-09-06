/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.fraud.scenario;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.nop.core.initialize.CoreInitialization;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.fraud.scenario.ReplayableCdcSourceFunction.CdcEventFixtures;
import io.nop.stream.core.checkpoint.StorageJobIds;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static io.nop.stream.fraud.scenario.ScenarioTestSupport.PATTERN_RAPID;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.PATTERN_TAKEOVER;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.PATTERN_UNUSUAL;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.S1_STREAM_PATH;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.T0;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.alertRow;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.buildEnv;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.createS1Tables;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.newH2DataSource;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.newJdbcTemplate;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.parseStreamXml;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.readAlertRows;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.readLedgerRows;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.registerCheckpointExecutorFactory;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.replaySource;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.s1Resolver;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.sameRows;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.unregisterCheckpointExecutorFactory;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S1 recovery semantics E2E (roadmap item 13, design §3.1.4 A1-6/A1-7):
 * checkpoint -> stop -> restore from the latest durable epoch manifest ->
 * replay from the checkpointed CDC offset -> final table equals the full
 * expected set with no duplicates (ledger idempotent guard skips the
 * already-committed epochs on the restored sink).
 *
 * <p>The two-run split is time-partitioned (run 1 = W0 slice, run 2 = W1 slice)
 * and bob's unusual-amount alert in run 2 depends on the keyed per-user history
 * RESTORED from run 1 — a hollow restore (empty keyed state) would leave bob's
 * row missing, so the assertion is restore-sensitive.
 */
public class TestS1CdcRecoveryE2E {

    @TempDir
    Path tempDir;

    private static javax.sql.DataSource dataSource;
    private static io.nop.dao.jdbc.IJdbcTemplate jdbcTemplate;

    private Path storageDir;

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
        registerCheckpointExecutorFactory();
        dataSource = newH2DataSource("fraud-s1-recovery");
        jdbcTemplate = newJdbcTemplate(dataSource);
        createS1Tables(jdbcTemplate);
    }

    @AfterAll
    public static void destroy() {
        unregisterCheckpointExecutorFactory();
        CoreInitialization.destroy();
    }

    private static List<Map<String, Object>> w0Slice() {
        List<Map<String, Object>> events = new ArrayList<>();
        // alice rapid pair (W0)
        events.add(CdcEventFixtures.spec("c", T0 + 1000, "tx-a2", "alice", "1200", "NYC", "PURCHASE"));
        events.add(CdcEventFixtures.spec("c", T0 + 2000, "tx-a1", "alice", "1500", "NYC", "PURCHASE"));
        // bob history for the run-2 unusual proof (no alert in W0)
        events.add(CdcEventFixtures.spec("c", T0 + 3000, "tx-b1", "bob", "100", "NYC", "PURCHASE"));
        events.add(CdcEventFixtures.spec("c", T0 + 4000, "tx-b2", "bob", "100", "NYC", "PURCHASE"));
        events.add(CdcEventFixtures.spec("c", T0 + 5000, "tx-b3", "bob", "100", "NYC", "PURCHASE"));
        // NOTE: dave gets NO W0 history event: his LOGIN/CHANGE (amount 0) already
        // count into the keyed history, and a third W0 event would make his
        // WITHDRAWAL (3000) trip the unusual-amount rule (avg 3 events incl. zeros).

        // watermark flush + TRAILING events: the flush (T0+13s) pushes the
        // watermark strictly past W0's end (11000 > 10000) so W0 fires mid-run;
        // the trailing events keep the source collecting, which drains the
        // barrier-control mailbox so the periodic checkpoints that COMMIT the
        // fired rows can complete before the bounded source ends. The persisted
        // CEP/window watermark stays BELOW run 2's W1 slice (watermark continuity
        // across restore: events older than the restored watermark are late).
        events.add(CdcEventFixtures.spec("c", T0 + 13000, "tx-f1", "frank", "50", "NYC", "PURCHASE"));
        events.add(CdcEventFixtures.spec("c", T0 + 14000, "tx-f2", "frank", "60", "NYC", "PURCHASE"));
        events.add(CdcEventFixtures.spec("c", T0 + 15000, "tx-f3", "frank", "70", "NYC", "PURCHASE"));
        return events;
    }

    private static List<Map<String, Object>> fullFixture() {
        List<Map<String, Object>> events = new ArrayList<>(w0Slice());
        // W1 slice: dave takeover + bob's unusual event (depends on RESTORED
        // history). All W1 event times are strictly above the watermark persisted
        // by run 1 (13000).
        events.add(CdcEventFixtures.spec("c", T0 + 16000, "tx-d1", "dave", "0", "SEA", "LOGIN"));
        events.add(CdcEventFixtures.spec("c", T0 + 17000, "tx-d2", "dave", "0", "SEA", "CHANGE_PASSWORD"));
        events.add(CdcEventFixtures.spec("c", T0 + 18000, "tx-d3", "dave", "3000", "SEA", "WITHDRAWAL"));
        events.add(CdcEventFixtures.spec("c", T0 + 19000, "tx-b4", "bob", "2500", "NYC", "PURCHASE"));
        // watermark flush for W1 (events far beyond W1's end) + trailing
        // collects so the committing checkpoints complete before source end
        events.add(CdcEventFixtures.spec("c", T0 + 35000, "tx-f4", "frank", "50", "NYC", "PURCHASE"));
        for (int i = 0; i < 10; i++) {
            events.add(CdcEventFixtures.spec("c", T0 + 36000L + i * 1000L,
                    "tx-f5-" + i, "frank", "60", "NYC", "PURCHASE"));
        }
        return events;
    }

    private void run(List<Map<String, Object>> specs) throws Exception {
        // linger 必须容纳「W0 窗口触发 → saveState 进 pendingCommits → 下一 checkpoint 周期 commit」
        // 至少两个周期。600ms（6 个周期）在全量并行构建负载下曾不足（checkpoint executor 被抢占，
        // commit 周期推迟到秒级，run 1 断言时 alice alert 未提交）。3000ms ≈ 30 个周期提供充裕裕度。
        ReplayableCdcSourceFunction source = replaySource(
                "fraud-s1-recovery", specs, 25L, 3000L);
        StreamExecutionEnvironment env = buildEnv(
                parseStreamXml(S1_STREAM_PATH),
                s1Resolver(source, jdbcTemplate),
                storageDir.toString(), null);
        env.execute("fraud-s1-recovery");
    }

    @Test
    public void s1RestoreReplaysFromCheckpointAndCommitsExactlyOnce() throws Exception {
        storageDir = tempDir.resolve("checkpoints");

        // ---- run 1: W0 slice only ----
        run(w0Slice());

        // A1-7 anchor: a durable epoch manifest exists after run 1
        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(storageDir.toString());
        assertNotNull(storage.loadLatestEpochManifest(StorageJobIds.sanitizeJobId("fraud-s1-recovery"), "pipeline-0"),
                "run 1 must leave a durable epoch manifest");
        long maxEpochAfterRun1 = maxLedgerEpoch();

        // run 1 already committed the alice alert (W0 fired mid-run)
        Set<AlertSummaryRow> afterRun1 = readAlertRows(jdbcTemplate);
        assertTrue(afterRun1.stream().anyMatch(r -> "alice".equals(r.getUserId())),
                "run 1 must have committed the alice rapid alert: " + afterRun1);
        assertTrue(afterRun1.stream().noneMatch(r -> "bob".equals(r.getUserId())),
                "bob must NOT alert in run 1 (history only): " + afterRun1);

        // ---- run 2: full fixture, same storage -> restore from latest manifest ----
        run(fullFixture());

        // Final table: exactly the union expectation, no duplicates. bob's row
        // requires the keyed history restored across the job boundary; the alice
        // row must NOT be re-inserted (ledger idempotent guard on restored
        // pendingCommits / no re-derivation of pre-checkpoint events).
        Set<AlertSummaryRow> expected = new LinkedHashSet<>();
        expected.add(alertRow(T0 + 2000, "alice", PATTERN_RAPID, 1, "2700"));
        expected.add(alertRow(T0 + 13000, "dave", PATTERN_TAKEOVER, 1, "3000"));
        expected.add(alertRow(T0 + 14000, "bob", PATTERN_UNUSUAL, 1, "2500"));

        Set<AlertSummaryRow> actual = readAlertRows(jdbcTemplate);
        assertTrue(sameRows(actual, expected),
                "S1 final table after restore must equal the full expected set.\n"
                        + "expected=" + expected + "\nactual=" + actual);

        // checkpoint ids advanced past the restored epoch (A1-7: restore anchors
        // on the durable manifest; new epochs strictly increase)
        assertTrue(maxLedgerEpoch() > maxEpochAfterRun1,
                "run 2 must commit epochs strictly greater than run 1's: "
                        + maxLedgerEpoch() + " vs " + maxEpochAfterRun1);
    }

    private long maxLedgerEpoch() {
        long max = -1;
        for (String ledger : new String[]{"fraud_ledger_rapid", "fraud_ledger_unusual",
                "fraud_ledger_geo", "fraud_ledger_takeover"}) {
            for (Long epoch : readLedgerRows(jdbcTemplate, ledger).keySet()) {
                max = Math.max(max, epoch);
            }
        }
        return max;
    }
}

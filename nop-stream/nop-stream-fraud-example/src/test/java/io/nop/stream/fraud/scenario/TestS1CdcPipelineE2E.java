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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static io.nop.stream.fraud.scenario.ScenarioTestSupport.PATTERN_GEO;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.PATTERN_RAPID;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.PATTERN_TAKEOVER;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.PATTERN_UNUSUAL;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S1 composite scenario happy-path E2E (roadmap item 13, design §3.1):
 * XDSL pipeline (CDC source -> watermarks -> decode -> keyBy -> keyed-state enrich
 * -> 4 CEP chains -> window -> aggregate -> 2PC JDBC sink) executed end-to-end from
 * the declarative definition; assertions A1-1..A1-4 + A1-5 (exactly-once table +
 * ledger shape).
 *
 * <p>Expected outputs are PRE-COMPUTED literals (not engine re-derivations):
 * every row is hand-derived from the fixture semantics in the comments below.
 */
public class TestS1CdcPipelineE2E {

    @TempDir
    Path tempDir;

    private static javax.sql.DataSource dataSource;
    private static io.nop.dao.jdbc.IJdbcTemplate jdbcTemplate;

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
        registerCheckpointExecutorFactory();
        dataSource = newH2DataSource("fraud-s1-e2e");
        jdbcTemplate = newJdbcTemplate(dataSource);
        createS1Tables(jdbcTemplate);
    }

    @AfterAll
    public static void destroy() {
        unregisterCheckpointExecutorFactory();
        CoreInitialization.destroy();
    }

    /**
     * Deterministic CDC fixture (arrival order = list order). Window W0 = [T0, T0+10s),
     * W1 = [T0+10s, T0+20s). Out-of-order arrivals are kept WITHIN the 2s bounded
     * out-of-orderness delay (the engine's CEP drops records whose timestamp is
     * already behind the watermark — disorder beyond the delay is the late-event
     * case at the end of the fixture).
     */
    private static List<Map<String, Object>> fullFixture() {
        List<Map<String, Object>> events = new ArrayList<>();
        // W0 noise
        events.add(CdcEventFixtures.spec("c", T0 + 0, "tx-f1", "frank", "50", "NYC", "PURCHASE"));
        events.add(CdcEventFixtures.spec("c", T0 + 1000, "tx-f2", "frank", "60", "NYC", "PURCHASE"));
        // heidi early history (for the late-event case)
        events.add(CdcEventFixtures.spec("c", T0 + 1000, "tx-h1", "heidi", "100", "NYC", "PURCHASE"));
        events.add(CdcEventFixtures.spec("c", T0 + 2000, "tx-h2", "heidi", "100", "NYC", "PURCHASE"));
        // alice rapid pair (>1000 twice, strictly adjacent in alice's keyed stream)
        events.add(CdcEventFixtures.spec("c", T0 + 1000, "tx-a2", "alice", "1200", "NYC", "PURCHASE"));
        events.add(CdcEventFixtures.spec("c", T0 + 2000, "tx-a1", "alice", "1500", "NYC", "PURCHASE"));
        events.add(CdcEventFixtures.spec("c", T0 + 3000, "tx-h3", "heidi", "100", "NYC", "PURCHASE"));
        // bob: 3 x 100 history with OUT-OF-ORDER arrival (4s arrives before 3s —
        // A1-1: the keyed average and window assignment are order-independent),
        // then 2500 -> unusual alert (prior avg 100, 2500 > 10*100)
        events.add(CdcEventFixtures.spec("c", T0 + 4000, "tx-b1", "bob", "100", "NYC", "PURCHASE"));
        events.add(CdcEventFixtures.spec("c", T0 + 3000, "tx-b2", "bob", "100", "NYC", "PURCHASE"));
        // carol control: avg 500 after 3 txns, then 3000 < 10*500 -> NO alert
        // (a fixed $100 stub average WOULD alert here - A1-4 differentiator)
        events.add(CdcEventFixtures.spec("c", T0 + 3000, "tx-c1", "carol", "500", "NYC", "PURCHASE"));
        events.add(CdcEventFixtures.spec("c", T0 + 4000, "tx-c2", "carol", "500", "NYC", "PURCHASE"));
        events.add(CdcEventFixtures.spec("c", T0 + 5000, "tx-b3", "bob", "100", "NYC", "PURCHASE"));
        events.add(CdcEventFixtures.spec("c", T0 + 5000, "tx-c3", "carol", "500", "NYC", "PURCHASE"));
        events.add(CdcEventFixtures.spec("c", T0 + 6000, "tx-b4", "bob", "2500", "NYC", "PURCHASE"));
        events.add(CdcEventFixtures.spec("c", T0 + 7000, "tx-c4", "carol", "3000", "NYC", "PURCHASE"));

        // W1: dave takeover sequence with gina interference interleaved in ARRIVAL
        // order (different key - keyed CEP must not cross-match)
        events.add(CdcEventFixtures.spec("c", T0 + 11000, "tx-d1", "dave", "0", "SEA", "LOGIN"));
        events.add(CdcEventFixtures.spec("c", T0 + 11500, "tx-g1", "gina", "2000", "NYC", "PURCHASE"));
        events.add(CdcEventFixtures.spec("c", T0 + 12000, "tx-d2", "dave", "0", "SEA", "CHANGE_PASSWORD"));
        events.add(CdcEventFixtures.spec("c", T0 + 12500, "tx-g2", "gina", "90", "NYC", "PURCHASE"));
        events.add(CdcEventFixtures.spec("c", T0 + 13000, "tx-d3", "dave", "3000", "SEA", "WITHDRAWAL"));
        events.add(CdcEventFixtures.spec("c", T0 + 13500, "tx-g3", "gina", "2000", "NYC", "PURCHASE"));
        // erin geo anomaly (NYC then LA — the second event's prevCity = NYC != LA)
        events.add(CdcEventFixtures.spec("c", T0 + 15000, "tx-e2", "erin", "200", "NYC", "PURCHASE"));
        events.add(CdcEventFixtures.spec("c", T0 + 16000, "tx-e1", "erin", "150", "LA", "PURCHASE"));
        // LATE event (A1-2): heidi 2500@5s arrives LAST, after the watermark
        // (max seen 16000 - delay 2000 = 14000) passed W0's end (10000):
        // the alert may fire in CEP but the W0 window is closed -> no output row.
        events.add(CdcEventFixtures.spec("c", T0 + 5000, "tx-h4", "heidi", "2500", "NYC", "PURCHASE"));

        // trailing watermark-flush events: advance the watermark past W1's end
        // mid-run so W0/W1 windows fire, and KEEP EMITTING afterwards — each
        // collect drains the barrier-control mailbox, so the periodic checkpoints
        // that COMMIT the fired rows complete before the bounded source ends
        // (a commit that only starts during the finish linger races the
        // coordinator shutdown).
        events.add(CdcEventFixtures.spec("c", T0 + 25000, "tx-f3", "frank", "50", "NYC", "PURCHASE"));
        for (int i = 0; i < 10; i++) {
            events.add(CdcEventFixtures.spec("c", T0 + 26000L + i * 1000L,
                    "tx-f4-" + i, "frank", "60", "NYC", "PURCHASE"));
        }
        return events;
    }

    private void runOnce() throws Exception {
        ReplayableCdcSourceFunction source = replaySource(
                "fraud-s1-e2e", fullFixture(), 25L, 600L);
        io.nop.stream.flow.builder.InMemoryBeanFunctionResolver resolver =
                s1Resolver(source, jdbcTemplate);
        // The H2 database is class-static; each run must start from empty tables
        // (the previous run's rows share the same primary keys and would violate
        // the insert-only sink contract on re-run). The resolver construction
        // above already ensured the ledger tables exist.
        ScenarioTestSupport.execute(jdbcTemplate, "DELETE FROM " + ScenarioTestSupport.ALERT_TABLE);
        for (String ledger : new String[]{"fraud_ledger_rapid", "fraud_ledger_unusual",
                "fraud_ledger_geo", "fraud_ledger_takeover"}) {
            ScenarioTestSupport.execute(jdbcTemplate, "DELETE FROM " + ledger);
        }
        // unique storage per runOnce() invocation: each test method's run must
        // start fresh (a shared path would restore the previous run's manifest,
        // including its watermark — early events would be late-dropped)
        StreamExecutionEnvironment env = buildEnv(
                parseStreamXml(ScenarioTestSupport.S1_STREAM_PATH),
                resolver,
                tempDir.resolve("checkpoints-" + System.nanoTime()).toString(), null);
        env.execute("fraud-s1-e2e");
    }

    @Test
    public void s1PipelineProducesExactExpectedAlertRows() throws Exception {
        runOnce();

        Set<AlertSummaryRow> actual = readAlertRows(jdbcTemplate);

        // Pre-computed expectations (see fixture comments):
        // A1-3 exact alert set + zero false positives for noise/control users
        Set<AlertSummaryRow> expected = new LinkedHashSet<>();
        expected.add(alertRow(T0 + 2000, "alice", PATTERN_RAPID, 1, "2700"));     // A1-1: out-of-order pair, still W0
        expected.add(alertRow(T0 + 6000, "bob", PATTERN_UNUSUAL, 1, "2500"));     // A1-4: real per-user avg
        expected.add(alertRow(T0 + 13000, "dave", PATTERN_TAKEOVER, 1, "3000"));
        expected.add(alertRow(T0 + 16000, "erin", PATTERN_GEO, 1, "350"));

        assertTrue(sameRows(actual, expected),
                "S1 sink table must equal the exact expected alert set.\nexpected=" + expected
                        + "\nactual=" + actual);
        // A1-2: the late heidi event produced no row; carol's control txn produced
        // no row (no fixed-$100 stub behaviour). Exact set equality already pins
        // both; assert the negative explicitly for diagnosis clarity.
        for (AlertSummaryRow row : actual) {
            assertFalse("heidi".equals(row.getUserId()) || "carol".equals(row.getUserId())
                            || "gina".equals(row.getUserId()) || "frank".equals(row.getUserId()),
                    "noise/control user must not appear in the alert table: " + row);
        }
    }

    @Test
    public void s1ExactlyOnceLedgerAndPrimaryKeyShapeHolds() throws Exception {
        runOnce();

        // A1-5: PK dims have no duplicates (guaranteed by the H2 PK + insert-only
        // sink; the run completing without PK violation is the assertion).
        Set<AlertSummaryRow> rows = readAlertRows(jdbcTemplate);
        assertEquals(4, rows.size(), "exactly 4 alert rows (one per fired pattern)");

        // every chain's ledger recorded its committed epochs
        assertTrue(readLedgerRows(jdbcTemplate, "fraud_ledger_rapid").size() >= 1,
                "rapid chain ledger must contain committed epochs");
        assertTrue(readLedgerRows(jdbcTemplate, "fraud_ledger_unusual").size() >= 1,
                "unusual chain ledger must contain committed epochs");
        assertTrue(readLedgerRows(jdbcTemplate, "fraud_ledger_geo").size() >= 1,
                "geo chain ledger must contain committed epochs");
        assertTrue(readLedgerRows(jdbcTemplate, "fraud_ledger_takeover").size() >= 1,
                "takeover chain ledger must contain committed epochs");
    }

    @Test
    public void s1PipelineIsXdslDrivenNotJavaAssembled() {
        // Gap A wiring proof: the pipeline above executes from the XDSL model —
        // the declarative definition contains all four CEP chains and the
        // checkpoint declaration, and build() consumes only the parsed model.
        var model = parseStreamXml(ScenarioTestSupport.S1_STREAM_PATH);
        assertEquals(26, model.getTransforms().size(),
                "S1 XDSL declares 26 transforms (shared prefix + 4 x 5-element CEP chains)");
        assertEquals(4, model.getPatterns().size(), "S1 XDSL declares all 4 fraud patterns");
        assertTrue(model.getCheckpoint().isEnabled(), "S1 XDSL declares checkpointing enabled");
        assertEquals("STRICT_EXACTLY_ONCE",
                model.getCheckpoint().getProcessingGuarantee().name());
    }
}

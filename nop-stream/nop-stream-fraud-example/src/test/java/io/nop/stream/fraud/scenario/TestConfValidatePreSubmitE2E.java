/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.fraud.scenario;

import javax.sql.DataSource;

import java.nio.file.Files;
import java.nio.file.Path;

import io.nop.core.initialize.CoreInitialization;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.stream.flow.builder.InMemoryBeanFunctionResolver;
import io.nop.stream.runtime.maintain.StreamConfValidateCommand;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static io.nop.stream.fraud.scenario.ScenarioTestSupport.newH2DataSource;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.newJdbcTemplate;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.replaySource;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.s1Resolver;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.s2Resolver;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Item 20 (P-REQ-14) end-to-end: the conf-validate command entry validates the REAL
 * composite-scenario XDSL topologies WITHOUT starting any job. The beans exist only
 * in test-side programmatic registration (the exact S1/S2 scenario shape), so this is
 * the D2 form-1 (programmatic resolver) proof: command entry → resolver-provided
 * beans → real stream.xml parse + full graph construction → exit code verdict.
 */
public class TestConfValidatePreSubmitE2E {

    private static IJdbcTemplate jdbcTemplate;

    @TempDir
    Path tempDir;

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
        DataSource ds = newH2DataSource("conf-validate-s1-e2e");
        jdbcTemplate = newJdbcTemplate(ds);
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void s1TopologyValidatesWithoutStartingTheJob() {
        InMemoryBeanFunctionResolver resolver = s1Resolver(
                replaySource("conf-validate-s1", ScenarioTestSupport.s1FullFixture(), 0L, 0L),
                jdbcTemplate);
        int exit = StreamConfValidateCommand.run(
                java.util.List.of("file=/nop/stream/demo/fraud-s1-cdc.stream.xml"), resolver);
        assertEquals(0, exit, "S1 topology must pass layer 1+2 validation without execution");
    }

    @Test
    public void s2TopologyValidatesWithoutStartingTheJob() throws Exception {
        Path inputDir = tempDir.resolve("s2-input");
        Path outputDir = tempDir.resolve("s2-output");
        Files.createDirectories(inputDir);
        InMemoryBeanFunctionResolver resolver = s2Resolver(
                inputDir.toString(), outputDir.toString());
        int exit = StreamConfValidateCommand.run(
                java.util.List.of("file=/nop/stream/demo/fraud-s2-file.stream.xml"), resolver);
        assertEquals(0, exit, "S2 topology must pass layer 1+2 validation without execution");
    }

    @Test
    public void s1TopologyWithMissingBeanFailsNamingTheBean() {
        InMemoryBeanFunctionResolver resolver = s1Resolver(
                replaySource("conf-validate-s1-bad", ScenarioTestSupport.s1FullFixture(), 0L, 0L),
                jdbcTemplate);
        // Drop one sink bean: layer 2 must fail fast naming it (option name contract).
        InMemoryBeanFunctionResolver partial = new InMemoryBeanFunctionResolver();
        resolver.getBeans().forEach((name, bean) -> {
            if (!"jdbcSinkGeo".equals(name)) {
                partial.register(name, bean);
            }
        });
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        java.io.PrintStream original = System.out;
        System.setOut(new java.io.PrintStream(out, true, java.nio.charset.StandardCharsets.UTF_8));
        int exit;
        try {
            exit = StreamConfValidateCommand.run(
                    java.util.List.of("file=/nop/stream/demo/fraud-s1-cdc.stream.xml"), partial);
        } finally {
            System.setOut(original);
        }
        String report = out.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(1, exit, "missing bean must fail validation with exit 1");
        assertTrue(report.contains("jdbcSinkGeo"),
                "report must name the missing bean (option name): " + report);
    }

    /**
     * Item 29 (Phase 3) CLI integration: a bad model's conf-validate report carries
     * the failing declaration's file:line anchor (layer-2 build error anchored on
     * the offending edge element).
     */
    @Test
    public void badEdgeReportCarriesFileLineAnchor() {
        InMemoryBeanFunctionResolver resolver = new InMemoryBeanFunctionResolver();
        resolver.register("cdcSource", replaySource("conf-validate-bad-edge",
                ScenarioTestSupport.s1FullFixture(), 0L, 0L));
        resolver.register("jdbcSinkRapid", new io.nop.stream.core.common.functions.SinkFunction<Object>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void consume(Object value) {
            }
        });

        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        java.io.PrintStream original = System.out;
        System.setOut(new java.io.PrintStream(out, true, java.nio.charset.StandardCharsets.UTF_8));
        int exit;
        try {
            exit = StreamConfValidateCommand.run(
                    java.util.List.of("file=/nop/stream/test/fraud-conf-validate-bad-edge.stream.xml"),
                    resolver);
        } finally {
            System.setOut(original);
        }
        String report = out.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(1, exit, "unknown edge endpoint must fail validation with exit 1: " + report);
        assertTrue(report.contains("nop.err.stream.ref-unknown"),
                "report must carry the error code: " + report);
        assertTrue(report.contains("fraud-conf-validate-bad-edge.stream.xml"),
                "report must carry the failing file: " + report);
        assertTrue(report.contains("fraud-conf-validate-bad-edge.stream.xml:17"),
                "report must render the file:line anchor of the offending edge (line 17): " + report);
    }

    // ------------------------------------------------------------------
    // Phase 3: dry-run as the pre-submit step (composite-scenario-design §3.2)
    // ------------------------------------------------------------------

    @Test
    public void s1DryRunProbesAllEndpointsAsPreSubmitStep() {
        InMemoryBeanFunctionResolver resolver = s1Resolver(
                replaySource("conf-validate-s1-dry", ScenarioTestSupport.s1FullFixture(), 0L, 0L),
                jdbcTemplate);
        for (String ledger : new String[]{"fraud_ledger_rapid", "fraud_ledger_unusual",
                "fraud_ledger_geo", "fraud_ledger_takeover"}) {
            ScenarioTestSupport.execute(jdbcTemplate, "DELETE FROM " + ledger);
        }
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        java.io.PrintStream original = System.out;
        System.setOut(new java.io.PrintStream(out, true, java.nio.charset.StandardCharsets.UTF_8));
        int exit;
        try {
            exit = StreamConfValidateCommand.run(
                    java.util.List.of("file=/nop/stream/demo/fraud-s1-cdc.stream.xml", "--connect"),
                    resolver);
        } finally {
            System.setOut(original);
        }
        String report = out.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(0, exit, "valid S1 topology must pass dry-run (skips allowed): " + report);

        // The 4 JDBC 2PC sinks (jdbcSinkRapid/Unusual/Geo/Takeover) implement
        // ConnectivityCheckable → PASS items (H-5 wiring through the command entry).
        for (String bean : new String[]{"jdbcSinkRapid", "jdbcSinkUnusual", "jdbcSinkGeo",
                "jdbcSinkTakeover"}) {
            assertTrue(report.contains("bean " + bean + "): PASS"),
                    "sink " + bean + " must report PASS: " + report);
        }
        // The CDC source bean (ReplayableCdcSourceFunction extends DebeziumCdcSourceFunction)
        // implements ConnectivityCheckable → parameter-level PASS (D3-⑤: name validation,
        // no engine start — its config carries a valid connector name).
        assertTrue(report.contains("bean cdcSource): PASS"),
                "cdc source must pass the debezium parameter-level probe: " + report);

        // Residue red line (D3-⑥): dry-run must not leave ledger ROWS. The ledger
        // TABLES are the exempt idempotent objects; rows would be residue.
        for (String ledger : new String[]{"fraud_ledger_rapid", "fraud_ledger_unusual",
                "fraud_ledger_geo", "fraud_ledger_takeover"}) {
            assertEquals(0, ScenarioTestSupport.readLedgerRows(jdbcTemplate, ledger).size(),
                    "dry-run must not leave rows in " + ledger);
        }
    }

    @Test
    public void s1DryRunWithUnreachableSinkFailsExplicitly() {
        InMemoryBeanFunctionResolver resolver = s1Resolver(
                replaySource("conf-validate-s1-dead", ScenarioTestSupport.s1FullFixture(), 0L, 0L),
                jdbcTemplate);
        // Replace one sink with a construction-valid but connection-dead 2PC sink:
        // the template is built against a live H2 (JdbcFactory eagerly resolves the
        // dialect), then the pool is closed so every probe connection attempt fails.
        // Construction (layer 2) passes; the probe (layer 3) must fail with the
        // explicit connectivity error code naming the endpoint.
        com.zaxxer.hikari.HikariDataSource deadPool = new com.zaxxer.hikari.HikariDataSource();
        deadPool.setDriverClassName("org.h2.Driver");
        deadPool.setJdbcUrl("jdbc:h2:mem:conf-validate-dead;MODE=MySQL;DB_CLOSE_DELAY=-1");
        IJdbcTemplate deadTemplate = ScenarioTestSupport.newJdbcTemplate(deadPool);
        deadPool.close();

        io.nop.stream.connector.jdbc.JdbcTwoPhaseCommitSink<AlertSummaryRow> deadSink =
                io.nop.stream.connector.jdbc.JdbcTwoPhaseCommitSink
                        .<AlertSummaryRow>builder()
                        .jdbcTemplate(deadTemplate)
                        .tableName(ScenarioTestSupport.ALERT_TABLE)
                        .ledgerTableName("fraud_ledger_rapid")
                        .columns("window_start", "window_end", "user_id", "pattern",
                                "alert_count", "total_amount")
                        .recordMapper(row -> java.util.Map.of("user_id", row.getUserId()))
                        .build();
        resolver.register("jdbcSinkRapid", deadSink);

        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        java.io.PrintStream original = System.out;
        System.setOut(new java.io.PrintStream(out, true, java.nio.charset.StandardCharsets.UTF_8));
        int exit;
        try {
            exit = StreamConfValidateCommand.run(
                    java.util.List.of("file=/nop/stream/demo/fraud-s1-cdc.stream.xml", "--connect"),
                    resolver);
        } finally {
            System.setOut(original);
        }
        String report = out.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(1, exit, "unreachable sink must fail dry-run: " + report);
        assertTrue(report.contains("nop.err.stream.connectivity-check-failed"),
                "report must carry the explicit error code: " + report);
        assertTrue(report.contains("jdbcSinkRapid"),
                "report must name the failing endpoint bean: " + report);
    }
}

/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector.jdbc;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;

import io.nop.commons.util.StringHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.jdbc.impl.JdbcFactory;
import io.nop.stream.core.connector.ConnectivityProbeOutcome;
import io.nop.stream.core.connector.StreamConnectivityProber;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Item 20 (P-REQ-13, D3-①) jdbc-2pc probe tests: the adjudicated
 * beginTransaction + initializeLedgerTable + rollback combination. The residue red
 * line (D3-⑥) is asserted directly: the idempotent ledger TABLE is the exempt
 * expected object, but ZERO ledger ROWS may exist after probing; an unreachable
 * database surfaces the explicit typed error code, never a silent pass.
 */
public class TestJdbcPreSubmitConnectivityProbe {

    private static final String LEDGER = "probe_ledger";

    private HikariDataSource dataSource;
    private IJdbcTemplate jdbcTemplate;

    @BeforeAll
    static void initCore() {
        CoreInitialization.initialize();
    }

    @AfterAll
    static void destroyCore() {
        CoreInitialization.destroy();
    }

    @BeforeEach
    void setUp() {
        dataSource = new HikariDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setJdbcUrl("jdbc:h2:mem:" + getClass().getSimpleName() + StringHelper.generateUUID()
                + ";MODE=MySQL");
        jdbcTemplate = JdbcFactory.newJdbcTemplateFor(dataSource);
    }

    @AfterEach
    void tearDown() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    private JdbcTwoPhaseCommitSink<Object> newSink() {
        return JdbcTwoPhaseCommitSink.<Object>builder()
                .jdbcTemplate(jdbcTemplate)
                .tableName("probe_data")
                .ledgerTableName(LEDGER)
                .columns("id", "name")
                .recordMapper(row -> java.util.Map.of("id", row))
                .build();
    }

    @Test
    public void probeCreatesExemptLedgerTableWithoutAnyRow() throws Exception {
        JdbcTwoPhaseCommitSink<Object> sink = newSink();
        ConnectivityProbeOutcome outcome = StreamConnectivityProber.probe("jdbc-2pc-sink", sink);
        assertEquals(ConnectivityProbeOutcome.Status.PASS, outcome.getStatus(), () -> String.valueOf(outcome));

        // Exempt idempotent object: the ledger table now exists (the job's own requirement).
        assertTrue(tableExists(LEDGER), "ledger table is the exempt idempotent object");

        // Red line: no ledger ROW was written (probe never commits).
        assertEquals(0, countRows(LEDGER), "probe must not leave ledger rows (residue red line)");
        // And no data rows either.
        if (tableExists("probe_data")) {
            assertEquals(0, countRows("probe_data"));
        }
    }

    @Test
    public void probeIsRepeatableWithoutResidue() throws Exception {
        JdbcTwoPhaseCommitSink<Object> sink = newSink();
        for (int i = 0; i < 3; i++) {
            ConnectivityProbeOutcome outcome = StreamConnectivityProber.probe("jdbc-2pc-sink", sink);
            assertEquals(ConnectivityProbeOutcome.Status.PASS, outcome.getStatus());
        }
        assertEquals(0, countRows(LEDGER), "repeated probes must not accumulate ledger rows");
    }

    @Test
    public void unreachableDatabaseFailsWithExplicitErrorCode() {
        // Unreachable database simulated by closing the pool AFTER the template was
        // built (JdbcFactory.newJdbcTemplateFor eagerly opens one connection for
        // dialect detection, so the datasource must be alive at construction): every
        // subsequent physical connection attempt from the probe fails.
        HikariDataSource dead = new HikariDataSource();
        dead.setDriverClassName("org.h2.Driver");
        dead.setJdbcUrl("jdbc:h2:mem:" + getClass().getSimpleName() + StringHelper.generateUUID()
                + ";MODE=MySQL");
        IJdbcTemplate deadTemplate = JdbcFactory.newJdbcTemplateFor(dead);
        dead.close();

        JdbcTwoPhaseCommitSink<Object> sink = JdbcTwoPhaseCommitSink.<Object>builder()
                .jdbcTemplate(deadTemplate)
                .tableName("probe_data")
                .ledgerTableName(LEDGER)
                .columns("id", "name")
                .recordMapper(row -> java.util.Map.of("id", row))
                .build();
        ConnectivityProbeOutcome outcome = StreamConnectivityProber.probe("jdbc-2pc-sink", sink);
        assertEquals(ConnectivityProbeOutcome.Status.FAIL, outcome.getStatus(), () -> String.valueOf(outcome));
        assertEquals("nop.err.stream.connectivity-check-failed", outcome.getErrorCode());
        // Direct checkConnection must throw (fail-fast), not return silently.
        assertThrows(Exception.class, sink::checkConnection);
    }

    private boolean tableExists(String table) throws Exception {
        try (Connection conn = jdbcTemplate.openConnection("");
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = '"
                             + table.toUpperCase() + "'")) {
            rs.next();
            return rs.getLong(1) > 0;
        }
    }

    private long countRows(String table) throws Exception {
        try (Connection conn = jdbcTemplate.openConnection("");
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getLong(1);
        }
    }
}

/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.stream.runtime.cluster;

import io.nop.commons.util.StringHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.jdbc.impl.JdbcFactory;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import io.nop.stream.runtime.ops.OpsJobManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2510 C1-006 / C10-020 (plan 2510 裁定⑦, JDBC plane): the form-A periodic
 * cleanup on {@code nop_stream_task_assignment} uses the R01 combined
 * predicate — terminal whitelist (in-process terminalAt) OR assigned_at TTL
 * fallback, with the active-job-set exclusion as the hard protection — and the
 * terminal transition path deletes by job_id. Active jobs' attempt rows are
 * never touched (+1 pin / monotonic preservation).
 */
class TestJdbcClusterRegistryAssignmentRetention {

    private static HikariDataSource dataSource;
    private IJdbcTemplate jdbcTemplate;
    private JdbcClusterRegistry registry;

    @BeforeAll
    static void initAll() {
        CoreInitialization.initialize();
        dataSource = new HikariDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setJdbcUrl("jdbc:h2:mem:" + StringHelper.generateUUID() + ";MODE=MySQL");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        dataSource.setMaximumPoolSize(4);
    }

    @AfterAll
    static void destroyAll() {
        if (dataSource != null) {
            dataSource.close();
        }
        CoreInitialization.destroy();
    }

    @BeforeEach
    void setUp() {
        JdbcFactory factory = new JdbcFactory();
        jdbcTemplate = factory.newJdbcTemplate(factory.newTransactionTemplate(dataSource));
        dropSilently("nop_stream_task_assignment");
        registry = new JdbcClusterRegistry(jdbcTemplate);
        // ensureTables via first assignTask
        registry.assignTask("warmup", "v", 0, "n", "a", 1L, 1);
    }

    @AfterEach
    void tearDown() {
        dropSilently("nop_stream_task_assignment");
    }

    private void dropSilently(String table) {
        try {
            jdbcTemplate.executeUpdate(io.nop.core.lang.sql.SQL.begin()
                    .sql("DROP TABLE IF EXISTS " + table).end());
        } catch (Exception ignored) {
            // best-effort cleanup
        }
    }

    private int countRows(String jobId) {
        return jdbcTemplate.executeQuery(io.nop.core.lang.sql.SQL.begin()
                .sql("SELECT COUNT(*) FROM nop_stream_task_assignment WHERE job_id = ?", jobId)
                .end(), dataSet -> dataSet.next().getInt(0));
    }

    @Test
    void terminalPathRemovesAllRowsForJob() {
        registry.assignTask("job-term", "source", 0, "n1", "a1", 1L, 1);
        registry.assignTask("job-term", "source", 0, "n1", "a2", 1L, 2);
        registry.assignTask("job-term", "sink", 0, "n1", "a1", 1L, 1);
        assertEquals(3, countRows("job-term"));

        registry.removeJobTaskAssignments("job-term");
        assertEquals(0, countRows("job-term"), "terminal job_id DELETE removes every attempt row");
    }

    /**
     * Rule-23 wiring (C1-006 form A, R01 anti-hollow): the periodic assignment
     * cleanup is armed on the OpsJobManager governance lifecycle hook when the
     * registry is JDBC-backed, and the scheduled body executes against the live
     * JDBC registry.
     */
    @Test
    void opsFormAScheduleArmedOnGovernanceLifecycleHook(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir) {
        OpsJobManager jdbcManager = new OpsJobManager(
                new io.nop.message.core.local.LocalMessageService(), registry,
                new io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage(tempDir.toString()),
                java.util.Map.of());
        io.nop.stream.runtime.ledger.LedgerRetentionConfig config =
                io.nop.stream.runtime.ledger.LedgerRetentionConfig.assignmentFace();
        config.setRetentionDays(30);
        jdbcManager.setAssignmentRetentionConfig(config);
        try {
            assertFalse(jdbcManager.isAssignmentRetentionScheduled(), "not armed before startGovernance");
            jdbcManager.startGovernance();
            assertTrue(jdbcManager.isAssignmentRetentionScheduled(),
                    "form-A assignment cleanup must be armed on the OpsJobManager lifecycle hook "
                            + "(rule 23 wiring, R01 anti-hollow)");
            // The scheduled body executes against the live JDBC registry:
            // whitelist cleanup of the terminal job deletes its rows.
            registry.assignTask("job-sched-term", "source", 0, "n1", "a1", 1L, 1);
            jdbcManager.getTerminalAt().put("job-sched-term", System.currentTimeMillis());
            int deleted = jdbcManager.runAssignmentRetentionCleanupOnce();
            assertEquals(1, deleted, "scheduled body deletes whitelisted terminal-job rows");
        } finally {
            jdbcManager.close();
        }
    }

    @Test
    void formACleanupUsesCombinedPredicateWithActiveJobProtection() {
        long now = io.nop.api.core.time.CoreMetrics.currentTimeMillis();
        long old = now - 40L * 24 * 3600_000L; // beyond the 30d default retention

        // job-terminal: two attempts, recent — deletable via the terminal whitelist only.
        registry.assignTask("job-terminal", "source", 0, "n1", "a1", 1L, 1);
        // job-crash-orphan: old assigned_at, no whitelist entry (crash lost the map) —
        // deletable only via the assigned_at TTL fallback.
        jdbcTemplate.executeUpdate(io.nop.core.lang.sql.SQL.begin()
                .sql("INSERT INTO nop_stream_task_assignment (job_id, vertex_id, subtask_index, node_id, "
                        + "attempt_id, fencing_token, assigned_at, attempt_number) VALUES (?,?,?,?,?,?,?,?)",
                        "job-crash-orphan", "source", 0, "n1", "a1", "1", old, 1)
                .end());
        // job-active: old rows but the job is in the active set — hard-protected.
        jdbcTemplate.executeUpdate(io.nop.core.lang.sql.SQL.begin()
                .sql("INSERT INTO nop_stream_task_assignment (job_id, vertex_id, subtask_index, node_id, "
                        + "attempt_id, fencing_token, assigned_at, attempt_number) VALUES (?,?,?,?,?,?,?,?)",
                        "job-active", "source", 0, "n1", "a1", "1", old, 1)
                .end());
        // job-fresh: recent TTL, no whitelist — retained.
        registry.assignTask("job-fresh", "source", 0, "n1", "a1", 1L, 1);

        int deleted = registry.cleanupTaskAssignments(
                Set.of("job-terminal"),   // in-process terminal whitelist
                Set.of("job-active"),     // active-job hard protection
                old + 1_000,              // TTL cutoff: rows assigned before this are expired
                1000);

        assertEquals(2, deleted, "terminal whitelist row + TTL orphan row are deletable");
        assertEquals(0, countRows("job-terminal"));
        assertEquals(0, countRows("job-crash-orphan"), "crash orphan cleaned by the TTL fallback");
        assertEquals(1, countRows("job-active"), "active job rows are hard-protected");
        assertEquals(1, countRows("job-fresh"), "within-TTL rows are retained");

        // Bounded batches: delete-batch-size caps the rows touched per pass.
        for (int i = 1; i <= 5; i++) {
            registry.assignTask("job-batch", "v" + i, 0, "n1", "a1", 1L, 1);
        }
        int firstPass = registry.cleanupTaskAssignments(Set.of("job-batch"), Set.of(), 0, 3);
        assertEquals(3, firstPass, "a single pass respects the delete-batch-size bound");
        int secondPass = registry.cleanupTaskAssignments(Set.of("job-batch"), Set.of(), 0, 1000);
        assertEquals(2, secondPass, "the next pass drains the remainder");
        assertTrue(registry.cleanupTaskAssignments(Set.of(), Set.of(), 0, 1000) == 0,
                "empty predicate set = no-op pass");
    }
}

/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector.jdbc;

import com.zaxxer.hikari.HikariDataSource;

import io.nop.commons.util.StringHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.jdbc.impl.JdbcFactory;
import io.nop.stream.core.checkpoint.TaskLocation;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R5-CON-01 regression: the JDBC 2PC ledger guard key gains the deployment namespace
 * ({@code jobId|vertexId}, carried through {@code copyForSubtask(TaskLocation)}).
 *
 * <p>Before the fix the ledger key was {@code (epoch_id, subtask_id)} only, so two sink
 * branches of the SAME job (two vertices, same epoch numbering, subtask indexes both
 * starting at 0) sharing one database silently skipped the second branch's writes:
 * branch A's ledger row {@code (epoch, 0)} blocked branch B's commit — permanent silent
 * data loss with no ERROR in the job log.
 *
 * <p>Test scenarios (per plan 368 Phase 4):
 * <ol>
 *   <li>Two branches (different vertexIds, same jobId) committing the same
 *       epoch/subtask: both must write their full batch (no mutual blocking).</li>
 *   <li>Two jobs sharing the database: namespaces must differ by jobId.</li>
 *   <li>Same-version recovery path: a restart-equivalent copy rebuilt from the SAME
 *       TaskLocation must hit its own ledger row (idempotent re-commit skips data,
 *       no duplicate rows, pendingCommits drained).</li>
 * </ol>
 */
class TestJdbcTwoPhaseCommitLedgerNamespace {

    private static final String V2_LEDGER_TABLE = "stream_epoch_ledger_v2";

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
    void setUp() throws Exception {
        dataSource = new HikariDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setJdbcUrl("jdbc:h2:mem:" + getClass().getSimpleName() + StringHelper.generateUUID() + ";MODE=MySQL");
        jdbcTemplate = JdbcFactory.newJdbcTemplateFor(dataSource);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "CREATE TABLE target_data (id BIGINT, name VARCHAR(100), amount BIGINT)")) {
            ps.execute();
        }
    }

    @AfterEach
    void tearDown() {
        dataSource.close();
    }

    private JdbcTwoPhaseCommitSink<Map<String, Object>> createTemplate() {
        return JdbcTwoPhaseCommitSink.<Map<String, Object>>builder()
                .jdbcTemplate(jdbcTemplate)
                .tableName("target_data")
                .columns("id", "name", "amount")
                .recordMapper(Function.identity())
                .build();
    }

    private Map<String, Object> row(long id, String name, long amount) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("amount", amount);
        return m;
    }

    private int countDataRows() throws Exception {
        return countRows("target_data");
    }

    private int countLedgerRows() throws Exception {
        return countRows(V2_LEDGER_TABLE);
    }

    private int countRows(String tableName) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM " + tableName);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    /**
     * Audit R5-CON-01 scenario 1: one job, two jdbc-2pc sink branches (vertex
     * {@code sinkA} and vertex {@code sinkB}), one shared database, same epoch and
     * same subtask index. Both branches must commit their full batch — branch A's
     * ledger row must never block branch B.
     */
    @Test
    void testSameJobTwoSinkBranchesDoNotBlockEachOther() throws Exception {
        TaskLocation locA = new TaskLocation("job-1", "pipeline-0", "sinkA", 0);
        TaskLocation locB = new TaskLocation("job-1", "pipeline-0", "sinkB", 0);

        JdbcTwoPhaseCommitSink<Map<String, Object>> branchA = createTemplate().copyForSubtask(locA);
        JdbcTwoPhaseCommitSink<Map<String, Object>> branchB = createTemplate().copyForSubtask(locB);

        assertEquals("job-1|sinkA", branchA.getLedgerNamespace());
        assertEquals("job-1|sinkB", branchB.getLedgerNamespace());

        branchA.beginTransaction();
        branchB.beginTransaction();
        branchA.initializeLedgerTable();

        branchA.consume(row(1L, "a", 100L));
        branchA.consume(row(2L, "a", 101L));
        branchB.consume(row(11L, "b", 200L));
        branchB.consume(row(12L, "b", 201L));

        branchA.saveState(1L);
        branchA.preCommit(1L);
        branchB.saveState(1L);
        branchB.preCommit(1L);

        branchA.commit(1L);
        // Before the fix, this commit was skipped by branch A's ledger row (1,0).
        branchB.commit(1L);

        assertEquals(4, countDataRows(),
                "both branches' records must be committed — the second branch's write "
                        + "must not be silently skipped by the first branch's ledger row");
        assertEquals(2, countLedgerRows(),
                "one ledger row per branch for the same epoch (namespace disambiguates)");
        assertTrue(branchA.getPendingCommits().isEmpty());
        assertTrue(branchB.getPendingCommits().isEmpty());
    }

    /**
     * Audit R5-CON-01 scenario 2: two different jobs sharing one database must not
     * collide — the namespace carries the jobId.
     */
    @Test
    void testCrossJobNamespacesDiffer() throws Exception {
        TaskLocation locJob1 = new TaskLocation("job-1", "pipeline-0", "sink-0", 0);
        TaskLocation locJob2 = new TaskLocation("job-2", "pipeline-0", "sink-0", 0);

        JdbcTwoPhaseCommitSink<Map<String, Object>> job1Sink = createTemplate().copyForSubtask(locJob1);
        JdbcTwoPhaseCommitSink<Map<String, Object>> job2Sink = createTemplate().copyForSubtask(locJob2);

        assertEquals("job-1|sink-0", job1Sink.getLedgerNamespace());
        assertEquals("job-2|sink-0", job2Sink.getLedgerNamespace());

        job1Sink.beginTransaction();
        job2Sink.beginTransaction();
        job1Sink.initializeLedgerTable();

        job1Sink.consume(row(1L, "j1", 1L));
        job2Sink.consume(row(2L, "j2", 2L));
        job1Sink.saveState(1L);
        job2Sink.saveState(1L);
        job1Sink.commit(1L);
        job2Sink.commit(1L);

        assertEquals(2, countDataRows(), "both jobs' records must be committed");
        assertEquals(2, countLedgerRows(), "one ledger row per job (namespace carries jobId)");
    }

    /**
     * Same-version recovery self-consistency: a sink copy rebuilt for the SAME vertex
     * and task index after a region restart (same TaskLocation → same namespace) hits
     * its own ledger row on re-commit, so the recovery path is idempotent — no duplicate
     * data rows, and the guard logs carry the full key context.
     */
    @Test
    void testSameVersionRestorePathIsSelfConsistent() throws Exception {
        TaskLocation loc = new TaskLocation("job-1", "pipeline-0", "sinkA", 0);

        JdbcTwoPhaseCommitSink<Map<String, Object>> original = createTemplate().copyForSubtask(loc);
        original.beginTransaction();
        original.initializeLedgerTable();
        original.consume(row(1L, "a", 100L));
        original.saveState(1L);
        original.preCommit(1L);
        original.commit(1L);
        assertEquals(1, countDataRows());

        // Region-restart equivalent: a fresh copy for the same TaskLocation (the
        // identity pipeline passes the same location on restart) re-commits the
        // durable-but-uncommitted epoch restored from the checkpoint.
        JdbcTwoPhaseCommitSink<Map<String, Object>> restarted = createTemplate().copyForSubtask(loc);
        restarted.beginTransaction();
        assertNotNull(restarted.getLedgerNamespace());
        restarted.getPendingCommits().put(1L, List.of(row(1L, "a", 100L)));
        restarted.commit(1L);

        assertEquals(1, countDataRows(),
                "idempotent re-commit after restart must not duplicate data rows");
        assertEquals(1, countLedgerRows(), "re-commit must not add a new ledger row");
        assertTrue(restarted.getPendingCommits().isEmpty(),
                "guard-hit path must drain the pending entry like the normal commit path");
    }

    /**
     * The default ledger table is versioned: fresh deployments create the v2 table and
     * the pre-v2 3-column table is neither migrated nor read.
     */
    @Test
    void testDefaultLedgerTableNameIsVersioned() throws Exception {
        JdbcTwoPhaseCommitSink<Map<String, Object>> sink = createTemplate();
        String ddl = sink.getLedgerTableDDL();
        assertTrue(ddl.contains("stream_epoch_ledger_v2"),
                "default DDL must target the versioned v2 ledger table");
        assertTrue(ddl.contains("sink_namespace"), "v2 DDL must include the namespace column");

        sink.beginTransaction();
        sink.initializeLedgerTable();
        assertEquals(0, countLedgerRows(), "v2 table must be created fresh (old table untouched)");
    }
}

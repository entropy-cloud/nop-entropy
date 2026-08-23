/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector.jdbc;

import com.zaxxer.hikari.HikariDataSource;

import io.nop.commons.util.StringHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.jdbc.impl.JdbcFactory;

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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P0 regression (parallelism &gt; 1 batch loss): the runtime deep-copies the operator
 * chain per subtask ({@code OperatorChain.deepCopy(taskIndex)}). The two copies below
 * simulate the deepCopy products for subtask 0 and subtask 1. Each copy must buffer,
 * snapshot and commit its own batch for the SAME epoch without the other copy
 * overwriting it (a shared pendingCommits map previously left only the last-written
 * subtask's batch committed — exactly-once broken), and the ledger idempotent-commit
 * guard must be keyed per subtask so both same-epoch commits succeed.
 */
class TestJdbcTwoPhaseCommitSinkParallelIsolation {

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

    @Test
    void testParallelSubtaskCopiesCommitIndependently() throws Exception {
        JdbcTwoPhaseCommitSink<Map<String, Object>> template = createTemplate();
        JdbcTwoPhaseCommitSink<Map<String, Object>> subtask0 = template.copyForSubtask(0);
        JdbcTwoPhaseCommitSink<Map<String, Object>> subtask1 = template.copyForSubtask(1);
        assertNotSame(subtask0, subtask1, "each subtask must get an independent sink copy");
        assertEquals(0, subtask0.getSubtaskIndex());
        assertEquals(1, subtask1.getSubtaskIndex());

        subtask0.beginTransaction();
        subtask1.beginTransaction();
        subtask0.initializeLedgerTable();

        subtask0.consume(row(1L, "s0-a", 100L));
        subtask0.consume(row(2L, "s0-b", 101L));
        subtask1.consume(row(101L, "s1-a", 200L));
        subtask1.consume(row(102L, "s1-b", 201L));
        subtask1.consume(row(103L, "s1-c", 202L));

        // Both subtasks receive the SAME barrier epoch; each saveState must land in the
        // copy's OWN pendingCommits map with its OWN batch (no mutual overwrite).
        subtask0.saveState(1L);
        subtask0.preCommit(1L);
        subtask1.saveState(1L);
        subtask1.preCommit(1L);

        Object pending0 = subtask0.getPendingCommits().get(1L);
        Object pending1 = subtask1.getPendingCommits().get(1L);
        assertTrue(pending0 instanceof List, "subtask 0's pending batch must survive subtask 1's saveState");
        assertTrue(pending1 instanceof List, "subtask 1's pending batch must exist");
        assertNotSame(pending0, pending1);
        assertEquals(2, ((List<?>) pending0).size());
        assertEquals(3, ((List<?>) pending1).size());

        assertEquals(0, countRows("target_data"), "no data before commit");

        // Commit from both subtasks: every record must be committed, none dropped by
        // the ledger guard (the guard key includes the subtask id).
        subtask0.commit(1L);
        subtask1.commit(1L);

        assertEquals(5, countRows("target_data"),
                "all records from both subtasks must be committed — no loss, no overwrite");
        assertEquals(2, countRows("stream_epoch_ledger"),
                "one ledger row per subtask for the same epoch (composite PK epoch_id+subtask_id)");
        assertTrue(subtask0.getPendingCommits().isEmpty());
        assertTrue(subtask1.getPendingCommits().isEmpty());
    }

    @Test
    void testLedgerGuardIsPerSubtask() throws Exception {
        JdbcTwoPhaseCommitSink<Map<String, Object>> template = createTemplate();
        JdbcTwoPhaseCommitSink<Map<String, Object>> subtask0 = template.copyForSubtask(0);
        JdbcTwoPhaseCommitSink<Map<String, Object>> subtask1 = template.copyForSubtask(1);

        subtask0.beginTransaction();
        subtask1.beginTransaction();
        subtask0.initializeLedgerTable();

        subtask0.consume(row(1L, "s0-a", 100L));
        subtask1.consume(row(101L, "s1-a", 200L));
        subtask0.saveState(1L);
        subtask1.saveState(1L);
        subtask0.commit(1L);
        subtask1.commit(1L);

        // Simulate a durable-but-uncommitted recovery re-commit for subtask 1: the
        // ledger guard must skip ONLY subtask 1's own row (not subtask 0's data).
        subtask1.getPendingCommits().put(1L, List.of(row(101L, "s1-a", 200L)));
        subtask1.commit(1L);

        assertEquals(2, countRows("target_data"),
                "idempotent re-commit must not duplicate data");
        assertEquals(2, countRows("stream_epoch_ledger"),
                "re-commit must not add a new ledger row");
    }

    // ---- Helpers ----

    private Map<String, Object> row(long id, String name, long amount) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("amount", amount);
        return m;
    }

    private int countRows(String tableName) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM " + tableName);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }
}

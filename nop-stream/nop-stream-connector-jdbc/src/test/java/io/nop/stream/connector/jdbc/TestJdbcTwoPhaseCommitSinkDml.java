/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.stream.connector.jdbc;

import io.nop.commons.util.StringHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.message.debezium.ChangeEvent;
import io.nop.message.debezium.ChangeEventMetadata;
import io.nop.stream.core.common.functions.sink.SinkConsistencyCapability;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.jdbc.impl.JdbcFactory;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI5（ai-dev/backlog/nop-stream-cdc-roadmap.md）：jdbc-2pc dmlMode——
 * UPSERT / UPSERT_DELETE 的键控落库、批内同 key 折叠、ChangeEvent 原生路径与
 * recordMapper+opMapper 泛型路径、模式守卫（INSERT 拒 u/d、UPSERT 拒 d）、
 * 2PC epoch 提交与 ledger 幂等复用。
 */
class TestJdbcTwoPhaseCommitSinkDml {

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
        dataSource.setJdbcUrl("jdbc:h2:mem:" + getClass().getSimpleName()
                + StringHelper.generateUUID() + ";MODE=MySQL");
        jdbcTemplate = JdbcFactory.newJdbcTemplateFor(dataSource);

        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "CREATE TABLE cdc_target (id BIGINT PRIMARY KEY, name VARCHAR(100))")) {
                ps.execute();
            }
        }
    }

    @AfterEach
    void tearDown() {
        dataSource.close();
    }

    // ---- helpers ----

    private static ChangeEventMetadata meta() {
        return new ChangeEventMetadata("mysql", "srv", "db", "schema", "cdc_target", null);
    }

    private static ChangeEvent event(String operation, Map<String, Object> after,
                                     Map<String, Object> key) {
        return new ChangeEvent(meta(), operation, null, after, key, 0L);
    }

    private static Map<String, Object> row(Object id, String name) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("name", name);
        return row;
    }

    private JdbcTwoPhaseCommitSink<ChangeEvent> newUpsertSink(
            JdbcTwoPhaseCommitSink.DmlMode mode) {
        JdbcTwoPhaseCommitSink<ChangeEvent> sink = JdbcTwoPhaseCommitSink.<ChangeEvent>builder()
                .jdbcTemplate(jdbcTemplate)
                .tableName("cdc_target")
                .dmlMode(mode)
                .keyColumns("id")
                .build();
        sink.initializeLedgerTable();
        return sink;
    }

    private int countRows(Object id) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT name FROM cdc_target WHERE id = ?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? 1 : 0;
            }
        }
    }

    private String nameOf(Object id) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT name FROM cdc_target WHERE id = ?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    // ---- capability surface ----

    @Test
    void testDmlModeGetterAndConsistency() {
        JdbcTwoPhaseCommitSink<ChangeEvent> sink = newUpsertSink(JdbcTwoPhaseCommitSink.DmlMode.UPSERT_DELETE);
        assertEquals(JdbcTwoPhaseCommitSink.DmlMode.UPSERT_DELETE, sink.getDmlMode());
        assertEquals(SinkConsistencyCapability.TWO_PHASE_COMMIT, sink.getSinkConsistency(),
                "keyed modes keep the 2PC exactly-once capability");
    }

    @Test
    void testKeyedModeRequiresKeyColumns() {
        assertThrows(StreamException.class, () -> JdbcTwoPhaseCommitSink.<ChangeEvent>builder()
                .jdbcTemplate(jdbcTemplate)
                .tableName("cdc_target")
                .dmlMode(JdbcTwoPhaseCommitSink.DmlMode.UPSERT)
                .build());
    }

    @Test
    void testInsertModeRejectsKeyColumns() {
        assertThrows(StreamException.class, () -> JdbcTwoPhaseCommitSink.<Map<String, Object>>builder()
                .jdbcTemplate(jdbcTemplate)
                .tableName("cdc_target")
                .columns("id", "name")
                .recordMapper(Function.identity())
                .keyColumns("id")
                .build());
    }

    // ---- mode guards ----

    @Test
    void testInsertModeRejectsUpdateDeleteEvents() {
        JdbcTwoPhaseCommitSink<ChangeEvent> sink = JdbcTwoPhaseCommitSink.<ChangeEvent>builder()
                .jdbcTemplate(jdbcTemplate)
                .tableName("cdc_target")
                .columns("id", "name")
                .recordMapper(ChangeEvent::getAfter)
                .build();
        assertThrows(StreamException.class, () -> sink.invoke(event("u", row(1, "x"), null)));
        assertThrows(StreamException.class, () -> sink.invoke(event("d", null, row(1, null))));
    }

    @Test
    void testUpsertModeRejectsDeleteEvents() {
        JdbcTwoPhaseCommitSink<ChangeEvent> sink = newUpsertSink(JdbcTwoPhaseCommitSink.DmlMode.UPSERT);
        assertThrows(StreamException.class, () -> sink.invoke(event("d", null, row(1, null))));
    }

    // ---- UPSERT / UPSERT_DELETE behavior over real JDBC ----

    @Test
    void testUpsertNativeChangeEventRoundTripWithDelete() throws Exception {
        JdbcTwoPhaseCommitSink<ChangeEvent> sink = newUpsertSink(JdbcTwoPhaseCommitSink.DmlMode.UPSERT_DELETE);

        // epoch 1: c(id=1,a) + u(id=1,b) 同 key 折叠 + c(id=2,c)
        sink.beginTransaction();
        sink.invoke(event("c", row(1, "a"), null));
        sink.invoke(event("u", row(1, "b"), null));
        sink.invoke(event("r", row(2, "c"), null)); // snapshot read 也是 upsert
        sink.saveState(1L);
        sink.preCommit(1L);
        sink.commit(1L);

        assertEquals(1, countRows(1), "same-key ops within one epoch must fold to one row");
        assertEquals("b", nameOf(1), "fold is last-write-wins");
        assertEquals(1, countRows(2));

        // epoch 2: d(id=1)
        sink.beginTransaction();
        sink.invoke(event("d", null, row(1, null)));
        sink.saveState(2L);
        sink.preCommit(2L);
        sink.commit(2L);

        assertEquals(0, countRows(1), "UPSERT_DELETE applies keyed deletes");
        assertEquals(1, countRows(2), "unrelated rows survive the delete");
    }

    @Test
    void testLedgerIdempotentRecommitWithUpsert() throws Exception {
        JdbcTwoPhaseCommitSink<ChangeEvent> sink = newUpsertSink(JdbcTwoPhaseCommitSink.DmlMode.UPSERT);
        sink.beginTransaction();
        sink.invoke(event("c", row(1, "a"), null));
        sink.saveState(7L);
        sink.commit(7L);
        assertEquals("a", nameOf(1));

        // 模拟恢复后的重提交：pendingCommits 重新携带 epoch 7 → ledger 命中 → 跳过写数据
        sink.getPendingCommits().put(7L, java.util.List.of(
                new JdbcTwoPhaseCommitSink.DmlOp(JdbcTwoPhaseCommitSink.DmlOp.KIND_UPSERT, row(1, "SHOULD-NOT-WRITE"))));
        sink.commit(7L);
        assertEquals("a", nameOf(1), "idempotent re-commit must not overwrite via the ledger guard");
    }

    @Test
    void testGenericRecordsWithOpAndDeleteKeyMappers() throws Exception {
        // 泛型记录路径：Map 携带 op 字段；recordMapper 提取值列；deleteKeyMapper 提供 key
        JdbcTwoPhaseCommitSink<Map<String, Object>> sink = JdbcTwoPhaseCommitSink
                .<Map<String, Object>>builder()
                .jdbcTemplate(jdbcTemplate)
                .tableName("cdc_target")
                .dmlMode(JdbcTwoPhaseCommitSink.DmlMode.UPSERT_DELETE)
                .keyColumns("id")
                .recordMapper(row -> {
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("id", row.get("id"));
                    out.put("name", row.get("name"));
                    return out;
                })
                .opMapper(row -> String.valueOf(row.get("op")))
                .deleteKeyMapper(row -> {
                    Map<String, Object> key = new LinkedHashMap<>();
                    key.put("id", row.get("id"));
                    return key;
                })
                .build();
        sink.initializeLedgerTable();
        sink.beginTransaction();
        Map<String, Object> insertRecord = new LinkedHashMap<>(row(5, "e"));
        insertRecord.put("op", "c");
        sink.invoke(insertRecord);
        sink.saveState(1L);
        sink.commit(1L);
        assertEquals("e", nameOf(5));

        sink.beginTransaction();
        Map<String, Object> deleteRecord = new LinkedHashMap<>();
        deleteRecord.put("id", 5);
        deleteRecord.put("op", "d");
        sink.invoke(deleteRecord);
        sink.saveState(2L);
        sink.commit(2L);
        assertEquals(0, countRows(5));
    }
}

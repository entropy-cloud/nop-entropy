/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector.jdbc;

import com.zaxxer.hikari.HikariDataSource;

import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.jdbc.impl.JdbcFactory;
import io.nop.stream.core.exceptions.NopStreamErrors;
import io.nop.stream.core.exceptions.StreamException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import io.nop.core.initialize.CoreInitialization;
import io.nop.dao.dialect.IDialect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA 8.43 R4-03 (plan 2536) red ④ + red ⑪ — W-04 (MA-13=C5-005, merged
 * C8-003/C5-010; adjudication C.1) and W-11 (C8-009; adjudication C.5) on the
 * JDBC 2PC sink.
 *
 * <p>red ④ legs (S5 seam = small-knob construction, zero product seams):
 * epoch-buffer overflow typed reject (cap-1 boundary passes), pendingCommits
 * stack overflow typed reject, non-positive knobs fail fast typed at
 * construction. The pre-fix red is the COMPILE face of this class (no knob
 * constructor/fluent surface existed), registered in
 * {@code _tmp/red-2536-GD-compile.log}; the same-file 2511 retention cleaner
 * and largeEpochIsCommittedInSegments zero-rewrite anchors keep running
 * unchanged.
 *
 * <p>red ⑪ legs (S11 seam = statement-recording stub via a JDK dynamic proxy —
 * zero mockito): every statement created inside the commit transaction
 * (ledgerExists probe + data inserts + ledger entry) receives
 * {@code setQueryTimeout} exactly once before execution; a simulated driver
 * timeout enforcement propagates typed out of {@code commit} (the
 * checkpointTimeout blind-zone closure — the completion-notification path has
 * no other bound).
 */
class TestJdbcSinkEnvelopeR403 {

    private static HikariDataSource dataSource;
    private static IJdbcTemplate jdbcTemplate;

    @BeforeAll
    static void initCore() {
        CoreInitialization.initialize();
        dataSource = new HikariDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setJdbcUrl("jdbc:h2:mem:" + TestJdbcSinkEnvelopeR403.class.getSimpleName()
                + ";MODE=MySQL");
        jdbcTemplate = JdbcFactory.newJdbcTemplateFor(dataSource);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "CREATE TABLE target_data (id BIGINT, name VARCHAR(100), amount BIGINT)")) {
            ps.execute();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @AfterAll
    static void destroyAll() {
        dataSource.close();
        CoreInitialization.destroy();
    }

    /** Wraps a FRESH pooled connection in a counting proxy (the sink closes its
     *  connection in commit's finally, so each openConnection must hand out a new
     *  one — a stale Hikari proxy throws "Connection is closed" on reuse). */
    private Connection recordingConnection(AtomicInteger timeoutCalls) {
        Connection real;
        try {
            real = dataSource.getConnection();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(), new Class[]{Connection.class},
                (proxy, method, args) -> {
                    try {
                        Object result = method.invoke(real, args);
                        // setQueryTimeout is called on the STATEMENT, so the
                        // counting proxy must wrap the statement that
                        // prepareStatement hands out.
                        if ("prepareStatement".equals(method.getName())
                                && result instanceof java.sql.Statement stmt) {
                            return java.sql.Statement.class.cast(Proxy.newProxyInstance(
                                    java.sql.Statement.class.getClassLoader(),
                                    new Class[]{PreparedStatement.class},
                                    (p2, m2, a2) -> {
                                        if ("setQueryTimeout".equals(m2.getName())) {
                                            timeoutCalls.incrementAndGet();
                                        }
                                        try {
                                            return m2.invoke(stmt, a2);
                                        } catch (InvocationTargetException e2) {
                                            throw e2.getCause();
                                        }
                                    }));
                        }
                        return result;
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }

    /** IJdbcTemplate stub whose openConnection hands out a fresh recording connection. */
    private IJdbcTemplate recordingTemplate(AtomicInteger timeoutCalls) {
        return (IJdbcTemplate) Proxy.newProxyInstance(
                IJdbcTemplate.class.getClassLoader(), new Class[]{IJdbcTemplate.class},
                (proxy, method, args) -> {
                    if ("openConnection".equals(method.getName())) {
                        return recordingConnection(timeoutCalls);
                    }
                    try {
                        return method.invoke(jdbcTemplate, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }

    private static Map<String, Object> row(long id) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", "n" + id);
        m.put("amount", id);
        return m;
    }

    private static JdbcTwoPhaseCommitSink<Map<String, Object>> newSink() {
        JdbcTwoPhaseCommitSink<Map<String, Object>> sink = JdbcTwoPhaseCommitSink
                .<Map<String, Object>>builder()
                .jdbcTemplate(jdbcTemplate)
                .tableName("target_data")
                .columns("id", "name", "amount")
                .recordMapper(m -> m)
                .build();
        sink.beginTransaction();
        sink.initializeLedgerTable();
        return sink;
    }

    // ------------------------------------------------------------------
    // red ④ — W-04 envelope knobs
    // ------------------------------------------------------------------

    @Test
    void epochBufferOverflowFailsTypedR403() throws Exception {
        JdbcTwoPhaseCommitSink<Map<String, Object>> sink = JdbcTwoPhaseCommitSink
                .<Map<String, Object>>builder()
                .jdbcTemplate(jdbcTemplate)
                .tableName("target_data")
                .columns("id", "name", "amount")
                .recordMapper(m -> m)
                .maxEpochRecords(3)
                .build();
        sink.beginTransaction();
        // cap-1 boundary: 3 records buffer normally.
        for (long i = 0; i < 3; i++) {
            sink.invoke(row(i));
        }
        // The (cap+1)-th record must be typed-rejected BEFORE buffering.
        StreamException e = assertThrows(StreamException.class, () -> sink.invoke(row(4)),
                "epoch buffer overflow must fail typed (pre-fix: the record silently "
                        + "buffers and the barrier-duration × input-rate product grows "
                        + "the heap without bound)");
        assertEquals(NopStreamErrors.ERR_STREAM_STATE_ERROR.getErrorCode(), e.getErrorCode());
        String detail = String.valueOf(e.getMessage()) + e;
        assertTrue(detail.contains("epoch buffer"), "the failure must name the envelope: " + detail);
    }

    @Test
    void pendingEpochStackOverflowFailsTypedR403() throws Exception {
        JdbcTwoPhaseCommitSink<Map<String, Object>> sink = JdbcTwoPhaseCommitSink
                .<Map<String, Object>>builder()
                .jdbcTemplate(jdbcTemplate)
                .tableName("target_data")
                .columns("id", "name", "amount")
                .recordMapper(m -> m)
                .maxPendingEpochs(2)
                .build();
        sink.beginTransaction();
        sink.initializeLedgerTable();
        sink.invoke(row(1));
        sink.saveState(101L);
        sink.invoke(row(2));
        sink.saveState(102L);
        // The 3rd uncommitted epoch must be typed-rejected before the transfer.
        StreamException e = assertThrows(StreamException.class, () -> {
            sink.invoke(row(3));
            sink.saveState(103L);
        }, "pendingCommits stack overflow must fail typed (pre-fix: the stack grows, "
                + "every barrier re-serializes all pending epochs — positive feedback)");
        assertEquals(NopStreamErrors.ERR_STREAM_STATE_ERROR.getErrorCode(), e.getErrorCode());
        String detail = String.valueOf(e.getMessage()) + e;
        assertTrue(detail.contains("pendingCommits"),
                "the failure must name the pendingCommits envelope: " + detail);
    }

    @Test
    void nonPositiveEnvelopeParamsFailFastR403() {
        assertThrows(StreamException.class,
                () -> JdbcTwoPhaseCommitSink.<Map<String, Object>>builder()
                        .jdbcTemplate(jdbcTemplate).tableName("t")
                        .columns("id").recordMapper(m -> m)
                        .maxEpochRecords(0).build(),
                "maxEpochRecords <= 0 must fail fast");
        assertThrows(StreamException.class,
                () -> JdbcTwoPhaseCommitSink.<Map<String, Object>>builder()
                        .jdbcTemplate(jdbcTemplate).tableName("t")
                        .columns("id").recordMapper(m -> m)
                        .maxPendingEpochs(-1).build(),
                "maxPendingEpochs <= 0 must fail fast");
        StreamException e = assertThrows(StreamException.class,
                () -> JdbcTwoPhaseCommitSink.<Map<String, Object>>builder()
                        .jdbcTemplate(jdbcTemplate).tableName("t")
                        .columns("id").recordMapper(m -> m)
                        .statementTimeoutSeconds(0).build(),
                "statementTimeoutSeconds <= 0 must fail fast");
        assertEquals(NopStreamErrors.ERR_STREAM_INVALID_ARG.getErrorCode(), e.getErrorCode());
    }

    // ------------------------------------------------------------------
    // red ⑪ — W-11 statement-level timeout
    // ------------------------------------------------------------------

    /**
     * Statement-recording stub (S11 white-box, JDK dynamic proxy — zero
     * mockito): the sink's commit transaction must apply setQueryTimeout to
     * EVERY statement it creates (ledgerExists probe + data inserts + ledger
     * entry). Pre-fix red evidence: the capture counts zero
     * (module-wide setQueryTimeout zero hits).
     */
    @Test
    void commitAppliesStatementTimeoutToEveryStatementR403() throws Exception {
        AtomicInteger timeoutCalls = new AtomicInteger();
        IJdbcTemplate template = recordingTemplate(timeoutCalls);
        JdbcTwoPhaseCommitSink<Map<String, Object>> sink = JdbcTwoPhaseCommitSink
                .<Map<String, Object>>builder()
                .jdbcTemplate(template)
                .tableName("target_data")
                .columns("id", "name", "amount")
                .recordMapper(m -> m)
                .statementTimeoutSeconds(7)
                .build();
        sink.beginTransaction();
        sink.initializeLedgerTable();
        timeoutCalls.set(0); // exclude the initializeLedgerTable DDL statements
        sink.invoke(row(1));
        sink.saveState(201L);
        sink.commit(201L);

        // ledgerExists probe + INSERT data batch + INSERT ledger row = >= 3
        // statements, EVERY one bounded.
        assertTrue(timeoutCalls.get() >= 3,
                "every statement in the commit transaction must receive setQueryTimeout "
                        + "(pre-fix: 0 — module-wide zero hits), got " + timeoutCalls.get());
    }

    /**
     * rule 22 end-to-end chain (W-04 × W-11 combined face): invoke → saveState
     * multi-epoch staging → envelope-boundary typed rejects (both containers) →
     * a clean epoch commits through the statement-timeout-bounded path — one
     * continuous drive from invoke to the commit terminus.
     */
    @Test
    void combinedTwoPhaseChainEnvelopeAndCommitTimeoutContractR403() throws Exception {
        AtomicInteger timeoutCalls = new AtomicInteger();
        IJdbcTemplate recordingTemplate = recordingTemplate(timeoutCalls);
        JdbcTwoPhaseCommitSink<Map<String, Object>> sink = JdbcTwoPhaseCommitSink
                .<Map<String, Object>>builder()
                .jdbcTemplate(recordingTemplate)
                .tableName("target_data")
                .columns("id", "name", "amount")
                .recordMapper(m -> m)
                .maxEpochRecords(3)
                .maxPendingEpochs(2)
                .statementTimeoutSeconds(7)
                .build();
        sink.beginTransaction();
        sink.initializeLedgerTable();
        timeoutCalls.set(0);

        // Leg 1: epoch buffer boundary — the 4th record typed-rejects.
        for (long i = 0; i < 3; i++) {
            sink.invoke(row(i));
        }
        assertThrows(StreamException.class, () -> sink.invoke(row(4)),
                "buffer boundary must reject typed inside the chain");

        // Leg 2: pendingCommits boundary — saveState e101, e102 ok, e103 rejected.
        sink.saveState(101L);
        sink.invoke(row(11));
        sink.saveState(102L);
        sink.invoke(row(12));
        assertThrows(StreamException.class, () -> sink.saveState(103L),
                "pendingCommits boundary must reject typed inside the chain");

        // Leg 3: a clean epoch commits through the timeout-bounded statement path
        // — first drain the pending stack (commit e101), then stage+commit e104.
        sink.commit(101L);
        sink.invoke(row(21));
        sink.saveState(104L);
        sink.commit(104L);
        assertTrue(timeoutCalls.get() >= 6,
                "both committed epochs' statements were all timeout-bounded, got "
                        + timeoutCalls.get());
    }

    /**
     * red ⑪(c) behavior leg (慢语句桩): a driver enforcing the statement
     * timeout (simulated by the stub throwing on executeBatch AFTER the timeout
     * was applied) must propagate typed out of {@code commit} — the
     * completion-notification path's failure must be VISIBLE, never swallowed
     * into silent epoch pile-up.
     */
    @Test
    void driverEnforcedStatementTimeoutPropagatesTypedR403() throws Exception {
        Connection real = dataSource.getConnection();
        Connection failing = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(), new Class[]{Connection.class},
                (proxy, method, args) -> {
                    try {
                        Object result = method.invoke(real, args);
                        if ("prepareStatement".equals(method.getName())
                                && result instanceof PreparedStatement ps) {
                            return PreparedStatement.class.cast(Proxy.newProxyInstance(
                                    PreparedStatement.class.getClassLoader(),
                                    new Class[]{PreparedStatement.class},
                                    (p2, m2, a2) -> {
                                        try {
                                            if ("executeBatch".equals(m2.getName())) {
                                                throw new SQLException(
                                                        "simulated driver statement timeout "
                                                                + "enforcement (S11 stub)");
                                            }
                                            return m2.invoke(ps, a2);
                                        } catch (InvocationTargetException e) {
                                            throw e.getCause();
                                        }
                                    }));
                        }
                        return result;
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
        IJdbcTemplate failingTemplate = (IJdbcTemplate) Proxy.newProxyInstance(
                IJdbcTemplate.class.getClassLoader(), new Class[]{IJdbcTemplate.class},
                (proxy, method, args) -> {
                    if ("openConnection".equals(method.getName())) {
                        return failing;
                    }
                    try {
                        return method.invoke(jdbcTemplate, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
        JdbcTwoPhaseCommitSink<Map<String, Object>> sink = JdbcTwoPhaseCommitSink
                .<Map<String, Object>>builder()
                .jdbcTemplate(failingTemplate)
                .tableName("target_data")
                .columns("id", "name", "amount")
                .recordMapper(m -> m)
                .build();
        sink.beginTransaction();
        sink.initializeLedgerTable();
        sink.invoke(row(1));
        sink.saveState(202L);
        // The simulated timeout must surface (translated typed) — never a
        // silent retry/roll-forward into epoch pile-up.
        assertThrows(Exception.class, () -> sink.commit(202L),
                "a driver-enforced statement timeout must fail the commit visibly");
    }
}

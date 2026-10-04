/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.jdbc;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.DaoErrors;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.exceptions.JdbcException;
import io.nop.dao.jdbc.impl.JdbcDialectProvider;
import io.nop.dao.jdbc.impl.JdbcTemplateImpl;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI11 roadmap EC ③: real-database (H2) integration tests for the batch-upsert
 * primitive's three classes — insert / update / duplicate-key conflict — plus batch
 * semantics and the explicit fallback behaviors. Every test drives the primitive's
 * public entry point ({@link IJdbcTemplate#upsertBatch}) end to end against the H2
 * database (no mocks): Minimum Rules #22.
 *
 * <p>H2 upsert syntax is MERGE INTO .. KEY (Phase 0 §5: green real-execution
 * precedent on the repository-resolved H2 2.4.240; the PG-style ON CONFLICT form has
 * a documented syntax-error pitfall on H2 2.3.232).
 */
public class TestUpsertPrimitiveH2 extends JdbcTestCase {
    static final String TABLE = "upsert_target";
    static final List<String> COLUMNS = Arrays.asList("id", "scope", "val", "label");
    static final List<String> CONFLICT = Arrays.asList("id", "scope");
    static final List<String> UPDATE = Arrays.asList("val", "label");

    @Override
    public String getInitSql() {
        return "/upsert-init.sql";
    }

    private Object[] row(String id, String scope, int val, String label) {
        return new Object[]{id, scope, val, label};
    }

    private long count(String table) {
        Object value = jdbc().findFirst(new SQL("select count(1) from " + table));
        return ((Number) value).longValue();
    }

    private Object scalar(String sql) {
        return jdbc().findFirst(new SQL(sql));
    }

    /** Force the querySpace "default" onto the oracle dialect model (no native upsert). */
    private void forceOracleDialect() {
        JdbcTemplateImpl impl = (JdbcTemplateImpl) jdbc();
        JdbcDialectProvider provider = new JdbcDialectProvider(txn());
        provider.setQuerySpaceToDialectMap(Map.of("default", "oracle"));
        impl.setDialectProvider(provider);
    }

    // ---------------- insert class ----------------

    /** First upsert inserts the row (count +1, values correct). */
    @Test
    public void testUpsertInsertsNewRow() {
        jdbc().upsertBatch("default", "testUpsertInsert", TABLE, COLUMNS, CONFLICT, UPDATE,
                List.<Object[]>of(row("a", "s1", 10, "x")), UpsertPolicy.NATIVE_ONLY);

        assertEquals(1, count(TABLE));
        assertEquals(10, ((Number) scalar("select val from " + TABLE + " where id = 'a' and scope = 's1'")).intValue());
        assertEquals("x", scalar("select label from " + TABLE + " where id = 'a' and scope = 's1'"));
    }

    // ---------------- update class ----------------

    /** Second upsert on the same conflict key updates in place (count unchanged). */
    @Test
    public void testUpsertUpdatesExistingRow() {
        jdbc().upsertBatch("default", "testUpsertUpdate", TABLE, COLUMNS, CONFLICT, UPDATE,
                List.<Object[]>of(row("b", "s1", 1, "old")), UpsertPolicy.NATIVE_ONLY);
        jdbc().upsertBatch("default", "testUpsertUpdate", TABLE, COLUMNS, CONFLICT, UPDATE,
                List.<Object[]>of(row("b", "s1", 2, "new")), UpsertPolicy.NATIVE_ONLY);

        assertEquals(1, count(TABLE), "same conflict key must update, not insert a second row");
        assertEquals(2, ((Number) scalar("select val from " + TABLE + " where id = 'b' and scope = 's1'")).intValue());
        assertEquals("new", scalar("select label from " + TABLE + " where id = 'b' and scope = 's1'"));
    }

    // ---------------- batch class ----------------

    /** One call, mixed batch (new keys + existing keys): inserts and updates in one transaction. */
    @Test
    public void testUpsertBatchMixedNewAndOldKeys() {
        jdbc().upsertBatch("default", "testUpsertBatch", TABLE, COLUMNS, CONFLICT, UPDATE,
                List.<Object[]>of(row("old", "s1", 1, "first")), UpsertPolicy.NATIVE_ONLY);

        jdbc().upsertBatch("default", "testUpsertBatch", TABLE, COLUMNS, CONFLICT, UPDATE,
                Arrays.asList(
                        row("old", "s1", 11, "updated"),
                        row("new1", "s1", 22, "inserted"),
                        row("new2", "s2", 33, "inserted")),
                UpsertPolicy.NATIVE_ONLY);

        assertEquals(3, count(TABLE));
        assertEquals(11, ((Number) scalar("select val from " + TABLE + " where id = 'old' and scope = 's1'")).intValue());
        assertEquals("updated", scalar("select label from " + TABLE + " where id = 'old' and scope = 's1'"));
        assertEquals(22, ((Number) scalar("select val from " + TABLE + " where id = 'new1' and scope = 's1'")).intValue());
        assertEquals(33, ((Number) scalar("select val from " + TABLE + " where id = 'new2' and scope = 's2'")).intValue());
    }

    // ---------------- conflict class ----------------

    /**
     * Conflict class, leg 1: the primitive-generated INSERT executed against an
     * existing conflict key surfaces the existing normalized translation chain — the
     * real SQLException translates to {@code ERR_SQL_DUPLICATE_KEY} with its sqlText
     * param, not a bare SQLException (h2 registers 23001,23505).
     */
    @Test
    public void testConflictSurfacesDuplicateKeyErrorCode() {
        jdbc().upsertBatch("default", "testConflict", TABLE, COLUMNS, CONFLICT, UPDATE,
                List.<Object[]>of(row("c", "s1", 1, "first")), UpsertPolicy.NATIVE_ONLY);

        IDialect h2 = jdbc().getDialectForQuerySpace("default");
        String insertText = UpsertStatements.buildInsertSqlText(h2, TABLE, COLUMNS);
        SQL dupInsert = SQL.begin().name("testConflictDupInsert").querySpace("default")
                .sql(insertText, row("c", "s1", 2, "second")).end();
        JdbcException ex = assertThrows(JdbcException.class, () -> jdbc().executeUpdate(dupInsert));

        assertEquals(DaoErrors.ERR_SQL_DUPLICATE_KEY.getErrorCode(), ex.getErrorCode(),
                "real duplicate-key execution must normalize to ERR_SQL_DUPLICATE_KEY");
        assertNotNull(ex.getParams().get(DaoErrors.ARG_SQL_TEXT), "sqlText param must be attached");
    }

    /**
     * Conflict class, leg 2 (the collision-instance scenario): the querySpace is
     * forced onto the oracle dialect model (no native upsert, {1} registration) while
     * the backend is H2. The FALLBACK policy consumes the duplicate INSERT through the
     * unified predicate (SQLState-23505 fallback — oracle's registration cannot see
     * H2's 23505) and completes the upsert via an UPDATE in a fresh transaction.
     * Mirrors TestJdbcCheckpointStorage#testGenericDialectFallbackSeparateTransactionUpsert
     * through the primitive's own execution face (regression anchor).
     */
    @Test
    public void testFallbackConsumesDuplicateAndUpdatesInFreshTransaction() {
        forceOracleDialect();

        jdbc().upsertBatch("default", "testFallbackDup", TABLE, COLUMNS, CONFLICT, UPDATE,
                List.<Object[]>of(row("f", "s1", 1, "first")), UpsertPolicy.FALLBACK_INSERT_UPDATE);
        // Same conflict key again: INSERT fails duplicate, UPDATE in a fresh txn wins.
        jdbc().upsertBatch("default", "testFallbackDup", TABLE, COLUMNS, CONFLICT, UPDATE,
                List.<Object[]>of(row("f", "s1", 2, "second")), UpsertPolicy.FALLBACK_INSERT_UPDATE);

        assertEquals(1, count(TABLE), "fallback upsert must not insert a second row");
        assertEquals(2, ((Number) scalar("select val from " + TABLE + " where id = 'f' and scope = 's1'")).intValue());
        assertEquals("second", scalar("select label from " + TABLE + " where id = 'f' and scope = 's1'"));
    }

    // ---------------- explicit unsupported behaviors ----------------

    /** NATIVE_ONLY on a dialect without native upsert fails loudly (never silently). */
    @Test
    public void testNativeOnlyFailsLoudlyOnUnsupportedDialect() {
        forceOracleDialect();

        NopException ex = assertThrows(NopException.class,
                () -> jdbc().upsertBatch("default", "testNativeOnly", TABLE, COLUMNS, CONFLICT, UPDATE,
                        List.<Object[]>of(row("n", "s1", 1, "x")), UpsertPolicy.NATIVE_ONLY));
        assertEquals(DaoErrors.ERR_SQL_FEATURE_NOT_SUPPORTED.getErrorCode(), ex.getErrorCode());
        assertEquals(0, count(TABLE), "failed upsert must not have written anything");
    }

    /** The execution face rejects the key-only (empty update set) form explicitly. */
    @Test
    public void testExecutionFaceRejectsKeyOnlyForm() {
        forceOracleDialect();

        NopException ex = assertThrows(NopException.class,
                () -> jdbc().upsertBatch("default", "testKeyOnlyExec", TABLE, COLUMNS, CONFLICT,
                        List.of(), List.<Object[]>of(row("k", "s1", 1, "x")), UpsertPolicy.FALLBACK_INSERT_UPDATE));
        assertEquals(DaoErrors.ERR_SQL_FEATURE_NOT_SUPPORTED.getErrorCode(), ex.getErrorCode());
    }

    /**
     * No false positive: a check-constraint violation under FALLBACK is not classified
     * as a duplicate key — it is rethrown (integrity violation) instead of being
     * masked by an UPDATE retry, and the row is not written.
     */
    @Test
    public void testFallbackRethrowsNonDuplicateIntegrityViolation() {
        forceOracleDialect();

        JdbcException ex = assertThrows(JdbcException.class,
                () -> jdbc().upsertBatch("default", "testFallbackCheck", "upsert_checked",
                        Arrays.asList("id", "val"), Arrays.asList("id"), Arrays.asList("val"),
                        List.<Object[]>of(new Object[]{"neg", -1}), UpsertPolicy.FALLBACK_INSERT_UPDATE));
        assertEquals(DaoErrors.ERR_SQL_DATA_INTEGRITY_VIOLATION.getErrorCode(), ex.getErrorCode(),
                "check violation must not be classified as duplicate key");
        assertEquals(0, count("upsert_checked"));
    }

    // ---------------- argument contract ----------------

    /** Malformed argument shapes fail fast with IllegalArgumentException (caller bug). */
    @Test
    public void testArgumentContractViolationsFailFast() {
        assertThrows(IllegalArgumentException.class,
                () -> jdbc().upsertBatch("default", "testArgs", TABLE, COLUMNS, CONFLICT, UPDATE,
                        List.<Object[]>of(new Object[]{"only", "three", 1}), UpsertPolicy.NATIVE_ONLY),
                "row length must align with the column set");

        assertThrows(IllegalArgumentException.class,
                () -> jdbc().upsertBatch("default", "testArgs", TABLE, COLUMNS, Arrays.asList("id", "nope"), UPDATE,
                        List.<Object[]>of(row("a", "s1", 1, "x")), UpsertPolicy.NATIVE_ONLY),
                "conflict keys must be a subset of the column set");

        assertNull(scalar("select min(val) from " + TABLE + " where id = 'a'"), "failed calls write nothing");
    }
}

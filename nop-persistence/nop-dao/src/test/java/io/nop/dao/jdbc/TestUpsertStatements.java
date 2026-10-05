/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.jdbc;

import io.nop.core.initialize.CoreInitialization;
import io.nop.dao.dialect.DialectManager;
import io.nop.dao.dialect.IDialect;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the WI11 batch-upsert primitive static face
 * ({@link UpsertStatements}): per-dialect upsert SQL shape (assertions migrated from
 * nop-stream TestJdbcCheckpointStorage#testUpsertSqlShapePerDialect — coverage moved,
 * not deleted), key-only forms, identifier escaping, style resolution, and the
 * unified three-stage duplicate-key predicate (design contract §3.2).
 */
public class TestUpsertStatements {
    static final List<String> COLUMNS = Arrays.asList("sid", "job_id", "pipeline_id", "checkpoint_id",
            "checkpoint_type", "trigger_timestamp", "completed_timestamp", "state_data");
    static final List<String> CONFLICT = Arrays.asList("job_id", "pipeline_id", "checkpoint_id");
    static final List<String> UPDATE = Arrays.asList("checkpoint_type", "trigger_timestamp", "completed_timestamp",
            "state_data");

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private IDialect dialect(String name) {
        return DialectManager.instance().getDialect(name);
    }

    /**
     * Per-dialect single-statement upsert shape. Simple identifiers pass through
     * {@code escapeSQLName} unchanged, so the texts are byte-identical to the legacy
     * private implementations' output (the migration assertions this test carries over
     * from TestJdbcCheckpointStorage).
     */
    @Test
    public void testUpsertSqlShapePerDialect() {
        String pg = UpsertStatements.buildNativeUpsertSqlText(dialect("postgresql"), "stream_checkpoint",
                COLUMNS, CONFLICT, UPDATE);
        assertTrue(pg.startsWith("INSERT INTO stream_checkpoint (sid, job_id, pipeline_id, checkpoint_id,"), pg);
        assertTrue(pg.contains("VALUES (?, ?, ?, ?, ?, ?, ?, ?)"), pg);
        assertTrue(pg.contains("ON CONFLICT (job_id, pipeline_id, checkpoint_id) DO UPDATE SET"), pg);
        assertTrue(pg.contains("state_data = EXCLUDED.state_data"), pg);
        assertFalse(pg.contains("ON DUPLICATE KEY"), pg);

        String mysql = UpsertStatements.buildNativeUpsertSqlText(dialect("mysql"), "stream_checkpoint",
                COLUMNS, CONFLICT, UPDATE);
        assertTrue(mysql.contains("ON DUPLICATE KEY UPDATE"), mysql);
        assertTrue(mysql.contains("state_data = VALUES(state_data)"), mysql);
        assertFalse(mysql.contains("ON CONFLICT"), mysql);

        String h2 = UpsertStatements.buildNativeUpsertSqlText(dialect("h2"), "stream_checkpoint",
                COLUMNS, CONFLICT, UPDATE);
        assertTrue(h2.startsWith("MERGE INTO stream_checkpoint"), h2);
        assertTrue(h2.contains("KEY (job_id, pipeline_id, checkpoint_id)"), h2);
        assertTrue(h2.endsWith("VALUES (?, ?, ?, ?, ?, ?, ?, ?)"), h2);
        assertFalse(h2.contains("INSERT INTO"), h2);
    }

    /**
     * The unified primitive covers the name families of both legacy enums: the
     * checkpoint storage matched {@code postgresql} only, the sink also matched
     * {@code pg}; mariadb joins mysql; resolution is case-insensitive.
     */
    @Test
    public void testResolveUpsertStyleNameFamilies() {
        assertEquals(UpsertStyle.POSTGRESQL, UpsertStatements.resolveUpsertStyle(dialect("postgresql")));
        assertEquals(UpsertStyle.MYSQL, UpsertStatements.resolveUpsertStyle(dialect("mysql")));
        assertEquals(UpsertStyle.MYSQL, UpsertStatements.resolveUpsertStyle(dialect("mariadb")));
        assertEquals(UpsertStyle.H2, UpsertStatements.resolveUpsertStyle(dialect("h2")));
        assertNull(UpsertStatements.resolveUpsertStyle(dialect("oracle")),
                "dialect without native upsert must resolve to null (explicit signal, not GENERIC)");
        assertNull(UpsertStatements.resolveUpsertStyle(null));
    }

    /** No-native-support dialects produce the explicit null contract signal. */
    @Test
    public void testNoNativeUpsertReturnsNullSignal() {
        assertNull(UpsertStatements.buildNativeUpsertSqlText(dialect("oracle"), "t", COLUMNS, CONFLICT, UPDATE));
    }

    /**
     * Key-only (empty update set) forms: PostgreSQL DO NOTHING, MySQL first conflict
     * key self-assignment, H2 MERGE (naturally key-only). Shapes carried over from
     * JdbcTwoPhaseCommitSink#buildUpsertSql.
     */
    @Test
    public void testKeyOnlyShapes() {
        List<String> keyOnlyUpdate = Arrays.asList();

        String pg = UpsertStatements.buildNativeUpsertSqlText(dialect("postgresql"), "t",
                Arrays.asList("k", "v"), Arrays.asList("k"), keyOnlyUpdate);
        assertTrue(pg.contains("ON CONFLICT (k) DO NOTHING"), pg);

        String mysql = UpsertStatements.buildNativeUpsertSqlText(dialect("mysql"), "t",
                Arrays.asList("k", "v"), Arrays.asList("k"), keyOnlyUpdate);
        assertTrue(mysql.contains("ON DUPLICATE KEY UPDATE k = k"), mysql);

        String h2 = UpsertStatements.buildNativeUpsertSqlText(dialect("h2"), "t",
                Arrays.asList("k", "v"), Arrays.asList("k"), keyOnlyUpdate);
        assertTrue(h2.startsWith("MERGE INTO t (k, v) KEY (k) VALUES (?, ?)"), h2);
    }

    /**
     * Reserved-word identifiers are quoted via escapeSQLName (net fix over the legacy
     * raw concatenation); {@code order} is in the h2 reserved keyword list.
     */
    @Test
    public void testReservedWordEscaping() {
        String h2 = UpsertStatements.buildNativeUpsertSqlText(dialect("h2"), "t",
                Arrays.asList("k", "order"), Arrays.asList("k"), Arrays.asList("order"));
        assertTrue(h2.contains("(k, \"order\")"), h2);

        String pg = UpsertStatements.buildNativeUpsertSqlText(dialect("postgresql"), "t",
                Arrays.asList("k", "order"), Arrays.asList("k"), Arrays.asList("order"));
        assertTrue(pg.contains("(k, \"order\") VALUES"), pg);
        assertTrue(pg.contains("SET \"order\" = EXCLUDED.\"order\""), pg);
    }

    /**
     * Stage 1 (translator first): vendor codes registered in the dialect XML are
     * classified as duplicate-key — DB2 -803 (registered, SQLState absent to isolate
     * stage 1) and H2 23001 (registered; the legacy decision table missed it).
     */
    @Test
    public void testDuplicateKeyTranslatorFirst() {
        assertTrue(UpsertStatements.isDuplicateKeyException(dialect("db2"),
                new SQLException("DB2 SQL Error -803", null, -803)));
        assertTrue(UpsertStatements.isDuplicateKeyException(dialect("h2"),
                new SQLException("unique index violation", "23001", 23001)));

        // The same H2-shaped exception under the oracle registration ({1}) is NOT a
        // false positive: no registered code, SQLState 23001 is neither 23505 nor 23000.
        assertFalse(UpsertStatements.isDuplicateKeyException(dialect("oracle"),
                new SQLException("unique index violation", "23001", 23001)));
    }

    /**
     * Stage 2 (unconditional SQLState-23505 fallback): the collision instance from
     * TestJdbcCheckpointStorage#testGenericDialectFallbackSeparateTransactionUpsert —
     * an oracle dialect model driven against an H2 backend reports SQLState/vendorCode
     * 23505, which is not in the oracle XML registration {1}. The fallback keeps this
     * path classified as duplicate (regression anchor for the migration).
     */
    @Test
    public void testDuplicateKeySqlStateFallbackCollisionInstance() {
        SQLException collision = new SQLException("Unique index or primary key violation", "23505", 23505);
        assertTrue(UpsertStatements.isDuplicateKeyException(dialect("oracle"), collision));
        assertTrue(UpsertStatements.isDuplicateKeyException(null, collision),
                "stages 2/3 apply even without a dialect");
    }

    /**
     * Stage 3 (SQLState-23000 + known vendor code carryover): MySQL 1022 is the
     * decision-table-exclusive code and must not be silently dropped; unknown vendor
     * codes under 23000 are not duplicates. SQL Server 2601/2627 and Oracle 1 are the
     * registered duplicate signatures; MySQL 1451 (foreign key) shares SQLState 23000
     * with the duplicates and must stay separated by the vendor code.
     */
    @Test
    public void testDuplicateKeyVendorCodeCarryover() {
        assertTrue(UpsertStatements.isDuplicateKeyException(dialect("oracle"),
                new SQLException("duplicate key", "23000", 1022)));
        assertTrue(UpsertStatements.isDuplicateKeyException(dialect("oracle"),
                new SQLException("duplicate entry", "23000", 1062)));
        assertTrue(UpsertStatements.isDuplicateKeyException(dialect("oracle"),
                new SQLException("Violation of UNIQUE KEY constraint", "23000", 2627)));
        assertTrue(UpsertStatements.isDuplicateKeyException(dialect("oracle"),
                new SQLException("Violation of UNIQUE KEY constraint", "23000", 2601)));
        assertTrue(UpsertStatements.isDuplicateKeyException(dialect("oracle"),
                new SQLException("ORA-00001", "23000", 1)));
        assertFalse(UpsertStatements.isDuplicateKeyException(dialect("oracle"),
                new SQLException("some other integrity problem", "23000", 4242)));
        assertFalse(UpsertStatements.isDuplicateKeyException(dialect("oracle"),
                new SQLException("Cannot delete or update a parent row: a foreign key constraint fails", "23000", 1451)),
                "a 23000 foreign-key violation must not be misread as a duplicate");
    }

    /**
     * Structured-field decision discipline (R5-CON-08, carried over from the legacy
     * decision table's test battery): genuine errors are never classified as
     * duplicates, message text alone never decides, and localized (non-English)
     * conflict messages are driver-locale independent via SQLState.
     */
    @Test
    public void testStructuredFieldsOnlyDecisionDiscipline() {
        // Genuine errors must not enter the UPDATE retry branch.
        assertFalse(UpsertStatements.isDuplicateKeyException(dialect("oracle"),
                new SQLException("Connection refused", "08001", 0)));
        assertFalse(UpsertStatements.isDuplicateKeyException(dialect("oracle"),
                new SQLException("Table not found in statement", "42S02", 0)));
        assertFalse(UpsertStatements.isDuplicateKeyException(dialect("oracle"),
                new SQLException("Syntax error in SQL statement", "42000", 0)));
        assertFalse(UpsertStatements.isDuplicateKeyException(dialect("oracle"),
                new SQLException("Data conversion failed", "HY000", 0)));
        assertFalse(UpsertStatements.isDuplicateKeyException(dialect("oracle"),
                new SQLException("Login denied", null, 1045)));
        assertFalse(UpsertStatements.isDuplicateKeyException(dialect("oracle"),
                new RuntimeException("no sql exception at all")));

        // Message sniffing is gone: "unique"/"primary key" words in a message with
        // non-conflict structured fields must not decide.
        assertFalse(UpsertStatements.isDuplicateKeyException(dialect("oracle"),
                new SQLException("The statement was rejected because it would update a unique index during validation",
                        "08000", 0)));
        assertFalse(UpsertStatements.isDuplicateKeyException(dialect("oracle"),
                new SQLException("connection dropped mid-primary key lookup", "08S01", 0)));

        // Localized conflict message with the real SQLState is detected.
        assertTrue(UpsertStatements.isDuplicateKeyException(dialect("oracle"),
                new SQLException("主键冲突：记录已存在", "23505", 0)));
    }

    /**
     * Both chain walks survive the migration: Java cause chains (translated
     * JdbcException wrapping the driver exception) and JDBC getNextException chains
     * (batch drivers).
     */
    @Test
    public void testDuplicateKeyChainWalks() {
        SQLException dup = new SQLException("dup", "23505", 23505);

        RuntimeException wrapped = new RuntimeException("outer", dup);
        assertTrue(UpsertStatements.isDuplicateKeyException(dialect("oracle"), wrapped));

        SQLException batchOuter = new SQLException("batch failure", "08000", 0);
        batchOuter.setNextException(dup);
        assertTrue(UpsertStatements.isDuplicateKeyException(dialect("oracle"), batchOuter));

        // A non-duplicate outer chain stays non-duplicate.
        SQLException other = new SQLException("other", "08000", 0);
        other.setNextException(new SQLException("still other", "0A000", 5000));
        assertFalse(UpsertStatements.isDuplicateKeyException(dialect("oracle"), other));
    }
}

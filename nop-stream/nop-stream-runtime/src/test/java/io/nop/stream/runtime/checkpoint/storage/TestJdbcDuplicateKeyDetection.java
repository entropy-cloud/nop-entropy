/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint.storage;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R5-CON-08: {@code JdbcCheckpointStorage} duplicate-key detection must be
 * decided by the exception's structured fields (SQLState + vendor error code),
 * never by exception/class/message text sniffing. Verified via reflection so the
 * test compiles against the method regardless of its visibility.
 */
class TestJdbcDuplicateKeyDetection {

    private boolean detect(Exception e) throws Exception {
        Method m = JdbcCheckpointStorage.class.getDeclaredMethod("isDuplicateKeyException", Exception.class);
        m.setAccessible(true);
        return (Boolean) m.invoke(null, e);
    }

    // ====================== true conflicts ======================

    @Test
    void sqlState23505IsDuplicate() throws Exception {
        assertTrue(detect(new SQLException("UNIQUE constraint violation", "23505", 197)),
                "SQLState 23505 is the standard unique-violation state");
    }

    @Test
    void mysqlStyle23000WithDuplicateVendorCodeIsDuplicate() throws Exception {
        assertTrue(detect(new SQLException("Duplicate entry '1-1' for key 'PRIMARY'", "23000", 1062)),
                "MySQL reports duplicates as SQLState 23000 + vendor code 1062");
        assertTrue(detect(new SQLException("duplicate key", "23000", 1022)),
                "MySQL error 1022 is a duplicate-key error");
    }

    @Test
    void sqlServerAndOracleDuplicateSignaturesAreDuplicate() throws Exception {
        assertTrue(detect(new SQLException("Violation of UNIQUE KEY constraint", "23000", 2627)),
                "SQL Server 2627 is a unique-constraint violation");
        assertTrue(detect(new SQLException("Violation of UNIQUE KEY constraint", "23000", 2601)),
                "SQL Server 2601 is a unique-index violation");
        assertTrue(detect(new SQLException("ORA-00001: unique constraint violated", "23000", 1)),
                "Oracle ORA-00001 carries vendor code 1");
    }

    @Test
    void wrappedConflictIsDetectedThroughCauseChain() throws Exception {
        IllegalStateException wrapper = new IllegalStateException(
                "storeCheckpoint failed",
                new SQLException("UNIQUE constraint violation", "23505", 0));
        assertTrue(detect(wrapper), "a wrapped SQLException must still be detected");
    }

    @Test
    void localizedConflictMessageWithRealSqlStateIsDetected() throws Exception {
        // A localized (Chinese) driver message the old message-sniffing version
        // could never match — SQLState makes it driver-locale independent.
        SQLException localized = new SQLException("主键冲突：记录已存在", "23505", 0);
        assertTrue(detect(localized),
                "real conflicts must be detected regardless of message locale");
    }

    // ====================== genuine errors (must NOT be classified as duplicate) ======================

    @Test
    void genuineErrorsAreNotDuplicate() throws Exception {
        assertFalse(detect(new SQLException("Connection refused", "08001", 0)),
                "connection failure is not a duplicate-key error");
        assertFalse(detect(new SQLException("Table not found in statement", "42S02", 0)),
                "missing table is not a duplicate-key error");
        assertFalse(detect(new SQLException("Syntax error in SQL statement", "42000", 0)),
                "syntax error is not a duplicate-key error");
        assertFalse(detect(new SQLException("Data conversion failed", "HY000", 0)),
                "generic driver error is not a duplicate-key error");
        assertFalse(detect(new SQLException("Login denied", null, 1045)),
                "null SQLState with a non-duplicate vendor code is not a duplicate");
        assertFalse(detect(new RuntimeException("no sql exception at all")),
                "non-SQL exceptions are never duplicates");
    }

    @Test
    void duplicateWordInMessageWithoutConflictStateIsRejected() throws Exception {
        // The old message-sniffing false positive: a genuine error whose message
        // happens to contain "unique"/"duplicate"/"primary key" but whose
        // structured fields say otherwise must NOT enter the UPDATE retry branch.
        assertFalse(detect(new SQLException(
                        "The statement was rejected because it would update a unique index during validation",
                        "08000", 0)),
                "message text alone must never decide duplicate classification");
        assertFalse(detect(new SQLException("connection dropped mid-primary key lookup", "08S01", 0)),
                "message text alone must never decide duplicate classification");
    }

    @Test
    void foreignKeyViolation23000IsNotDuplicate() throws Exception {
        // MySQL reports FK violations as 23000 + 1451 — the vendor code must
        // separate them from duplicate entries (also 23000 + 1062).
        assertFalse(detect(new SQLException("Cannot delete or update a parent row: a foreign key constraint fails",
                        "23000", 1451)),
                "a 23000 foreign-key violation must not be misread as a duplicate");
    }

    @Test
    void nextExceptionChainIsWalked() throws Exception {
        // Batch drivers attach per-statement failures via getNextException().
        SQLException primary = new SQLException("Batch entry 0 failed", "07000", 0);
        primary.setNextException(new SQLException("duplicate key value", "23505", 0));
        assertTrue(detect(primary),
                "conflicts attached via getNextException must be detected");
    }
}

/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.db.migration.core;

import com.zaxxer.hikari.HikariDataSource;
import io.nop.api.core.exceptions.NopException;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.jdbc.impl.JdbcFactory;
import io.nop.dao.txn.ITransactionTemplate;
import io.nop.db.migration.AbstractMigrationTestCase;
import io.nop.db.migration.DbMigrationErrors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cases for MigrationHistoryManager
 */
class TestMigrationHistoryManager extends AbstractMigrationTestCase {

    private MigrationHistoryManager historyManager;

    private final List<HikariDataSource> extraDataSources = new ArrayList<>();

    @BeforeEach
    void setUpHistoryManager() throws Exception {
        super.setUp();
        historyManager = new MigrationHistoryManager(jdbcTemplate, "default");
    }

    @AfterEach
    void tearDownHistoryManager() throws Exception {
        for (HikariDataSource ds : extraDataSources) {
            ds.close();
        }
        extraDataSources.clear();
        super.tearDown();
    }

    @Test
    void testEnsureHistoryTableExists() {
        // Table should not exist initially
        assertFalse(tableExists(MigrationHistoryManager.TABLE_NAME),
            "History table should not exist before ensureHistoryTableExists is called");

        // Create table
        historyManager.ensureHistoryTableExists(dialect);

        // Table should now exist
        assertTrue(tableExists(MigrationHistoryManager.TABLE_NAME),
            "History table should exist after ensureHistoryTableExists is called");

        // Calling again should not fail (idempotent)
        historyManager.ensureHistoryTableExists(dialect);
        assertTrue(tableExists(MigrationHistoryManager.TABLE_NAME),
            "History table should still exist after calling ensureHistoryTableExists twice");
    }

    @Test
    void testGetExecutedVersions_NoTable() {
        Set<String> versions = historyManager.getExecutedVersions();
        assertNotNull(versions, "getExecutedVersions should return a non-null set");
        assertTrue(versions.isEmpty(), "getExecutedVersions should return empty set when table doesn't exist");
    }

    @Test
    void testGetExecutedVersions_EmptyTable() {
        historyManager.ensureHistoryTableExists(dialect);

        Set<String> versions = historyManager.getExecutedVersions();
        assertNotNull(versions, "getExecutedVersions should return a non-null set");
        assertTrue(versions.isEmpty(), "getExecutedVersions should return empty set when table is empty");
    }

    @Test
    void testGetExecutedVersions_WithMigrations() {
        historyManager.ensureHistoryTableExists(dialect);

        // Record multiple migrations
        MigrationRecord record1 = new MigrationRecord();
        record1.setVersion("1.0.0");
        record1.setDescription("Initial migration");
        record1.setChecksum("checksum1");
        record1.setExecutionTime(100);
        record1.setInstalledBy("test");
        record1.setSuccess(true);
        historyManager.recordMigration(record1);

        MigrationRecord record2 = new MigrationRecord();
        record2.setVersion("1.1.0");
        record2.setDescription("Add users table");
        record2.setChecksum("checksum2");
        record2.setExecutionTime(150);
        record2.setInstalledBy("test");
        record2.setSuccess(true);
        historyManager.recordMigration(record2);

        MigrationRecord record3 = new MigrationRecord();
        record3.setVersion("1.2.0");
        record3.setDescription("Add indexes");
        record3.setChecksum("checksum3");
        record3.setExecutionTime(50);
        record3.setInstalledBy("test");
        record3.setSuccess(true);
        historyManager.recordMigration(record3);

        Set<String> versions = historyManager.getExecutedVersions();
        assertEquals(3, versions.size(), "Should return 3 executed versions");
        assertTrue(versions.contains("1.0.0"), "Should contain version 1.0.0");
        assertTrue(versions.contains("1.1.0"), "Should contain version 1.1.0");
        assertTrue(versions.contains("1.2.0"), "Should contain version 1.2.0");
    }

    @Test
    void testRecordMigration() {
        historyManager.ensureHistoryTableExists(dialect);

        MigrationRecord record = new MigrationRecord();
        record.setVersion("1.0.0");
        record.setDescription("Test migration");
        record.setType("VERSIONED");
        record.setChecksum("test-checksum");
        record.setExecutionTime(100);
        record.setInstalledBy("test-user");
        record.setSuccess(true);

        historyManager.recordMigration(record);

        // Verify migration was recorded
        MigrationRecord retrieved = historyManager.getMigrationByVersion("1.0.0");
        assertNotNull(retrieved, "Migration should be retrievable after recording");
        assertEquals("1.0.0", retrieved.getVersion());
        assertEquals("Test migration", retrieved.getDescription());
        assertEquals("VERSIONED", retrieved.getType());
        assertEquals("test-checksum", retrieved.getChecksum());
        assertEquals(100, retrieved.getExecutionTime());
        assertEquals("test-user", retrieved.getInstalledBy());
        assertTrue(retrieved.isSuccess());
        assertNotNull(retrieved.getInstalledOn(), "installed_on timestamp should be set");
    }

    @Test
    void testGetMigrationByVersion_NoTable() {
        MigrationRecord record = historyManager.getMigrationByVersion("1.0.0");
        assertNull(record, "Should return null when table doesn't exist");
    }

    @Test
    void testGetMigrationByVersion_NotFound() {
        historyManager.ensureHistoryTableExists(dialect);

        MigrationRecord record = historyManager.getMigrationByVersion("1.0.0");
        assertNull(record, "Should return null for non-existent version");
    }

    @Test
    void testGetMigrationByVersion_Found() {
        historyManager.ensureHistoryTableExists(dialect);

        MigrationRecord original = new MigrationRecord();
        original.setVersion("1.5.0");
        original.setDescription("Found test");
        original.setType("REPEATABLE");
        original.setChecksum("found-checksum");
        original.setExecutionTime(200);
        original.setInstalledBy("test-user");
        original.setSuccess(true);
        original.setInstalledOn(new Date());

        historyManager.recordMigration(original);

        MigrationRecord retrieved = historyManager.getMigrationByVersion("1.5.0");
        assertNotNull(retrieved);
        assertEquals("1.5.0", retrieved.getVersion());
        assertEquals("Found test", retrieved.getDescription());
        assertEquals("REPEATABLE", retrieved.getType());
        assertEquals("found-checksum", retrieved.getChecksum());
        assertEquals(200, retrieved.getExecutionTime());
        assertEquals("test-user", retrieved.getInstalledBy());
        assertTrue(retrieved.isSuccess());
    }

    @Test
    void testMultipleQueries() {
        historyManager.ensureHistoryTableExists(dialect);

        // Record migrations in non-sequential order
        MigrationRecord v2 = new MigrationRecord();
        v2.setVersion("2.0.0");
        v2.setDescription("Second migration");
        v2.setChecksum("c2");
        v2.setExecutionTime(100);
        v2.setInstalledBy("test");
        v2.setSuccess(true);
        historyManager.recordMigration(v2);

        MigrationRecord v1 = new MigrationRecord("1.0.0", "First migration", "c1", 50, "test");
        historyManager.recordMigration(v1);

        MigrationRecord v3 = new MigrationRecord();
        v3.setVersion("3.0.0");
        v3.setDescription("Third migration");
        v3.setChecksum("c3");
        v3.setExecutionTime(75);
        v3.setInstalledBy("test");
        v3.setSuccess(true);
        historyManager.recordMigration(v3);

        // getExecutedVersions should return all versions
        Set<String> versions = historyManager.getExecutedVersions();
        assertEquals(3, versions.size());

        // Each version should be retrievable
        assertNotNull(historyManager.getMigrationByVersion("1.0.0"));
        assertNotNull(historyManager.getMigrationByVersion("2.0.0"));
        assertNotNull(historyManager.getMigrationByVersion("3.0.0"));
    }

    @Test
    void testRecordMigrationFailedWithoutErrorMessageStaysFailed() {
        historyManager.ensureHistoryTableExists(dialect);

        // A failed record without an error message (e.g. an exception whose
        // getMessage() is null) must NOT be flipped to success, otherwise the
        // failed migration is skipped on every later run
        MigrationRecord failed = new MigrationRecord();
        failed.setVersion("1.0.0");
        failed.setDescription("Failed migration");
        failed.setSuccess(false);
        failed.setErrorMessage(null);
        historyManager.recordMigration(failed);

        assertFalse(historyManager.getExecutedVersions().contains("1.0.0"),
            "a failed record without error message must not be treated as executed");
        MigrationRecord retrieved = historyManager.getMigrationByVersion("1.0.0");
        assertNotNull(retrieved);
        assertFalse(retrieved.isSuccess(), "success must be persisted as explicitly set");
    }

    @Test
    void testRecordMigrationReplacesExistingRecord() {
        historyManager.ensureHistoryTableExists(dialect);

        MigrationRecord failed = new MigrationRecord();
        failed.setVersion("1.0.0");
        failed.setDescription("Failed attempt");
        failed.setSuccess(false);
        failed.setErrorMessage("boom");
        historyManager.recordMigration(failed);

        // A retried migration succeeds: the failed row must be replaced, not
        // rejected with a primary key violation
        MigrationRecord success = new MigrationRecord();
        success.setVersion("1.0.0");
        success.setDescription("Retry");
        success.setSuccess(true);
        success.setInstalledBy("test");
        assertDoesNotThrow(() -> historyManager.recordMigration(success));

        assertTrue(historyManager.getExecutedVersions().contains("1.0.0"),
            "replaced record should be the successful one");
        MigrationRecord retrieved = historyManager.getMigrationByVersion("1.0.0");
        assertNotNull(retrieved);
        assertTrue(retrieved.isSuccess());
        assertEquals("Retry", retrieved.getDescription());
    }

    @Test
    void testGetExecutedVersionsWithLowerCaseColumnLabels() {
        // MySQL keeps the column labels in the case used by the DDL (lowercase),
        // so history queries must read columns by position instead of assuming
        // upper-case labels. H2 with DATABASE_TO_UPPER=false behaves the same.
        MigrationHistoryManager lowerCaseManager = newHistoryManagerWithLowerCaseIdentifiers();

        lowerCaseManager.ensureHistoryTableExists(dialect);
        MigrationRecord record = new MigrationRecord();
        record.setVersion("9.9.9");
        record.setDescription("lowercase");
        record.setSuccess(true);
        lowerCaseManager.recordMigration(record);

        assertTrue(lowerCaseManager.getExecutedVersions().contains("9.9.9"),
            "should read version by position regardless of column label case");
    }

    @Test
    void testGetMigrationByVersionWithLowerCaseColumnLabels() {
        MigrationHistoryManager lowerCaseManager = newHistoryManagerWithLowerCaseIdentifiers();

        lowerCaseManager.ensureHistoryTableExists(dialect);
        MigrationRecord record = new MigrationRecord();
        record.setVersion("9.9.9");
        record.setDescription("lowercase");
        record.setType("VERSIONED");
        record.setInstalledBy("tester");
        record.setSuccess(true);
        lowerCaseManager.recordMigration(record);

        MigrationRecord retrieved = lowerCaseManager.getMigrationByVersion("9.9.9");
        assertNotNull(retrieved, "should read the row by position regardless of column label case");
        assertEquals("9.9.9", retrieved.getVersion());
        assertEquals("lowercase", retrieved.getDescription());
        assertEquals("VERSIONED", retrieved.getType());
        assertEquals("tester", retrieved.getInstalledBy());
        assertTrue(retrieved.isSuccess());
    }

    @Test
    void testGetExecutedVersionsPropagatesFailure() {
        // Connection/metadata failures must not be swallowed into "table does
        // not exist", which would make every already-executed migration run
        // again
        IJdbcTemplate failing = newRejectingJdbcTemplate(jdbcTemplate);
        MigrationHistoryManager manager = new MigrationHistoryManager(failing, "default");
        NopException e = assertThrows(NopException.class, manager::getExecutedVersions);
        assertEquals(DbMigrationErrors.ERR_DB_MIGRATION_HISTORY_QUERY_FAILED.getErrorCode(), e.getErrorCode());
    }

    /**
     * Builds a manager on an H2 database created with DATABASE_TO_UPPER=false,
     * so unquoted identifiers stay lowercase like on MySQL.
     */
    private MigrationHistoryManager newHistoryManagerWithLowerCaseIdentifiers() {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl("jdbc:h2:mem:" + java.util.UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false");
        ds.setDriverClassName("org.h2.Driver");
        ds.setUsername("sa");
        ds.setPassword("");
        extraDataSources.add(ds);

        JdbcFactory factory = new JdbcFactory();
        ITransactionTemplate txnTemplate = factory.newTransactionTemplate(ds);
        IJdbcTemplate lowerCaseJdbcTemplate = factory.newJdbcTemplate(txnTemplate);
        return new MigrationHistoryManager(lowerCaseJdbcTemplate, "default");
    }

    /**
     * Delegating proxy that fails every runWithConnection call, simulating an
     * unreachable database.
     */
    private static IJdbcTemplate newRejectingJdbcTemplate(IJdbcTemplate delegate) {
        return (IJdbcTemplate) java.lang.reflect.Proxy.newProxyInstance(
            IJdbcTemplate.class.getClassLoader(),
            new Class<?>[]{IJdbcTemplate.class},
            (proxy, method, args) -> {
                if (method.getName().equals("runWithConnection")) {
                    throw new IllegalStateException("connection rejected by test");
                }
                try {
                    return method.invoke(delegate, args);
                } catch (java.lang.reflect.InvocationTargetException ex) {
                    throw ex.getCause();
                }
            });
    }
}

/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.db.migration.core;

import io.nop.db.migration.AbstractMigrationTestCase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cases for MigrationHistoryManager
 */
class TestMigrationHistoryManager extends AbstractMigrationTestCase {

    private MigrationHistoryManager historyManager;

    @BeforeEach
    void setUpHistoryManager() throws Exception {
        super.setUp();
        historyManager = new MigrationHistoryManager(jdbcTemplate, "default");
    }

    @AfterEach
    void tearDownHistoryManager() throws Exception {
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
        historyManager.recordMigration(record1);

        MigrationRecord record2 = new MigrationRecord();
        record2.setVersion("1.1.0");
        record2.setDescription("Add users table");
        record2.setChecksum("checksum2");
        record2.setExecutionTime(150);
        record2.setInstalledBy("test");
        historyManager.recordMigration(record2);

        MigrationRecord record3 = new MigrationRecord();
        record3.setVersion("1.2.0");
        record3.setDescription("Add indexes");
        record3.setChecksum("checksum3");
        record3.setExecutionTime(50);
        record3.setInstalledBy("test");
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
    void testRecordMigration_UpdateExistingVersion() {
        historyManager.ensureHistoryTableExists(dialect);

        MigrationRecord failed = new MigrationRecord();
        failed.setVersion("2.0.0");
        failed.setDescription("First try");
        failed.setType("VERSIONED");
        failed.setChecksum("checksum-failed");
        failed.setExecutionTime(10);
        failed.setInstalledBy("tester");
        failed.setSuccess(false);
        failed.setErrorMessage("boom");
        historyManager.recordMigration(failed);

        MigrationRecord success = new MigrationRecord();
        success.setVersion("2.0.0");
        success.setDescription("Retry success");
        success.setType("VERSIONED");
        success.setChecksum("checksum-success");
        success.setExecutionTime(20);
        success.setInstalledBy("tester");
        success.setSuccess(true);
        historyManager.recordMigration(success);

        Set<String> versions = historyManager.getExecutedVersions();
        assertEquals(1, versions.size(), "Version should be unique after update");
        assertTrue(versions.contains("2.0.0"), "Updated version should be treated as executed");

        MigrationRecord retrieved = historyManager.getMigrationByVersion("2.0.0");
        assertNotNull(retrieved);
        assertTrue(retrieved.isSuccess(), "Retry should update success status");
        assertEquals("checksum-success", retrieved.getChecksum());
        assertNull(retrieved.getErrorMessage(), "Successful retry should clear error message");
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
        historyManager.recordMigration(v2);

        MigrationRecord v1 = new MigrationRecord("1.0.0", "First migration", "c1", 50, "test");
        historyManager.recordMigration(v1);

        MigrationRecord v3 = new MigrationRecord();
        v3.setVersion("3.0.0");
        v3.setDescription("Third migration");
        v3.setChecksum("c3");
        v3.setExecutionTime(75);
        v3.setInstalledBy("test");
        historyManager.recordMigration(v3);

        // getExecutedVersions should return all versions
        Set<String> versions = historyManager.getExecutedVersions();
        assertEquals(3, versions.size());

        // Each version should be retrievable
        assertNotNull(historyManager.getMigrationByVersion("1.0.0"));
        assertNotNull(historyManager.getMigrationByVersion("2.0.0"));
        assertNotNull(historyManager.getMigrationByVersion("3.0.0"));
    }
}

/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.db.migration.core;

import io.nop.db.migration.AbstractMigrationTestCase;
import io.nop.db.migration.executor.AddColumnExecutor;
import io.nop.db.migration.executor.CreateTableExecutor;
import io.nop.db.migration.executor.DeleteDataExecutor;
import io.nop.db.migration.executor.DropColumnExecutor;
import io.nop.db.migration.executor.DropIndexExecutor;
import io.nop.db.migration.executor.DropTableExecutor;
import io.nop.db.migration.executor.InsertDataExecutor;
import io.nop.db.migration.executor.UpdateDataExecutor;
import io.nop.db.migration.model.DbChangeModel;
import io.nop.db.migration.model.DbMigrationModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for the XML migration file path:
 * MigrationFileScanner -> DslModelParser -> MigrationEngine.
 *
 * <p>Ensures changes parsed from {@code *.migration.xml} files get their
 * {@code type} backfilled from the XML element tag so the engine dispatches
 * them to the matching IChangeExecutor instead of silently skipping them.
 */
class TestMigrationFileScanner extends AbstractMigrationTestCase {

    private static final String IT_MIGRATIONS_PATH = "/nop/db-migration/it-migrations";
    private static final String TYPE_COVERAGE_PATH = "/nop/db-migration/type-coverage";

    private MigrationFileScanner scanner;
    private MigrationEngine migrationEngine;

    @BeforeEach
    void setUpScanner() throws Exception {
        super.setUp();
        scanner = new MigrationFileScanner();
        migrationEngine = new MigrationEngine();
    }

    @AfterEach
    void tearDownScanner() throws Exception {
        super.tearDown();
    }

    @Test
    void testScanFillsChangeTypeForAllChanges() {
        List<DbMigrationModel> migrations = scanner.scan(Arrays.asList(IT_MIGRATIONS_PATH, TYPE_COVERAGE_PATH));

        assertEquals(2, migrations.size());

        for (DbMigrationModel migration : migrations) {
            assertNotNull(migration.getChangeset(), "changeset should be parsed: " + migration.getVersion());
            assertFalse(migration.getChangeset().isEmpty(),
                "changeset should not be empty: " + migration.getVersion());
            for (DbChangeModel change : migration.getChangeset()) {
                assertNotNull(change.getType(),
                    "change type must be backfilled from the XML tag, otherwise the engine "
                        + "silently skips the change. migration=" + migration.getVersion());
                assertFalse(change.getType().isEmpty(),
                    "change type must not be empty. migration=" + migration.getVersion());
            }
            if (migration.getRollback() != null && migration.getRollback().getChanges() != null) {
                for (DbChangeModel change : migration.getRollback().getChanges()) {
                    assertNotNull(change.getType(),
                        "rollback change type must be backfilled. migration=" + migration.getVersion());
                }
            }
        }
    }

    @Test
    void testScanMapsEveryTagToRegisteredChangeType() {
        DbMigrationModel migration = loadCoverageMigration();

        List<DbChangeModel> changeset = migration.getChangeset();
        assertEquals(7, changeset.size());

        assertChangeType(changeset.get(0), CreateTableExecutor.CHANGE_TYPE, "createTable");
        assertChangeType(changeset.get(1), AddColumnExecutor.CHANGE_TYPE, "addColumn");
        assertChangeType(changeset.get(2), InsertDataExecutor.CHANGE_TYPE, "insert");
        assertChangeType(changeset.get(3), UpdateDataExecutor.CHANGE_TYPE, "update");
        assertChangeType(changeset.get(4), DeleteDataExecutor.CHANGE_TYPE, "delete");
        assertChangeType(changeset.get(5), DropColumnExecutor.CHANGE_TYPE, "dropColumn");
        assertChangeType(changeset.get(6), DropIndexExecutor.CHANGE_TYPE, "dropIndex");

        List<DbChangeModel> rollbackChanges = migration.getRollback().getChanges();
        assertEquals(2, rollbackChanges.size());
        assertChangeType(rollbackChanges.get(0), DropTableExecutor.CHANGE_TYPE, "dropTable");
        assertChangeType(rollbackChanges.get(1), AddColumnExecutor.CHANGE_TYPE, "addColumn");
    }

    @Test
    void testMigrateFromXmlFilesExecutesChanges() {
        MigrationContext context = new MigrationContext();
        context.setJdbcTemplate(jdbcTemplate);
        context.setDialect(dialect);
        context.setQuerySpace("default");
        context.setInstalledBy("test-user");
        context.setFailFast(true);
        context.setMigrationPaths(Collections.singletonList(IT_MIGRATIONS_PATH));

        MigrationResult result = migrationEngine.migrate(context);

        assertNotNull(result);
        assertEquals(1, result.getRecords().size());
        assertTrue(result.getRecords().get(0).isSuccess(),
            "migration loaded from XML should execute successfully");

        assertTrue(tableExists("xml_e2e_user"),
            "createTable from an XML migration file must be executed");
        assertFalse(tableExists("xml_e2e_tmp"),
            "dropTable from an XML migration file must be executed");
    }

    private DbMigrationModel loadCoverageMigration() {
        List<DbMigrationModel> migrations = scanner.scan(Collections.singletonList(TYPE_COVERAGE_PATH));
        assertEquals(1, migrations.size());
        return migrations.get(0);
    }

    private void assertChangeType(DbChangeModel change, String expectedType, String xmlTag) {
        assertEquals(expectedType, change.getType(),
            "tag <" + xmlTag + "> must map to the registered change type " + expectedType);
    }
}

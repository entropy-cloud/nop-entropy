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
import io.nop.db.migration.executor.AlterColumnExecutor;
import io.nop.db.migration.executor.CreateIndexExecutor;
import io.nop.db.migration.executor.CreateTableExecutor;
import io.nop.db.migration.executor.CreateViewExecutor;
import io.nop.db.migration.executor.CustomChangeExecutor;
import io.nop.db.migration.executor.DbTypeFilterExecutor;
import io.nop.db.migration.executor.DeleteDataExecutor;
import io.nop.db.migration.executor.DropColumnExecutor;
import io.nop.db.migration.executor.DropIndexExecutor;
import io.nop.db.migration.executor.DropTableExecutor;
import io.nop.db.migration.executor.DropViewExecutor;
import io.nop.db.migration.executor.InsertDataExecutor;
import io.nop.db.migration.executor.RenameTableExecutor;
import io.nop.db.migration.executor.SqlExecutor;
import io.nop.db.migration.executor.UpdateDataExecutor;
import io.nop.db.migration.model.DbChangeModel;
import io.nop.db.migration.model.DbMigrationModel;
import io.nop.db.migration.model.DbTypeFilterChange;
import io.nop.db.migration.model.InsertColumnModel;
import io.nop.db.migration.model.InsertDataChange;
import io.nop.db.migration.model.UpdateColumnModel;
import io.nop.db.migration.model.UpdateDataChange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    private static final String NINE_TYPES_PATH = "/nop/db-migration/nine-types";

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

    @Test
    void testXmlDataChangesParseToTypedColumns() {
        // Regression for the DynamicObject parsing defect: the xdef must
        // declare xdef:name on the <column> elements of insert/update so the
        // columns parse to InsertColumnModel/UpdateColumnModel instead of
        // DynamicObject (which made the executors fail with ClassCastException)
        DbMigrationModel migration = scanner.scan(Collections.singletonList(IT_MIGRATIONS_PATH)).get(0);

        Object firstInsertColumn = null;
        Object firstUpdateColumn = null;
        for (DbChangeModel change : migration.getChangeset()) {
            if (change instanceof InsertDataChange && firstInsertColumn == null) {
                firstInsertColumn = ((InsertDataChange) change).getColumns().get(0);
            } else if (change instanceof UpdateDataChange && firstUpdateColumn == null) {
                firstUpdateColumn = ((UpdateDataChange) change).getColumns().get(0);
            }
        }

        assertNotNull(firstInsertColumn, "fixture must contain an <insert> change");
        assertTrue(firstInsertColumn instanceof InsertColumnModel,
            "<insert><column> must parse to InsertColumnModel, but was: " + firstInsertColumn.getClass().getName());
        assertEquals("id", ((InsertColumnModel) firstInsertColumn).getName());
        assertEquals("u-1", ((InsertColumnModel) firstInsertColumn).getValue());

        assertNotNull(firstUpdateColumn, "fixture must contain an <update> change");
        assertTrue(firstUpdateColumn instanceof UpdateColumnModel,
            "<update><column> must parse to UpdateColumnModel, but was: " + firstUpdateColumn.getClass().getName());
        assertEquals("age", ((UpdateColumnModel) firstUpdateColumn).getName());
    }

    @Test
    void testXmlDataChangesExecute() {
        MigrationContext context = new MigrationContext();
        context.setJdbcTemplate(jdbcTemplate);
        context.setDialect(dialect);
        context.setQuerySpace("default");
        context.setInstalledBy("test-user");
        context.setFailFast(true);
        context.setMigrationPaths(Collections.singletonList(IT_MIGRATIONS_PATH));

        MigrationResult result = migrationEngine.migrate(context);

        assertTrue(result.getRecords().get(0).isSuccess(),
            "data changes from an XML migration file should execute successfully");

        // two rows inserted, one deleted => exactly one row left
        assertEquals(1, countRows("xml_e2e_user"), "insert + delete from XML must both be executed");
        // the update must have changed u-1's age from 30 to 31
        jdbcTemplate.executeQuery(
            io.nop.core.lang.sql.SQL.begin().append("SELECT age FROM xml_e2e_user WHERE id = 'u-1'").end(),
            dataSet -> {
                for (io.nop.dataset.IDataRow row : dataSet) {
                    org.junit.jupiter.api.Assertions.assertEquals(31L, row.getLong(0),
                        "update from XML must be executed");
                }
                return null;
            });
    }

    @Test
    void testNinePreviouslyUnparseableTagsParseAndBackfillType() {
        // Regression for the 9 change types whose model classes did not extend
        // DbChangeModel: loading a migration containing any of them used to
        // fail with ClassCastException in _DbMigrationModel.setChangeset /
        // backfillChangeTypes. All of them must now parse and get their type
        // backfilled so the engine dispatches them.
        List<DbMigrationModel> migrations = scanner.scan(Collections.singletonList(NINE_TYPES_PATH));

        assertEquals(1, migrations.size());
        List<?> changeset = migrations.get(0).getChangeset();
        assertEquals(10, changeset.size());

        assertTypedChange(changeset.get(0), CreateTableExecutor.CHANGE_TYPE, "createTable");
        assertTypedChange(changeset.get(1), RenameTableExecutor.CHANGE_TYPE, "renameTable");
        assertTypedChange(changeset.get(2), SqlExecutor.CHANGE_TYPE, "sql");
        assertTypedChange(changeset.get(3), CreateIndexExecutor.CHANGE_TYPE, "createIndex");
        assertTypedChange(changeset.get(4), AlterColumnExecutor.CHANGE_TYPE, "alterColumn");
        assertTypedChange(changeset.get(5), CreateViewExecutor.CHANGE_TYPE, "createView");
        assertTypedChange(changeset.get(6), DropViewExecutor.CHANGE_TYPE, "dropView");
        assertTypedChange(changeset.get(7), DbTypeFilterExecutor.CHANGE_TYPE, "dbTypeFilter");
        assertTypedChange(changeset.get(8), CustomChangeExecutor.CHANGE_TYPE, "customChange");
        // executeMark has no executor; its type stays null and the engine skips it
        assertNull(((DbChangeModel) changeset.get(9)).getType(), "executeMark has no registered executor");

        // nested changes inside dbTypeFilter need the same type backfill,
        // otherwise DbTypeFilterExecutor silently skips them
        DbTypeFilterChange filter = (DbTypeFilterChange) (Object) changeset.get(7);
        assertEquals(1, filter.getChanges().size());
        assertEquals(SqlExecutor.CHANGE_TYPE, ((DbChangeModel) (Object) filter.getChanges().get(0)).getType(),
            "nested <sql> inside dbTypeFilter must get its type backfilled");
    }

    private void assertTypedChange(Object change, String expectedType, String xmlTag) {
        assertTrue(change instanceof DbChangeModel,
            "<" + xmlTag + "> must parse to a DbChangeModel subclass, but was: " + change.getClass().getName());
        assertEquals(expectedType, ((DbChangeModel) change).getType(),
            "tag <" + xmlTag + "> must map to the registered change type " + expectedType);
    }

    @Test
    void testNineTypesMigrationExecutes() {
        MigrationContext context = new MigrationContext();
        context.setJdbcTemplate(jdbcTemplate);
        context.setDialect(dialect);
        context.setQuerySpace("default");
        context.setInstalledBy("test-user");
        context.setFailFast(true);
        context.setMigrationPaths(Collections.singletonList(NINE_TYPES_PATH));

        MigrationResult result = migrationEngine.migrate(context);

        assertEquals(1, result.getRecords().size());
        assertTrue(result.getRecords().get(0).isSuccess(),
            "the nine-type migration must execute (renameTable, sql, createIndex, views, dbTypeFilter)");
        assertFalse(tableExists("nine_user"), "renameTable must have renamed nine_user");
        assertTrue(tableExists("nine_member"), "renameTable must have produced nine_member");

        // <sql> inserted name='a', the nested dbTypeFilter <sql> updated it to 'b'
        jdbcTemplate.executeQuery(
            io.nop.core.lang.sql.SQL.begin().append("SELECT name FROM nine_member WHERE id = '1'").end(),
            dataSet -> {
                for (io.nop.dataset.IDataRow row : dataSet) {
                    org.junit.jupiter.api.Assertions.assertEquals("b", row.getString(0),
                        "both the top-level and the dbTypeFilter-nested <sql> changes must execute");
                }
                return null;
            });
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

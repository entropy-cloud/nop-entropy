/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.db.migration.core;

import io.nop.commons.type.StdSqlType;
import io.nop.db.migration.AbstractMigrationTestCase;
import io.nop.db.migration.executor.DbTypeFilterExecutor;
import io.nop.db.migration.model.AddColumnChange;
import io.nop.db.migration.model.ColumnDefinition;
import io.nop.db.migration.model.CreateTableChange;
import io.nop.db.migration.model.DbChangeModel;
import io.nop.db.migration.model.DbMigrationModel;
import io.nop.db.migration.model.DbTypeFilterChange;
import io.nop.db.migration.model.InsertColumnModel;
import io.nop.db.migration.model.InsertDataChange;
import io.nop.db.migration.model.RollbackDefinition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cases for MigrationEngine
 */
class TestMigrationEngine extends AbstractMigrationTestCase {

    private MigrationEngine migrationEngine;

    @BeforeEach
    void setUpMigrationEngine() throws Exception {
        super.setUp();
        migrationEngine = new MigrationEngine();
    }

    @AfterEach
    void tearDownMigrationEngine() throws Exception {
        super.tearDown();
    }

    @Test
    void testMigrate_NoMigrations() {
        MigrationContext context = createMigrationContext(Collections.emptyList());

        MigrationResult result = migrationEngine.migrate(context);

        assertNotNull(result);
        assertTrue(result.getRecords().isEmpty());
    }

    @Test
    void testMigrate_SingleMigration_CreateTable() {
        DbMigrationModel migration = createCreateTableMigration("1.0.0", "Create users table");
        List<DbMigrationModel> migrations = Collections.singletonList(migration);

        MigrationContext context = createMigrationContext(migrations);

        MigrationResult result = migrationEngine.migrate(context);

        assertNotNull(result);
        assertEquals(1, result.getRecords().size());

        MigrationRecord record = result.getRecords().get(0);
        assertEquals("1.0.0", record.getVersion());
        assertEquals("Create users table", record.getDescription());
        assertTrue(record.isSuccess());

        assertTrue(tableExists("users"), "Users table should be created");
    }

    @Test
    void testMigrate_MultipleMigrations() {
        List<DbMigrationModel> migrations = new ArrayList<>();

        migrations.add(createCreateTableMigration("1.0.0", "Create users table"));
        migrations.add(createAddColumnMigration("1.1.0", "Add email column"));
        migrations.add(createInsertDataMigration("1.2.0", "Insert test user"));

        MigrationContext context = createMigrationContext(migrations);

        MigrationResult result = migrationEngine.migrate(context);

        assertNotNull(result);
        assertEquals(3, result.getRecords().size());

        assertTrue(tableExists("users"));
        assertEquals(1, countRows("users"));
    }

    @Test
    void testMigrate_SkipExecutedMigrations() {
        DbMigrationModel migration = createCreateTableMigration("1.0.0", "Create users table");
        List<DbMigrationModel> migrations = Collections.singletonList(migration);

        MigrationContext context = createMigrationContext(migrations);

        MigrationResult result1 = migrationEngine.migrate(context);
        assertEquals(1, result1.getRecords().size());

        MigrationResult result2 = migrationEngine.migrate(context);
        assertEquals(0, result2.getRecords().size(), "Already executed migrations should be skipped");
    }

    @Test
    void testMigrate_WithFailedMigration() {
        DbMigrationModel migration = createInvalidMigration("1.0.0", "Invalid migration");
        List<DbMigrationModel> migrations = Collections.singletonList(migration);

        MigrationContext context = createMigrationContext(migrations);
        context.setFailFast(false);

        MigrationResult result = migrationEngine.migrate(context);

        assertNotNull(result);
        assertEquals(1, result.getRecords().size());
        assertFalse(result.getRecords().get(0).isSuccess());
    }

    @Test
    void testMigrate_FailFast() {
        DbMigrationModel migration1 = createCreateTableMigration("1.0.0", "Create table");
        DbMigrationModel migration2 = createInvalidMigration("1.1.0", "Invalid migration");
        DbMigrationModel migration3 = createInsertDataMigration("1.2.0", "Insert data");

        List<DbMigrationModel> migrations = new ArrayList<>();
        migrations.add(migration1);
        migrations.add(migration2);
        migrations.add(migration3);

        MigrationContext context = createMigrationContext(migrations);
        context.setFailFast(true);

        assertThrows(Exception.class, () -> {
            migrationEngine.migrate(context);
        }, "Should throw exception when failFast is true and migration fails");

        assertTrue(tableExists("users"), "First migration should have succeeded");
    }

    @Test
    void testRegisterExecutor() {
        DbMigrationModel migration = new DbMigrationModel();
        migration.setVersion("1.0.0");
        migration.setDescription("Custom migration");

        CreateTableChange change = new CreateTableChange();
        change.setId("custom-change-1");
        change.setType("customChange");

        migration.addChange(change);

        MigrationContext context = createMigrationContext(Collections.singletonList(migration));

        assertThrows(Exception.class, () -> {
            migrationEngine.migrate(context);
        }, "Should fail for unknown change type");
    }

    @Test
    void testMigrateRetryAfterFailureSucceeds() {
        // First run: the migration fails (unknown change type) and a failed
        // history record is written
        MigrationContext failingContext = createMigrationContext(
            Collections.singletonList(createInvalidMigration("1.0.0", "Invalid migration")));
        failingContext.setFailFast(false);
        MigrationResult failedResult = migrationEngine.migrate(failingContext);
        assertEquals(1, failedResult.getRecords().size());
        assertFalse(failedResult.getRecords().get(0).isSuccess());

        // Second run: the same version now succeeds. The old failed record
        // must be replaced instead of causing a primary key violation that
        // aborts the whole migration run
        MigrationContext retryContext = createMigrationContext(
            Collections.singletonList(createCreateTableMigration("1.0.0", "Fixed migration")));
        MigrationResult retryResult = assertDoesNotThrow(() -> migrationEngine.migrate(retryContext),
            "retry after a failed attempt must not hit a primary key conflict");
        assertEquals(1, retryResult.getRecords().size());
        assertTrue(retryResult.getRecords().get(0).isSuccess());

        MigrationHistoryManager historyManager = new MigrationHistoryManager(jdbcTemplate, "default");
        assertTrue(historyManager.getExecutedVersions().contains("1.0.0"),
            "the retried version must be recorded as executed");
    }

    @Test
    void testRegisterExecutorVisibleToDbTypeFilter() throws Exception {
        // Executors registered after engine construction must also be visible
        // to DbTypeFilterExecutor, which dispatches nested changes through its
        // own executor table
        List<String> executed = new ArrayList<>();
        migrationEngine.registerExecutor("probeType", (change, context, dialect) -> executed.add("probe"));

        java.lang.reflect.Field field = MigrationEngine.class.getDeclaredField("dbTypeFilterExecutor");
        field.setAccessible(true);
        DbTypeFilterExecutor dbTypeFilterExecutor = (DbTypeFilterExecutor) field.get(migrationEngine);

        DbTypeFilterChange filter = new DbTypeFilterChange();
        filter.setId("filter-1");
        filter.setType("dbTypeFilter");
        filter.setDbTypes(Collections.singleton(dialect.getName().toLowerCase()));
        DbChangeModel nested = newChange("probeType");
        filter.setChanges(Collections.singletonList(nested));

        MigrationContext context = createMigrationContext(Collections.emptyList());
        dbTypeFilterExecutor.execute(filter, context, dialect);

        assertEquals(Collections.singletonList("probe"), executed,
            "dbTypeFilter must dispatch to executors registered after construction");
    }

    @Test
    void testRollbackTwiceKeepsSameOrder() {
        List<String> order = new ArrayList<>();
        migrationEngine.registerExecutor("probeA", (change, context, dialect) -> order.add("A"));
        migrationEngine.registerExecutor("probeB", (change, context, dialect) -> order.add("B"));

        DbMigrationModel migration = new DbMigrationModel();
        migration.setVersion("1.0.0");
        migration.setDescription("Rollback order test");

        RollbackDefinition rollback = new RollbackDefinition();
        rollback.setChanges(new ArrayList<>(Arrays.asList(newChange("probeA"), newChange("probeB"))));
        migration.setRollback(rollback);

        MigrationContext context = createMigrationContext(Collections.emptyList());

        migrationEngine.rollback(migration, context);
        assertEquals(Arrays.asList("B", "A"), order, "rollback executes changes in reverse order");

        // Rolling back the same model again must observe the original order,
        // not the order mutated by the first rollback
        order.clear();
        migrationEngine.rollback(migration, context);
        assertEquals(Arrays.asList("B", "A"), order, "second rollback of the same model must keep the same order");
    }

    @Test
    void testErrorMessageFallsBackToStringForNullMessage() {
        // Exceptions like NPE carry a null message; the history record relies
        // on a non-null error message
        String message = MigrationEngine.errorMessage(new NullPointerException());
        assertNotNull(message);
        assertEquals("java.lang.NullPointerException", message);
    }

    private DbChangeModel newChange(String type) {
        // DbChangeModel is abstract; any concrete change subclass works as a
        // carrier for the probe change type
        CreateTableChange change = new CreateTableChange();
        change.setId(type);
        change.setType(type);
        return change;
    }

    private MigrationContext createMigrationContext(List<DbMigrationModel> migrations) {
        MigrationContext context = new MigrationContext();
        context.setJdbcTemplate(jdbcTemplate);
        context.setDialect(dialect);
        context.setQuerySpace("default");
        context.setInstalledBy("test-user");
        context.setMigrationPaths(new ArrayList<>());
        context.setMigrations(migrations);

        return context;
    }

    private DbMigrationModel createCreateTableMigration(String version, String description) {
        DbMigrationModel migration = new DbMigrationModel();
        migration.setVersion(version);
        migration.setDescription(description);

        CreateTableChange change = new CreateTableChange();
        change.setId("create-users-" + version);
        change.setType("createTable");
        change.setName("users");

        ColumnDefinition idColumn = new ColumnDefinition();
        idColumn.setName("id");
        idColumn.setType(StdSqlType.VARCHAR);
        idColumn.setSize(36);
        idColumn.setPrimaryKey(true);
        idColumn.setNullable(false);

        ColumnDefinition nameColumn = new ColumnDefinition();
        nameColumn.setName("name");
        nameColumn.setType(StdSqlType.VARCHAR);
        nameColumn.setSize(100);
        nameColumn.setNullable(false);

        change.setColumns(Arrays.asList(idColumn, nameColumn));

        List<DbChangeModel> changeset = new ArrayList<>();
        changeset.add(change);
        migration.setChangeset(changeset);

        return migration;
    }

    private DbMigrationModel createAddColumnMigration(String version, String description) {
        DbMigrationModel migration = new DbMigrationModel();
        migration.setVersion(version);
        migration.setDescription(description);

        AddColumnChange change = new AddColumnChange();
        change.setId("add-email-" + version);
        change.setType("addColumn");
        change.setTableName("users");

        ColumnDefinition emailColumn = new ColumnDefinition();
        emailColumn.setName("email");
        emailColumn.setType(StdSqlType.VARCHAR);
        emailColumn.setSize(200);
        emailColumn.setNullable(true);

        change.setColumns(Collections.singletonList(emailColumn));

        List<DbChangeModel> changeset = new ArrayList<>();
        changeset.add(change);
        migration.setChangeset(changeset);

        return migration;
    }

    private DbMigrationModel createInsertDataMigration(String version, String description) {
        DbMigrationModel migration = new DbMigrationModel();
        migration.setVersion(version);
        migration.setDescription(description);

        InsertDataChange change = new InsertDataChange();
        change.setId("insert-user-" + version);
        change.setType("insertData");
        change.setTableName("users");

        InsertColumnModel idColumn = new InsertColumnModel();
        idColumn.setName("id");
        idColumn.setValue("1");

        InsertColumnModel nameColumn = new InsertColumnModel();
        nameColumn.setName("name");
        nameColumn.setValue("Test User");

        change.setColumns(new ArrayList<>());
        change.getColumns().add(idColumn);
        change.getColumns().add(nameColumn);

        List<DbChangeModel> changeset = new ArrayList<>();
        changeset.add(change);
        migration.setChangeset(changeset);

        return migration;
    }

    private DbMigrationModel createInvalidMigration(String version, String description) {
        DbMigrationModel migration = new DbMigrationModel();
        migration.setVersion(version);
        migration.setDescription(description);

        CreateTableChange change = new CreateTableChange();
        change.setId("invalid-" + version);
        change.setType("invalidType");
        change.setName("invalid_table");

        List<DbChangeModel> changeset = new ArrayList<>();
        changeset.add(change);
        migration.setChangeset(changeset);

        return migration;
    }
}

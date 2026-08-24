/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.db.migration.core;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.type.StdSqlType;
import io.nop.dao.dialect.DialectManager;
import io.nop.db.migration.AbstractMigrationTestCase;
import io.nop.db.migration.PreconditionExpect;
import io.nop.db.migration.RunOnChange;
import io.nop.db.migration.executor.DbTypeFilterExecutor;
import io.nop.db.migration.model.SqlChange;
import io.nop.db.migration.model.TableExistsPrecondition;
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

    // ---- repeatable migration + checksum regression ----

    private DbMigrationModel createSqlMigration(String version, String description, String sqlBody) {
        DbMigrationModel migration = new DbMigrationModel();
        migration.setVersion(version);
        migration.setDescription(description);

        SqlChange change = new SqlChange();
        change.setId("sql-" + version);
        change.setType("sql");
        change.setBody(sqlBody);

        List<DbChangeModel> changeset = new ArrayList<>();
        changeset.add(change);
        migration.setChangeset(changeset);
        return migration;
    }

    @Test
    void testChecksumIncludesChangeContent() {
        DbMigrationModel a = createSqlMigration("1.0.0", "same", "INSERT INTO t VALUES (1)");
        DbMigrationModel b = createSqlMigration("1.0.0", "same", "INSERT INTO t VALUES (2)");

        assertNotEquals(migrationEngine.calculateChecksum(a), migrationEngine.calculateChecksum(b),
            "checksum must change when the change content changes, not only the type names");
    }

    @Test
    void testRepeatableMigrationReRunsWhenChecksumChanges() {
        executeSql("CREATE TABLE rep_log (id INT PRIMARY KEY, note VARCHAR(50))");

        MigrationResult first = migrationEngine.migrate(createMigrationContext(
            Collections.singletonList(createSqlMigration("R__rep", "repeatable", "INSERT INTO rep_log VALUES (1,'a')"))));
        assertEquals(1, first.getRecords().size());
        assertEquals(1, countRows("rep_log"));

        // unchanged content: skipped
        MigrationResult second = migrationEngine.migrate(createMigrationContext(
            Collections.singletonList(createSqlMigration("R__rep", "repeatable", "INSERT INTO rep_log VALUES (1,'a')"))));
        assertEquals(0, second.getRecords().size(), "unchanged repeatable migration must be skipped");
        assertEquals(1, countRows("rep_log"));

        // changed content: re-executed and history row updated
        MigrationResult third = migrationEngine.migrate(createMigrationContext(
            Collections.singletonList(createSqlMigration("R__rep", "repeatable", "INSERT INTO rep_log VALUES (2,'b')"))));
        assertEquals(1, third.getRecords().size(), "changed repeatable migration must re-run");
        assertTrue(third.getRecords().get(0).isSuccess());
        assertEquals(2, countRows("rep_log"), "re-run repeatable migration must execute its changes again");

        MigrationHistoryManager historyManager = new MigrationHistoryManager(jdbcTemplate, "default");
        assertTrue(historyManager.getExecutedVersions().contains("R__rep"),
            "the history row must be updated, not duplicated or lost");
    }

    @Test
    void testVersionedChecksumMismatchThrows() {
        executeSql("CREATE TABLE nowhere_a (id INT)");

        MigrationResult first = migrationEngine.migrate(createMigrationContext(
            Collections.singletonList(createSqlMigration("1.0.0", "original", "INSERT INTO nowhere_a VALUES (1)"))));
        assertEquals(1, first.getRecords().size());
        assertTrue(first.getRecords().get(0).isSuccess());

        MigrationContext context = createMigrationContext(
            Collections.singletonList(createSqlMigration("1.0.0", "original", "INSERT INTO nowhere_a VALUES (2)")));

        NopException e = assertThrows(NopException.class, () -> migrationEngine.migrate(context),
            "a versioned migration whose content changed must fail validation");
        assertEquals("nop.err.db-migration.checksum-mismatch", e.getErrorCode());
    }

    // ---- precondition wiring regression ----

    @Test
    void testFailedPreconditionBlocksMigration() {
        executeSql("CREATE TABLE existing_t (id INT PRIMARY KEY)");

        DbMigrationModel migration = createTableMigrationFor("1.0.0", "guarded migration", "ctl_guard_t");

        TableExistsPrecondition precondition = new TableExistsPrecondition();
        precondition.setId("pre-1");
        precondition.setTableName("existing_t");
        precondition.setExpect(PreconditionExpect.NOT_EXISTS);
        List<io.nop.db.migration.model.DbPreconditionModel> preconditions = new ArrayList<>();
        preconditions.add(precondition);
        migration.setPreconditions(preconditions);

        MigrationContext context = createMigrationContext(Collections.singletonList(migration));
        context.setFailFast(false);

        MigrationResult result = migrationEngine.migrate(context);

        assertFalse(tableExists("ctl_guard_t"),
            "a migration whose precondition fails must not execute its changeset");
        assertEquals(1, result.getRecords().size());
        assertFalse(result.getRecords().get(0).isSuccess(),
            "precondition failure must be recorded as a failed migration");
    }

    @Test
    void testSatisfiedPreconditionAllowsMigration() {
        executeSql("CREATE TABLE existing_t2 (id INT PRIMARY KEY)");

        DbMigrationModel migration = createTableMigrationFor("1.0.0", "guarded migration", "ctl_guard2_t");

        TableExistsPrecondition precondition = new TableExistsPrecondition();
        precondition.setId("pre-1");
        precondition.setTableName("existing_t2");
        precondition.setExpect(PreconditionExpect.EXISTS);
        List<io.nop.db.migration.model.DbPreconditionModel> preconditions = new ArrayList<>();
        preconditions.add(precondition);
        migration.setPreconditions(preconditions);

        MigrationResult result = migrationEngine.migrate(createMigrationContext(Collections.singletonList(migration)));

        assertTrue(result.getRecords().get(0).isSuccess());
        assertTrue(tableExists("ctl_guard2_t"));
    }

    // ---- migration control fields regression ----

    @Test
    void testIgnoreSkipsMigration() {
        DbMigrationModel migration = createTableMigrationFor("1.0.0", "ignored", "ctl_ignore_t");
        migration.setIgnore(true);

        MigrationResult result = migrationEngine.migrate(createMigrationContext(Collections.singletonList(migration)));

        assertEquals(0, result.getRecords().size(), "ignore=true must skip the migration");
        assertFalse(tableExists("ctl_ignore_t"), "ignored migration must not execute");
    }

    @Test
    void testRunOnNeverSkipsMigration() {
        DbMigrationModel migration = createTableMigrationFor("1.0.0", "never", "ctl_never_t");
        migration.setRunOn(RunOnChange.NEVER);

        MigrationResult result = migrationEngine.migrate(createMigrationContext(Collections.singletonList(migration)));

        assertEquals(0, result.getRecords().size(), "runOn=never must skip the migration");
        assertFalse(tableExists("ctl_never_t"));
    }

    @Test
    void testContextMismatchSkipsMigration() {
        DbMigrationModel migration = createTableMigrationFor("1.0.0", "test only", "ctl_ctx_t");
        migration.setContexts("test");

        MigrationContext context = createMigrationContext(Collections.singletonList(migration));
        context.setContext("prod");

        MigrationResult result = migrationEngine.migrate(context);

        assertEquals(0, result.getRecords().size(),
            "a migration for another context must not run in this context");
        assertFalse(tableExists("ctl_ctx_t"));
    }

    @Test
    void testLabelMismatchSkipsMigration() {
        DbMigrationModel migration = createTableMigrationFor("1.0.0", "prod labeled", "ctl_label_t");
        migration.setLabels("prod");

        MigrationContext context = createMigrationContext(Collections.singletonList(migration));
        context.setLabels(Collections.singletonList("dev"));

        MigrationResult result = migrationEngine.migrate(context);

        assertEquals(0, result.getRecords().size(),
            "when the context declares labels, a migration without a shared label must not run");
        assertFalse(tableExists("ctl_label_t"));
    }

    @Test
    void testFailOnErrorFalseContinuesAfterFailure() {
        DbMigrationModel failing = createInvalidMigration("1.0.0", "fails");
        failing.setFailOnError(false);
        DbMigrationModel succeeding = createCreateTableMigration("1.1.0", "runs after failure");

        MigrationContext context = createMigrationContext(Arrays.asList(failing, succeeding));
        context.setFailFast(true);

        MigrationResult result = assertDoesNotThrow(() -> migrationEngine.migrate(context),
            "failOnError=false must override the global failFast for this migration");
        assertEquals(2, result.getRecords().size());
        assertFalse(result.getRecords().get(0).isSuccess());
        assertTrue(result.getRecords().get(1).isSuccess());
        assertTrue(tableExists("users"), "the migration after the tolerated failure must still run");
    }

    // ---- result aggregation regression ----

    @Test
    void testResultSuccessIsFalseWhenAnyRecordFailed() {
        MigrationContext context = createMigrationContext(
            Collections.singletonList(createInvalidMigration("1.0.0", "Invalid migration")));
        context.setFailFast(false);

        MigrationResult result = migrationEngine.migrate(context);

        assertEquals(1, result.getRecords().size());
        assertFalse(result.isSuccess(),
            "a result containing a failed record must not report overall success");
        assertEquals(0, result.getExecutedCount(),
            "failed records must not be counted as executed");
    }

    // ---- dbType alias regression ----

    @Test
    void testSqlServerAliasMatchesMssqlDialect() throws Exception {
        List<String> executed = new ArrayList<>();

        DbTypeFilterChange filter = new DbTypeFilterChange();
        filter.setId("filter-1");
        filter.setType("dbTypeFilter");
        // the xdef documentation advertised "sqlserver" while the dialect
        // registry names it "mssql"
        filter.setDbTypes(Collections.singleton("sqlserver"));
        DbChangeModel nested = newChange("probeType");
        filter.setChanges(Collections.singletonList(nested));

        MigrationContext context = createMigrationContext(Collections.emptyList());
        migrationEngine.registerExecutor("probeType", (change, ctx, dialect) -> executed.add("probe"));

        java.lang.reflect.Field field = MigrationEngine.class.getDeclaredField("dbTypeFilterExecutor");
        field.setAccessible(true);
        DbTypeFilterExecutor dbTypeFilterExecutor = (DbTypeFilterExecutor) field.get(migrationEngine);

        dbTypeFilterExecutor.execute(filter, context, DialectManager.instance().getDialect("mssql"));

        assertEquals(Collections.singletonList("probe"), executed,
            "dbTypes=\"sqlserver\" must match the mssql dialect");
    }

    // ---- caller list immutability regression ----

    @Test
    void testMigrateDoesNotMutateCallerListAndAcceptsImmutableList() {
        DbMigrationModel second = createCreateTableMigration("1.1.0", "second");
        // distinct table so both migrations succeed
        CreateTableChange change = new CreateTableChange();
        change.setId("create-first");
        change.setType("createTable");
        change.setName("first_table");
        ColumnDefinition idColumn = new ColumnDefinition();
        idColumn.setName("id");
        idColumn.setType(StdSqlType.VARCHAR);
        idColumn.setSize(36);
        idColumn.setPrimaryKey(true);
        idColumn.setNullable(false);
        change.setColumns(Collections.singletonList(idColumn));
        DbMigrationModel first = new DbMigrationModel();
        first.setVersion("1.0.0");
        first.setDescription("first");
        first.setChangeset(Collections.singletonList(change));

        // immutable input: Collections.sort on it used to throw
        // UnsupportedOperationException
        MigrationResult result = assertDoesNotThrow(() ->
            migrationEngine.migrate(createMigrationContext(List.of(second, first))),
            "migrate must not sort the caller-provided list in place");

        assertEquals(2, result.getRecords().size());
        assertTrue(tableExists("first_table"));
        assertTrue(tableExists("users"));
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

    private DbMigrationModel createTableMigrationFor(String version, String description, String tableName) {
        DbMigrationModel migration = new DbMigrationModel();
        migration.setVersion(version);
        migration.setDescription(description);

        CreateTableChange change = new CreateTableChange();
        change.setId("create-" + tableName + "-" + version);
        change.setType("createTable");
        change.setName(tableName);

        ColumnDefinition idColumn = new ColumnDefinition();
        idColumn.setName("id");
        idColumn.setType(StdSqlType.VARCHAR);
        idColumn.setSize(36);
        idColumn.setPrimaryKey(true);
        idColumn.setNullable(false);
        change.setColumns(Collections.singletonList(idColumn));

        migration.setChangeset(Collections.singletonList(change));
        return migration;
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

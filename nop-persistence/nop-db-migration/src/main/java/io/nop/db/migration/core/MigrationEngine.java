/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical-entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.db.migration.core;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.resource.component.AbstractComponentModel;
import io.nop.dao.dialect.IDialect;
import io.nop.db.migration.RunOnChange;
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
import io.nop.db.migration.executor.IChangeExecutor;
import io.nop.db.migration.executor.InsertDataExecutor;
import io.nop.db.migration.executor.RenameTableExecutor;
import io.nop.db.migration.executor.SqlExecutor;
import io.nop.db.migration.executor.UpdateDataExecutor;
import io.nop.db.migration.model.ColumnExistsPrecondition;
import io.nop.db.migration.model.CustomConditionPrecondition;
import io.nop.db.migration.model.DbChangeModel;
import io.nop.db.migration.model.DbMigrationModel;
import io.nop.db.migration.model.ForeignKeyExistsPrecondition;
import io.nop.db.migration.model.IndexExistsPrecondition;
import io.nop.db.migration.model.TableExistsPrecondition;
import io.nop.db.migration.precondition.ColumnExistsChecker;
import io.nop.db.migration.precondition.CustomConditionChecker;
import io.nop.db.migration.precondition.ForeignKeyExistsChecker;
import io.nop.db.migration.precondition.IPreconditionChecker;
import io.nop.db.migration.precondition.IndexExistsChecker;
import io.nop.db.migration.precondition.TableExistsChecker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.nop.db.migration.DbMigrationErrors.ARG_CHANGE_TYPE;
import static io.nop.db.migration.DbMigrationErrors.ARG_CHECKSUM;
import static io.nop.db.migration.DbMigrationErrors.ARG_EXPECTED_CHECKSUM;
import static io.nop.db.migration.DbMigrationErrors.ARG_PRECONDITION_TYPE;
import static io.nop.db.migration.DbMigrationErrors.ARG_VERSION;
import static io.nop.db.migration.DbMigrationErrors.ERR_DB_MIGRATION_CHECKSUM_MISMATCH;
import static io.nop.db.migration.DbMigrationErrors.ERR_DB_MIGRATION_PRECONDITION_FAILED;
import static io.nop.db.migration.DbMigrationErrors.ERR_DB_MIGRATION_UNKNOWN_CHANGE_TYPE;

public class MigrationEngine {

    private final MigrationFileScanner scanner = new MigrationFileScanner();
    private final MigrationExecutor executor = new MigrationExecutor();
    private final Map<String, IChangeExecutor> changeExecutors = new HashMap<>();
    private final Map<Class<?>, IPreconditionChecker> preconditionCheckers = new HashMap<>();
    private DbTypeFilterExecutor dbTypeFilterExecutor;

    public MigrationEngine() {
        registerDefaultExecutors();
        registerDefaultPreconditionCheckers();
    }

    protected void registerDefaultExecutors() {
        registerExecutor(CreateTableExecutor.CHANGE_TYPE, new CreateTableExecutor());
        registerExecutor(DropTableExecutor.CHANGE_TYPE, new DropTableExecutor());
        registerExecutor(RenameTableExecutor.CHANGE_TYPE, new RenameTableExecutor());
        registerExecutor(AddColumnExecutor.CHANGE_TYPE, new AddColumnExecutor());
        registerExecutor(DropColumnExecutor.CHANGE_TYPE, new DropColumnExecutor());
        registerExecutor(AlterColumnExecutor.CHANGE_TYPE, new AlterColumnExecutor());
        registerExecutor(CreateIndexExecutor.CHANGE_TYPE, new CreateIndexExecutor());
        registerExecutor(DropIndexExecutor.CHANGE_TYPE, new DropIndexExecutor());
        registerExecutor(CreateViewExecutor.CHANGE_TYPE, new CreateViewExecutor());
        registerExecutor(DropViewExecutor.CHANGE_TYPE, new DropViewExecutor());
        registerExecutor(SqlExecutor.CHANGE_TYPE, new SqlExecutor());
        registerExecutor(InsertDataExecutor.CHANGE_TYPE, new InsertDataExecutor());
        registerExecutor(UpdateDataExecutor.CHANGE_TYPE, new UpdateDataExecutor());
        registerExecutor(DeleteDataExecutor.CHANGE_TYPE, new DeleteDataExecutor());
        registerExecutor(CustomChangeExecutor.CHANGE_TYPE, new CustomChangeExecutor());

        dbTypeFilterExecutor = new DbTypeFilterExecutor();
        for (Map.Entry<String, IChangeExecutor> entry : changeExecutors.entrySet()) {
            dbTypeFilterExecutor.registerExecutor(entry.getKey(), entry.getValue());
        }
        registerExecutor(DbTypeFilterExecutor.CHANGE_TYPE, dbTypeFilterExecutor);
    }

    protected void registerDefaultPreconditionCheckers() {
        registerPreconditionChecker(TableExistsPrecondition.class, new TableExistsChecker());
        registerPreconditionChecker(ColumnExistsPrecondition.class, new ColumnExistsChecker());
        registerPreconditionChecker(IndexExistsPrecondition.class, new IndexExistsChecker());
        registerPreconditionChecker(ForeignKeyExistsPrecondition.class, new ForeignKeyExistsChecker());
        registerPreconditionChecker(CustomConditionPrecondition.class, new CustomConditionChecker());
    }

    public void registerPreconditionChecker(Class<?> preconditionClass, IPreconditionChecker checker) {
        preconditionCheckers.put(preconditionClass, checker);
    }

    public void registerExecutor(String changeType, IChangeExecutor executor) {
        changeExecutors.put(changeType, executor);
        this.executor.registerExecutor(changeType, executor);
        // dbTypeFilter dispatches nested changes through its own executor table,
        // so executors registered after construction must be visible there too
        if (dbTypeFilterExecutor != null) {
            dbTypeFilterExecutor.registerExecutor(changeType, executor);
        }
    }

    public MigrationResult migrate(MigrationContext context) {
        MigrationHistoryManager localHistoryManager = new MigrationHistoryManager(
            context.getJdbcTemplate(),
            context.getQuerySpace()
        );

        localHistoryManager.ensureHistoryTableExists(context.getDialect());

        List<DbMigrationModel> migrations;
        if (context.getMigrations() != null && !context.getMigrations().isEmpty()) {
            migrations = context.getMigrations();
        } else {
            migrations = scanner.scan(context.getMigrationPaths());
        }

        // never sort the caller's list in place: an immutable list (List.of)
        // would throw UnsupportedOperationException, and a mutable one must
        // not have its order mutated as a side effect
        migrations = new ArrayList<>(migrations);
        Collections.sort(migrations, MigrationVersionComparator.INSTANCE);

        Set<String> executedVersions = localHistoryManager.getExecutedVersions();

        MigrationResult result = new MigrationResult();

        for (DbMigrationModel migration : migrations) {
            String version = migration.getVersion();

            if (migration.isIgnore()) {
                continue;
            }
            if (migration.getRunOn() == RunOnChange.NEVER) {
                continue;
            }
            if (!context.matchesContext(migration.getContexts())) {
                continue;
            }
            if (!matchesLabels(context, migration)) {
                continue;
            }

            boolean repeatable = MigrationVersionComparator.isRepeatable(version);
            if (executedVersions.contains(version)) {
                MigrationRecord recorded = localHistoryManager.getMigrationByVersion(version);
                String newChecksum = calculateChecksum(migration);
                if (recorded != null && recorded.getChecksum() != null) {
                    if (!recorded.getChecksum().equals(newChecksum)) {
                        if (repeatable) {
                            // repeatable migrations re-run when their content
                            // changed (Flyway semantics)
                            executeAndRecord(migration, context, localHistoryManager, result);
                            continue;
                        }
                        if (context.isValidateChecksum()) {
                            throw new NopException(ERR_DB_MIGRATION_CHECKSUM_MISMATCH)
                                .param(ARG_VERSION, version)
                                .param(ARG_EXPECTED_CHECKSUM, recorded.getChecksum())
                                .param(ARG_CHECKSUM, newChecksum);
                        }
                    }
                }
                continue;
            }

            executeAndRecord(migration, context, localHistoryManager, result);
        }

        return result;
    }

    /**
     * Executes the migration and records the result. Failures are recorded as
     * failed history rows; the exception is rethrown when the fail-fast policy
     * (context flag AND the migration's own failOnError) requires it.
     */
    private void executeAndRecord(DbMigrationModel migration, MigrationContext context,
                                  MigrationHistoryManager historyManager, MigrationResult result) {
        String version = migration.getVersion();
        try {
            MigrationRecord record = executeMigration(migration, context);
            historyManager.recordMigration(record);
            result.addRecord(record);
        } catch (Exception e) {
            MigrationRecord failedRecord = new MigrationRecord();
            failedRecord.setVersion(version);
            failedRecord.setDescription(migration.getDescription());
            failedRecord.setSuccess(false);
            failedRecord.setErrorMessage(errorMessage(e));

            historyManager.recordMigration(failedRecord);
            result.addRecord(failedRecord);

            // a migration can opt out of fail-fast per migration via
            // failOnError="false"; the global context flag alone no longer
            // overrides the per-migration contract
            if (context.isFailFast() && migration.isFailOnError()) {
                throw NopException.adapt(e);
            }
        }
    }

    /**
     * Labels filter: when the context declares labels, a migration declaring
     * labels must share at least one of them. Migrations without labels always
     * run.
     */
    private boolean matchesLabels(MigrationContext context, DbMigrationModel migration) {
        List<String> contextLabels = context.getLabels();
        if (contextLabels == null || contextLabels.isEmpty() || StringHelper.isBlank(migration.getLabels())) {
            return true;
        }
        for (String label : migration.getLabels().split(",")) {
            if (contextLabels.contains(label.trim())) {
                return true;
            }
        }
        return false;
    }

    protected MigrationRecord executeMigration(DbMigrationModel migration, MigrationContext context) {
        long startTime = System.currentTimeMillis();

        checkPreconditions(migration, context);

        List<DbChangeModel> changeset = migration.getChangeset();
        if (changeset != null) {
            for (DbChangeModel change : changeset) {
                executeChange(change, context);
            }
        }

        long executionTime = System.currentTimeMillis() - startTime;

        MigrationRecord record = new MigrationRecord();
        record.setVersion(migration.getVersion());
        record.setDescription(migration.getDescription());
        record.setType(MigrationVersionComparator.isRepeatable(migration.getVersion()) ? "REPEATABLE" : "VERSIONED");
        record.setChecksum(calculateChecksum(migration));
        record.setExecutionTime(executionTime);
        record.setInstalledBy(context.getInstalledBy());
        record.setSuccess(true);

        return record;
    }

    /**
     * Preconditions declared on the migration must all pass before the
     * changeset executes. The list is traversed as plain objects because the
     * concrete precondition classes only extend DbPreconditionModel after the
     * _gen models are regenerated from the current migration.xdef.
     */
    protected void checkPreconditions(DbMigrationModel migration, MigrationContext context) {
        List<?> preconditions = migration.getPreconditions();
        if (preconditions == null || preconditions.isEmpty()) {
            return;
        }
        for (Object precondition : preconditions) {
            IPreconditionChecker checker = preconditionCheckers.get(precondition.getClass());
            if (checker == null) {
                continue;
            }
            boolean passed = checker.check((AbstractComponentModel) precondition, context);
            if (!passed) {
                throw new NopException(ERR_DB_MIGRATION_PRECONDITION_FAILED)
                    .param(ARG_PRECONDITION_TYPE, checker.getPreconditionType())
                    .param(ARG_VERSION, migration.getVersion());
            }
        }
    }

    protected void executeChange(DbChangeModel change, MigrationContext context) {
        String changeType = change.getType();
        if (changeType == null || changeType.isEmpty()) {
            return;
        }

        IChangeExecutor executor = changeExecutors.get(changeType);
        if (executor == null) {
            throw new NopException(ERR_DB_MIGRATION_UNKNOWN_CHANGE_TYPE)
                .param(ARG_CHANGE_TYPE, changeType);
        }

        IDialect dialect = context.getDialect();
        executor.execute(change, context, dialect);
    }

    /**
     * The checksum covers the full serialized changeset so that any content
     * change (SQL text, column definitions, values) is detected. The previous
     * implementation only concatenated the change type names, which made the
     * checksum invariant to content changes.
     */
    protected String calculateChecksum(DbMigrationModel migration) {
        StringBuilder sb = new StringBuilder();
        if (migration.getVersion() != null) {
            sb.append(migration.getVersion());
        }
        if (migration.getDescription() != null) {
            sb.append(migration.getDescription());
        }

        if (migration.getChangeset() != null) {
            for (DbChangeModel change : migration.getChangeset()) {
                if (change.getType() != null) {
                    sb.append(change.getType());
                }
                sb.append(JsonTool.stringify(change));
            }
        }

        return StringHelper.md5Hash(sb.toString());
    }

    public MigrationResult rollback(DbMigrationModel migration, MigrationContext context) {
        long startTime = System.currentTimeMillis();

        MigrationHistoryManager localHistoryManager = new MigrationHistoryManager(
            context.getJdbcTemplate(),
            context.getQuerySpace()
        );

        MigrationResult result = new MigrationResult();

        if (migration.getRollback() == null || migration.getRollback().getChanges() == null) {
            MigrationRecord record = new MigrationRecord();
            record.setVersion(migration.getVersion());
            record.setDescription("No rollback defined");
            record.setSuccess(false);
            record.setErrorMessage("No rollback changes defined for this migration");
            result.addRecord(record);
            return result;
        }

        if (Boolean.TRUE.equals(migration.getRollback().isImpossible())) {
            MigrationRecord record = new MigrationRecord();
            record.setVersion(migration.getVersion());
            record.setDescription("Rollback marked as impossible");
            record.setSuccess(false);
            record.setErrorMessage("This migration is marked as impossible to rollback");
            result.addRecord(record);
            return result;
        }

        try {
            // Reverse a copy: the parsed model may be cached and reused, so a
            // second rollback of the same model must observe the original order
            List<DbChangeModel> rollbackChanges = new ArrayList<>(migration.getRollback().getChanges());
            Collections.reverse(rollbackChanges);

            for (DbChangeModel change : rollbackChanges) {
                executeChange(change, context);
            }

            localHistoryManager.removeMigrationRecord(migration.getVersion());

            long executionTime = System.currentTimeMillis() - startTime;

            MigrationRecord record = new MigrationRecord();
            record.setVersion(migration.getVersion());
            record.setDescription("Rollback: " + migration.getDescription());
            record.setType("ROLLBACK");
            record.setExecutionTime(executionTime);
            record.setInstalledBy(context.getInstalledBy());
            record.setSuccess(true);

            result.addRecord(record);

        } catch (Exception e) {
            MigrationRecord record = new MigrationRecord();
            record.setVersion(migration.getVersion());
            record.setDescription("Rollback failed: " + migration.getDescription());
            record.setSuccess(false);
            record.setErrorMessage(errorMessage(e));

            result.addRecord(record);

            if (context.isFailFast()) {
                throw NopException.adapt(e);
            }
        }

        return result;
    }

    /**
     * Some exceptions (e.g. NPE) carry a null message. The history record uses
     * a non-null error message to distinguish failed records from successful
     * ones, so fall back to the exception's toString() form.
     */
    static String errorMessage(Exception e) {
        return StringHelper.isEmpty(e.getMessage()) ? e.toString() : e.getMessage();
    }
}

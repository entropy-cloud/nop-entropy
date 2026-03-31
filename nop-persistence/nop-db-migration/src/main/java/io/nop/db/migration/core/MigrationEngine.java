/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.db.migration.core;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.component.AbstractComponentModel;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.db.migration.MigrationType;
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
import io.nop.db.migration.model.DbChangeModel;
import io.nop.db.migration.model.DbMigrationModel;
import io.nop.db.migration.model.ColumnExistsPrecondition;
import io.nop.db.migration.model.CustomConditionPrecondition;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static io.nop.db.migration.DbMigrationErrors.ARG_CHECKSUM;
import static io.nop.db.migration.DbMigrationErrors.ARG_CHANGE_TYPE;
import static io.nop.db.migration.DbMigrationErrors.ARG_EXPECTED_CHECKSUM;
import static io.nop.db.migration.DbMigrationErrors.ARG_PRECONDITION_TYPE;
import static io.nop.db.migration.DbMigrationErrors.ARG_VERSION;
import static io.nop.db.migration.DbMigrationErrors.ERR_DB_MIGRATION_CHECKSUM_MISMATCH;
import static io.nop.db.migration.DbMigrationErrors.ERR_DB_MIGRATION_PRECONDITION_FAILED;
import static io.nop.db.migration.DbMigrationErrors.ERR_DB_MIGRATION_UNKNOWN_CHANGE_TYPE;

public class MigrationEngine {
    
    private MigrationHistoryManager historyManager;
    private final MigrationFileScanner scanner = new MigrationFileScanner();
    private final MigrationExecutor executor = new MigrationExecutor();
    private final Map<String, IChangeExecutor> changeExecutors = new HashMap<>();
    private final Map<Class<?>, IPreconditionChecker> preconditionCheckers = new HashMap<>();
    
    public MigrationEngine() {
        registerDefaultExecutors();
    }
    
    public MigrationEngine(IJdbcTemplate jdbcTemplate, String querySpace) {
        this.historyManager = new MigrationHistoryManager(jdbcTemplate, querySpace);
        registerDefaultExecutors();
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
        
        DbTypeFilterExecutor dbTypeFilterExecutor = new DbTypeFilterExecutor();
        for (Map.Entry<String, IChangeExecutor> entry : changeExecutors.entrySet()) {
            dbTypeFilterExecutor.registerExecutor(entry.getKey(), entry.getValue());
        }
        registerExecutor(DbTypeFilterExecutor.CHANGE_TYPE, dbTypeFilterExecutor);

        registerPreconditionChecker(TableExistsPrecondition.class, new TableExistsChecker());
        registerPreconditionChecker(ColumnExistsPrecondition.class, new ColumnExistsChecker());
        registerPreconditionChecker(IndexExistsPrecondition.class, new IndexExistsChecker());
        registerPreconditionChecker(ForeignKeyExistsPrecondition.class, new ForeignKeyExistsChecker());
        registerPreconditionChecker(CustomConditionPrecondition.class, new CustomConditionChecker());
    }
    
    public MigrationHistoryManager getHistoryManager() {
        return historyManager;
    }
    
    public void registerExecutor(String changeType, IChangeExecutor executor) {
        changeExecutors.put(changeType, executor);
        this.executor.registerExecutor(changeType, executor);
    }

    public void registerPreconditionChecker(Class<?> preconditionClass,
                                            IPreconditionChecker checker) {
        preconditionCheckers.put(preconditionClass, checker);
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
        
        Collections.sort(migrations, MigrationVersionComparator.INSTANCE);
        
        MigrationResult result = new MigrationResult();
        
        for (DbMigrationModel migration : migrations) {
            if (migration == null || migration.isIgnore()) {
                continue;
            }

            if (!matchesMigrationContext(migration, context) || !matchesMigrationLabels(migration, context)) {
                continue;
            }

            String version = migration.getVersion();

            MigrationRecord existingRecord = localHistoryManager.getMigrationByVersion(version);
            String checksum = calculateChecksum(migration);

            if (shouldSkipMigration(migration, existingRecord, checksum, context)) {
                continue;
            }
            
            try {
                MigrationRecord record = executeMigration(migration, context, checksum);
                localHistoryManager.recordMigration(record);
                result.addRecord(record);
            } catch (Exception e) {
                MigrationRecord failedRecord = new MigrationRecord();
                failedRecord.setVersion(version);
                failedRecord.setDescription(migration.getDescription());
                failedRecord.setSuccess(false);
                failedRecord.setErrorMessage(e.getMessage());
                
                localHistoryManager.recordMigration(failedRecord);
                result.addRecord(failedRecord);
                
                if (shouldStopOnFailure(migration, context)) {
                    throw NopException.adapt(e);
                }
            }
        }
        
        return result;
    }

    protected boolean shouldSkipMigration(DbMigrationModel migration,
                                          MigrationRecord existingRecord,
                                          String currentChecksum,
                                          MigrationContext context) {
        RunOnChange runOn = migration.getRunOn();
        if (runOn == RunOnChange.NEVER) {
            return true;
        }

        if (existingRecord == null) {
            return false;
        }

        if (!existingRecord.isSuccess()) {
            return false;
        }

        if (runOn == RunOnChange.ALWAYS) {
            return false;
        }

        boolean repeatable = isRepeatableMigration(migration);
        if (repeatable) {
            return Objects.equals(existingRecord.getChecksum(), currentChecksum);
        }

        if (runOn == RunOnChange.ON_CHANGE) {
            return Objects.equals(existingRecord.getChecksum(), currentChecksum);
        }

        if (context.isValidateChecksum() && !Objects.equals(existingRecord.getChecksum(), currentChecksum)) {
            throw new NopException(ERR_DB_MIGRATION_CHECKSUM_MISMATCH)
                .param(ARG_VERSION, migration.getVersion())
                .param(ARG_EXPECTED_CHECKSUM, existingRecord.getChecksum())
                .param(ARG_CHECKSUM, currentChecksum);
        }

        return true;
    }

    protected boolean isRepeatableMigration(DbMigrationModel migration) {
        if (migration.getType() == MigrationType.REPEATABLE) {
            return true;
        }
        if (migration.getType() == MigrationType.VERSIONED) {
            return false;
        }
        return MigrationVersionComparator.isRepeatable(migration.getVersion());
    }

    protected boolean shouldStopOnFailure(DbMigrationModel migration, MigrationContext context) {
        if (!context.isFailFast()) {
            return false;
        }
        return migration.isFailOnError();
    }

    protected boolean matchesMigrationContext(DbMigrationModel migration, MigrationContext context) {
        String contexts = migration.getContexts();
        if (contexts == null || contexts.trim().isEmpty()) {
            return true;
        }

        String currentContext = context.getContext();
        if (currentContext == null || currentContext.trim().isEmpty()) {
            return false;
        }

        Set<String> contextSet = parseCsvSet(contexts);
        return contextSet.contains(currentContext.trim());
    }

    protected boolean matchesMigrationLabels(DbMigrationModel migration, MigrationContext context) {
        String labels = migration.getLabels();
        if (labels == null || labels.trim().isEmpty()) {
            return true;
        }

        List<String> currentLabels = context.getLabels();
        if (currentLabels == null || currentLabels.isEmpty()) {
            return false;
        }

        Set<String> required = parseCsvSet(labels);
        for (String label : currentLabels) {
            if (label != null && required.contains(label.trim())) {
                return true;
            }
        }
        return false;
    }

    protected Set<String> parseCsvSet(String csv) {
        if (csv == null || csv.trim().isEmpty()) {
            return Collections.emptySet();
        }

        return Arrays.stream(csv.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toSet());
    }
    
    protected MigrationRecord executeMigration(DbMigrationModel migration, MigrationContext context, String checksum) {
        long startTime = System.currentTimeMillis();

        validatePreconditions(migration, context);
        
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
        record.setType(isRepeatableMigration(migration) ? "REPEATABLE" : "VERSIONED");
        record.setChecksum(checksum);
        record.setExecutionTime(executionTime);
        record.setInstalledBy(context.getInstalledBy());
        record.setSuccess(true);
        
        return record;
    }

    protected void validatePreconditions(DbMigrationModel migration, MigrationContext context) {
        List<?> preconditions = migration.getPreconditions();
        if (preconditions == null || preconditions.isEmpty()) {
            return;
        }

        for (Object item : preconditions) {
            if (!(item instanceof AbstractComponentModel)) {
                throw new NopException(ERR_DB_MIGRATION_PRECONDITION_FAILED)
                    .param(ARG_VERSION, migration.getVersion())
                    .param(ARG_PRECONDITION_TYPE, item == null ? null : item.getClass().getName());
            }

            AbstractComponentModel precondition = (AbstractComponentModel) item;
            IPreconditionChecker checker = resolvePreconditionChecker(precondition);
            if (checker == null || !checker.check(precondition, context)) {
                throw new NopException(ERR_DB_MIGRATION_PRECONDITION_FAILED)
                    .param(ARG_VERSION, migration.getVersion())
                    .param(ARG_PRECONDITION_TYPE, precondition.getClass().getSimpleName());
            }
        }
    }

    protected IPreconditionChecker resolvePreconditionChecker(AbstractComponentModel precondition) {
        if (precondition == null) {
            return null;
        }

        for (Map.Entry<Class<?>, IPreconditionChecker> entry : preconditionCheckers.entrySet()) {
            if (entry.getKey().isInstance(precondition)) {
                return entry.getValue();
            }
        }
        return null;
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
            record.setErrorMessage(e.getMessage());
            
            result.addRecord(record);
            
            if (context.isFailFast()) {
                throw NopException.adapt(e);
            }
        }
        
        return result;
    }
}

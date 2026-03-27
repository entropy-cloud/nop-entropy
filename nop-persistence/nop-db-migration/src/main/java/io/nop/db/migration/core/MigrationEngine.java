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
import io.nop.dao.dialect.IDialect;
import io.nop.dao.jdbc.IJdbcTemplate;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.nop.db.migration.DbMigrationErrors.ARG_CHANGE_TYPE;
import static io.nop.db.migration.DbMigrationErrors.ARG_VERSION;
import static io.nop.db.migration.DbMigrationErrors.ERR_DB_MIGRATION_CHECKSUM_MISMATCH;
import static io.nop.db.migration.DbMigrationErrors.ERR_DB_MIGRATION_EXECUTION_FAILED;
import static io.nop.db.migration.DbMigrationErrors.ERR_DB_MIGRATION_UNKNOWN_CHANGE_TYPE;

public class MigrationEngine {
    
    private MigrationHistoryManager historyManager;
    private final MigrationFileScanner scanner = new MigrationFileScanner();
    private final MigrationExecutor executor = new MigrationExecutor();
    private final Map<String, IChangeExecutor> changeExecutors = new HashMap<>();
    
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
    }
    
    public MigrationHistoryManager getHistoryManager() {
        return historyManager;
    }
    
    public void registerExecutor(String changeType, IChangeExecutor executor) {
        changeExecutors.put(changeType, executor);
        this.executor.registerExecutor(changeType, executor);
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
        
        Set<String> executedVersions = localHistoryManager.getExecutedVersions();
        
        MigrationResult result = new MigrationResult();
        
        for (DbMigrationModel migration : migrations) {
            String version = migration.getVersion();
            
            if (executedVersions.contains(version)) {
                continue;
            }
            
            try {
                MigrationRecord record = executeMigration(migration, context);
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
                
                if (context.isFailFast()) {
                    throw NopException.adapt(e);
                }
            }
        }
        
        return result;
    }
    
    protected MigrationRecord executeMigration(DbMigrationModel migration, MigrationContext context) {
        long startTime = System.currentTimeMillis();
        
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
            List<DbChangeModel> rollbackChanges = migration.getRollback().getChanges();
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

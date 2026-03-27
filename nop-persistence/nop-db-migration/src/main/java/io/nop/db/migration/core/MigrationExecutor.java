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
import io.nop.db.migration.DbMigrationErrors;
import io.nop.db.migration.executor.IChangeExecutor;
import io.nop.db.migration.model.DbChangeModel;
import io.nop.db.migration.model.DbMigrationModel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.nop.db.migration.DbMigrationErrors.ARG_CHANGE_TYPE;
import static io.nop.db.migration.DbMigrationErrors.ERR_DB_MIGRATION_UNKNOWN_CHANGE_TYPE;

public class MigrationExecutor {
    
    private Map<String, IChangeExecutor> executors = new HashMap<>();
    
    public void setExecutors(Map<String, IChangeExecutor> executors) {
        this.executors = executors != null ? executors : new HashMap<>();
    }
    
    public void registerExecutor(String changeType, IChangeExecutor executor) {
        executors.put(changeType, executor);
    }
    
    public MigrationRecord execute(DbMigrationModel migration, MigrationContext context) {
        long startTime = System.currentTimeMillis();
        
        try {
            List<DbChangeModel> changeset = migration.getChangeset();
            if (changeset != null) {
                for (DbChangeModel change : changeset) {
                    executeChange(change, context);
                }
            }
            
            String checksum = calculateChecksum(migration);
            long executionTime = System.currentTimeMillis() - startTime;
            
            MigrationRecord record = new MigrationRecord(
                migration.getVersion(),
                migration.getDescription(),
                checksum,
                executionTime,
                context.getInstalledBy()
            );
            record.setType(MigrationVersionComparator.isRepeatable(migration.getVersion()) ? "REPEATABLE" : "VERSIONED");
            
            return record;
            
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            
            MigrationRecord record = new MigrationRecord();
            record.setVersion(migration.getVersion());
            record.setDescription(migration.getDescription());
            record.setExecutionTime(executionTime);
            record.setInstalledBy(context.getInstalledBy());
            record.setSuccess(false);
            record.setErrorMessage(e.getMessage());
            
            throw NopException.adapt(e);
        }
    }
    
    protected void executeChange(DbChangeModel change, MigrationContext context) {
        String changeType = change.getType();
        if (StringHelper.isBlank(changeType)) {
            return;
        }
        
        IChangeExecutor executor = executors.get(changeType);
        if (executor == null) {
            throw new NopException(ERR_DB_MIGRATION_UNKNOWN_CHANGE_TYPE)
                .param(ARG_CHANGE_TYPE, changeType);
        }
        
        IDialect dialect = context.getDialect();
        executor.execute(change, context, dialect);
    }
    
    protected String calculateChecksum(DbMigrationModel migration) {
        StringBuilder sb = new StringBuilder();
        sb.append(migration.getVersion() != null ? migration.getVersion() : "");
        sb.append(migration.getDescription() != null ? migration.getDescription() : "");
        
        if (migration.getChangeset() != null) {
            for (DbChangeModel change : migration.getChangeset()) {
                sb.append(change.getType() != null ? change.getType() : "");
            }
        }
        
        return StringHelper.md5Hash(sb.toString());
    }
}

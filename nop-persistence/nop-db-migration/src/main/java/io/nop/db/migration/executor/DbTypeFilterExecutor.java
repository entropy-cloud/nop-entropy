package io.nop.db.migration.executor;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.resource.component.AbstractComponentModel;
import io.nop.dao.dialect.IDialect;
import io.nop.db.migration.core.MigrationContext;
import io.nop.db.migration.model.DbChangeModel;
import io.nop.db.migration.model.DbTypeFilterChange;

import java.util.List;
import java.util.Set;

import static io.nop.db.migration.DbMigrationErrors.ARG_CHANGE_TYPE;
import static io.nop.db.migration.DbMigrationErrors.ERR_DB_MIGRATION_UNKNOWN_CHANGE_TYPE;

public class DbTypeFilterExecutor implements IChangeExecutor {
    
    public static final String CHANGE_TYPE = "dbTypeFilter";
    
    private final java.util.Map<String, IChangeExecutor> executors;
    
    public DbTypeFilterExecutor() {
        this.executors = new java.util.HashMap<>();
    }
    
    public DbTypeFilterExecutor(java.util.Map<String, IChangeExecutor> executors) {
        this.executors = executors;
    }
    
    public void registerExecutor(String changeType, IChangeExecutor executor) {
        executors.put(changeType, executor);
    }
    
    @Override
    public void execute(AbstractComponentModel change, MigrationContext context, IDialect dialect) {
        DbTypeFilterChange dbTypeFilter = (DbTypeFilterChange) change;
        
        Set<String> dbTypes = dbTypeFilter.getDbTypes();
        if (dbTypes == null || dbTypes.isEmpty()) {
            return;
        }
        
        String currentDbType = dialect.getName().toLowerCase();
        boolean shouldExecute = false;
        
        for (String allowedType : dbTypes) {
            if (allowedType != null && allowedType.toLowerCase().equals(currentDbType)) {
                shouldExecute = true;
                break;
            }
        }
        
        if (!shouldExecute) {
            return;
        }
        
        List<DbChangeModel> changes = dbTypeFilter.getChanges();
        if (changes == null || changes.isEmpty()) {
            return;
        }
        
        for (DbChangeModel subChange : changes) {
            executeSubChange(subChange, context, dialect);
        }
    }
    
    protected void executeSubChange(DbChangeModel change, MigrationContext context, IDialect dialect) {
        String changeType = change.getType();
        if (changeType == null || changeType.isEmpty()) {
            return;
        }
        
        IChangeExecutor executor = executors.get(changeType);
        if (executor == null) {
            throw new NopException(ERR_DB_MIGRATION_UNKNOWN_CHANGE_TYPE)
                .param(ARG_CHANGE_TYPE, changeType);
        }
        
        executor.execute(change, context, dialect);
    }
    
    @Override
    public String generateRollbackSql(AbstractComponentModel change, IDialect dialect) {
        return null;
    }
    
    @Override
    public boolean supports(String changeType) {
        return CHANGE_TYPE.equals(changeType);
    }
}

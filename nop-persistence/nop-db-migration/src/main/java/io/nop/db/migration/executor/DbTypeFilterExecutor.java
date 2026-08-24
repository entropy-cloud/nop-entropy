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

    /**
     * Normalizes a database type name to the nop-dao dialect name. The xdef
     * documentation used to advertise "sqlserver", while the dialect registry
     * names it "mssql", so values written as "sqlserver" never matched and the
     * corresponding changes were silently skipped.
     */
    static String normalizeDbType(String dbType) {
        if (dbType == null) {
            return null;
        }
        String name = dbType.trim().toLowerCase();
        if ("sqlserver".equals(name)) {
            return "mssql";
        }
        return name;
    }

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

    /**
     * Allows IoC containers to populate the nested executor table, mirroring
     * {@code MigrationExecutor#setExecutors}.
     */
    public void setExecutors(java.util.Map<String, IChangeExecutor> executors) {
        if (executors == null || executors.isEmpty())
            return;
        this.executors.putAll(executors);
    }
    
    @Override
    public void execute(AbstractComponentModel change, MigrationContext context, IDialect dialect) {
        DbTypeFilterChange dbTypeFilter = (DbTypeFilterChange) change;
        
        Set<String> dbTypes = dbTypeFilter.getDbTypes();
        if (dbTypes == null || dbTypes.isEmpty()) {
            return;
        }
        
        String currentDbType = normalizeDbType(dialect.getName());
        boolean shouldExecute = false;

        for (String allowedType : dbTypes) {
            if (allowedType != null && normalizeDbType(allowedType).equals(currentDbType)) {
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

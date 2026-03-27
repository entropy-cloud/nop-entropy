package io.nop.db.migration.executor;

import io.nop.core.resource.component.AbstractComponentModel;
import io.nop.dao.dialect.IDialect;
import io.nop.db.migration.core.MigrationContext;

public interface IChangeExecutor {
    
    void execute(AbstractComponentModel change, MigrationContext context, IDialect dialect);
    
    default String generateRollbackSql(AbstractComponentModel change, IDialect dialect) {
        return null;
    }
    
    default boolean supports(String changeType) {
        return false;
    }
}

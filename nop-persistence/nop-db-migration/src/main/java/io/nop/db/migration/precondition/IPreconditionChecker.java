package io.nop.db.migration.precondition;

import io.nop.core.resource.component.AbstractComponentModel;
import io.nop.db.migration.core.MigrationContext;

public interface IPreconditionChecker {
    
    String getPreconditionType();
    
    boolean check(AbstractComponentModel precondition, MigrationContext context);
}

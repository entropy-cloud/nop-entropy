package io.nop.db.migration.precondition;

import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.resource.component.AbstractComponentModel;
import io.nop.db.migration.PreconditionExpect;
import io.nop.db.migration.core.MigrationContext;
import io.nop.db.migration.model.CustomConditionPrecondition;
import io.nop.xlang.api.XLang;

public class CustomConditionChecker implements IPreconditionChecker {
    
    public static final String PRECONDITION_TYPE = "customCondition";
    
    @Override
    public String getPreconditionType() {
        return PRECONDITION_TYPE;
    }
    
    @Override
    public boolean check(AbstractComponentModel precondition, MigrationContext context) {
        if (!(precondition instanceof CustomConditionPrecondition)) {
            return false;
        }
        
        CustomConditionPrecondition ccp = (CustomConditionPrecondition) precondition;
        
        if (ccp.getExpression() == null) {
            return true;
        }
        
        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue("context", context);
        scope.setLocalValue("jdbcTemplate", context.getJdbcTemplate());
        scope.setLocalValue("dialect", context.getDialect());
        
        Object result = ccp.getExpression().invoke(scope);
        
        PreconditionExpect expect = ccp.getExpect();
        if (expect == null) {
            expect = PreconditionExpect.EXISTS;
        }
        
        boolean conditionMet = Boolean.TRUE.equals(result);
        
        return expect == PreconditionExpect.EXISTS ? conditionMet : !conditionMet;
    }
}

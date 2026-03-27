/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.db.migration.executor;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.resource.component.AbstractComponentModel;
import io.nop.dao.dialect.IDialect;
import io.nop.db.migration.core.MigrationContext;
import io.nop.db.migration.model.CustomChange;
import io.nop.xlang.api.XLang;

public class CustomChangeExecutor implements IChangeExecutor {

    public static final String CHANGE_TYPE = "customChange";

    @Override
    public void execute(AbstractComponentModel change, MigrationContext context, IDialect dialect) {
        CustomChange customChange = (CustomChange) change;
        
        if (customChange.getImplementation() == null) {
            return;
        }
        
        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue("context", context);
        scope.setLocalValue("dialect", dialect);
        scope.setLocalValue("change", customChange);
        
        customChange.getImplementation().invoke(scope);
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

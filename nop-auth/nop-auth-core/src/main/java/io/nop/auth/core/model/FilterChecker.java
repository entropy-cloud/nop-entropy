package io.nop.auth.core.model;

import io.nop.auth.core.AuthCoreConstants;
import io.nop.core.context.IEvalContext;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.eval.IEvalPredicate;
import io.nop.core.lang.xml.IXNodeGenerator;
import io.nop.core.lang.xml.XNode;
import io.nop.xlang.filter.BizFilterEvaluator;

public class FilterChecker implements IEvalPredicate {
    private final IXNodeGenerator filterGenerator;

    public FilterChecker(IXNodeGenerator filterGenerator) {
        this.filterGenerator = filterGenerator;
    }

    @Override
    public boolean passConditions(IEvalContext ctx) {
        XNode filter = filterGenerator.generateNode(ctx);
        if (filter == null)
            return true;
        return new BizFilterEvaluator(IServiceContext.fromEvalContext(ctx)).testForEntity(filter.toTreeBean(), ctx.getEvalScope().getValue(AuthCoreConstants.VAR_ENTITY));
    }
}

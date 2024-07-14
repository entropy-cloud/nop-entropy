package io.nop.batch.core.filter;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.util.Guard;
import io.nop.batch.core.IBatchRecordFilter;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.core.lang.eval.IEvalFunction;

public class EvalBatchRecordFilter<R> implements IBatchRecordFilter<R> {
    private final IEvalFunction func;

    public EvalBatchRecordFilter(IEvalFunction func) {
        this.func = Guard.notNull(func, "func");
    }

    @Override
    public boolean accept(R record, IBatchTaskContext context) {
        Object result = func.call2(null, record, context, context.getEvalScope());
        return ConvertHelper.toTruthy(result);
    }
}

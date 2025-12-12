package io.nop.record.resource;

import io.nop.commons.aggregator.IAggregator;
import io.nop.core.context.IEvalContext;
import io.nop.core.lang.eval.IEvalFunction;

public class EvalFunctionAggregator implements IAggregator {
    private Object value;
    private final IEvalFunction fn;
    private final IEvalContext context;

    public EvalFunctionAggregator(IEvalFunction fn, IEvalContext context) {
        this.fn = fn;
        this.context = context;
    }

    @Override
    public void update(Object value) {
        this.value = fn.call3(null, this.value, value, context, context.getEvalScope());
    }

    @Override
    public Object getValue() {
        return value;
    }

    @Override
    public void reset() {
        this.value = null;
    }
}

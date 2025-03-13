package io.nop.core.lang.eval;

import io.nop.core.context.IEvalContext;

public class FixedValueEvalAction implements IEvalAction {
    private final Object value;

    public FixedValueEvalAction(Object value) {
        this.value = value;
    }

    @Override
    public Object invoke(IEvalContext ctx) {
        return value;
    }
}

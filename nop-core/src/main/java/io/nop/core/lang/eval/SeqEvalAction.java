package io.nop.core.lang.eval;

import io.nop.core.context.IEvalContext;

public class SeqEvalAction implements IEvalAction {
    private final IEvalAction[] actions;

    public SeqEvalAction(IEvalAction... actions) {
        this.actions = actions;
    }

    @Override
    public Object invoke(IEvalContext ctx) {
        Object ret = null;
        for (IEvalAction action : actions) {
            ret = action.invoke(ctx);
        }
        return ret;
    }
}
package io.nop.xlang.api;

import io.nop.core.context.IEvalContext;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.xlang.api.source.IWithSourceCode;

public class EvalActionWithCode implements IEvalAction, IWithSourceCode {
    private final IEvalAction action;
    private final String code;

    public EvalActionWithCode(IEvalAction action, String code) {
        this.action = action;
        this.code = code;
    }

    public IEvalAction getAction() {
        return action;
    }

    public String getSource() {
        return code;
    }

    public String toString() {
        return code == null ? "" : code;
    }

    @Override
    public Object invoke(IEvalContext ctx) {
        return action.invoke(ctx);
    }
}

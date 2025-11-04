package io.nop.xlang.script;

import io.nop.api.core.util.ISourceLocationGetter;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.context.IEvalContext;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.reflect.IFunctionArgument;
import io.nop.xlang.api.source.IWithSourceCode;

import java.util.List;

public class ScriptEvalAction implements IEvalAction, IWithSourceCode, ISourceLocationGetter {
    private final SourceLocation loc;
    private final String code;
    private final IEvalFunction function;
    private final List<? extends IFunctionArgument> argModels;

    public ScriptEvalAction(SourceLocation loc,
                            String code, List<? extends IFunctionArgument> argModels, IEvalFunction function) {
        this.argModels = argModels;
        this.loc = loc;
        this.code = code;
        this.function = function;
    }

    @Override
    public String getSource() {
        return code;
    }

    @Override
    public SourceLocation getLocation() {
        return loc;
    }

    @Override
    public Object invoke(IEvalContext ctx) {
        int n = argModels.size();
        if (n == 0)
            return function.call0(null, ctx.getEvalScope());

        IEvalScope scope = ctx.getEvalScope();
        if (n == 1) {
            return function.call1(null, scope.getValue(argModels.get(0).getName()), scope);
        } else if (n == 2) {
            Object arg1 = scope.getValue(argModels.get(0).getName());
            Object arg2 = scope.getValue(argModels.get(1).getName());
            return function.call2(null, arg1, arg2, scope);
        } else {
            Object[] args = new Object[n];
            for (int i = 0; i < n; i++) {
                args[i] = scope.getValue(argModels.get(i).getName());
            }
            return function.invoke(null, args, scope);
        }
    }
}

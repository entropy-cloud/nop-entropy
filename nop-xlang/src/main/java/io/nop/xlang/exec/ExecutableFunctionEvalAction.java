package io.nop.xlang.exec;

import io.nop.core.context.IEvalContext;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.reflect.IFunctionModel;
import io.nop.xlang.api.AbstractEvalAction;
import io.nop.xlang.api.XLang;

public class ExecutableFunctionEvalAction extends AbstractEvalAction {
    private final IFunctionModel functionModel;
    private final ExecutableFunction func;

    public ExecutableFunctionEvalAction(IFunctionModel functionModel) {
        this.functionModel = functionModel;
        this.func = (ExecutableFunction) functionModel.getInvoker();
    }


    @Override
    public Object invoke(IEvalContext ctx) {
        return doInvoke(new EvalRuntime(ctx.getEvalScope()));
    }

    @Override
    protected Object doInvoke(EvalRuntime rt) {
        Object[] args = functionModel.buildArgValuesFromScope(rt.getScope());
        return func.executeWithArgs(XLang.getExecutor(), args, rt);
    }
}

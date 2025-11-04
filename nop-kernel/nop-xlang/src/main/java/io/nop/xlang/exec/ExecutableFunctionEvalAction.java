package io.nop.xlang.exec;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.context.IEvalContext;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExecutableExpressionVisitor;
import io.nop.core.lang.eval.IExpressionExecutor;
import io.nop.core.reflect.IFunctionModel;
import io.nop.xlang.api.AbstractEvalAction;
import io.nop.xlang.api.XLang;

public class ExecutableFunctionEvalAction extends AbstractEvalAction implements IExecutableExpression {
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

    @Override
    public SourceLocation getLocation() {
        return func.getLocation();
    }

    @Override
    public void display(StringBuilder sb) {
        func.display(sb);
    }

    @Override
    public boolean allowBreakPoint() {
        return func.allowBreakPoint();
    }

    @Override
    public boolean containsReturnStatement() {
        return func.containsReturnStatement();
    }

    @Override
    public boolean containsBreakStatement() {
        return func.containsBreakStatement();
    }

    @Override
    public boolean isUseExitMode() {
        return func.isUseExitMode();
    }

    @Override
    public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
        return func.execute(executor, rt);
    }

    @Override
    public void visit(IExecutableExpressionVisitor visitor) {
        if (visitor.onVisitExpr(this)) {
            func.visit(visitor);
            visitor.onEndVisitExpr(this);
        }
    }
}

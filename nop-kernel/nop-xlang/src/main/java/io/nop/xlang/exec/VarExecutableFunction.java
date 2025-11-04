package io.nop.xlang.exec;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExecutableExpressionVisitor;
import io.nop.core.lang.eval.IExpressionExecutor;

import static io.nop.xlang.XLangErrors.ARG_FUNC_EXPR;
import static io.nop.xlang.XLangErrors.ERR_EXEC_CALL_NULL_FUNCTION;

public class VarExecutableFunction extends AbstractExecutable {
    protected final IExecutableExpression funcExpr;
    private final boolean optional;
    protected final IExecutableExpression[] args;

    public VarExecutableFunction(SourceLocation loc, IExecutableExpression funcExpr,
                                 boolean optional, IExecutableExpression[] args) {
        super(loc);
        this.funcExpr = funcExpr;
        this.optional = optional;
        this.args = args;
    }

    protected void checkAllowNullFunc() {
        if (!optional)
            throw newError(ERR_EXEC_CALL_NULL_FUNCTION).param(ARG_FUNC_EXPR, funcExpr.display());
    }


    @Override
    public void display(StringBuilder sb) {
        funcExpr.display(sb);
        if (optional) {
            sb.append("?.");
        }
        sb.append('(');
        for (int i = 0, n = args.length; i < n; i++) {
            args[i].display(sb);
            if (i != n - 1)
                sb.append(',');
        }
        sb.append(')');
    }


    @Override
    public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
        ExecutableFunction func = (ExecutableFunction) executor.execute(funcExpr, rt);
        if (func == null) {
            checkAllowNullFunc();
            return null;
        }


        try {
            return func.executeWithArgExprs(executor, args, rt);
        } catch (NopException e) {
            e.addXplStack(this);
            throw e;
        }
    }

    @Override
    public void visit(IExecutableExpressionVisitor visitor) {
        if (visitor.onVisitExpr(this)) {
            funcExpr.visit(visitor);
            for (IExecutableExpression arg : args) {
                arg.visit(visitor);
            }
            visitor.onEndVisitExpr(this);
        }
    }
}

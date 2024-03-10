/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.exec;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExpressionExecutor;
import io.nop.xlang.utils.EvalFunctionHelper;

import static io.nop.xlang.XLangErrors.ARG_CLASS_NAME;
import static io.nop.xlang.XLangErrors.ARG_FUNC_EXPR;
import static io.nop.xlang.XLangErrors.ERR_EXEC_CALL_NULL_FUNCTION;
import static io.nop.xlang.XLangErrors.ERR_EXEC_EXPR_NOT_RETURN_FUNC;

public class VarFunctionExecutable extends AbstractExecutable {
    protected final IExecutableExpression funcExpr;
    private final boolean optional;
    protected final IExecutableExpression[] args;

    private VarFunctionExecutable(SourceLocation loc, IExecutableExpression funcExpr,
                                  boolean optional,
                                  IExecutableExpression[] args) {
        super(loc);
        this.funcExpr = Guard.notNull(funcExpr, "funcExpr is null");
        this.optional = optional;
        this.args = args;
    }

    protected void checkAllowNullFunc() {
        if (!optional)
            throw newError(ERR_EXEC_CALL_NULL_FUNCTION).param(ARG_FUNC_EXPR, funcExpr.display());
    }

    public static VarFunctionExecutable build(SourceLocation loc, IExecutableExpression funcExpr,
                                              boolean optional,
                                              IExecutableExpression[] args) {
        switch (args.length) {
            case 0:
                return new NoArgExecutable(loc, funcExpr, optional, args);
            case 1:
                return new OneArgExecutable(loc, funcExpr, optional, args);
            case 2:
                return new TwoArgExecutable(loc, funcExpr, optional, args);
            case 3:
                return new ThreeArgExecutable(loc, funcExpr, optional, args);
            default:
                return new VarFunctionExecutable(loc, funcExpr, optional, args);
        }
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

    protected IEvalFunction getFunction(IExpressionExecutor executor, IEvalScope scope) {
        Object o = executor.execute(funcExpr, scope);
        if (o == null)
            return null;
        IEvalFunction fn = EvalFunctionHelper.toEvalFunction(o);
        if (fn == null)
            throw newError(ERR_EXEC_EXPR_NOT_RETURN_FUNC).param(ARG_FUNC_EXPR, funcExpr).param(ARG_CLASS_NAME,
                    o.getClass().getName());
        return fn;
    }

    @Override
    public Object execute(IExpressionExecutor executor, IEvalScope scope) {
        IEvalFunction func = getFunction(executor, scope);
        if (func == null) {
            checkAllowNullFunc();
            return null;
        }

        Object[] argValues = new Object[args.length];
        for (int i = 0, n = args.length; i < n; i++) {
            argValues[i] = executor.execute(args[i], scope);
        }

        try {
            return func.invoke(null, argValues, scope);
        } catch (NopException e) {
            e.addXplStack(this);
            throw e;
        }
    }

    protected Object doInvoke0(IEvalFunction func, IEvalScope scope) {
        return func.call0(null, scope);
    }

    protected Object doInvoke1(IEvalFunction func, Object arg1, IEvalScope scope) {
        return func.call1(null, arg1, scope);
    }

    protected Object doInvoke2(IEvalFunction func, Object arg1, Object arg2, IEvalScope scope) {
        return func.call2(null, arg1, arg2, scope);
    }

    protected Object doInvoke3(IEvalFunction func, Object arg1, Object arg2, Object arg3, IEvalScope scope) {
        return func.call3(null, arg1, arg2, arg3, scope);
    }

    static class NoArgExecutable extends VarFunctionExecutable {

        public NoArgExecutable(SourceLocation loc, IExecutableExpression funcExpr,
                               boolean optional, IExecutableExpression[] args) {
            super(loc, funcExpr, optional, args);
        }

        @Override
        public Object execute(IExpressionExecutor executor, IEvalScope scope) {
            IEvalFunction func = getFunction(executor, scope);
            if (func == null) {
                checkAllowNullFunc();
                return null;
            }
            return doInvoke0(func, scope);
        }
    }

    static class OneArgExecutable extends VarFunctionExecutable {
        private final IExecutableExpression argExpr;

        public OneArgExecutable(SourceLocation loc, IExecutableExpression funcExpr,
                                boolean optional, IExecutableExpression[] args) {
            super(loc, funcExpr, optional, args);
            this.argExpr = args[0];
        }

        @Override
        public Object execute(IExpressionExecutor executor, IEvalScope scope) {
            IEvalFunction func = getFunction(executor, scope);
            if (func == null) {
                checkAllowNullFunc();
                return null;
            }
            Object arg = executor.execute(argExpr, scope);
            return doInvoke1(func, arg, scope);
        }
    }

    static class TwoArgExecutable extends VarFunctionExecutable {
        private final IExecutableExpression argExpr1;
        private final IExecutableExpression argExpr2;

        public TwoArgExecutable(SourceLocation loc, IExecutableExpression funcExpr,
                                boolean optional, IExecutableExpression[] args) {
            super(loc, funcExpr, optional, args);
            this.argExpr1 = args[0];
            this.argExpr2 = args[1];
        }

        @Override
        public Object execute(IExpressionExecutor executor, IEvalScope scope) {

            IEvalFunction func = getFunction(executor, scope);
            if (func == null) {
                checkAllowNullFunc();
                return null;
            }
            Object arg1 = executor.execute(argExpr1, scope);
            Object arg2 = executor.execute(argExpr2, scope);
            return doInvoke2(func, arg1, arg2, scope);
        }
    }

    static class ThreeArgExecutable extends VarFunctionExecutable {
        private final IExecutableExpression argExpr1;
        private final IExecutableExpression argExpr2;
        private final IExecutableExpression argExpr3;

        public ThreeArgExecutable(SourceLocation loc, IExecutableExpression funcExpr,
                                  boolean optional, IExecutableExpression[] args) {
            super(loc, funcExpr, optional, args);
            this.argExpr1 = args[0];
            this.argExpr2 = args[1];
            this.argExpr3 = args[2];
        }

        @Override
        public Object execute(IExpressionExecutor executor, IEvalScope scope) {
            IEvalFunction func = getFunction(executor, scope);
            if (func == null) {
                checkAllowNullFunc();
                return null;
            }
            Object arg1 = executor.execute(argExpr1, scope);
            Object arg2 = executor.execute(argExpr2, scope);
            Object arg3 = executor.execute(argExpr3, scope);
            return doInvoke3(func, arg1, arg2, arg3, scope);
        }
    }
}
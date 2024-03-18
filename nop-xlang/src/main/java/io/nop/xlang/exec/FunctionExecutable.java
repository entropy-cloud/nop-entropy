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
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExpressionExecutor;

public class FunctionExecutable extends AbstractExecutable {
    private final String funcName;
    protected final IEvalFunction func;
    protected final IExecutableExpression[] args;

    private FunctionExecutable(SourceLocation loc, String funcName, IEvalFunction func, IExecutableExpression[] args) {
        super(loc);
        this.funcName = Guard.notEmpty(funcName, "funcName is empty");
        this.func = Guard.notNull(func, "func is null");
        this.args = args;
    }

    public static FunctionExecutable build(SourceLocation loc, String funcName, IEvalFunction func,
                                           IExecutableExpression[] args) {
        switch (args.length) {
            case 0:
                return new NoArgExecutable(loc, funcName, func, args);
            case 1:
                return new OneArgExecutable(loc, funcName, func, args);
            case 2:
                return new TwoArgExecutable(loc, funcName, func, args);
            case 3:
                return new ThreeArgExecutable(loc, funcName, func, args);
            default:
                return new FunctionExecutable(loc, funcName, func, args);
        }
    }

    @Override
    public void display(StringBuilder sb) {
        sb.append(funcName);
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
        Object[] argValues = new Object[args.length];
        for (int i = 0, n = args.length; i < n; i++) {
            argValues[i] = executor.execute(args[i], rt);
        }

        try {
            return func.invoke(null, argValues, rt.getScope());
        } catch (NopException e) {
            e.addXplStack(this);
            throw e;
        }
    }

    static class NoArgExecutable extends FunctionExecutable {
        static final Object[] EMPTY_ARGS = new Object[0];

        public NoArgExecutable(SourceLocation loc, String funcName, IEvalFunction func, IExecutableExpression[] args) {
            super(loc, funcName, func, args);
        }

        @Override
        public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
            try {
                return func.invoke(null, EMPTY_ARGS, rt.getScope());
            } catch (NopException e) {
                e.addXplStack(this);
                throw e;
            }
        }
    }

    static class OneArgExecutable extends FunctionExecutable {
        private final IExecutableExpression argExpr;

        public OneArgExecutable(SourceLocation loc, String funcName, IEvalFunction func, IExecutableExpression[] args) {
            super(loc, funcName, func, args);
            this.argExpr = args[0];
        }

        @Override
        public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
            Object arg = executor.execute(argExpr, rt);
            try {
                return func.call1(null, arg, rt.getScope());
            } catch (NopException e) {
                e.addXplStack(this);
                throw e;
            }
        }
    }

    static class TwoArgExecutable extends FunctionExecutable {
        private final IExecutableExpression argExpr1;
        private final IExecutableExpression argExpr2;

        public TwoArgExecutable(SourceLocation loc, String funcName, IEvalFunction func, IExecutableExpression[] args) {
            super(loc, funcName, func, args);
            this.argExpr1 = args[0];
            this.argExpr2 = args[1];
        }

        @Override
        public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
            Object arg1 = executor.execute(argExpr1, rt);
            Object arg2 = executor.execute(argExpr2, rt);

            try {
                return func.call2(null, arg1, arg2, rt.getScope());
            } catch (NopException e) {
                e.addXplStack(this);
                throw e;
            }
        }
    }

    static class ThreeArgExecutable extends FunctionExecutable {
        private final IExecutableExpression argExpr1;
        private final IExecutableExpression argExpr2;
        private final IExecutableExpression argExpr3;

        public ThreeArgExecutable(SourceLocation loc, String funcName, IEvalFunction func,
                                  IExecutableExpression[] args) {
            super(loc, funcName, func, args);
            this.argExpr1 = args[0];
            this.argExpr2 = args[1];
            this.argExpr3 = args[2];
        }

        @Override
        public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
            Object arg1 = executor.execute(argExpr1, rt);
            Object arg2 = executor.execute(argExpr2, rt);
            Object arg3 = executor.execute(argExpr3, rt);

            try {
                return func.call3(null, arg1, arg2, arg3, rt.getScope());
            } catch (NopException e) {
                e.addXplStack(this);
                throw e;
            }
        }
    }
}

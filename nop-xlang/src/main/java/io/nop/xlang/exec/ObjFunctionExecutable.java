/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.exec;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExpressionExecutor;

public class ObjFunctionExecutable extends AbstractObjFunctionExecutable {

    protected ObjFunctionExecutable(SourceLocation loc, IExecutableExpression objExpr, String funcName,
                                    boolean optional,
                                    IExecutableExpression[] args) {
        super(loc, objExpr, funcName, optional, args);
    }

    public static ObjFunctionExecutable build(SourceLocation loc, IExecutableExpression objExpr, String funcName,
                                              boolean optional,
                                              IExecutableExpression[] args) {
        switch (args.length) {
            case 0:
                return new NoArgExecutable(loc, objExpr, funcName, optional, args);
            case 1:
                return new OneArgExecutable(loc, objExpr, funcName, optional, args);
            case 2:
                return new TwoArgExecutable(loc, objExpr, funcName, optional, args);
            case 3:
                return new ThreeArgExecutable(loc, objExpr, funcName, optional, args);
            default:
                return new ObjFunctionExecutable(loc, objExpr, funcName, optional, args);
        }
    }

    private final ObjFunctionHandle funcHandle = new ObjFunctionHandle();

    @Override
    public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
        Object obj = eval(objExpr, executor, rt);
        if (obj == null)
            return null;

        Object[] argValues = new Object[args.length];
        for (int i = 0, n = args.length; i < n; i++) {
            argValues[i] = executor.execute(args[i], rt);
        }

        IEvalFunction func = getFunctionForObj(obj, argValues);
        return doInvoke(func, obj, argValues, rt.getScope());
    }

    protected IEvalFunction getFunctionForObj(Object obj, Object... argValues) {
        return funcHandle.getFunctionForObj(obj, funcName, this::newError, argValues);
    }

    protected IEvalFunction getFunction(Class<?> clazz, Object... argValues) {
        return funcHandle.getFunction(clazz, funcName, argValues);
    }

    protected IEvalFunction getStaticFunction(Class<?> clazz, Object... argValues) {
        return funcHandle.getStaticFunction(clazz, funcName, argValues);
    }

    static class NoArgExecutable extends ObjFunctionExecutable {
        public NoArgExecutable(SourceLocation loc, IExecutableExpression objExpr, String funcName,
                               boolean optional,
                               IExecutableExpression[] args) {
            super(loc, objExpr, funcName, optional, args);
        }

        @Override
        public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
            Object obj = executor.execute(objExpr, rt);
            if (obj == null)
                return null;

            IEvalFunction func = getFunctionForObj(obj);
            return doInvoke0(func, obj, rt.getScope());
        }
    }

    static class OneArgExecutable extends ObjFunctionExecutable {
        private final IExecutableExpression argExpr;

        public OneArgExecutable(SourceLocation loc, IExecutableExpression objExpr, String funcName,
                                boolean optional,
                                IExecutableExpression[] args) {
            super(loc, objExpr, funcName, optional, args);
            this.argExpr = args[0];
        }

        @Override
        public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
            Object obj = executor.execute(objExpr, rt);
            if (obj == null)
                return null;

            Object arg = executor.execute(argExpr, rt);
            IEvalFunction func = getFunctionForObj(obj, arg);
            return doInvoke1(func, obj, arg, rt.getScope());
        }
    }

    static class TwoArgExecutable extends ObjFunctionExecutable {
        private final IExecutableExpression argExpr1;
        private final IExecutableExpression argExpr2;

        public TwoArgExecutable(SourceLocation loc, IExecutableExpression objExpr, String funcName,
                                boolean optional,
                                IExecutableExpression[] args) {
            super(loc, objExpr, funcName, optional, args);
            this.argExpr1 = args[0];
            this.argExpr2 = args[1];
        }

        @Override
        public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
            Object obj = executor.execute(objExpr, rt);
            if (obj == null)
                return null;

            Object arg1 = executor.execute(argExpr1, rt);
            Object arg2 = executor.execute(argExpr2, rt);

            IEvalFunction func = getFunctionForObj(obj, arg1, arg2);
            return doInvoke2(func, obj, arg1, arg2, rt.getScope());
        }
    }

    static class ThreeArgExecutable extends ObjFunctionExecutable {
        private final IExecutableExpression argExpr1;
        private final IExecutableExpression argExpr2;
        private final IExecutableExpression argExpr3;

        public ThreeArgExecutable(SourceLocation loc, IExecutableExpression objExpr, String funcName,
                                  boolean optional,
                                  IExecutableExpression[] args) {
            super(loc, objExpr, funcName, optional, args);
            this.argExpr1 = args[0];
            this.argExpr2 = args[1];
            this.argExpr3 = args[2];
        }

        @Override
        public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
            Object obj = executor.execute(objExpr, rt);
            if (obj == null)
                return null;

            Object arg1 = executor.execute(argExpr1, rt);
            Object arg2 = executor.execute(argExpr2, rt);
            Object arg3 = executor.execute(argExpr3, rt);

            IEvalFunction func = getFunctionForObj(obj, arg1, arg2, arg3);
            return doInvoke3(func, obj, arg1, arg2, arg3, rt.getScope());
        }
    }
}

/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.exec;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.objects.Pair;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExpressionExecutor;
import io.nop.core.reflect.IMethodModelCollection;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.reflect.hook.IMethodMissingHook;
import io.nop.core.reflect.hook.MethodMissingHookFunction;

import static io.nop.xlang.XLangErrors.ARG_ARG_COUNT;
import static io.nop.xlang.XLangErrors.ARG_CLASS_NAME;
import static io.nop.xlang.XLangErrors.ARG_FUNC_NAME;
import static io.nop.xlang.XLangErrors.ARG_METHOD_NAME;
import static io.nop.xlang.XLangErrors.ERR_EXEC_CLASS_NO_STATIC_METHOD;
import static io.nop.xlang.XLangErrors.ERR_EXEC_NO_OBJ_METHOD;

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

    private transient Pair<Class<?>, IEvalFunction> _cacheFunc = Pair.of(null, null);

    @Override
    public Object execute(IExpressionExecutor executor, IEvalScope scope) {
        Object obj = eval(objExpr, executor, scope);
        if (obj == null)
            return null;

        Object[] argValues = new Object[args.length];
        for (int i = 0, n = args.length; i < n; i++) {
            argValues[i] = executor.execute(args[i], scope);
        }

        IEvalFunction func = getFunctionForObj(obj, argValues);
        return doInvoke(func, obj, argValues, scope);
    }

    protected IEvalFunction getFunctionForObj(Object obj, Object... argValues) {
        Class<?> clazz = obj.getClass();
        if (clazz == Class.class) {
            IEvalFunction fn = getStaticFunction((Class<?>) obj, argValues);
            if (fn == null) {
                throw newError(ERR_EXEC_CLASS_NO_STATIC_METHOD).param(ARG_CLASS_NAME, ((Class<?>) obj).getName())
                        .param(ARG_ARG_COUNT, argValues.length).param(ARG_METHOD_NAME, funcName);
            }
            return fn;
        }
        IEvalFunction fn = getFunction(clazz, argValues);
        if (fn == null) {
            throw newError(ERR_EXEC_NO_OBJ_METHOD).param(ARG_CLASS_NAME, clazz.getName())
                    .param(ARG_ARG_COUNT, argValues.length).param(ARG_FUNC_NAME, funcName);
        }
        return fn;
    }

    protected IEvalFunction getFunction(Class<?> clazz, Object... argValues) {
        IEvalFunction func;
        Pair<Class<?>, IEvalFunction> pair = _cacheFunc;
        if (pair.getLeft() == clazz) {
            func = pair.getRight();
        } else {
            IMethodModelCollection coll = ReflectionManager.instance().getClassModel(clazz).getMethodsByName(funcName);
            if (coll == null) {
                if (IMethodMissingHook.class.isAssignableFrom(clazz)) {
                    func = new MethodMissingHookFunction(funcName);
                } else {
                    return null;
                }
            } else {
                func = coll.getUniqueMethod(args.length);
            }
            if (func == null) {
                func = coll.getMethodForArgValues(argValues);
            } else {
                _cacheFunc = Pair.of(clazz, func);
            }
        }
        return func;
    }

    protected IEvalFunction getStaticFunction(Class<?> clazz, Object... argValues) {
        IEvalFunction func;
        Pair<Class<?>, IEvalFunction> pair = _cacheFunc;
        if (pair.getLeft() == clazz) {
            func = pair.getRight();
        } else {
            IMethodModelCollection coll = ReflectionManager.instance().getClassModel(clazz)
                    .getStaticMethodsByName(funcName);
            if (coll == null) {
                throw newError(ERR_EXEC_NO_OBJ_METHOD).param(ARG_CLASS_NAME, clazz.getName()).param(ARG_FUNC_NAME,
                        funcName);
            }
            func = coll.getUniqueMethod(args.length);
            if (func == null) {
                func = coll.getMethodForArgValues(argValues);
            } else {
                _cacheFunc = Pair.of(clazz, func);
            }
        }
        return func;
    }

    static class NoArgExecutable extends ObjFunctionExecutable {
        public NoArgExecutable(SourceLocation loc, IExecutableExpression objExpr, String funcName,
                               boolean optional,
                               IExecutableExpression[] args) {
            super(loc, objExpr, funcName, optional, args);
        }

        @Override
        public Object execute(IExpressionExecutor executor, IEvalScope scope) {
            Object obj = executor.execute(objExpr, scope);
            if (obj == null)
                return null;

            IEvalFunction func = getFunctionForObj(obj);
            return doInvoke0(func, obj, scope);
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
        public Object execute(IExpressionExecutor executor, IEvalScope scope) {
            Object obj = executor.execute(objExpr, scope);
            if (obj == null)
                return null;

            Object arg = executor.execute(argExpr, scope);

            IEvalFunction func = getFunctionForObj(obj, arg);
            return doInvoke1(func, obj, arg, scope);
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
        public Object execute(IExpressionExecutor executor, IEvalScope scope) {
            Object obj = executor.execute(objExpr, scope);
            if (obj == null)
                return null;

            Object arg1 = executor.execute(argExpr1, scope);
            Object arg2 = executor.execute(argExpr2, scope);

            IEvalFunction func = getFunctionForObj(obj, arg1, arg2);
            return doInvoke2(func, obj, arg1, arg2, scope);
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
        public Object execute(IExpressionExecutor executor, IEvalScope scope) {
            Object obj = executor.execute(objExpr, scope);
            if (obj == null)
                return null;

            Object arg1 = executor.execute(argExpr1, scope);
            Object arg2 = executor.execute(argExpr2, scope);
            Object arg3 = executor.execute(argExpr3, scope);

            IEvalFunction func = getFunctionForObj(obj, arg1, arg2, arg3);
            return doInvoke3(func, obj, arg1, arg2, arg3, scope);
        }
    }
}

/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.exec;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopEvalException;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.MathHelper;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.global.EvalGlobalRegistry;
import io.nop.core.reflect.IFunctionModel;
import io.nop.core.reflect.IMethodModelCollection;
import io.nop.core.reflect.ReflectionManager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static io.nop.xlang.XLangErrors.ARG_ARG_COUNT;
import static io.nop.xlang.XLangErrors.ARG_CLASS_NAME;
import static io.nop.xlang.XLangErrors.ARG_EXPR;
import static io.nop.xlang.XLangErrors.ARG_FUNC_NAME;
import static io.nop.xlang.XLangErrors.ARG_METHOD_NAME;
import static io.nop.xlang.XLangErrors.ERR_EXEC_CLASS_NO_STATIC_METHOD;
import static io.nop.xlang.XLangErrors.ERR_EXEC_INVOKE_FUNCTION_FAIL;
import static io.nop.xlang.XLangErrors.ERR_EXEC_INVOKE_METHOD_FAIL;
import static io.nop.xlang.XLangErrors.ERR_EXEC_OBJ_UNKNOWN_METHOD;
import static io.nop.xlang.XLangErrors.ERR_EXEC_VALUE_NOT_ALLOW_NULL;

/**
 * 表达式子集语义敏感操作的共享 helper 基座（设计 xlang-java 01 §三语义一致性策略）。
 *
 * <p>解释器节点与 java 后端生成代码统一调用本类（同一实现来源，行为一致优先于生成代码的纯度）：
 * 算术/比较/逻辑语义委托既有共享实现（nop-commons {@link MathHelper}、nop-api-core
 * {@link ConvertHelper}，不重复造）；宿主方法反射分派族的解析、调用与异常包装在此收口为单一实现。
 * 禁止为生成代码重写语义等价实现——两套实现的漂移是对拍失败的恒定来源。
 */
public final class XLangSemantics {

    /**
     * 按 funcName 分立的反射分派 handle（缓存键=类名，必须与 funcName 绑定分立——
     * 解释器为每节点一 handle，生成代码共享本表时按 funcName 分立保证缓存不跨函数名污染）。
     */
    private static final Map<String, ObjFunctionHandle> OBJ_FUNCTION_HANDLES = new ConcurrentHashMap<>();

    private XLangSemantics() {
    }

    // ------------------------------------------------------------------
    // 算术（Plus 完整语义 = String 拼接分支 + 数值路径，自 PlusExecutable 内联分支提取）
    // ------------------------------------------------------------------

    public static Object plus(Object v1, Object v2) {
        if (v1 instanceof String || v2 instanceof String) {
            return String.valueOf(v1) + String.valueOf(v2);
        }
        return MathHelper.add(v1, v2);
    }

    public static Number minus(Object v1, Object v2) {
        return MathHelper.minus(v1, v2);
    }

    public static Number multiply(Object v1, Object v2) {
        return MathHelper.multiply(v1, v2);
    }

    public static Number divide(Object v1, Object v2) {
        return MathHelper.divide(v1, v2);
    }

    // ------------------------------------------------------------------
    // 宽松比较 / 严格比较（解释器现状：Strict 变体与宽松变体同实现，均走 xlangEq）
    // ------------------------------------------------------------------

    public static boolean eq(Object v1, Object v2) {
        return MathHelper.xlangEq(v1, v2);
    }

    public static boolean ne(Object v1, Object v2) {
        return !MathHelper.xlangEq(v1, v2);
    }

    public static boolean lt(Object v1, Object v2) {
        return MathHelper.lt(v1, v2);
    }

    public static boolean le(Object v1, Object v2) {
        return MathHelper.le(v1, v2);
    }

    public static boolean gt(Object v1, Object v2) {
        return MathHelper.gt(v1, v2);
    }

    public static boolean ge(Object v1, Object v2) {
        return MathHelper.ge(v1, v2);
    }

    // ------------------------------------------------------------------
    // 真值转换（逻辑运算与守卫共用）
    // ------------------------------------------------------------------

    public static boolean truthy(Object v) {
        return ConvertHelper.toTruthy(v);
    }

    // ------------------------------------------------------------------
    // 宿主方法反射分派族：ObjFunctionExecutable 系（实例方法 / Class 对象上的静态方法）
    // ------------------------------------------------------------------

    /**
     * 完整实例方法分派（生成代码入口）：接收者 null 检查 + 反射解析 + 调用 + 异常包装。
     * 与解释器 ObjFunctionExecutable 族同一语义来源。
     */
    public static Object invokeObjMethod(SourceLocation loc, String display, Object obj, String funcName,
                                         Object[] argValues, IEvalScope scope) {
        if (obj == null)
            return null;
        ObjFunctionHandle handle = OBJ_FUNCTION_HANDLES.computeIfAbsent(funcName, k -> new ObjFunctionHandle());
        IEvalFunction func = handle.getFunctionForObj(obj, funcName,
                code -> newError(code, loc, display), argValues);
        return invokeObjFunction(func, obj, argValues, scope, loc, display, funcName);
    }

    public static Object invokeObjFunction(IEvalFunction func, Object obj, Object[] argValues, IEvalScope scope,
                                           SourceLocation loc, String display, String funcName) {
        try {
            return func.invoke(obj, argValues, scope);
        } catch (Exception e) {
            throw wrapInvokeException(loc, display, funcName, e, obj);
        }
    }

    public static Object invokeObjFunction0(IEvalFunction func, Object obj, IEvalScope scope,
                                            SourceLocation loc, String display, String funcName) {
        try {
            return func.call0(obj, scope);
        } catch (Exception e) {
            throw wrapInvokeException(loc, display, funcName, e, obj);
        }
    }

    public static Object invokeObjFunction1(IEvalFunction func, Object obj, Object arg, IEvalScope scope,
                                            SourceLocation loc, String display, String funcName) {
        try {
            return func.call1(obj, arg, scope);
        } catch (Exception e) {
            throw wrapInvokeException(loc, display, funcName, e, obj);
        }
    }

    public static Object invokeObjFunction2(IEvalFunction func, Object obj, Object arg1, Object arg2,
                                            IEvalScope scope, SourceLocation loc, String display, String funcName) {
        try {
            return func.call2(obj, arg1, arg2, scope);
        } catch (Exception e) {
            throw wrapInvokeException(loc, display, funcName, e, obj);
        }
    }

    public static Object invokeObjFunction3(IEvalFunction func, Object obj, Object arg1, Object arg2, Object arg3,
                                            IEvalScope scope, SourceLocation loc, String display, String funcName) {
        try {
            return func.call3(obj, arg1, arg2, arg3, scope);
        } catch (Exception e) {
            throw wrapInvokeException(loc, display, funcName, e, obj);
        }
    }

    /**
     * 方法调用异常包装（自 AbstractObjFunctionExecutable 内联实现提取）：错误码、loc、
     * forWrap、params、bizFatal 传播语义保持不变。
     */
    public static NopException wrapInvokeException(SourceLocation loc, String display, String funcName,
                                                   Exception e, Object obj) {
        NopException err = newError(ERR_EXEC_INVOKE_METHOD_FAIL, e, loc, display).forWrap()
                .param(ARG_CLASS_NAME, getClassName(obj))
                .param(ARG_FUNC_NAME, funcName);
        if (e instanceof NopException && ((NopException) e).isBizFatal())
            err.bizFatal(true);
        return err;
    }

    static String getClassName(Object o) {
        if (o == null)
            return "null";
        if (o instanceof Class)
            return o.toString();
        return o.getClass().getName();
    }

    // ------------------------------------------------------------------
    // 宿主方法反射分派族：FunctionExecutable 系（注册全局函数静态分派）
    // ------------------------------------------------------------------

    /**
     * 注册全局函数分派（生成代码入口）：按函数名经 {@link EvalGlobalRegistry} 运行时解析
     * （与编译期 LexicalScopeAnalysis 的全局函数解析同一注册表），再统一调用。
     */
    public static Object invokeGlobalFunction(SourceLocation loc, String display, String funcName,
                                              Object[] argValues, IEvalScope scope) {
        IFunctionModel fn = EvalGlobalRegistry.instance().getRegisteredFunction(funcName);
        if (fn == null)
            throw newError(ERR_EXEC_INVOKE_FUNCTION_FAIL, loc, display).param(ARG_FUNC_NAME, funcName);
        return invokeEvalFunction(fn, argValues, scope, display + "@" + loc);
    }

    public static Object invokeEvalFunction(IEvalFunction func, Object[] argValues, IEvalScope scope,
                                            Object stackObj) {
        try {
            return func.invoke(null, argValues, scope);
        } catch (NopException e) {
            e.addXplStack(stackObj);
            throw e;
        }
    }

    public static Object invokeEvalFunction0(IEvalFunction func, IEvalScope scope, Object stackObj) {
        try {
            return func.invoke(null, new Object[0], scope);
        } catch (NopException e) {
            e.addXplStack(stackObj);
            throw e;
        }
    }

    public static Object invokeEvalFunction1(IEvalFunction func, Object arg, IEvalScope scope, Object stackObj) {
        try {
            return func.call1(null, arg, scope);
        } catch (NopException e) {
            e.addXplStack(stackObj);
            throw e;
        }
    }

    public static Object invokeEvalFunction2(IEvalFunction func, Object arg1, Object arg2, IEvalScope scope,
                                             Object stackObj) {
        try {
            return func.call2(null, arg1, arg2, scope);
        } catch (NopException e) {
            e.addXplStack(stackObj);
            throw e;
        }
    }

    public static Object invokeEvalFunction3(IEvalFunction func, Object arg1, Object arg2, Object arg3,
                                             IEvalScope scope, Object stackObj) {
        try {
            return func.call3(null, arg1, arg2, arg3, scope);
        } catch (NopException e) {
            e.addXplStack(stackObj);
            throw e;
        }
    }

    // ------------------------------------------------------------------
    // 宿主方法反射分派族：StaticFunctionExecutable 系（类静态方法分派）
    // ------------------------------------------------------------------

    /**
     * 静态方法分派（编译期已解析 {@code methodCollection} 的调用路径，解释器与生成代码共用）。
     */
    public static Object invokeStaticMethod(SourceLocation loc, String display, String className, String funcName,
                                            boolean optional, IMethodModelCollection methodCollection,
                                            Object[] argValues, IEvalScope scope) {
        IFunctionModel fn = methodCollection.getMethodForArgValues(argValues);
        if (fn == null) {
            if (optional)
                return null;
            throw newError(ERR_EXEC_OBJ_UNKNOWN_METHOD, loc, display)
                    .param(ARG_CLASS_NAME, className)
                    .param(ARG_METHOD_NAME, funcName)
                    .param(ARG_ARG_COUNT, argValues.length);
        }
        return fn.invoke(null, argValues, scope);
    }

    /**
     * 静态方法分派（生成代码入口）：按 className/funcName 运行时解析方法集合——与编译期
     * BuildExecutableProcessor 的 {@code classModel.getStaticMethodsByName(funcName)} 同一解析方式，
     * 再走统一的 {@link #invokeStaticMethod}。
     */
    public static Object invokeStaticMethodResolved(SourceLocation loc, String display, String className,
                                                    String funcName, boolean optional, Object[] argValues,
                                                    IEvalScope scope) {
        IMethodModelCollection coll = resolveStaticMethodCollection(className, funcName);
        if (coll == null) {
            if (optional)
                return null;
            throw newError(ERR_EXEC_CLASS_NO_STATIC_METHOD, loc, display)
                    .param(ARG_CLASS_NAME, className)
                    .param(ARG_METHOD_NAME, funcName);
        }
        return invokeStaticMethod(loc, display, className, funcName, optional, coll, argValues, scope);
    }

    static IMethodModelCollection resolveStaticMethodCollection(String className, String funcName) {
        Class<?> clazz;
        try {
            clazz = Class.forName(className);
        } catch (ClassNotFoundException e) {
            return null;
        }
        return ReflectionManager.instance().getClassModel(clazz).getStaticMethodsByName(funcName);
    }

    // ------------------------------------------------------------------
    // null 守卫（GuardNotNullExecutable）
    // ------------------------------------------------------------------

    public static Object guardNotNull(SourceLocation loc, String display, Object v) {
        if (v == null)
            throw newError(ERR_EXEC_VALUE_NOT_ALLOW_NULL, loc, display);
        return v;
    }

    private static NopException newError(ErrorCode errorCode, SourceLocation loc, String display) {
        return new NopEvalException(errorCode).loc(loc).param(ARG_EXPR, display);
    }

    private static NopException newError(ErrorCode errorCode, Throwable e, SourceLocation loc, String display) {
        return new NopEvalException(errorCode, e).loc(loc).param(ARG_EXPR, display);
    }
}

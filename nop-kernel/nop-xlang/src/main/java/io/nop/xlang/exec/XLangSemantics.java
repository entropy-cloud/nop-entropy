/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.exec;

import io.nop.api.core.ApiErrors;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.convert.ITypeConverter;
import io.nop.api.core.convert.SysConverterRegistry;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopEvalException;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ISourceLocationSetter;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.collections.iterator.IntRangeIterator;
import io.nop.commons.collections.iterator.LoopVarStatus;
import io.nop.commons.lang.Undefined;
import io.nop.commons.util.ClassHelper;
import io.nop.api.core.util.CloneHelper;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.MathHelper;
import io.nop.commons.util.StringHelper;
import io.nop.commons.text.RawText;
import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.core.lang.eval.DisabledEvalOutput;
import io.nop.core.lang.eval.EvalReference;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.ExitMode;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.lang.eval.IEvalOutput;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.StringBuilderEvalOutput;
import io.nop.core.lang.eval.global.EvalGlobalRegistry;
import io.nop.core.lang.eval.global.IGlobalVariableDefinition;
import io.nop.core.model.object.DynamicObject;
import io.nop.core.reflect.bean.IBeanModel;
import io.nop.core.reflect.bean.IBeanPropertyModel;
import io.nop.core.reflect.IClassModel;
import io.nop.core.reflect.IFieldModel;
import io.nop.core.reflect.IFunctionModel;
import io.nop.core.reflect.IMethodModelCollection;
import io.nop.core.reflect.IPropertyGetter;
import io.nop.core.reflect.IPropertySetter;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.reflect.accessor.ArrayLengthGetter;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.lang.sql.SQL;
import io.nop.core.lang.xml.IXNodeHandler;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.handler.CollectJObjectHandler;
import io.nop.core.lang.xml.handler.CollectXNodeHandler;
import io.nop.commons.util.StringHelper;
import io.nop.core.CoreConstants;
import io.nop.xlang.XLangConstants;
import io.nop.xlang.api.XLang;
import io.nop.xlang.ast.XLangEscapeMode;
import io.nop.xlang.ast.XLangOperator;
import io.nop.xlang.utils.ExprEvalHelper;

import io.nop.xlang.utils.EvalFunctionHelper;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static io.nop.xlang.XLangErrors.ARG_ARG_COUNT;
import static io.nop.xlang.XLangErrors.ARG_ATTR_EXPR;
import static io.nop.xlang.XLangErrors.ARG_ATTR_VALUE;
import static io.nop.xlang.XLangErrors.ARG_CLASS_NAME;
import static io.nop.xlang.XLangErrors.ARG_EXPR;
import static io.nop.xlang.XLangErrors.ARG_FUNC_NAME;
import static io.nop.xlang.XLangErrors.ARG_METHOD_NAME;
import static io.nop.xlang.XLangErrors.ARG_OBJ_EXPR;
import static io.nop.xlang.XLangErrors.ARG_OP;
import static io.nop.xlang.XLangErrors.ARG_PARAM_NAME;
import static io.nop.xlang.XLangErrors.ARG_PROP_NAME;
import static io.nop.xlang.XLangErrors.ARG_TARGET;
import static io.nop.xlang.XLangErrors.ARG_VALUE;
import static io.nop.xlang.XLangErrors.ARG_VAR_NAME;
import static io.nop.xlang.XLangErrors.ERR_EXEC_ARRAY_BINDING_NOT_LIST;
import static io.nop.xlang.XLangErrors.ERR_EXEC_CALL_FUNC_FAIL;
import static io.nop.xlang.XLangErrors.ERR_EXEC_CLASS_NO_CONSTRUCTOR;
import static io.nop.xlang.XLangErrors.ERR_EXEC_CLASS_NO_STATIC_METHOD;
import static io.nop.xlang.XLangErrors.ERR_EXEC_CLASS_NOT_FOUND;
import static io.nop.xlang.XLangErrors.ERR_EXEC_GET_ATTR_ON_NULL_OBJ;
import static io.nop.xlang.XLangErrors.ERR_EXEC_GET_PROP_ON_NULL_OBJ;
import static io.nop.xlang.XLangErrors.ERR_EXEC_EXPR_NOT_RETURN_FUNC;
import static io.nop.xlang.XLangErrors.ERR_EXEC_FOR_IN_ITEMS_MUST_BE_MAP;
import static io.nop.xlang.XLangErrors.ERR_EXEC_IDENTIFIER_NOT_INITIALIZED;
import static io.nop.xlang.XLangErrors.ERR_EXEC_INVOKE_FUNCTION_FAIL;
import static io.nop.xlang.XLangErrors.ERR_EXEC_INVOKE_METHOD_FAIL;
import static io.nop.xlang.XLangErrors.ERR_EXEC_LOOP_STEP_MUST_NOT_BE_ZERO;
import static io.nop.xlang.XLangErrors.ERR_EXEC_MAKE_PROP_NULL;
import static io.nop.xlang.XLangErrors.ERR_EXEC_MAKE_PROP_OBJ_NULL;
import static io.nop.xlang.XLangErrors.ERR_EXEC_NOT_SUPPORTED_OPERATOR;
import static io.nop.xlang.XLangErrors.ERR_EXEC_OBJ_ATTR_IS_NULL;
import static io.nop.xlang.XLangErrors.ERR_EXEC_OBJ_PROP_IS_NULL;
import static io.nop.xlang.XLangErrors.ERR_EXEC_OBJ_UNKNOWN_METHOD;
import static io.nop.xlang.XLangErrors.ERR_EXEC_OBJECT_BINDING_NOT_MAP;
import static io.nop.xlang.XLangErrors.ERR_EXEC_READ_ATTR_EXPR_RETURN_NULL;
import static io.nop.xlang.XLangErrors.ERR_EXEC_READ_ATTR_FAIL;
import static io.nop.xlang.XLangErrors.ERR_EXEC_READ_PROP_FAIL;
import static io.nop.xlang.XLangErrors.ERR_EXEC_SCOPE_VAR_IS_UNDEFINED;
import static io.nop.xlang.XLangErrors.ERR_EXEC_UNKNOWN_PROP;
import static io.nop.xlang.XLangErrors.ARG_ERROR;
import static io.nop.xlang.XLangErrors.ARG_TAG_NAME;
import static io.nop.xlang.XLangErrors.ARG_TAG_NAME_EXPR;
import static io.nop.xlang.XLangErrors.ARG_VALUE;
import static io.nop.xlang.XLangErrors.ERR_EXEC_CALL_NULL_FUNCTION;
import static io.nop.xlang.XLangErrors.ERR_EXEC_COLLECT_RESULT_NOT_SINGLE_NODE;
import static io.nop.xlang.XLangErrors.ERR_EXEC_THROW_EXCEPTION;
import static io.nop.xlang.XLangErrors.ERR_EXEC_THROW_INVALID_ERROR;
import static io.nop.xlang.XLangErrors.ERR_EXEC_THROW_NULL_EXCEPTION;
import static io.nop.xlang.XLangErrors.ERR_EXEC_TOO_MANY_ARGS;
import static io.nop.xlang.XLangErrors.ERR_EXEC_UNKNOWN_STATIC_FIELD;
import static io.nop.xlang.XLangErrors.ERR_EXEC_XML_EXT_ATTRS_NOT_MAP;
import static io.nop.xlang.XLangErrors.ERR_XPL_DISALLOW_OUTPUT_INVALID_XML_NAME;
import static io.nop.xlang.XLangErrors.ERR_EXEC_VALUE_NOT_ALLOW_EMPTY;
import static io.nop.xlang.XLangErrors.ERR_EXEC_VALUE_NOT_ALLOW_NULL;
import static io.nop.xlang.XLangErrors.ERR_EXEC_WRITE_ATTR_EXPR_RETURN_NULL;
import static io.nop.xlang.XLangErrors.ERR_EXEC_WRITE_ATTR_FAIL;
import static io.nop.xlang.XLangErrors.ERR_EXEC_WRITE_PROP_FAIL;
import static io.nop.xlang.XLangErrors.ERR_EXEC_WRITE_PROP_OBJ_NULL;
import static io.nop.xlang.XLangErrors.ERR_EXEC_DELETE_ON_NULL_OBJ;
import static io.nop.xlang.XLangErrors.ERR_EXEC_DELETE_ON_ARRAY;
import static io.nop.xlang.XLangErrors.ERR_EXEC_DELETE_NOT_SUPPORTED;
import static io.nop.xlang.XLangErrors.ERR_EXEC_DELETE_ATTR_EXPR_RETURN_NULL;
import static io.nop.xlang.XLangErrors.ERR_XLANG_UNRESOLVED_IDENTIFIER;

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
        try {
            return fn.invoke(null, argValues, scope);
        } catch (NopException e) {
            // display 串仅错误路径构造（I2 closure audit 移交的急切构造收敛，行为等价）
            e.addXplStack(display + "@" + loc);
            throw e;
        }
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

    // ------------------------------------------------------------------
    // 覆盖 A（I3）：作用域链访问族（经 $scope 参数的作用域访问 API，设计 java §三）
    // ------------------------------------------------------------------

    /** ScopeIdentifierExecutable 语义：按名读取，null 且未定义时报错。 */
    public static Object getScopeValue(SourceLocation loc, String display, IEvalScope scope, String varName) {
        Object value = scope.getValue(varName);
        if (value == null && !scope.containsValue(varName))
            throw newError(ERR_EXEC_SCOPE_VAR_IS_UNDEFINED, loc, display).param(ARG_VAR_NAME, varName);
        return value;
    }

    /** ScopeAssignExecutable / 各 slot<0 写路径语义：带 loc 写本地变量。 */
    public static Object setScopeValue(SourceLocation loc, IEvalScope scope, String varName, Object value) {
        scope.setLocalValue(loc, varName, value);
        return value;
    }

    /** ScopeSelfInc/Dec 语义：读旧值（不校验未定义），MathHelper.add 提升，写回，返回旧值。 */
    public static Object scopeSelfInc(SourceLocation loc, IEvalScope scope, String varName, int delta) {
        Object value = scope.getValue(varName);
        Object newValue = MathHelper.add(value, delta);
        scope.setLocalValue(loc, varName, newValue);
        return value;
    }

    /** DeleteScopeVarExecutable 生成代码入口：记录旧值 → removeLocalValue → 返回旧值非 null 的 boolean。 */
    public static Object deleteScopeValue(SourceLocation loc, IEvalScope scope, String varName) {
        Object oldValue = scope.getValue(varName);
        scope.removeLocalValue(varName);
        return oldValue != null;
    }

    /** GlobalVarExecutable 生成代码入口：注册表解析 + 生成代码无帧运行时交接（脚本单元等价形态）。 */
    public static Object getGlobalVarValue(SourceLocation loc, String display, IEvalScope scope, String varName) {
        IGlobalVariableDefinition varDef = EvalGlobalRegistry.instance().getRegisteredVariable(varName);
        if (varDef == null)
            throw newError(ERR_XLANG_UNRESOLVED_IDENTIFIER, loc, display).param(ARG_VAR_NAME, varName);
        return varDef.getValue(new EvalRuntime(scope));
    }

    // ------------------------------------------------------------------
    // 覆盖 A（I3）：引用族（帧 slot 持有 EvalReference cell；生成代码以局部变量承载 slot 值）
    // ------------------------------------------------------------------

    /** ReferenceIdentifierExecutable 语义。 */
    public static Object getRefValue(SourceLocation loc, String display, String varName, Object slotValue) {
        return asRef(loc, display, varName, slotValue).getValue();
    }

    /** 引用族公共前置：slot 未初始化报 ERR_EXEC_IDENTIFIER_NOT_INITIALIZED。 */
    public static EvalReference asRef(SourceLocation loc, String display, String varName, Object slotValue) {
        if (slotValue == null)
            throw newError(ERR_EXEC_IDENTIFIER_NOT_INITIALIZED, loc, display).param(ARG_VAR_NAME, varName);
        return (EvalReference) slotValue;
    }

    /** ReferenceAssignExecutable / frame.setRefValue 语义：已有 ref 则写值，否则新建 cell 写入 slot。 */
    public static Object setRefValue(Object slotValue, Object value) {
        if (slotValue instanceof EvalReference) {
            ((EvalReference) slotValue).setValue(value);
            return slotValue;
        }
        return new EvalReference(value);
    }

    /** RenewReferenceExecutable 语义（含 frame.getRef 的强制转型语义）。 */
    public static Object renewReference(Object slotValue) {
        EvalReference ref = (EvalReference) slotValue;
        if (ref != null) {
            ref = new EvalReference(ref.getValue());
        } else {
            ref = new EvalReference(0);
        }
        return ref;
    }

    // ------------------------------------------------------------------
    // 覆盖 A（I3）：slot 写族 / 复合赋值公共语义（自 AbstractExecutable.selfAssignValue 提取）
    // ------------------------------------------------------------------

    public static Object selfAssignValue(SourceLocation loc, String display, XLangOperator op,
                                         Object value, Object change) {
        switch (op) {
            case SELF_ASSIGN_BIT_AND:
                return MathHelper.band(value, change);
            case SELF_ASSIGN_BIT_OR:
                return MathHelper.bor(value, change);
            case SELF_ASSIGN_BIT_XOR:
                return MathHelper.bxor(value, change);
            case SELF_ASSIGN_DIV:
                return MathHelper.divide(value, change);
            case SELF_ASSIGN_MULTI:
                return MathHelper.multiply(value, change);
            case SELF_ASSIGN_MOD:
                return MathHelper.mod(value, change);
            case SELF_ASSIGN_LEFT_SHIFT:
                return MathHelper.sl(value, change);
            case SELF_ASSIGN_RIGHT_SHIFT:
                return MathHelper.sr(value, change);
            case SELF_ASSIGN_UNSIGNED_RIGHT_SHIFT:
                return MathHelper.usr(value, change);
            case SELF_ASSIGN_ADD:
                if (value instanceof String || change instanceof String)
                    return String.valueOf(value) + String.valueOf(change);
                return MathHelper.add(value, change);
            case SELF_ASSIGN_MINUS:
                return MathHelper.minus(value, change);
            default:
                throw newError(ERR_EXEC_NOT_SUPPORTED_OPERATOR, loc, display).param(ARG_OP, op);
        }
    }

    /** slot 自增/自减语义（SelfInc/SelfDec/ReferenceSelfInc/ReferenceSelfDec）：返回旧值。 */
    public static Object selfIncValue(Object value, int delta) {
        return MathHelper.add(value, delta);
    }

    // ------------------------------------------------------------------
    // 覆盖 A（I3）：类型操作族
    // ------------------------------------------------------------------

    /** TypeOfExecutable 语义（意图语义：null => "undefined"；原实现误引用枚举常量致 NPE，已修复）。 */
    public static Object typeOf(Object v) {
        return v == null ? "undefined" : v.getClass().getTypeName();
    }

    /** InstanceOfExecutable 语义（IGenericType.isInstance = rawClass.isInstance）。 */
    public static boolean instanceOf(Object value, String className) {
        if (value == null)
            return false;
        return resolveClass(className).isInstance(value);
    }

    /** ConvertExecutable 生成代码入口：按 `$xxx` 函数名经 SysConverterRegistry 解析同一 converter。 */
    public static Object convertValue(SourceLocation loc, String display, String funcName,
                                      IEvalScope scope, Object value) {
        ITypeConverter converter = resolveConverter(funcName);
        return converter.convertEx(scope, value, err -> newError(err, loc, display));
    }

    /** ConvertWithDefaultExecutable 生成代码入口（default 值由调用方在 value==null 时求值后传入）。 */
    public static Object convertWithDefault(SourceLocation loc, String display, String funcName,
                                            IEvalScope scope, Object value, Object defaultValue) {
        if (value != null)
            return convertValue(loc, display, funcName, scope, value);
        return convertValue(loc, display, funcName, scope, defaultValue);
    }

    /** CastExecutable 生成代码入口（converter/defaultValue 按 className 运行时解析，与构造期同源）。 */
    public static Object castValue(SourceLocation loc, String display, IEvalScope scope,
                                   String className, Object value) {
        Class<?> clazz = resolveClass(className);
        Object defaultValue = ConvertHelper.getDefault(clazz);
        if (value == null)
            return defaultValue;
        ITypeConverter converter = ReflectionManager.instance().getConverterForJavaType(clazz);
        Object converted = converter.convertEx(scope, value, err -> newError(err, loc, display));
        if (converted != null) {
            // 与解释器现状一致：校验的是原值（quirk 忠实保留，见 I3 log watch-only 记录）
            if (!clazz.isInstance(value))
                throw newError(ApiErrors.ERR_CONVERT_TO_TYPE_FAIL, loc, display)
                        .param(ApiErrors.ARG_VALUE, value)
                        .param(ApiErrors.ARG_SRC_TYPE, value.getClass().getTypeName())
                        .param(ApiErrors.ARG_TARGET_TYPE, clazz.getTypeName());
        }
        return converted;
    }

    private static ITypeConverter resolveConverter(String funcName) {
        ITypeConverter converter = SysConverterRegistry.instance()
                .getConverterByName(funcName.substring(1));
        if (converter == null)
            throw new NopEvalException(ERR_XLANG_UNRESOLVED_IDENTIFIER)
                    .param(ARG_VAR_NAME, funcName);
        return converter;
    }

    private static Class<?> resolveClass(String className) {
        try {
            return ClassHelper.forName(className);
        } catch (ClassNotFoundException e) {
            throw new NopEvalException(ERR_EXEC_CLASS_NOT_FOUND, e).param(ARG_CLASS_NAME, className);
        }
    }

    // ------------------------------------------------------------------
    // 覆盖 A（I3）：对象/集合构造与访问族——属性反射（与解释器同一实现，按 propName 全局缓存）
    // ------------------------------------------------------------------

    /**
     * 按 propName 分立的属性访问器缓存（镜像解释器 per-node transient 缓存语义：
     * 同 propName 下按 class 缓存 getter/setter，跨名不污染）。
     */
    private static final Map<String, PropAccessor> PROP_ACCESSORS = new ConcurrentHashMap<>();

    static final class PropAccessor {
        final Map<Class<?>, IPropertyGetter> getters = new ConcurrentHashMap<>();
        final Map<Class<?>, IPropertySetter> setters = new ConcurrentHashMap<>();
    }

    static PropAccessor propAccessor(String propName) {
        return PROP_ACCESSORS.computeIfAbsent(propName, k -> new PropAccessor());
    }

    /** AbstractPropertyExecutable.getGetter 提取（Annotation/array-length/Class 静态字段/扩展属性）。 */
    public static IPropertyGetter getPropGetter(SourceLocation loc, String display, String propName,
                                                Class<?> clazz, Object bean) {
        if (bean instanceof Annotation)
            clazz = ((Annotation) bean).annotationType();

        if (clazz.isArray() && XLangConstants.PROP_NAME_LENGTH.equals(propName))
            return ArrayLengthGetter.INSTANCE;

        IBeanModel beanModel = ReflectionManager.instance().getBeanModelForClass(clazz);
        if (clazz == Class.class)
            return getStaticFieldGetter(loc, display, propName, clazz);
        IBeanPropertyModel field = beanModel.getPropertyModel(propName);
        if (field == null) {
            if (beanModel.isAllowGetExtProperty())
                return beanModel.getExtPropertyGetter();
            throw newError(ERR_EXEC_UNKNOWN_PROP, loc, display).param(ARG_CLASS_NAME, clazz.getName())
                    .param(ARG_PROP_NAME, propName);
        }
        return field.getGetter();
    }

    /** AbstractPropertyExecutable.getSetter 提取。 */
    public static IPropertySetter getPropSetter(SourceLocation loc, String display, String propName,
                                                Class<?> clazz) {
        if (clazz == Class.class) {
            return getStaticFieldSetter(loc, display, propName, clazz);
        }
        IBeanModel beanModel = ReflectionManager.instance().getBeanModelForClass(clazz);
        IBeanPropertyModel field = beanModel.getPropertyModel(propName);
        if (field == null) {
            if (beanModel.isAllowSetExtProperty())
                return beanModel.getExtPropertySetter();
            throw newError(ERR_EXEC_UNKNOWN_PROP, loc, display).param(ARG_CLASS_NAME, clazz.getName())
                    .param(ARG_PROP_NAME, propName);
        }
        return field.getSetter();
    }

    public static IPropertySetter getStaticFieldSetter(SourceLocation loc, String display, String propName,
                                                       Class<?> clazz) {
        IClassModel classModel = ReflectionManager.instance().getClassModel(clazz);
        IFieldModel field = classModel.getStaticField(propName);
        if (field == null)
            throw newError(ERR_EXEC_UNKNOWN_STATIC_FIELD, loc, display).param(ARG_CLASS_NAME, clazz.getName())
                    .param(ARG_PARAM_NAME, propName);
        return field.getSetter();
    }

    public static IPropertyGetter getStaticFieldGetter(SourceLocation loc, String display, String propName,
                                                       Class<?> clazz) {
        IClassModel classModel = ReflectionManager.instance().getClassModel(clazz);
        IFieldModel field = classModel.getStaticField(propName);
        if (field == null)
            throw newError(ERR_EXEC_UNKNOWN_STATIC_FIELD, loc, display).param(ARG_CLASS_NAME, clazz.getName())
                    .param(ARG_PARAM_NAME, propName);
        return field.getGetter();
    }

    /** AbstractPropertyExecutable.readProp 提取（含 bizFatal 传播）。 */
    public static Object readPropValue(SourceLocation loc, String display, String propName, Object obj,
                                       IPropertyGetter reader, IEvalScope scope) {
        try {
            return reader.getProperty(obj, propName, scope);
        } catch (Exception e) {
            throw wrapPropException(loc, display, ERR_EXEC_READ_PROP_FAIL, e,
                    obj == null ? "null" : obj.getClass().getName(), propName);
        }
    }

    /** AbstractPropertyExecutable.setProp 提取。 */
    public static void writePropValue(SourceLocation loc, String display, String propName, Object obj,
                                      Object value, IPropertySetter setter, IEvalScope scope) {
        try {
            setter.setProperty(obj, propName, value, scope);
        } catch (Exception e) {
            throw wrapPropException(loc, display, ERR_EXEC_WRITE_PROP_FAIL, e, obj.getClass().getName(), propName);
        }
    }

    /** AbstractExecutable.wrapPropException 提取（forWrap + bizFatal 传播语义保持）。 */
    public static NopException wrapPropException(SourceLocation loc, String display, ErrorCode errorCode,
                                                 Throwable e, String className, String propName) {
        NopException err = newError(errorCode, e, loc, display).forWrap()
                .param(ARG_CLASS_NAME, className)
                .param(ARG_PROP_NAME, propName);
        if (e instanceof NopException && ((NopException) e).isBizFatal())
            err.bizFatal(true);
        return err;
    }

    /** GetPropertyExecutable 完整语义（生成代码入口，含按 propName 全局缓存的 getter 解析）。 */
    public static Object getProperty(SourceLocation loc, String display, String objDisplay, boolean optional,
                                     String propName, Object obj, IEvalScope scope) {
        if (obj == null) {
            if (!optional)
                throw newError(ERR_EXEC_GET_PROP_ON_NULL_OBJ, loc, display)
                        .param(ARG_PROP_NAME, propName).param(ARG_OBJ_EXPR, objDisplay);
            return null;
        }
        IPropertyGetter reader = cachedGetter(loc, display, propName, obj);
        return readPropValue(loc, display, propName, obj, reader, scope);
    }

    /** SetterSetPropertyExecutable 生成代码入口（无产生路径；按 propName 同一解析）。 */
    public static Object getterGetProperty(SourceLocation loc, String display, String propName,
                                           Object obj, IEvalScope scope) {
        if (obj == null)
            return null;
        IPropertyGetter reader = cachedGetter(loc, display, propName, obj);
        return readPropValue(loc, display, propName, obj, reader, scope);
    }

    /** StaticGetterGetPropertyExecutable 生成代码入口（按 className/propName 运行时解析静态字段 getter）。 */
    public static Object getStaticProperty(SourceLocation loc, String display, String className,
                                           String propName, IEvalScope scope) {
        IPropertyGetter getter = getStaticFieldGetter(loc, display, propName, resolveClass(className));
        try {
            return getter.getProperty(null, propName, scope);
        } catch (Exception e) {
            throw wrapPropException(loc, display, ERR_EXEC_READ_PROP_FAIL, e, className, propName);
        }
    }

    private static IPropertyGetter cachedGetter(SourceLocation loc, String display, String propName, Object bean) {
        Class<?> clazz = bean.getClass();
        PropAccessor accessor = propAccessor(propName);
        IPropertyGetter reader = accessor.getters.get(clazz);
        if (reader == null) {
            // 解析失败（unknown prop / not readable）在共享 getPropGetter 中报错，与解释器 cache-miss 路径一致
            reader = getPropGetter(loc, display, propName, clazz, bean);
            accessor.getters.put(clazz, reader);
        }
        return reader;
    }

    /** SetPropertyExecutable 完整语义（生成代码入口）。 */
    public static Object setProperty(SourceLocation loc, String display, String propName,
                                     Object obj, Object value, IEvalScope scope) {
        if (obj == null)
            throw newError(ERR_EXEC_WRITE_PROP_OBJ_NULL, loc, display);
        IPropertySetter setter = cachedSetter(loc, display, propName, obj.getClass());
        writePropValue(loc, display, propName, obj, value, setter, scope);
        return value;
    }

    /** SetterSetPropertyExecutable 生成代码入口（无产生路径；按 propName 同一解析）。 */
    public static Object setterSetProperty(SourceLocation loc, String display, String propName,
                                           Object obj, Object value, IEvalScope scope) {
        return setProperty(loc, display, propName, obj, value, scope);
    }

    /**
     * DeletePropertyExecutable 生成代码入口（non-computed 形式 `delete obj.prop`）。
     * 分派：Map-like → remove；Bean → 普通 setter 设 null（失败回退 setExtProperty(null)）；数组在 deleteAttr 路径拒绝。
     */
    public static Object deleteProperty(SourceLocation loc, String display, String propName,
                                       Object obj, IEvalScope scope) {
        if (obj == null)
            throw newError(ERR_EXEC_DELETE_ON_NULL_OBJ, loc, display);

        Class<?> clazz = obj.getClass();
        IBeanModel beanModel = ReflectionManager.instance().getBeanModelForClass(clazz);
        if (beanModel.isMapLike()) {
            Object oldValue = ((Map) obj).remove(propName);
            return oldValue != null;
        }

        // IMapLike（DynamicObject 等非 Map 子类但语义 Map-like）：
        //   1. toMap() 可写时用 remove 删除条目
        //   2. unmodifiableMap 回退到 prop_remove（DynamicObject 已覆盖为 removeProp 真删除）
        if (obj instanceof io.nop.api.core.util.IMapLike) {
            Map<String, Object> map = ((io.nop.api.core.util.IMapLike) obj).toMap();
            try {
                Object oldValue = map.remove(propName);
                return oldValue != null;
            } catch (UnsupportedOperationException e) {
                // unmodifiableMap 回退到 prop_remove 真删除（DynamicObject 等已实现）
            }
        }
        if (obj instanceof io.nop.core.reflect.hook.IPropSetMissingHook) {
            io.nop.core.reflect.hook.IPropSetMissingHook hook =
                    (io.nop.core.reflect.hook.IPropSetMissingHook) obj;
            Object oldValue = obj instanceof io.nop.core.reflect.hook.IPropGetMissingHook
                    ? ((io.nop.core.reflect.hook.IPropGetMissingHook) obj).prop_get(propName)
                    : null;
            hook.prop_remove(propName);  // 真删除（DynamicObject.removeProp 等）；default fallback 是 set null
            return oldValue != null;
        }

        // Bean 路径：先尝试普通 setter 设 null（清空值），失败后回退到 setExtProperty(null)
        IPropertyGetter getter = getPropGetter(loc, display, propName, clazz, obj);
        Object oldValue = null;
        IPropertySetter setter = null;
        if (getter != null) {
            oldValue = readPropValue(loc, display, propName, obj, getter, scope);
            try {
                setter = getPropSetter(loc, display, propName, clazz);
                writePropValue(loc, display, propName, obj, null, setter, scope);
                return oldValue != null;
            } catch (Exception ignored) {
                // setter 拒绝 null（如基本类型字段），回退到 setExtProperty(null) 路径
            }
        }

        if (beanModel.isAllowSetExtProperty()) {
            try {
                beanModel.setExtProperty(obj, propName, null);
                return oldValue != null;
            } catch (Exception ignored) {
                // setExtProperty 也拒绝时落入 NOT_SUPPORTED
            }
        }

        throw newError(ERR_EXEC_DELETE_NOT_SUPPORTED, loc, display).param(ARG_CLASS_NAME, clazz.getName());
    }

    /**
     * DeleteAttrExecutable 生成代码入口（computed 形式 `delete obj[expr]`）。
     * 分派：数组拒绝；attr=null 时 Map 走 map.remove(null)，其他抛错；attr=Integer 且 obj=List 走 List.remove(int)；
     *       List 按值删；Map 按 key 删；Bean 走 deleteProperty。
     */
    public static Object deleteAttr(SourceLocation loc, String display, String attrDisplay,
                                    Object obj, Object attr, IEvalScope scope) {
        if (obj == null)
            throw newError(ERR_EXEC_DELETE_ON_NULL_OBJ, loc, display);

        Class<?> clazz = obj.getClass();
        if (clazz.isArray())
            throw newError(ERR_EXEC_DELETE_ON_ARRAY, loc, display);

        if (attr == null) {
            IBeanModel beanModel = ReflectionManager.instance().getBeanModelForClass(clazz);
            if (beanModel.isMapLike())
                return ((Map) obj).remove(null) != null;
            if (obj instanceof io.nop.api.core.util.IMapLike)
                return ((io.nop.api.core.util.IMapLike) obj).toMap().remove(null) != null;
            throw newError(ERR_EXEC_DELETE_ATTR_EXPR_RETURN_NULL, loc, display).param(ARG_ATTR_EXPR, attrDisplay);
        }

        if (obj instanceof List) {
            if (attr instanceof Integer) {
                int idx = ((Integer) attr).intValue();
                int size = ((List<?>) obj).size();
                if (idx < 0 || idx >= size)
                    return false;
                ((List<?>) obj).remove(idx);
                return true;
            }
            return ((List<?>) obj).remove(attr);
        }

        IBeanModel beanModel = ReflectionManager.instance().getBeanModelForClass(clazz);
        if (beanModel.isMapLike())
            return ((Map) obj).remove(attr) != null;

        return deleteProperty(loc, display, String.valueOf(attr), obj, scope);
    }

    private static IPropertySetter cachedSetter(SourceLocation loc, String display, String propName,
                                                Class<?> clazz) {
        PropAccessor accessor = propAccessor(propName);
        IPropertySetter setter = accessor.setters.get(clazz);
        if (setter == null) {
            setter = getPropSetter(loc, display, propName, clazz);
            accessor.setters.put(clazz, setter);
        }
        return setter;
    }

    /** MakePropertyExecutable 完整语义（生成代码入口）。 */
    public static Object makeProperty(SourceLocation loc, String display, String objDisplay,
                                      String propName, Object obj, IEvalScope scope) {
        if (obj == null)
            throw newError(ERR_EXEC_MAKE_PROP_OBJ_NULL, loc, display).param(ARG_OBJ_EXPR, objDisplay);
        IPropertyGetter reader = getMakerGetter(loc, display, propName, obj);
        return readMakerPropValue(loc, display, propName, obj, reader, scope);
    }

    /** MakePropertyExecutable.getGetter 提取（maker 解析：注解类型/扩展属性/未知属性报错）。 */
    public static IPropertyGetter getMakerGetter(SourceLocation loc, String display, String propName, Object bean) {
        Class<?> clazz = bean.getClass();
        if (bean instanceof Annotation)
            clazz = ((Annotation) bean).annotationType();
        IBeanModel beanModel = ReflectionManager.instance().getBeanModelForClass(clazz);
        IBeanPropertyModel field = beanModel.getPropertyModel(propName);
        if (field == null) {
            if (beanModel.isAllowGetExtProperty()) {
                return beanModel.getExtPropertyGetter();
            }
            throw newError(ERR_EXEC_UNKNOWN_PROP, loc, display).param(ARG_CLASS_NAME, clazz.getName())
                    .param(ARG_PARAM_NAME, propName);
        }
        return field.getMaker();
    }

    /** MakePropertyExecutable.readProp 提取（读值 + null 报错）。 */
    public static Object readMakerPropValue(SourceLocation loc, String display, String propName,
                                            Object obj, IPropertyGetter reader, IEvalScope scope) {
        Object value = readPropValue(loc, display, propName, obj, reader, scope);
        if (value == null)
            throw newError(ERR_EXEC_MAKE_PROP_NULL, loc, display);
        return value;
    }

    /** SelfAssignPropertyExecutable 完整语义（生成代码入口）。 */
    public static Object selfAssignProperty(SourceLocation loc, String display, String propName,
                                            XLangOperator operator, Object obj, Object value,
                                            IEvalScope scope) {
        if (obj == null)
            throw newError(ERR_EXEC_WRITE_PROP_OBJ_NULL, loc, display);

        IPropertyGetter getter = cachedGetter(loc, display, propName, obj);
        Object oldValue = readPropValue(loc, display, propName, obj, getter, scope);
        if (oldValue == null)
            throw newError(ERR_EXEC_OBJ_PROP_IS_NULL, loc, display).param(ARG_PROP_NAME, propName);

        Object newValue = selfAssignValue(loc, display, operator, oldValue, value);
        IPropertySetter setter = cachedSetter(loc, display, propName, obj.getClass());
        writePropValue(loc, display, propName, obj, newValue, setter, scope);
        return newValue;
    }

    // ------------------------------------------------------------------
    // 覆盖 A（I3）：对象/集合构造与访问族——下标/属性表达式（Get/SetAttr、SelfAssignAttr）
    // ------------------------------------------------------------------

    /** AbstractExecutable.readAttr 提取。 */
    public static Object readAttrValue(SourceLocation loc, String display, ErrorCode errorCode,
                                       IBeanModel beanModel, Object obj, Object attrValue) {
        try {
            if (beanModel.isMapLike())
                return ((Map) obj).get(attrValue);
            return beanModel.getProperty(obj, attrValue.toString());
        } catch (Exception e) {
            throw wrapAttrException(loc, display, errorCode, e, obj, attrValue);
        }
    }

    /** AbstractExecutable.setAttr 提取。 */
    public static void writeAttrValue(SourceLocation loc, String display, ErrorCode errorCode,
                                      IBeanModel beanModel, Object obj, Object attrValue, Object value) {
        try {
            if (beanModel.isMapLike()) {
                ((Map) obj).put(attrValue, value);
            } else {
                beanModel.setProperty(obj, attrValue.toString(), value);
            }
        } catch (Exception e) {
            throw wrapAttrException(loc, display, errorCode, e, obj, attrValue);
        }
    }

    /** AbstractExecutable.wrapAttrException 提取。 */
    public static NopException wrapAttrException(SourceLocation loc, String display, ErrorCode errorCode,
                                                 Throwable e, Object obj, Object attrValue) {
        NopException err = newError(errorCode, e, loc, display).forWrap()
                .param(ARG_CLASS_NAME, obj.getClass().getName())
                .param(ARG_ATTR_VALUE, attrValue);
        if (e instanceof NopException && ((NopException) e).isBizFatal())
            err.bizFatal(true);
        return err;
    }

    /** GetAttrExecutable 完整语义（生成代码入口）。 */
    public static Object getAttr(SourceLocation loc, String display, String objDisplay, String attrDisplay,
                                 boolean optional, Object obj, Object attr) {
        if (obj == null) {
            if (!optional)
                throw newError(ERR_EXEC_GET_ATTR_ON_NULL_OBJ, loc, display)
                        .param(ARG_ATTR_EXPR, attrDisplay).param(ARG_OBJ_EXPR, objDisplay);
            return null;
        }
        if (attr instanceof Integer)
            return BeanTool.getByIndex(obj, (Integer) attr);
        IBeanModel beanModel = ReflectionManager.instance().getBeanModelForClass(obj.getClass());
        if (attr == null && !beanModel.isMapLike())
            throw newError(ERR_EXEC_READ_ATTR_EXPR_RETURN_NULL, loc, display).param(ARG_ATTR_EXPR, attrDisplay);
        return readAttrValue(loc, display, ERR_EXEC_READ_ATTR_FAIL, beanModel, obj, attr);
    }

    /** SetAttrExecutable 完整语义（生成代码入口；obj 为 null 静默返回 null 与解释器一致）。 */
    public static Object setAttr(SourceLocation loc, String display, String attrDisplay,
                                 Object obj, Object attr, Object value) {
        if (obj == null)
            return null;
        if (attr instanceof Integer) {
            BeanTool.setByIndex(obj, (Integer) attr, value);
            return value;
        }
        IBeanModel beanModel = ReflectionManager.instance().getBeanModelForClass(obj.getClass());
        if (attr == null && !beanModel.isMapLike())
            throw newError(ERR_EXEC_WRITE_ATTR_EXPR_RETURN_NULL, loc, display).param(ARG_ATTR_EXPR, attrDisplay);
        writeAttrValue(loc, display, ERR_EXEC_WRITE_ATTR_FAIL, beanModel, obj, attr, value);
        return value;
    }

    /** SelfAssignAttrExecutable 完整语义（生成代码入口）。 */
    public static Object selfAssignAttr(SourceLocation loc, String display, String attrDisplay,
                                        XLangOperator operator, Object obj, Object attr, Object value) {
        if (obj == null)
            return null;

        Object oldValue;
        if (attr instanceof Integer) {
            oldValue = BeanTool.getByIndex(obj, (Integer) attr);
        } else {
            IBeanModel beanModel = ReflectionManager.instance().getBeanModelForClass(obj.getClass());
            if (attr == null && !beanModel.isMapLike())
                throw newError(ERR_EXEC_READ_ATTR_EXPR_RETURN_NULL, loc, display).param(ARG_ATTR_EXPR, attrDisplay);
            oldValue = readAttrValue(loc, display, ERR_EXEC_READ_ATTR_FAIL, beanModel, obj, attr);
        }

        if (oldValue == null)
            throw newError(ERR_EXEC_OBJ_ATTR_IS_NULL, loc, display).param(ARG_ATTR_EXPR, attrDisplay);

        Object newValue = selfAssignValue(loc, display, operator, oldValue, value);

        if (attr instanceof Integer) {
            BeanTool.setByIndex(obj, (Integer) attr, newValue);
            return newValue;
        }
        IBeanModel beanModel = ReflectionManager.instance().getBeanModelForClass(obj.getClass());
        if (attr == null && !beanModel.isMapLike())
            throw newError(ERR_EXEC_WRITE_ATTR_EXPR_RETURN_NULL, loc, display).param(ARG_ATTR_EXPR, attrDisplay);
        writeAttrValue(loc, display, ERR_EXEC_WRITE_ATTR_FAIL, beanModel, obj, attr, newValue);
        return newValue;
    }

    // ------------------------------------------------------------------
    // 覆盖 A（I3）：对象/集合构造族（NewObject/NewList/NewMap spread 分支）
    // ------------------------------------------------------------------

    private static final Map<String, IClassModel> CLASS_MODELS = new ConcurrentHashMap<>();

    /** NewObjectExecutable 完整语义（生成代码入口；按 className 缓存 classModel）。 */
    public static Object newInstance(SourceLocation loc, String display, String className,
                                     Object[] argValues, IEvalScope scope) {
        IClassModel classModel = CLASS_MODELS.computeIfAbsent(className, k ->
                ReflectionManager.instance().getClassModel(resolveClass(k)));
        return newInstance(loc, display, classModel, argValues, scope);
    }

    /** NewObjectExecutable 完整语义（解释器入口：复用编译期 classModel，零查找）。 */
    public static Object newInstance(SourceLocation loc, String display, IClassModel classModel,
                                     Object[] argValues, IEvalScope scope) {
        IFunctionModel constructor = classModel.getConstructorForArgs(argValues);
        if (constructor == null)
            throw newError(ERR_EXEC_CLASS_NO_CONSTRUCTOR, loc, display)
                    .param(ARG_CLASS_NAME, classModel.getClassName()).param(ARG_ARG_COUNT, argValues.length);
        Object ret = constructor.invoke(null, argValues, scope);
        if (classModel.isAssignableTo(ISourceLocationSetter.class))
            ((ISourceLocationSetter) ret).setLocation(loc);
        return ret;
    }

    /** NewListExecutable spread 分支提取（null 跳过 / Collection 展开 / 单值追加）。 */
    public static void spreadListAdd(List<Object> list, Object value) {
        if (value != null) {
            if (value instanceof Collection) {
                list.addAll((Collection<?>) value);
            } else {
                list.add(value);
            }
        }
    }

    /** NewMapExecutable spread 分支提取（null/undefined 跳过 / Map / DynamicObject / bean 序列化属性）。 */
    public static void spreadMapPut(Map<String, Object> map, Object value) {
        if (value != null && value != Undefined.undefined) {
            if (value instanceof Map) {
                map.putAll(((Map<String, ?>) value));
            } else if (value instanceof DynamicObject) {
                map.putAll(((DynamicObject) value).obj_propValues());
            } else {
                IBeanModel beanModel = ReflectionManager.instance().getBeanModelForClass(value.getClass());
                beanModel.forEachSerializableProp(prop -> {
                    Object propValue = prop.getPropertyValue(value);
                    map.put(prop.getName(), propValue);
                });

                Set<String> propNames = beanModel.getExtPropertyNames(value);
                if (propNames != null) {
                    for (String propName : propNames) {
                        Object propValue = beanModel.getExtProperty(value, propName);
                        map.put(propName, propValue);
                    }
                }
            }
        }
    }

    /** CloneLiteralExecutable 生成代码入口：List 复合字面量（每次求值深拷贝；嵌套复合字面量由转译器
     *  以嵌套 cloneList/cloneMap 调用作为元素表达式产生，此处只需按值收集后整体深拷贝）。 */
    public static Object cloneList(Object[] items) {
        List<Object> list = new ArrayList<>(items.length);
        for (Object item : items) {
            list.add(item);
        }
        return CloneHelper.deepClone(list);
    }

    /** CloneLiteralExecutable 生成代码入口：Map 复合字面量（键值交替数组，每次求值深拷贝）。 */
    public static Object cloneMap(Object[] kv) {
        Map<String, Object> map = CollectionHelper.newLinkedHashMap(kv.length / 2);
        for (int i = 0, n = kv.length; i < n; i += 2) {
            map.put(StringHelper.toString(kv[i], null), kv[i + 1]);
        }
        return CloneHelper.deepClone(map);
    }

    // ------------------------------------------------------------------
    // 覆盖 A（I3）：绑定/守卫/调试族
    // ------------------------------------------------------------------

    /** ArrayBindingAssignExecutable 前置语义。 */
    @SuppressWarnings("unchecked")
    public static List<Object> asListBinding(SourceLocation loc, String display, Object value) {
        if (!(value instanceof List))
            throw newError(ERR_EXEC_ARRAY_BINDING_NOT_LIST, loc, display).param(ARG_VALUE, value);
        return (List<Object>) value;
    }

    /** ObjectBindingAssignExecutable 前置语义。 */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> asMapBinding(SourceLocation loc, String display, Object value) {
        if (!(value instanceof Map))
            throw newError(ERR_EXEC_OBJECT_BINDING_NOT_MAP, loc, display).param(ARG_VALUE, value);
        return (Map<String, Object>) value;
    }

    /** GuardNotEmptyExecutable 语义（提取；解释器改调）。 */
    public static Object guardNotEmpty(SourceLocation loc, String display, String target, Object v) {
        if (StringHelper.isEmptyObject(v))
            throw newError(ERR_EXEC_VALUE_NOT_ALLOW_EMPTY, loc, display).param(ARG_TARGET, target);
        return v;
    }

    /** VarStatusExecutable 语义（生成代码入口；写入 slot 由生成代码完成）。 */
    public static Object varStatus(SourceLocation loc, String display, String itemsDisplay, Object items) {
        if (items == null)
            return null;
        try {
            return new LoopVarStatus<>(CollectionHelper.toIterator(items, false), true);
        } catch (NopException e) {
            e.addXplStack(itemsDisplay);
            throw e;
        }
    }

    // ------------------------------------------------------------------
    // 覆盖 A（I3）：并入残余算子族（PropIn/Range）
    // ------------------------------------------------------------------

    /** PropInExecutable 语义（提取；解释器改调）。 */
    public static boolean propIn(Object leftValue, Object rightValue) {
        if (leftValue == null || rightValue == null)
            return false;
        if (!(leftValue instanceof String))
            return false;
        IBeanModel beanModel = ReflectionManager.instance().getBeanModelForClass(rightValue.getClass());
        String propName = leftValue.toString();
        IBeanPropertyModel propModel = beanModel.getPropertyModel(propName);
        if (propModel != null)
            return true;
        return beanModel.isAllowExtProperty(rightValue, propName);
    }

    /** RangeExecutable 语义（提取；解释器改调）。 */
    public static Object range(SourceLocation loc, String display, Object begin, Object end, Object step) {
        Integer beginValue = ConvertHelper.toInt(begin, err -> newError(err, loc, display));
        Integer endValue = ConvertHelper.toInt(end, err -> newError(err, loc, display));
        Integer stepValue = ConvertHelper.toInt(step, err -> newError(err, loc, display));

        if (stepValue == null) {
            stepValue = 1;
        } else if (stepValue == 0) {
            throw newError(ERR_EXEC_LOOP_STEP_MUST_NOT_BE_ZERO, loc, display);
        }

        if (beginValue == null)
            beginValue = 0;
        if (endValue == null)
            endValue = 0;

        return new IntRangeIterator(beginValue, endValue, stepValue);
    }

    // ------------------------------------------------------------------
    // 覆盖 B（I4）：函数值适配 / 输出与换缓冲 / throw 语义共享 helper
    // ------------------------------------------------------------------

    /**
     * 生成函数体回调：生成类私有方法经方法引用接入（签名 = scope + 实参数组 + 闭包捕获数组）。
     * 实参缺省填充（demandArgCount..argCount 的字面量缺省）由生成体自身内嵌承载。
     */
    public interface IGeneratedFunctionBody {
        Object invoke(IEvalScope scope, Object[] args, Object[] captured);
    }

    /**
     * 换缓冲类节点（Collect 族与 Gen 族）的生成体回调：输出缓冲、pending ExitMode 通道与
     * 调用方帧 slot 数组以参数线程化（生成代码无 EvalRuntime；解释器侧改调时以闭包内
     * rt.setOut 承载同等交换，frame 参数不使用）。
     */
    public interface IGeneratedOutBody {
        Object run(IEvalScope scope, IEvalOutput out, ExitMode[] exit, Object[] frame);
    }

    /**
     * 函数值载荷（LiteralExecutable(ExecutableFunction)/BuildFuncRefExecutable）下降为生成私有方法后的
     * {@link IEvalFunction} 适配（设计 java §三目标形态；invoke/callN 参数个数校验与
     * {@code ExecutableFunction.callN} 同码）。
     */
    public static IEvalFunction generatedFunction(int argCount, int demandArgCount,
                                                  IGeneratedFunctionBody body, Object[] captured) {
        return new GeneratedEvalFunction(argCount, demandArgCount, body, captured);
    }

    public static final class GeneratedEvalFunction implements IEvalFunction {
        private final int argCount;
        private final int demandArgCount;
        private final IGeneratedFunctionBody body;
        private final Object[] captured;

        GeneratedEvalFunction(int argCount, int demandArgCount, IGeneratedFunctionBody body, Object[] captured) {
            this.argCount = argCount;
            this.demandArgCount = demandArgCount;
            this.body = body;
            this.captured = captured == null ? new Object[0] : captured;
        }

        public Object invokeWithArgs(Object[] args, IEvalScope scope) {
            Object[] a = args == null ? new Object[0] : args;
            if (a.length > argCount)
                throw tooManyArgs(a.length);
            return body.invoke(scope, a, captured);
        }

        @Override
        public Object invoke(Object thisObj, Object[] args, IEvalScope scope) {
            return invokeWithArgs(args, scope);
        }

        @Override
        public Object call0(Object thisObj, IEvalScope scope) {
            return body.invoke(scope, new Object[0], captured);
        }

        @Override
        public Object call1(Object thisObj, Object arg, IEvalScope scope) {
            if (argCount < 1)
                throw tooManyArgs(1);
            return body.invoke(scope, new Object[]{arg}, captured);
        }

        @Override
        public Object call2(Object thisObj, Object arg1, Object arg2, IEvalScope scope) {
            if (argCount < 2)
                throw tooManyArgs(2);
            return body.invoke(scope, new Object[]{arg1, arg2}, captured);
        }

        @Override
        public Object call3(Object thisObj, Object arg1, Object arg2, Object arg3, IEvalScope scope) {
            if (argCount < 3)
                throw tooManyArgs(3);
            return body.invoke(scope, new Object[]{arg1, arg2, arg3}, captured);
        }

        private NopEvalException tooManyArgs(int count) {
            NopEvalException e = new NopEvalException(ERR_EXEC_TOO_MANY_ARGS);
            e.param(io.nop.xlang.XLangErrors.ARG_MAX_COUNT, argCount)
                    .param(io.nop.xlang.XLangErrors.ARG_ARG_COUNT, count);
            return e;
        }
    }

    /**
     * VarFunctionExecutable/VarExecutableFunction 的函数值调用语义（生成代码入口，先例
     * {@link #invokeGlobalFunction}）：null → optional 短路或 ERR_EXEC_CALL_NULL_FUNCTION；
     * 非 IEvalFunction 且非 null → ERR_EXEC_EXPR_NOT_RETURN_FUNC（与解释器 getFunction 同码）；
     * ExecutableFunction 走 executeWithArgs；GeneratedEvalFunction 走 invokeWithArgs；其余统一 invoke。
     * argValues 在 funcValue == null 时允许传 null（实参不求值短路由生成代码承载）。
     */
    public static Object callVarFunction(SourceLocation loc, String display, boolean optional,
                                         Object funcValue, Object[] argValues, IEvalScope scope) {
        IEvalFunction func = null;
        if (funcValue instanceof ExecutableFunction) {
            return ((ExecutableFunction) funcValue).executeWithArgs(XLang.getExecutor(), argValues,
                    new EvalRuntime(scope));
        }
        if (funcValue != null) {
            func = EvalFunctionHelper.toEvalFunction(funcValue);
            if (func == null)
                throw newError(ERR_EXEC_EXPR_NOT_RETURN_FUNC, loc, display).param(ARG_FUNC_NAME, display)
                        .param(ARG_CLASS_NAME, funcValue.getClass().getName());
        }
        if (func == null) {
            if (optional)
                return null;
            throw newError(ERR_EXEC_CALL_NULL_FUNCTION, loc, display);
        }
        try {
            if (func instanceof GeneratedEvalFunction)
                return ((GeneratedEvalFunction) func).invokeWithArgs(argValues, scope);
            return func.invoke(null, argValues, scope);
        } catch (NopException e) {
            e.addXplStack(display + "@" + loc);
            throw e;
        }
    }

    /**
     * ThrowErrorCodeExecutable 语义（提取；解释器改调）。source 仅解释器传节点自身（display 用），
     * 生成代码传 null；display = 节点 display 串（ARG_EXPR 语义与解释器一致）。
     */
    public static void throwErrorCode(SourceLocation loc, io.nop.api.core.util.ISourceLocationGetter source,
                                      String display, Object error, Object params) {
        if (error instanceof NopException) {
            NopException exp = (NopException) error;
            if (exp.getErrorLocation() == null)
                exp.loc(loc);
            throw exp;
        }
        if (error instanceof Throwable)
            throw NopException.adapt((Throwable) error);

        if (error instanceof ErrorCode) {
            throw throwErrorCodeParams(source, new NopEvalException((ErrorCode) error), params);
        } else if (error instanceof String) {
            throw new NopEvalException((String) error, null, false, false).loc(loc);
        }
        throw newError(ERR_EXEC_THROW_INVALID_ERROR, loc, display).param(ARG_ERROR, error);
    }

    private static NopException throwErrorCodeParams(io.nop.api.core.util.ISourceLocationGetter source,
                                                     NopException e, Object params) {
        if (source != null)
            e.source(source);
        if (params == null)
            return e;
        if (params instanceof Map) {
            return e.params((Map) params);
        }
        return e.param(io.nop.xlang.XLangErrors.ARG_ARGS, params);
    }

    /** ThrowExceptionExecutable 语义（提取；解释器改调）。display = 节点 display 串。 */
    public static void throwException(SourceLocation loc, String display, Object value) {
        if (value == null)
            throw newError(ERR_EXEC_THROW_NULL_EXCEPTION, loc, display);
        if (value instanceof NopException) {
            NopException e = (NopException) value;
            if (e.getErrorLocation() == null)
                e.loc(loc);
            throw e;
        } else if (value instanceof Throwable) {
            throw newError(ERR_EXEC_THROW_EXCEPTION, (Throwable) value, loc, display).forWrap();
        }
        throw newError(ERR_EXEC_THROW_EXCEPTION, loc, display).param(ARG_VALUE, value);
    }

    /** OutputXmlAttrExecutable 语义（提取；解释器改调）：属性文本片序列。 */
    public static void outputXmlAttr(SourceLocation loc, IEvalOutput out, String name, Object v) {
        if (v == null)
            return;
        out.text(null, " ");
        out.text(null, name);
        out.text(loc, "=\"");
        out.text(loc, StringHelper.escapeXmlAttr(v.toString()));
        out.text(null, "\"");
    }

    /** OutputXmlExtAttrsExecutable 语义（提取；解释器改调）。display = 节点 display 串。 */
    public static void outputXmlExtAttrs(SourceLocation loc, String display, IEvalOutput out,
                                         Set<String> excludeNames, Object v) {
        if (v == null)
            return;
        if (!(v instanceof Map))
            throw newError(ERR_EXEC_XML_EXT_ATTRS_NOT_MAP, loc, display);
        Map<String, Object> map = (Map<String, Object>) v;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object value = entry.getValue();
            if (value != null && !excludeNames.contains(entry.getKey())) {
                out.text(null, " ");
                out.text(null, entry.getKey());
                out.text(null, "=\"");
                out.text(loc, StringHelper.escapeXmlAttr(value.toString()));
                out.text(null, "\"");
            }
        }
    }

    /** EscapeOutputExecutable 语义（提取；解释器改调）。 */
    public static void escapeOutput(SourceLocation loc, IEvalOutput out, XLangEscapeMode escapeMode, Object value) {
        if (value == null)
            return;
        if (value instanceof RawText) {
            out.text(loc, ((RawText) value).getText());
            return;
        }
        switch (escapeMode) {
            case xml: {
                out.text(loc, StringHelper.escapeXml(value.toString()));
                break;
            }
            case xmlAttr: {
                out.text(loc, StringHelper.escapeXmlAttr(value.toString()));
                break;
            }
            case xmlValue: {
                out.text(loc, StringHelper.escapeXmlValue(value.toString()));
                break;
            }
            default:
                out.value(loc, value);
        }
    }

    /** CollectTextExecutable 语义（提取；解释器改调，scope/frame 透传给生成体回调）。 */
    public static String collectText(IEvalScope scope, ExitMode[] exit, IGeneratedOutBody body, Object[] frame) {
        StringBuilderEvalOutput out = new StringBuilderEvalOutput();
        body.run(scope, out, exit, frame);
        return out.getOutput();
    }

    /** CollectJsonExecutable 语义（提取；解释器改调，scope/frame 透传给生成体回调）。 */
    public static Object collectJson(IEvalScope scope, ExitMode[] exit, IGeneratedOutBody body, Object[] frame) {
        CollectJObjectHandler out = new CollectJObjectHandler();
        body.run(scope, out, exit, frame);
        return out.getResult();
    }

    /** CollectNodeExecutable 语义（提取；解释器改调，scope/frame 透传给生成体回调）。 */
    public static Object collectNode(IEvalScope scope, SourceLocation loc, boolean singleNode,
                                     ExitMode[] exit, IGeneratedOutBody body, Object[] frame) {
        CollectXNodeHandler out = new CollectXNodeHandler();
        out.beginNode(loc, CoreConstants.DUMMY_TAG_NAME, Collections.emptyMap());
        body.run(scope, out, exit, frame);
        out.endNode(CoreConstants.DUMMY_TAG_NAME);
        XNode node = out.endDoc();
        if (singleNode) {
            if (node.getChildCount() != 1)
                throw newError(ERR_EXEC_COLLECT_RESULT_NOT_SINGLE_NODE, loc, "@collect");
            return node.child(0);
        }
        return node;
    }

    /** CollectSqlExecutable 语义（单源复用 ExprEvalHelper.generateSql 的收集核心）。 */
    public static SQL collectSql(IEvalScope scope, ExitMode[] exit, IGeneratedOutBody body, Object[] frame) {
        return ExprEvalHelper.generateSql(ctx -> body.run(scope, ctx.getOut(), exit, frame), new EvalRuntime(scope));
    }

    /** GenXJsonExecutable 语义（单源复用 ExprEvalHelper.generateXjson 的收集核心）。 */
    public static Object genXjson(IEvalScope scope, ExitMode[] exit, IGeneratedOutBody body, Object[] frame) {
        return ExprEvalHelper.generateXjson(ctx -> body.run(scope, ctx.getOut(), exit, frame), new EvalRuntime(scope));
    }

    /** GenNodeExecutable 的分支语义（提取；解释器改调）：DisabledEvalOutput → 收集并返回 XNode，否则 cast handler 直发。 */
    public interface IGenNodeContent {
        void emit(IXNodeHandler handler, ExitMode[] exit, Object[] frame);
    }

    public static Object genNode(IEvalOutput output, ExitMode[] exit, IGenNodeContent content, Object[] frame) {
        if (output == DisabledEvalOutput.INSTANCE) {
            CollectXNodeHandler out = new CollectXNodeHandler();
            content.emit(out, exit, frame);
            return out.endDoc();
        }
        content.emit((IXNodeHandler) output, exit, frame);
        return null;
    }

    /** GenNodeExecutable 的节点发射语义（simpleNode/beginNode+body+endNode，提取；解释器改调）。 */
    public static void genNodeHandler(IXNodeHandler handler, SourceLocation loc, String tagName,
                                      Map<String, ValueWithLocation> attrs, Runnable body) {
        if (body == null) {
            handler.simpleNode(loc, tagName, attrs);
        } else {
            handler.beginNode(loc, tagName, attrs);
            body.run();
            handler.endNode(tagName);
        }
    }

    /** GenNodeExecutable 的 tagName 校验语义（提取；解释器改调）。display = 节点 display 串。 */
    public static String genNodeTagName(SourceLocation loc, String tagName, Object tagNameValue,
                                        Object tagNameExpr, String display) {
        if (tagName != null)
            return tagName;
        if (tagNameValue instanceof String) {
            String str = (String) tagNameValue;
            if (!StringHelper.isValidXmlName(str))
                throw new NopEvalException(ERR_XPL_DISALLOW_OUTPUT_INVALID_XML_NAME).loc(loc)
                        .param(ARG_EXPR, display)
                        .param(ARG_TAG_NAME, str).param(ARG_TAG_NAME_EXPR, tagNameExpr);
            return str;
        }
        throw new NopEvalException(ERR_XPL_DISALLOW_OUTPUT_INVALID_XML_NAME).loc(loc)
                .param(ARG_EXPR, display)
                .param(ARG_TAG_NAME, tagNameValue).param(ARG_TAG_NAME_EXPR, tagNameExpr);
    }

    /**
     * GenNodeExecutable 的属性合并语义（显式属性优先 + extAttrs 跳过重复/空值；提取；解释器改调）。
     * values 为已按求值顺序求值的显式属性值，extAttrsValue 为随后求值的扩展属性值。
     */
    public static Map<String, ValueWithLocation> genNodeAttrs(String[] names, SourceLocation[] valueLocs,
                                                              Object[] values, Object extAttrsValue,
                                                              SourceLocation extAttrsLoc, Set<String> attrNames) {
        Map<String, ValueWithLocation> map = new LinkedHashMap<>();
        for (int i = 0; i < names.length; i++) {
            if (values[i] == null)
                continue;
            map.put(names[i], ValueWithLocation.of(valueLocs[i], values[i]));
        }
        if (extAttrsValue != null) {
            if (!(extAttrsValue instanceof Map))
                throw new NopEvalException(ERR_EXEC_XML_EXT_ATTRS_NOT_MAP).loc(extAttrsLoc);
            Map<String, Object> extMap = (Map<String, Object>) extAttrsValue;
            for (Map.Entry<String, Object> entry : extMap.entrySet()) {
                Object extValue = entry.getValue();
                if (extValue == null || attrNames.contains(entry.getKey()))
                    continue;
                map.put(entry.getKey(), ValueWithLocation.of(extAttrsLoc, extValue));
            }
        }
        return map;
    }

    // ------------------------------------------------------------------
    // 覆盖 B（I4）：控制流族共享 helper（ForIn 前置校验 / ForOf 迭代 / CallFunc 族异常包装）
    // ------------------------------------------------------------------

    /** ForInExecutable 前置语义（提取；解释器改调）：items null → null（跳过）；非 Map 报错。 */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> asForInMap(SourceLocation loc, String display, Object items) {
        if (items == null)
            return null;
        if (!(items instanceof Map))
            throw newError(ERR_EXEC_FOR_IN_ITEMS_MUST_BE_MAP, loc, display);
        return (Map<String, Object>) items;
    }

    /** ForOfExecutable.toIterator 提取（NopException 时 addXplStack(itemsDisplay)）。 */
    public static Iterator<Object> forOfIterator(SourceLocation loc, String display,
                                                 String itemsDisplay, Object items) {
        try {
            return CollectionHelper.toIterator(items, false);
        } catch (NopException e) {
            e.addXplStack(itemsDisplay);
            throw e;
        }
    }

    /**
     * CallFuncExecutable/CallFuncWithClosureExecutable 的 catch 包装语义（提取；解释器改调）：
     * NopException → addXplStack(stackObj) 重抛；其他 Exception → ERR_EXEC_CALL_FUNC_FAIL forWrap 包装。
     */
    public static NopException wrapCallFuncException(Object stackObj, SourceLocation loc, String display, Exception e) {
        if (e instanceof NopException) {
            ((NopException) e).addXplStack(stackObj);
            return (NopException) e;
        }
        return (NopException) new NopEvalException(ERR_EXEC_CALL_FUNC_FAIL, e).loc(loc).param(ARG_EXPR, display)
                .forWrap();
    }
}

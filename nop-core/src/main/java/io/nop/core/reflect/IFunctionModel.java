/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.reflect;

import io.nop.api.core.annotations.lang.EvalMethod;
import io.nop.api.core.util.IFreezable;
import io.nop.api.core.util.ISourceLocationGetter;
import io.nop.api.core.util.IVariableScope;
import io.nop.commons.util.ArrayHelper;
import io.nop.core.lang.eval.DisabledEvalScope;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.type.IFunctionType;
import io.nop.core.type.IGenericType;
import io.nop.core.type.PredefinedGenericTypes;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/**
 * 用于反射调用的方法模型对象。系统中不会直接使用java reflection机制。
 */
public interface IFunctionModel
        extends IAnnotatedElement, IClassMember, IEvalFunction, ISourceLocationGetter, IFreezable {
    /**
     * 在EL表达式中调用时使用的方法名，当方法名为java中的关键字时，可以使用@ReflectionName注解来指定。
     */
    String getName();

    /**
     * java实现类中的方法名，有可能与name不同。
     */
    String getImplName();

    Class<?> getDeclaringClass();

    IFunctionType getFunctionType();

    IGenericType getReturnType();

    String getBizActionName();

    /**
     * 浅拷贝
     */
    IFunctionModel cloneInstance();

    default Class<?> getReturnClass() {
        return getReturnType().getRawClass();
    }

    boolean isDeterministic();

    boolean isReturnNullable();

    /**
     * 宏函数在编译期运行，参数为{@code List<? extends XLangASTNode>}, 返回XLangASTNode
     */
    boolean isMacro();

    boolean isDeprecated();

    boolean isEvalMethod();

    boolean isHelperMethod();

    default boolean isReturnVoid() {
        return getReturnType() == PredefinedGenericTypes.VOID_TYPE;
    }

    /**
     * 异步函数总是返回CompletionStage类型
     */
    default boolean isAsync() {
        return getReturnType().isAssignableTo(CompletionStage.class);
    }

    /**
     * 如果是async调用，则返回的是实际类型。如果是普通调用，则和returnType相同
     */
    IGenericType getAsyncReturnType();

    int getMinArgCount();

    int getMaxArgCount();

    default int getArgCount() {
        return getArgs().size();
    }

    /**
     * 最后一个参数是否是变长参数
     */
    boolean isVarArgs();

    List<? extends IFunctionArgument> getArgs();

    List<Class<?>> getExceptionTypes();

    default List<String> getArgNames() {
        return getArgs().stream().map(IFunctionArgument::getName).collect(Collectors.toList());
    }

    default String[] getArgNamesArray() {
        return getArgs().stream().map(IFunctionArgument::getName).toArray(String[]::new);
    }

    default boolean hasGenericTypeArg() {
        return getArgs().stream().anyMatch(IFunctionArgument::hasGenericType);
    }

    Class<?>[] getArgRawTypes();

    IEvalFunction getInvoker();

    default Object call0(Object thisObj, IEvalScope scope) {
        return getInvoker().call0(thisObj, scope);
    }

    default Object call1(Object thisObj, Object arg, IEvalScope scope) {
        if (isVarArgs()) {
            return invoke(thisObj, new Object[]{arg}, scope);
        } else {
            arg = getArgs().get(0).castArg(arg, scope);
            return getInvoker().call1(thisObj, arg, scope);
        }
    }

    default Object call2(Object thisObj, Object arg1, Object arg2, IEvalScope scope) {
        if (isVarArgs()) {
            return invoke(thisObj, new Object[]{arg1, arg2}, scope);
        } else {
            List<? extends IFunctionArgument> args = getArgs();
            arg1 = args.get(0).castArg(arg1, scope);
            arg2 = args.get(1).castArg(arg2, scope);
            return getInvoker().call2(thisObj, arg1, arg2, scope);
        }
    }

    default Object call3(Object thisObj, Object arg1, Object arg2, Object arg3, IEvalScope scope) {
        if (isVarArgs()) {
            return invoke(thisObj, new Object[]{arg1, arg2, arg3}, scope);
        } else {
            List<? extends IFunctionArgument> args = getArgs();
            arg1 = args.get(0).castArg(arg1, scope);
            arg2 = args.get(1).castArg(arg2, scope);
            arg3 = args.get(2).castArg(arg3, scope);
            return getInvoker().call3(thisObj, arg1, arg2, arg3, scope);
        }
    }

    default Object invoke(Object thisObj, Object[] args, IEvalScope scope) {
        args = castArgs(args, scope);
        return getInvoker().invoke(thisObj, args, scope);
    }

    /**
     * 将按名称传递参数转化为按位置传递参数
     *
     * @param namedArgs 按名称传递的参数集合
     */
    default Object[] buildArgValues(Map<String, Object> namedArgs) {
        List<? extends IFunctionArgument> args = getArgs();
        int n = args.size();
        Object[] ret = new Object[n];
        for (int i = 0; i < n; i++) {
            IFunctionArgument arg = args.get(i);
            Object value = namedArgs.get(arg.getName());
            value = arg.castArg(value, DisabledEvalScope.INSTANCE);
            ret[i] = value;
        }
        return ret;
    }

    default Object[] buildArgValuesFromScope(IVariableScope scope) {
        List<? extends IFunctionArgument> args = getArgs();
        int n = args.size();
        Object[] ret = new Object[n];
        for (int i = 0; i < n; i++) {
            IFunctionArgument arg = args.get(i);
            Object value = scope.getValue(arg.getName());
            value = arg.castArg(value, DisabledEvalScope.INSTANCE);
            ret[i] = value;
        }
        return ret;
    }

    Class<?> getVarArgElementClass();

    default Object[] castArgs(Object[] argValues, IEvalScope scope) {
        if (isVarArgs()) {
            argValues = ArrayHelper.setupVarArgs(argValues, getVarArgElementClass(), getArgCount());
        }
        List<? extends IFunctionArgument> args = getArgs();
        int n = args.size();
        for (int i = 0; i < n; i++) {
            Object value = argValues[i];
            value = args.get(i).castArg(value, scope);
            argValues[i] = value;
        }
        return argValues;
    }

    default boolean isAllowArgTypes(Class<?>[] argTypes) {
        List<? extends IFunctionArgument> args = this.getArgs();
        int n = args.size();
        if (n > argTypes.length)
            return false;
        if (isVarArgs()) {
            for (int i = 0; i < n - 1; i++) {
                if (!args.get(i).isAssignableFrom(argTypes[i]))
                    return false;
            }
            // 忽略变长参数的类型匹配
        } else {
            for (int i = 0; i < n; i++) {
                if (!args.get(i).isAssignableFrom(argTypes[i]))
                    return false;
            }
        }
        return true;
    }

    default boolean isAllowArgValues(Object[] argValues) {
        List<? extends IFunctionArgument> args = this.getArgs();
        int n = args.size();
        if (n > argValues.length)
            return false;
        if (isVarArgs()) {
            n--;
            // 忽略变长参数的类型匹配
        }
        for (int i = 0; i < n; i++) {
            Object value = argValues[i];
            if (value == null)
                continue;
            if (!args.get(i).isAssignableFrom(value.getClass()))
                return false;
        }
        return true;
    }

    default boolean isExactlyMatch(Class<?>[] argTypes) {
        List<? extends IFunctionArgument> args = this.getArgs();
        int n = args.size();
        if (n != argTypes.length)
            return false;

        for (int i = 0; i < n; i++) {
            if (args.get(i).getRawClass() != argTypes[i])
                return false;
        }

        return true;
    }

    @EvalMethod
    default Object invokeWithNamedArgs(IEvalScope scope, Map<String, Object> args) {
        Object[] array = buildNamedArgs(scope, args);
        return invoke(null, array, scope);
    }

    @EvalMethod
    default Object[] buildNamedArgs(IEvalScope scope, Map<String, Object> args) {
        Object[] array = new Object[getArgCount()];
        int index = 0;
        for (IFunctionArgument argModel : getArgs()) {
            String name = argModel.getName();
            Object value = argModel.castArg(args.get(name), scope);
            array[index] = value;
            index++;
        }
        return array;
    }
}
/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.java.gen;

import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.reflect.impl.EvalMethodInvoker;
import io.nop.core.reflect.impl.MethodInvoker;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * 生成类的绑定入口（EvalMethod 约定，janino {@code JaninoScriptCompiler} 先例同构）：
 * 按约定定位生成类入口方法（static + 首参 {@code IEvalScope $scope}），经
 * {@code MethodInvoker} + {@code EvalMethodInvoker} 包装为 {@link IEvalFunction}。
 */
public final class GeneratedEvalBinding {

    private GeneratedEvalBinding() {
    }

    /**
     * 定位符合 EvalMethod 约定的入口方法；不存在时快速失败（不静默返回 null）。
     */
    public static Method findEntryMethod(Class<?> generatedClass) {
        for (Method method : generatedClass.getDeclaredMethods()) {
            if (EvalMethodConvention.ENTRY_METHOD_NAME.equals(method.getName())
                    && Modifier.isStatic(method.getModifiers())
                    && method.getParameterCount() >= 1
                    && method.getParameterTypes()[0] == IEvalScope.class) {
                return method;
            }
        }
        throw new IllegalStateException("generated class does not declare EvalMethod entry: "
                + EvalMethodConvention.ENTRY_METHOD_NAME + "(IEvalScope "
                + EvalMethodConvention.SCOPE_PARAM + ", ...): " + generatedClass.getName());
    }

    /**
     * 将生成类入口方法包装为 {@link IEvalFunction}（纯表达式单元的调用通路）。
     */
    public static IEvalFunction bind(Class<?> generatedClass) {
        return new EvalMethodInvoker(new MethodInvoker(findEntryMethod(generatedClass)));
    }
}

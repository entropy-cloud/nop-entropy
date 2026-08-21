/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.java.backend;

import io.nop.api.core.context.ContextProvider;
import io.nop.core.lang.eval.EvalFrame;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.lang.eval.IEvalOutput;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.reflect.impl.EvalMethodInvoker;
import io.nop.core.reflect.impl.MethodInvoker;
import io.nop.xlang.backend.EvalBackendObservation;
import io.nop.xlang.backend.IEvalStaticBinding;
import io.nop.xlang.exec.ExecutableFunction;
import io.nop.xlang.java.gen.EvalMethodConvention;
import io.nop.xlang.java.gen.ExecutableTreeFingerprints;
import io.nop.xlang.java.gen.GeneratedClassManifest;
import io.nop.xlang.java.gen.GeneratedEvalBinding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * 生产 binder（I10）：生成类清单内存契约消费 + 生成类 classpath 常规加载（无自定义
 * ClassLoader）+ Executable 树指纹一致性校验（防 stale），经 I9 移交的
 * {@code JavaEvalExecutionBackend.setBinder} 接入缝接入。
 *
 * <p>降级契约（返回 null = 应有而缺失族，I9 §9(c) 保持）：null 前按 D5 分级自记观测——
 * <ul>
 * <li>清单条目缺失 / 清单内类缺失（NoClassDefFound/ClassNotFound）/ 入口约定违规 →
 * {@code generated-binding-missing}（stale 缺陷，每次 WARN）；</li>
 * <li>指纹失配 + 无租户上下文 → {@code generated-fingerprint-mismatch}（stale 缺陷，每次 WARN）；</li>
 * <li>指纹失配 + 租户上下文活跃 → {@code tenant-divergent-tree}（预期稳态，WARN 按 sourceKey
 * 去重、指标计数不衰减——`_gen/` 产物只从基树生成，租户 delta 合并树结构性无生成类）。</li>
 * </ul>
 * 分级判定不可得（租户上下文读取异常）保守归 stale。
 */
public class GeneratedClassBindingBinder implements IEvalStaticBindingBinder {

    private static final Logger LOG = LoggerFactory.getLogger(GeneratedClassBindingBinder.class);

    private final GeneratedClassManifest manifest;

    public GeneratedClassBindingBinder(GeneratedClassManifest manifest) {
        this.manifest = manifest == null ? GeneratedClassManifest.empty() : manifest;
    }

    public GeneratedClassManifest getManifest() {
        return manifest;
    }

    @Override
    public IEvalStaticBinding findStaticBinding(String resourcePath, IExecutableExpression tree) {
        GeneratedClassManifest.Entry entry = manifest.find(resourcePath);
        if (entry == null) {
            observeStale(resourcePath, tree, EvalBackendObservation.REASON_GENERATED_BINDING_MISSING,
                    "manifest entry missing");
            return null;
        }

        String fingerprint = ExecutableTreeFingerprints.fingerprint(tree);
        if (!fingerprint.equals(entry.getTreeFingerprint())) {
            if (isTenantContextActive()) {
                EvalBackendObservation.onSteadyStateDegradation(JavaEvalExecutionBackend.BACKEND_ID,
                        EvalBackendObservation.REASON_TENANT_DIVERGENT_TREE, resourcePath, tree.getLocation());
            } else {
                EvalBackendObservation.onDegradation(JavaEvalExecutionBackend.BACKEND_ID,
                        EvalBackendObservation.REASON_GENERATED_FINGERPRINT_MISMATCH, resourcePath,
                        tree.getLocation());
            }
            return null;
        }

        Class<?> generatedClass;
        try {
            generatedClass = Class.forName(entry.getClassName());
        } catch (ClassNotFoundException | LinkageError e) {
            LOG.warn("nop.xlang.execution.generated-class-load-failed: path={}, class={}, error={}",
                    resourcePath, entry.getClassName(), String.valueOf(e));
            observeStale(resourcePath, tree, EvalBackendObservation.REASON_GENERATED_BINDING_MISSING,
                    "generated class not on classpath: " + entry.getClassName());
            return null;
        }

        try {
            if (tree instanceof ExecutableFunction) {
                // xlib 每标签形态（I11，I10 D4）：树根 = 标签函数（键 path#tag），
                // 入口 = execute(IEvalScope, Object[], IEvalOutput)——实参组经标签函数帧槽位重建
                return new TagGeneratedBinding(findTagEntryMethod(generatedClass),
                        ((ExecutableFunction) tree).getArgCount());
            }
            Method entryMethod = GeneratedEvalBinding.findEntryMethod(generatedClass);
            if (isTagEntryMethod(entryMethod)) {
                // 动作路径收到标签形态入口 = 约定违规（键/形态错配），降级而非误调用
                throw new IllegalStateException("tag-form entry on action binding: " + entryMethod);
            }
            return new ReflectiveGeneratedBinding(entryMethod);
        } catch (RuntimeException e) {
            LOG.warn("nop.xlang.execution.generated-binding-convention-violated: path={}, class={}",
                    resourcePath, entry.getClassName(), e);
            observeStale(resourcePath, tree, EvalBackendObservation.REASON_GENERATED_BINDING_MISSING,
                    "generated class violates EvalMethod convention: " + entry.getClassName());
            return null;
        }
    }

    /** 标签形态入口：static execute(IEvalScope, Object[], IEvalOutput)（{@link EvalMethodConvention#ARGS_PARAM} 形态） */
    static boolean isTagEntryMethod(Method method) {
        return method.getParameterCount() == 3 && method.getParameterTypes()[1] == Object[].class
                && method.getParameterTypes()[2] == IEvalOutput.class;
    }

    static Method findTagEntryMethod(Class<?> generatedClass) {
        for (Method method : generatedClass.getDeclaredMethods()) {
            if (EvalMethodConvention.ENTRY_METHOD_NAME.equals(method.getName())
                    && java.lang.reflect.Modifier.isStatic(method.getModifiers())
                    && isTagEntryMethod(method)) {
                return method;
            }
        }
        throw new IllegalStateException("generated class does not declare tag-form EvalMethod entry: "
                + EvalMethodConvention.ENTRY_METHOD_NAME + "(IEvalScope, Object[] "
                + EvalMethodConvention.ARGS_PARAM + ", IEvalOutput " + EvalMethodConvention.OUT_PARAM
                + "): " + generatedClass.getName());
    }

    private void observeStale(String resourcePath, IExecutableExpression tree, String reason, String detail) {
        if (LOG.isDebugEnabled())
            LOG.debug("nop.xlang.execution.generated-binding-stale: path={}, reason={}, detail={}",
                    resourcePath, reason, detail);
        EvalBackendObservation.onDegradation(JavaEvalExecutionBackend.BACKEND_ID, reason, resourcePath,
                tree == null ? null : tree.getLocation());
    }

    private boolean isTenantContextActive() {
        try {
            String tenantId = ContextProvider.currentTenantId();
            return tenantId != null && !tenantId.isEmpty();
        } catch (RuntimeException e) {
            // 判定不可得 → 保守归 stale（缺陷优先暴露）
            return false;
        }
    }

    /**
     * 生成类反射绑定执行体：入口 static {@code execute(IEvalScope $scope[,
     * IEvalOutput $out])}（EvalMethod 约定）；身份证据 = 入口 {@link Method}（确定性派生生成类）。
     */
    static final class ReflectiveGeneratedBinding implements IEvalStaticBinding {

        private final Method entryMethod;

        private final IEvalFunction plainFunction;

        private final boolean usesOut;

        ReflectiveGeneratedBinding(Method entryMethod) {
            this.entryMethod = entryMethod;
            this.usesOut = entryMethod.getParameterCount() == 2;
            this.plainFunction = usesOut ? null
                    : new EvalMethodInvoker(new MethodInvoker(entryMethod));
        }

        @Override
        public Object execute(io.nop.core.lang.eval.EvalRuntime rt) {
            try {
                if (usesOut) {
                    // 模板单元：第二隐参 IEvalOutput $out 直接注入运行时输出缓冲
                    return entryMethod.invoke(null, rt.getScope(), rt.getOut());
                }
                IEvalScope scope = rt.getScope();
                return plainFunction.invoke(null, new Object[0], scope);
            } catch (Throwable t) {
                Throwable cause = t instanceof InvocationTargetException && t.getCause() != null
                        ? t.getCause() : t;
                if (cause instanceof RuntimeException)
                    throw (RuntimeException) cause;
                if (cause instanceof Error)
                    throw (Error) cause;
                throw new IllegalStateException("generated binding eval failed: " + entryMethod, cause);
            }
        }

        @Override
        public Object getBindingArtifact() {
            return entryMethod;
        }
    }

    /**
     * xlib 每标签生成类绑定执行体（I11）：入口 static
     * {@code execute(IEvalScope $scope, Object[] $args, IEvalOutput $out)}；实参组经标签函数帧
     * 槽位（0..argCount-1，含缺省实参已求值态）重建，输出缓冲注入运行时输出。身份证据 = 入口
     * {@link Method}（确定性派生标签生成类）。执行语义对应解释器
     * {@code LazyCompiledExecutableFunction.execute} 的帧构建 + body 执行（绑定替换 body 后
     * 帧构建发生在调用方，本执行体在既有帧上取实参）。
     */
    static final class TagGeneratedBinding implements IEvalStaticBinding {

        private final Method entryMethod;

        private final int argCount;

        TagGeneratedBinding(Method entryMethod, int argCount) {
            this.entryMethod = entryMethod;
            this.argCount = argCount;
        }

        @Override
        public Object execute(io.nop.core.lang.eval.EvalRuntime rt) {
            Object[] args = new Object[argCount];
            EvalFrame frame = rt.getCurrentFrame();
            for (int i = 0; i < argCount; i++) {
                args[i] = frame == null ? null : frame.getArg(i);
            }
            try {
                return entryMethod.invoke(null, rt.getScope(), args, rt.getOut());
            } catch (Throwable t) {
                Throwable cause = t instanceof InvocationTargetException && t.getCause() != null
                        ? t.getCause() : t;
                if (cause instanceof RuntimeException)
                    throw (RuntimeException) cause;
                if (cause instanceof Error)
                    throw (Error) cause;
                throw new IllegalStateException("generated tag binding eval failed: " + entryMethod, cause);
            }
        }

        @Override
        public Object getBindingArtifact() {
            return entryMethod;
        }
    }
}

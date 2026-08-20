/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.backend;

/**
 * 统一决策树的一次裁决结果（可复现、可记录）。
 */
public class EvalBackendDecision {

    /** 裁决落点：解释器 / 静态后端（java 槽位）/ 动态后端（truffle 槽位） */
    public enum RouteKind {INTERPRETER, STATIC, DYNAMIC}

    private final RouteKind kind;

    private final String backendId;

    private final String reason;

    /** 是否发生降级观测（force-interpreter / native 结构性排除 / 后端未注册为静默，不记观测） */
    private final boolean degraded;

    private final IEvalExecutionBackend backend;

    private final IEvalStaticBinding staticBinding;

    private String sourceKey;

    /** 执行后回填的身份证据（生成类绑定 artifact / 翻译 AST / 解释器=原树实例） */
    private Object artifact;

    private EvalBackendDecision(RouteKind kind, String backendId, String reason, boolean degraded,
                                IEvalExecutionBackend backend, IEvalStaticBinding staticBinding) {
        this.kind = kind;
        this.backendId = backendId;
        this.reason = reason;
        this.degraded = degraded;
        this.backend = backend;
        this.staticBinding = staticBinding;
    }

    public static EvalBackendDecision interpreter(String reason, boolean degraded) {
        return new EvalBackendDecision(RouteKind.INTERPRETER, "interpreter", reason, degraded, null, null);
    }

    public static EvalBackendDecision staticBackend(IEvalStaticBackend backend, IEvalStaticBinding binding) {
        return new EvalBackendDecision(RouteKind.STATIC, backend.getBackendId(), null, false, backend, binding);
    }

    public static EvalBackendDecision dynamicBackend(IEvalDynamicBackend backend) {
        return new EvalBackendDecision(RouteKind.DYNAMIC, backend.getBackendId(), null, false, backend, null);
    }

    public RouteKind getKind() {
        return kind;
    }

    public String getBackendId() {
        return backendId;
    }

    public String getReason() {
        return reason;
    }

    public boolean isDegraded() {
        return degraded;
    }

    public IEvalExecutionBackend getBackend() {
        return backend;
    }

    /** STATIC 裁决的生成类绑定（执行体） */
    public IEvalStaticBinding getStaticBinding() {
        return staticBinding;
    }

    public String getSourceKey() {
        return sourceKey;
    }

    public void setSourceKey(String sourceKey) {
        this.sourceKey = sourceKey;
    }

    public Object getArtifact() {
        return artifact;
    }

    public void setArtifact(Object artifact) {
        this.artifact = artifact;
    }
}

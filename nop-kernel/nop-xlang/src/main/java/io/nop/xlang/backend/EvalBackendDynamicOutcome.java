/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.backend;

/**
 * 动态路径求值结果：求值返回值 + 身份证据 artifact，或单元级翻译失败 fallback 标记。
 */
public final class EvalBackendDynamicOutcome {

    public static final String FALLBACK_UNIT_TRANSLATION_FAILURE = "unit-translation-failure";

    public static final String FALLBACK_BACKEND_UNAVAILABLE = "backend-unavailable";

    private final Object value;

    private final Object artifact;

    private final String fallbackReason;

    private final Object fallbackDetail;

    private EvalBackendDynamicOutcome(Object value, Object artifact, String fallbackReason, Object fallbackDetail) {
        this.value = value;
        this.artifact = artifact;
        this.fallbackReason = fallbackReason;
        this.fallbackDetail = fallbackDetail;
    }

    public static EvalBackendDynamicOutcome ofValue(Object value, Object artifact) {
        return new EvalBackendDynamicOutcome(value, artifact, null, null);
    }

    public static EvalBackendDynamicOutcome fallback(String reason, Object detail) {
        return new EvalBackendDynamicOutcome(null, null, reason, detail);
    }

    public boolean isFallback() {
        return fallbackReason != null;
    }

    /** 求值返回值（非 fallback 时有效） */
    public Object getValue() {
        return value;
    }

    /** 后端身份证据（如翻译 AST 根节点，非 fallback 时有效） */
    public Object getArtifact() {
        return artifact;
    }

    /** fallback 原因（null 表示非 fallback） */
    public String getFallbackReason() {
        return fallbackReason;
    }

    /** fallback 细节（如翻译失败事件），供观测记录 */
    public Object getFallbackDetail() {
        return fallbackDetail;
    }
}

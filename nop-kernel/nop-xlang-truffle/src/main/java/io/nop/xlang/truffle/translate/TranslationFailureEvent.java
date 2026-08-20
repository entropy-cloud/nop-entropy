package io.nop.xlang.truffle.translate;

import io.nop.api.core.util.SourceLocation;

import java.util.Objects;

/**
 * 单元级翻译失败观测事件（plan I8 Phase 1 §6 定稿）：支持集外节点 fail-fast 抛错路径的
 * 观测增量（fail-fast 语义不变，事件不参与控制流）。
 *
 * <p>载荷四元组 + 原因对象：sourceKey / 节点类名 / SourceLocation / 原因（+ 原始异常可选）。
 * nodeClassName 与 sourceLocation 从 fail-fast {@code NopEvalException} 的参数与定位提取
 * （translator {@code unsupported()} 已写入 {@code ARG_CLASS_NAME}/{@code loc(loc)}）。
 *
 * <p>消费方（依赖方向裁定，plan I8 Phase 1 §6）：I9 的 truffle 侧 SPI 适配器（落 truffle
 * 模块内，经池/Engine 运行时句柄取得语言实例、注册 {@link TranslationFailureListener}，
 * 向上经注册表契约暴露降级决策）——I9 决策树裁决入口在 nop-xlang，不可反向 import 本模块。
 */
public final class TranslationFailureEvent {

    private final String sourceKey;

    private final String nodeClassName;

    private final SourceLocation sourceLocation;

    private final String reason;

    private final Throwable cause;

    public TranslationFailureEvent(String sourceKey, String nodeClassName, SourceLocation sourceLocation,
                                   String reason, Throwable cause) {
        this.sourceKey = Objects.requireNonNull(sourceKey, "sourceKey");
        this.nodeClassName = nodeClassName;
        this.sourceLocation = sourceLocation;
        this.reason = reason;
        this.cause = cause;
    }

    public String getSourceKey() {
        return sourceKey;
    }

    /**
     * 触发 fail-fast 的节点类名（提取不可得时为 null）。
     */
    public String getNodeClassName() {
        return nodeClassName;
    }

    /**
     * 触发节点的源位置（提取不可得时为 null）。
     */
    public SourceLocation getSourceLocation() {
        return sourceLocation;
    }

    public String getReason() {
        return reason;
    }

    public Throwable getCause() {
        return cause;
    }

    @Override
    public String toString() {
        return "TranslationFailureEvent[" + sourceKey + ", node=" + nodeClassName
                + ", location=" + sourceLocation + ", reason=" + reason + "]";
    }
}

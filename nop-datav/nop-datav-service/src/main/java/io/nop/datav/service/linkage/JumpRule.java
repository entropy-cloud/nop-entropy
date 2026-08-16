package io.nop.datav.service.linkage;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * 跳转规则 POJO。从源 Panel 的 {@code panelConfig} JSON 的 {@code jump} 区域解析得到。
 *
 * <p>参见 {@code ai-dev/design/nop-datav/linkage-design.md} §8.3 跳转配置结构约定。</p>
 *
 * <p>每条跳转规则描述「源字段 → 目标 dashboard 或外部 URL + 参数映射」。URL 模板使用
 * {@code ${paramName}} 占位符；解析时将点击上下文中的字段值替换到模板占位符。</p>
 */
public final class JumpRule {

    public static final String TARGET_TYPE_DASHBOARD = "dashboard";
    public static final String TARGET_TYPE_EXTERNAL_URL = "external-url";

    private final String sourceField;
    private final String targetType;
    private final String targetId;
    private final Map<String, String> params;

    public JumpRule(String sourceField, String targetType, String targetId, Map<String, String> params) {
        this.sourceField = sourceField;
        this.targetType = targetType;
        this.targetId = targetId;
        this.params = params == null ? Collections.emptyMap() : Collections.unmodifiableMap(params);
    }

    /**
     * 源字段名（被点击的数据点对应的列名/维度名）。
     */
    public String getSourceField() {
        return sourceField;
    }

    /**
     * 目标类型：{@value #TARGET_TYPE_DASHBOARD} 或 {@value #TARGET_TYPE_EXTERNAL_URL}。
     */
    public String getTargetType() {
        return targetType;
    }

    /**
     * 目标标识：dashboard 类型为目标看板 ID；external-url 类型为 URL 模板。
     */
    public String getTargetId() {
        return targetId;
    }

    /**
     * 参数映射（key = 目标参数名，value = 源字段引用 {@code ${sourceField}} 或常量）。
     */
    public Map<String, String> getParams() {
        return params;
    }

    public boolean isDashboardType() {
        return TARGET_TYPE_DASHBOARD.equals(targetType);
    }

    public boolean isExternalUrlType() {
        return TARGET_TYPE_EXTERNAL_URL.equals(targetType);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof JumpRule)) return false;
        JumpRule that = (JumpRule) o;
        return Objects.equals(sourceField, that.sourceField)
                && Objects.equals(targetType, that.targetType)
                && Objects.equals(targetId, that.targetId)
                && Objects.equals(params, that.params);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceField, targetType, targetId, params);
    }

    @Override
    public String toString() {
        return "JumpRule{sourceField='" + sourceField + "', targetType='" + targetType
                + "', targetId='" + targetId + "', params=" + params + "}";
    }
}

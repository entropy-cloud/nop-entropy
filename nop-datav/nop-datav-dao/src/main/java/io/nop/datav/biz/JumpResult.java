package io.nop.datav.biz;

import java.util.Collections;
import java.util.Map;

/**
 * 跳转解析结果。{@code resolveJump} 的返回类型。
 *
 * <p>参见 {@code ai-dev/design/nop-datav/linkage-design.md} §8.5 API 契约。</p>
 *
 * <p>包含 {@code targetType}（dashboard/external-url）、{@code targetId}（dashboardId 或解析后的 URL，
 * 模板占位符已被替换）、{@code params}（注入的参数 Map，源字段引用已被解析为常量值）。</p>
 */
public class JumpResult {

    public static final String TARGET_TYPE_DASHBOARD = "dashboard";
    public static final String TARGET_TYPE_EXTERNAL_URL = "external-url";

    private final String targetType;
    private final String targetId;
    private final Map<String, Object> params;

    public JumpResult(String targetType, String targetId, Map<String, Object> params) {
        this.targetType = targetType;
        this.targetId = targetId;
        this.params = params == null ? Collections.emptyMap() : params;
    }

    /**
     * 目标类型：{@value #TARGET_TYPE_DASHBOARD} 或 {@value #TARGET_TYPE_EXTERNAL_URL}。
     */
    public String getTargetType() {
        return targetType;
    }

    /**
     * 目标标识：dashboard 类型为目标看板 ID；external-url 类型为解析后的 URL（模板占位符已替换）。
     */
    public String getTargetId() {
        return targetId;
    }

    /**
     * 注入的参数 Map（key = 目标参数名，value = 解析后的字段值，源字段引用 ${sourceField} 已被替换）。
     */
    public Map<String, Object> getParams() {
        return params;
    }
}

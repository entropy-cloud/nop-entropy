package io.nop.datav.biz;

import java.util.Collections;
import java.util.Map;

/**
 * 联动执行结果。{@code resolveLinkage} 的返回类型。
 *
 * <p>参见 {@code ai-dev/design/nop-datav/linkage-design.md} §8.5 API 契约。</p>
 *
 * <p>包含目标面板 ID + 应用的筛选参数 Map（参数值已注入到 paramMapping 的 source key 格式）。
 * 调用方可将 {@link #getParams()} 直接作为 {@code getPanelData} 的 {@code requestParams} 传入，
 * 经 paramMapping 求值后注入 SQL。</p>
 */
public class LinkageResult {

    private final String targetPanelId;
    private final Map<String, Object> params;

    public LinkageResult(String targetPanelId, Map<String, Object> params) {
        this.targetPanelId = targetPanelId;
        this.params = params == null ? Collections.emptyMap() : params;
    }

    /**
     * 目标面板 ID（联动触发的目标面板，与源面板属于同一 dashboardId）。
     */
    public String getTargetPanelId() {
        return targetPanelId;
    }

    /**
     * 应用的筛选参数 Map（key = paramMapping 的 source key，value = 点击上下文中的字段值）。
     * 格式与 {@code resolveFilterValues} 输出兼容，可直接作为 {@code getPanelData} 的 requestParams。
     */
    public Map<String, Object> getParams() {
        return params;
    }
}

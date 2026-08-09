package io.nop.datav.biz;

import java.util.Collections;
import java.util.Map;

/**
 * 看板筛选/联动状态。{@code getFilterState} 的返回类型，{@code saveFilterState} 的入参载体。
 *
 * <p>参见 {@code ai-dev/design/nop-datav/linkage-design.md} §9.3 filter_state 内容契约。</p>
 *
 * <p>包含三个区域：globalFilters（看板参数名 → 值，扁平 key）、panelSelections（源面板 ID →
 * 联动选择对象 {field, value}）、urlState（URL 序列化形式）。序列化/反序列化保持契约结构一致。</p>
 */
public class FilterState {

    private final Map<String, Object> globalFilters;
    private final Map<String, PanelSelection> panelSelections;
    private final String urlState;

    public FilterState(Map<String, Object> globalFilters,
                       Map<String, PanelSelection> panelSelections,
                       String urlState) {
        this.globalFilters = globalFilters == null ? Collections.emptyMap() : globalFilters;
        this.panelSelections = panelSelections == null ? Collections.emptyMap() : panelSelections;
        this.urlState = urlState;
    }

    /**
     * 全局筛选值（key = 看板参数名，含扁平化复合 key 如 {@code dateRange.start}；value = 参数值）。
     * 与 D2-1 {@code resolveFilterValues} 输出格式一致。
     */
    public Map<String, Object> getGlobalFilters() {
        return globalFilters;
    }

    /**
     * 面板联动选择（key = 源面板 ID；value = 该面板当前的联动选择状态）。
     */
    public Map<String, PanelSelection> getPanelSelections() {
        return panelSelections;
    }

    /**
     * URL 序列化形式（D2-1 URL 同步输出），用于快速分享/恢复。
     */
    public String getUrlState() {
        return urlState;
    }

    /**
     * 面板联动选择状态：被点击的字段名 + 值（支持单个字段+值）。
     */
    public static final class PanelSelection {
        private final String field;
        private final Object value;

        public PanelSelection(String field, Object value) {
            this.field = field;
            this.value = value;
        }

        public String getField() {
            return field;
        }

        public Object getValue() {
            return value;
        }
    }
}

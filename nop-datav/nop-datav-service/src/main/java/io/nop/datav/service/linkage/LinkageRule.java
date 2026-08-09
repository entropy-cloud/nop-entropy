package io.nop.datav.service.linkage;

import java.util.Objects;

/**
 * 联动规则 POJO。从源 Panel 的 {@code panelConfig} JSON 的 {@code linkage} 区域解析得到。
 *
 * <p>参见 {@code ai-dev/design/nop-datav/linkage-design.md} §8.2 联动配置结构约定。</p>
 *
 * <p>每条联动规则描述「源字段 → 目标面板 + 目标参数」的映射：用户点击源面板的某个数据点时，
 * 将值注入到 {@code targetPanelId} 的 {@code targetParam} 筛选参数。</p>
 */
public final class LinkageRule {

    private final String sourceField;
    private final String targetPanelId;
    private final String targetParam;

    public LinkageRule(String sourceField, String targetPanelId, String targetParam) {
        this.sourceField = sourceField;
        this.targetPanelId = targetPanelId;
        this.targetParam = targetParam;
    }

    /**
     * 源字段名（被点击的数据点对应的列名/维度名）。
     */
    public String getSourceField() {
        return sourceField;
    }

    /**
     * 目标面板 ID（与源面板属于同一 dashboardId）。
     */
    public String getTargetPanelId() {
        return targetPanelId;
    }

    /**
     * 目标参数名（注入到目标面板的筛选参数名，对应 paramMapping 的 source key）。
     */
    public String getTargetParam() {
        return targetParam;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LinkageRule)) return false;
        LinkageRule that = (LinkageRule) o;
        return Objects.equals(sourceField, that.sourceField)
                && Objects.equals(targetPanelId, that.targetPanelId)
                && Objects.equals(targetParam, that.targetParam);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceField, targetPanelId, targetParam);
    }

    @Override
    public String toString() {
        return "LinkageRule{sourceField='" + sourceField + "', targetPanelId='" + targetPanelId
                + "', targetParam='" + targetParam + "'}";
    }
}

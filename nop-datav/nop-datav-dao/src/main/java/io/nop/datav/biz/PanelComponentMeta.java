package io.nop.datav.biz;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 面板组件元信息。每种组件类型通过实现 {@code IPanelComponent.getMetadata()} 暴露其元信息。
 *
 * <p>不可变值对象。</p>
 *
 * <p>D4-2 扩展：可携带命名配置区域描述符（{@link PanelComponentConfigArea}）。
 * 既有构造（无描述符）按空列表处理，保持向后兼容。</p>
 *
 * <p>位于 {@code nop-datav-dao} biz 包以保持依赖方向正确（接口 {@code INopDatavScreenBiz}
 * 的返回类型需在 dao 层；与 {@code ScreenLayoutConfig}/{@code PanelDataResult} 同模式）。</p>
 */
public final class PanelComponentMeta {
    private final String type;
    private final String displayName;
    private final boolean needsDataset;
    private final List<PanelComponentConfigArea> configAreas;

    public PanelComponentMeta(String type, String displayName, boolean needsDataset) {
        this(type, displayName, needsDataset, Collections.emptyList());
    }

    public PanelComponentMeta(String type, String displayName, boolean needsDataset,
                              List<PanelComponentConfigArea> configAreas) {
        if (type == null || type.isEmpty()) {
            throw new IllegalArgumentException("type must not be null or empty");
        }
        if (displayName == null || displayName.isEmpty()) {
            throw new IllegalArgumentException("displayName must not be null or empty");
        }
        this.type = type;
        this.displayName = displayName;
        this.needsDataset = needsDataset;
        // Defensive copy + unmodifiable view (null → empty list for robustness)
        this.configAreas = configAreas == null || configAreas.isEmpty()
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(configAreas));
    }

    public String getType() {
        return type;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isNeedsDataset() {
        return needsDataset;
    }

    /**
     * 返回此组件声明的命名配置区域描述符列表（不可变）。
     * D1-1 既有组件返回空列表（未声明描述符）；D4-2 装饰/媒体组件返回非空列表。
     */
    public List<PanelComponentConfigArea> getConfigAreas() {
        return configAreas;
    }
}

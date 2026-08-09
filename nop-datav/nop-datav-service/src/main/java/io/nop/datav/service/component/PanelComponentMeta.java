package io.nop.datav.service.component;

/**
 * 面板组件元信息。每种组件类型通过实现 {@link IPanelComponent#getMetadata()} 暴露其元信息。
 *
 * <p>不可变值对象。</p>
 */
public final class PanelComponentMeta {
    private final String type;
    private final String displayName;
    private final boolean needsDataset;

    public PanelComponentMeta(String type, String displayName, boolean needsDataset) {
        if (type == null || type.isEmpty()) {
            throw new IllegalArgumentException("type must not be null or empty");
        }
        if (displayName == null || displayName.isEmpty()) {
            throw new IllegalArgumentException("displayName must not be null or empty");
        }
        this.type = type;
        this.displayName = displayName;
        this.needsDataset = needsDataset;
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
}

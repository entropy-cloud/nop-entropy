package io.nop.datav.service.component;

/**
 * 简单内置组件实现。基于元信息构造，无额外行为（D1-1 阶段组件接口仅暴露元信息）。
 */
public class SimplePanelComponent implements IPanelComponent {

    private final PanelComponentMeta meta;

    public SimplePanelComponent(String type, String displayName, boolean needsDataset) {
        this.meta = new PanelComponentMeta(type, displayName, needsDataset);
    }

    @Override
    public PanelComponentMeta getMetadata() {
        return meta;
    }
}

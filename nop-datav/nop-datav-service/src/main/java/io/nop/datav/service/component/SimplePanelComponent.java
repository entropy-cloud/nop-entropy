package io.nop.datav.service.component;

import io.nop.datav.biz.PanelComponentConfigArea;
import io.nop.datav.biz.PanelComponentMeta;

import java.util.List;

/**
 * 简单内置组件实现。基于元信息构造，无额外行为（D1-1 阶段组件接口仅暴露元信息）。
 *
 * <p>D4-2 扩展构造函数支持携带配置区域描述符。</p>
 */
public class SimplePanelComponent implements IPanelComponent {

    private final PanelComponentMeta meta;

    public SimplePanelComponent(String type, String displayName, boolean needsDataset) {
        this.meta = new PanelComponentMeta(type, displayName, needsDataset);
    }

    public SimplePanelComponent(String type, String displayName, boolean needsDataset,
                                List<PanelComponentConfigArea> configAreas) {
        this.meta = new PanelComponentMeta(type, displayName, needsDataset, configAreas);
    }

    @Override
    public PanelComponentMeta getMetadata() {
        return meta;
    }
}

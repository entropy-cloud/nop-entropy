package io.nop.datav.service.component;

/**
 * 面板组件接口。每种组件类型实现本接口，向 {@link PanelComponentRegistry} 暴露其元信息。
 *
 * <p>本接口仅定义组件元信息查询（D1-1）。具体渲染逻辑走 nop-chaos-flux renderers，不在本接口范围内。</p>
 */
public interface IPanelComponent {

    /**
     * 返回此组件的元信息（类型标识、显示名、是否需要数据集绑定）。
     */
    PanelComponentMeta getMetadata();
}

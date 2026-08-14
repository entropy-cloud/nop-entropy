package io.nop.plugin.api;

/**
 * 插件实例级状态（每个实例 = 一个 fiber，见设计文档 01-architecture-baseline.md §三）。
 *
 * <p>一个 LOADED 的插件定义可派生 N 个独立激活实例（多租户/多 agent），每个实例持有独立的
 * instanceKey、scope、effect 与配置域，各自处于 {@link #ACTIVATED} 或 {@link #DEACTIVATED} 状态。
 */
public enum InstanceState {

    /**
     * 激活中：实例对象持有内部子容器与已注册的 effect（可经 {@link IPluginInstance#getScope()} 获取）。
     */
    ACTIVATED,

    /**
     * 已去激活：effect 已回退、内部子容器已 stop，但实例对象保留（配置域仍可读，可重新激活）。
     */
    DEACTIVATED
}

package io.nop.plugin.api;

/**
 * 插件定义级状态（load/unload，见设计文档 01-architecture-baseline.md §三）。
 *
 * <p>定义级只描述静态定义是否存在：{@link #UNLOADED} 表示定义不存在（未加载或已卸载），
 * {@link #LOADED} 表示定义已加载（可缓存、可派生实例），与实例级状态（{@link InstanceState}）正交。
 */
public enum PluginState {

    /**
     * 定义未加载（初始态 / unload 后）。
     */
    UNLOADED,

    /**
     * 定义已加载（静态定义可缓存，可派生 0..N 个激活实例）。
     */
    LOADED
}

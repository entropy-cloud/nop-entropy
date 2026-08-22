package io.nop.plugin.api;

/**
 * 插件单层六态状态机（见设计文档 01-architecture-baseline.md §三）：定义（load/unload）与激活
 * （activate/deactivate）在同一对象上演进，一个定义至多一个激活。
 *
 * <p>各态对静态定义/内部子容器/bean 实例/effect 的占有关系：
 * <ul>
 *     <li>{@link #UNLOADED}：均无（初始态 / unload 后）。</li>
 *     <li>{@link #LOADED}：静态定义 ✓（可缓存，loader 被动失效）；无子容器 / bean / effect。</li>
 *     <li>{@link #ACTIVATING}：静态定义 ✓；子容器 build 中；bean 部分创建；effect 注册中。</li>
 *     <li>{@link #ACTIVATED}：静态定义 ✓；子容器 ✓（started）；bean ✓；effect ✓（已注册）。</li>
 *     <li>{@link #DEACTIVATING}：静态定义 ✓；子容器 stop 中；bean destroy 中；effect 回退中。</li>
 *     <li>{@link #FAILED}：静态定义 ✓；子容器/bean/effect 已清理（可重试激活）。</li>
 * </ul>
 */
public enum PluginState {

    /**
     * 定义未加载（初始态 / unload 后）。
     */
    UNLOADED,

    /**
     * 定义已加载（静态定义可缓存；不建子容器，无 bean 实例与 effect）。
     */
    LOADED,

    /**
     * 激活中：门控满足后建子容器、执行 activator、注册 effect 的中间态（可观测，非瞬时黑盒）。
     */
    ACTIVATING,

    /**
     * 已激活：子容器 started、bean 就绪、effect 已注册；getService/invokeCommand 可用。
     */
    ACTIVATED,

    /**
     * 去激活中：scope.close() LIFO 回退全部 effect、子容器 stop 的中间态
     * （quiescence 断言可精确表达"回退中 ≠ 已回退"）。
     */
    DEACTIVATING,

    /**
     * 激活失败（或 ACTIVATED 态运行时非预期错误）：错误清理完成后停留此态（定义保留，可重试激活）。
     */
    FAILED
}

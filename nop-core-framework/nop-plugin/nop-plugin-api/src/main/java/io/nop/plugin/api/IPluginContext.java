package io.nop.plugin.api;

import java.util.Collection;

/**
 * 全局插件上下文——职责收敛为两件事：plugin registry + coeffect reconcile
 * （见设计文档 01-architecture-baseline.md §7.6）。
 *
 * <p>不承担共享数据、不暴露宿主容器。registry 供全局查询与 quiescence 断言
 * （逐个 plugin 的 scope effects 清空）；reconcile 在 context 变化时评估全部 LOADED plugin 的
 * coeffect spec（插件级 requires/if-property），批量 activating / deactivating。
 */
public interface IPluginContext {

    /**
     * 按 pluginId 取插件；不存在返回 null。
     */
    IPlugin getPlugin(String pluginId);

    /**
     * 全部已加载 plugin（供全局 quiescence 断言：逐个激活态 scope 的 effects() 清空）。
     */
    Collection<IPlugin> allPlugins();

    /**
     * 评估全部 LOADED plugin 的 coeffect spec（插件级 requires + if-property），批量
     * activating / deactivating。环处理 = 静态依赖图环检测（环成员强制门控关闭并报告 unresolved）
     * + 防御性迭代上限（级联收敛终止保护）。
     *
     * <p>本方法只做 registry + coeffect reconcile；配置变更的自动订阅由实现层保证，API 层不做配置监听。
     */
    void reconcile();
}

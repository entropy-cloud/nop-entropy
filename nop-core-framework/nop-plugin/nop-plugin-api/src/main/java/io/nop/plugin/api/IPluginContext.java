package io.nop.plugin.api;

import java.util.Collection;

/**
 * 全局插件上下文——职责收敛为两件事：实例 registry + coeffect reconcile
 * （见设计文档 01-architecture-baseline.md §7.6）。
 *
 * <p>不承担共享数据、不暴露宿主容器。实例 registry 供全局查询与 quiescence 断言
 * （逐个实例 scope.effects() 清空）；reconcile 在 context 变化时评估全部 plugin 的 coeffect spec
 * （定义级 + 实例级），批量 activating / deactivating / neutral。
 */
public interface IPluginContext {

    /**
     * 按 pluginId + instanceKey 取实例；不存在返回 null。
     */
    IPluginInstance getInstance(String pluginId, String instanceKey);

    /**
     * 全部实例（供全局 quiescence 断言：逐个 {@link IPluginScope#effects()} 清空）。
     */
    Collection<IPluginInstance> allInstances();

/**
 * 评估全部 plugin 的 coeffect spec（定义级：能否派生实例；实例级：该实例是否激活，基于实例配置域），
 * 批量 activating / deactivating。环处理 = 静态依赖图环检测（环成员强制门控关闭并报告 unresolved）
 * + 防御性迭代上限（级联收敛终止保护）。
 *
 * <p>本方法只做 registry + coeffect reconcile；配置变更的自动订阅由实现层保证，API 层不做配置监听。
 */
void reconcile();
}

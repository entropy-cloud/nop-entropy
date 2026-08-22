package io.nop.plugin.manager;

import io.nop.api.core.beans.ArtifactCoordinates;
import io.nop.plugin.api.IPlugin;

import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * 每一个plugin对应于一个uber jar，通过远程仓库下载，并使用独立的ClassLoader加载
 *
 * <p>双轨来源（设计文档 01-architecture-baseline.md §二）：id 为统一 String 标识——
 * 判别规则 = {@link ArtifactCoordinates#parse(String)} 成功（Maven 坐标，双冒号格式）
 * → uber jar 轨（resolver → PluginClassLoader → plugin.json）；解析失败 → VFS 路径轨
 * （本地 VFS 的 *.plugin.xml，经 plugin.xdef 解析为静态定义）。
 * <p>状态机感知（{@link IPlugin#isStateMachineAware()}）路由：aware → 单层六态状态机
 * （loadPlugin 只到 LOADED；reconcile 按门控自动激活）；非 aware → 兼容路径
 * （loadPlugin 执行旧 start 语义）。
 */
public interface IPluginManager {

    /**
     * 统一入口：按 id 类型路由到双轨之一，加载插件定义到 LOADED（aware）或激活（非 aware 兼容路径）。
     *
     * <p>重复调用幂等：返回同一定义对象（按 id 缓存）。
     */
    IPlugin loadPlugin(String pluginId);

    /**
     * 卸载插件定义：aware → unload()（须未激活，激活态抛异常）；非 aware → stop()（旧语义）。
     */
    void unloadPlugin(String pluginId);

    /**
     * 按 Maven 坐标加载（兼容旧调用方）：委托到 {@link #loadPlugin(String)}。
     */
    default IPlugin loadPlugin(ArtifactCoordinates pluginId) {
        return loadPlugin(pluginId.toString());
    }

    /**
     * 按 Maven 坐标卸载（兼容旧调用方）：委托到 {@link #unloadPlugin(String)}。
     */
    default void unloadPlugin(ArtifactCoordinates pluginId) {
        unloadPlugin(pluginId.toString());
    }

    /**
     * 激活插件（§7.3）：委托 {@link IPlugin#activate()}——门控满足建子容器 + activator + effect
     * 返回 true；门控未满足 no-op 返回 false；已 ACTIVATED 幂等返回 true；激活失败显式抛异常
     * （置 FAILED、错误可经定义读取）。
     */
    boolean activatePlugin(String pluginId);

    /**
     * 去激活插件（§7.3）：委托 {@link IPlugin#deactivate()}——先 scope.close() LIFO 回退全部
     * effect 再子容器 stop，回到 LOADED（定义保留）。
     */
    CompletionStage<Void> deactivatePlugin(String pluginId);

    /**
     * 按 id 查询已加载插件；未加载返回 null。
     */
    IPlugin getPlugin(String pluginId);

    List<IPlugin> getLoadedPlugins();

    /**
     * 扫描全部 LOADED plugin 的插件级 coeffect（requires + if-property），批量
     * activating / deactivating（§7.3，委托 {@link io.nop.plugin.api.IPluginContext#reconcile()}）。
     *
     * <p>迭代收敛 + 静态环检测报告 + 激活失败阈值暂停；reconcile 不自动 load——
     * 未加载的定义保持 UNLOADED（load 是显式调用）。
     */
    void reconcilePlugins();

    /**
     * HMR 热重载（§六）：VFS 轨定义变更 → 若激活态先 deactivate → unload → load
     * （重解析 plugin.xml，重放定义级 updateConfig 累积值）→ reconcile（重新门控激活）。
     *
     * <p>jar 轨（uber jar 不可编辑，设计 §六 HMR 面向本地/开发场景）显式抛
     * {@code ERR_PLUGIN_RELOAD_NOT_SUPPORTED}。
     */
    void reloadPlugin(String pluginId);
}

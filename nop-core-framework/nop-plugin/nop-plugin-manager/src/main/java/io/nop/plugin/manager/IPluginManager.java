package io.nop.plugin.manager;

import io.nop.api.core.beans.ArtifactCoordinates;
import io.nop.plugin.api.IPlugin;
import io.nop.plugin.api.IPluginInstance;

import java.util.List;
import java.util.Map;

/**
 * 每一个plugin对应于一个uber jar，通过远程仓库下载，并使用独立的ClassLoader加载
 *
 * <p>双轨来源（设计文档 01-architecture-baseline.md §二）：id 为统一 String 标识——
 * 判别规则 = {@link ArtifactCoordinates#parse(String)} 成功（Maven 坐标，双冒号格式）
 * → uber jar 轨（resolver → PluginClassLoader → plugin.json）；解析失败 → VFS 路径轨
 * （本地 VFS 的 *.plugin.xml，经 plugin.xdef 解析为静态定义）。
 * <p>状态机感知（{@link IPlugin#isStateMachineAware()}）路由：aware → 定义级状态机
 * （loadPlugin 只到 LOADED，不激活）；非 aware → 兼容路径（loadPlugin 执行旧 start 语义）。
 */
public interface IPluginManager {

    /**
     * 统一入口：按 id 类型路由到双轨之一，加载插件定义到 LOADED（aware）或激活（非 aware 兼容路径）。
     *
     * <p>重复调用幂等：返回同一定义对象（按 id 缓存）。
     */
    IPlugin loadPlugin(String pluginId);

    /**
     * 卸载插件定义：aware → unload()（须先 destroy 全部实例，有实例抛异常）；非 aware → stop()（旧语义）。
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
     * 为已 LOADED 的定义派生一个激活实例（§7.3）：实例 registry 注册 → 立即激活
     * （子容器 + activator + effect）。同 key 重复创建抛明确异常；激活失败抛明确异常
     * （错误带实例 key 参数，实例回退 DEACTIVATED 留在 registry）。
     *
     * <p><b>W5 定义级 coeffect 门控</b>：定义级 spec（requires + if-property）不满足时
     * <b>no-op 返回 null</b>（设计 §五）——重复 key 检查先于门控，门控关闭但实例已存在
     * 仍抛重复 key 异常。创建成功后 manager 自动 reconcile（级联激活下游依赖实例）。
     *
     * <p>parent 为层级实例化（subagent）预留（W6 落地，本阶段传 null）。
     * uber jar 轨定义的实例化路径为 successor 项（W4/W7 评估），调用时抛明确异常。
     */
    IPluginInstance createInstance(String pluginId, String instanceKey, Map<String, Object> config,
                                   IPluginInstance parent);

    /**
     * 销毁指定实例（回退 effect、从定义移除）；实例不存在抛明确异常。
     */
    void destroyInstance(String pluginId, String instanceKey);

    /**
     * 按 instanceKey 查询已加载定义的实例；定义未加载或实例不存在返回 null。
     */
    IPluginInstance getInstance(String pluginId, String instanceKey);

    /**
     * 已加载定义的全部实例。
     */
    List<IPluginInstance> getInstances(String pluginId);

    List<IPlugin> getLoadedPlugins();

    /**
     * 扫描全部实例的 coeffect（定义级 + 实例级），批量 activating / deactivating（§7.3）。
     *
     * <p>委托 {@link io.nop.plugin.api.IPluginContext#reconcile()}：迭代收敛 + 静态环检测报告
     * + 激活失败阈值暂停；reconcile 不自动创建实例——定义级满足但无实例的定义保持 LOADED，
     * 实例由 {@link #createInstance}（显式调用）创建。
     */
    void reconcileInstances();
}

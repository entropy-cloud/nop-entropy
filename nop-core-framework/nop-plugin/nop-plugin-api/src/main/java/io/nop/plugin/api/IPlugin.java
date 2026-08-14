package io.nop.plugin.api;

import java.sql.Timestamp;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * 最小化的插件接口，不要求插件使用Nop平台实现，只需要引入nop-plugin-api实现这个接口即可。
 * 另外利用ServiceLoader机制注册插件实现类
 *
 * <p>新状态机语义（见设计文档 01-architecture-baseline.md §7.1）：插件定义有定义级状态
 * （{@link PluginState}：UNLOADED → LOADED）与实例级状态（{@link InstanceState}）。
 * 新增方法均为 default——默认实现收敛到"非状态机感知"（{@link #isStateMachineAware()} = false）
 * 的兼容行为，不破坏现有第三方插件实现。
 */
public interface IPlugin {
    String getPluginGroupId();

    String getPluginArtifactId();

    String getPluginVersion();

    Timestamp getLastChangeTime();

    Timestamp getLoadTime();

    /**
     * 相当于执行本地RPC调用
     *
     * <p>新状态机下的兼容语义：仅当实例数=1 时经该实例路由；多实例时抛明确异常
     * （要求经 {@link IPluginInstance#invokeCommandAsync} 显式指定实例，避免静默路由错误）；
     * 无 ACTIVATED 实例抛 INACTIVE 错误。
     */
    CompletionStage<Map<String, Object>> invokeCommandAsync(String command, Map<String, Object> args,
                                                            String fieldSelection,
                                                            IPluginCancelToken cancelToken);

    /**
     * 相当于执行本地RPC调用（同步版本）
     *
     * <p>兼容语义同 {@link #invokeCommandAsync}：仅当实例数=1 时经该实例路由；
     * 多实例抛明确异常；无 ACTIVATED 实例抛 INACTIVE 错误。
     */
    Map<String, Object> invokeCommand(String command, Map<String, Object> args,
                                      String fieldSelection,
                                      IPluginCancelToken cancelToken);

    /**
     * 启动插件（旧接口，兼容保留）
     *
     * <p>新状态机下等价于 {@code load(config) + createInstance(默认key)}；非状态机感知插件
     * （{@link #isStateMachineAware()} = false）走兼容路径：{@code loadPlugin} 执行旧 start 语义
     * （= load + activate，无"已加载未激活"态）。
     */
    void start(String pluginGroupId, String pluginArtifactId, String pluginVersion,
               Map<String, Object> config);

    /**
     * 更新插件配置
     *
     * <p>新状态机下的态语义：DEACTIVATED 时缓存待下次激活应用；ACTIVATED 时热应用。
     * 非状态机感知插件的既有实现行为不在本接口范围内（由兼容路径与实例配置域接管）。
     */
    default void updateConfig(Map<String, Object> config) {

    }

    /**
     * 停止插件（旧接口，兼容保留）
     *
     * <p>新状态机下等价于 {@code destroyInstance + unload()}；非状态机感知插件走兼容路径：
     * {@code unloadPlugin} 执行旧 stop 语义。
     */
    void stop();

    /**
     * 本插件是否感知（参与）新状态机（load/activate 分离 + 实例概念）。
     *
     * <p>默认返回 false——存量第三方插件（只有 start/stop）不进入新状态机；
     * 实现层据此路由：aware → 新状态机（load/activate 分离）；非 aware → 兼容路径
     * （loadPlugin 执行旧 start 语义、unloadPlugin 执行旧 stop 语义）。
     */
    default boolean isStateMachineAware() {
        return false;
    }

    /**
     * 返回插件定义级状态（{@link PluginState}）。
     *
     * <p>默认返回 {@link PluginState#LOADED}（保守值）——插件对象仅经 loadPlugin 成功后可见，
     * unload 后从 manager 移除；非 aware 插件不存在"可见的 UNLOADED"窗口。
     * 非 aware 插件不进入新状态机。
     */
    default PluginState getState() {
        return PluginState.LOADED;
    }

    /**
     * 按 instanceKey 查询该定义的实例；不存在返回 null。
     *
     * <p>默认返回 null——非 aware 插件无实例概念，不进入新状态机。
     */
    default IPluginInstance getInstance(String instanceKey) {
        return null;
    }

    /**
     * 该定义的全部实例。
     *
     * <p>默认返回空列表——非 aware 插件无实例概念，不进入新状态机。
     */
    default List<IPluginInstance> getInstances() {
        return Collections.emptyList();
    }

    /**
     * 加载定义到 LOADED（不实例化）。
     *
     * <p>默认抛 {@link UnsupportedOperationException}——非 aware 插件不进入新状态机，
     * 实现层对非 aware 插件永不调用本方法；显式失败避免静默跳过（No Silent No-Op）。
     */
    default void load(Map<String, Object> config) {
        throw new UnsupportedOperationException("load is not supported for non-state-machine-aware plugin");
    }

    /**
     * 丢弃定义（须先 destroy 全部实例）。
     *
     * <p>默认抛 {@link UnsupportedOperationException}——非 aware 插件不进入新状态机，
     * 实现层对非 aware 插件永不调用本方法；显式失败避免静默跳过（No Silent No-Op）。
     */
    default void unload() {
        throw new UnsupportedOperationException("unload is not supported for non-state-machine-aware plugin");
    }
}

package io.nop.plugin.api;

import java.sql.Timestamp;
import java.util.Collection;
import java.util.Map;
import io.nop.api.core.exceptions.NopException;

import java.util.concurrent.CompletionStage;

/**
 * 最小化的插件接口，不要求插件使用Nop平台实现，只需要引入nop-plugin-api实现这个接口即可。
 * 插件实现类的发现机制为 uber jar 内 plugin.json 指定实现类 + 反射实例化
 * （{@code PluginClassLoader}，非 ServiceLoader）。
 *
 * <p>单层六态状态机语义（见设计文档 01-architecture-baseline.md §7.1）：插件定义
 * （{@link PluginState}：UNLOADED → LOADED ⇄ ACTIVATED，含 ACTIVATING/DEACTIVATING/FAILED
 * 中间态与失败态）与激活承载于本接口自身——一个定义至多一个激活。新增生命周期方法均为 default，
 * 默认实现收敛到"非状态机感知"（{@link #isStateMachineAware()} = false）的兼容行为，
 * 显式抛 {@link NopException}（不静默 no-op），不破坏现有第三方插件实现。
 */
public interface IPlugin {
    String getPluginGroupId();

    String getPluginArtifactId();

    String getPluginVersion();

    Timestamp getLastChangeTime();

    Timestamp getLoadTime();

    /**
     * 相当于执行本地RPC调用。定义级路由：命令 bean 分发于本插件激活容器（单容器，无实例路由）。
     *
     * <p>未激活（非 ACTIVATED）抛 INACTIVE 错误。
     */
    CompletionStage<Map<String, Object>> invokeCommandAsync(String command, Map<String, Object> args,
                                                             String fieldSelection,
                                                             IPluginCancelToken cancelToken);

    /**
     * 相当于执行本地RPC调用（同步版本）。定义级路由语义同 {@link #invokeCommandAsync}。
     */
    Map<String, Object> invokeCommand(String command, Map<String, Object> args,
                                       String fieldSelection,
                                       IPluginCancelToken cancelToken);

    /**
     * 启动插件（旧接口，兼容保留）：{@code start = load + activate}。
     *
     * <p>非状态机感知插件（{@link #isStateMachineAware()} = false）走兼容路径，
     * 执行旧 start 语义（不进入新状态机）。
     */
    void start(String pluginGroupId, String pluginArtifactId, String pluginVersion,
                Map<String, Object> config);

    /**
     * 更新插件配置（定义级配置域 = load 传入的初始 config + 本方法累积合并视图）。
     *
     * <p>态语义：LOADED 时缓存待下次激活应用；ACTIVATED 时热应用（合并视图重算 + provider 变更传播）。
     *
     * <p>默认抛 {@link NopException}——非状态机感知插件不进入新状态机，
     * 实现层对其不调用本方法；显式失败避免静默跳过（No Silent No-Op）。
     */
    default void updateConfig(Map<String, Object> config) {
        throw new NopException(PluginApiErrors.ERR_PLUGIN_LIFECYCLE_NOT_SUPPORTED);
    }

    /**
     * 停止插件（旧接口，兼容保留）：{@code stop = deactivate + unload}。
     *
     * <p>非状态机感知插件走兼容路径，执行旧 stop 语义。
     */
    void stop();

    /**
     * 本插件是否感知（参与）新状态机（load/activate 分离）。
     *
     * <p>默认返回 false——存量第三方插件（只有 start/stop）不进入新状态机；
     * 实现层据此路由：aware → 新状态机（load/activate 分离）；非 aware → 兼容路径
     * （loadPlugin 执行旧 start 语义、unloadPlugin 执行旧 stop 语义）。
     */
    default boolean isStateMachineAware() {
        return false;
    }

    /**
     * 返回插件当前状态（{@link PluginState} 单层六态）。
     *
     * <p>默认返回 {@link PluginState#LOADED}（保守值）——插件对象仅经 loadPlugin 成功后可见，
     * unload 后从 manager 移除；非 aware 插件不存在"可见的 UNLOADED"窗口。
     */
    default PluginState getState() {
        return PluginState.LOADED;
    }

    /**
     * 加载定义到 LOADED（解析静态定义，不建子容器）。
     *
     * <p>默认抛 {@link NopException}（{@code ERR_PLUGIN_LIFECYCLE_NOT_SUPPORTED}）——非 aware 插件不进入新状态机，
     * 实现层对非 aware 插件永不调用本方法；显式失败避免静默跳过（No Silent No-Op）。
     */
    default void load(Map<String, Object> config) {
        throw new NopException(PluginApiErrors.ERR_PLUGIN_LIFECYCLE_NOT_SUPPORTED);
    }

    /**
     * 丢弃定义（须未激活：ACTIVATED/中间态时 unload 抛明确异常，见 01 §三不变量）。
     *
     * <p>默认抛 {@link NopException}（{@code ERR_PLUGIN_LIFECYCLE_NOT_SUPPORTED}）——非 aware 插件不进入新状态机，
     * 实现层对非 aware 插件永不调用本方法；显式失败避免静默跳过（No Silent No-Op）。
     */
    default void unload() {
        throw new NopException(PluginApiErrors.ERR_PLUGIN_LIFECYCLE_NOT_SUPPORTED);
    }

    /**
     * 激活（01 §7.1）：门控满足时建子容器 + 执行 activator + 注册 effect，返回 true；
     * 门控未满足 no-op 返回 false（不抛异常）；已 ACTIVATED 幂等返回 true（并发重复经
     * in-flight 单飞收敛，不重跑）。
     *
     * <p><b>同步返回 boolean 与 {@link #deactivate()} 返回 {@code CompletionStage<Void>}
     * 的不对称是有意设计</b>：激活的资源建立为同步操作（展开窗口短）；deactivate 涉及异步
     * effect 回退故返回 CompletionStage（见 01 §四时间静止语义）。
     *
     * <p>默认抛 {@link NopException}（{@code ERR_PLUGIN_LIFECYCLE_NOT_SUPPORTED}）——非 aware 插件不进入新状态机，
     * 实现层对非 aware 插件永不调用本方法；显式失败避免静默跳过（No Silent No-Op）。
     */
    default boolean activate() {
        throw new NopException(PluginApiErrors.ERR_PLUGIN_LIFECYCLE_NOT_SUPPORTED);
    }

    /**
     * 去激活（01 §7.1）：先 {@code scope.close()}（LIFO 回退全部 effect）再子容器 stop；
     * 回到 LOADED（定义保留）。重复 deactivate 幂等。
     *
     * <p>默认抛 {@link NopException}（{@code ERR_PLUGIN_LIFECYCLE_NOT_SUPPORTED}）——非 aware 插件不进入新状态机，
     * 实现层对非 aware 插件永不调用本方法；显式失败避免静默跳过（No Silent No-Op）。
     */
    default CompletionStage<Void> deactivate() {
        throw new NopException(PluginApiErrors.ERR_PLUGIN_LIFECYCLE_NOT_SUPPORTED);
    }

    /**
     * 按强类型接口获取服务，返回<b>激活态绑定代理</b>：ACTIVATED 时路由到实现；
     * DEACTIVATING 完成后调用快速失败（抛 INACTIVE）；重新激活后同一代理引用恢复可用。
     *
     * <p>多候选规则：primary 优先 → bean id 与接口匹配的唯一实现 → 多候选且无 primary 抛明确异常
     * （不静默返回集合）。仅支持接口类型。
     *
     * <p>默认抛 {@link NopException}（{@code ERR_PLUGIN_LIFECYCLE_NOT_SUPPORTED}）——非 aware 插件不进入新状态机；
     * 显式失败避免静默跳过（No Silent No-Op）。
     */
    default <T> T getService(Class<T> serviceType) {
        throw new NopException(PluginApiErrors.ERR_PLUGIN_LIFECYCLE_NOT_SUPPORTED);
    }

    /**
     * 按类型获取全部实现（集合版；每个候选均为激活态绑定代理，重激活后按 bean id 恢复可用）。
     *
     * <p>默认抛 {@link NopException}（{@code ERR_PLUGIN_LIFECYCLE_NOT_SUPPORTED}）——非 aware 插件不进入新状态机；
     * 显式失败避免静默跳过（No Silent No-Op）。
     */
    default <T> Collection<T> getServices(Class<T> serviceType) {
        throw new NopException(PluginApiErrors.ERR_PLUGIN_LIFECYCLE_NOT_SUPPORTED);
    }
}

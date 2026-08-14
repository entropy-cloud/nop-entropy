package io.nop.plugin.api;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * 插件实例（实例级状态机，每个实例 = 一个 fiber，见设计文档 01-architecture-baseline.md §7.2）。
 *
 * <p>一个 LOADED 的插件定义可派生 N 个独立激活实例（多租户/多 agent），每实例持有独立
 * instanceKey / scope / effect / 配置域。实例由 createInstance 创建，deactivate 只销毁内部子容器
 * 并回退 effect（实例对象保留，可重新 activate），destroy 移除实例。
 *
 * <p>关键语义：
 * <ul>
 *   <li>{@link #getConfig()} 任何态可读（DEACTIVATED 仍可读——实例级 coeffect 依此评估）。</li>
 *   <li>{@link #getScope()} 非 ACTIVATED 态返回 null。</li>
 *   <li>{@link #getService(Class)} 返回生命周期绑定代理：ACTIVATED 时路由到实现，
 *       deactivate/destroy 后调用快速失败（抛异常，不悬空）。</li>
 *   <li>per-instance 生命周期串行：同一实例的 activate/deactivate 不并发执行（in-flight 单飞）。</li>
 * </ul>
 *
 * <p>不暴露内部容器：子容器是实现细节，API 层不出现 IBeanContainer/BeansModel。
 */
public interface IPluginInstance {

    /**
     * 实例标识（tenant/agent/session），同一定义下唯一。
     */
    String getInstanceKey();

    /**
     * 返回当前实例状态（ACTIVATED / DEACTIVATED）。
     */
    InstanceState getState();

    /**
     * 激活本实例（实例化、注册 effect），异步完成。
     *
     * <p>并发重复 activate 幂等（返回既有实例的激活过程）；deactivate 后重新 activate
     * 复用静态定义重新执行 activator。
     */
    CompletionStage<Void> activate();

    /**
     * 去激活本实例（回退 effect，保留实例对象），异步完成。重复 deactivate 幂等。
     */
    CompletionStage<Void> deactivate();

    /**
     * ACTIVATED 态返回 effect 聚合器（{@link IPluginScope}）；否则返回 null。
     */
    IPluginScope getScope();

    /**
     * 按强类型接口获取服务，返回生命周期绑定代理。
     *
     * <p>ACTIVATED 时路由到实现；deactivate/destroy 后调用快速失败（抛异常）。
     * 多候选规则：按 primary 优先；无 primary 时按 bean id 与接口匹配的唯一实现；
     * 多候选且无 primary 时抛明确异常（不静默返回集合）。
     */
    <T> T getService(Class<T> serviceType);

    /**
     * 按类型获取全部实现（集合版）。
     */
    <T> Collection<T> getServices(Class<T> serviceType);

    /**
     * 父实例（层级实例化 subagent）：服务查找沿链回退、生命周期级联销毁、配置层叠（子覆盖父）；
     * 顶层实例返回 null。
     */
    IPluginInstance getParent();

    /**
     * 实例配置域（createInstance 传入 + 定义默认合并视图，定义默认 ← 实例覆盖）。
     *
     * <p>随实例持有、任何态可读（DEACTIVATED 仍可读——实例级 coeffect 依此评估）。
     * 由 plugin 框架自己管理，不写全局配置。
     */
    Map<String, Object> getConfig();

    /**
     * per-instance 命令路由：路由到本实例子容器（多实例下命令隔离由此保证）。
     */
    CompletionStage<Map<String, Object>> invokeCommandAsync(String command, Map<String, Object> args,
                                                            String fieldSelection,
                                                            IPluginCancelToken cancelToken);

    /**
     * per-instance 命令路由：同步版本（内部等价调用 {@link #invokeCommandAsync}）。
     */
    Map<String, Object> invokeCommand(String command, Map<String, Object> args,
                                      String fieldSelection,
                                      IPluginCancelToken cancelToken);

    /**
     * 销毁本实例（回退 effect、从定义移除）。销毁后实例对象不再可用。
     */
    void destroy();
}

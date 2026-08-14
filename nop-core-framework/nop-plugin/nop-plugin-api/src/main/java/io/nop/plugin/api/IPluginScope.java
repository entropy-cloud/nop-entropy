package io.nop.plugin.api;

import java.util.Collection;
import java.util.List;

/**
 * 激活期作用域句柄——plugin 框架自己的可逆 effect 机制（见设计文档 01-architecture-baseline.md §7.4）。
 *
 * <p>作为参数传入 {@link IPluginActivator#activate(IPluginScope, Map)}，提供 effect 注册与服务获取。
 * 可逆性由本 scope 自身保证（注册即回退），不依赖、不承诺观测 IoC 内部（子容器 stop 触发 bean
 * destroy 是实现细节，非 API 契约）。
 *
 * <p>关闭语义：{@link #close()} 按 LIFO（后注册先回退）执行全部 disposer，执行后
 * {@link #effects()} 清空 = quiescence。close 幂等（已关闭则直接返回）；close 之后再次
 * {@link #effect(Disposable)} 注册抛异常。
 */
public interface IPluginScope {

    /**
     * 注册可逆操作，返回可移除句柄。
     *
     * @param d 可逆操作（disposer），不允许为 null。
     * @return 可移除句柄——调用其 {@link Disposable#dispose()} 可从本 scope 移除并回退该 effect。
     * @throws IllegalStateException scope 已 close 后注册。
     */
    Disposable effect(Disposable d);

    /**
     * 当前已注册 effect 的可观测视图，供调试与 quiescence 断言（清空 = 内部 quiescence）。
     */
    List<Disposable> effects();

    /**
     * 按 LIFO（后注册先回退）执行全部 disposer 并清空列表（quiescence）。
     *
     * <p>一个 disposer 抛异常不阻断其余（记录并继续）；重复 close 幂等（已关闭则直接返回）。
     */
    void close();

    /**
     * 激活期等价于实例的 {@link IPluginInstance#getService(Class)}（activator 取 bean 用）。
     *
     * <p>多候选规则：按 primary 优先；无 primary 时按 bean id 与接口匹配的唯一实现；
     * 多候选且无 primary 时抛明确异常（不静默返回集合）。
     */
    <T> T getService(Class<T> serviceType);

    /**
     * 按类型获取全部实现（集合版）。
     */
    <T> Collection<T> getServices(Class<T> serviceType);
}

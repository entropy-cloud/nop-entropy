package io.nop.plugin.api;

import java.util.Map;

/**
 * 插件激活器——实例激活入口（见设计文档 01-architecture-baseline.md §7.5）。
 *
 * <p>插件定义（{@code *.plugin.xml}）通过 {@code activator="beanId"} 声明激活器。激活实例时，
 * 实现层实例化子容器后调用 {@link #activate(IPluginScope, Map)}，scope 与 config 作为参数传入
 * （与 Cordis {@code apply(ctx, config)} 双参数对齐，避免成员变量保存 context 的坏设计）。
 *
 * <p>重激活语义：实例 deactivate 后再次 activate 会重新执行本方法（scope 已 close，需重新注册 effect）；
 * "重复 activate 幂等"仅指并发重复（in-flight 单飞，不重跑 activator）。
 */
@FunctionalInterface
public interface IPluginActivator {

    /**
     * 激活入口。
     *
     * @param scope  实例作用域句柄——经 {@link IPluginScope#getService(Class)} 获取 bean、
     *               {@link IPluginScope#effect(Disposable)} 注册可逆操作（实例 deactivate/destroy 时
     *               LIFO 回退）。
     * @param config 实例配置域（createInstance 传入的合并视图，实例配置 + 定义默认，实例覆盖定义）。
     * @return 返回值 = disposer（可空），非 null 时实现层自动注册为该实例的 effect
     *         （与显式 {@link IPluginScope#effect(Disposable)} 完全等价，便捷模式 {@code return () -> cleanup}）。
     */
    Disposable activate(IPluginScope scope, Map<String, Object> config);
}

package io.nop.plugin.api;

/**
 * 可逆操作（effect）的统一接口。plugin 框架自定的函数式接口，保持 {@code nop-plugin-api} 零依赖（纯 JDK 类型）。
 *
 * <p>语义（见设计文档 01-architecture-baseline.md §7.4）：注册到 {@link IPluginScope} 的可逆操作，
 * 实例 deactivate/destroy 时按 LIFO 顺序回退执行。dispose 方法应幂等——重复执行不抛异常。
 */
@FunctionalInterface
public interface Disposable {

    /**
     * 回退（撤销）本可逆操作。实现应保证幂等，且不抛出检查异常。
     */
    void dispose();
}

package io.nop.sys.dao.message;

import java.util.function.BooleanSupplier;

/**
 * 事件认领租约的续租回调载体（C2-102 续租挂点）。
 *
 * <p>消费等待全程阻塞在 {@code NonBroadcastEventProcessor.process} → dispatchCallback →
 * invokeConsumer 调用链内，处理器线程在 dispatch 返回前无执行机会——续租只能由等待循环
 * 本体按切片触发。process 在 dispatch 期间以线程绑定上下文注入续租回调，invokeConsumer
 * 的有界等待循环每耗尽一个切片即调用一次；broadcast 路径不注入（广播事件无租约语义）。
 *
 * <p>上下文必须由 process 入口注入、出口 finally 清除：轮询 executor 线程复用，
 * 残留会把上一事件的续租回调泄漏给后续 dispatch。
 */
final class DispatchLeaseContext {
    private static final ThreadLocal<BooleanSupplier> KEEP_ALIVE = new ThreadLocal<>();

    private DispatchLeaseContext() {
    }

    static void install(BooleanSupplier keepAlive) {
        KEEP_ALIVE.set(keepAlive);
    }

    static void clear() {
        KEEP_ALIVE.remove();
    }

    static BooleanSupplier current() {
        return KEEP_ALIVE.get();
    }
}

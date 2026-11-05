/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.debugger;

import io.nop.api.core.util.ICancellable;
import io.nop.core.initialize.ICoreInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * XLang 全局设施引用计数 registrar（CRA 8.40 W-05）。
 *
 * <p>契约语义（进程级共享设施）：每项目照常各自执行一次 {@code XLangCoreInitializer.initialize()}
 * ——全局部分为同实例幂等覆盖（无害），per-project 的 registerXDef 经 ProjectResourceComponentManager
 * 委托链必须每项目执行；registrar 引用计数<b>只门控销毁不门控初始化</b>：持有者释放时计数减一，
 * 计数归零时按获取逆序对全部保留的初始化器逐个执行 destroy（{@code remove(name, func)} 按实例匹配
 * ——同实例前提下幂等无害，ReflectionManager 缓存淘汰后异实例前提下精确撤销），最后一个项目关闭
 * 才撤销全局设施，先关闭的项目不再拆走其余项目的 XLang 全设施。</p>
 *
 * <p>全部操作串行化（低频，项目开/关粒度）。抽象面 = 平台既有 {@link ICoreInitializer} 使用面，
 * 测试以计数替身实现同一接口。</p>
 */
public class SharedCoreInitializerRegistrar {
    static final Logger LOG = LoggerFactory.getLogger(SharedCoreInitializerRegistrar.class);

    private static final SharedCoreInitializerRegistrar INSTANCE = new SharedCoreInitializerRegistrar();

    private final ReentrantLock lock = new ReentrantLock();
    private final List<ICoreInitializer> retained = new ArrayList<>();
    private int holders;

    public static SharedCoreInitializerRegistrar instance() {
        return INSTANCE;
    }

    /**
     * 包内可见构造器：测试每用例独立实例（静态单例共享计数会使用例间断言互相污染）。生产走
     * {@link #instance()}。
     */
    SharedCoreInitializerRegistrar() {
    }

    /**
     * 同步执行 {@code init.initialize()} 并登记持有：成功 → 持有者计数 +1 并返回单次有效凭据
     * （凭据释放 = 计数 −1，重复释放无效）；失败 → 不计数、原异常上抛（调用方按 CL-204 三态处置，
     * 可经 {@link #destroyIfOrphaned} 清理孤儿全局注册）。
     *
     * <p>作用域契约：须在 {@code ProjectEnv.withProject} 作用域内调用——initialize 的 registerXDef
     * 经委托链需要当前项目（违者按委托链 IllegalStateException 显式失败）。</p>
     */
    public ICancellable acquire(ICoreInitializer init) {
        lock.lock();
        try {
            init.initialize();
            holders++;
            retained.add(init);
            return new Holder(init);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 孤儿注册清理（W-05 失败路径）：仅当当前无任何持有者时执行该初始化器的 destroy——存在其他
     * 持有者时全局注册为共享实例，销毁即跨项目拆设施（缺陷复现形态，禁止）。
     */
    public void destroyIfOrphaned(ICoreInitializer init) {
        lock.lock();
        try {
            if (holders == 0) {
                try {
                    init.destroy();
                } catch (Exception e) {
                    LOG.warn("nop.debugger.destroy-initializer-fail", e);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    private void release(ICoreInitializer init) {
        lock.lock();
        try {
            // 重复释放由凭据侧单次有效标记（Holder.released）阻断
            holders--;
            if (holders == 0) {
                // 计数归零：对全部保留的初始化器按获取逆序逐个 destroy——保留集在释放时不移除：
                // remove(name, func) 按实例匹配，各项目各自的初始化器 destroy 撤销各自注册的
                // 全局实例（同实例前提下幂等无害，ReflectionManager 缓存淘汰后异实例前提下精确撤销）
                List<ICoreInitializer> toDestroy = new ArrayList<>(retained);
                retained.clear();
                for (int i = toDestroy.size() - 1; i >= 0; i--) {
                    try {
                        toDestroy.get(i).destroy();
                    } catch (Exception e) {
                        LOG.warn("nop.debugger.destroy-initializer-fail", e);
                    }
                }
            }
        } finally {
            lock.unlock();
        }
    }

    private final class Holder implements ICancellable {
        private final ICoreInitializer init;
        private final AtomicBoolean released = new AtomicBoolean(false);
        private volatile String cancelReason;

        Holder(ICoreInitializer init) {
            this.init = init;
        }

        @Override
        public void cancel(String reason) {
            if (released.compareAndSet(false, true)) {
                this.cancelReason = reason;
                release(init);
            }
        }

        @Override
        public boolean isCancelled() {
            return released.get();
        }

        @Override
        public String getCancelReason() {
            return released.get() ? cancelReason : null;
        }

        @Override
        public void removeOnCancel(java.util.function.Consumer<String> callback) {
            // 凭据不支持外部追加取消回调——释放语义已由 cancel(reason) 承载
        }

        @Override
        public void appendOnCancel(java.util.function.Consumer<String> callback) {
            // 同上：持有凭据的取消语义固定为 registrar 计数释放，不受理外部追加
        }
    }
}

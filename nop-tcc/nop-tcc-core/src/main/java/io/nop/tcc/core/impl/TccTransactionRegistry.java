/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.tcc.core.impl;

import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.context.IContext;
import io.nop.commons.concurrent.ContextualizedRegistry;
import io.nop.tcc.api.ITccTransaction;

import java.util.Map;

public class TccTransactionRegistry extends ContextualizedRegistry<String, ITccTransaction> {
    static final String CONTEXT_KEY = TccTransactionRegistry.class.getSimpleName();

    /**
     * 1.6-C1-004（plan 2543 W-02）：注册表身份原子化。
     * <p>
     * 原实现 get→null→new→setAttribute 无同步：同一 IContext 被两线程并发首次进入时各自 new
     * 一个注册表，后 set 者赢、先者成孤儿——同一逻辑操作分裂为两条 TCC 事务，输者事务脱管。
     * <p>
     * 锁载体是 <b>属性 Map 本体</b>（{@code context.getAttrs()}）而非 context 对象：租户/委托包装
     * 上下文（{@code DelegateContext}）下两线程可持有不同 wrapper 而共享同一底层 attributes Map，
     * 锁 wrapper 对象不互斥；锁 Map 才互斥。不用 {@code computeIfAbsent}——{@code IContext.getAttrs()}
     * 的契约未保证返回并发 Map，{@code synchronized} + 双重检查对任意 Map 实现安全。
     */
    public static TccTransactionRegistry instance() {
        return instance(ContextProvider.getOrCreateContext());
    }

    /**
     * 包级测试 seam（plan 2543 W-02 红②可构造性）：允许测试把同一 {@link IContext} 实例分发给
     * 多线程。公共 {@link #instance()} 经 ContextProvider 的 ThreadLocal 取上下文，N 线程天然各持
     * 独立上下文，无法构造竞态。
     */
    /**
     * 测试 seam（plan 2543 红②确定性）：首次查询为 null 与进入临界区之间插入交错点。
     * 生产为 null（零行为影响）；测试设置后可在该点强制「多线程都已读到 null」再继续，
     * 使「非原子创建 → 身份分裂」的缺陷态可确定性复现（否则多线程竞速腿是概率性的）。
     */
    static volatile Runnable raceProbeHook;

    static TccTransactionRegistry instance(IContext context) {
        Map<String, Object> attrs = context.getAttrs();
        TccTransactionRegistry registry = (TccTransactionRegistry) attrs.get(CONTEXT_KEY);
        if (registry != null)
            return registry;

        Runnable hook = raceProbeHook;
        if (hook != null)
            hook.run();

        synchronized (attrs) {
            registry = (TccTransactionRegistry) attrs.get(CONTEXT_KEY);
            if (registry == null) {
                registry = new TccTransactionRegistry();
                context.setAttribute(CONTEXT_KEY, registry);
            }
        }
        return registry;
    }

    /**
     * 1.6-C1-004（plan 2543 W-02）：条件恢复——仅当我方事务仍是该 txnGroup 的当前值时才把旧值放回。
     * 无条件 {@code put(old)} 的叠放恢复会在并发交错下清掉对端的注册（B put(txnB) 后 A 的
     * whenComplete 恢复 old=null → B 的分支 join 判定失效）。单线程嵌套语义零变化：
     * 内层完成时 get()==当前内层事务 → 恢复外层；外层完成时 get()==外层 → 恢复 old。
     */
    public void restore(String txnGroup, ITccTransaction expectCurrent, ITccTransaction old) {
        if (get(txnGroup) == expectCurrent) {
            put(txnGroup, old);
        }
    }

    @Override
    protected String normalizeKey(String txnGroup) {
        return TccHelper.normalizeTxnGroup(txnGroup);
    }
}
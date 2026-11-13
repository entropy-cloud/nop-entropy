/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.txn.impl;

import io.nop.api.core.annotations.core.GlobalInstance;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.context.IContext;
import io.nop.commons.concurrent.ContextualizedRegistry;
import io.nop.commons.util.StringHelper;
import io.nop.dao.DaoConstants;
import io.nop.dao.txn.ITransaction;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@GlobalInstance
public class TransactionRegistry extends ContextualizedRegistry<String, ITransaction> {
    static final String CONTEXT_KEY = TransactionRegistry.class.getSimpleName();

    /**
     * plan 2517 R2-14 裁定⑧：挂起位集合（runWithoutTransaction/NOT_SUPPORTED 注销窗口的挂起标记形态）
     */
    private final Set<String> suspendedGroups = ConcurrentHashMap.newKeySet();

    public static TransactionRegistry instance() {
        IContext context = ContextProvider.getOrCreateContext();
        // plan 2517 R2-14 裁定①：computeIfAbsent 原子首触（C1-023 同构面），零 nop-api-core 触碰
        return (TransactionRegistry) context.getAttrs().computeIfAbsent(CONTEXT_KEY, k -> new TransactionRegistry());
    }

    /**
     * plan 2517 R2-14 裁定①：实例创建层原子化载体（N8 两层不混淆——本类只负责 registry 单例；
     * 事务协议层原子化见 getOrCreate / suspend / resume）
     */
    public RegistrationResult getOrCreate(String txnGroup, Function<String, ITransaction> fn) {
        String key = normalizeKey(txnGroup);
        ITransaction existing = get(key);
        if (existing != null)
            return new RegistrationResult(existing, false);
        // 注册表锁只保护结构变更（锁序裁定），compute 函数体内无 I/O 无其他锁获取
        synchronized (this) {
            existing = get(key);
            if (existing != null)
                return new RegistrationResult(existing, false);
            ITransaction created = fn.apply(txnGroup);
            if (created != null)
                put(key, created);
            return new RegistrationResult(created, created != null);
        }
    }

    /**
     * plan 2517 R2-14 裁定⑧：挂起事务——registry entry 摘除并挂 suspended 位（查找侧跳过）。
     * 锁只保护注册表结构变更。
     */
    public ITransaction suspend(String txnGroup, ITransaction txn) {
        String key = normalizeKey(txnGroup);
        synchronized (this) {
            ITransaction current = super.get(key);
            suspendedGroups.add(key);
            if (current == txn) {
                remove(key);
                return txn;
            }
            // 并发方已替换注册项：挂起标记仍然隐藏该组，返回现值供调用方判定
            return current;
        }
    }

    /**
     * plan 2517 R2-14 裁定⑧：恢复挂起事务（摘 suspended 位并重挂注册表）
     */
    public void resume(String txnGroup, ITransaction txn) {
        String key = normalizeKey(txnGroup);
        synchronized (this) {
            suspendedGroups.remove(key);
            if (txn != null)
                put(key, txn);
        }
    }

    @Override
    public ITransaction get(String key) {
        key = normalizeKey(key);
        // 裁定⑧：挂起位的组对查找侧不可见
        if (suspendedGroups.contains(key))
            return null;
        return super.get(key);
    }

    /**
     * getOrCreate 的结果载体（与 ITransactionManager.RegisteredTxn 对应）
     */
    static class RegistrationResult {
        private final ITransaction txn;
        private final boolean newlyRegistered;

        RegistrationResult(ITransaction txn, boolean newlyRegistered) {
            this.txn = txn;
            this.newlyRegistered = newlyRegistered;
        }

        ITransaction getTxn() {
            return txn;
        }

        boolean isNewlyRegistered() {
            return newlyRegistered;
        }
    }

    @Override
    protected String normalizeKey(String txnGroup) {
        if (StringHelper.isEmpty(txnGroup))
            txnGroup = DaoConstants.DEFAULT_TXN_GROUP;
        return txnGroup;
    }
}
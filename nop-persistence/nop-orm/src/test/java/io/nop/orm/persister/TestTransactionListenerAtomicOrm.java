/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.persister;

import io.nop.app.SimsLesson;
import io.nop.commons.cache.ICache;
import io.nop.dao.txn.ITransaction;
import io.nop.dao.txn.ITransactionListener;
import io.nop.dao.txn.impl.AbstractTransaction;
import io.nop.orm.AbstractOrmTestCase;
import io.nop.orm.ILoadedOrmModel;
import io.nop.orm.factory.SessionFactoryImpl;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2.3-C9-001（plan 2553 裁定 C）钉：事务级单聚合 evict listener + pendingEvictKeys 聚合
 * + 完成后自注销。
 *
 * <p>路径：真实 session factory + H2 布景，SimsLesson 实体在测试模型中
 * useGlobalCache="true"（app.orm.xml），全局开关 nop.orm.entity.global-cache.enabled
 * 缺省为 true——无需反射启用。evictGlobalCache 为 EntityPersisterImpl 的
 * package-private 方法，本测试同包直调。事务经 env.txn().runInTransaction 显式开启
 * （无事务时 evict 走即时 remove 分支，不注册 listener）。</p>
 *
 * <p>断言面：(a) 同一事务内三次 evict（同实体两次 + 另一实体一次）只注册一个
 * ITransactionListener（缺陷态每实体各注册一个 → 红）；(b) pendingTxnEvictKeys 恰一个
 * txn 条目、聚合 2 个去重 cache key；(c) 提交前 cache 中的陈旧条目仍在（延迟到
 * onAfterCommit 才失效），提交后条目被移除且 pendingTxnEvictKeys 条目自注销。</p>
 */
public class TestTransactionListenerAtomicOrm extends AbstractOrmTestCase {

    private static Map<ITransaction, Set<String>> pendingKeys(EntityPersisterImpl persister) {
        try {
            Field field = EntityPersisterImpl.class.getDeclaredField("pendingTxnEvictKeys");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            ConcurrentHashMap<ITransaction, Set<String>> map =
                    (ConcurrentHashMap<ITransaction, Set<String>>) field.get(persister);
            return map;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<ITransactionListener> txnListeners(ITransaction txn) {
        try {
            Field field = AbstractTransaction.class.getDeclaredField("listeners");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<ITransactionListener> listeners = (List<ITransactionListener>) field.get(txn);
            return listeners;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    public void testAggregateSingleListenerAndDeferredEvict() throws Exception {
        ILoadedOrmModel loadedModel = ((SessionFactoryImpl) sessionFactory).getLoadedOrmModel();
        EntityPersisterImpl persister = (EntityPersisterImpl) loadedModel
                .getEntityPersister(SimsLesson.class.getName());

        // 前置条件守卫：全局缓存必须处于启用态，否则本 pin 空转
        Field useGlobalCacheField = EntityPersisterImpl.class.getDeclaredField("useGlobalCache");
        useGlobalCacheField.setAccessible(true);
        assertTrue(useGlobalCacheField.getBoolean(persister),
                "SimsLesson persister 必须启用全局缓存（布景前提）");

        ICache<String, Object> globalCache = loadedModel.getEnv().getGlobalCache(SimsLesson.class.getName());

        SimsLesson l1 = new SimsLesson();
        l1.setLessonId("T1");
        SimsLesson l2 = new SimsLesson();
        l2.setLessonId("T2");

        // 实际参与失效的 cache key（getCacheKey 私有，且租户实体 key 带前缀——以聚合集为准）
        List<String> capturedKeys = new java.util.ArrayList<>();

        sessionFactory.txn().runInTransaction(txn -> {
            // 同一实体 evict 两次 + 第二个实体一次：共 2 个去重 key
            persister.evictGlobalCache(null, l1);
            persister.evictGlobalCache(null, l1);
            persister.evictGlobalCache(null, l2);

            // (a) 单聚合 listener：后续 evict 仅追加 key，不重复注册
            List<ITransactionListener> listeners = txnListeners(txn);
            assertEquals(1, listeners.size(),
                    "同一事务内多次 evict 必须只注册一个聚合 ITransactionListener（缺陷态每实体一个 -> 红）");

            // (b) pendingTxnEvictKeys 聚合
            Map<ITransaction, Set<String>> pending = pendingKeys(persister);
            assertEquals(1, pending.size(), "必须恰有一个 txn 条目");
            Set<String> keys = pending.values().iterator().next();
            assertEquals(2, keys.size(), "同实体重复 evict 去重 + 第二实体 -> 恰 2 个 key");
            capturedKeys.addAll(keys);

            // (c) 提交前失效必须尚未发生：预置陈旧条目，commit 时才被 listener 移除
            for (String key : keys) {
                globalCache.put(key, "stale");
            }

            // 提交前缓存条目仍在（延迟失效语义）
            for (String key : keys) {
                assertEquals("stale", globalCache.get(key), "提交前 evict 不得提前生效");
            }
            return null;
        });

        // 提交后：onAfterCommit 移除缓存条目 + onAfterCompletion 自注销聚合状态
        Map<ITransaction, Set<String>> pending = pendingKeys(persister);
        assertTrue(pending.isEmpty(),
                "提交后 pendingTxnEvictKeys 必须已自注销（缺陷态 txn key 泄漏 -> 红）");
        for (String key : capturedKeys) {
            assertNull(globalCache.get(key), "提交后缓存条目必须被 onAfterCommit 失效: " + key);
        }
    }
}

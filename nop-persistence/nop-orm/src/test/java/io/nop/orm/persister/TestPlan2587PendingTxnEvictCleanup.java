/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.persister;

import io.nop.dao.shard.ShardSelection;
import io.nop.dao.txn.ITransaction;
import io.nop.dao.txn.ITransactionListener;
import io.nop.dao.txn.ITransactionTemplate;
import io.nop.commons.cache.ICache;
import io.nop.orm.IOrmEntity;
import io.nop.orm.OrmEntityState;
import io.nop.orm.driver.IEntityPersistDriver;
import io.nop.orm.impl.ProxyStub;
import io.nop.orm.model.IEntityModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2587 钉：pendingTxnEvictKeys close 路径同步清理。
 * <ul>
 * <li>pin-a：事务内 evict 注册聚合 listener；事务未走 commit/rollback 终态即 close（放弃路径）时，
 *     onClose 必须移除聚合条目——否则以强引用 ITransaction 为 key 的 entry 泄漏（缺陷态红）。</li>
 * <li>pin-b：对照——onAfterCompletion（ROLLBACK/UNKNOWN 清理臂）保持既有语义。</li>
 * </ul>
 */
public class TestPlan2587PendingTxnEvictCleanup {

    private EntityPersisterImpl persister;
    private ITransaction txn;
    private final AtomicReference<ITransactionListener> registeredListener = new AtomicReference<>();

    @BeforeEach
    public void setUp() {
        persister = new EntityPersisterImpl();

        ICache<String, Object> globalCache = ProxyStub.of(ICache.class, (proxy, method, args) -> {
            if (method.getName().equals("removeAsync"))
                return CompletableFuture.completedFuture(null);
            return null;
        });

        IEntityModel entityModel = ProxyStub.of(IEntityModel.class, (proxy, method, args) -> {
            switch (method.getName()) {
                case "getName":
                    return "TestPlan2587EvictEntity";
                case "getQuerySpace":
                    return "plan2587";
                case "isUseGlobalCache":
                    return Boolean.TRUE;
                case "isUseTenant":
                case "isUseRevision":
                    return Boolean.FALSE;
                case "getShardPropId":
                    return 0;
                case "getTableName":
                    return "TBL_PLAN2587";
                case "getPersistDriver":
                    return "default";
                case "getAllPropIds":
                    return ProxyStub.of(io.nop.commons.collections.IntArray.class, (p2, m2, a2) -> {
                        if (m2.getName().equals("size"))
                            return 2;
                        if (m2.getName().equals("get"))
                            return (int) a2[0];
                        return null;
                    });
                default:
                    return null;
            }
        });

        txn = ProxyStub.of(ITransaction.class, (proxy, method, args) -> {
            if (method.getName().equals("addListener") && args[0] instanceof ITransactionListener) {
                registeredListener.set((ITransactionListener) args[0]);
            }
            return null;
        });

        ITransactionTemplate txnTemplate = ProxyStub.of(ITransactionTemplate.class, (proxy, method, args) -> {
            switch (method.getName()) {
                case "isTransactionOpened":
                    return Boolean.TRUE;
                case "getRegisteredTransaction":
                    return txn;
                case "addTransactionListener":
                    registeredListener.set((ITransactionListener) args[1]);
                    return null;
                default:
                    return null;
            }
        });

        IPersistEnv env = ProxyStub.of(IPersistEnv.class, (proxy, method, args) -> {
            switch (method.getName()) {
                case "getGlobalCache":
                    return globalCache;
                case "txn":
                    return txnTemplate;
                case "createEntityPersistDriver":
                    return ProxyStub.of(IEntityPersistDriver.class, (p2, m2, a2) -> null);
                default:
                    return null;
            }
        });

        persister.init(entityModel, entity -> {
        }, env);
    }

    private IOrmEntity entity() {
        return ProxyStub.of(IOrmEntity.class, (proxy, method, args) -> {
            switch (method.getName()) {
                case "orm_entityName":
                    return "TestPlan2587EvictEntity";
                case "orm_idString":
                    return "1";
                case "orm_state":
                    return OrmEntityState.MANAGED;
                default:
                    return null;
            }
        });
    }

    @SuppressWarnings("unchecked")
    private Map<ITransaction, java.util.Set<String>> pendingMap() throws Exception {
        Field f = EntityPersisterImpl.class.getDeclaredField("pendingTxnEvictKeys");
        f.setAccessible(true);
        return (Map<ITransaction, java.util.Set<String>>) f.get(persister);
    }

    /**
     * pin-a：close 路径同步清理——放弃事务（close，无 commit/rollback）不泄漏聚合条目。
     * 缺陷态：无 onClose 臂 → map 残留强引用 txn entry → 红。
     */
    @Test
    public void testPlan2587_txnCloseWithoutCompletionCleansPendingEvictKeys() throws Exception {
        persister.evictGlobalCache((ShardSelection) null, entity());
        assertEquals(1, pendingMap().size(), "事务内 evict 必须先注册聚合条目（布景自检）");
        ITransactionListener listener = registeredListener.get();
        assertNotNull(listener, "evict 必须注册聚合 listener（布景自检）");

        listener.onClose(txn);
        assertTrue(pendingMap().isEmpty(),
                "缺陷态：放弃事务 close 后 pendingTxnEvictKeys 残留强引用 entry 泄漏 → 红；修复后 close 路径同步清理 → 绿");
    }

    /**
     * pin-b：对照臂——onAfterCompletion 清理语义保持（不因新增 onClose 臂回归）。
     */
    @Test
    public void testPlan2587_afterCompletionCleanupStillWorks() throws Exception {
        persister.evictGlobalCache((ShardSelection) null, entity());
        ITransactionListener listener = registeredListener.get();
        listener.onAfterCompletion(txn, ITransactionListener.CompleteStatus.ROLLBACK, null);
        assertTrue(pendingMap().isEmpty(), "onAfterCompletion 清理臂语义必须保持");
    }
}

/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.persister;

import io.nop.dao.shard.ShardSelection;
import io.nop.dao.txn.ITransactionTemplate;
import io.nop.commons.cache.ICache;
import io.nop.commons.collections.IAsyncMap;
import io.nop.orm.IOrmEntity;
import io.nop.orm.OrmEntityState;
import io.nop.orm.driver.IEntityPersistDriver;
import io.nop.orm.impl.ProxyStub;
import io.nop.orm.model.IEntityModel;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * plan 2517 R2-14 红⑥（C2-004，C10-005 半边）回归钉：
 * 迟到回填 vs 并发 evict 交错——evict 记失效世代后，持旧 readGen 的迟到回填必须被世代围栏放弃。
 * 缺陷态（无世代围栏）：miss 回填迟到 put 覆盖并发 evict → 缓存=v1 DB=v2 无界驻留 → 红；
 * 修复后：readGen != fenceGen 的 CAS 败者放弃回填 → 绿（接线验证 rule 23：围栏真实拦截迟到回填）。
 */
public class TestCra2517GenerationFence {

    private EntityPersisterImpl persister;
    private List<String> cachePuts;
    private List<String> cacheRemoves;
    private IOrmEntity entity;

    @BeforeEach
    public void setUp() {
        persister = new EntityPersisterImpl();
        cachePuts = new CopyOnWriteArrayList<>();
        cacheRemoves = new CopyOnWriteArrayList<>();

        // ICache 桩：记录 putAsync/removeAsync 调用（经 IAsyncMap 异步接口）
        ICache<String, Object> globalCache = ProxyStub.of(ICache.class, (proxy, method, args) -> {
            switch (method.getName()) {
                case "putAsync":
                    cachePuts.add(String.valueOf(args[0]));
                    return CompletableFuture.completedFuture(null);
                case "removeAsync":
                    cacheRemoves.add(String.valueOf(args[0]));
                    return CompletableFuture.completedFuture(null);
                default:
                    return null;
            }
        });

        IEntityModel entityModel = ProxyStub.of(IEntityModel.class, (proxy, method, args) -> {
            switch (method.getName()) {
                case "getName":
                    return "TestCra2517FenceEntity";
                case "getQuerySpace":
                    return "cra2517";
                case "isUseGlobalCache":
                    return Boolean.TRUE;
                case "isUseTenant":
                case "isUseRevision":
                    return Boolean.FALSE;
                case "getShardPropId":
                    return 0;
                case "getTableName":
                    return "TBL_CRA2517";
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

        ITransactionTemplate txnTemplate = ProxyStub.of(ITransactionTemplate.class, (proxy, method, args) -> {
            if (method.getName().equals("isTransactionOpened"))
                return Boolean.FALSE;
            return null;
        });

        IPersistEnv env = ProxyStub.of(IPersistEnv.class, (proxy, method, args) -> {
            switch (method.getName()) {
                case "getGlobalCache":
                    return globalCache;
                case "createEntityPersistDriver":
                    return ProxyStub.of(IEntityPersistDriver.class, (p2, m2, a2) -> null);
                case "txn":
                    return txnTemplate;
                default:
                    return null;
            }
        });

        persister.init(entityModel, entity -> {
        }, env);

        entity = ProxyStub.of(IOrmEntity.class, (proxy, method, args) -> {
            switch (method.getName()) {
                case "orm_entityName":
                    return "TestCra2517FenceEntity";
                case "orm_idString":
                    return "1";
                case "orm_state":
                    return OrmEntityState.MANAGED;
                case "orm_propValue":
                    return "v";
                default:
                    return null;
            }
        });
    }

    private Object invokeRepair(long readGen) throws Exception {
        Method m = EntityPersisterImpl.class.getDeclaredMethod("repairGlobalCache", IOrmEntity.class, long.class);
        m.setAccessible(true);
        return m.invoke(persister, entity, readGen);
    }

    private GenerationFence fence() throws Exception {
        Field f = EntityPersisterImpl.class.getDeclaredField("genFence");
        f.setAccessible(true);
        return (GenerationFence) f.get(persister);
    }

    /**
     * 红⑥核心交错：evict（记失效世代）后，装载发起时的旧 readGen 迟到回填必须被放弃（putAsync 不执行）。
     * 缺陷态：无世代围栏 → 迟到 putAsync 覆盖 evict → 红（repairGlobalCache 缺失即红——变异论证）。
     */
    @Test
    public void testCra2517_C2_004_lateRepairAfterEvictIsAbandoned() throws Exception {
        // 模拟装载发起时的世代快照（evict 之前）
        long readGen = fence().snapshot("1");

        // 并发 evict：记失效世代 + removeAsync
        persister.evictGlobalCache((ShardSelection) null, entity);
        assertTrue(cacheRemoves.size() >= 1, "evict 必须保持 remove 语义");

        // 迟到回填（持旧 readGen）必须被围栏放弃
        invokeRepair(readGen);
        assertEquals(0, cachePuts.size(), "缺陷态：迟到 putAsync 覆盖并发 evict → 红；修复后围栏放弃 → 绿");

        // 接线验证（rule 23）：世代围栏真实拦截迟到回填——新鲜世代快照的回填正常执行
        long freshGen = fence().snapshot("1");
        invokeRepair(freshGen);
        assertTrue(cachePuts.size() >= 1, "相等性判据（readGen==fenceGen）的新鲜回填必须执行");
    }

    /**
     * fence 非零单调起点（R2 终轮 Major 修正）：新建 fence entry 的初始世代必须严格大于全部历史发放世代，
     * 消除「fence 身份重置 × gen-0 快照碰撞」放行旧世代回填的破口。
     */
    @Test
    public void testCra2517_C2_004_fenceAllocatorMonotonicNonZero() {
        GenerationFence fence = new GenerationFence();
        long g1 = fence.snapshot("k1");
        long g2 = fence.snapshot("k2");
        assertTrue(g1 > 0 && g2 > 0, "fence 起点必须非零");
        assertTrue(g2 > g1, "fence 分配器必须单调递增（gen-0 碰撞破口）");

        // 相等性唯一公式：readGen == fenceGen 才回填，旧快照 CAS 必败
        assertTrue(fence.tryAcquireRepair("k1", g1), "相等性判据：新鲜快照回填成功");
        assertTrue(!fence.tryAcquireRepair("k1", g1), "旧快照（已被首次回填推进）CAS 必败");
        assertTrue(!fence.tryAcquireRepair("k1", g1 - 1_000_000L), "历史旧世代 CAS 必败");
    }
}

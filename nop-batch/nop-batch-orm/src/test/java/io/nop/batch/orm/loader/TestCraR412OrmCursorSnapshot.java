/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.orm.loader;

import io.nop.dao.api.IDaoEntity;
import io.nop.dao.api.IEntityDao;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * plan 2546 R4-12（C7-007 / W-03）：orm keyset 锚点改为**排序键值快照**。
 * <p>
 * 缺陷背景：原实现 {@code state.lastEntity} 持有活实体引用；orderBy 列被本任务就地变更后，
 * {@code findNext} 按实体**当前**排序值构造排他下界 → 剩余 WAITING 行永久落在窗口外（静默丢行）。
 * <p>
 * 本钉直接驱动 {@code snapshotCursor}（package-private），构造一个"会被就地变更的源行"与
 * 一个快照实体，断言快照值不随源行变更而改变。这是缺陷语义的最小可判面——比走完整
 * {@code load} 更精确（后者需要 fake IEntityDao 全 API）。
 */
public class TestCraR412OrmCursorSnapshot {

    /** 最小实体桩（动态代理）：支持 orm_propValueByName 读写。 */
    @SuppressWarnings("unchecked")
    static IDaoEntity newFakeEntity() {
        Map<String, Object> values = new HashMap<>();
        return (IDaoEntity) java.lang.reflect.Proxy.newProxyInstance(
                TestCraR412OrmCursorSnapshot.class.getClassLoader(),
                new Class[]{IDaoEntity.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "orm_propValueByName":
                            if (args.length == 1)
                                return values.get(args[0]);
                            values.put((String) args[0], args[1]);
                            return null;
                        case "toString":
                            return values.toString();
                        case "hashCode":
                            return System.identityHashCode(proxy);
                        case "equals":
                            return proxy == args[0];
                        default:
                            return null;
                    }
                });
    }

    /** pin03a：源行就地变更后，快照仍持有**变更前**的排序值（缺陷态=活实体引用会读到 DONE）。 */
    @Test
    public void pin03a_snapshotDoesNotFollowSourceMutation() throws Exception {
        IDaoEntity row = newFakeEntity();
        put(row, "sid", "s1");
        put(row, "status", "WAITING");

        OrmQueryBatchLoaderProvider.LoaderState<IDaoEntity> state = new OrmQueryBatchLoaderProvider.LoaderState<>();
        state.dao = stubDao();
        state.query = new io.nop.api.core.beans.query.QueryBean();
        state.query.addOrderField("status", false);

        invokeSnapshot(state, row);
        IDaoEntity cursor = state.cursor;

        // 快照建立后，源行被就地变更（模拟"本任务改了 orderBy 列"）
        put(row, "status", "DONE");

        assertEquals("WAITING", cursor.orm_propValueByName("status"),
                "快照必须冻结建立时的排序值——缺陷态是活实体引用，此处会读到 DONE → 游标漂移丢行");
        assertEquals("s1", cursor.orm_propValueByName("sid"), "主键亦须快照");
    }

    /** pin03b：同一 state 多次快照只创建一次实体并原地覆盖（复用，不重建）。 */
    @Test
    public void pin03b_cursorEntityCreatedOnceAndReused() throws Exception {
        IDaoEntity row1 = newFakeEntity();
        put(row1, "sid", "s1");
        put(row1, "status", "WAITING");

        NEW_ENTITY_CALLS.set(0);
        OrmQueryBatchLoaderProvider.LoaderState<IDaoEntity> state = new OrmQueryBatchLoaderProvider.LoaderState<>();
        state.dao = stubDao();
        state.query = new io.nop.api.core.beans.query.QueryBean();
        state.query.addOrderField("status", false);

        invokeSnapshot(state, row1);
        IDaoEntity firstCursor = state.cursor;

        IDaoEntity row2 = newFakeEntity();
        put(row2, "sid", "s2");
        put(row2, "status", "WAITING");
        invokeSnapshot(state, row2);

        assertEquals(firstCursor, state.cursor, "快照实体必须复用，不得每次重建");
        assertEquals("s2", state.cursor.orm_propValueByName("sid"), "复用时值须被覆盖为新行");
        assertEquals(1, NEW_ENTITY_CALLS.get(),
                "同一 state 内 newEntity() 必须恰调用 1 次（复用），实际=" + NEW_ENTITY_CALLS.get());
    }

    // ==================== 辅助 ====================

    private static final AtomicInteger NEW_ENTITY_CALLS = new AtomicInteger();

    private static void put(IDaoEntity entity, String name, Object value) {
        entity.orm_propValueByName(name, value);
    }

    @SuppressWarnings("unchecked")
    private static IEntityDao<IDaoEntity> stubDao() {
        return (IEntityDao<IDaoEntity>) java.lang.reflect.Proxy.newProxyInstance(
                TestCraR412OrmCursorSnapshot.class.getClassLoader(),
                new Class[]{IEntityDao.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "newEntity":
                            NEW_ENTITY_CALLS.incrementAndGet();
                            return newFakeEntity();
                        case "getPkColumnNames":
                            return new ArrayList<>(List.of("sid"));
                        default:
                            return null;
                    }
                });
    }

    private static void invokeSnapshot(OrmQueryBatchLoaderProvider.LoaderState<IDaoEntity> state,
                                       IDaoEntity lastEntity) throws Exception {
        java.lang.reflect.Method m = OrmQueryBatchLoaderProvider.class.getDeclaredMethod(
                "snapshotCursor", OrmQueryBatchLoaderProvider.LoaderState.class, IDaoEntity.class);
        m.setAccessible(true);
        m.invoke(null, state, lastEntity);
    }
}

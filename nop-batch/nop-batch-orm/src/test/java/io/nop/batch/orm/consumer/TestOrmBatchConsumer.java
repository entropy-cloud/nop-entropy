package io.nop.batch.orm.consumer;

import io.nop.api.core.beans.FilterBeanConstants;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.commons.type.StdDataType;
import io.nop.orm.IOrmEntity;
import io.nop.orm.dao.IOrmEntityDao;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityModel;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1-4.2-003（plan 2407）：OrmBatchConsumer upsert 语义直接测试。
 * 覆盖 useIdAsKey 两种路径、insert/update 计数与开关、单键 IN / 复合键 OR-AND 过滤形状、
 * entity==item 早退。全部经 JDK Proxy fake（先例 TestOrmBatchRecordSnapshotRestore），
 * 不触真库；生产代码零改动。
 */
public class TestOrmBatchConsumer {
    // 列布局：id(propId=0), colA(propId=1), colB(propId=2)
    private static final String[] COL_NAMES = {"id", "colA", "colB"};

    /** fake entity 同时充当 item bean：暴露 id/colA/colB getter 供 BeanTool 反射。 */
    public interface TestEntityBean {
        String getId();

        String getColA();

        String getColB();
    }

    private static IColumnModel newColumn(int propId) {
        return (IColumnModel) Proxy.newProxyInstance(TestOrmBatchConsumer.class.getClassLoader(),
                new Class[]{IColumnModel.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getName":
                            return COL_NAMES[propId];
                        case "getPropId":
                            return propId;
                        case "getStdDataType":
                            return StdDataType.STRING;
                        default:
                            return null;
                    }
                });
    }

    private static IEntityModel newEntityModel(List<IColumnModel> columns) {
        Map<String, IColumnModel> byName = new LinkedHashMap<>();
        for (IColumnModel col : columns)
            byName.put(col.getName(), col);
        return (IEntityModel) Proxy.newProxyInstance(TestOrmBatchConsumer.class.getClassLoader(),
                new Class[]{IEntityModel.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        // default 方法同样路由到 handler——兜底 null 会使 keyCols 引用断裂，必须显式返回
                        case "requireColumn":
                            return byName.get(args[0]);
                        case "getColumns":
                            return new ArrayList<IColumnModel>(columns);
                        default:
                            return null;
                    }
                });
    }

    /** DaoFake：记录 batchGet/findAll/save/update 调用。 */
    private static final class DaoFake {
        final List<String> pkNames;
        final IEntityModel entityModel;
        final Map<Object, IOrmEntity> existingById;
        final List<IOrmEntity> foundByQuery;
        final Collection<Object> batchGetIds = new ArrayList<>();
        final AtomicReference<QueryBean> capturedQuery = new AtomicReference<>();
        final List<Object> savedEntities = new ArrayList<>();
        final List<Object> updatedEntities = new ArrayList<>();
        int newEntityCount;

        DaoFake(List<String> pkNames, IEntityModel entityModel,
                Map<Object, IOrmEntity> existingById, List<IOrmEntity> foundByQuery) {
            this.pkNames = pkNames;
            this.entityModel = entityModel;
            this.existingById = existingById;
            this.foundByQuery = foundByQuery;
        }

        @SuppressWarnings("unchecked")
        IOrmEntityDao<IOrmEntity> proxy() {
            return (IOrmEntityDao<IOrmEntity>) Proxy.newProxyInstance(TestOrmBatchConsumer.class.getClassLoader(),
                    new Class[]{IOrmEntityDao.class},
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "getPkColumnNames":
                                return pkNames;
                            case "getEntityModel":
                                return entityModel;
                            case "batchGetEntityMapByIds":
                                batchGetIds.addAll((Collection<Object>) args[0]);
                                return existingById;
                            case "findAllByQuery":
                                capturedQuery.set((QueryBean) args[0]);
                                return foundByQuery;
                            case "newEntity":
                                newEntityCount++;
                                return newEntityProxy(entityModel);
                            case "saveEntity":
                                savedEntities.add(args[0]);
                                return null;
                            case "updateEntity":
                                updatedEntities.add(args[0]);
                                return null;
                            case "castId":
                                return args[0];
                            default:
                                return null;
                        }
                    });
        }
    }

    private static IOrmEntity newEntityProxy(IEntityModel entityModel) {
        Object[] propValues = new Object[COL_NAMES.length];
        return (IOrmEntity) Proxy.newProxyInstance(TestOrmBatchConsumer.class.getClassLoader(),
                new Class[]{IOrmEntity.class, TestEntityBean.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "orm_entityModel":
                            return entityModel;
                        case "orm_propValue":
                            if (args.length == 2) {
                                propValues[(Integer) args[0]] = args[1];
                                if ((Integer) args[0] == 0)
                                    propValues[0] = args[1];
                                return null;
                            }
                            return propValues[(Integer) args[0]];
                        case "orm_id":
                            return propValues[0];
                        case "getId":
                            return propValues[0];
                        case "getColA":
                            return propValues[1];
                        case "getColB":
                            return propValues[2];
                        default:
                            return null;
                    }
                });
    }

    /** item bean：真实 POJO（BeanTool 面向真实类反射）。 */
    public static final class Item {
        private final String id;
        private final String colA;
        private final String colB;

        public Item(String id, String colA, String colB) {
            this.id = id;
            this.colA = colA;
            this.colB = colB;
        }

        public String getId() {
            return id;
        }

        public String getColA() {
            return colA;
        }

        public String getColB() {
            return colB;
        }
    }

    private static IBatchChunkContext newContext(AtomicInteger insertCount, AtomicInteger updateCount) {
        IBatchTaskContext taskCtx = (IBatchTaskContext) Proxy.newProxyInstance(
                TestOrmBatchConsumer.class.getClassLoader(),
                new Class[]{IBatchTaskContext.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "incInsertCount":
                            insertCount.addAndGet((Integer) args[0]);
                            return null;
                        case "incUpdateCount":
                            updateCount.addAndGet((Integer) args[0]);
                            return null;
                        default:
                            return null;
                    }
                });
        return (IBatchChunkContext) Proxy.newProxyInstance(TestOrmBatchConsumer.class.getClassLoader(),
                new Class[]{IBatchChunkContext.class},
                (proxy, method, args) -> method.getName().equals("getTaskContext") ? taskCtx : null);
    }

    private static List<IColumnModel> defaultColumns() {
        return Arrays.asList(newColumn(0), newColumn(1), newColumn(2));
    }

    private static IOrmEntity entityOf(IEntityModel entityModel, String id, String colA, String colB) {
        IOrmEntity entity = newEntityProxy(entityModel);
        setProps(entity, id, colA, colB);
        return entity;
    }

    private static void setProps(IOrmEntity entity, String id, String colA, String colB) {
        entity.orm_propValue(0, id);
        entity.orm_propValue(1, colA);
        entity.orm_propValue(2, colB);
    }

    @Test
    public void testIdKeyPathBatchGetThenInsertAndUpdate() {
        List<IColumnModel> columns = defaultColumns();
        IEntityModel entityModel = newEntityModel(columns);
        IOrmEntity existing = entityOf(entityModel, "1", "oldA", "oldB");

        DaoFake dao = new DaoFake(List.of("id"), entityModel,
                Map.of("1", existing), Collections.emptyList());
        AtomicInteger inserts = new AtomicInteger();
        AtomicInteger updates = new AtomicInteger();

        OrmBatchConsumer<IOrmEntity, Item> consumer = new OrmBatchConsumer<>(dao.proxy(),
                List.of("id"), true, true);
        consumer.consume(Arrays.asList(new Item("1", "newA", "newB"), new Item("2", "x", "y")),
                newContext(inserts, updates));

        // useIdAsKey=true：走 batchGetEntityMapByIds，不走 findAllByQuery
        assertEquals(Set.of("1", "2"), Set.copyOf(dao.batchGetIds), "id path must batchGet by key set");
        assertNull(dao.capturedQuery.get(), "id path must not issue query");

        assertEquals(1, dao.updatedEntities.size());
        assertSame(existing, dao.updatedEntities.get(0), "hit must update the loaded entity instance");
        assertEquals("newA", existing.orm_propValue(1), "assignEntity must copy item props");
        assertEquals(1, dao.savedEntities.size());
        assertEquals(1, dao.newEntityCount);
        assertEquals(1, inserts.get());
        assertEquals(1, updates.get());
    }

    @Test
    public void testCompositeKeyOrAndFilterShapeAndMatchedUpdate() {
        List<IColumnModel> columns = defaultColumns();
        IEntityModel entityModel = newEntityModel(columns);
        // 命中 (a1,b1) 的既有实体
        IOrmEntity hit = entityOf(entityModel, "k1", "a1", "b1");

        DaoFake dao = new DaoFake(List.of("id"), entityModel,
                Collections.emptyMap(), List.of(hit));
        AtomicInteger inserts = new AtomicInteger();
        AtomicInteger updates = new AtomicInteger();

        OrmBatchConsumer<IOrmEntity, Item> consumer = new OrmBatchConsumer<>(dao.proxy(),
                List.of("colA", "colB"), true, true);
        consumer.consume(Arrays.asList(new Item("k1", "a1", "b1"), new Item("k2", "a2", "b2")),
                newContext(inserts, updates));

        QueryBean query = dao.capturedQuery.get();
        assertTrue(query != null, "composite key path must issue findAllByQuery");
        assertTrue(dao.batchGetIds.isEmpty(), "non-id path must not call batchGetEntityMapByIds");

        TreeBean filter = query.getFilter();
        assertEquals(FilterBeanConstants.FILTER_OP_OR, filter.getTagName(), "composite keys must OR per-key rows");
        List<TreeBean> ands = filter.getChildren();
        assertEquals(2, ands.size(), "two key rows expected");
        Set<Set<String>> seen = new java.util.HashSet<>();
        for (TreeBean and : ands) {
            assertEquals(FilterBeanConstants.FILTER_OP_AND, and.getTagName());
            List<TreeBean> eqs = and.getChildren();
            assertEquals(2, eqs.size(), "each OR branch must AND both key columns in keyCols order");
            assertEquals(FilterBeanConstants.FILTER_OP_EQ, eqs.get(0).getTagName());
            assertEquals("colA", eqs.get(0).getAttr(FilterBeanConstants.FILTER_ATTR_NAME));
            assertEquals(FilterBeanConstants.FILTER_OP_EQ, eqs.get(1).getTagName());
            assertEquals("colB", eqs.get(1).getAttr(FilterBeanConstants.FILTER_ATTR_NAME));
            seen.add(Set.of(String.valueOf(eqs.get(0).getAttr(FilterBeanConstants.FILTER_ATTR_VALUE)),
                    String.valueOf(eqs.get(1).getAttr(FilterBeanConstants.FILTER_ATTR_VALUE))));
        }
        assertEquals(Set.of(Set.of("a1", "b1"), Set.of("a2", "b2")), seen,
                "OR branches must carry both key rows");

        assertEquals(1, dao.updatedEntities.size());
        assertSame(hit, dao.updatedEntities.get(0), "found entity must be matched via getKeyFromEntity");
        assertEquals("a1", hit.orm_propValue(1));
        assertEquals(1, dao.savedEntities.size());
        assertEquals(1, inserts.get());
        assertEquals(1, updates.get());
    }

    @Test
    public void testSingleNonIdKeyUsesInFilter() {
        List<IColumnModel> columns = defaultColumns();
        IEntityModel entityModel = newEntityModel(columns);

        DaoFake dao = new DaoFake(List.of("id"), entityModel,
                Collections.emptyMap(), Collections.emptyList());
        AtomicInteger inserts = new AtomicInteger();
        AtomicInteger updates = new AtomicInteger();

        OrmBatchConsumer<IOrmEntity, Item> consumer = new OrmBatchConsumer<>(dao.proxy(),
                List.of("colA"), true, false);
        consumer.consume(Arrays.asList(new Item("k1", "a1", "b1"), new Item("k2", "a2", "b2")),
                newContext(inserts, updates));

        QueryBean query = dao.capturedQuery.get();
        assertTrue(query != null);
        TreeBean filter = query.getFilter();
        assertEquals(FilterBeanConstants.FILTER_OP_IN, filter.getTagName(),
                "single non-id key must build an IN filter");
        assertEquals("colA", filter.getAttr(FilterBeanConstants.FILTER_ATTR_NAME));
        Collection<?> values = (Collection<?>) filter.getAttr(FilterBeanConstants.FILTER_ATTR_VALUE);
        assertEquals(Set.of("a1", "a2"), Set.copyOf(values));
    }

    @Test
    public void testAllowInsertFalseSkipsInsert() {
        List<IColumnModel> columns = defaultColumns();
        IEntityModel entityModel = newEntityModel(columns);

        DaoFake dao = new DaoFake(List.of("id"), entityModel,
                new HashMap<>(), Collections.emptyList());
        AtomicInteger inserts = new AtomicInteger();
        AtomicInteger updates = new AtomicInteger();

        OrmBatchConsumer<IOrmEntity, Item> consumer = new OrmBatchConsumer<>(dao.proxy(),
                List.of("id"), false, true);
        consumer.consume(List.of(new Item("9", "a", "b")), newContext(inserts, updates));

        assertEquals(0, dao.savedEntities.size(), "allowInsert=false must not insert on miss");
        assertEquals(0, inserts.get());
        assertEquals(0, updates.get());
    }

    @Test
    public void testAllowUpdateFalseSkipsUpdate() {
        List<IColumnModel> columns = defaultColumns();
        IEntityModel entityModel = newEntityModel(columns);
        IOrmEntity existing = entityOf(entityModel, "1", "oldA", "oldB");

        DaoFake dao = new DaoFake(List.of("id"), entityModel,
                Map.of("1", existing), Collections.emptyList());
        AtomicInteger inserts = new AtomicInteger();
        AtomicInteger updates = new AtomicInteger();

        OrmBatchConsumer<IOrmEntity, Item> consumer = new OrmBatchConsumer<>(dao.proxy(),
                List.of("id"), true, false);
        consumer.consume(List.of(new Item("1", "newA", "newB")), newContext(inserts, updates));

        assertEquals(0, dao.updatedEntities.size(), "allowUpdate=false must not update on hit");
        assertEquals(0, dao.savedEntities.size());
        assertEquals(0, updates.get());
        assertEquals(0, inserts.get());
    }

    @Test
    public void testSameObjectSkipsUpdate() {
        List<IColumnModel> columns = defaultColumns();
        IEntityModel entityModel = newEntityModel(columns);
        // item 即查得实体自身：entity == item 必须早退，不产生 update
        IOrmEntity item = entityOf(entityModel, "1", "a", "b");

        DaoFake dao = new DaoFake(List.of("id"), entityModel,
                Map.of("1", item), Collections.emptyList());
        AtomicInteger inserts = new AtomicInteger();
        AtomicInteger updates = new AtomicInteger();

        OrmBatchConsumer<IOrmEntity, IOrmEntity> consumer = new OrmBatchConsumer<>(dao.proxy(),
                List.of("id"), true, true);
        consumer.consume(List.of(item), newContext(inserts, updates));

        assertEquals(0, dao.updatedEntities.size(), "entity==item must early-return without update");
        assertEquals(0, dao.savedEntities.size());
        assertEquals(0, updates.get());
        assertEquals(0, inserts.get());
    }

    @Test
    public void testEmptyChunkZeroCounts() {
        List<IColumnModel> columns = defaultColumns();
        IEntityModel entityModel = newEntityModel(columns);

        DaoFake dao = new DaoFake(List.of("id"), entityModel,
                new HashMap<>(), Collections.emptyList());
        AtomicInteger inserts = new AtomicInteger();
        AtomicInteger updates = new AtomicInteger();

        OrmBatchConsumer<IOrmEntity, Item> consumer = new OrmBatchConsumer<>(dao.proxy(),
                List.of("id"), true, true);
        consumer.consume(Collections.emptyList(), newContext(inserts, updates));

        assertEquals(0, inserts.get());
        assertEquals(0, updates.get());
        assertTrue(dao.batchGetIds.isEmpty());
        assertNull(dao.capturedQuery.get());
    }

    @Test
    public void testItemPropsPropagateToNewEntity() {
        List<IColumnModel> columns = defaultColumns();
        IEntityModel entityModel = newEntityModel(columns);

        DaoFake dao = new DaoFake(List.of("id"), entityModel,
                new HashMap<>(), Collections.emptyList());
        AtomicInteger inserts = new AtomicInteger();
        AtomicInteger updates = new AtomicInteger();

        OrmBatchConsumer<IOrmEntity, Item> consumer = new OrmBatchConsumer<>(dao.proxy(),
                List.of("id"), true, true);
        consumer.consume(List.of(new Item("7", "a7", "b7")), newContext(inserts, updates));

        assertEquals(1, dao.savedEntities.size());
        IOrmEntity saved = (IOrmEntity) dao.savedEntities.get(0);
        assertEquals("7", saved.orm_propValue(0));
        assertEquals("a7", saved.orm_propValue(1));
        assertEquals("b7", saved.orm_propValue(2), "assignEntity must copy every item property");
        assertEquals(1, inserts.get());
        assertEquals(0, updates.get());
    }
}

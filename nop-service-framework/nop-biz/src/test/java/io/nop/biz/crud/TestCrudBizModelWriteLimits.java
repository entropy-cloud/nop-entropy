/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.biz.crud;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.beans.std.StdTreeEntity;
import io.nop.api.core.exceptions.NopException;
import io.nop.biz.BizConstants;
import io.nop.core.context.IServiceContext;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.IOrmEntity;
import io.nop.xlang.xmeta.impl.ObjMetaImpl;
import io.nop.xlang.xmeta.impl.ObjPropMetaImpl;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.nop.biz.BizErrors.ERR_BIZ_BATCH_SIZE_EXCEEDS_LIMIT;
import static io.nop.biz.BizErrors.ERR_BIZ_BY_QUERY_EXCEEDS_LIMIT;
import static io.nop.biz.BizErrors.ERR_BIZ_DICT_OPTIONS_EXCEEDS_LIMIT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2269：批量入参上限（nop.biz.max-batch-size，缺省500，ext:maxBatchSize仅允许抬高）与
 * by-query变更/asDict的前置计数超限抛错。fixture必须注册BizObject+ObjMetaImpl
 * （checkMaxBatchSize与count-first依赖getThisObj()非空）。
 */
public class TestCrudBizModelWriteLimits {

    static final int BATCH_LIMIT = 3;

    // ==================== Phase 1：批量入参上限 ====================

    @Test
    public void testMaxBatchSizeDefaultIs500() {
        LimitFixture f = new LimitFixture();
        assertEquals(500, f.model.getMaxBatchSize());
    }

    @Test
    public void testExtMaxBatchSizeOverrideOnlyRaises() {
        LimitFixture f = new LimitFixture();
        // 低于全局缺省值的覆写不生效（仅允许抬高，与ext:maxPageSize语义一致）
        f.objMeta.setExtProp(BizConstants.EXT_MAX_BATCH_SIZE, 10);
        assertEquals(500, f.model.getMaxBatchSize());

        f.objMeta.setExtProp(BizConstants.EXT_MAX_BATCH_SIZE, 600);
        assertEquals(600, f.model.getMaxBatchSize());
    }

    @Test
    public void testBatchGetThrowsWhenExceedsMaxBatchSize() {
        LimitFixture f = new LimitFixture();
        f.model.maxBatchSizeOverride = BATCH_LIMIT;

        NopException e = assertThrows(NopException.class,
                () -> f.model.batchGet(ids(BATCH_LIMIT + 1), false, f.context()));
        assertEquals(ERR_BIZ_BATCH_SIZE_EXCEEDS_LIMIT.getErrorCode(), e.getErrorCode());
        // 零副作用：超限时不应触发任何DAO加载
        assertEquals(0, f.dao.batchGetCalls);
    }

    @Test
    public void testBatchGetPassesAtExactLimit() {
        LimitFixture f = new LimitFixture();
        f.model.maxBatchSizeOverride = BATCH_LIMIT;

        f.model.batchGet(ids(BATCH_LIMIT), false, f.context());
        assertEquals(1, f.dao.batchGetCalls);
    }

    @Test
    public void testBatchDeleteThrowsWhenExceedsMaxBatchSize() {
        LimitFixture f = new LimitFixture();
        f.model.maxBatchSizeOverride = BATCH_LIMIT;

        NopException e = assertThrows(NopException.class,
                () -> f.model.batchDelete(new HashSet<>(ids(BATCH_LIMIT + 1)), f.context()));
        assertEquals(ERR_BIZ_BATCH_SIZE_EXCEEDS_LIMIT.getErrorCode(), e.getErrorCode());
        assertEquals(0, f.dao.batchGetCalls);
        assertTrue(f.model.deletedIds.isEmpty());
    }

    @Test
    public void testBatchUpdateThrowsWhenExceedsMaxBatchSize() {
        LimitFixture f = new LimitFixture();
        f.model.maxBatchSizeOverride = BATCH_LIMIT;

        NopException e = assertThrows(NopException.class,
                () -> f.model.batchUpdate(new HashSet<>(ids(BATCH_LIMIT + 1)), Map.of("name", "n"), true, f.context()));
        assertEquals(ERR_BIZ_BATCH_SIZE_EXCEEDS_LIMIT.getErrorCode(), e.getErrorCode());
        assertEquals(0, f.dao.batchGetCalls);
        assertTrue(f.model.updates.isEmpty());
    }

    @Test
    public void testBatchModifyThrowsWhenDataOrDelIdsExceeds() {
        LimitFixture f = new LimitFixture();
        f.model.maxBatchSizeOverride = BATCH_LIMIT;

        List<Map<String, Object>> bigData = new ArrayList<>();
        for (int i = 0; i < BATCH_LIMIT + 1; i++)
            bigData.add(Map.of("id", "id-" + i, "name", "n"));

        NopException e = assertThrows(NopException.class,
                () -> f.model.batchModify(bigData, null, null, f.context()));
        assertEquals(ERR_BIZ_BATCH_SIZE_EXCEEDS_LIMIT.getErrorCode(), e.getErrorCode());

        assertThrows(NopException.class,
                () -> f.model.batchModify(null, null, new HashSet<>(ids(BATCH_LIMIT + 1)), f.context()));
        assertEquals(0, f.dao.batchGetCalls);
    }

    @Test
    public void testManyToManyThrowsBeforeEntityLoad() {
        LimitFixture f = new LimitFixture();
        f.model.maxBatchSizeOverride = BATCH_LIMIT;

        NopException e = assertThrows(NopException.class,
                () -> f.model.addManyToManyRelations("1", "roles", ids(BATCH_LIMIT + 1), null, f.context()));
        assertEquals(ERR_BIZ_BATCH_SIZE_EXCEEDS_LIMIT.getErrorCode(), e.getErrorCode());
        // 检查必须位于get(id)之前，超限时不应加载实体
        assertEquals(0, f.dao.getEntityByIdCalls);

        assertThrows(NopException.class,
                () -> f.model.removeManyToManyRelations("1", "roles", ids(BATCH_LIMIT + 1), null, f.context()));
        assertThrows(NopException.class,
                () -> f.model.updateManyToManyRelations("1", "roles", ids(BATCH_LIMIT + 1), null, f.context()));
        assertEquals(0, f.dao.getEntityByIdCalls);
    }

    @Test
    public void testTreePathBypassesBatchLimit() {
        LimitFixture f = new LimitFixture();
        f.model.maxBatchSizeOverride = BATCH_LIMIT;
        f.dao.batchResult = new ArrayList<>();

        // 树查询的idList可合法达到maxPageSize，内部路径走doBatchGet不受批量上限约束
        List<StdTreeEntity> treeList = new ArrayList<>();
        for (int i = 0; i < BATCH_LIMIT * 3; i++) {
            StdTreeEntity node = new StdTreeEntity();
            node.setId("id-" + i);
            treeList.add(node);
        }
        f.model.getEntityListByTreeEntity(treeList, f.context());
        assertEquals(1, f.dao.batchGetCalls);

        f.model.doBatchGet(ids(BATCH_LIMIT * 3), false, f.context());
        assertEquals(2, f.dao.batchGetCalls);
    }

    // ==================== Phase 2：by-query变更前置计数 ====================

    @Test
    public void testDeleteByQueryThrowsWhenCountExceedsLimit() {
        LimitFixture f = new LimitFixture();
        f.dao.countResult = 10;

        QueryBean query = new QueryBean();
        query.setLimit(5);
        NopException e = assertThrows(NopException.class,
                () -> f.model.deleteByQuery(query, f.context()));
        assertEquals(ERR_BIZ_BY_QUERY_EXCEEDS_LIMIT.getErrorCode(), e.getErrorCode());
        // 零副作用：检查在任何变更发生之前完成
        assertEquals(0, f.dao.findPageCalls);
        assertTrue(f.model.deletedIds.isEmpty());
    }

    @Test
    public void testUpdateByQueryThrowsWhenCountExceedsLimit() {
        LimitFixture f = new LimitFixture();
        f.dao.countResult = 10;

        QueryBean query = new QueryBean();
        query.setLimit(5);
        NopException e = assertThrows(NopException.class,
                () -> f.model.updateByQuery(query, Map.of("name", "n"), f.context()));
        assertEquals(ERR_BIZ_BY_QUERY_EXCEEDS_LIMIT.getErrorCode(), e.getErrorCode());
        assertEquals(0, f.dao.findPageCalls);
        assertTrue(f.model.updates.isEmpty());
    }

    @Test
    public void testUpdateByQueryEmptyDataIsNoOpWithoutCountCheck() {
        LimitFixture f = new LimitFixture();
        f.dao.countResult = 10000;

        QueryBean query = new QueryBean();
        query.setLimit(5);
        // 空data与doUpdateByQuery的no-op语义一致：不计数、不抛错、返回0
        int ret = f.model.updateByQuery(query, Map.of(), f.context());
        assertEquals(0, ret);
        assertNull(f.dao.capturedCountQuery);
    }

    @Test
    public void testBatchSizeErrorCarriesParamName() {
        LimitFixture f = new LimitFixture();
        f.model.maxBatchSizeOverride = BATCH_LIMIT;

        NopException e = assertThrows(NopException.class,
                () -> f.model.batchModify(new ArrayList<>(List.of(Map.of("id", "1"))),
                        null, new HashSet<>(ids(BATCH_LIMIT + 1)), f.context()));
        // 错误必须指明超限的是哪个入参（data与delIds分开检查）
        assertEquals("delIds", e.getParam("paramName"));
    }

    @Test
    public void testDeleteByQueryExplicitSmallLimitThrows() {
        LimitFixture f = new LimitFixture();
        f.dao.countResult = 10;

        // 前端显式传入小limit：命中数超过该limit同样属于静默截断，必须抛错
        QueryBean query = new QueryBean();
        query.setLimit(5);
        assertThrows(NopException.class, () -> f.model.deleteByQuery(query, f.context()));
    }

    @Test
    public void testDeleteByQueryPassesWhenCountEqualsLimit() {
        LimitFixture f = new LimitFixture();
        f.dao.countResult = 5;
        f.dao.pageResult = new ArrayList<>();

        QueryBean query = new QueryBean();
        query.setLimit(5);
        int ret = f.model.deleteByQuery(query, f.context());
        // count==limit属于完整执行，放行
        assertEquals(0, ret);
        assertEquals(1, f.dao.findPageCalls);
    }

    @Test
    public void testDeleteByQueryNullQueryChecksAgainstMaxPageSize() {
        LimitFixture f = new LimitFixture();
        f.model.maxPageSizeOverride = 100;
        f.dao.countResult = 101;

        // null query等价于对全表计数，limit归一化为maxPageSize后仍然超限
        NopException e = assertThrows(NopException.class,
                () -> f.model.deleteByQuery(null, f.context()));
        assertEquals(ERR_BIZ_BY_QUERY_EXCEEDS_LIMIT.getErrorCode(), e.getErrorCode());
    }

    @Test
    public void testCountQueryIsCloneNotOriginal() {
        LimitFixture f = new LimitFixture();
        f.dao.countResult = 2;
        f.dao.pageResult = new ArrayList<>();

        QueryBean query = new QueryBean();
        query.setLimit(5);
        query.addFilter(io.nop.api.core.beans.FilterBeans.eq("name", "a"));
        Object originalFilter = query.getFilter();

        f.model.deleteByQuery(query, f.context());

        // 计数使用克隆query：不是原实例，且不污染原query的filter
        assertNotSame(query, f.dao.capturedCountQuery);
        assertSame(originalFilter, query.getFilter());
        assertNull(f.dao.capturedCountQuery.getCursor());
        assertEquals(5, f.dao.capturedCountQuery.getLimit());
    }

    // ==================== Phase 3：asDict字典选项超限 ====================

    @Test
    public void testAsDictThrowsWhenExceedsMaxPageSize() {
        LimitFixture f = new LimitFixture(true);
        f.model.maxPageSizeOverride = 100;
        f.dao.countResult = 101;

        NopException e = assertThrows(NopException.class, () -> f.model.asDict(f.context()));
        assertEquals(ERR_BIZ_DICT_OPTIONS_EXCEEDS_LIMIT.getErrorCode(), e.getErrorCode());
        assertEquals(0, f.dao.findPageCalls);
    }

    @Test
    public void testAsDictPassesWhenWithinLimit() {
        LimitFixture f = new LimitFixture(true);
        f.model.maxPageSizeOverride = 100;
        f.dao.countResult = 2;
        f.dao.pageResult = new ArrayList<>();
        f.dao.pageResult.add(f.entity("id-1").asOrmEntity());
        f.dao.pageResult.add(f.entity("id-2").asOrmEntity());

        io.nop.api.core.beans.DictBean dict = f.model.asDict(f.context());
        assertEquals(2, dict.getOptions().size());
        assertEquals(1, f.dao.findPageCalls);
    }

    // ==================== fixtures ====================

    static List<String> ids(int n) {
        List<String> ret = new ArrayList<>();
        for (int i = 0; i < n; i++)
            ret.add("id-" + i);
        return ret;
    }

    static class LimitFixture {
        final FakeLimitDao dao = new FakeLimitDao();
        final TestWriteLimitsModel model;
        final ObjMetaImpl objMeta;
        final TestCrudBizModelBatchAndTreeQuery.RecordingActionChecker checker =
                new TestCrudBizModelBatchAndTreeQuery.RecordingActionChecker();

        LimitFixture() {
            this(false);
        }

        LimitFixture(boolean dictTag) {
            objMeta = new ObjMetaImpl();
            objMeta.setBizObjName("TestWriteLimitsObj");
            // name prop供testCountQueryIsCloneNotOriginal的filter校验使用（需标记为可查询）
            ObjPropMetaImpl nameProp = new ObjPropMetaImpl();
            nameProp.setName("name");
            nameProp.setQueryable(true);
            objMeta.setProps(new ArrayList<>(List.of(nameProp)));
            if (dictTag) {
                objMeta.setTagSet(new HashSet<>(Set.of("dict")));
                ObjPropMetaImpl idProp = new ObjPropMetaImpl();
                idProp.setName("id");
                objMeta.setProps(new ArrayList<>(List.of(nameProp, idProp)));
            }

            TestCrudBizModelCrudFlow.TestBizObject bizObject = new TestCrudBizModelCrudFlow.TestBizObject("TestWriteLimitsObj");
            bizObject.objMeta = objMeta;
            TestCrudBizModelCrudFlow.TestBizObjectManager manager = new TestCrudBizModelCrudFlow.TestBizObjectManager();
            manager.register(bizObject);

            model = new TestWriteLimitsModel();
            model.setDaoProvider(new TestCrudBizModelBatchAndTreeQuery.SingleDaoProvider(dao.asDao()));
            model.setBizObjectManager(manager);
            model.setEntityName("TestEntity");
            // checkAllowQuery的filter校验路径依赖crudToolProvider
            CrudToolProvider toolProvider = new CrudToolProvider();
            toolProvider.setDaoProvider(new TestCrudBizModelBatchAndTreeQuery.SingleDaoProvider(dao.asDao()));
            toolProvider.setBizObjectManager(manager);
            model.setCrudToolProvider(toolProvider);
        }

        IServiceContext context() {
            return TestCrudBizModelCrudFlow.serviceContext(checker);
        }

        FakeDictEntity entity(String id) {
            return new FakeDictEntity(id);
        }
    }

    /** 带getId()的实体接口，使BeanTool.getProperty(entity,"id")可反射到属性 */
    interface DictTestEntity extends IOrmEntity {
        String getId();
    }

    @BizModel("TestWriteLimitsObj")
    static class TestWriteLimitsModel extends CrudBizModel<IOrmEntity> {
        final List<Map<String, Object>> updates = new ArrayList<>();
        final List<String> deletedIds = new ArrayList<>();
        Integer maxBatchSizeOverride;
        Integer maxPageSizeOverride;

        @Override
        public int getMaxBatchSize() {
            return maxBatchSizeOverride != null ? maxBatchSizeOverride : super.getMaxBatchSize();
        }

        @Override
        public int getMaxPageSize() {
            return maxPageSizeOverride != null ? maxPageSizeOverride : super.getMaxPageSize();
        }

        @Override
        public IOrmEntity update(Map<String, Object> data, IServiceContext context) {
            updates.add(data);
            return null;
        }

        @Override
        public boolean delete(String id, IServiceContext context) {
            deletedIds.add(id);
            return true;
        }
    }

    static class FakeDictEntity {
        final String id;
        final DictTestEntity proxy;

        FakeDictEntity(String id) {
            this.id = id;
            this.proxy = (DictTestEntity) Proxy.newProxyInstance(
                    TestCrudBizModelWriteLimits.class.getClassLoader(),
                    new Class[]{DictTestEntity.class},
                    this::invoke);
        }

        IOrmEntity asOrmEntity() {
            return proxy;
        }

        private Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "getId":
                case "orm_id":
                case "get_id":
                case "orm_idString":
                    return id;
                case "orm_state":
                    return io.nop.orm.OrmEntityState.MANAGED;
                case "orm_logicalDeleted":
                    return false;
                case "toString":
                    return "FakeDictEntity[" + id + "]";
                case "hashCode":
                    return System.identityHashCode(this);
                case "equals":
                    return proxy == args[0];
                default:
                    return TestCrudBizModelCrudFlow.defaultValue(method.getReturnType());
            }
        }
    }

    static class FakeLimitDao {
        long countResult;
        List<IOrmEntity> batchResult = new ArrayList<>();
        List<IOrmEntity> pageResult = new ArrayList<>();
        int batchGetCalls;
        int getEntityByIdCalls;
        int findPageCalls;
        QueryBean capturedCountQuery;

        IEntityDao<IOrmEntity> asDao() {
            return (IEntityDao<IOrmEntity>) Proxy.newProxyInstance(
                    TestCrudBizModelWriteLimits.class.getClassLoader(),
                    new Class[]{IEntityDao.class},
                    this::invoke);
        }

        private Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "getEntityName":
                    return "TestEntity";
                case "getPkColumnNames":
                    return List.of("id");
                case "getEntityById":
                    getEntityByIdCalls++;
                    return null;
                case "batchGetEntitiesByIds":
                case "tryBatchGetEntitiesByIds":
                case "batchRequireEntitiesByIds":
                    batchGetCalls++;
                    return batchResult;
                case "countByQuery":
                    capturedCountQuery = (QueryBean) args[0];
                    return countResult;
                case "findPageByQuery":
                    findPageCalls++;
                    return pageResult;
                default:
                    return TestCrudBizModelCrudFlow.defaultValue(method.getReturnType());
            }
        }
    }
}

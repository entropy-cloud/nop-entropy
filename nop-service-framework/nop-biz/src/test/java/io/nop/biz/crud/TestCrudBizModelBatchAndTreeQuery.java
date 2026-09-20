package io.nop.biz.crud;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.auth.IDataAuthChecker;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.biz.api.IBizObjectManager;
import io.nop.core.context.IServiceContext;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.dao.exceptions.UnknownEntityException;
import io.nop.orm.IOrmEntity;
import io.nop.orm.OrmEntityState;
import io.nop.xlang.xmeta.impl.ObjMetaImpl;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * batchUpdate/batchDelete对dao返回null元素的防御，以及findTreeEntityList的数据权限action语义。
 */
public class TestCrudBizModelBatchAndTreeQuery {

    // ==================== batchUpdate：dao返回null元素 ====================

    @Test
    public void testBatchUpdateSkipsNullEntityWhenIgnoreUnknown() {
        BatchFixture f = new BatchFixture();
        f.dao.batchResult = Arrays.asList(f.entity("1").asOrmEntity(), null);

        Set<String> ids = new LinkedHashSet<>(Arrays.asList("1", "2"));
        f.model.batchUpdate(ids, Map.<String, Object>of("name", "n"), true, f.context());

        // 修复前：entity为null时在orm_idString处抛NPE
        assertEquals(1, f.model.updates.size());
        assertEquals("1", f.model.updates.get(0).get("id"));
    }

    @Test
    public void testBatchUpdateThrowsUnknownEntityWhenRequire() {
        BatchFixture f = new BatchFixture();
        f.dao.batchResult = Arrays.asList(f.entity("1").asOrmEntity(), null);

        Set<String> ids = new LinkedHashSet<>(Arrays.asList("1", "2"));
        // 修复前抛NPE；修复后按UnknownEntityException规范报错
        assertThrows(UnknownEntityException.class,
                () -> f.model.batchUpdate(ids, Map.<String, Object>of("name", "n"), false, f.context()));
        assertEquals(1, f.model.updates.size());
    }

    // ==================== batchDelete：返回集合不写入null ====================

    @Test
    public void testBatchDeleteMapsMissingIdsBackToInput() {
        BatchFixture f = new BatchFixture();
        FakeBatchEntity missing = f.entity("3");
        missing.state = OrmEntityState.MISSING;
        f.dao.batchResult = Arrays.asList(f.entity("1").asOrmEntity(), null, missing.asOrmEntity());

        Set<String> ids = new LinkedHashSet<>(Arrays.asList("1", "2", "3"));
        Set<String> ret = f.model.batchDelete(ids, f.context());

        // 修复前：null实体被映射为返回集合中的null元素
        assertFalse(ret.contains(null));
        assertEquals(new LinkedHashSet<>(Arrays.asList("2", "3")), ret);
        assertEquals(List.of("1"), f.model.deletedIds);
    }

    // ==================== findTreeEntityList数据权限action ====================

    @Test
    public void testFindTreeListUsesFindTreeListAuthAction() {
        TreeFixture f = new TreeFixture();
        f.model.findTreeEntityList(null, null, f.context());

        // 修复前：list入口错误地按findTreePage应用数据权限规则
        assertEquals("TestTreeObj.findTreeList", f.model.capturedQueryName);
        assertEquals("findTreeList", f.checker.capturedAction);
    }

    // ==================== fixtures ====================

    static class BatchFixture {
        final FakeBatchDao dao = new FakeBatchDao();
        final TestBatchModel model;
        final RecordingActionChecker checker = new RecordingActionChecker();

        BatchFixture() {
            // batch入口的checkMaxBatchSize依赖getThisObj()非空，需注册BizObject
            ObjMetaImpl objMeta = new ObjMetaImpl();
            objMeta.setBizObjName("TestBatchObj");
            TestCrudBizModelCrudFlow.TestBizObject bizObject = new TestCrudBizModelCrudFlow.TestBizObject("TestBatchObj");
            bizObject.objMeta = objMeta;
            TestCrudBizModelCrudFlow.TestBizObjectManager manager = new TestCrudBizModelCrudFlow.TestBizObjectManager();
            manager.register(bizObject);

            model = new TestBatchModel();
            model.setDaoProvider(new SingleDaoProvider(dao.asDao()));
            model.setBizObjectManager(manager);
            model.setEntityName("TestEntity");
            CrudToolProvider toolProvider = new CrudToolProvider();
            toolProvider.setDaoProvider(new SingleDaoProvider(dao.asDao()));
            toolProvider.setBizObjectManager(new TestCrudBizModelCrudFlow.TestBizObjectManager());
            model.setCrudToolProvider(toolProvider);
        }

        IServiceContext context() {
            return TestCrudBizModelCrudFlow.serviceContext(null);
        }

        FakeBatchEntity entity(String id) {
            FakeBatchEntity entity = new FakeBatchEntity(id);
            dao.entities.put(id, entity);
            return entity;
        }
    }

    @BizModel("TestBatchObj")
    static class TestBatchModel extends CrudBizModel<IOrmEntity> {
        final List<Map<String, Object>> updates = new ArrayList<>();
        final List<String> deletedIds = new ArrayList<>();

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

    static class TreeFixture {
        final RecordingActionChecker checker = new RecordingActionChecker();
        final TestTreeModel model;

        TreeFixture() {
            ObjMetaImpl objMeta = new ObjMetaImpl();
            objMeta.setBizObjName("TestTreeObj");
            TestCrudBizModelCrudFlow.TestBizObject bizObject = new TestCrudBizModelCrudFlow.TestBizObject("TestTreeObj");
            bizObject.objMeta = objMeta;

            TestCrudBizModelCrudFlow.TestBizObjectManager manager = new TestCrudBizModelCrudFlow.TestBizObjectManager();
            manager.register(bizObject);

            FakeBatchDao dao = new FakeBatchDao();
            model = new TestTreeModel();
            model.setDaoProvider(new SingleDaoProvider(dao.asDao()));
            model.setBizObjectManager(manager);
            model.setEntityName("TestTreeEntity");
            model.checker = this.checker;
        }

        IServiceContext context() {
            return TestCrudBizModelCrudFlow.serviceContext(checker);
        }
    }

    @BizModel("TestTreeObj")
    static class TestTreeModel extends CrudBizModel<IOrmEntity> {
        RecordingActionChecker checker;
        String capturedQueryName;

        @Override
        public int getMaxPageSize() {
            return 100;
        }

        @Override
        protected List<io.nop.api.core.beans.std.StdTreeEntity> getTreeEntityList(QueryBean query) {
            capturedQueryName = query.getName();
            return new ArrayList<>();
        }
    }

    static class RecordingActionChecker implements IDataAuthChecker {
        String capturedAction;

        @Override
        public boolean isPermitted(String bizObj, String action, Object entity,
                                   io.nop.api.core.auth.ISecurityContext context) {
            return true;
        }

        @Override
        public TreeBean getFilter(String bizObj, String action,
                                  io.nop.api.core.auth.ISecurityContext context) {
            capturedAction = action;
            return null;
        }
    }

    static class SingleDaoProvider implements IDaoProvider {
        private final IEntityDao<?> entityDao;

        SingleDaoProvider(IEntityDao<?> entityDao) {
            this.entityDao = entityDao;
        }

        @Override
        public Set<String> getEntityNames() {
            return java.util.Collections.singleton("TestEntity");
        }

        @Override
        public String normalizeEntityName(String entityName) {
            return entityName;
        }

        @Override
        public boolean hasDao(String entityName) {
            return true;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T extends io.nop.dao.api.IDaoEntity> IEntityDao<T> dao(String entityName) {
            return (IEntityDao<T>) entityDao;
        }

        @Override
        public <T extends io.nop.dao.api.IDaoEntity> IEntityDao<T> daoForTable(String tableName) {
            return dao(tableName);
        }
    }

    static class FakeBatchDao {
        final Map<String, FakeBatchEntity> entities = new java.util.LinkedHashMap<>();
        List<IOrmEntity> batchResult = new ArrayList<>();

        IEntityDao<IOrmEntity> asDao() {
            return (IEntityDao<IOrmEntity>) Proxy.newProxyInstance(
                    TestCrudBizModelBatchAndTreeQuery.class.getClassLoader(),
                    new Class[]{IEntityDao.class},
                    this::invoke);
        }

        private Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "getEntityName":
                    return "TestEntity";
                case "getPkColumnNames":
                    return java.util.Collections.singletonList("id");
                case "getEntityById":
                    FakeBatchEntity entity = entities.get(args[0]);
                    return entity == null ? null : entity.asOrmEntity();
                case "batchGetEntitiesByIds":
                case "tryBatchGetEntitiesByIds":
                    return batchResult;
                default:
                    return TestCrudBizModelCrudFlow.defaultValue(method.getReturnType());
            }
        }
    }

    static class FakeBatchEntity {
        final String id;
        OrmEntityState state = OrmEntityState.MANAGED;
        final IOrmEntity proxy;

        FakeBatchEntity(String id) {
            this.id = id;
            this.proxy = (IOrmEntity) Proxy.newProxyInstance(
                    TestCrudBizModelBatchAndTreeQuery.class.getClassLoader(),
                    new Class[]{IOrmEntity.class}, this::invoke);
        }

        IOrmEntity asOrmEntity() {
            return proxy;
        }

        private Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "orm_id":
                case "get_id":
                case "orm_idString":
                    return id;
                case "orm_state":
                    return state;
                case "orm_logicalDeleted":
                    return state == OrmEntityState.DELETED;
                case "toString":
                    return "FakeBatchEntity[" + id + "]";
                case "hashCode":
                    return System.identityHashCode(this);
                case "equals":
                    return proxy == args[0];
                default:
                    return TestCrudBizModelCrudFlow.defaultValue(method.getReturnType());
            }
        }
    }
}

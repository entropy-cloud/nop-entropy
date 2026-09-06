package io.nop.biz.crud;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.core.context.IServiceContext;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.dao.exceptions.UnknownEntityException;
import io.nop.orm.IOrmEntity;
import io.nop.orm.OrmEntityState;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityModel;
import io.nop.xlang.xmeta.impl.ObjMetaImpl;
import io.nop.xlang.xmeta.impl.ObjPropMetaImpl;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * copyForNew在未定义copy-for-new selection时走整实体克隆分支，必须对所有主键列统一清空。
 * 修复前只清空带seq/seq-default标签的主键，非序列主键（业务分配主键）实体克隆后保留源主键，
 * 保存时必然触发主键重复。
 */
public class TestCrudBizModelCopyForNewPk {

    private static final int PK_PROP_ID = 1;

    @Test
    public void testCloneBranchClearsNonSeqPrimaryKey() {
        Fixture f = new Fixture();
        // 未配置copy-for-new field selection => 走cloneInstance分支
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", "src-1");
        data.put("name", "copied-name");

        f.model.copyForNew(data, f.context());

        assertEquals(1, f.dao.savedEntities.size());
        IOrmEntity saved = f.dao.savedEntities.get(0);
        // 修复前：非seq主键未被清空，克隆体保留源主键"src-1"
        assertNull(saved.orm_propValue(PK_PROP_ID), "clone branch must clear all pk columns");
        assertEquals("copied-name", saved.orm_propValueByName("name"));
    }

    // ==================== fixture ====================

    static class Fixture {
        final CloneableOrmEntity sourceEntity = new CloneableOrmEntity("src-1");
        final FakeDao dao = new FakeDao();
        final ObjMetaImpl objMeta = new ObjMetaImpl();
        final TestModel model;

        Fixture() {
            objMeta.setBizObjName("TestPkObj");
            objMeta.addProp(plainProp("name"));

            TestCrudBizModelCrudFlow.TestBizObject bizObject = new TestCrudBizModelCrudFlow.TestBizObject("TestPkObj");
            bizObject.objMeta = objMeta;

            TestCrudBizModelCrudFlow.TestBizObjectManager manager = new TestCrudBizModelCrudFlow.TestBizObjectManager();
            manager.register(bizObject);

            dao.entities.put("src-1", sourceEntity);

            model = new TestModel();
            model.setDaoProvider(new FakeDaoProvider(dao.asDao()));
            model.setBizObjectManager(manager);
            model.setEntityName("TestPkEntity");
            CrudToolProvider toolProvider = new CrudToolProvider();
            toolProvider.setDaoProvider(new FakeDaoProvider(dao.asDao()));
            toolProvider.setBizObjectManager(manager);
            model.setCrudToolProvider(toolProvider);
        }

        IServiceContext context() {
            return TestCrudBizModelCrudFlow.serviceContext(null);
        }
    }

    @BizModel("TestPkObj")
    static class TestModel extends CrudBizModel<IOrmEntity> {
        @Override
        public int getMaxPageSize() {
            return 100;
        }
    }

    static ObjPropMetaImpl plainProp(String name) {
        ObjPropMetaImpl prop = new ObjPropMetaImpl();
        prop.setName(name);
        prop.setInsertable(true);
        prop.setUpdatable(true);
        return prop;
    }

    /**
     * 主键列为非序列主键（无seq/seq-default标签），cloneInstance按真实语义保留全部属性（含主键）
     */
    static IEntityModel pkEntityModel() {
        return (IEntityModel) Proxy.newProxyInstance(
                TestCrudBizModelCopyForNewPk.class.getClassLoader(),
                new Class[]{IEntityModel.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getPkColumns":
                            return Collections.singletonList(pkColumn());
                        case "getTenantPropId":
                            return 0;
                        case "getProp":
                        case "requireProp":
                            return null;
                        default:
                            return defaultValue(method.getReturnType());
                    }
                });
    }

    static IColumnModel pkColumn() {
        return (IColumnModel) Proxy.newProxyInstance(
                TestCrudBizModelCopyForNewPk.class.getClassLoader(),
                new Class[]{IColumnModel.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getName":
                            return "id";
                        case "getPropId":
                            return PK_PROP_ID;
                        // 非序列主键：无seq/seq-default标签（boolean默认值即false）
                        default:
                            return defaultValue(method.getReturnType());
                    }
                });
    }

    static class CloneableOrmEntity {
        final Map<String, Object> props = new HashMap<>();
        final IOrmEntity proxy;

        CloneableOrmEntity(String id) {
            if (id != null)
                props.put("id", id);
            this.proxy = (IOrmEntity) Proxy.newProxyInstance(
                    TestCrudBizModelCopyForNewPk.class.getClassLoader(),
                    new Class[]{IOrmEntity.class}, this::invoke);
        }

        IOrmEntity asOrmEntity() {
            return proxy;
        }

        private Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "orm_propValueByName":
                    if (args.length == 1) {
                        return props.get(args[0]);
                    }
                    props.put((String) args[0], args[1]);
                    return null;
                case "orm_propValue":
                    // propId=1为主键列
                    if (args.length == 1) {
                        return Integer.valueOf(PK_PROP_ID).equals(args[0]) ? props.get("id") : null;
                    }
                    if (Integer.valueOf(PK_PROP_ID).equals(args[0])) {
                        props.put("id", args[1]);
                    }
                    return null;
                case "orm_id":
                case "get_id":
                case "orm_idString":
                    return props.get("id");
                case "orm_state":
                    return OrmEntityState.MANAGED;
                case "orm_entityModel":
                    return pkEntityModel();
                case "orm_entityName":
                    return "TestPkEntity";
                case "orm_propId":
                    return -1;
                case "orm_logicalDeleted":
                    return false;
                case "cloneInstance": {
                    // 真实cloneInstance为含主键的深拷贝
                    CloneableOrmEntity copy = new CloneableOrmEntity(null);
                    copy.props.putAll(this.props);
                    return copy.asOrmEntity();
                }
                case "toString":
                    return "CloneableOrmEntity[" + props.get("id") + "]";
                case "hashCode":
                    return System.identityHashCode(this);
                case "equals":
                    return proxy == args[0];
                default:
                    return defaultValue(method.getReturnType());
            }
        }
    }

    static class FakeDao {
        final Map<Object, CloneableOrmEntity> entities = new LinkedHashMap<>();
        final List<IOrmEntity> savedEntities = new ArrayList<>();

        IEntityDao<IOrmEntity> asDao() {
            return (IEntityDao<IOrmEntity>) Proxy.newProxyInstance(
                    TestCrudBizModelCopyForNewPk.class.getClassLoader(),
                    new Class[]{IEntityDao.class}, this::invoke);
        }

        private Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "getEntityName":
                case "getTableName":
                    return "TestPkEntity";
                case "getPkColumnNames":
                    return Collections.singletonList("id");
                case "castId":
                case "castIdList":
                    return args[0];
                case "getEntityById":
                    CloneableOrmEntity entity = entities.get(args[0]);
                    return entity == null ? null : entity.asOrmEntity();
                case "requireEntityById":
                    CloneableOrmEntity required = entities.get(args[0]);
                    if (required == null)
                        throw new UnknownEntityException("TestPkEntity", args[0]);
                    return required.asOrmEntity();
                case "newEntity":
                    return new CloneableOrmEntity(null).asOrmEntity();
                case "saveEntity":
                case "updateEntity":
                    savedEntities.add((IOrmEntity) args[0]);
                    return null;
                case "getEntityModel":
                    return pkEntityModel();
                default:
                    return defaultValue(method.getReturnType());
            }
        }
    }

    static class FakeDaoProvider implements IDaoProvider {
        private final IEntityDao<?> entityDao;

        FakeDaoProvider(IEntityDao<?> entityDao) {
            this.entityDao = entityDao;
        }

        @Override
        public java.util.Set<String> getEntityNames() {
            return Collections.singleton("TestPkEntity");
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

    static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive())
            return null;
        if (type == boolean.class)
            return false;
        if (type == int.class)
            return 0;
        if (type == long.class)
            return 0L;
        if (type == double.class)
            return 0D;
        if (type == float.class)
            return 0F;
        if (type == short.class)
            return (short) 0;
        if (type == byte.class)
            return (byte) 0;
        if (type == char.class)
            return (char) 0;
        return null;
    }
}

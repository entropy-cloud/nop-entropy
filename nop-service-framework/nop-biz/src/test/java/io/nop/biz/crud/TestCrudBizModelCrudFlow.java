package io.nop.biz.crud;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.auth.IDataAuthChecker;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.biz.BizConstants;
import io.nop.biz.api.IBizObject;
import io.nop.biz.api.IBizObjectManager;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.action.IServiceAction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.dao.exceptions.UnknownEntityException;
import io.nop.fsm.execution.IStateMachine;
import io.nop.graphql.core.ast.GraphQLFieldDefinition;
import io.nop.graphql.core.ast.GraphQLObjectDefinition;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.reflection.GraphQLBizModels;
import io.nop.orm.IOrmEntity;
import io.nop.orm.OrmEntityState;
import io.nop.orm.dao.IOrmEntityDao;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.IEntityPropModel;
import io.nop.orm.model.IEntityRelationModel;
import io.nop.orm.model.OrmModelConstants;
import io.nop.xlang.api.XLang;
import io.nop.xlang.xmeta.IObjMeta;
import io.nop.xlang.xmeta.ObjRelationWriteMode;
import io.nop.xlang.xmeta.impl.ObjMetaImpl;
import io.nop.xlang.xmeta.impl.ObjPropMetaImpl;
import io.nop.xlang.xmeta.impl.SchemaImpl;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.nop.biz.BizErrors.ERR_BIZ_NOT_ALLOW_GET_DELETED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 针对CrudBizModel关键流程（copyForNew、多对多数据权限、deleted_get）的单元测试，
 * 全部基于手工fake，不依赖数据库。
 */
public class TestCrudBizModelCrudFlow {

    // ==================== copyForNew：逻辑删除实体 ====================

    @Test
    public void testCopyForNewOnLogicalDeleteEntity() {
        Fixture f = new Fixture();
        f.objMeta.addProp(plainProp("name"));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", "src-1");
        data.put("name", "copied-name");

        IOrmEntity newEntity = f.model.copyForNew(data, f.context());

        // 修复前：findLogicalDeleted按存活源记录id误抛ERR_BIZ_ENTITY_ALREADY_EXISTS
        assertEquals("copied-name", newEntity.orm_propValueByName("name"));
        assertEquals(1, f.dao.savedEntities.size());
        assertNotSame(f.sourceEntity.asOrmEntity(), newEntity);
    }

    // ==================== copyForNew：writeMode=biz关联的延迟动作 ====================

    @Test
    public void testCopyForNewExecutesBizRelationAction() {
        Fixture f = new Fixture();
        f.objMeta.addProp(plainProp("name"));
        f.objMeta.addProp(bizRelationProp("child"));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", "src-1");
        data.put("name", "copied-name");
        data.put("child", "c-1");

        IOrmEntity newEntity = f.model.copyForNew(data, f.context());

        // 修复前：doCopyForNew使用无context/delayedActions的copier，BIZ模式关联被静默丢弃
        assertEquals(1, f.childBizObject.invocations.size());
        assertEquals("update", f.childBizObject.invocations.get(0).action);
        assertEquals("c-1", f.childBizObject.invocations.get(0).request.get("id"));
        // 关联结果回填到新建实体
        assertTrue(newEntity.orm_propValueByName("child") instanceof IOrmEntity);
        assertEquals(1, f.dao.savedEntities.size());
    }

    // ==================== 多对多mutation的update数据权限 ====================

    @Test
    public void testManyToManyMutationsCheckUpdateDataAuth() {
        Fixture f = new Fixture();
        f.objMeta.addProp(manyToManyProp("roles"));
        f.model.recordingTool = new RecordingTool(f.daoProvider);
        f.sourceEntity.put("leftId", "user-1");

        f.model.addManyToManyRelations("1", "roles", List.of("r-1"), null, f.context());
        f.model.removeManyToManyRelations("1", "roles", List.of("r-1"), null, f.context());
        f.model.updateManyToManyRelations("1", "roles", List.of("r-1", "r-2"), null, f.context());

        // 修复前：仅有get行级检查，无update行级检查
        assertEquals(3, f.authChecker.updateChecks);
        assertEquals(3, f.authChecker.getChecks);
        assertEquals(List.of("add", "remove", "update"), f.model.recordingTool.calls);
    }

    // ==================== 无xmeta对象的deleted_get ====================

    @Test
    public void testDeletedGetWithoutObjMetaReturnsBizError() {
        Fixture f = new Fixture();
        f.mainBizObject.objMeta = null;

        // 修复前：isAllowGetDeleted直接解引用null objMeta抛NPE
        NopException ex = assertThrows(NopException.class,
                () -> f.model.deleted_get("1", false, f.context()));
        assertEquals(ERR_BIZ_NOT_ALLOW_GET_DELETED.getErrorCode(), ex.getErrorCode());
    }

    // ==================== fixture ====================

    static class Fixture {
        final FakeOrmEntity sourceEntity = new FakeOrmEntity("src-1");
        final FakeEntityDao dao = new FakeEntityDao();
        final FakeDaoProvider daoProvider = new FakeDaoProvider(dao.asDao());
        final ObjMetaImpl objMeta = new ObjMetaImpl();
        final TestBizObject mainBizObject = new TestBizObject("TestObj");
        final TestBizObject childBizObject = new TestBizObject("ChildBiz");
        final RecordingAuthChecker authChecker = new RecordingAuthChecker();
        final TestCrudBizModel model;

        Fixture() {
            objMeta.setBizObjName("TestObj");
            mainBizObject.objMeta = objMeta;

            dao.entities.put("src-1", sourceEntity);
            dao.entities.put("1", sourceEntity);

            TestBizObjectManager manager = new TestBizObjectManager();
            manager.register(mainBizObject);
            manager.register(childBizObject);

            model = new TestCrudBizModel();
            model.setDaoProvider(daoProvider);
            model.setBizObjectManager(manager);
            model.setEntityName("TestEntity");
            CrudToolProvider toolProvider = new CrudToolProvider();
            toolProvider.setDaoProvider(daoProvider);
            toolProvider.setBizObjectManager(manager);
            model.setCrudToolProvider(toolProvider);
        }

        IServiceContext context() {
            return serviceContext(authChecker);
        }
    }

    @BizModel("TestObj")
    static class TestCrudBizModel extends CrudBizModel<IOrmEntity> {
        RecordingTool recordingTool;

        @Override
        public int getMaxPageSize() {
            return 100;
        }

        @Override
        public <R extends IOrmEntity> ManyToManyTool<R> manyToMany(String relationEntityName,
                                                                   String leftProp, String rightProp) {
            if (recordingTool != null)
                return (ManyToManyTool<R>) recordingTool;
            return super.manyToMany(relationEntityName, leftProp, rightProp);
        }
    }

    static class RecordingTool extends ManyToManyTool<IOrmEntity> {
        final List<String> calls = new ArrayList<>();

        RecordingTool(IDaoProvider daoProvider) {
            super(daoProvider, "NopUserRoleRelation", "rightId", "manyToManyRefProp");
        }

        @Override
        public void addRelations(Object leftValue, Collection<?> rightValues, TreeBean filter) {
            calls.add("add");
        }

        @Override
        public void removeRelations(Object leftValue, Collection<?> rightValues, TreeBean filter) {
            calls.add("remove");
        }

        @Override
        public void updateRelations(Object leftValue, Collection<?> rightValues, TreeBean filter) {
            calls.add("update");
        }
    }

    static class RecordingAuthChecker implements IDataAuthChecker {
        int getChecks;
        int updateChecks;

        @Override
        public boolean isPermitted(String bizObj, String action, Object entity,
                                   io.nop.api.core.auth.ISecurityContext context) {
            if (BizConstants.METHOD_GET.equals(action))
                getChecks++;
            if (BizConstants.METHOD_UPDATE.equals(action))
                updateChecks++;
            return true;
        }

        @Override
        public TreeBean getFilter(String bizObj, String action,
                                  io.nop.api.core.auth.ISecurityContext context) {
            return null;
        }
    }

    static ObjPropMetaImpl plainProp(String name) {
        ObjPropMetaImpl prop = new ObjPropMetaImpl();
        prop.setName(name);
        prop.setInsertable(true);
        prop.setUpdatable(true);
        return prop;
    }

    static ObjPropMetaImpl bizRelationProp(String name) {
        ObjPropMetaImpl prop = plainProp(name);
        prop.setWriteMode(ObjRelationWriteMode.BIZ);
        SchemaImpl schema = new SchemaImpl();
        schema.setBizObjName("ChildBiz");
        prop.setSchema(schema);
        return prop;
    }

    static ObjPropMetaImpl manyToManyProp(String name) {
        ObjPropMetaImpl prop = plainProp(name);
        prop.prop_set(BizConstants.EXT_KIND, BizConstants.PROP_KIND_TO_MANY);
        prop.prop_set(OrmModelConstants.EXT_JOIN_LEFT_PROP, "leftId");
        prop.prop_set(OrmModelConstants.EXT_JOIN_RIGHT_PROP, "rightId");
        prop.prop_set(OrmModelConstants.ORM_MANY_TO_MANY_REF_PROP, "manyToManyRefProp");

        SchemaImpl itemSchema = new SchemaImpl();
        itemSchema.setBizObjName("NopUserRoleRelation");
        SchemaImpl listSchema = new SchemaImpl();
        listSchema.setItemSchema(itemSchema);
        prop.setSchema(listSchema);
        return prop;
    }

    static IServiceContext serviceContext(IDataAuthChecker checker) {
        return (IServiceContext) Proxy.newProxyInstance(
                TestCrudBizModelCrudFlow.class.getClassLoader(),
                new Class[]{IServiceContext.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        switch (method.getName()) {
                            case "getEvalScope":
                                return XLang.newEvalScope();
                            case "getDataAuthChecker":
                                return checker;
                            case "getUserContext":
                                return Proxy.newProxyInstance(
                                        TestCrudBizModelCrudFlow.class.getClassLoader(),
                                        new Class[]{io.nop.api.core.auth.IUserContext.class},
                                        (p, m, a) -> defaultValue(m.getReturnType()));
                            default:
                                return defaultValue(method.getReturnType());
                        }
                    }
                });
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

    static class TestBizObjectManager implements IBizObjectManager {
        final Map<String, IBizObject> objects = new HashMap<>();

        void register(TestBizObject obj) {
            objects.put(obj.getBizObjName(), obj);
        }

        @Override
        public IBizObject getBizObject(String bizObjName) {
            return objects.get(bizObjName);
        }

        @Override
        public Set<String> getBizObjNames() {
            return objects.keySet();
        }

        @Override
        public boolean containsBizObject(String bizObjName) {
            return objects.containsKey(bizObjName);
        }

        @Override
        public void setDynamicBizModels(GraphQLBizModels bizModels) {
        }

        @Override
        public ApiResponse<?> buildResponse(String locale, Object result, IServiceContext rt) {
            return null;
        }

        @Override
        public void clearCache() {
        }
    }

    static class Invocation {
        final String action;
        final Map<String, Object> request;

        Invocation(String action, Map<String, Object> request) {
            this.action = action;
            this.request = request;
        }
    }

    static class TestBizObject implements IBizObject {
        private final String bizObjName;
        IObjMeta objMeta;
        final List<Invocation> invocations = new ArrayList<>();

        TestBizObject(String bizObjName) {
            this.bizObjName = bizObjName;
        }

        @Override
        public String getBizObjName() {
            return bizObjName;
        }

        @Override
        public io.nop.biz.api.IBizModel getBizModel() {
            return null;
        }

        @Override
        public IObjMeta getObjMeta() {
            return objMeta;
        }

        @Override
        public String getEntityName() {
            return "TestEntity";
        }

        @Override
        public Object getExtAttribute(String name) {
            return null;
        }

        @Override
        public boolean isAllowInheritAction(String action) {
            return false;
        }

        @Override
        public Map<String, IServiceAction> getActions() {
            return Collections.emptyMap();
        }

        @Override
        public Map<String, GraphQLFieldDefinition> getOperations() {
            return Collections.emptyMap();
        }

        @Override
        public IStateMachine getStateMachine() {
            return null;
        }

        @Override
        public GraphQLOperationType getOperationType(String action) {
            return null;
        }

        @Override
        public IServiceAction getAction(String action) {
            return null;
        }

        @Override
        public IServiceAction requireAction(String action) {
            return null;
        }

        @Override
        public Object invoke(String action, Object request, FieldSelectionBean selection, IServiceContext context) {
            invocations.add(new Invocation(action, (Map<String, Object>) request));
            return null;
        }

        @Override
        public GraphQLFieldDefinition getOperationDefinition(GraphQLOperationType opType, String name) {
            return null;
        }

        @Override
        public Collection<GraphQLFieldDefinition> getOperationDefinitions(GraphQLOperationType opType) {
            return Collections.emptyList();
        }

        @Override
        public Map<String, GraphQLFieldDefinition> getOperationDefinitions() {
            return Collections.emptyMap();
        }

        @Override
        public GraphQLObjectDefinition getObjectDefinition() {
            return null;
        }

        @Override
        public <T> T asProxy() {
            return null;
        }

        @Override
        public io.nop.api.core.util.SourceLocation getLocation() {
            return null;
        }
    }

    static class FakeDaoProvider implements IDaoProvider {
        private final IEntityDao<?> entityDao;

        FakeDaoProvider(IEntityDao<?> entityDao) {
            this.entityDao = entityDao;
        }

        @Override
        public Set<String> getEntityNames() {
            return Collections.singleton("TestEntity");
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

    static class FakeEntityDao {
        final Map<Object, FakeOrmEntity> entities = new LinkedHashMap<>();
        final List<IOrmEntity> savedEntities = new ArrayList<>();

        IEntityDao<IOrmEntity> asDao() {
            return (IEntityDao<IOrmEntity>) Proxy.newProxyInstance(
                    TestCrudBizModelCrudFlow.class.getClassLoader(),
                    new Class[]{IEntityDao.class, IOrmEntityDao.class},
                    this::invoke);
        }

        private Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "getEntityName":
                case "getTableName":
                    return "TestEntity";
                case "getPkColumnNames":
                    return Collections.singletonList("id");
                case "castId":
                case "castIdList":
                    return args[0];
                case "getEntityById":
                    FakeOrmEntity entity = entities.get(args[0]);
                    return entity == null ? null : entity.asOrmEntity();
                case "requireEntityById":
                    FakeOrmEntity required = entities.get(args[0]);
                    if (required == null)
                        throw new UnknownEntityException("TestEntity", args[0]);
                    return required.asOrmEntity();
                case "loadEntityById":
                    FakeOrmEntity loaded = entities.get(args[0]);
                    if (loaded == null) {
                        loaded = new FakeOrmEntity(String.valueOf(args[0]));
                        entities.put(args[0], loaded);
                    }
                    return loaded.asOrmEntity();
                case "newEntity":
                    return new FakeOrmEntity(null).asOrmEntity();
                case "isUseLogicalDelete":
                    return true;
                case "resetToDefaultValues":
                case "deleteEntity":
                case "batchLoadSelection":
                    return null;
                case "saveEntity":
                case "updateEntity":
                    savedEntities.add((IOrmEntity) args[0]);
                    return null;
                case "getEntityModel":
                    return entityModel();
                default:
                    return defaultValue(method.getReturnType());
            }
        }
    }

    static IEntityModel entityModel() {
        return (IEntityModel) Proxy.newProxyInstance(
                TestCrudBizModelCrudFlow.class.getClassLoader(),
                new Class[]{IEntityModel.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getPkColumns":
                            return Collections.emptyList();
                        case "getTenantPropId":
                            return 0;
                        case "getProp":
                        case "requireProp": {
                            String name = (String) args[0];
                            if ("child".equals(name))
                                return toOneRelation(name);
                            return plainPropModel(name);
                        }
                        default:
                            return defaultValue(method.getReturnType());
                    }
                });
    }

    static IEntityPropModel plainPropModel(String name) {
        return (IEntityPropModel) Proxy.newProxyInstance(
                TestCrudBizModelCrudFlow.class.getClassLoader(),
                new Class[]{IEntityPropModel.class},
                (proxy, method, args) -> {
                    if ("getName".equals(method.getName()))
                        return name;
                    if ("isToOneRelation".equals(method.getName()) || "isToManyRelation".equals(method.getName()))
                        return false;
                    return defaultValue(method.getReturnType());
                });
    }

    static IEntityRelationModel toOneRelation(String name) {
        return (IEntityRelationModel) Proxy.newProxyInstance(
                TestCrudBizModelCrudFlow.class.getClassLoader(),
                new Class[]{IEntityRelationModel.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getName":
                            return name;
                        case "isToOneRelation":
                            return true;
                        case "isToManyRelation":
                            return false;
                        case "getRefEntityName":
                            return "ChildEntity";
                        case "getRefEntityModel":
                        case "getOwnerEntityModel":
                            return entityModel();
                        case "getJoin":
                            return Collections.emptyList();
                        default:
                            return defaultValue(method.getReturnType());
                    }
                });
    }

    static class FakeOrmEntity {
        final String id;
        final Map<String, Object> props = new HashMap<>();
        final IOrmEntity proxy;

        FakeOrmEntity(String id) {
            this.id = id;
            this.proxy = (IOrmEntity) Proxy.newProxyInstance(
                    TestCrudBizModelCrudFlow.class.getClassLoader(),
                    new Class[]{IOrmEntity.class}, this::invoke);
        }

        IOrmEntity asOrmEntity() {
            return proxy;
        }

        void put(String name, Object value) {
            props.put(name, value);
        }

        private Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "orm_propValueByName":
                    if (args.length == 1) {
                        return props.get(args[0]);
                    }
                    props.put((String) args[0], args[1]);
                    return null;
                case "orm_id":
                case "get_id":
                case "orm_idString":
                    return id;
                case "orm_state":
                    return OrmEntityState.MANAGED;
                case "orm_entityModel":
                    return entityModel();
                case "orm_entityName":
                    return "TestEntity";
                case "orm_propId":
                    return -1;
                case "orm_logicalDeleted":
                    return false;
                case "orm_forceLoad":
                case "orm_propValue":
                case "orm_propOldValue":
                    return null;
                case "cloneInstance":
                    return new FakeOrmEntity(null).asOrmEntity();
                case "toString":
                    return "FakeOrmEntity[" + id + "]";
                case "hashCode":
                    return System.identityHashCode(this);
                case "equals":
                    return proxy == args[0];
                default:
                    return defaultValue(method.getReturnType());
            }
        }
    }
}

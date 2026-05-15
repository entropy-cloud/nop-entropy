package io.nop.biz.crud;

import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.beans.ApiResponse;
import io.nop.biz.BizConstants;
import io.nop.biz.api.IBizObject;
import io.nop.biz.api.IBizObjectManager;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.action.IServiceAction;
import io.nop.dao.DaoConstants;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.fsm.execution.IStateMachine;
import io.nop.graphql.core.ast.GraphQLFieldDefinition;
import io.nop.graphql.core.ast.GraphQLObjectDefinition;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.reflection.GraphQLBizModels;
import io.nop.orm.IOrmEntity;
import io.nop.orm.IOrmEntitySet;
import io.nop.orm.OrmEntityState;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.IEntityRelationModel;
import io.nop.xlang.xmeta.IObjPropMeta;
import io.nop.xlang.xmeta.IObjMeta;
import io.nop.xlang.xmeta.IObjSchema;
import io.nop.xlang.xmeta.ObjRelationWriteMode;
import io.nop.xlang.xmeta.impl.ObjConditionExpr;
import io.nop.xlang.xmeta.impl.ObjMetaImpl;
import io.nop.xlang.xmeta.impl.ObjPropMetaImpl;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestOrmEntityCopierWriteMode {

    @Test
    public void testResolveWriteModeUsesRequestForInlineProp() throws Exception {
        OrmEntityCopier copier = new OrmEntityCopier(new FakeDaoProvider(), new NoopBizObjectManager());
        ObjPropMetaImpl propMeta = new ObjPropMetaImpl();
        propMeta.setName("child");
        propMeta.setWriteMode(ObjRelationWriteMode.INLINE);

        Map<String, Object> request = new HashMap<>();
        request.put(BizConstants.PROP_WRITE_MODE + "_child", "link");

        ObjRelationWriteMode mode = (ObjRelationWriteMode) invoke(copier, "resolveWriteMode",
                new Class[]{io.nop.xlang.xmeta.IObjPropMeta.class, Map.class, String.class},
                propMeta, request, "child");

        assertEquals(ObjRelationWriteMode.LINK, mode);
    }

    @Test
    public void testCopyRefEntityInBizModeCollectsDelayedAction() throws Exception {
        FakeDaoProvider daoProvider = new FakeDaoProvider();
        FakeBizObjectManager bizObjectManager = new FakeBizObjectManager();
        OrmEntityCopier copier = new OrmEntityCopier(daoProvider, bizObjectManager);
        List<IDelayedAction> delayedActions = new ArrayList<>();
        copier.setDelayedActions(delayedActions);
        copier.setServiceContext(NoopServiceContext.instance());

        FakeOrmEntity parent = new FakeOrmEntity("parent-1");
        IEntityRelationModel relation = relation("child", true);
        ObjPropMetaImpl propMeta = relationProp("child", ObjRelationWriteMode.BIZ, "ChildBiz");
        ObjMetaImpl objMeta = new ObjMetaImpl();
        objMeta.setBizObjName("ChildBiz");

        Map<String, Object> payload = mapOf("id", "child-1");

        invoke(copier, "copyRefEntity",
                new Class[]{Object.class, Map.class, IOrmEntity.class, IEntityRelationModel.class,
                        FieldSelectionBean.class, io.nop.xlang.xmeta.IObjPropMeta.class,
                        IObjSchema.class, String.class, io.nop.core.lang.eval.IEvalScope.class},
                payload, payload, parent.asOrmEntity(), relation, null, propMeta, objMeta, "ParentBiz", null);

        assertEquals(1, delayedActions.size());
        DelayedRelationAction action = (DelayedRelationAction) delayedActions.get(0);
        assertEquals(ObjRelationWriteMode.BIZ, action.getWriteMode());
        assertEquals(BizConstants.METHOD_UPDATE, action.getBizAction());
        assertSame(parent.asOrmEntity(), action.getParentEntity());
        assertEquals("ChildBiz", action.getTargetBizObjName());
        assertEquals(0, daoProvider.loadByIdCalls.size());
    }

    @Test
    public void testCopyRefEntityInLinkModeLoadsAndAssignsRelation() throws Exception {
        FakeDaoProvider daoProvider = new FakeDaoProvider();
        OrmEntityCopier copier = new OrmEntityCopier(daoProvider, new NoopBizObjectManager());

        FakeOrmEntity parent = new FakeOrmEntity("parent-1");
        IEntityRelationModel relation = relation("child", true);
        ObjPropMetaImpl propMeta = relationProp("child", ObjRelationWriteMode.LINK, null);

        Map<String, Object> payload = mapOf("id", "child-1");

        invoke(copier, "copyRefEntity",
                new Class[]{Object.class, Map.class, IOrmEntity.class, IEntityRelationModel.class,
                        FieldSelectionBean.class, io.nop.xlang.xmeta.IObjPropMeta.class,
                        IObjSchema.class, String.class, io.nop.core.lang.eval.IEvalScope.class},
                payload, payload, parent.asOrmEntity(), relation, null, propMeta, null, "ParentBiz", null);

        assertEquals(List.of("ChildEntity#child-1"), daoProvider.loadByIdCalls);
        assertNotNull(parent.get("child"));
    }

    @Test
    public void testCopyRefEntitySetInBizModeCollectsOneActionPerItem() throws Exception {
        FakeDaoProvider daoProvider = new FakeDaoProvider();
        FakeBizObjectManager bizObjectManager = new FakeBizObjectManager();
        OrmEntityCopier copier = new OrmEntityCopier(daoProvider, bizObjectManager);
        List<IDelayedAction> delayedActions = new ArrayList<>();
        copier.setDelayedActions(delayedActions);
        copier.setServiceContext(NoopServiceContext.instance());

        FakeOrmEntity parent = new FakeOrmEntity("parent-1");
        FakeOrmEntitySet refSet = new FakeOrmEntitySet(parent, "children");
        parent.setRefSet("children", refSet);
        IEntityRelationModel relation = relation("children", false);
        ObjPropMetaImpl propMeta = relationProp("children", ObjRelationWriteMode.BIZ, "ChildBiz");
        ObjMetaImpl objMeta = new ObjMetaImpl();
        objMeta.setBizObjName("ChildBiz");

        List<Map<String, Object>> payload = List.of(
                mapOf("id", "child-1"),
                mapOf(DaoConstants.PROP_CHANGE_TYPE, DaoConstants.CHANGE_TYPE_ADD));

        invoke(copier, "copyRefEntitySet",
                new Class[]{Object.class, Map.class, IOrmEntity.class, IEntityRelationModel.class,
                        FieldSelectionBean.class, io.nop.xlang.xmeta.IObjPropMeta.class,
                        IObjSchema.class, String.class, io.nop.core.lang.eval.IEvalScope.class},
                payload, new HashMap<>(), parent.asOrmEntity(), relation, null, propMeta, objMeta, "ParentBiz", null);

        assertEquals(2, delayedActions.size());
        assertEquals(BizConstants.METHOD_UPDATE, ((DelayedRelationAction) delayedActions.get(0)).getBizAction());
        assertEquals(BizConstants.METHOD_SAVE, ((DelayedRelationAction) delayedActions.get(1)).getBizAction());
        assertTrue(daoProvider.loadByIdCalls.isEmpty());
    }

    @Test
    public void testCopyRefEntitySetInLinkModeSyncsById() throws Exception {
        FakeDaoProvider daoProvider = new FakeDaoProvider();
        OrmEntityCopier copier = new OrmEntityCopier(daoProvider, new NoopBizObjectManager());

        FakeOrmEntity parent = new FakeOrmEntity("parent-1");
        FakeOrmEntitySet refSet = new FakeOrmEntitySet(parent, "children");
        refSet.add(new FakeOrmEntity("old-1").asOrmEntity());
        parent.setRefSet("children", refSet);
        IEntityRelationModel relation = relation("children", false);
        ObjPropMetaImpl propMeta = relationProp("children", ObjRelationWriteMode.LINK, null);

        List<Map<String, Object>> payload = List.of(mapOf("id", "child-1"), mapOf("id", "child-2"));

        invoke(copier, "copyRefEntitySet",
                new Class[]{Object.class, Map.class, IOrmEntity.class, IEntityRelationModel.class,
                        FieldSelectionBean.class, io.nop.xlang.xmeta.IObjPropMeta.class,
                        IObjSchema.class, String.class, io.nop.core.lang.eval.IEvalScope.class},
                payload, new HashMap<>(), parent.asOrmEntity(), relation, null, propMeta, null, "ParentBiz", null);

        assertEquals(List.of("ChildEntity#child-1", "ChildEntity#child-2"), daoProvider.loadByIdCalls);
        assertEquals(2, refSet.size());
    }

    @Test
    public void testChildAutoExprCanAccessParentDuringInlineCopy() {
        FakeDaoProvider daoProvider = new FakeDaoProvider();
        OrmEntityCopier copier = new OrmEntityCopier(daoProvider, new NoopBizObjectManager());

        FakeOrmEntity parent = new FakeOrmEntity("parent-1");
        ObjMetaImpl rootMeta = new ObjMetaImpl();
        ObjPropMetaImpl childProp = relationProp("child", ObjRelationWriteMode.INLINE, null);
        ObjMetaImpl childMeta = new ObjMetaImpl();

        ObjPropMetaImpl derivedFromParent = new ObjPropMetaImpl();
        derivedFromParent.setName("derivedFromParent");
        ObjConditionExpr autoExpr = new ObjConditionExpr();
        autoExpr.setWhen(Collections.singleton(BizConstants.METHOD_SAVE));
        autoExpr.setSource(ctx -> {
            io.nop.core.lang.eval.IEvalScope scope = ctx.getEvalScope();
            IOrmEntity entity = (IOrmEntity) scope.getValue(BizConstants.VAR_ENTITY);
            IOrmEntity owner = (IOrmEntity) entity.orm_propValueByName("parent");
            return owner == null ? "missing" : owner.orm_idString();
        });
        derivedFromParent.setAutoExpr(autoExpr);
        childMeta.addProp(derivedFromParent);
        childProp.setSchema(childMeta);
        rootMeta.addProp(childProp);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("child", new LinkedHashMap<>());

        copier.copyToEntity(payload, parent.asOrmEntity(), null, rootMeta, "ParentBiz",
                BizConstants.METHOD_SAVE, null);

        IOrmEntity child = (IOrmEntity) parent.get("child");
        assertNotNull(child);
        assertSame(parent.asOrmEntity(), child.orm_propValueByName("parent"));
        assertEquals("parent-1", child.orm_propValueByName("derivedFromParent"));
    }

    @Test
    public void testAutoExprUsesChildScopeWithoutOverwritingCallerEntity() {
        ObjMetaImpl objMeta = new ObjMetaImpl();
        ObjPropMetaImpl propMeta = new ObjPropMetaImpl();
        propMeta.setName("computed");
        Map<String, Object> outerEntity = new HashMap<>();
        ObjConditionExpr autoExpr = new ObjConditionExpr();
        autoExpr.setWhen(Collections.singleton(BizConstants.METHOD_SAVE));
        autoExpr.setSource(ctx -> {
            io.nop.core.lang.eval.IEvalScope exprScope = ctx.getEvalScope();
            assertFalse(exprScope == null);
            return exprScope.getValue(BizConstants.VAR_ENTITY) != outerEntity ? "ok" : null;
        });
        propMeta.setAutoExpr(autoExpr);
        objMeta.addProp(propMeta);

        Map<String, Object> entity = new HashMap<>();
        io.nop.core.lang.eval.IEvalScope scope = io.nop.xlang.api.XLang.newEvalScope();
        scope.setLocalValue(BizConstants.VAR_ENTITY, outerEntity);

        AutoExprRunner.runAutoExpr(BizConstants.METHOD_SAVE, entity, Collections.emptyMap(), objMeta, scope, Collections.emptySet());

        assertSame(outerEntity, scope.getValue(BizConstants.VAR_ENTITY));
        assertEquals("ok", entity.get("computed"));
    }

    private static Object invoke(Object target, String methodName, Class<?>[] argTypes, Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName, argTypes);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private static ObjPropMetaImpl relationProp(String name, ObjRelationWriteMode writeMode, String refBizObjName) {
        ObjPropMetaImpl propMeta = new ObjPropMetaImpl();
        propMeta.setName(name);
        propMeta.setWriteMode(writeMode);
        if (refBizObjName != null) {
            propMeta.prop_set("refBizObjName", refBizObjName);
        }
        return propMeta;
    }

    private static IEntityRelationModel relation(String name, boolean toOne) {
        InvocationHandler handler = (proxy, method, args) -> {
            switch (method.getName()) {
                case "getName":
                    return name;
                case "isToOneRelation":
                    return toOne;
                case "isToManyRelation":
                    return !toOne;
                case "getRefEntityName":
                    return "ChildEntity";
                case "getRefPropName":
                    return "parent";
                case "getOwnerEntityModel":
                    return ownerEntityModel();
                case "getRefEntityModel":
                    return refEntityModel();
                case "getKeyProp":
                    return null;
                case "getJoin":
                    return Collections.emptyList();
                case "toString":
                    return "Relation[" + name + "]";
                default:
                    return defaultValue(method.getReturnType());
            }
        };
        return (IEntityRelationModel) Proxy.newProxyInstance(
                TestOrmEntityCopierWriteMode.class.getClassLoader(),
                new Class[]{IEntityRelationModel.class}, handler);
    }

    private static IEntityModel refEntityModel() {
        InvocationHandler handler = (proxy, method, args) -> {
            switch (method.getName()) {
                case "isCompositePk":
                    return false;
                case "getPkColumns":
                    return Collections.singletonList(pkColumn());
                case "toString":
                    return "RefEntityModel";
                default:
                    return defaultValue(method.getReturnType());
            }
        };
        return (IEntityModel) Proxy.newProxyInstance(
                TestOrmEntityCopierWriteMode.class.getClassLoader(),
                new Class[]{IEntityModel.class}, handler);
    }

    private static io.nop.orm.model.IColumnModel pkColumn() {
        InvocationHandler handler = (proxy, method, args) -> {
            switch (method.getName()) {
                case "getName":
                    return "id";
                case "getStdDataType":
                    return io.nop.commons.type.StdDataType.STRING;
                default:
                    return defaultValue(method.getReturnType());
            }
        };
        return (io.nop.orm.model.IColumnModel) Proxy.newProxyInstance(
                TestOrmEntityCopierWriteMode.class.getClassLoader(),
                new Class[]{io.nop.orm.model.IColumnModel.class}, handler);
    }

    private static Map<String, Object> mapOf(String key, Object value) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(key, value);
        return map;
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == double.class) {
            return 0D;
        }
        if (type == float.class) {
            return 0F;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == char.class) {
            return (char) 0;
        }
        return null;
    }

    private static class FakeDaoProvider implements IDaoProvider {
        private final List<String> loadByIdCalls = new ArrayList<>();

        @Override
        public Set<String> getEntityNames() {
            return Collections.singleton("ChildEntity");
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
        public <T extends io.nop.dao.api.IDaoEntity> IEntityDao<T> dao(String entityName) {
            return (IEntityDao<T>) newEntityDao(entityName, loadByIdCalls);
        }

        @Override
        public <T extends io.nop.dao.api.IDaoEntity> IEntityDao<T> daoForTable(String tableName) {
            return dao(tableName);
        }
    }

    private static IEntityDao<IOrmEntity> newEntityDao(String entityName, List<String> loadByIdCalls) {
        InvocationHandler handler = (proxy, method, args) -> {
            switch (method.getName()) {
                case "getEntityName":
                case "getTableName":
                case "getEntityClassName":
                    return entityName;
                case "getPkColumnNames":
                    return Collections.singletonList("id");
                case "castId":
                    return args[0];
                case "castIdList":
                    return new ArrayList<>((Collection<?>) args[0]);
                case "initEntityId":
                case "getEntityId":
                    return ((IOrmEntity) args[0]).get_id();
                case "newEntity":
                    return new FakeOrmEntity(null).asOrmEntity();
                case "loadEntityById":
                case "getEntityById":
                    loadByIdCalls.add(entityName + "#" + args[0]);
                    return new FakeOrmEntity(String.valueOf(args[0])).asOrmEntity();
                default:
                    return defaultValue(method.getReturnType());
            }
        };
        return (IEntityDao<IOrmEntity>) Proxy.newProxyInstance(
                TestOrmEntityCopierWriteMode.class.getClassLoader(),
                new Class[]{IEntityDao.class}, handler);
    }

    private static class FakeOrmEntity {
        private final String id;
        private final Map<String, Object> props = new HashMap<>();
        private final Map<String, FakeOrmEntitySet> refSets = new HashMap<>();
        private final IOrmEntity proxy;

        FakeOrmEntity(String id) {
            this.id = id;
            this.proxy = (IOrmEntity) Proxy.newProxyInstance(
                    TestOrmEntityCopierWriteMode.class.getClassLoader(),
                    new Class[]{IOrmEntity.class, FakeEntityBean.class}, this::invoke);
        }

        IOrmEntity asOrmEntity() { return proxy; }

        Object get(String name) { return props.get(name); }

        void setRefSet(String name, FakeOrmEntitySet set) { refSets.put(name, set); }

        private Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "orm_propValueByName":
                    if (args.length == 1) {
                        return props.get(args[0]);
                    }
                    props.put((String) args[0], args[1]);
                    return null;
                case "orm_refEntitySet":
                    return refSets.get(args[0]).asOrmEntitySet();
                case "orm_entityModel":
                    return ownerEntityModel();
                case "orm_id":
                case "get_id":
                    return id;
                case "orm_idString":
                    return id;
                case "orm_state":
                    return OrmEntityState.MANAGED;
                case "orm_forceLoad":
                    return null;
                case "toString":
                    return "FakeOrmEntity[" + id + "]";
                case "hashCode":
                    return System.identityHashCode(this);
                case "equals":
                    return proxy == args[0];
                default:
                    if (method.getName().startsWith("get") && method.getParameterCount() == 0) {
                        return props.get(decapitalize(method.getName().substring(3)));
                    }
                    if (method.getName().startsWith("is") && method.getParameterCount() == 0) {
                        return props.get(decapitalize(method.getName().substring(2)));
                    }
                    if (method.getName().startsWith("set") && method.getParameterCount() == 1) {
                        props.put(decapitalize(method.getName().substring(3)), args[0]);
                        return null;
                    }
                    return defaultValue(method.getReturnType());
            }
        }
    }

    private interface FakeEntityBean {
        IOrmEntity getParent();

        void setParent(IOrmEntity parent);

        String getDerivedFromParent();

        void setDerivedFromParent(String value);
    }

    private static String decapitalize(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    private static IEntityModel ownerEntityModel() {
        InvocationHandler handler = (proxy, method, args) -> {
            switch (method.getName()) {
                case "getProp":
                    return relation((String) args[0], "child".equals(args[0]));
                case "getTenantPropId":
                    return 0;
                default:
                    return defaultValue(method.getReturnType());
            }
        };
        return (IEntityModel) Proxy.newProxyInstance(
                TestOrmEntityCopierWriteMode.class.getClassLoader(),
                new Class[]{IEntityModel.class}, handler);
    }

    private static class FakeOrmEntitySet {
        private final FakeOrmEntity owner;
        private final String name;
        private final LinkedHashSet<IOrmEntity> values = new LinkedHashSet<>();
        private final IOrmEntitySet<IOrmEntity> proxy;

        FakeOrmEntitySet(FakeOrmEntity owner, String name) {
            this.owner = owner;
            this.name = name;
            this.proxy = (IOrmEntitySet<IOrmEntity>) Proxy.newProxyInstance(
                    TestOrmEntityCopierWriteMode.class.getClassLoader(),
                    new Class[]{IOrmEntitySet.class}, this::invoke);
        }

        IOrmEntitySet<IOrmEntity> asOrmEntitySet() { return proxy; }
        int size() { return values.size(); }
        void add(IOrmEntity entity) { values.add(entity); }

        private Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "orm_owner":
                    return owner.asOrmEntity();
                case "orm_propName":
                    return name;
                case "orm_collectionName":
                    return "ParentEntity@" + name;
                case "orm_forceLoad":
                    return null;
                case "clear":
                    values.clear();
                    return null;
                case "addAll":
                    return values.addAll((Collection<? extends IOrmEntity>) args[0]);
                case "add":
                    return values.add((IOrmEntity) args[0]);
                case "size":
                    return values.size();
                case "iterator":
                    return values.iterator();
                case "toString":
                    return values.toString();
                default:
                    return defaultValue(method.getReturnType());
            }
        }
    }

    private static class FakeBizObjectManager implements IBizObjectManager {
        @Override
        public IBizObject getBizObject(String bizObjName) { return new NoopBizObject(); }
        @Override
        public Set<String> getBizObjNames() { return Collections.singleton("ChildBiz"); }
        @Override
        public boolean containsBizObject(String bizObjName) { return true; }
        @Override
        public void setDynamicBizModels(GraphQLBizModels bizModels) { }
        @Override
        public ApiResponse<?> buildResponse(String locale, Object result, IServiceContext rt) { return null; }
        @Override
        public void clearCache() { }
    }

    private static class NoopBizObjectManager extends FakeBizObjectManager { }

    private static class NoopBizObject implements IBizObject {
        @Override public String getBizObjName() { return "ChildBiz"; }
        @Override public io.nop.biz.api.IBizModel getBizModel() { return null; }
        @Override public IObjMeta getObjMeta() { return null; }
        @Override public String getEntityName() { return "ChildEntity"; }
        @Override public Object getExtAttribute(String name) { return null; }
        @Override public boolean isAllowInheritAction(String action) { return false; }
        @Override public Map<String, IServiceAction> getActions() { return Collections.emptyMap(); }
        @Override public Map<String, GraphQLFieldDefinition> getOperations() { return Collections.emptyMap(); }
        @Override public IStateMachine getStateMachine() { return null; }
        @Override public GraphQLOperationType getOperationType(String action) { return null; }
        @Override public IServiceAction getAction(String action) { return null; }
        @Override public IServiceAction requireAction(String action) { return null; }
        @Override public GraphQLFieldDefinition getOperationDefinition(GraphQLOperationType opType, String name) { return null; }
        @Override public Collection<GraphQLFieldDefinition> getOperationDefinitions(GraphQLOperationType opType) { return Collections.emptyList(); }
        @Override public Map<String, GraphQLFieldDefinition> getOperationDefinitions() { return Collections.emptyMap(); }
        @Override public GraphQLObjectDefinition getObjectDefinition() { return null; }
        @Override public <T> T asProxy() { return null; }
        @Override public io.nop.api.core.util.SourceLocation getLocation() { return null; }
    }

    private static class NoopServiceContext {
        static IServiceContext instance() {
            return (IServiceContext) Proxy.newProxyInstance(
                    TestOrmEntityCopierWriteMode.class.getClassLoader(),
                    new Class[]{IServiceContext.class},
                    (proxy, method, args) -> defaultValue(method.getReturnType()));
        }
    }
}

package io.nop.biz.crud;

import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.biz.BizConstants;
import io.nop.biz.api.IBizModel;
import io.nop.biz.api.IBizObject;
import io.nop.biz.api.IBizObjectManager;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.action.IServiceAction;
import io.nop.fsm.execution.IStateMachine;
import io.nop.api.core.util.SourceLocation;
import io.nop.graphql.core.ast.GraphQLFieldDefinition;
import io.nop.graphql.core.ast.GraphQLObjectDefinition;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.reflection.GraphQLBizModels;
import io.nop.orm.IOrmEntity;
import io.nop.orm.IOrmEntitySet;
import io.nop.orm.OrmConstants;
import io.nop.orm.OrmEntityState;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.IEntityRelationModel;
import io.nop.xlang.xmeta.IObjMeta;
import io.nop.xlang.xmeta.ObjRelationWriteMode;
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
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestCrudBizModelDelayedRelationAction {

    @Test
    public void testToOneUnlinkClearsParentRelation() {
        TestBizObject targetBizObject = new TestBizObject();
        TestCrudBizModel model = newModel(targetBizObject);
        FakeOrmEntity parent = new FakeOrmEntity("parent-1");

        DelayedRelationAction action = newAction(parent, relation("child", true), "child",
                ObjRelationWriteMode.BIZ, "unlink", null);

        model.runAction(action);

        assertNull(parent.get("child"));
        assertTrue(targetBizObject.invocations.isEmpty());
    }

    @Test
    public void testToOneDeleteInvokesTargetAndClearsRelation() {
        TestBizObject targetBizObject = new TestBizObject();
        TestCrudBizModel model = newModel(targetBizObject);
        FakeOrmEntity parent = new FakeOrmEntity("parent-1");
        FakeOrmEntity child = new FakeOrmEntity("child-1");
        targetBizObject.result = child.asOrmEntity();
        parent.put("child", child.asOrmEntity());

        DelayedRelationAction action = newAction(parent, relation("child", true), "child",
                ObjRelationWriteMode.BIZ, BizConstants.METHOD_DELETE, mapOf(OrmConstants.PROP_ID, "child-1"));

        model.runAction(action);

        assertNull(parent.get("child"));
        assertEquals(1, targetBizObject.invocations.size());
        assertEquals(BizConstants.METHOD_DELETE, targetBizObject.invocations.get(0).action);
        assertEquals("child-1", targetBizObject.invocations.get(0).request.get(OrmConstants.PROP_ID));
    }

    @Test
    public void testToManyUnlinkIsNoOp() {
        TestBizObject targetBizObject = new TestBizObject();
        TestCrudBizModel model = newModel(targetBizObject);
        FakeOrmEntity parent = new FakeOrmEntity("parent-1");
        FakeOrmEntitySet set = new FakeOrmEntitySet(parent);
        FakeOrmEntity child = new FakeOrmEntity("child-1");
        set.add(child.asOrmEntity());
        parent.setRefSet("children", set);

        DelayedRelationAction action = newAction(parent, relation("children", false), "children",
                ObjRelationWriteMode.BIZ, "unlink", null);

        model.runAction(action);

        assertEquals(1, set.size());
        assertTrue(targetBizObject.invocations.isEmpty());
    }

    @Test
    public void testToManyDeleteRemovesMatchingEntity() {
        TestBizObject targetBizObject = new TestBizObject();
        TestCrudBizModel model = newModel(targetBizObject);
        FakeOrmEntity parent = new FakeOrmEntity("parent-1");
        FakeOrmEntitySet set = new FakeOrmEntitySet(parent);
        FakeOrmEntity keep = new FakeOrmEntity("keep-1");
        FakeOrmEntity remove = new FakeOrmEntity("remove-1");
        set.add(keep.asOrmEntity());
        set.add(remove.asOrmEntity());
        parent.setRefSet("children", set);

        DelayedRelationAction action = newAction(parent, relation("children", false), "children",
                ObjRelationWriteMode.BIZ, BizConstants.METHOD_DELETE, mapOf(OrmConstants.PROP_ID, "remove-1"));

        model.runAction(action);

        assertEquals(1, set.size());
        assertTrue(set.contains(keep.asOrmEntity()));
        assertFalse(set.contains(remove.asOrmEntity()));
        assertEquals(1, targetBizObject.invocations.size());
        assertEquals(BizConstants.METHOD_DELETE, targetBizObject.invocations.get(0).action);
    }

    @Test
    public void testNonBizWriteModeIsIgnored() {
        TestBizObject targetBizObject = new TestBizObject();
        TestCrudBizModel model = newModel(targetBizObject);
        FakeOrmEntity parent = new FakeOrmEntity("parent-1");

        DelayedRelationAction action = newAction(parent, relation("child", true), "child",
                ObjRelationWriteMode.LINK, BizConstants.METHOD_UPDATE, mapOf(OrmConstants.PROP_ID, "child-1"));

        model.runAction(action);

        assertTrue(targetBizObject.invocations.isEmpty());
        assertNull(parent.get("child"));
    }

    private static TestCrudBizModel newModel(TestBizObject targetBizObject) {
        TestCrudBizModel model = new TestCrudBizModel();
        model.setBizObjectManager(new SingleBizObjectManager(targetBizObject));
        return model;
    }

    private static DelayedRelationAction newAction(FakeOrmEntity parent, IEntityRelationModel relation,
                                                   String propName, ObjRelationWriteMode writeMode,
                                                   String bizAction, Object payload) {
        DelayedRelationAction action = new DelayedRelationAction();
        action.setParentEntity(parent.asOrmEntity());
        action.setRelationModel(relation);
        action.setPropName(propName);
        action.setWriteMode(writeMode);
        action.setBizAction(bizAction);
        action.setTargetBizObjName("ChildBiz");
        action.setPayload(payload);
        return action;
    }

    private static IEntityRelationModel relation(String propName, boolean toOne) {
        InvocationHandler handler = (proxy, method, args) -> {
            switch (method.getName()) {
                case "getName":
                    return propName;
                case "isToOneRelation":
                    return toOne;
                case "isToManyRelation":
                    return !toOne;
                case "getRefEntityName":
                    return "ChildEntity";
                case "getKind":
                    return null;
                case "toString":
                    return "Relation[" + propName + "]";
                default:
                    return defaultValue(method.getReturnType());
            }
        };
        return (IEntityRelationModel) Proxy.newProxyInstance(
                TestCrudBizModelDelayedRelationAction.class.getClassLoader(),
                new Class[]{IEntityRelationModel.class}, handler);
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

    private static Map<String, Object> mapOf(String key, Object value) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(key, value);
        return map;
    }

    private static class TestCrudBizModel extends CrudBizModel<IOrmEntity> {
        void runAction(DelayedRelationAction action) {
            executeDelayedRelationAction(action, null);
        }
    }

    private static class SingleBizObjectManager implements IBizObjectManager {
        private final IBizObject bizObject;

        SingleBizObjectManager(IBizObject bizObject) {
            this.bizObject = bizObject;
        }

        @Override
        public IBizObject getBizObject(String bizObjName) {
            return bizObject;
        }

        @Override
        public Set<String> getBizObjNames() {
            return Collections.singleton("ChildBiz");
        }

        @Override
        public boolean containsBizObject(String bizObjName) {
            return true;
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

    private static class TestBizObject implements IBizObject {
        private final List<Invocation> invocations = new ArrayList<>();
        private Object result;

        @Override
        public String getBizObjName() {
            return "ChildBiz";
        }

        @Override
        public IBizModel getBizModel() {
            return null;
        }

        @Override
        public IObjMeta getObjMeta() {
            return null;
        }

        @Override
        public String getEntityName() {
            return "ChildEntity";
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
            invocations.add(new Invocation(action, (Map<String, Object>) request, selection));
            return result;
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
        public SourceLocation getLocation() {
            return null;
        }
    }

    private static class Invocation {
        private final String action;
        private final Map<String, Object> request;
        private final FieldSelectionBean selection;

        Invocation(String action, Map<String, Object> request, FieldSelectionBean selection) {
            this.action = action;
            this.request = request;
            this.selection = selection;
        }
    }

    private static class FakeOrmEntity {
        private final String id;
        private final Map<String, Object> props = new HashMap<>();
        private final Map<String, FakeOrmEntitySet> refSets = new HashMap<>();
        private final IOrmEntity proxy;

        FakeOrmEntity(String id) {
            this.id = id;
            this.proxy = (IOrmEntity) Proxy.newProxyInstance(
                    TestCrudBizModelDelayedRelationAction.class.getClassLoader(),
                    new Class[]{IOrmEntity.class}, this::invoke);
        }

        IOrmEntity asOrmEntity() {
            return proxy;
        }

        void put(String name, Object value) {
            props.put(name, value);
        }

        Object get(String name) {
            return props.get(name);
        }

        void setRefSet(String name, FakeOrmEntitySet set) {
            refSets.put(name, set);
        }

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
                case "orm_id":
                case "get_id":
                    return id;
                case "orm_idString":
                    return id;
                case "orm_state":
                    return OrmEntityState.MANAGED;
                case "orm_entityModel":
                    return null;
                case "orm_entityName":
                    return "FakeEntity";
                case "orm_propId":
                    return -1;
                case "orm_propOldValue":
                case "orm_propValue":
                    return null;
                case "orm_dirtyOldValues":
                case "orm_dirtyNewValues":
                case "orm_initedValues":
                    return Collections.emptyMap();
                case "orm_forEachDirtyProp":
                case "orm_forEachInitedProp":
                case "orm_clearDirty":
                case "orm_markFullyLoaded":
                case "orm_useOldValues":
                case "orm_unload":
                case "orm_reset":
                case "orm_readonly":
                case "orm_locked":
                case "orm_attach":
                case "orm_detach":
                case "orm_internalSet":
                    return null;
                case "orm_propIdBound":
                    return 0;
                case "orm_dirty":
                case "orm_extDirty":
                case "orm_inited":
                case "orm_fullyLoaded":
                case "orm_attached":
                case "orm_proxy":
                case "orm_logicalDeleted":
                    return false;
                case "orm_tenantId":
                    return null;
                case "cloneInstance":
                    return proxy;
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

    private static class FakeOrmEntitySet {
        private final FakeOrmEntity owner;
        private final LinkedHashSet<IOrmEntity> values = new LinkedHashSet<>();
        private final IOrmEntitySet<IOrmEntity> proxy;

        FakeOrmEntitySet(FakeOrmEntity owner) {
            this.owner = owner;
            this.proxy = (IOrmEntitySet<IOrmEntity>) Proxy.newProxyInstance(
                    TestCrudBizModelDelayedRelationAction.class.getClassLoader(),
                    new Class[]{IOrmEntitySet.class}, this::invoke);
        }

        IOrmEntitySet<IOrmEntity> asOrmEntitySet() {
            return proxy;
        }

        boolean add(IOrmEntity entity) {
            return values.add(entity);
        }

        int size() {
            return values.size();
        }

        boolean contains(IOrmEntity entity) {
            return values.contains(entity);
        }

        private Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "orm_owner":
                    return owner.asOrmEntity();
                case "orm_forceLoad":
                case "orm_clearDirty":
                case "orm_readonly":
                case "orm_sort":
                case "orm_unload":
                case "orm_beginLoad":
                case "orm_internalAdd":
                case "orm_endLoad":
                case "orm_tenantId":
                    return null;
                case "add":
                    return values.add((IOrmEntity) args[0]);
                case "remove":
                    return values.remove(args[0]);
                case "removeAll":
                    return values.removeAll((Collection<?>) args[0]);
                case "clear":
                    values.clear();
                    return null;
                case "iterator":
                    return values.iterator();
                case "size":
                    return values.size();
                case "isEmpty":
                    return values.isEmpty();
                case "contains":
                    return values.contains(args[0]);
                case "toArray":
                    return args == null ? values.toArray() : values.toArray((Object[]) args[0]);
                case "containsAll":
                    return values.containsAll((Collection<?>) args[0]);
                case "addAll":
                    return values.addAll((Collection<? extends IOrmEntity>) args[0]);
                case "retainAll":
                    return values.retainAll((Collection<?>) args[0]);
                case "spliterator":
                    return values.spliterator();
                case "stream":
                    return values.stream();
                case "parallelStream":
                    return values.parallelStream();
                case "forEach":
                    values.forEach((java.util.function.Consumer<? super IOrmEntity>) args[0]);
                    return null;
                case "orm_propName":
                    return "children";
                case "orm_refPropName":
                    return "parent";
                case "orm_refEntityName":
                    return "ChildEntity";
                case "orm_collectionName":
                    return "FakeEntity@children";
                case "orm_newItem":
                    return null;
                case "orm_proxy":
                case "orm_dirty":
                    return false;
                case "orm_removed":
                case "orm_added":
                    return Collections.emptySet();
                case "toString":
                    return values.toString();
                default:
                    return defaultValue(method.getReturnType());
            }
        }
    }
}

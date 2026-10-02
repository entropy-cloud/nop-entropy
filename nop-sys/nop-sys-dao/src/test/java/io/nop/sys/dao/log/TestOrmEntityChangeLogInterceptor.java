package io.nop.sys.dao.log;

import io.nop.orm.IOrmEntity;
import io.nop.orm.IOrmSession;
import io.nop.orm.IOrmTemplate;
import io.nop.orm.dao.IOrmEntityDao;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.OrmModelConstants;
import io.nop.sys.dao.NopSysDaoConstants;
import io.nop.sys.dao.entity.NopSysChangeLog;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.ObjIntConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OrmEntityChangeLogInterceptor 审计语义（纯单元测试，dao/ormTemplate/session/entity/model
 * 全部为反射代理桩）：audit-save/audit 标签门控、版本列与 no-audit 列剔除、脏属性 old/new
 * 值捕获、删除审计写 deleted=0->1、无上下文时 operatorId 回退 "system"、操作名回退默认操作名。
 */
public class TestOrmEntityChangeLogInterceptor {

    static final int PROP_ID_NAME = 1;
    static final int PROP_ID_SECRET = 3;
    static final int PROP_ID_VER = 99;

    /** 拦截器桩：dao() 替换为记录式假 dao，捕获经独立会话落库的审计行 */
    static class TestableInterceptor extends OrmEntityChangeLogInterceptor {
        final List<NopSysChangeLog> saved = new ArrayList<>();
        IEntityModel model;
        Map<String, Object> valuesByName = new HashMap<>();

        @Override
        protected IOrmEntityDao<NopSysChangeLog> dao() {
            return stub(IOrmEntityDao.class, name -> {
                switch (name) {
                    case "newEntity":
                        return (m1, a1) -> new NopSysChangeLog();
                    case "getOrmTemplate":
                        return (m1, a1) -> stub(IOrmTemplate.class, tplMethod -> {
                            if ("runInNewSession".equals(tplMethod)) {
                                return (m2, a2) -> {
                                    Function<IOrmSession, ?> fn = (Function<IOrmSession, ?>) a2[0];
                                    return fn.apply(stub(IOrmSession.class, sessMethod -> {
                                        if ("saveDirectly".equals(sessMethod)) {
                                            return (m3, a3) -> {
                                                saved.add((NopSysChangeLog) a3[0]);
                                                return null;
                                            };
                                        }
                                        return null;
                                    }));
                                };
                            }
                            return null;
                        });
                    default:
                        return null;
                }
            });
        }
    }

    @SuppressWarnings("unchecked")
    static <T> T stub(Class<T> type, Function<String, BiFunction<String, Object[], Object>> dispatcher) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class[]{type},
                (proxy, method, args) -> {
                    BiFunction<String, Object[], Object> handler = dispatcher.apply(method.getName());
                    if (handler != null)
                        return handler.apply(method.getName(), args);
                    return defaultValue(method.getReturnType());
                });
    }

    static Object defaultValue(Class<?> type) {
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

    static IColumnModel column(String name, int propId, String... tags) {
        List<String> tagList = Arrays.asList(tags);
        return stub(IColumnModel.class, methodName -> {
            switch (methodName) {
                case "getName":
                    return (m, a) -> name;
                case "getPropId":
                    return (m, a) -> propId;
                case "containsTag":
                    return (m, a) -> tagList.contains(a[0]);
                default:
                    return null;
            }
        });
    }

    static IEntityModel entityModel(String name, List<String> tags, int versionPropId,
                                    List<IColumnModel> columns, String bizKeyProp) {
        Map<Integer, IColumnModel> byPropId = new HashMap<>();
        for (IColumnModel col : columns)
            byPropId.put(col.getPropId(), col);
        return stub(IEntityModel.class, methodName -> {
            switch (methodName) {
                case "getName":
                    return (m, a) -> name;
                case "getShortName":
                    return (m, a) -> name.substring(name.lastIndexOf('.') + 1);
                case "containsTag":
                    return (m, a) -> tags.contains(a[0]);
                case "getVersionPropId":
                    return (m, a) -> versionPropId;
                case "getColumns":
                    return (m, a) -> columns;
                case "getColumnByPropId":
                    return (m, a) -> byPropId.get((Integer) a[0]);
                case "prop_get":
                    return (m, a) -> OrmModelConstants.ORM_BIZ_KEY_PROP.equals(a[0]) ? bizKeyProp : null;
                default:
                    return null;
            }
        });
    }

    static IOrmEntity entity(IEntityModel model, String idString, Map<Integer, Object> valuesByPropId,
                             Map<String, Object> valuesByName, LinkedHashMap<Integer, Object> dirtyProps) {
        return stub(IOrmEntity.class, methodName -> {
            switch (methodName) {
                case "orm_entityModel":
                    return (m, a) -> model;
                case "orm_idString":
                    return (m, a) -> idString;
                case "orm_propValue":
                    return (m, a) -> valuesByPropId.get((Integer) a[0]);
                case "orm_propValueByName":
                    return (m, a) -> valuesByName.get((String) a[0]);
                case "orm_forEachDirtyProp":
                    return (m, a) -> {
                        ObjIntConsumer<Object> consumer = (ObjIntConsumer<Object>) a[0];
                        dirtyProps.forEach((propId, value) -> consumer.accept(value, propId));
                        return null;
                    };
                default:
                    return null;
            }
        });
    }

    /** 带 audit-save/audit 标签的实体模型 + 名称列/版本列/no-audit 列 */
    static TestableInterceptor newAuditedInterceptor(String entityName) {
        TestableInterceptor interceptor = new TestableInterceptor();
        interceptor.model = entityModel(entityName,
                Arrays.asList(OrmModelConstants.TAG_AUDIT_SAVE, OrmModelConstants.TAG_AUDIT),
                PROP_ID_VER,
                Arrays.asList(column("wi7_name", PROP_ID_NAME),
                        column("ver", PROP_ID_VER),
                        column("secret_col", PROP_ID_SECRET, OrmModelConstants.TAG_NO_AUDIT)),
                "wi7_name");
        interceptor.valuesByName.put("wi7_name", "BIZ-001");
        return interceptor;
    }

    static Map<Integer, Object> currentValues() {
        Map<Integer, Object> values = new HashMap<>();
        values.put(PROP_ID_NAME, "current-name");
        values.put(PROP_ID_VER, 1L);
        values.put(PROP_ID_SECRET, "current-secret");
        return values;
    }

    @Test
    public void testPostSaveWritesOneRowPerAuditedColumn() {
        TestableInterceptor interceptor = newAuditedInterceptor("test.Wi7AuditedEntity");
        IOrmEntity entity = entity(interceptor.model, "id-1", currentValues(),
                interceptor.valuesByName, new LinkedHashMap<>());

        interceptor.postSave(entity);

        // 版本列与 no-audit 列必须被剔除，只有 wi7_name 落一条审计
        assertEquals(1, interceptor.saved.size(),
                "postSave 必须为每个可审计列写恰好一条记录（剔除 version 与 no-audit）");
        NopSysChangeLog log = interceptor.saved.get(0);
        assertEquals("wi7_name", log.getPropName());
        assertEquals("current-name", log.getNewValue());
        assertEquals("save", log.getOperationName(), "postSave 的默认操作名必须是 save");
        assertEquals(NopSysDaoConstants.OPERATION_SAVE, log.getOperationName());
        assertEquals("Wi7AuditedEntity", log.getBizObjName());
        assertEquals("id-1", log.getObjId());
        assertEquals("BIZ-001", log.getBizKey(), "orm:bizKeyProp 指向的业务键必须写入 bizKey");
    }

    @Test
    public void testPostSaveSkipsEntityWithoutAuditSaveTag() {
        TestableInterceptor interceptor = new TestableInterceptor();
        interceptor.model = entityModel("test.PlainEntity", List.of(), 1,
                List.of(column("name", 1)), null);
        IOrmEntity entity = entity(interceptor.model, "id-2", Map.of(1, "x"),
                Map.of(), new LinkedHashMap<>());

        interceptor.postSave(entity);
        assertTrue(interceptor.saved.isEmpty(), "无 audit-save 标签的实体不得产生审计记录");
    }

    @Test
    public void testPostSaveSkipsChangeLogEntityItself() {
        TestableInterceptor interceptor = newAuditedInterceptor(NopSysChangeLog.class.getName());
        IOrmEntity entity = entity(interceptor.model, "id-3", currentValues(),
                interceptor.valuesByName, new LinkedHashMap<>());

        interceptor.postSave(entity);
        assertTrue(interceptor.saved.isEmpty(), "审计实体自身的变化不得再次产生审计记录（防自引用）");
    }

    @Test
    public void testPostUpdateCapturesDirtyPropsAndSkipsVersionProp() {
        TestableInterceptor interceptor = newAuditedInterceptor("test.Wi7AuditedEntity");
        // 脏属性 = 名称列 + 乐观锁列；拦截器必须剔除 version、只保留名称列
        LinkedHashMap<Integer, Object> dirtyProps = new LinkedHashMap<>();
        dirtyProps.put(PROP_ID_NAME, "old-name");
        dirtyProps.put(PROP_ID_VER, 0L);
        IOrmEntity entity = entity(interceptor.model, "id-4", currentValues(),
                interceptor.valuesByName, dirtyProps);

        interceptor.postUpdate(entity);

        assertEquals(1, interceptor.saved.size(),
                "postUpdate 必须只为脏属性写审计，且剔除 version 脏属性");
        NopSysChangeLog log = interceptor.saved.get(0);
        assertEquals("wi7_name", log.getPropName());
        assertEquals("old-name", log.getOldValue(), "更新审计必须记录旧值");
        assertEquals("current-name", log.getNewValue(), "更新审计必须记录新值");
        assertEquals(NopSysDaoConstants.OPERATION_UPDATE, log.getOperationName());
    }

    @Test
    public void testPostDeleteWritesDeletedFlagRow() {
        TestableInterceptor interceptor = newAuditedInterceptor("test.Wi7AuditedEntity");
        IOrmEntity entity = entity(interceptor.model, "id-5", currentValues(),
                interceptor.valuesByName, new LinkedHashMap<>());

        interceptor.postDelete(entity);

        assertEquals(1, interceptor.saved.size(), "postDelete 必须恰好写一条删除审计");
        NopSysChangeLog log = interceptor.saved.get(0);
        assertEquals(NopSysDaoConstants.PROP_DELETED, log.getPropName());
        assertEquals("0", log.getOldValue());
        assertEquals("1", log.getNewValue());
        assertEquals("delete", log.getOperationName());
    }

    @Test
    public void testNoContextFallsBackToSystemOperatorAndDefaultOpName() {
        TestableInterceptor interceptor = new TestableInterceptor();
        interceptor.model = entityModel("test.Wi7CtxEntity",
                List.of(OrmModelConstants.TAG_AUDIT), 1, List.of(column("name", PROP_ID_NAME)), null);
        IOrmEntity entity = entity(interceptor.model, "id-6", Map.of(PROP_ID_NAME, "v"),
                Map.of(), new LinkedHashMap<>());

        interceptor.postDelete(entity);

        assertEquals(1, interceptor.saved.size());
        NopSysChangeLog log = interceptor.saved.get(0);
        assertEquals("system", log.getOperatorId(),
                "无执行上下文（后台任务/消息分发线程）时操作人必须回退为 system");
        assertEquals("delete", log.getOperationName(),
                "无上下文 callOperationName 时操作名必须回退为默认操作名");
    }
}

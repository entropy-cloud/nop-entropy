/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.sys.dao.log;

import io.nop.core.unittest.JunitBaseTestCase;
import io.nop.dao.api.DaoProvider;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.IOrmEntity;
import io.nop.orm.IOrmInterceptor;
import io.nop.orm.IOrmSession;
import io.nop.orm.IOrmTemplate;
import io.nop.orm.dao.IOrmEntityDao;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.OrmModelConstants;
import io.nop.sys.dao.entity.NopSysChangeLog;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 回归覆盖 wi7#4（plan 2306 项 20）：postSave 审计对 null 属性值必须显式记录为
 * 空串（newValue="" 表示"列存在但值为空"），newValue 为 NULL 保留给"记录未初始化"，
 * 两者不得混同。
 */
public class TestOrmEntityChangeLogInterceptorNullMarker extends JunitBaseTestCase {

    static IDaoProvider originalProvider;

    @BeforeAll
    public static void before() {
        originalProvider = DaoProvider.instance();
    }

    @AfterAll
    public static void restore() {
        DaoProvider.registerInstance(originalProvider);
    }

    static Object defaultValue(Method method) {
        Class<?> rt = method.getReturnType();
        if (rt == boolean.class)
            return false;
        if (rt.isPrimitive())
            return 0;
        return null;
    }

    @SuppressWarnings("unchecked")
    static IEntityDao<NopSysChangeLog> capturingDao(AtomicReference<NopSysChangeLog> captured) {
        IOrmSession session = (IOrmSession) Proxy.newProxyInstance(
                TestOrmEntityChangeLogInterceptorNullMarker.class.getClassLoader(),
                new Class[]{IOrmSession.class}, new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        if (method.getName().equals("saveDirectly")) {
                            captured.set((NopSysChangeLog) args[0]);
                            return null;
                        }
                        return defaultValue(method);
                    }
                });

        IOrmTemplate ormTemplate = (IOrmTemplate) Proxy.newProxyInstance(
                TestOrmEntityChangeLogInterceptorNullMarker.class.getClassLoader(),
                new Class[]{IOrmTemplate.class}, new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        if (method.getName().equals("runInNewSession"))
                            return ((Function<IOrmSession, ?>) args[0]).apply(session);
                        return defaultValue(method);
                    }
                });

        return (IEntityDao<NopSysChangeLog>) Proxy.newProxyInstance(
                TestOrmEntityChangeLogInterceptorNullMarker.class.getClassLoader(),
                new Class[]{IEntityDao.class, IOrmEntityDao.class}, new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        switch (method.getName()) {
                            case "newEntity":
                                return new NopSysChangeLog();
                            case "getOrmTemplate":
                                return ormTemplate;
                            default:
                                return defaultValue(method);
                        }
                    }
                });
    }

    @SuppressWarnings("unchecked")
    static IDaoProvider stubProvider(AtomicReference<NopSysChangeLog> captured) {
        return (IDaoProvider) Proxy.newProxyInstance(
                TestOrmEntityChangeLogInterceptorNullMarker.class.getClassLoader(),
                new Class[]{IDaoProvider.class}, new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        if (method.getName().equals("daoFor")
                                && NopSysChangeLog.class.equals(args[0]))
                            return capturingDao(captured);
                        return defaultValue(method);
                    }
                });
    }

    static IColumnModel nullColumn() {
        return (IColumnModel) Proxy.newProxyInstance(
                TestOrmEntityChangeLogInterceptorNullMarker.class.getClassLoader(),
                new Class[]{IColumnModel.class}, new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        switch (method.getName()) {
                            case "getPropId":
                                return 1;
                            case "getName":
                                return "nullableCol";
                            case "containsTag":
                                return false;
                            default:
                                return defaultValue(method);
                        }
                    }
                });
    }

    static IEntityModel auditEntityModel() {
        return (IEntityModel) Proxy.newProxyInstance(
                TestOrmEntityChangeLogInterceptorNullMarker.class.getClassLoader(),
                new Class[]{IEntityModel.class}, new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        switch (method.getName()) {
                            case "containsTag":
                                return OrmModelConstants.TAG_AUDIT_SAVE.equals(args[0]);
                            case "getName":
                                return "io.nop.test.AuditedEntity";
                            case "getShortName":
                                return "AuditedEntity";
                            case "getVersionPropId":
                                return -1;
                            case "getColumns":
                                return Collections.singletonList(nullColumn());
                            case "prop_get":
                                return null;
                            default:
                                return defaultValue(method);
                        }
                    }
                });
    }

    static IOrmEntity entityWithNullColumn() {
        return (IOrmEntity) Proxy.newProxyInstance(
                TestOrmEntityChangeLogInterceptorNullMarker.class.getClassLoader(),
                new Class[]{IOrmEntity.class}, new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        switch (method.getName()) {
                            case "orm_entityModel":
                                return auditEntityModel();
                            case "orm_propValue":
                                return null;
                            case "orm_idString":
                                return "id-1";
                            case "orm_propValueByName":
                                return null;
                            default:
                                return defaultValue(method);
                        }
                    }
                });
    }

    @Test
    public void testPostSaveRecordsEmptyStringForNullColumnValue() {
        AtomicReference<NopSysChangeLog> captured = new AtomicReference<>();
        DaoProvider.registerInstance(stubProvider(captured));

        OrmEntityChangeLogInterceptor interceptor = new OrmEntityChangeLogInterceptor();
        interceptor.postSave(entityWithNullColumn());

        NopSysChangeLog log = captured.get();
        assertNotNull(log, "审计行必须被写出");
        assertEquals("nullableCol", log.getPropName());
        // null 值必须显式记为空串，与"记录未初始化"（NULL）区分
        assertEquals("", log.getNewValue());
    }
}

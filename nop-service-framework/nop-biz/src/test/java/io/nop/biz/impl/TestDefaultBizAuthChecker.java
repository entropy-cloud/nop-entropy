package io.nop.biz.impl;

import io.nop.biz.api.IBizObject;
import io.nop.biz.api.IBizObjectManager;
import io.nop.core.context.IServiceContext;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * checkAuth对objectDefinition为null的biz对象（not-pub标记导致字段定义被清空）
 * 必须按无字段级权限限制放行。修复前objDef.getField直接NPE。
 */
public class TestDefaultBizAuthChecker {

    @Test
    public void testCheckAuthToleratesNullObjectDefinition() {
        DefaultBizAuthChecker checker = new DefaultBizAuthChecker();
        checker.setBizObjectManager(fakeManager(fakeActor("TestObj", null)));

        // 修复前：fieldName非空且objDef为null时在objDef.getField处抛NPE
        boolean ret = checker.checkAuth("TestObj", "1", "someField", serviceContext());

        assertTrue(ret);
    }

    @Test
    public void testCheckAuthWithEmptyFieldNameStillAllowed() {
        DefaultBizAuthChecker checker = new DefaultBizAuthChecker();
        checker.setBizObjectManager(fakeManager(fakeActor("TestObj", null)));

        assertTrue(checker.checkAuth("TestObj", "1", null, serviceContext()));
        assertTrue(checker.checkAuth("TestObj", "1", "", serviceContext()));
    }

    // ==================== fixture ====================

    static IBizObjectManager fakeManager(IBizObject actor) {
        return (IBizObjectManager) Proxy.newProxyInstance(
                TestDefaultBizAuthChecker.class.getClassLoader(), new Class[]{IBizObjectManager.class},
                (p, m, a) -> "getBizObject".equals(m.getName()) ? actor : defaultValue(m.getReturnType()));
    }

    /**
     * @param objDef getObjectDefinition()的返回值；null模拟not-pub被清空字段定义的对象
     */
    static IBizObject fakeActor(String bizObjName, Object objDef) {
        return (IBizObject) Proxy.newProxyInstance(
                TestDefaultBizAuthChecker.class.getClassLoader(), new Class[]{IBizObject.class},
                (p, m, a) -> {
                    switch (m.getName()) {
                        case "getBizObjName":
                            return bizObjName;
                        case "getObjectDefinition":
                            return objDef;
                        case "invoke":
                            return null;
                        default:
                            return defaultValue(m.getReturnType());
                    }
                });
    }

    static IServiceContext serviceContext() {
        return (IServiceContext) Proxy.newProxyInstance(
                TestDefaultBizAuthChecker.class.getClassLoader(), new Class[]{IServiceContext.class},
                (p, m, a) -> defaultValue(m.getReturnType()));
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

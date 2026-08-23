package io.nop.biz.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.action.IServiceAction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.xlang.api.XLang;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static io.nop.biz.BizErrors.ERR_BIZ_ACTION_NO_SVC_CONTEXT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestBizObjectImplMethodInvoke {

    @Test
    public void testTwoArgsCallReportsMissingSvcContext() {
        BizObjectImpl bizObject = new BizObjectImpl("TestObj");
        bizObject.setActions(Collections.singletonMap("find", (request, selection, ctx) -> "ok"));

        Map<String, Object> data = new HashMap<>();
        data.put("id", "1");

        // 修复前：两参(data, selection)调用误报too-many-action-args且为裸IllegalArgumentException
        NopException ex = assertThrows(NopException.class,
                () -> bizObject.method_invoke("find", new Object[]{data, null}, null));
        assertEquals(ERR_BIZ_ACTION_NO_SVC_CONTEXT.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testNoSvcContextInScopeReportsBizError() {
        BizObjectImpl bizObject = new BizObjectImpl("TestObj");
        bizObject.setActions(Collections.singletonMap("find", (IServiceAction) (request, selection, ctx) -> "ok"));

        Map<String, Object> data = new HashMap<>();
        data.put("id", "1");

        IEvalScope scope = XLang.newEvalScope();

        // 修复前：裸IllegalArgumentException拼nop.err字符串
        NopException ex = assertThrows(NopException.class,
                () -> bizObject.method_invoke("find", new Object[]{data}, scope));
        assertEquals(ERR_BIZ_ACTION_NO_SVC_CONTEXT.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testThreeArgsCallSucceeds() {
        BizObjectImpl bizObject = new BizObjectImpl("TestObj");
        bizObject.setActions(Collections.singletonMap("find", (request, selection, ctx) -> "ok"));

        Object result = bizObject.method_invoke("find",
                new Object[]{new HashMap<>(), null, serviceContext()}, null);
        assertEquals("ok", result);
    }

    static IServiceContext serviceContext() {
        return (IServiceContext) java.lang.reflect.Proxy.newProxyInstance(
                TestBizObjectImplMethodInvoke.class.getClassLoader(),
                new Class[]{IServiceContext.class},
                (proxy, method, args) -> {
                    Class<?> type = method.getReturnType();
                    if (!type.isPrimitive())
                        return null;
                    if (type == boolean.class)
                        return false;
                    if (type == long.class)
                        return 0L;
                    if (type == double.class)
                        return 0D;
                    if (type == float.class)
                        return 0F;
                    if (type == void.class)
                        return null;
                    return 0;
                });
    }
}

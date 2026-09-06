package io.nop.biz.makerchecker;

import io.nop.api.core.annotations.biz.BizMakerCheckerMeta;
import io.nop.api.core.util.FutureHelper;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.action.IServiceAction;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * SendForCheckRequest契约要求携带bizObjName（供下游审批记录关联业务对象），
 * 修复前生产端只设置bizMethod，bizObjName恒为null。
 */
public class TestMakerCheckerTryServiceAction {

    @Test
    public void testSendForCheckCarriesBizObjName() {
        RecordingProvider provider = new RecordingProvider();
        BizMakerCheckerMeta meta = new BizMakerCheckerMeta("tryMethod", "cancelMethod");
        IServiceAction action = (request, selection, ctx) -> "try-result";

        MakerCheckerTryServiceAction tryAction = new MakerCheckerTryServiceAction(
                provider, action, meta, "NopAuthUser", "activeUser");

        FutureHelper.syncGet((CompletionStage<Object>) tryAction.invoke(new Object(), null, serviceContext()));

        assertEquals("NopAuthUser", provider.request.getBizObjName());
        assertEquals("activeUser", provider.request.getBizMethod());
        assertEquals("tryMethod", provider.request.getTryMethod());
    }

    // ==================== fixture ====================

    static IServiceContext serviceContext() {
        return (IServiceContext) Proxy.newProxyInstance(
                TestMakerCheckerTryServiceAction.class.getClassLoader(),
                new Class[]{IServiceContext.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        return defaultValue(method.getReturnType());
                    }
                });
    }

    static class RecordingProvider implements IMakerCheckerProvider {
        SendForCheckRequest request;

        @Override
        public boolean isMakerCheckerEnabled(String bizObjName, String bizMethod) {
            return true;
        }

        @Override
        public CompletionStage<String> sendForCheckAsync(SendForCheckRequest request) {
            this.request = request;
            return FutureHelper.success("req-1");
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

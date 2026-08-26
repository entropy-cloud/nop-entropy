package io.nop.biz.proxy;

import io.nop.core.context.IServiceContext;
import io.nop.core.context.action.IServiceAction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.xlang.api.XLang;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * buildFunction的参数分类按IExecutionContext宽匹配，但调用时曾无条件强转IServiceContext，
 * 业务接口形参声明为IEvalContext等父类型时抛ClassCastException。
 */
public class TestBizProxyInvocationHandler {

    @Test
    public void testEvalContextParamAdaptedInsteadOfCCE() {
        ContextCaptureAction action = new ContextCaptureAction();
        Function<Object[], Object> fn = BizProxyInvocationHandler.buildFunction(-1, 0, -1, -1,
                new String[0], action);

        IEvalScope evalScope = XLang.newEvalScope();
        // 修复前：IEvalScope实参被强转IServiceContext抛ClassCastException
        Object ret = fn.apply(new Object[]{evalScope});

        assertEquals("ok", ret);
        // 纯IEvalContext无法适配出IServiceContext时传null，不抛异常
        assertNull(action.capturedContext);
    }

    @Test
    public void testServiceContextParamStillPassedThrough() {
        ContextCaptureAction action = new ContextCaptureAction();
        Function<Object[], Object> fn = BizProxyInvocationHandler.buildFunction(-1, 0, -1, -1,
                new String[0], action);

        IServiceContext svcCtx = serviceContext();
        Object ret = fn.apply(new Object[]{svcCtx});

        assertEquals("ok", ret);
        assertSame(svcCtx, action.capturedContext);
    }

    @Test
    public void testNullContextParamTolerated() {
        ContextCaptureAction action = new ContextCaptureAction();
        Function<Object[], Object> fn = BizProxyInvocationHandler.buildFunction(-1, 0, -1, -1,
                new String[0], action);

        assertEquals("ok", fn.apply(new Object[]{null}));
        assertNull(action.capturedContext);
    }

    // ==================== fixture ====================

    static IServiceContext serviceContext() {
        return (IServiceContext) Proxy.newProxyInstance(
                TestBizProxyInvocationHandler.class.getClassLoader(),
                new Class[]{IServiceContext.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        return defaultValue(method.getReturnType());
                    }
                });
    }

    static class ContextCaptureAction implements IServiceAction {
        IServiceContext capturedContext;

        @Override
        public Object invoke(Object request, io.nop.api.core.beans.FieldSelectionBean selection,
                             IServiceContext context) {
            this.capturedContext = context;
            return "ok";
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

package io.nop.xlang.expr;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopEvalException;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.xlang.api.XLang;
import org.junit.jupiter.api.Test;

import static io.nop.xlang.XLangErrors.ERR_EXEC_INVOKE_METHOD_FAIL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 249 xlang 层单元测试：验证 {@code AbstractObjFunctionExecutable.doInvoke*}（5 个重载）
 * 包装方法调用异常时，被调用方法抛出的 bizFatal {@link NopException} 标记穿透到包装异常。
 *
 * <p>对偶回归：非 bizFatal 异常仍被包装为 {@code NopEvalException(ERR_EXEC_INVOKE_METHOD_FAIL)}，
 * 保留 {@code wrapException=true} / cause / errorCode 全部既有语义，且 {@code isBizFatal()==false}。
 */
public class TestInvokeMethodBizFatalWrap {

    private static final ErrorCode ERR_BIZ_FATAL = ErrorCode.define("nop.err.test.plan-249.biz-fatal",
            "plan 249 test: bizFatal");
    private static final ErrorCode ERR_NOT_FATAL = ErrorCode.define("nop.err.test.plan-249.not-fatal",
            "plan 249 test: not fatal");

    /**
     * 被调用方法抛 bizFatal NopException：经 doInvoke* 包装后，最终抛出异常 isBizFatal()==true。
     * 覆盖全部 5 个 doInvoke* 重载（0/1/2/3/4+ 参数分别走 doInvoke0/1/2/3/invoke）。
     */
    @Test
    public void bizFatalException_propagatedThroughInvokeWrap_allOverloads() {
        // 0 args -> doInvoke0, 1 arg -> doInvoke1, 2 args -> doInvoke2,
        // 3 args -> doInvoke3, 4+ args -> doInvoke (varargs path)
        assertWrappedBizFatal("o.throwBizFatal0()");
        assertWrappedBizFatal("o.throwBizFatal1(1)");
        assertWrappedBizFatal("o.throwBizFatal2(1,2)");
        assertWrappedBizFatal("o.throwBizFatal3(1,2,3)");
        assertWrappedBizFatal("o.throwBizFatal4(1,2,3,4)");
    }

    /**
     * 对偶回归：被调用方法抛非 bizFatal 异常 → 经 doInvoke* 包装 →
     * 最终异常为 NopEvalException、errorCode=ERR_EXEC_INVOKE_METHOD_FAIL、isBizFatal()==false、
     * isWrapException()==true、getCause() 保留原异常。
     */
    @Test
    public void nonBizFatalException_wrappedPreservingSemantics() {
        NopException wrapped = invokeAndCatch("o.throwRecoverable()", null);
        assertTrue(wrapped instanceof NopEvalException, "non-bizFatal should be wrapped as NopEvalException");
        assertEquals(ERR_EXEC_INVOKE_METHOD_FAIL.getErrorCode(), wrapped.getErrorCode(),
                "wrapped errorCode must remain ERR_EXEC_INVOKE_METHOD_FAIL");
        assertFalse(wrapped.isBizFatal(), "non-bizFatal cause must keep isBizFatal()==false");
        assertTrue(wrapped.isWrapException(), "wrapException flag must be preserved");
        assertNotNull(wrapped.getCause(), "cause must be preserved");
        assertEquals(ERR_NOT_FATAL.getErrorCode(), ((NopException) wrapped.getCause()).getErrorCode(),
                "cause must be the original exception");
    }

    private void assertWrappedBizFatal(String expr) {
        BizFatalBean bean = new BizFatalBean();
        NopException wrapped = invokeAndCatch(expr, bean);
        assertTrue(wrapped.isBizFatal(),
                "bizFatal cause must propagate to wrap exception. expr=" + expr);
        assertEquals(ERR_EXEC_INVOKE_METHOD_FAIL.getErrorCode(), wrapped.getErrorCode(),
                "wrapped errorCode must remain ERR_EXEC_INVOKE_METHOD_FAIL (way A: copy flag only)");
        assertTrue(wrapped.isWrapException(), "wrapException flag must be preserved");
        assertNotNull(wrapped.getCause(), "cause must be preserved");
        assertEquals(ERR_BIZ_FATAL.getErrorCode(), ((NopException) wrapped.getCause()).getErrorCode(),
                "cause must be the original bizFatal exception");
        assertEquals(1, bean.callCount.get(),
                "bean method must be invoked exactly once for expr=" + expr);
    }

    private NopException invokeAndCatch(String expr, BizFatalBean bean) {
        IEvalAction action = XLang.newCompileTool().allowUnregisteredScopeVar(true).compileSimpleExpr(null, expr);
        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue("o", bean == null ? new BizFatalBean() : bean);
        try {
            action.invoke(scope);
        } catch (NopException e) {
            return e;
        }
        throw new AssertionError("expression should have thrown: " + expr);
    }

    public static class BizFatalBean {
        public final java.util.concurrent.atomic.AtomicInteger callCount =
                new java.util.concurrent.atomic.AtomicInteger(0);

        public void throwBizFatal0() {
            callCount.incrementAndGet();
            throw new NopException(ERR_BIZ_FATAL).bizFatal(true);
        }

        public void throwBizFatal1(int a) {
            callCount.incrementAndGet();
            throw new NopException(ERR_BIZ_FATAL).bizFatal(true);
        }

        public void throwBizFatal2(int a, int b) {
            callCount.incrementAndGet();
            throw new NopException(ERR_BIZ_FATAL).bizFatal(true);
        }

        public void throwBizFatal3(int a, int b, int c) {
            callCount.incrementAndGet();
            throw new NopException(ERR_BIZ_FATAL).bizFatal(true);
        }

        public void throwBizFatal4(int a, int b, int c, int d) {
            callCount.incrementAndGet();
            throw new NopException(ERR_BIZ_FATAL).bizFatal(true);
        }

        public void throwRecoverable() {
            throw new NopException(ERR_NOT_FATAL);
        }
    }
}

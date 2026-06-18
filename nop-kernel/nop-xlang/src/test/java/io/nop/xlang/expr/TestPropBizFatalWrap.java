package io.nop.xlang.expr;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopEvalException;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.xlang.api.XLang;
import org.junit.jupiter.api.Test;

import static io.nop.xlang.XLangErrors.ERR_EXEC_READ_PROP_FAIL;
import static io.nop.xlang.XLangErrors.ERR_EXEC_WRITE_PROP_FAIL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 251 xlang 层单元测试：验证 {@code AbstractPropertyExecutable.readProp}/{@code setProp}
 * （xpl dot 记法 {@code obj.prop} / {@code obj.prop = v}，编译为
 * {@code GetPropertyExecutable}/{@code SetPropertyExecutable}，调用基类 readProp/setProp）
 * 包装属性读/写异常时，被访问属性的 getter/setter 抛出的 bizFatal {@link NopException}
 * 标记穿透到包装异常。
 *
 * <p>对偶回归：非 bizFatal 异常仍被包装为 {@code NopEvalException(ERR_EXEC_READ_PROP_FAIL /
 * ERR_EXEC_WRITE_PROP_FAIL)}，保留 {@code wrapException=true} / cause / errorCode 全部既有语义，
 * 且 {@code isBizFatal()==false}。
 */
public class TestPropBizFatalWrap {

    private static final ErrorCode ERR_BIZ_FATAL = ErrorCode.define("nop.err.test.plan-251.biz-fatal",
            "plan 251 test: bizFatal");
    private static final ErrorCode ERR_NOT_FATAL = ErrorCode.define("nop.err.test.plan-251.not-fatal",
            "plan 251 test: not fatal");

    /**
     * readProp bizFatal 穿透：bean getter 抛 bizFatal NopException → 经 dot 记法 {@code o.bizFatalProp}
     * → GetPropertyExecutable → 基类 readProp 包装后，最终抛出异常 isBizFatal()==true。
     */
    @Test
    public void readProp_bizFatalException_propagatedThroughWrap() {
        PropBizFatalBean bean = new PropBizFatalBean();
        NopException wrapped = evalAndCatch("o.bizFatalProp", bean);
        assertTrue(wrapped.isBizFatal(),
                "bizFatal getter cause must propagate to wrap exception");
        assertEquals(ERR_EXEC_READ_PROP_FAIL.getErrorCode(), wrapped.getErrorCode(),
                "wrapped errorCode must remain ERR_EXEC_READ_PROP_FAIL (copy flag only)");
        assertTrue(wrapped.isWrapException(), "wrapException flag must be preserved");
        assertNotNull(wrapped.getCause(), "cause must be preserved");
        assertEquals(ERR_BIZ_FATAL.getErrorCode(), ((NopException) wrapped.getCause()).getErrorCode(),
                "cause must be the original bizFatal exception");
        assertEquals(1, bean.readCount.get(),
                "bizFatal getter must be invoked exactly once");
    }

    /**
     * readProp 对偶回归：getter 抛非 bizFatal 异常 → 经 dot 记法 → readProp 包装 →
     * 最终异常为 NopEvalException、errorCode=ERR_EXEC_READ_PROP_FAIL、isBizFatal()==false、
     * isWrapException()==true、getCause() 保留原异常。
     */
    @Test
    public void readProp_nonBizFatalException_wrappedPreservingSemantics() {
        PropBizFatalBean bean = new PropBizFatalBean();
        NopException wrapped = evalAndCatch("o.recoverableProp", bean);
        assertTrue(wrapped instanceof NopEvalException, "non-bizFatal should be wrapped as NopEvalException");
        assertEquals(ERR_EXEC_READ_PROP_FAIL.getErrorCode(), wrapped.getErrorCode(),
                "wrapped errorCode must remain ERR_EXEC_READ_PROP_FAIL");
        assertFalse(wrapped.isBizFatal(), "non-bizFatal cause must keep isBizFatal()==false");
        assertTrue(wrapped.isWrapException(), "wrapException flag must be preserved");
        assertNotNull(wrapped.getCause(), "cause must be preserved");
        assertEquals(ERR_NOT_FATAL.getErrorCode(), ((NopException) wrapped.getCause()).getErrorCode(),
                "cause must be the original exception");
    }

    /**
     * setProp bizFatal 穿透：bean setter 抛 bizFatal NopException → 经 dot 记法 {@code o.bizFatalProp=1}
     * → SetPropertyExecutable → 基类 setProp 包装后，最终抛出异常 isBizFatal()==true。
     */
    @Test
    public void setProp_bizFatalException_propagatedThroughWrap() {
        PropBizFatalBean bean = new PropBizFatalBean();
        NopException wrapped = evalAndCatch("o.bizFatalProp = 1", bean);
        assertTrue(wrapped.isBizFatal(),
                "bizFatal setter cause must propagate to wrap exception");
        assertEquals(ERR_EXEC_WRITE_PROP_FAIL.getErrorCode(), wrapped.getErrorCode(),
                "wrapped errorCode must remain ERR_EXEC_WRITE_PROP_FAIL (copy flag only)");
        assertTrue(wrapped.isWrapException(), "wrapException flag must be preserved");
        assertNotNull(wrapped.getCause(), "cause must be preserved");
        assertEquals(ERR_BIZ_FATAL.getErrorCode(), ((NopException) wrapped.getCause()).getErrorCode(),
                "cause must be the original bizFatal exception");
        assertEquals(1, bean.writeCount.get(),
                "bizFatal setter must be invoked exactly once");
    }

    /**
     * setProp 对偶回归：setter 抛非 bizFatal 异常 → 经 dot 记法 → setProp 包装 →
     * 最终异常 errorCode=ERR_EXEC_WRITE_PROP_FAIL、isBizFatal()==false、isWrapException()==true、
     * getCause() 保留原异常。
     */
    @Test
    public void setProp_nonBizFatalException_wrappedPreservingSemantics() {
        PropBizFatalBean bean = new PropBizFatalBean();
        NopException wrapped = evalAndCatch("o.recoverableProp = 1", bean);
        assertTrue(wrapped instanceof NopEvalException, "non-bizFatal should be wrapped as NopEvalException");
        assertEquals(ERR_EXEC_WRITE_PROP_FAIL.getErrorCode(), wrapped.getErrorCode(),
                "wrapped errorCode must remain ERR_EXEC_WRITE_PROP_FAIL");
        assertFalse(wrapped.isBizFatal(), "non-bizFatal cause must keep isBizFatal()==false");
        assertTrue(wrapped.isWrapException(), "wrapException flag must be preserved");
        assertNotNull(wrapped.getCause(), "cause must be preserved");
        assertEquals(ERR_NOT_FATAL.getErrorCode(), ((NopException) wrapped.getCause()).getErrorCode(),
                "cause must be the original exception");
    }

    private NopException evalAndCatch(String expr, PropBizFatalBean bean) {
        IEvalAction action = XLang.newCompileTool().allowUnregisteredScopeVar(true).compileFullExpr(null, expr);
        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue("o", bean);
        try {
            action.invoke(scope);
        } catch (NopException e) {
            return e;
        }
        throw new AssertionError("expression should have thrown: " + expr);
    }

    public static class PropBizFatalBean {
        public final java.util.concurrent.atomic.AtomicInteger readCount =
                new java.util.concurrent.atomic.AtomicInteger(0);
        public final java.util.concurrent.atomic.AtomicInteger writeCount =
                new java.util.concurrent.atomic.AtomicInteger(0);

        public Object getBizFatalProp() {
            readCount.incrementAndGet();
            throw new NopException(ERR_BIZ_FATAL).bizFatal(true);
        }

        public void setBizFatalProp(Object value) {
            writeCount.incrementAndGet();
            throw new NopException(ERR_BIZ_FATAL).bizFatal(true);
        }

        public Object getRecoverableProp() {
            throw new NopException(ERR_NOT_FATAL);
        }

        public void setRecoverableProp(Object value) {
            throw new NopException(ERR_NOT_FATAL);
        }
    }
}

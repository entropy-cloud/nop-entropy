package io.nop.core.exceptions;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestNamedExceptionFilter {
    static final ErrorCode ERR_TEST_A = ErrorCode.define("nop.err.core.test-filter-a", "test error a");
    static final ErrorCode ERR_TEST_B = ErrorCode.define("nop.err.core.test-filter-b", "test error b");

    @Test
    public void testRejectsJvmErrorByDefault() {
        NamedExceptionFilter filter = new NamedExceptionFilter();
        assertFalse(filter.test(new Error("jvm error")),
                "java.lang.Error should be rejected unless acceptError=true");

        // acceptError=true 仅解除 Error 拦截，仍需 accept 规则命中才接收
        filter.setAcceptError(true);
        assertFalse(filter.test(new Error("jvm error")), "acceptError without accept rules still rejects");

        filter.setAcceptClasses(setOf("java.lang.Error"));
        assertTrue(filter.test(new Error("jvm error")),
                "acceptError=true + acceptClasses hit should accept Error");
    }

    @Test
    public void testBizFatalGateIsIndependentOfAcceptRules() {
        NopException bizFatal = new NopException(ERR_TEST_A).bizFatal(true);
        NopException normal = new NopException(ERR_TEST_A);

        // acceptBizFatal=true 只是解除 bizFatal 拦截，仍需 accept 规则命中才会接收
        NamedExceptionFilter filter = new NamedExceptionFilter();
        filter.setAcceptBizFatal(true);
        assertFalse(filter.test(bizFatal), "no accept rule -> still rejected");
        assertFalse(filter.test(normal));

        // 正常（非 bizFatal）异常 + errorCode 命中 -> 接收
        filter.setAcceptErrorCodes(setOf(ERR_TEST_A.getErrorCode()));
        assertTrue(filter.test(normal));
    }

    @Test
    public void testAcceptAndRejectByClassName() {
        NamedExceptionFilter filter = new NamedExceptionFilter();
        filter.setAcceptClasses(setOf("java.lang.IllegalStateException"));

        assertTrue(filter.test(new IllegalStateException("ise")),
                "acceptClasses hit should accept the exception");
        assertFalse(filter.test(new RuntimeException("re")),
                "exception not in acceptClasses should not be accepted");

        // reject 优先于 accept
        filter.setRejectClasses(setOf("java.lang.IllegalStateException"));
        assertFalse(filter.test(new IllegalStateException("ise")), "rejectClasses should override acceptClasses");
    }

    @Test
    public void testAcceptAndRejectByErrorCode() {
        NamedExceptionFilter filter = new NamedExceptionFilter();
        filter.setAcceptErrorCodes(setOf(ERR_TEST_A.getErrorCode()));

        assertTrue(filter.test(new NopException(ERR_TEST_A)),
                "acceptErrorCodes hit should accept the exception");
        assertFalse(filter.test(new NopException(ERR_TEST_B)),
                "exception with other error code should not be accepted");

        // 非 NopException 无法提取 errorCode，不受 acceptErrorCodes 影响
        assertFalse(filter.test(new RuntimeException("plain")));

        // rejectErrorCodes 优先
        filter.setRejectErrorCodes(setOf(ERR_TEST_A.getErrorCode()));
        assertFalse(filter.test(new NopException(ERR_TEST_A)), "rejectErrorCodes should override acceptErrorCodes");
    }

    @Test
    public void testNoAcceptRuleMeansReject() {
        NamedExceptionFilter filter = new NamedExceptionFilter();
        assertFalse(filter.test(new RuntimeException("anything")),
                "without accept rules no exception should be accepted");
    }

    @Test
    public void testEmptySetsAreTreatedAsConfigured() {
        NamedExceptionFilter filter = new NamedExceptionFilter();
        Set<String> empty = Collections.emptySet();
        filter.setAcceptClasses(empty);
        filter.setAcceptErrorCodes(empty);
        assertFalse(filter.test(new RuntimeException("re")),
                "empty accept sets match nothing, so reject");
    }

    @Test
    public void testBizFatalNotRejectedWhenAcceptBizFatalAndCodeAccepted() {
        NamedExceptionFilter filter = new NamedExceptionFilter();
        filter.setAcceptBizFatal(true);
        filter.setAcceptErrorCodes(setOf(ERR_TEST_A.getErrorCode()));

        assertTrue(filter.test(new NopException(ERR_TEST_A).bizFatal(true)),
                "acceptBizFatal=true + acceptErrorCodes hit should accept");
    }

    private Set<String> setOf(String... items) {
        return new HashSet<>(java.util.Arrays.asList(items));
    }
}

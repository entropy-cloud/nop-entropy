package io.nop.stream.core.util;

import io.nop.stream.core.common.functions.IterationRuntimeContext;
import io.nop.stream.core.common.functions.RichFunction;
import io.nop.stream.core.common.functions.RuntimeContext;
import io.nop.stream.core.common.functions.StreamFunction;
import io.nop.stream.core.configuration.Configuration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestFunctionUtils {

    @Test
    void testOpenFunctionDelegatesToRichFunction() {
        MockRichFunction fn = new MockRichFunction();
        FunctionUtils.openFunction(fn, null);
        assertTrue(fn.openCalled);
    }

    @Test
    void testOpenFunctionSkipsPlainFunction() {
        FunctionUtils.openFunction(new MockPlainFunction(), null);
    }

    @Test
    void testCloseFunctionDelegatesToRichFunction() {
        MockRichFunction fn = new MockRichFunction();
        FunctionUtils.closeFunction(fn);
        assertTrue(fn.closeCalled);
    }

    @Test
    void testSetAndGetRuntimeContext() {
        MockRichFunction fn = new MockRichFunction();
        RuntimeContext ctx = new RuntimeContext() {};
        FunctionUtils.setFunctionRuntimeContext(fn, ctx);
        assertSame(ctx, FunctionUtils.getFunctionRuntimeContext(fn, null));
    }

    @Test
    void testGetRuntimeContextForPlainFunction() {
        RuntimeContext defaultCtx = new RuntimeContext() {};
        RuntimeContext result = FunctionUtils.getFunctionRuntimeContext(new MockPlainFunction(), defaultCtx);
        assertSame(defaultCtx, result);
    }

    private static class MockPlainFunction implements StreamFunction {
    }

    private static class MockRichFunction implements RichFunction {
        boolean openCalled;
        boolean closeCalled;
        private RuntimeContext runtimeContext;

        @Override
        public void open(Configuration parameters) { openCalled = true; }

        @Override
        public void close() { closeCalled = true; }

        @Override
        public RuntimeContext getRuntimeContext() { return runtimeContext; }

        @Override
        public void setRuntimeContext(RuntimeContext context) { this.runtimeContext = context; }

        @Override
        public IterationRuntimeContext getIterationRuntimeContext() { return null; }
    }
}

package io.nop.xlang.expr;

import io.nop.core.context.ServiceContextImpl;
import io.nop.core.unittest.BaseTestCase;
import io.nop.xlang.api.XLang;
import io.nop.xlang.utils.JsPromise;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestJsPromise extends BaseTestCase {

    private Object eval(String expr) {
        return XLang.newCompileTool().compileFullExpr(null, expr).invoke(new ServiceContextImpl());
    }

    @Test
    public void testPromiseResolve() {
        assertEquals(42, eval("Promise.resolve(42).get()"));
    }

    @Test
    public void testThenSingleArg() {
        assertEquals(2, eval("Promise.resolve(1).then(v => v + 1).get()"));
    }

    @Test
    public void testThenChained() {
        assertEquals(6, eval("Promise.resolve(1).then(v => v + 1).then(v => v * 3).get()"));
    }

    @Test
    public void testCatch() {
        assertEquals("caught:oops", eval("Promise.reject('oops').catch(e => 'caught:' + e).get()"));
    }

    @Test
    public void testFinally() {
        assertEquals(1, eval("Promise.resolve(1).finally(() => 'cleanup').get()"));
    }
}
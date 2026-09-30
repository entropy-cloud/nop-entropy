package io.nop.xlang.expr;

import io.nop.commons.concurrent.executor.ContinuationExecutor;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.unittest.BaseTestCase;
import io.nop.xlang.api.XLang;
import io.nop.xlang.utils.JsPromise;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    /**
     * [G2-10-01] (1) new Promise(executor) 中 executor 同步抛错：
     * promise 必须进入 rejected 状态（.get() 快速失败），不得永久悬挂。
     */
    @Test
    public void testExecutorSyncThrowRejects() {
        JsPromise promise = new JsPromise(null, (res, rej) -> {
            throw new IllegalStateException("executor-sync-boom");
        });
        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> promise.get(5, TimeUnit.SECONDS));
        assertNotNull(ex.getCause());
        assertEquals("executor-sync-boom", ex.getCause().getMessage());
    }

    /**
     * [G2-10-01] (1) executor 抛错经 ContinuationExecutor.runLoop 异步执行时：
     * 异常不得被 runLoop 吞掉，promise 必须进入 rejected 状态。
     */
    @Test
    public void testExecutorAsyncThrowRejects() {
        JsPromise[] holder = new JsPromise[1];
        ContinuationExecutor.INSTANCE.execute(() -> {
            holder[0] = new JsPromise(null, (res, rej) -> {
                throw new IllegalStateException("executor-async-boom");
            });
        });
        assertNotNull(holder[0]);
        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> holder[0].get(5, TimeUnit.SECONDS));
        assertNotNull(ex.getCause());
        assertEquals("executor-async-boom", ex.getCause().getMessage());
    }

    /**
     * [G2-10-01] (2) rejected promise 接无 onRejected 的 then(onF)：
     * 必须保持 rejected 透传（catch 仍可捕获），不得把 reason 当正常值 resolve。
     */
    @Test
    public void testRejectedThenFulfilledOnlyStaysRejected() {
        Object result = eval(
                "Promise.reject('oops').then(v => 'fulfilled:' + v).catch(e => 'caught:' + e).get()");
        assertEquals("caught:oops", result);
    }

    /**
     * [G2-10-01] (2) rejection 经多级 then(onF) 链透传后仍可被 catch 捕获。
     */
    @Test
    public void testThenChainPropagatesRejection() {
        Object result = eval(
                "Promise.reject('x').then(v => v + 1).then(v => 'recovered:' + v).catch(e => 'caught:' + e).get()");
        assertEquals("caught:x", result);
    }

    /**
     * [G2-10-01] (3) finally 回调抛错：promise 必须转 rejected（不再静默吞掉）。
     */
    @Test
    public void testFinallyCallbackThrowRejects() {
        JsPromise promise = JsPromise.resolve(1).finallyDo(null, (Runnable) () -> {
            throw new IllegalStateException("cleanup-fail");
        });
        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> promise.get(5, TimeUnit.SECONDS));
        assertNotNull(ex.getCause());
        assertEquals("cleanup-fail", ex.getCause().getMessage());
    }

    /**
     * [G2-10-01] (3) finally 回调正常完成时透传原 settle 状态（rejection 继续传播，
     * 回调返回值被忽略，对齐 JS finally 语义）。
     */
    @Test
    public void testFinallyPassesThroughRejection() {
        Object result = eval(
                "Promise.reject('x').finally(() => 'cleanup').catch(e => 'caught:' + e).get()");
        assertEquals("caught:x", result);
    }
}

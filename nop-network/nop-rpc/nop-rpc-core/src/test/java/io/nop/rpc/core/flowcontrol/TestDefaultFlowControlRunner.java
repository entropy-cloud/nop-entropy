package io.nop.rpc.core.flowcontrol;

import io.nop.api.core.exceptions.NopException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static io.nop.rpc.core.RpcErrors.ERR_RPC_FLOW_CONTROL_REJECT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 DefaultFlowControlRunner 的限流语义：配额内任务正常执行，
 * 配额耗尽时同步调用抛 ERR_RPC_FLOW_CONTROL_REJECT，异步调用以失败 future 结束。
 */
@Timeout(10)
public class TestDefaultFlowControlRunner {

    @Test
    public void testRunExecutesTaskWhenPermitAvailable() {
        DefaultFlowControlRunner runner = new DefaultFlowControlRunner(1000);
        assertEquals("ok", runner.run(null, () -> "ok"),
                "task must execute when a permit is acquired");
    }

    @Test
    public void testRunRejectsWhenRateExceeded() {
        // 限流器首张令牌即时发放（bursty 语义），先消耗掉再验证超限拒绝
        DefaultFlowControlRunner runner = new DefaultFlowControlRunner(0.001);
        runner.setMaxWaitMillis(1);
        assertEquals("first", runner.run(null, () -> "first"),
                "first permit must be granted immediately");

        NopException e = assertThrows(NopException.class, () -> runner.run(null, () -> "second"));
        assertEquals(ERR_RPC_FLOW_CONTROL_REJECT.getErrorCode(), e.getErrorCode());
    }

    @Test
    public void testRunAsyncRejectsWithFailedFuture() throws Exception {
        DefaultFlowControlRunner runner = new DefaultFlowControlRunner(0.001);
        runner.setMaxWaitMillis(1);
        // 消耗首张即时令牌
        runner.runAsync(null, () -> java.util.concurrent.CompletableFuture.completedStage("first"))
                .toCompletableFuture().get(5, TimeUnit.SECONDS);

        try {
            runner.runAsync(null, () -> java.util.concurrent.CompletableFuture.completedStage("ok"))
                    .toCompletableFuture().get(5, TimeUnit.SECONDS);
            throw new AssertionError("rate exceeded must reject the async call");
        } catch (java.util.concurrent.ExecutionException e) {
            assertTrue(e.getCause() instanceof NopException);
            assertEquals(ERR_RPC_FLOW_CONTROL_REJECT.getErrorCode(),
                    ((NopException) e.getCause()).getErrorCode());
        }
    }

    @Test
    public void testRunAsyncExecutesTaskWhenPermitAvailable() throws Exception {
        DefaultFlowControlRunner runner = new DefaultFlowControlRunner(1000);
        String ret = runner.runAsync(null, () -> java.util.concurrent.CompletableFuture.completedStage("ok"))
                .toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertEquals("ok", ret);
    }
}

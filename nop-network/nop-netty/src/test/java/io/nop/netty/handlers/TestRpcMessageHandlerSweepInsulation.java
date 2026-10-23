package io.nop.netty.handlers;

import io.netty.channel.embedded.EmbeddedChannel;
import io.nop.api.core.exceptions.NopTimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA 30 点表 #7（finding 2.1-C3-015）回归：RpcMessageHandler 的超时清扫运行在裸 Netty
 * EventExecutor.scheduleWithFixedDelay 上（平台绝缘设施不覆盖），清扫任务体抛出的 Throwable
 * 会让 Netty 抑制后续轮次——清扫链死亡，ResponseFuture 泄漏、请求永不超时。
 *
 * <p>修复形态 = 站点侧接入：调度入口包守卫（sweepExpiredFutures），catch Throwable + 结构化
 * ERROR 日志（观测），Error 不逃逸进 Netty 管线。本测试注入确定性 Error 验证三面：
 * 清扫链存活（Error 轮后下一轮仍清扫）、future/promise 终态正确（过期请求仍被异常终态化）、
 * EventExecutor 不因清扫失败死亡。
 */
@Timeout(30)
public class TestRpcMessageHandlerSweepInsulation {

    private static IRpcMessageAdapter echoAdapter() {
        return new IRpcMessageAdapter() {
            @Override
            public Object getRequestId(Object request) {
                return request;
            }

            @Override
            public Object getResponseId(Object response) {
                return response;
            }
        };
    }

    /**
     * 首轮清扫注入确定性 Error（LinkageError 派生，模拟 OOM/类缺失类逃逸面），此后放行正常清扫。
     */
    static class ErrorOnceSweepHandler extends RpcMessageHandler {
        final AtomicInteger sweeps = new AtomicInteger();

        ErrorOnceSweepHandler(int maxInFlight) {
            super(maxInFlight, echoAdapter());
        }

        @Override
        protected void checkTimeout() {
            if (sweeps.incrementAndGet() == 1)
                throw new LinkageError("injected-sweep-error");
            super.checkTimeout();
        }
    }

    private static void runOneSweepRound(EmbeddedChannel channel) throws InterruptedException {
        // 清扫周期 100ms（initialDelay 亦 100ms）：睡过一个完整周期后该轮必已到期
        Thread.sleep(150);
        channel.runScheduledPendingTasks();
    }

    @Test
    public void testSweepChainSurvivesInjectedErrorAndStillExpiresFutures() throws Exception {
        ErrorOnceSweepHandler handler = new ErrorOnceSweepHandler(100);
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        try {
            // 1ms 后过期的在飞请求：将 write 投递进事件循环并落地到 futures 表
            CompletableFuture<Object> pending = new CompletableFuture<>();
            handler.send("m1", 1, pending);
            channel.runPendingTasks();
            assertFalse(pending.isDone(), "request must stay pending before expiry");

            // 第 1 轮清扫注入 Error：守卫必须吃掉（不逃逸），但本轮跳过清扫动作
            runOneSweepRound(channel);
            assertEquals(1, handler.sweeps.get(), "first sweep round must have run");
            assertFalse(pending.isDone(), "error round must not corrupt pending futures");

            // 第 2 轮清扫：清扫链必须仍然存活（修复前 Netty 周期任务抛 Throwable 后续轮次被抑制）
            runOneSweepRound(channel);
            assertEquals(2, handler.sweeps.get(), "sweep chain must survive the injected Error");

            // future/promise 终态正确：过期在飞请求被异常终态化（不悬挂、不泄漏、请求可超时）
            assertTrue(pending.isCompletedExceptionally(), "expired request must reach a terminal state");
            ExecutionException ex = assertThrows(ExecutionException.class, () -> pending.get(1, TimeUnit.SECONDS));
            assertInstanceOf(NopTimeoutException.class, ex.getCause(), "terminal cause must be the timeout error");

            // EventExecutor / handler 线程载体不因清扫失败死亡：第 3 轮照常触发
            runOneSweepRound(channel);
            assertEquals(3, handler.sweeps.get(), "sweep rounds must keep firing on the event executor");
        } finally {
            channel.close().await(1, TimeUnit.SECONDS);
        }
    }
}

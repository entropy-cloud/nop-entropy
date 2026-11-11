package io.nop.rpc.core.message;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.concurrent.executor.DefaultScheduledExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 4.3-C1-003（plan 2560 裁定 J）+ MA-15（裁定 H）载体：RpcChannelState.prepareSend
 * 容量守卫原子化 + CLOSED/TOO_MANY 拒绝面 failed-future 包络。
 * <p>
 * 缺陷面：check-size-then-put TOCTOU——并发 prepareSend 在容量检查与 put 之间形成窗口，
 * 在飞 waitFutures 可瞬时突破 maxWaitingRequests；且 CLOSED/TOO_MANY 拒绝以同步抛交付，
 * 违反「返回 CompletionStage 的方法不得同步抛」契约（裁定 H）。
 * <p>
 * 判别口径（新造即红）：并发发送 + 高频采样 waitFutures.size()——修复前 check-then-put
 * 窗口被并发命中即观测到 size &gt; capacity（红）；修复后检查与 put 在 waitFutures 监视器
 * 同一临界区内，采样不可超容量（绿）。变异面：摘除 synchronized 收敛（回到裸检查）即红。
 */
@Timeout(60)
public class TestRpcChannelStateCapacity {

    static final int CAPACITY = 4;

    DefaultScheduledExecutor timer;

    @BeforeEach
    void setUp() {
        timer = DefaultScheduledExecutor.newSingleThreadTimer("test-cap-timer");
    }

    @AfterEach
    void tearDown() {
        timer.destroy();
    }

    @SuppressWarnings("unchecked")
    static Map<Object, CompletableFuture<ApiResponse<?>>> waitFutures(RpcChannelState<String, ApiResponse<?>> state)
            throws Exception {
        java.lang.reflect.Field f = RpcChannelState.class.getDeclaredField("waitFutures");
        f.setAccessible(true);
        return (Map<Object, CompletableFuture<ApiResponse<?>>>) f.get(state);
    }

    /**
     * 并发 prepareSend 恰好过容量判定：在飞登记数任何时刻不超 maxWaitingRequests；
     * 超容请求以 failed future 交付（非同步抛）。
     */
    @Test
    public void testConcurrentPrepareSendNeverExceedsCapacity() throws Exception {
        RpcChannelState<String, ApiResponse<?>> state = new RpcChannelState<>("cap-channel", timer);
        state.setMaxWaitingRequests(CAPACITY);

        int threads = 8;
        int rounds = 200;
        AtomicInteger admitted = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        AtomicLong maxObservedSize = new AtomicLong();
        AtomicBoolean running = new AtomicBoolean(true);
        Map<Object, CompletableFuture<ApiResponse<?>>> waitFutures = waitFutures(state);

        // 高频采样线程：TOCTOU 窗口的瞬时超容即被捕获
        Thread sampler = new Thread(() -> {
            while (running.get()) {
                int size = waitFutures.size();
                maxObservedSize.accumulateAndGet(size, Math::max);
            }
        });
        sampler.start();

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(threads);
            for (int i = 0; i < threads; i++) {
                final int tid = i;
                pool.submit(() -> {
                    try {
                        start.await();
                        for (int r = 0; r < rounds; r++) {
                            String id = "msg-" + tid + "-" + r;
                            try {
                                CompletableFuture<ApiResponse<?>> future = state.prepareSend(id, "req", 0);
                                if (future.isCompletedExceptionally()) {
                                    rejected.incrementAndGet();
                                } else {
                                    admitted.incrementAndGet();
                                    // 立即应答释放槽位（生产路径经 onReceive 移除 raw 登记），
                                    // 制造持续的容量竞态压力
                                    state.onReceive(id, ApiResponse.success("ok"));
                                }
                            } catch (Throwable e) {
                                // 4.3-MA-15（裁定 H）：拒绝面不得同步抛
                                throw new AssertionError("prepareSend must not sync-throw", e);
                            }
                        }
                        done.countDown();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
            start.countDown();
            assertTrue(done.await(45, TimeUnit.SECONDS), "all senders must finish");
        } finally {
            pool.shutdownNow();
            running.set(false);
            sampler.join(5000);
        }

        assertEquals(threads * rounds, admitted.get() + rejected.get(),
                "every send must be either admitted or failed-future rejected");

        // 4.3-C1-003（裁定 J）：在飞登记数任何时刻不突破容量上界
        assertTrue(maxObservedSize.get() <= CAPACITY,
                "in-flight registrations must never exceed capacity (observed max="
                        + maxObservedSize.get() + ", capacity=" + CAPACITY + ")");
    }

    /**
     * 超容拒绝面（MA-15 裁定 H）：容量满后 prepareSend 返回 failed future（非同步抛），
     * 错误键 nop.err.rpc.too-many-inflight-messages。
     */
    @Test
    public void testOverCapacityReturnsFailedFutureNotSyncThrow() {
        RpcChannelState<String, ApiResponse<?>> state = new RpcChannelState<>("cap-channel", timer);
        state.setMaxWaitingRequests(CAPACITY);

        for (int i = 0; i < CAPACITY; i++) {
            state.prepareSend("fill-" + i, "req", 0);
        }

        // 不应同步抛——assertDoesNotThrow 语义由「无 assertThrows 包裹」承载
        CompletableFuture<ApiResponse<?>> rejected = state.prepareSend("over", "req", 0);
        assertTrue(rejected.isCompletedExceptionally(),
                "over-capacity send must be delivered as a failed future (4.3-MA-15 裁定 H)");

        Throwable err = rejected.handle((r, e) -> e).join();
        assertTrue(err instanceof NopException, "rejection must carry NopException, got: " + err);
        assertEquals("nop.err.rpc.too-many-inflight-messages", ((NopException) err).getErrorCode());
    }

    /**
     * CLOSED 拒绝面（MA-15 裁定 H）：通道关闭后 prepareSend 返回 failed future（非同步抛），
     * 错误键 nop.err.rpc.channel-closed。
     */
    @Test
    public void testClosedChannelReturnsFailedFutureNotSyncThrow() {
        RpcChannelState<String, ApiResponse<?>> state = new RpcChannelState<>("cap-channel", timer);
        state.onChannelClose(new RuntimeException("down"));

        CompletableFuture<ApiResponse<?>> rejected = state.prepareSend("after-close", "req", 0);
        assertTrue(rejected.isCompletedExceptionally(),
                "closed-channel send must be delivered as a failed future (4.3-MA-15 裁定 H)");

        Throwable err = rejected.handle((r, e) -> e).join();
        assertTrue(err instanceof NopException, "rejection must carry NopException, got: " + err);
        assertEquals("nop.err.rpc.channel-closed", ((NopException) err).getErrorCode());
    }
}

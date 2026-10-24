package io.nop.ai.toolkit.manager;

import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolExecutor;
import io.nop.ai.toolkit.api.IToolExecutorProvider;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.concurrent.executor.DefaultThreadPoolExecutor;
import io.nop.commons.concurrent.executor.IThreadPoolExecutor;
import io.nop.core.lang.xml.XNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA 3.2-C4-008（plan 2499 Phase 3 子分片 C，裁定⑥取消链接线）：
 * <p>
 * 缺陷：经 ToolManagerImpl 返回 future 的取消任何路径都不达池任务——排队分支手工 result
 * future 与执行链断链（result.cancel(true) 假确认，排队/在途任务照常跑满）+ 排队窗口
 * cancelToken 盲区（监听器轮次到来才注册）。
 * <p>
 * 修复面（C10-014 wrapper 传播断言）：(1) 排队取消联动——轮到本调用时判 result 取消状态
 * 即不再派生执行；(2) 执行入口统一 isCancelled 短路——context cancelToken 已取消时显式
 * 取消终态；(3) 在途取消联动——result 取消传播到执行 future。
 */
public class TestToolManagerCancelChain_C4_008 {

    private static DefaultThreadPoolExecutor pool;

    @BeforeAll
    static void initPool() {
        pool = DefaultThreadPoolExecutor.newExecutor("toolkit-cancel-test", 4, 32, true);
    }

    @AfterAll
    static void destroyPool() {
        if (pool != null) {
            pool.destroy();
        }
    }

    /**
     * 可控执行器：任务体挂起在 latch 上（可被 future.cancel(true) 的 worker 中断打断），
     * 记录派生次数与取消观测。
     */
    static class HangingExecutor implements IToolExecutor {
        static final String TOOL_NAME = "hanging-tool";

        final AtomicInteger started = new AtomicInteger();
        final AtomicInteger cancelled = new AtomicInteger();
        final AtomicInteger completed = new AtomicInteger();
        /** 显式放行（零回归用例）：置 true 后所有挂起体以成功路径退出 */
        volatile boolean released;

        @Override
        public String getToolName() {
            return TOOL_NAME;
        }

        @Override
        public CompletionStage<AiToolCallResult> executeAsync(AiToolCall call, IToolExecuteContext context) {
            started.incrementAndGet();
            return context.getExecutor().submit(() -> {
                // 挂起体：future.cancel(true) 的 worker 中断 → 取消路径；released 标志 → 成功路径
                long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(5);
                while (!Thread.currentThread().isInterrupted() && !released
                        && System.nanoTime() < deadline) {
                    LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(20));
                }
                if (Thread.currentThread().isInterrupted()) {
                    cancelled.incrementAndGet();
                    AiToolCallResult result = new AiToolCallResult();
                    result.setId(call.getId());
                    result.setStatus("cancelled");
                    return result;
                }
                completed.incrementAndGet();
                AiToolCallResult result = new AiToolCallResult();
                result.setId(call.getId());
                result.setStatus("success");
                return result;
            });
        }
    }

    static class SingleExecutorProvider implements IToolExecutorProvider {
        private final IToolExecutor executor;

        SingleExecutorProvider(IToolExecutor executor) {
            this.executor = executor;
        }

        @Override
        public IToolExecutor getExecutor(String toolName) {
            return executor;
        }

        @Override
        public java.util.Collection<String> getToolNames() {
            return List.of(executor.getToolName());
        }
    }

    static class CancelTokenStub implements ICancelToken {
        volatile boolean cancelled;
        volatile String reason;

        void cancel(String cancelReason) {
            this.cancelled = true;
            this.reason = cancelReason;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public String getCancelReason() {
            return reason;
        }

        @Override
        public void appendOnCancel(java.util.function.Consumer<String> task) {
            // C4-008 立案面：排队/入口短路不依赖监听器补发（注册前补触发由平台单点
            // bindCancelToken 承载，api-core 侧断言见 TestFutureHelperBindCancelContract）
        }

        @Override
        public void removeOnCancel(java.util.function.Consumer<String> task) {
        }
    }

    static class Ctx implements IToolExecuteContext {
        ICancelToken cancelToken;

        @Override public File getWorkDir() {
            return new File(".");
        }

        @Override public Map<String, String> getEnvs() {
            return Map.of();
        }

        @Override public long getExpireAt() {
            return Long.MAX_VALUE;
        }

        @Override public ICancelToken getCancelToken() {
            return cancelToken;
        }

        @Override public io.nop.ai.toolkit.fs.IToolFileSystem getFileSystem() {
            return null;
        }

        @Override public IThreadPoolExecutor getExecutor() {
            return pool;
        }
    }

    private static AiToolCall callWithPath(String id, String path) {
        XNode node = XNode.make("hanging-tool");
        node.setAttr("id", id);
        AiToolCall call = AiToolCall.fromNode(node);
        call.setToolName(HangingExecutor.TOOL_NAME);
        call.setInput("{\"path\":\"" + path + "\"}");
        return call;
    }

    /**
     * (2) 执行入口统一 isCancelled 短路：context cancelToken 已取消时调用不再派生执行，
     * future 以取消终态失败（显式失败非静默成功，rule 24）。
     * 修复前执行照常派生（先红）。
     */
    @Test
    public void testCancelledContextTokenShortCircuitsExecution() throws Exception {
        HangingExecutor executor = new HangingExecutor();
        ToolManagerImpl manager = new ToolManagerImpl(new SingleExecutorProvider(executor), null);

        Ctx ctx = new Ctx();
        CancelTokenStub token = new CancelTokenStub();
        token.cancel("session-cancelled");
        ctx.cancelToken = token;

        CompletableFuture<AiToolCallResult> future = manager
                .callTool(HangingExecutor.TOOL_NAME, callWithPath("1", "/tmp/a.txt"), ctx)
                .toCompletableFuture();

        CompletableFuture<AiToolCallResult> probe = future;
        Throwable err = probe.handle((r, e) -> e).get(5, TimeUnit.SECONDS);
        assertNotNull(err, "cancelled token must fail the call, not return a tool result");
        assertInstanceOf(java.util.concurrent.CancellationException.class, err,
                "short-circuit must be a cancellation terminal state, got: " + err);
        assertEquals(0, executor.started.get(),
                "execution must not be dispatched for an already-cancelled token");
    }

    /**
     * (1) 排队取消联动：同路径第二调用排队期间 result 被取消——轮到它时不再派生执行
     * （消除 result.cancel(true) 假确认）。修复前第二调用照常派生执行（先红）。
     */
    @Test
    public void testQueuedCallCancelledBeforeTurnDoesNotExecute() throws Exception {
        HangingExecutor executor = new HangingExecutor();
        ToolManagerImpl manager = new ToolManagerImpl(new SingleExecutorProvider(executor), null);

        Ctx ctx = new Ctx();
        CompletableFuture<AiToolCallResult> first = manager
                .callTool(HangingExecutor.TOOL_NAME, callWithPath("2", "/tmp/queued.txt"), ctx)
                .toCompletableFuture();
        CompletableFuture<AiToolCallResult> second = manager
                .callTool(HangingExecutor.TOOL_NAME, callWithPath("3", "/tmp/queued.txt"), ctx)
                .toCompletableFuture();

        // 排队窗口内取消第二调用
        assertTrue(second.cancel(true), "queued result must be cancellable");
        assertTrue(second.isCancelled());

        // 放行第一调用（ latch 挂起态经中断解除：直接取消第一调用以结束排队链）
        assertTrue(first.cancel(true));

        Throwable secondErr = second.handle((r, e) -> e).get(5, TimeUnit.SECONDS);
        assertNotNull(secondErr);
        assertInstanceOf(java.util.concurrent.CancellationException.class, secondErr,
                "queued-cancelled call must settle as cancellation");

        // 第一调用在其轮次已派生执行（在途联动取消见下一用例）；第二调用轮到时不得派生
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (executor.started.get() < 1 && System.nanoTime() < deadline) {
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(20));
        }
        assertEquals(1, executor.started.get(),
                "only the first (already-started) call may derive execution; the queued-cancelled "
                        + "call must not derive a second execution");
    }

    /**
     * (3) 在途取消联动：result 取消传播到在途执行 future（worker 可观测取消），
     * 任务不再跑满。修复前取消止步于包装层、任务照常执行完毕（先红）。
     */
    @Test
    public void testResultCancelPropagatesToInFlightExecution() throws Exception {
        HangingExecutor executor = new HangingExecutor();
        ToolManagerImpl manager = new ToolManagerImpl(new SingleExecutorProvider(executor), null);

        Ctx ctx = new Ctx();
        CompletableFuture<AiToolCallResult> future = manager
                .callTool(HangingExecutor.TOOL_NAME, callWithPath("4", "/tmp/inflight.txt"), ctx)
                .toCompletableFuture();

        // 等待执行派生
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (executor.started.get() < 1 && System.nanoTime() < deadline) {
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(20));
        }
        assertEquals(1, executor.started.get());

        assertTrue(future.cancel(true), "in-flight result must be cancellable");

        long deadline2 = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (executor.cancelled.get() < 1 && System.nanoTime() < deadline2) {
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(20));
        }
        assertEquals(1, executor.cancelled.get(),
                "result cancellation must reach the in-flight execution (worker observes cancel)");
        assertEquals(0, executor.completed.get(),
                "the in-flight task must not run to completion");
    }

    /**
     * 零回归对照：无取消的排队调用完整执行且结果回填（排队链结构不被联动改造破坏）。
     */
    @Test
    public void testQueuedCallsWithoutCancelStillComplete() throws Exception {
        HangingExecutor executor = new HangingExecutor();
        ToolManagerImpl manager = new ToolManagerImpl(new SingleExecutorProvider(executor), null);

        Ctx ctx = new Ctx();
        CompletableFuture<AiToolCallResult> first = manager
                .callTool(HangingExecutor.TOOL_NAME, callWithPath("5", "/tmp/normal.txt"), ctx)
                .toCompletableFuture();
        CompletableFuture<AiToolCallResult> second = manager
                .callTool(HangingExecutor.TOOL_NAME, callWithPath("6", "/tmp/normal.txt"), ctx)
                .toCompletableFuture();

        executor.released = true;

        AiToolCallResult firstResult = first.get(10, TimeUnit.SECONDS);
        assertNotNull(firstResult, "first queued call must complete normally");
        assertEquals("success", firstResult.getStatus());

        AiToolCallResult secondResult = second.get(10, TimeUnit.SECONDS);
        assertNotNull(secondResult, "second queued call must execute to completion");
        assertEquals("success", secondResult.getStatus());
        assertEquals(2, executor.started.get(), "both queued calls derive execution");
        assertEquals(2, executor.completed.get(), "no cancel linkage interferes with normal flow");
    }
}

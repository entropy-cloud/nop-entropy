package io.nop.ai.toolkit.manager;

import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolExecutor;
import io.nop.ai.toolkit.api.IToolExecutorProvider;
import io.nop.ai.toolkit.executor.ToolExecuteContext;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.ai.toolkit.model.AiToolCalls;
import io.nop.ai.toolkit.model.AiToolCallsResponse;
import io.nop.commons.concurrent.executor.DefaultThreadPoolExecutor;
import io.nop.commons.concurrent.executor.IThreadPoolExecutor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA 3.2-C1-001（plan 2501 Phase 1，MA-01 pathQueues 排队链挂死族主登记）：
 * <p>
 * 缺陷（同一代码块三子缺陷）：(a) 已完成 trigger 的 {@code thenCompose} 在
 * {@code pathQueues.compute} 的 CHM bin 锁内同步执行工具分发（nop-cli
 * CliCallToolsCommand:75 注入 SyncThreadPoolExecutor.INSTANCE 为锁内全执行的实证装配）；
 * (b) 锁内重入（call-agent 嵌套派发同 pathKey）→ CHM Recursive update；(c)
 * {@code executeNow.get()} 同步抛（executor 未装配 NPE / 饱和池 REE）时手工 result
 * future 永不完成 → 调用方永久静默悬挂。
 * <p>
 * 修复面（C10-004 故障注入形态）：executeNow 移出 compute（compute 内只做 tail 哨兵
 * 取换）+ result 完成契约闭合（同步抛 → completeExceptionally）+ null executor 装配面
 * fail-loud。plan 2499（3.2-C4-008）取消联动语义零回退由
 * {@code TestToolManagerCancelChain_C4_008} 全组与 {@code TestToolPathQueue} 钉守。
 */
public class TestToolManagerQueueChain_C1_001 {

    private static DefaultThreadPoolExecutor pool;

    @BeforeAll
    static void initPool() {
        pool = DefaultThreadPoolExecutor.newExecutor("toolkit-c1-001-test", 4, 32, true);
    }

    @AfterAll
    static void destroyPool() {
        if (pool != null) {
            pool.destroy();
        }
    }

    static AiToolCall callWithPath(String path, String note) {
        AiToolCall call = new AiToolCall();
        call.setToolName("write-file");
        call.setInput("{\"path\":\"" + path + "\",\"note\":\"" + note + "\"}");
        return call;
    }

    /**
     * 同步分发执行器（模拟 nop-cli SyncThreadPoolExecutor 装配：executeAsync 在调用
     * 线程内联完成全部工作）。可选在首个调用上挂起以构造「分发体持锁」时序。
     */
    static class SyncDispatchProvider implements IToolExecutorProvider {
        final AtomicInteger started = new AtomicInteger();
        /** true 时 executeAsync 挂起在 latch 上（构造 bin 锁持有面） */
        volatile boolean blockDispatch;
        CountDownLatch blockLatch = new CountDownLatch(1);
        /** 嵌套派发探针：armed 时 executeAsync 内联再入 manager.callTool（仅首次分发触发一次） */
        volatile ToolManagerImpl nestedTarget;
        volatile boolean nestedArmed = true;
        String nestedPath;
        final AtomicReference<Throwable> nestedError = new AtomicReference<>();
        final AtomicReference<CompletableFuture<AiToolCallResult>> nestedFuture = new AtomicReference<>();
        /** true 时 executeAsync 向 context.getExecutor() 提交（可注入 REE/NPE 面） */
        volatile boolean submitToContextExecutor;

        @Override
        public IToolExecutor getExecutor(String toolName) {
            return new IToolExecutor() {
                @Override
                public String getToolName() {
                    return toolName;
                }

                @Override
                public CompletionStage<AiToolCallResult> executeAsync(AiToolCall call, IToolExecuteContext context) {
                    started.incrementAndGet();
                    if (nestedArmed && nestedTarget != null) {
                        // (b) 嵌套派发探针：同 pathKey 再入 callTool（仅首次分发触发）
                        nestedArmed = false;
                        try {
                            nestedFuture.set(nestedTarget
                                    .callTool(call.getToolName(), callWithPath(nestedPath, "nested"), null)
                                    .toCompletableFuture());
                        } catch (Throwable e) {
                            nestedError.set(e);
                        }
                    }
                    if (submitToContextExecutor) {
                        // (c) 同步抛面载体：submit 同步抛 REE（池饱和）时异常穿出 executeAsync
                        return context.getExecutor().submit(() -> {
                            AiToolCallResult result = new AiToolCallResult();
                            result.setId(call.getId());
                            result.setStatus("success");
                            result.setError(null);
                            return result;
                        });
                    }
                    if (blockDispatch) {
                        try {
                            blockLatch.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    return CompletableFuture.completedFuture(AiToolCallResult.successResult(0, "ok:" + call.getInput()));
                }
            };
        }

        @Override
        public java.util.Collection<String> getToolNames() {
            return Collections.singleton("write-file");
        }
    }

    private static Throwable errorOf(CompletableFuture<?> future) throws Exception {
        return future.handle((r, e) -> e).get(10, TimeUnit.SECONDS);
    }

    /**
     * (a) 锁内执行消除：已完成前驱的分发体不得在 pathQueues.compute 的 CHM bin 锁内
     * 运行。用恒等 hash 冲突对（"Aa"/"BB"，hashCode 同为 2112 → 必落同一 CHM bin）
     * 构造确定性同 bin 竞争：T1 的分发体挂起期间，T2 的同 bin callTool 仍可进入
     * compute 并派生执行。修复前 T1 在 compute 锁内挂起 → T2 永远进不来（先红）。
     */
    @Test
    @Timeout(30)
    public void sameBinDispatchDoesNotHoldComputeBinLock() throws Exception {
        SyncDispatchProvider provider = new SyncDispatchProvider();
        provider.blockDispatch = true;
        ToolManagerImpl manager = new ToolManagerImpl(provider, Collections.emptyList());

        Thread t1 = new Thread(() -> manager.callTool("write-file", callWithPath("Aa", "first"), null),
                "c1-001-bin-holder");
        t1.setDaemon(true);
        t1.start();

        long deadline = System.currentTimeMillis() + 5000;
        while (provider.started.get() < 1 && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertEquals(1, provider.started.get(), "T1 dispatch must have started (blocked in dispatch body)");

        // T2：同 bin（同 hashCode）路径的 callTool——修复后不被 bin 锁阻挡
        Thread t2 = new Thread(() -> manager.callTool("write-file", callWithPath("BB", "second"), null),
                "c1-001-bin-waiter");
        t2.setDaemon(true);
        t2.start();

        deadline = System.currentTimeMillis() + 5000;
        while (provider.started.get() < 2 && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        try {
            assertEquals(2, provider.started.get(),
                    "same-bin callTool must not be blocked by another path's in-flight dispatch body");
        } finally {
            provider.blockLatch.countDown();
            t1.join(10000);
            t2.join(10000);
        }
    }

    /**
     * (b) 锁内重入消除：同步分发体内的同 path 嵌套 callTool 不得触发 CHM
     * Recursive update。修复前嵌套 compute 抛 IllegalStateException（先红）。
     */
    @Test
    @Timeout(30)
    public void nestedSamePathCallToolDoesNotRecurseIntoCompute() throws Exception {
        SyncDispatchProvider provider = new SyncDispatchProvider();
        ToolManagerImpl manager = new ToolManagerImpl(provider, Collections.emptyList());
        provider.nestedTarget = manager;
        provider.nestedPath = "/a/nested.txt";

        CompletableFuture<AiToolCallResult> outer =
                manager.callTool("write-file", callWithPath("/a/nested.txt", "outer"), null).toCompletableFuture();

        Throwable nestedError = provider.nestedError.get();
        assertTrue(nestedError == null,
                "nested same-path callTool must not throw (CHM Recursive update), got: " + nestedError);
        assertNotNull(provider.nestedFuture.get(), "nested call must return a future");

        // 嵌套调用排队于 outer 之后：outer 完成后嵌套调用必须正常落定
        AiToolCallResult outerResult = outer.get(10, TimeUnit.SECONDS);
        assertNotNull(outerResult);
        AiToolCallResult nestedResult = provider.nestedFuture.get().get(10, TimeUnit.SECONDS);
        assertNotNull(nestedResult, "nested queued call must complete after the outer call releases the path");
        assertEquals(2, provider.started.get(), "both outer and nested calls must derive execution");
    }

    /**
     * (c) result 完成契约闭合（REE 形态，C10-004 故障注入）：排队链上 executeNow 同步抛
     * RejectedExecutionException 时，调用方 result 必须以异常终态落定而非永久悬挂。
     * 修复前 result 永不完成（先红：get 超时）。
     */
    @Test
    @Timeout(30)
    public void rejectedExecutionSettlesResultExceptionally() throws Exception {
        DefaultThreadPoolExecutor tiny = DefaultThreadPoolExecutor.newExecutor("c1-001-ree", 1, 1, true);
        CountDownLatch hang = new CountDownLatch(1);
        try {
            // 饱和注入：占满 1 线程 + 1 队列位（首任务体真实运行后再填队列位——
            // 消除线程启动竞态：负载下首 submit 若尚在队列，第二次 submit 会提前 REE）
            CountDownLatch occupierStarted = new CountDownLatch(1);
            tiny.submit(() -> {
                occupierStarted.countDown();
                hang.await();
                return null;
            });
            occupierStarted.await();
            tiny.submit(() -> null);

            SyncDispatchProvider provider = new SyncDispatchProvider();
            provider.submitToContextExecutor = true;
            ToolManagerImpl manager = new ToolManagerImpl(provider, Collections.emptyList());
            IToolExecuteContext ctx = ToolExecuteContext.builder().executor(tiny).build();

            CompletableFuture<AiToolCallResult> future =
                    manager.callTool("write-file", callWithPath("/a/ree.txt", "x"), ctx).toCompletableFuture();

            Throwable err = errorOf(future);
            assertNotNull(err, "saturated-pool submit must settle the caller result exceptionally, not hang");
            Throwable cause = (err instanceof CompletionException || err instanceof ExecutionException)
                    && err.getCause() != null ? err.getCause() : err;
            assertTrue(cause instanceof RejectedExecutionException,
                    "expected RejectedExecutionException terminal state, got: " + cause);
            assertEquals(1, provider.started.get(), "dispatch body must have been reached");
        } finally {
            hang.countDown();
            tiny.destroy();
        }
    }

    /**
     * ③ null executor 装配面 fail-loud：context 已装配但 executor 为 null 时，调用方
     * 得到显式异常终态（原形态：NPE 在分发体内同步抛 → result 永不完成悬挂）。
     */
    @Test
    @Timeout(30)
    public void nullExecutorContextFailsLoudInsteadOfHanging() throws Exception {
        SyncDispatchProvider provider = new SyncDispatchProvider();
        provider.submitToContextExecutor = true;
        ToolManagerImpl manager = new ToolManagerImpl(provider, Collections.emptyList());

        IToolExecuteContext ctx = ToolExecuteContext.builder().build(); // executor 未装配

        CompletableFuture<AiToolCallResult> future =
                manager.callTool("write-file", callWithPath("/a/null-exec.txt", "x"), ctx).toCompletableFuture();

        Throwable err = errorOf(future);
        assertNotNull(err, "null-executor context must settle the result exceptionally, not hang");
        assertEquals(0, provider.started.get(),
                "fail-loud must short-circuit before the tool dispatch body runs");
    }

    /**
     * (c) 队列不被失败调用毒化：同 path 的失败调用（REE 同步抛）之后，后续排队调用
     * 仍正常派生并完成。修复前首个调用悬挂（先红）；修复后失败终态 + 后继成功。
     */
    @Test
    @Timeout(30)
    public void queueNotPoisonedByFailedCall() throws Exception {
        DefaultThreadPoolExecutor tiny = DefaultThreadPoolExecutor.newExecutor("c1-001-poison", 1, 1, true);
        CountDownLatch hang = new CountDownLatch(1);
        try {
            // 首任务体真实运行后再填队列位（线程启动竞态消除，同上）
            CountDownLatch occupierStarted = new CountDownLatch(1);
            tiny.submit(() -> {
                occupierStarted.countDown();
                hang.await();
                return null;
            });
            occupierStarted.await();
            tiny.submit(() -> null);

            SyncDispatchProvider provider = new SyncDispatchProvider();
            provider.submitToContextExecutor = true;
            ToolManagerImpl manager = new ToolManagerImpl(provider, Collections.emptyList());
            IToolExecuteContext ctx = ToolExecuteContext.builder().executor(tiny).build();

            CompletableFuture<AiToolCallResult> failing =
                    manager.callTool("write-file", callWithPath("/a/p.txt", "fail"), ctx).toCompletableFuture();

            // 池仍饱和：排队调用同样以 REE 终态落定（不悬挂、不被前驱失败毒化为静默丢失）
            CompletableFuture<AiToolCallResult> follower =
                    manager.callTool("write-file", callWithPath("/a/p.txt", "follow"), ctx).toCompletableFuture();

            Throwable err1 = errorOf(failing);
            assertNotNull(err1, "failing call must settle exceptionally");
            Throwable err2 = errorOf(follower);
            assertNotNull(err2, "queued follower must settle exceptionally (saturated pool), not hang");

            // 池释放后同 path 新调用恢复正常（队列链健康）
            provider.submitToContextExecutor = false;
            hang.countDown();
            CompletableFuture<AiToolCallResult> recovered =
                    manager.callTool("write-file", callWithPath("/a/p.txt", "recover"), ctx).toCompletableFuture();
            AiToolCallResult recoveredResult = recovered.get(10, TimeUnit.SECONDS);
            assertNotNull(recoveredResult);
            assertTrue(recoveredResult.getStatus().startsWith("success"),
                    "queue must stay healthy after a failed call, got: " + recoveredResult.getStatus());
        } finally {
            hang.countDown();
            tiny.destroy();
        }
    }

    /**
     * (c) submitBounded 信号量随异常终态恢复：executeParallel(maxConcurrency=1) 下首
     * 调用同步抛 REE 时 permit 必须释放，第二调用得以提交并以异常终态落定——整批
     * future 落定而非永久悬挂。修复前首调用悬挂 → permit 永不释放（先红）。
     */
    @Test
    @Timeout(30)
    public void semaphoreReleasedWhenCallFailsSynchronously() throws Exception {
        DefaultThreadPoolExecutor tiny = DefaultThreadPoolExecutor.newExecutor("c1-001-sem", 1, 1, true);
        CountDownLatch hang = new CountDownLatch(1);
        try {
            // 首任务体真实运行后再填队列位（线程启动竞态消除，同上）
            CountDownLatch occupierStarted = new CountDownLatch(1);
            tiny.submit(() -> {
                occupierStarted.countDown();
                hang.await();
                return null;
            });
            occupierStarted.await();
            tiny.submit(() -> null);

            SyncDispatchProvider provider = new SyncDispatchProvider();
            provider.submitToContextExecutor = true;
            ToolManagerImpl manager = new ToolManagerImpl(provider, Collections.emptyList());
            IToolExecuteContext ctx = ToolExecuteContext.builder().executor(tiny).build();

            AiToolCalls calls = new AiToolCalls();
            calls.setParallel(true);
            calls.setMaxConcurrency(1);
            AiToolCall one = callWithPath("/a/s1.txt", "one");
            one.setId(101);
            AiToolCall two = callWithPath("/a/s2.txt", "two");
            two.setId(102);
            calls.setBody(List.of(one, two));

            CompletableFuture<AiToolCallsResponse> batch =
                    manager.callTools(calls, ctx).toCompletableFuture();
            Throwable err = errorOf(batch);
            assertNotNull(err, "batch with sync-throwing call must settle (semaphore released), not hang");
        } finally {
            hang.countDown();
            tiny.destroy();
        }
    }

    /**
     * rule 22 端到端 + rule 23 接线：真实 callTool 排队链入口（非 concurrency-safe
     * 工具 + pathKey 提取命中 + 真实线程池）到 result 终态输出完整走通——同 path 串行、
     * 结果体真实回填。修复后的完成契约（同步抛终态）在上述 REE/null-executor 用例中
     * 只有经新契约代码路径才可能通过（接线验证）。
     */
    @Test
    @Timeout(30)
    public void endToEndQueuedChainReachesTerminalOutput() throws Exception {
        SyncDispatchProvider provider = new SyncDispatchProvider();
        ToolManagerImpl manager = new ToolManagerImpl(provider, Collections.emptyList());
        IToolExecuteContext ctx = ToolExecuteContext.builder().executor(pool).build();

        CompletableFuture<AiToolCallResult> first =
                manager.callTool("write-file", callWithPath("/e2e/same.txt", "first"), ctx).toCompletableFuture();
        CompletableFuture<AiToolCallResult> second =
                manager.callTool("write-file", callWithPath("/e2e/same.txt", "second"), ctx).toCompletableFuture();

        AiToolCallResult firstResult = first.get(10, TimeUnit.SECONDS);
        AiToolCallResult secondResult = second.get(10, TimeUnit.SECONDS);
        assertNotNull(firstResult);
        assertNotNull(secondResult);
        assertEquals("success", firstResult.getStatus(), "first queued call must reach terminal output");
        assertEquals("success", secondResult.getStatus(), "second queued call must reach terminal output");
        assertTrue(firstResult.getError() == null || firstResult.getError().getBody() == null,
                "successful result must not carry an error body");
        assertTrue(provider.started.get() >= 2, "both queued calls must derive execution");
    }
}

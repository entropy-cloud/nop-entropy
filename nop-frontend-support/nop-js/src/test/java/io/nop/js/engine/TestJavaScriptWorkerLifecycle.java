/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.js.engine;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.unittest.BaseTestCase;
import io.nop.js.JsConstants;
import io.nop.js.JsErrors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA 8.40（6.5 单元）W-01 验收载体红①（C10-001 缺保护面同 diff）：JavaScriptWorker 生命周期
 * 拒绝语义——关闭窗口 check-then-act（失效层①）、死 worker 路由（失效层②）、队列无界（失效层③）
 * 与 CL-201 build-then-swap。断言形态遵守 orTimeout 假绿禁令：future 完成性只用同步
 * {@code get(timeout)}，捕获 TimeoutException 即红；CancellationException/ExecutionException
 * 均计为已完成。闩控两伪绿防线：闩阻塞点在委托 super.add 之前 + 预热确认 init 完成。
 */
public class TestJavaScriptWorkerLifecycle extends BaseTestCase {
    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    JavaScriptService newService() {
        JavaScriptService service = new JavaScriptService();
        service.setInitScriptPath("/nop/js/test-server.mjs");
        service.setJsLibLoader(path -> {
            IResource resource = VirtualFileSystem.instance().getResource(path);
            return ResourceHelper.readText(resource);
        });
        return service;
    }

    /**
     * CRA 8.40 测试缝 S1 的闩控队列：被标记的提交线程在委托 super.add 之前阻塞于闩（其余线程
     * 含 close 的 pill 不受影响），用于确定性构造「worker 终局排空完成后才入队」的搁浅窗口。
     */
    static final class LatchQueue extends LinkedBlockingQueue<Consumer<Boolean>> {
        final CountDownLatch latch = new CountDownLatch(1);
        volatile Thread blockedThread;

        @Override
        public boolean add(Consumer<Boolean> task) {
            if (Thread.currentThread() == blockedThread) {
                try {
                    if (!latch.await(30, TimeUnit.SECONDS))
                        throw new IllegalStateException("cra-w01 latch timeout");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("cra-w01 latch interrupted", e);
                }
            }
            return super.add(task);
        }
    }

    private static void awaitCondition(String message, long timeoutMillis, Condition condition)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (!condition.get()) {
            assertTrue(System.currentTimeMillis() < deadline, message);
            Thread.sleep(10);
        }
    }

    @FunctionalInterface
    interface Condition {
        boolean get() throws InterruptedException;
    }

    // 红①(a)(c) 主驱动：闩控确定性——双关闭入口各一轮（close / shutdownGracefully）
    @Test
    public void testShutdownDuringSubmitLatchDrivenClose() throws Exception {
        doLatchDrivenShutdown(JavaScriptWorker::close);
    }

    @Test
    public void testShutdownDuringSubmitLatchDrivenGraceful() throws Exception {
        doLatchDrivenShutdown(JavaScriptWorker::shutdownGracefully);
    }

    private JavaScriptWorker newWorker(BlockingQueue<Consumer<Boolean>> queue) {
        JavaScriptWorker worker = new JavaScriptWorker(queue);
        worker.setInitScriptPath("/nop/js/test-server.mjs");
        worker.setJsLibLoader(path -> {
            IResource resource = VirtualFileSystem.instance().getResource(path);
            return ResourceHelper.readText(resource);
        });
        return worker;
    }

    private void doLatchDrivenShutdown(Consumer<JavaScriptWorker> closer) throws Exception {
        LatchQueue queue = new LatchQueue();
        JavaScriptWorker worker = newWorker(queue);
        Thread workerThread = new Thread(worker, "cra-w01-latch-worker");
        workerThread.start();
        try {
            // 预热（m1 伪绿防线二）：确认 worker 线程已启动且 init 完成，防止
            // getExecuteThread() 初始 null 假成立与 worker 后启动消费任务的伪绿
            assertNotNull(worker.schedule("test_map", "a", "b").get(30, TimeUnit.SECONDS));
            assertNotNull(worker.getExecuteThread());

            AtomicReference<Throwable> submitError = new AtomicReference<>();
            AtomicReference<CompletableFuture<Object>> submitFuture = new AtomicReference<>();
            Thread submitter = new Thread(() -> {
                try {
                    submitFuture.set(worker.schedule("test_map", "x", "y"));
                } catch (Throwable e) {
                    submitError.set(e);
                }
            }, "cra-w01-submit");
            // 闩阻塞点在委托 super.add 之前（m1 伪绿防线一）：任务入队前提交线程即被闩住，
            // worker 终局排空不可能取走它——pre-fix 搁浅 / post-fix 复查拒绝均确定可达
            queue.blockedThread = submitter;
            submitter.start();
            awaitCondition("submitter never reached the latch", 10000,
                    () -> submitter.getState() == Thread.State.WAITING
                            || submitter.getState() == Thread.State.TIMED_WAITING);

            closer.accept(worker);
            // 轮询至 worker 线程终止（终局序列完成：closed=true → 排空 → safeClose → executeThread=null）
            awaitCondition("worker thread not terminated after close", 5000,
                    () -> worker.getExecuteThread() == null);
            workerThread.join(5000);

            // 放闸：提交线程越过闩完成入队
            queue.latch.countDown();
            submitter.join(10000);
            assertFalse(submitter.isAlive(), "submitter must finish after latch release");

            // (a) 拒绝语义：同步拒绝（通道①，与前置检查同码）或 future 在超时内真实完成
            if (submitError.get() != null) {
                assertTrue(submitError.get() instanceof NopException,
                        "expected ERR_JS_CONTEXT_ALREADY_CLOSED, got " + submitError.get());
                assertEquals("nop.err.js.context-already-closed",
                        ((NopException) submitError.get()).getErrorCode());
            } else {
                CompletableFuture<Object> future = submitFuture.get();
                // pre-fix：任务搁浅（终局排空之后入队、无消费者）——get 超时即红
                Object result = future.get(5, TimeUnit.SECONDS);
                assertNotNull(result);
            }
            // (c) 搁浅零残留：复查失败通路含同引用队列移除 → 终局后队列必空
            assertEquals(0, worker.getWaitingTasks(), "no stranded task may remain after shutdown");
            // (b) close 链零劣化绿锚：worker 线程已终止
            assertFalse(workerThread.isAlive());
        } finally {
            queue.latch.countDown(); // 收尾必放闸，防测试自身挂死
        }
    }

    // 红①(d) 失效层②：死 worker 存活过滤 + reinitWorkers 容错 + 全死同步拒绝
    @Test
    public void testDeadWorkerRoutingFilteredAndReinitWorkersTolerant() throws Exception {
        JavaScriptService service = newService();
        service.setWorkerCount(2);
        service.start();
        try {
            // 预热
            assertNotNull(service.invokeAsync("test_map", "a", "b").get(30, TimeUnit.SECONDS));
            JavaScriptWorker[] workers = service.snapshotWorkers();
            assertEquals(2, workers.length);

            // 关闭 index 0 worker 并等待其线程终止
            workers[0].close();
            awaitCondition("dead worker thread not terminated", 5000,
                    () -> workers[0].getExecuteThread() == null);

            // 存活路由：死 worker 排除出候选集，连续 10 次全部成功（pre-fix：零等待快路径
            // 优先命中死 worker → schedule 同步抛错——红）
            for (int i = 0; i < 10; i++) {
                assertNotNull(service.invokeAsync("test_map", "a", "b").get(5, TimeUnit.SECONDS),
                        "invokeAsync #" + i + " must route to a live worker");
            }

            // reinitWorkers 容错（m6）：死 worker 的同步拒绝只跳过，不中止循环；
            // 存活 worker 的 reinit 任务被消费（completedTasks 增量：reinit + FIFO 后续调用）
            long completedBefore = workers[1].getCompletedTasks();
            assertDoesNotThrow(() -> service.reinitWorkers(),
                    "reinitWorkers must skip closed workers instead of aborting the loop");
            assertNotNull(service.invokeAsync("test_map", "a", "b").get(5, TimeUnit.SECONDS));
            assertTrue(workers[1].getCompletedTasks() >= completedBefore + 2,
                    "survivor must consume the reinit task and the follow-up call");

            // 全部关闭 → 同步拒绝（既有码零回退锚）
            workers[1].close();
            awaitCondition("all workers dead", 5000,
                    () -> workers[1].getExecuteThread() == null);
            NopException ex = assertThrows(NopException.class,
                    () -> service.invokeAsync("test_map", "a", "b"));
            assertEquals("nop.err.js.context-already-closed", ex.getErrorCode());
        } finally {
            service.stop();
        }
    }

    // 红①(e) 失效层③：准入软上界 + 越界同步拒绝 + close pill 绕过容量界限（Major-3）
    @Test
    public void testQueueAdmissionBoundAndClosePillBypass() {
        JavaScriptWorker worker = new JavaScriptWorker(); // 未启动：无消费线程
        Consumer<Boolean> noop = b -> {
        };
        for (int i = 0; i < JavaScriptWorker.MAX_WAITING_TASKS; i++) {
            worker.execute(noop);
        }
        NopException ex = assertThrows(NopException.class, () -> worker.execute(noop),
                "admission must reject submissions beyond the soft bound");
        assertEquals("nop.err.js.worker-queue-full", ex.getErrorCode());
        assertEquals(Integer.valueOf(JavaScriptWorker.MAX_WAITING_TASKS),
                Integer.valueOf(((Number) ex.getParam(JsErrors.ARG_MAX_WAITING_TASKS)).intValue()));

        // close pill 直送队列、永不经准入检查：close 正常返回且 pill 入队（等待数 = 上界 + 1）
        assertDoesNotThrow(worker::close);
        assertEquals(JavaScriptWorker.MAX_WAITING_TASKS + 1, worker.getWaitingTasks());
    }

    // 红①(f) CL-201：build-then-swap + reinit future 显式完成 + 嵌套拒绝
    @Test
    public void testReinitBuildThenSwapAndNestedReinitRejected() throws Exception {
        JavaScriptService service = newService();
        service.start();
        try {
            assertNotNull(service.invokeAsync("test_map", "a", "b").get(30, TimeUnit.SECONDS));

            // 正常 reinit：future 正常完成（pre-fix：FUNC_REINIT 分支从不完成 future——红）
            CompletableFuture<Object> reinitFuture = service.invokeAsync(JsConstants.FUNC_REINIT);
            assertNull(reinitFuture.get(30, TimeUnit.SECONDS), "reinit future must complete normally");

            // 失败 reinit：init 失败 → future 以失败原因异常完成（pre-fix：future 永不完成——红）
            JavaScriptWorker worker = service.snapshotWorkers()[0];
            worker.setInitScriptPath("/nonexistent/cra-w01-no-such.mjs");
            CompletableFuture<Object> failFuture = service.invokeAsync(JsConstants.FUNC_REINIT);
            assertThrows(ExecutionException.class, () -> failFuture.get(30, TimeUnit.SECONDS),
                    "failed reinit must complete the future exceptionally");

            // 旧单元保持可用（pre-fix：先关后建，init 失败后 globals 悬挂——红）
            Map<?, ?> result = (Map<?, ?>) service.invokeAsync("test_map", "a", "b").get(5, TimeUnit.SECONDS);
            assertEquals("{a: 1, b: {c: [1, 2, \"a\"]}}", result.toString());

            // 嵌套拒绝：worker 线程内 invokeAsync("reinit") → 异常完成的 future
            //（pre-fix：进入重建分支且 future 永不完成——红）
            AtomicReference<CompletableFuture<Object>> nested = new AtomicReference<>();
            worker.execute(b -> nested.set(service.invokeAsync(JsConstants.FUNC_REINIT)));
            ExecutionException nestedEx = assertThrows(ExecutionException.class,
                    () -> nested.get().get(5, TimeUnit.SECONDS),
                    "nested FUNC_REINIT must complete exceptionally, not hang");
            assertTrue(nestedEx.getCause() instanceof IllegalStateException,
                    "nested rejection cause must be IllegalStateException, got " + nestedEx.getCause());

            // 嵌套拒绝后旧单元仍可服务（拒绝不得破坏旧 context）
            assertNotEquals(null, service.invokeAsync("test_map", "a", "b").get(5, TimeUnit.SECONDS));
        } finally {
            service.stop();
        }
    }

    // 红①(g) 服务层冒烟（非门禁性，K6）：stop 与并发 invokeAsync 交叠，future 全部完成
    @Test
    public void testStopDuringConcurrentInvokeSmoke() throws Exception {
        JavaScriptService service = newService();
        service.setWorkerCount(1);
        service.start();
        List<CompletableFuture<Object>> futures = new ArrayList<>();
        AtomicBoolean stopped = new AtomicBoolean(false);
        Thread submitter = new Thread(() -> {
            while (!stopped.get()) {
                try {
                    futures.add(service.invokeAsync("test_map", "a", "b"));
                } catch (Exception e) {
                    // 同步拒绝（停机窗口）也是合法终态：以异常完成的 future 记录
                    CompletableFuture<Object> failed = new CompletableFuture<>();
                    failed.completeExceptionally(e);
                    futures.add(failed);
                    break;
                }
            }
        }, "cra-w01-smoke-submit");
        submitter.start();
        Thread.sleep(300);
        service.stop();
        stopped.set(true);
        submitter.join(5000);

        // 完成性纪律：同步 get(5s)，TimeoutException 即红；ExecutionException/CancellationException
        // 均计为已完成
        for (int i = 0; i < futures.size(); i++) {
            try {
                futures.get(i).get(5, TimeUnit.SECONDS);
            } catch (ExecutionException | CancellationException e) {
                // 异常完成即已完成
            }
        }
    }
}

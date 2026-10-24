package io.nop.batch.core.impl;

import io.nop.api.core.context.IContext;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.ProcessResult;
import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchChunkProcessorProvider;
import io.nop.batch.core.IBatchConsumerProvider;
import io.nop.batch.core.IBatchLoaderProvider;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2496 1.7-C6-001 + C6-010（同 diff）回归：chunk 循环 lambda 的守卫起点上移到首行 +
 * catch 放宽 Throwable。任何 Error 级 Throwable（含注入点 propagateContext 所在的守卫生效前段）
 * 都必须使任务 future 异常终态化、afterComplete 关闭链执行——不再出现「future 永不完成 →
 * allOf 永不触发 → reader/writer/连接泄漏 + 任务行永久 RUNNING + 调用方 syncGet 挂死」。
 */
public class TestBatchTaskErrorTerminalization {

    static class SingleItemLoader implements IBatchLoaderProvider<String>, IBatchLoaderProvider.IBatchLoader<String> {
        @Override
        public IBatchLoader<String> setup(IBatchTaskContext context) {
            return this;
        }

        @Override
        public List<String> load(int batchSize, IBatchChunkContext context) {
            return List.of("a", "b");
        }
    }

    static class ErrorConsumer implements IBatchConsumerProvider.IBatchConsumer<String> {
        @Override
        public void consume(java.util.Collection<String> items, IBatchChunkContext context) {
            throw new LinkageError("processor-boom");
        }
    }

    private static IBatchChunkProcessorProvider<String> processorFor(IBatchConsumerProvider.IBatchConsumer<String> consumer) {
        return (loader, context) -> chunkContext -> {
            List<String> items = loader.load(100, chunkContext);
            if (!items.isEmpty())
                consumer.consume(items, chunkContext);
            return ProcessResult.STOP;
        };
    }

    /**
     * C6-010：注入点 = propagateContext（守卫起点之前的段——仅改 catch 宽度无法闭合的站点）。
     */
    static class ErrorPropagatingTask extends BatchTask<String> {
        ErrorPropagatingTask(java.util.concurrent.Executor executor, IBatchLoaderProvider<String> loader,
                             IBatchChunkProcessorProvider<String> processor) {
            super("test-c6-010", 0L, null, executor, 1, null, loader, processor, null, null, 0);
        }

        @Override
        protected void propagateContext(IContext ctx, IBatchTaskContext taskCtx) {
            throw new LinkageError("context-boom");
        }
    }

    private static BatchTask<String> buildBatchTask(IBatchLoaderProvider<String> loader,
                                                    IBatchChunkProcessorProvider<String> processor) {
        // 生产默认装配：executor = GlobalExecutors.cachedThreadPool()（BatchTask.getExecutor(null) 语义）
        return new BatchTask<>("test-c6", 0L, null, GlobalExecutors.cachedThreadPool(), 1, null,
                loader, processor, null, null, 0);
    }

    /**
     * 等待 future 结算（有界）。返回是否在时限内完成（含异常完成）。
     */
    private static boolean awaitSettled(CompletableFuture<?> future, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (!future.isDone() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        return future.isDone();
    }

    private static Throwable unwrap(Throwable err) {
        while (err != null && err instanceof CompletionException && err.getCause() != null)
            err = err.getCause();
        return err;
    }

    /**
     * C6-001：processor（用户脚本模拟位）抛 Error → 任务 future 必须异常终态、afterComplete 关闭链执行。
     * 修复前：Error 逃逸 chunk 循环 catch(Exception) → future 永不完成（悬挂）→ 调用方 syncGet 挂死。
     */
    @Test
    public void testProcessorErrorTerminalizesFutureAndFiresAfterComplete() throws Exception {
        BatchTask<String> task = buildBatchTask(new SingleItemLoader(), processorFor(new ErrorConsumer()));

        BatchTaskContextImpl context = new BatchTaskContextImpl();
        context.setTaskName("test-c6-001");

        CompletableFuture<Void> afterComplete = new CompletableFuture<>();
        AtomicReference<Throwable> afterCompleteError = new AtomicReference<>();
        context.onAfterComplete(err -> {
            afterCompleteError.set(err);
            FutureHelper.complete(afterComplete, null, null);
        });

        CompletableFuture<Void> future = task.executeAsync(context);

        assertTrue(awaitSettled(future, 5_000),
                "C6-001: task future must settle when processor throws Error (no hang)");
        assertTrue(future.isCompletedExceptionally(), "future must complete exceptionally");
        assertTrue(awaitSettled(afterComplete, 5_000),
                "C6-001: afterComplete close chain must run (reader/writer/connection release path)");
        Throwable err = unwrap(afterCompleteError.get());
        assertNotNull(err, "afterComplete must receive the error");
        assertTrue(err instanceof LinkageError,
                "original Error must be preserved as the task failure cause, got: " + err);
    }

    /**
     * C6-010：注入点移至 propagateContext（守卫生效前的段）。修复前该段在 try 之外，
     * Error 逃逸 → future 永不完成；修复后守卫起点 = lambda 首行，future 异常终态 + 关闭链执行。
     */
    @Test
    public void testPropagateContextErrorTerminalizesFutureAndFiresAfterComplete() throws Exception {
        ErrorPropagatingTask task = new ErrorPropagatingTask(GlobalExecutors.cachedThreadPool(),
                new SingleItemLoader(), processorFor((items, ctx) -> {
        }));

        BatchTaskContextImpl context = new BatchTaskContextImpl();
        context.setTaskName("test-c6-010");

        CompletableFuture<Void> afterComplete = new CompletableFuture<>();
        AtomicBoolean afterCompleteFired = new AtomicBoolean();
        context.onAfterComplete(err -> {
            afterCompleteFired.set(true);
            FutureHelper.complete(afterComplete, null, null);
        });

        CompletableFuture<Void> future = task.executeAsync(context);

        assertTrue(awaitSettled(future, 5_000),
                "C6-010: task future must settle when propagateContext throws Error (no hang)");
        assertTrue(future.isCompletedExceptionally());
        assertTrue(awaitSettled(afterComplete, 5_000),
                "C6-010: afterComplete close chain must run");
        assertTrue(afterCompleteFired.get());
    }
}

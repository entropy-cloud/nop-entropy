package io.nop.batch.core;

import io.nop.batch.core.consumer.RetryConsumeHelper;
import io.nop.batch.core.exceptions.BatchCancelException;
import io.nop.batch.core.impl.BatchTaskContextImpl;
import io.nop.batch.core.loader.PartitionDispatchLoaderProvider;
import io.nop.batch.core.loader.ResourceRecordLoaderProvider;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.nop.batch.core.BatchErrors.ERR_BATCH_CANCEL_PROCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * nop-batch 已知 bug 的回归测试（修复后启用）。
 * 见 ai-dev/analysis/2026-08/2026-08-16-nop-batch-design-and-concurrency-review.md
 *
 * 并发类测试不依赖时间等待：通过 CountDownLatch 精确编排线程推进点，
 * 被测钩子（loader/consumer/fetcher/processor）内按信号决定 报错/放行/阻塞，顺序完全由测试控制。
 * 所有 latch await 均带超时，遵循 docs-for-ai/02-core-guides/testing.md 的异步防挂起规则。
 */
@Timeout(30)
public class TestBatchBugVerification {

    static boolean await(CountDownLatch latch) {
        return await(latch, 5000);
    }

    static boolean await(CountDownLatch latch, long millis) {
        try {
            return latch.await(millis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Bug 1: setHistoryItemCount / incHistoryItemCount 写错计数器
    // 位置: BatchTaskContextImpl.java
    // ------------------------------------------------------------------
    @Test
    public void bug1_historyItemCountWrittenToProcessItemCount() {
        BatchTaskContextImpl ctx = new BatchTaskContextImpl();
        ctx.setProcessItemCount(10);
        ctx.setHistoryItemCount(5);

        assertEquals(5L, ctx.getHistoryItemCount());
        assertEquals(10L, ctx.getProcessItemCount());

        ctx.incHistoryItemCount(3);
        assertEquals(8L, ctx.getHistoryItemCount());
        assertEquals(10L, ctx.getProcessItemCount());
    }

    // ------------------------------------------------------------------
    // Bug 2: onConsumeEnd 把监听器注册到 onChunkTryEnd 列表
    // 位置: BatchTaskContextImpl.java
    // ------------------------------------------------------------------
    @Test
    public void bug2_onConsumeEndRegisteredToChunkTryEndList() {
        BatchTaskContextImpl ctx = new BatchTaskContextImpl();
        List<String> fired = new CopyOnWriteArrayList<>();

        ctx.onConsumeEnd((c, e) -> fired.add("consumeEnd"));
        ctx.onChunkTryEnd((c, e) -> fired.add("chunkTryEnd"));

        IBatchChunkContext chunk = ctx.newChunkContext();

        ctx.fireConsumeEnd(chunk, null);
        ctx.fireChunkTryEnd(chunk, null);

        // onConsumeEnd 注册的监听器在 fireConsumeEnd 时触发（且仅一次），
        // onChunkTryEnd 注册的监听器在 fireChunkTryEnd 时触发，互不串扰
        assertEquals(Arrays.asList("consumeEnd", "chunkTryEnd"), fired);
        assertEquals(1, fired.stream().filter("consumeEnd"::equals).count());
    }

    // ------------------------------------------------------------------
    // Bug 3: skip() aggregator 模式下每轮循环消费两条记录（resume 静默丢数据）
    // 位置: ResourceRecordLoaderProvider.skip()
    // resume(completedIndex=3) 时应只跳过 3 行，聚合 [1,2,3]，load 从行 4 继续。
    // ------------------------------------------------------------------
    @Test
    public void bug3_resumeWithAggregatorDoubleConsumesAndDropsRows() {
        List<String> aggregated = new ArrayList<>();

        ResourceRecordLoaderProvider<String> provider = new ResourceRecordLoaderProvider<>();
        provider.setResourcePath("/test.txt");
        provider.setResourceLocator(new DebugResourceLocator());
        DebugResourceRecordIO io = new DebugResourceRecordIO(10);
        provider.setRecordIO(io);
        provider.setAggregator(new IBatchAggregator<String, Object, Object>() {
            @Override
            public Object createCombinedValue(Map<String, Object> header, IBatchTaskContext context) {
                return aggregated;
            }

            @Override
            public void aggregate(String record, Object combinedValue) {
                aggregated.add(record);
            }
        });

        BatchTaskContextImpl ctx = new BatchTaskContextImpl();
        ctx.setTaskKey("bug3");
        ctx.setCompletedIndex(3); // 模拟断点续传: 前 3 行已完成

        IBatchLoaderProvider.IBatchLoader<String> loader = provider.setup(ctx);
        List<String> batch = loader.load(2, ctx.newChunkContext());

        // skip 聚合被跳过的 3 行，load 聚合读到的 2 行
        assertEquals(Arrays.asList("1", "2", "3", "4", "5"), aggregated);
        assertEquals(Arrays.asList("4", "5"), batch, "resume must continue from row 4");
        assertEquals(5L, io.getReadCount(), "skip 3 rows + load 2 rows");
    }

    // ------------------------------------------------------------------
    // 缺陷 4: chunk 失败后兄弟线程不停止（fail-fast 缺失）
    // 位置: BatchTask.java executeChunkLoop
    //
    // 确定性编排（concurrency=2 + pass-through processor）:
    // 1. F1/S1 均加载完成后，F1 消费者等待 S1 已记录（s1Recorded）再抛错 —— 保证失败发生在 S1 完成之后；
    // 2. 失败触发 context.cancel（修复点），cancel 回调 countDown cancelledLatch；
    // 3. 兄弟线程的 load#2（S2）显式等待 cancelledLatch —— S2 只会在 cancel 之后被投递；
    // 4. 修复后 BatchProcessorConsumer 的逐条 isCancelled 检查必然拒绝 S2（cancel 已发生），
    //    S2 的 consumer 永不执行；S3 永不加载。
    // ------------------------------------------------------------------
    @Test
    public void defect4_siblingThreadsKeepProcessingAfterChunkFailure() throws Exception {
        CountDownLatch failerLoaded = new CountDownLatch(1);
        CountDownLatch siblingLoaded = new CountDownLatch(1);
        CountDownLatch s1Recorded = new CountDownLatch(1);
        CountDownLatch failed = new CountDownLatch(1);
        CountDownLatch cancelled = new CountDownLatch(1);
        List<String> processedAll = new CopyOnWriteArrayList<>();
        AtomicInteger failerLoads = new AtomicInteger();
        AtomicInteger siblingLoads = new AtomicInteger();

        IBatchLoaderProvider<String> loaderProvider = taskCtx -> (batchSize, chunkCtx) -> {
            if (chunkCtx.getThreadIndex() == 0) {
                failerLoaded.countDown();
                return failerLoads.incrementAndGet() == 1
                        ? Collections.singletonList("F1") : Collections.emptyList();
            }
            int n = siblingLoads.incrementAndGet();
            if (n == 1) {
                siblingLoaded.countDown();
                return Collections.singletonList("S1");
            }
            if (n == 2) {
                // S2 只在任务被取消（fail-fast 生效）后才投递
                assertTrue(await(cancelled), "S2 must only be delivered after task cancellation");
                return Collections.singletonList("S2");
            }
            return Collections.emptyList();
        };

        IBatchConsumerProvider<String> consumerProvider = taskCtx -> (items, ctx) -> {
            for (String item : items) {
                if (item.startsWith("F")) {
                    // 失败必须发生在 S1 记录完成之后，消除调度偶然性
                    assertTrue(await(failerLoaded));
                    assertTrue(await(siblingLoaded));
                    assertTrue(await(s1Recorded));
                    failed.countDown();
                    throw new RuntimeException("boom on " + item);
                }
                // S1 是失败前的在途 chunk，直接记录；S2/S3 一旦到达 consumer 即为失败后继续处理的证据
                processedAll.add(item);
                if (item.equals("S1"))
                    s1Recorded.countDown();
            }
        };

        BatchTaskContextImpl ctx = new BatchTaskContextImpl();
        ctx.setTaskName("defect4");
        // cancel 回调：fail-fast 生效的确定性信号
        ctx.appendOnCancel(reason -> cancelled.countDown());

        CompletableFuture<Void> future = BatchTaskBuilder.<String, String>create()
                .loader(loaderProvider)
                .consumer(consumerProvider)
                .processor(taskCtx -> (item, out, pctx) -> out.accept(item)) // 启用逐条 isCancelled 检查
                .concurrency(2)
                .executor(GlobalExecutors.cachedThreadPool())
                .buildTask()
                .executeAsync(ctx).toCompletableFuture();

        // 任务最终以失败结束，且 cause 是原始异常（不是兄弟线程的 BatchCancelException）
        ExecutionException ex = assertThrows(ExecutionException.class, () -> future.get(20, TimeUnit.SECONDS));
        assertTrue(ex.getCause() instanceof RuntimeException, "cause should be original failure, got: " + ex.getCause());
        assertEquals("boom on F1", ex.getCause().getMessage());

        // fail-fast 后只应有 S1（失败前的在途 chunk）被处理，S2/S3 不被处理
        assertEquals(Collections.singletonList("S1"), processedAll,
                "no records may be consumed after failure");
    }

    // ------------------------------------------------------------------
    // 缺陷 5: allOf 的 CompletionException 包装使 BatchCancelException 状态映射失效
    // 位置: BatchTask.onTaskComplete → DaoBatchStateStore.getTaskStatus
    // ------------------------------------------------------------------
    @Test
    public void defect5_completionExceptionMasksBatchCancelException() throws Exception {
        List<Throwable> finalErrors = new CopyOnWriteArrayList<>();

        IBatchStateStore store = new IBatchStateStore() {
            @Override
            public void loadTaskState(IBatchTaskContext context) {
            }

            @Override
            public void saveTaskState(boolean complete, Throwable ex, IBatchTaskContext context) {
                if (complete)
                    finalErrors.add(ex);
            }
        };

        IBatchConsumerProvider<String> consumerProvider = taskCtx -> (items, ctx) -> {
            throw new BatchCancelException(ERR_BATCH_CANCEL_PROCESS);
        };

        BatchTaskContextImpl ctx = new BatchTaskContextImpl();
        ctx.setTaskName("defect5");

        CompletableFuture<Void> future = BatchTaskBuilder.<String, String>create()
                .loader(taskCtx -> (batchSize, chunkCtx) -> Collections.singletonList("X1"))
                .consumer(consumerProvider)
                .concurrency(1)
                .executor(GlobalExecutors.cachedThreadPool())
                .stateStore(store)
                .buildTask()
                .executeAsync(ctx).toCompletableFuture();

        ExecutionException ex = assertThrows(ExecutionException.class, () -> future.get(20, TimeUnit.SECONDS));
        assertTrue(ex.getCause() instanceof BatchCancelException,
                "future cause should be original BatchCancelException, got: " + ex.getCause());

        assertEquals(1, finalErrors.size(), "final saveTaskState(true, err) must be captured");
        Throwable err = finalErrors.get(0);
        assertTrue(err instanceof BatchCancelException,
                "state store must receive original BatchCancelException, got: " + err.getClass().getName());
    }

    // ------------------------------------------------------------------
    // 缺陷 6: 失败 chunk 的行被兄弟成功 chunk 压实越过 → completedIndex 越界 → resume 丢数据
    // 位置: ResourceRecordLoaderProvider.onChunkEnd
    //
    // 单线程受控顺序编排（该缺陷本质是 onChunkEnd 的调用顺序问题，无需真实并发）:
    // chunk1(行1-2) 失败 → chunk2(行3-4) 成功。正确语义: completedIndex 停在失败 chunk 之前，
    // resume 从行 1 重新投递。
    // ------------------------------------------------------------------
    @Test
    public void defect6_completedIndexAdvancesPastFailedChunk() {
        ResourceRecordLoaderProvider<String> provider = newLoaderProvider(6);

        BatchTaskContextImpl ctx = new BatchTaskContextImpl();
        ctx.setTaskKey("defect6");

        IBatchLoaderProvider.IBatchLoader<String> loader = provider.setup(ctx);

        IBatchChunkContext chunk1 = ctx.newChunkContext();
        List<String> list1 = loader.load(2, chunk1);
        chunk1.setChunkItems(list1); // onChunkEnd 遍历 getChunkItems

        IBatchChunkContext chunk2 = ctx.newChunkContext();
        List<String> list2 = loader.load(2, chunk2);
        chunk2.setChunkItems(list2);

        assertEquals(Arrays.asList("1", "2"), list1);
        assertEquals(Arrays.asList("3", "4"), list2);

        // chunk1 失败: 行1-2 的事务已回滚，不得计入完成前缀
        chunk1.completeExceptionally(new RuntimeException("chunk1 failed"));
        // chunk2 成功
        chunk2.complete();

        // 模拟重启: 用 completedIndex 恢复
        BatchTaskContextImpl resumeCtx = new BatchTaskContextImpl();
        resumeCtx.setTaskKey("defect6-resume");
        resumeCtx.setCompletedIndex(ctx.getCompletedIndex());
        IBatchLoaderProvider.IBatchLoader<String> resumeLoader = newLoaderProvider(6).setup(resumeCtx);
        List<String> resumed = resumeLoader.load(2, resumeCtx.newChunkContext());

        org.junit.jupiter.api.Assertions.assertAll(
                () -> assertEquals(0L, ctx.getCompletedIndex(),
                        "completedIndex must stop before failed chunk"),
                () -> assertEquals(Arrays.asList("1", "2"), resumed,
                        "resume must re-deliver failed rows 1-2, actual first batch: " + resumed));
    }

    private static ResourceRecordLoaderProvider<String> newLoaderProvider(int maxCount) {
        ResourceRecordLoaderProvider<String> p = new ResourceRecordLoaderProvider<>();
        p.setResourcePath("/test.txt");
        p.setResourceLocator(new DebugResourceLocator());
        p.setRecordIO(new DebugResourceRecordIO(maxCount));
        p.setSaveState(true);
        return p;
    }

    // ------------------------------------------------------------------
    // 缺陷 9: PartitionDispatch 同一分区跨页乱序
    // 位置: PartitionDispatchQueue.takeBatch（fetch 与 addBatch 的原子性）
    //
    // 确定性编排: T1 在 fetcher 内取到 page1(a1) 后扣住不放（此时页未入队）。
    // 旧实现: T2 可立即 fetch page2(a2) 并先入队处理 → 处理序 [a2,a1]；
    // 修复后: fetch+addBatch 串行化，T2 阻塞在 fetchMutex 上直到 page1 入队 → [a1,a2]。
    // 1 秒探测窗口仅用于在旧实现下暴露 T2 的抢先行为，修复后为固定等待成本。
    // ------------------------------------------------------------------
    @Test
    public void defect9_partitionProcessingOrderScrambledAcrossPages() throws Exception {
        AtomicInteger fetchCalls = new AtomicInteger();
        CountDownLatch page1Fetched = new CountDownLatch(1);
        CountDownLatch releasePage1 = new CountDownLatch(1);
        CountDownLatch t1Done = new CountDownLatch(1);
        CountDownLatch t2Done = new CountDownLatch(1);
        List<String> processingOrder = new CopyOnWriteArrayList<>();

        IBatchLoaderProvider<String> base = taskCtx -> (batchSize, ctx) -> {
            int n = fetchCalls.incrementAndGet();
            if (n == 1) {
                page1Fetched.countDown();
                // 扣住 page1: 在 fetcher 内部等待，此时未入队
                assertTrue(await(releasePage1), "page1 must be released");
                return Collections.singletonList("a1");
            }
            if (n == 2)
                return Collections.singletonList("a2");
            return Collections.emptyList();
        };

        PartitionDispatchLoaderProvider<String> provider =
                new PartitionDispatchLoaderProvider<>(base, 1, (k, ctx) -> 0);
        BatchTaskContextImpl taskCtx = new BatchTaskContextImpl();
        IBatchLoaderProvider.IBatchLoader<String> loader = provider.setup(taskCtx);

        Thread t1 = new Thread(() -> {
            IBatchChunkContext c = taskCtx.newChunkContext();
            c.setThreadIndex(0);
            List<String> r = loader.load(1, c);
            if (!r.isEmpty())
                processingOrder.add(r.get(0));
            c.complete(); // completeBatch 释放分区占用
            t1Done.countDown();
        });
        Thread t2 = new Thread(() -> {
            IBatchChunkContext c = taskCtx.newChunkContext();
            c.setThreadIndex(1);
            List<String> r = loader.load(1, c);
            if (!r.isEmpty())
                processingOrder.add(r.get(0));
            c.complete();
            t2Done.countDown();
        });

        try {
            t1.start();
            assertTrue(await(page1Fetched), "T1 must have fetched page1 (now blocked holding it)");
            t2.start();
            // 旧实现: T2 在 1s 内完成 a2 的处理（乱序证据）；修复后: T2 阻塞在 fetchMutex 上
            await(t2Done, 1000);

            releasePage1.countDown();
            assertTrue(await(t1Done), "T1 must finish processing a1");
            assertTrue(await(t2Done), "T2 must finish processing a2");

            assertEquals(Arrays.asList("a1", "a2"), processingOrder,
                    "same-partition records must be processed in source order");
        } finally {
            taskCtx.complete(); // queue.finish()
            t1.join(2000);
            t2.join(2000);
        }
    }

    // ------------------------------------------------------------------
    // 缺陷 11a: RetryConsumeHelper 对 null retryPolicy 不施加任何放弃/延迟逻辑
    // 位置: RetryConsumeHelper.checkRetry
    // ------------------------------------------------------------------
    @Test
    public void defect11a_nullRetryPolicyImposesNoGiveUp() {
        BatchTaskContextImpl taskCtx = new BatchTaskContextImpl();
        IBatchChunkContext chunkCtx = taskCtx.newChunkContext();

        // null policy: 首次失败即以原始异常放弃
        RuntimeException probe = assertThrows(RuntimeException.class,
                () -> RetryConsumeHelper.checkRetry(null, new RuntimeException("probe"), 1, chunkCtx));
        assertEquals("probe", probe.getMessage());

        AtomicInteger attempts = new AtomicInteger();
        IBatchConsumerProvider.IBatchConsumer<String> fail200ThenSucceed = (items, ctx) -> {
            if (attempts.incrementAndGet() <= 200)
                throw new RuntimeException("fail#" + attempts.get());
        };

        RuntimeException thrown = assertThrows(RuntimeException.class, () ->
                RetryConsumeHelper.retryConsume(null, fail200ThenSucceed, null,
                        Collections.singletonList("x"), chunkCtx));
        assertEquals("fail#1", thrown.getMessage());
        assertEquals(1, attempts.get(), "null retryPolicy must give up after first failure");
    }
}

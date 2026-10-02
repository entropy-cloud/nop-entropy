package io.nop.batch.core;

import io.nop.batch.core.IBatchConsumerProvider.IBatchConsumer;
import io.nop.batch.core.IBatchLoaderProvider.IBatchLoader;
import io.nop.batch.core.exceptions.BatchCancelException;
import io.nop.batch.core.impl.BatchTaskContextImpl;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * chunk 处理 / checkpoint（状态存储）/ 断点续传语义：
 * 1. 每个 chunk（含 EOF chunk）处理后与任务结束时都会落 checkpoint，load 先于任何 save；
 * 2. 断点续传时 history store 已标记的记录不再交给 consumer，且计入 history 计数而非完成计数；
 * 3. 任务失败/取消时 checkpoint 携带原始异常；fail-fast 让兄弟线程尽早停止且不重复消费。
 */
@Timeout(10)
public class TestBatchTaskCheckpoint {

    private static final int TOTAL = 7;

    /**
     * 游标式 loader：数据一次性给定，按 batchSize 切出 chunk；可配置在任务被取消后停止喂数据（模拟 fail-fast）。
     */
    static class ListLoader implements IBatchLoaderProvider<Integer>, IBatchLoader<Integer> {
        private final List<Integer> data;
        private final boolean stopWhenCancelled;
        private int cursor;

        ListLoader(List<Integer> data, boolean stopWhenCancelled) {
            this.data = data;
            this.stopWhenCancelled = stopWhenCancelled;
        }

        @Override
        public IBatchLoader<Integer> setup(IBatchTaskContext context) {
            return this;
        }

        @Override
        public synchronized List<Integer> load(int batchSize, IBatchChunkContext context) {
            if (stopWhenCancelled && context.getTaskContext().isCancelled())
                return Collections.emptyList();
            if (cursor >= data.size())
                return Collections.emptyList();
            int end = Math.min(cursor + batchSize, data.size());
            List<Integer> ret = new ArrayList<>(data.subList(cursor, end));
            cursor = end;
            return ret;
        }
    }

    /**
     * 消费记录器：可配置对满足条件的记录抛异常（模拟消费失败）。
     */
    static class RecordingConsumer implements IBatchConsumerProvider<Integer>, IBatchConsumer<Integer> {
        final Set<Integer> items = new ConcurrentSkipListSet<>();
        final AtomicInteger count = new AtomicInteger();
        final List<Integer> chunkSizes = new CopyOnWriteArrayList<>();
        final Predicate<Integer> failOn;

        RecordingConsumer(Predicate<Integer> failOn) {
            this.failOn = failOn;
        }

        @Override
        public IBatchConsumer<Integer> setup(IBatchTaskContext context) {
            return this;
        }

        @Override
        public void consume(Collection<Integer> batch, IBatchChunkContext context) {
            chunkSizes.add(batch.size());
            for (Integer item : batch) {
                if (failOn != null && failOn.test(item))
                    throw new IllegalStateException("consume fail at " + item);
                items.add(item);
                count.incrementAndGet();
            }
        }
    }

    static class SaveRecord {
        final boolean complete;
        final Throwable err;

        SaveRecord(boolean complete, Throwable err) {
            this.complete = complete;
            this.err = err;
        }
    }

    /**
     * checkpoint 状态存储记录器：记录调用顺序与每次保存的完成标记/异常。
     */
    static class RecordingStateStore implements IBatchStateStore {
        final List<String> events = new CopyOnWriteArrayList<>();
        final List<SaveRecord> saves = new CopyOnWriteArrayList<>();
        private final Consumer<IBatchTaskContext> onLoad;

        RecordingStateStore(Consumer<IBatchTaskContext> onLoad) {
            this.onLoad = onLoad;
        }

        @Override
        public void loadTaskState(IBatchTaskContext context) {
            events.add("load");
            if (onLoad != null)
                onLoad.accept(context);
        }

        @Override
        public void saveTaskState(boolean complete, Throwable ex, IBatchTaskContext context) {
            events.add(complete ? "save-complete" : "save-chunk");
            saves.add(new SaveRecord(complete, ex));
        }
    }

    /**
     * 基于集合的 history store：saveProcessed 记录已处理键，filterProcessed 过滤已处理记录。
     * 用于模拟断点续传：上次运行已处理过的记录不应再次交给 consumer。
     */
    static class SetHistoryStore implements IBatchRecordHistoryStore<Integer> {
        final Set<Integer> processed = ConcurrentHashMap.newKeySet();

        @Override
        public Collection<Integer> filterProcessed(Collection<Integer> records, IBatchChunkContext context) {
            List<Integer> ret = new ArrayList<>();
            for (Integer record : records) {
                if (!processed.contains(record))
                    ret.add(record);
            }
            return ret;
        }

        @Override
        public void saveProcessed(Collection<Integer> filtered, Throwable exception, IBatchChunkContext context) {
            processed.addAll(filtered);
        }
    }

    private static List<Integer> newData(int n) {
        List<Integer> data = new ArrayList<>(n);
        for (int i = 0; i < n; i++)
            data.add(i);
        return data;
    }

    private static BatchTaskBuilder<Integer, Integer> newBuilder(IBatchLoaderProvider<Integer> loader,
                                                                 IBatchConsumerProvider<Integer> consumer,
                                                                 int batchSize) {
        BatchTaskBuilder<Integer, Integer> builder = new BatchTaskBuilder<>();
        builder.taskName("test-checkpoint");
        builder.loader(loader).consumer(consumer);
        builder.batchSize(batchSize).concurrency(1);
        builder.executor(GlobalExecutors.cachedThreadPool());
        return builder;
    }

    @Test
    public void testCheckpointSavedPerChunkAndOnComplete() throws Exception {
        // 7条数据、batchSize=3 -> 3个数据chunk + 1个EOF chunk（EOF chunk同样落一次chunk级checkpoint）
        RecordingConsumer consumer = new RecordingConsumer(null);
        RecordingStateStore stateStore = new RecordingStateStore(null);
        BatchTaskBuilder<Integer, Integer> builder = newBuilder(new ListLoader(newData(TOTAL), false), consumer, 3);
        builder.stateStore(stateStore);

        CompletionStage<Void> future = builder.buildTask().executeAsync(new BatchTaskContextImpl());
        future.toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(TOTAL, consumer.count.get());
        assertEquals("load", stateStore.events.get(0), "loadTaskState must run before any save");
        long chunkSaves = stateStore.events.stream().filter("save-chunk"::equals).count();
        assertEquals(4, chunkSaves, "3 data chunks + 1 EOF chunk each save a checkpoint");
        assertEquals("save-complete", stateStore.events.get(stateStore.events.size() - 1));

        SaveRecord last = stateStore.saves.get(stateStore.saves.size() - 1);
        assertTrue(last.complete);
        assertNull(last.err);
    }

    @Test
    public void testResumeSkipsHistoryProcessedItems() throws Exception {
        // 第一次运行：全部处理完成
        SetHistoryStore history = new SetHistoryStore();
        RecordingConsumer firstRun = new RecordingConsumer(null);
        BatchTaskBuilder<Integer, Integer> builder = newBuilder(new ListLoader(newData(TOTAL), false), firstRun, 3);
        builder.historyStore(history);
        BatchTaskContextImpl firstCtx = new BatchTaskContextImpl();
        builder.buildTask().executeAsync(firstCtx).toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(TOTAL, firstRun.count.get());
        assertEquals(TOTAL, firstCtx.getCompleteItemCount());
        assertEquals(0, firstCtx.getHistoryItemCount());

        // 模拟断点续传：前一个chunk(0,1,2)已确认处理，重启后不得重复消费
        SetHistoryStore resumedHistory = new SetHistoryStore();
        resumedHistory.processed.addAll(Set.of(0, 1, 2));
        RecordingConsumer secondRun = new RecordingConsumer(null);
        BatchTaskBuilder<Integer, Integer> builder2 = newBuilder(new ListLoader(newData(TOTAL), false), secondRun, 3);
        builder2.historyStore(resumedHistory);
        BatchTaskContextImpl secondCtx = new BatchTaskContextImpl();
        builder2.buildTask().executeAsync(secondCtx).toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(Set.of(3, 4, 5, 6), secondRun.items, "resumed run must not re-consume processed items");
        assertEquals(4, secondRun.count.get());
        assertEquals(4, secondCtx.getCompleteItemCount(), "only unprocessed items count as completed");
        assertEquals(3, secondCtx.getHistoryItemCount(), "processed items are accounted as history");
    }

    @Test
    public void testTaskFailureSavesErrorCheckpoint() throws Exception {
        RecordingConsumer consumer = new RecordingConsumer(item -> true);
        RecordingStateStore stateStore = new RecordingStateStore(null);
        BatchTaskBuilder<Integer, Integer> builder = newBuilder(new ListLoader(newData(TOTAL), false), consumer, 3);
        builder.stateStore(stateStore);

        CompletionStage<Void> future = builder.buildTask().executeAsync(new BatchTaskContextImpl());

        ExecutionException ex = assertThrows(ExecutionException.class, () -> future.toCompletableFuture().get(5, TimeUnit.SECONDS));
        assertInstanceOf(IllegalStateException.class, ex.getCause());
        assertEquals("consume fail at 0", ex.getCause().getMessage());

        SaveRecord last = stateStore.saves.get(stateStore.saves.size() - 1);
        assertTrue(last.complete, "terminal save always carries complete=true");
        assertInstanceOf(IllegalStateException.class, last.err);

        long failedChunkSaves = stateStore.saves.stream()
                .filter(s -> !s.complete && s.err != null).count();
        assertTrue(failedChunkSaves >= 1, "failing chunk must save an error checkpoint");

        assertEquals("load", stateStore.events.get(0));
        // 消费在第一条记录上失败，没有任何记录被确认完成
        assertEquals(0, consumer.count.get());
    }

    @Test
    public void testCancelledBeforeStartSavesCancelCheckpoint() throws Exception {
        RecordingConsumer consumer = new RecordingConsumer(null);
        RecordingStateStore stateStore = new RecordingStateStore(null);
        BatchTaskBuilder<Integer, Integer> builder = newBuilder(new ListLoader(newData(TOTAL), false), consumer, 3);
        builder.stateStore(stateStore);

        BatchTaskContextImpl context = new BatchTaskContextImpl();
        context.cancel("test-cancel");
        CompletionStage<Void> future = builder.buildTask().executeAsync(context);

        ExecutionException ex = assertThrows(ExecutionException.class, () -> future.toCompletableFuture().get(5, TimeUnit.SECONDS));
        assertInstanceOf(BatchCancelException.class, ex.getCause());

        SaveRecord last = stateStore.saves.get(stateStore.saves.size() - 1);
        assertInstanceOf(BatchCancelException.class, last.err);
        assertTrue(last.complete);

        assertEquals(0, consumer.count.get());
        assertTrue(context.isCancelled());
    }

    @Test
    public void testFailFastStopsSiblingThreads() throws Exception {
        int total = 200_000;
        RecordingConsumer consumer = new RecordingConsumer(item -> item == 0);
        RecordingStateStore stateStore = new RecordingStateStore(null);
        BatchTaskBuilder<Integer, Integer> builder = newBuilder(new ListLoader(newData(total), true), consumer, 100);
        builder.stateStore(stateStore);
        builder.concurrency(3);

        BatchTaskContextImpl context = new BatchTaskContextImpl();
        CompletionStage<Void> future = builder.buildTask().executeAsync(context);

        ExecutionException ex = assertThrows(ExecutionException.class, () -> future.toCompletableFuture().get(8, TimeUnit.SECONDS));
        assertInstanceOf(IllegalStateException.class, ex.getCause());

        // fail-fast：失败后任务被取消、loader停止喂数据，剩余数据不允许被继续处理
        assertTrue(context.isCancelled(), "task must be cancelled after chunk failure");
        int consumed = consumer.count.get();
        assertTrue(consumed < total / 2, "fail-fast must stop processing early, consumed=" + consumed);
        assertEquals(consumed, consumer.items.size(), "concurrently consumed items must not duplicate");

        SaveRecord last = stateStore.saves.get(stateStore.saves.size() - 1);
        assertInstanceOf(IllegalStateException.class, last.err);
    }

    @Test
    public void testChunkBoundaryAndCountersAccumulate() throws Exception {
        RecordingConsumer consumer = new RecordingConsumer(null);
        BatchTaskBuilder<Integer, Integer> builder = newBuilder(new ListLoader(newData(TOTAL), false), consumer, 3);
        builder.processor(taskCtx -> (item, sink, chunkCtx) -> sink.accept(item * 10));

        BatchTaskContextImpl context = new BatchTaskContextImpl();
        builder.buildTask().executeAsync(context).toCompletableFuture().get(5, TimeUnit.SECONDS);

        // chunk 边界：7条数据按 batchSize=3 切为 3,3,1
        assertEquals(List.of(3, 3, 1), consumer.chunkSizes);
        // processor输出(item*10)不重不漏且顺序稳定（concurrency=1）
        assertEquals(List.of(0, 10, 20, 30, 40, 50, 60), new ArrayList<>(consumer.items));
        // 处理计数与完成计数
        assertEquals(TOTAL, context.getProcessItemCount());
        assertEquals(TOTAL, context.getCompleteItemCount());
        assertEquals(0, context.getHistoryItemCount());
        assertEquals(0, context.getErrorCount());
    }

    @Test
    public void testTaskKeyResolution() throws Exception {
        // 1) 无taskKey且无表达式：自动生成UUID
        RecordingConsumer consumer = new RecordingConsumer(null);
        BatchTaskBuilder<Integer, Integer> builder = newBuilder(new ListLoader(newData(1), false), consumer, 1);
        BatchTaskContextImpl ctx1 = new BatchTaskContextImpl();
        builder.buildTask().executeAsync(ctx1).toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertNotNull(ctx1.getTaskKey());
        assertTrue(ctx1.getTaskKey().length() >= 32, "generated task key should be a UUID-like value");

        // 2) 配置了taskKeyExpr：key由表达式求值得到
        RecordingConsumer consumer2 = new RecordingConsumer(null);
        BatchTaskBuilder<Integer, Integer> builder2 = newBuilder(new ListLoader(newData(1), false), consumer2, 1);
        builder2.taskKeyExpr((self, args, scope) -> "expr-key");
        BatchTaskContextImpl ctx2 = new BatchTaskContextImpl();
        builder2.buildTask().executeAsync(ctx2).toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertEquals("expr-key", ctx2.getTaskKey());

        // 3) context已携带taskKey：外部值优先，表达式不生效
        RecordingConsumer consumer3 = new RecordingConsumer(null);
        BatchTaskBuilder<Integer, Integer> builder3 = newBuilder(new ListLoader(newData(1), false), consumer3, 1);
        builder3.taskKeyExpr((self, args, scope) -> "expr-key");
        BatchTaskContextImpl ctx3 = new BatchTaskContextImpl();
        ctx3.setTaskKey("ctx-key");
        builder3.buildTask().executeAsync(ctx3).toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertEquals("ctx-key", ctx3.getTaskKey());
    }

    @Test
    public void testAllowStartIfCompleteAndStartLimitPropagation() throws Exception {
        RecordingConsumer consumer = new RecordingConsumer(null);
        BatchTaskBuilder<Integer, Integer> builder = newBuilder(new ListLoader(newData(2), false), consumer, 1);
        builder.allowStartIfComplete(false).startLimit(3);

        BatchTaskContextImpl ctx = new BatchTaskContextImpl();
        assertNull(ctx.getAllowStartIfComplete(), "fresh context has no start restriction");
        assertEquals(0, ctx.getStartLimit());
        builder.buildTask().executeAsync(ctx).toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertEquals(Boolean.FALSE, ctx.getAllowStartIfComplete(), "builder value propagates to context");
        assertEquals(3, ctx.getStartLimit());

        // context上已设置的值优先于builder配置
        RecordingConsumer consumer2 = new RecordingConsumer(null);
        BatchTaskBuilder<Integer, Integer> builder2 = newBuilder(new ListLoader(newData(2), false), consumer2, 1);
        builder2.allowStartIfComplete(false).startLimit(3);
        BatchTaskContextImpl ctx2 = new BatchTaskContextImpl();
        ctx2.setAllowStartIfComplete(true);
        ctx2.setStartLimit(5);
        builder2.buildTask().executeAsync(ctx2).toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertEquals(Boolean.TRUE, ctx2.getAllowStartIfComplete(), "explicit context value must win");
        assertEquals(5, ctx2.getStartLimit(), "explicit context startLimit must win");
    }
}

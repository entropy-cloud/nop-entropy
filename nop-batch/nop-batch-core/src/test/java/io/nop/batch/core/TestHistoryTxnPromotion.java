package io.nop.batch.core;

import io.nop.api.core.util.FutureHelper;
import io.nop.batch.core.impl.BatchTaskContextImpl;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.functional.IFunctionInvoker;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * historyStore的saveProcessed必须与业务consume处于同一事务（InvokerBatchConsumer的invoke深度>0），
 * 否则"业务已提交、history未写"的崩溃窗口会导致重启后重复处理。
 * consume scope下BatchTaskBuilder应自动把consumer链的事务包装提升到process级（WithHistory外侧）。
 */
public class TestHistoryTxnPromotion {

    static class TrackingInvoker implements IFunctionInvoker {
        final AtomicInteger depth = new AtomicInteger();

        boolean inTxn() {
            return depth.get() > 0;
        }

        @Override
        public <R, T> T invoke(Function<R, T> fn, R request) {
            depth.incrementAndGet();
            try {
                return fn.apply(request);
            } finally {
                depth.decrementAndGet();
            }
        }
    }

    static class RecordingHistoryStore implements IBatchRecordHistoryStore<String> {
        final TrackingInvoker invoker;
        final List<Boolean> saveInTxnFlags = new ArrayList<>();
        final List<Boolean> filterInTxnFlags = new ArrayList<>();

        RecordingHistoryStore(TrackingInvoker invoker) {
            this.invoker = invoker;
        }

        @Override
        public Collection<String> filterProcessed(Collection<String> records, IBatchChunkContext context) {
            filterInTxnFlags.add(invoker.inTxn());
            return new ArrayList<>(records);
        }

        @Override
        public void saveProcessed(Collection<String> filtered, Throwable exception, IBatchChunkContext context) {
            saveInTxnFlags.add(invoker.inTxn());
        }
    }

    static class ListLoader implements IBatchLoaderProvider.IBatchLoader<String>, IBatchLoaderProvider<String> {
        private final List<String> data;
        private int index;

        ListLoader(List<String> data) {
            this.data = data;
        }

        @Override
        public IBatchLoader<String> setup(IBatchTaskContext context) {
            return this;
        }

        @Override
        public synchronized List<String> load(int batchSize, IBatchChunkContext context) {
            if (index >= data.size())
                return new ArrayList<>();
            List<String> ret = new ArrayList<>();
            while (index < data.size() && ret.size() < batchSize) {
                ret.add(data.get(index++));
            }
            return ret;
        }
    }

    static class RecordingConsumer implements IBatchConsumerProvider.IBatchConsumer<String>, IBatchConsumerProvider<String> {
        final TrackingInvoker invoker;
        final List<Boolean> consumeInTxnFlags = new ArrayList<>();
        final List<String> consumed = new ArrayList<>();

        RecordingConsumer(TrackingInvoker invoker) {
            this.invoker = invoker;
        }

        @Override
        public IBatchConsumer<String> setup(IBatchTaskContext context) {
            return this;
        }

        @Override
        public void consume(Collection<String> items, IBatchChunkContext context) {
            consumeInTxnFlags.add(invoker.inTxn());
            consumed.addAll(items);
        }
    }

    private void runTask(BatchTaskBuilder<String, String> builder, TrackingInvoker invoker,
                         RecordingHistoryStore history, RecordingConsumer consumer,
                         List<String> source) {
        builder.loader(new ListLoader(source))
                .consumer(consumer)
                .concurrency(1)
                .executor(GlobalExecutors.cachedThreadPool())
                .transactionalInvoker(invoker);
        if (history != null)
            builder.historyStore(history);

        BatchTaskContextImpl context = new BatchTaskContextImpl();
        context.setTaskName("test-history-txn");
        CompletableFuture<Void> future = new CompletableFuture<>();
        context.onAfterComplete(err -> FutureHelper.complete(future, null, err));

        builder.buildTask().executeAsync(context);
        FutureHelper.syncGet(future);
    }

    @Test
    public void testConsumeScopeWithHistoryPromotesSaveIntoTxn() {
        TrackingInvoker invoker = new TrackingInvoker();
        RecordingHistoryStore history = new RecordingHistoryStore(invoker);
        RecordingConsumer consumer = new RecordingConsumer(invoker);

        BatchTaskBuilder<String, String> builder = new BatchTaskBuilder<>();
        builder.transactionScope(BatchTransactionScope.consume);
        runTask(builder, invoker, history, consumer, List.of("a", "b"));

        assertEquals(List.of("a", "b"), consumer.consumed);
        // 提升后：saveProcessed与consume都在invoke内
        assertEquals(1, history.saveInTxnFlags.size());
        assertTrue(history.saveInTxnFlags.get(0),
                "saveProcessed must run inside the consume transaction after auto-promotion, got flags=" + history.saveInTxnFlags);
        assertTrue(consumer.consumeInTxnFlags.get(0),
                "business consume must stay inside the transaction, got flags=" + consumer.consumeInTxnFlags);
    }

    @Test
    public void testConsumeScopeWithoutHistoryKeepsTxnOnBaseConsumerOnly() {
        TrackingInvoker invoker = new TrackingInvoker();
        RecordingConsumer consumer = new RecordingConsumer(invoker);

        BatchTaskBuilder<String, String> builder = new BatchTaskBuilder<>();
        builder.transactionScope(BatchTransactionScope.consume);
        runTask(builder, invoker, null, consumer, List.of("a"));

        assertEquals(List.of("a"), consumer.consumed);
        assertTrue(consumer.consumeInTxnFlags.get(0));
    }

    @Test
    public void testProcessScopeWithHistoryKeepsSaveInTxn() {
        TrackingInvoker invoker = new TrackingInvoker();
        RecordingHistoryStore history = new RecordingHistoryStore(invoker);
        RecordingConsumer consumer = new RecordingConsumer(invoker);

        BatchTaskBuilder<String, String> builder = new BatchTaskBuilder<>();
        builder.transactionScope(BatchTransactionScope.process);
        runTask(builder, invoker, history, consumer, List.of("a"));

        assertEquals(1, history.saveInTxnFlags.size());
        assertTrue(history.saveInTxnFlags.get(0));
        assertTrue(consumer.consumeInTxnFlags.get(0));
    }

    @Test
    public void testHistorySaveOutsideTxnIsRejectedByRegression() {
        // 模拟修复前的包装顺序：WithHistory在Invoker外侧 → saveProcessed在事务外。
        // 本断言钉死"saveInTxn必须为true"的契约；若有人把包装顺序改回去，此测试与上面的提升测试一起失败。
        TrackingInvoker invoker = new TrackingInvoker();
        RecordingHistoryStore history = new RecordingHistoryStore(invoker);

        // 直接构造修复前的错误嵌套：Invoker只包base，WithHistory在更外层
        IBatchConsumerProvider.IBatchConsumer<String> base = (items, ctx) -> {
        };
        io.nop.batch.core.consumer.InvokerBatchConsumer<String> invokerConsumer =
                new io.nop.batch.core.consumer.InvokerBatchConsumer<>(invoker, base);
        io.nop.batch.core.consumer.WithHistoryBatchConsumer<String> wrongNesting =
                new io.nop.batch.core.consumer.WithHistoryBatchConsumer<>(history, invokerConsumer, null);

        wrongNesting.consume(List.of("x"), new BatchTaskContextImpl().newChunkContext());

        assertEquals(1, history.saveInTxnFlags.size());
        assertEquals(Boolean.FALSE, history.saveInTxnFlags.get(0),
                "legacy nesting keeps saveProcessed outside txn — documents why promotion is required");
    }
}

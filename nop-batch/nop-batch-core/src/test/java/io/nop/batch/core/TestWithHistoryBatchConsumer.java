package io.nop.batch.core;

import io.nop.batch.core.IBatchConsumerProvider.IBatchConsumer;
import io.nop.batch.core.IBatchRecordHistoryStore;
import io.nop.batch.core.consumer.WithHistoryBatchConsumer;
import io.nop.batch.core.impl.BatchTaskContextImpl;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * filtered.contains(item)语义在HashSet化前后必须完全一致：
 * 部分记录被历史过滤时，historyConsumer收到的恰好是被过滤掉的补集；
 * 全部通过时不触发history路径。钉定O(n²)->O(n)优化的行为等价性。
 */
public class TestWithHistoryBatchConsumer {

    static class FixedHistoryStore implements IBatchRecordHistoryStore<String> {
        final Set<String> processed;

        FixedHistoryStore(Set<String> processed) {
            this.processed = processed;
        }

        @Override
        public Collection<String> filterProcessed(Collection<String> records, IBatchChunkContext context) {
            List<String> ret = new ArrayList<>();
            for (String r : records) {
                if (!processed.contains(r))
                    ret.add(r);
            }
            return ret;
        }

        @Override
        public void saveProcessed(Collection<String> filtered, Throwable exception, IBatchChunkContext context) {
        }
    }

    private IBatchChunkContext newChunkContext() {
        return new BatchTaskContextImpl().newChunkContext();
    }

    @Test
    public void testPartiallyFilteredRecordsGoToHistoryConsumer() {
        List<String> consumed = new ArrayList<>();
        List<String> historyConsumed = new ArrayList<>();

        IBatchConsumer<String> consumer = new IBatchConsumer<String>() {
            @Override
            public void consume(Collection<String> items, IBatchChunkContext context) {
                consumed.addAll(items);
            }
        };
        IBatchConsumer<String> historyConsumer = new IBatchConsumer<String>() {
            @Override
            public void consume(Collection<String> items, IBatchChunkContext context) {
                historyConsumed.addAll(items);
            }
        };

        WithHistoryBatchConsumer<String> c = new WithHistoryBatchConsumer<>(
                new FixedHistoryStore(new HashSet<>(Arrays.asList("b", "d"))), consumer, historyConsumer);

        c.consume(Arrays.asList("a", "b", "c", "d"), newChunkContext());

        assertEquals(Arrays.asList("a", "c"), consumed);
        // 被历史过滤掉的b、d恰好进入historyConsumer（补集，与contains语义一致）
        assertEquals(Arrays.asList("b", "d"), historyConsumed);
    }

    @Test
    public void testAllPassThroughDoesNotTriggerHistory() {
        List<String> consumed = new ArrayList<>();
        List<String> historyConsumed = new ArrayList<>();

        IBatchConsumer<String> consumer = new IBatchConsumer<String>() {
            @Override
            public void consume(Collection<String> items, IBatchChunkContext context) {
                consumed.addAll(items);
            }
        };
        IBatchConsumer<String> historyConsumer = new IBatchConsumer<String>() {
            @Override
            public void consume(Collection<String> items, IBatchChunkContext context) {
                historyConsumed.addAll(items);
            }
        };

        WithHistoryBatchConsumer<String> c = new WithHistoryBatchConsumer<>(
                new FixedHistoryStore(new HashSet<>()), consumer, historyConsumer);

        c.consume(Arrays.asList("a", "b"), newChunkContext());

        assertEquals(Arrays.asList("a", "b"), consumed);
        assertEquals(0, historyConsumed.size());
    }
}

package io.nop.stream.runtime.acceptance;

import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.operators.AbstractStreamOperator;
import io.nop.stream.core.operators.OneInputStreamOperator;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * U4 (deferred-unlock, plan 2377) E2E fixture beans: two sources whose DSL nodes
 * declare distinct tableIds, a {@code <custom>} observer operator that reads the
 * record-level metadata (the only user-level seam onto StreamRecord), and a plain
 * collecting sink.
 */
public class TableIdE2EBeans {

    /** Emits the "orders" records. */
    public static class OrdersSource implements SourceFunction<Map<String, Object>> {
        private static final long serialVersionUID = 1L;

        @Override
        public void run(SourceContext<Map<String, Object>> ctx) {
            for (int i = 1; i <= 3; i++) {
                ctx.collect(Map.of("kind", "order", "id", i));
            }
        }

        @Override
        public void cancel() {
        }
    }

    /** Emits the "users" records. */
    public static class UsersSource implements SourceFunction<Map<String, Object>> {
        private static final long serialVersionUID = 1L;

        @Override
        public void run(SourceContext<Map<String, Object>> ctx) {
            for (int i = 1; i <= 2; i++) {
                ctx.collect(Map.of("kind", "user", "id", i));
            }
        }

        @Override
        public void cancel() {
        }
    }

    /**
     * Metadata observer: reads {@code element.getTableId()} (only reachable from a full
     * operator — user functions see values, not records) and forwards THE SAME record
     * instance downstream — rebuilding a StreamRecord here would silently drop the
     * declared metadata.
     */
    public static class TableIdObserver
            extends AbstractStreamOperator<Map<String, Object>>
            implements OneInputStreamOperator<Map<String, Object>, Map<String, Object>> {

        private static final long serialVersionUID = 1L;

        /** observed value-key + tableId pairs, in arrival order. */
        public static final List<String[]> OBSERVED = new ArrayList<>();

        @Override
        public void processElement(StreamRecord<Map<String, Object>> element) throws Exception {
            Map<String, Object> value = element.getValue();
            OBSERVED.add(new String[]{(String) value.get("kind") + ":" + value.get("id"),
                    element.getTableId()});
            output.collect(element);
        }

        @Override
        public void processWatermark(Watermark mark) throws Exception {
            output.emitWatermark(mark);
        }
    }

    public static class CollectingSink implements SinkFunction<Map<String, Object>> {
        private static final long serialVersionUID = 1L;

        public static final List<Map<String, Object>> ROWS = new ArrayList<>();

        @Override
        public void consume(Map<String, Object> value) {
            ROWS.add(value);
        }
    }

    private TableIdE2EBeans() {
    }
}

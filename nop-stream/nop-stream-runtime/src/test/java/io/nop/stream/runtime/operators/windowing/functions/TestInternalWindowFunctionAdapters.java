package io.nop.stream.runtime.operators.windowing.functions;

import io.nop.stream.core.common.functions.ProcessWindowFunction;
import io.nop.stream.core.common.functions.WindowFunction;
import io.nop.stream.core.common.state.KeyedStateStore;
import io.nop.stream.core.util.Collector;
import io.nop.stream.core.util.OutputTag;
import io.nop.stream.core.windowing.windows.TimeWindow;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestInternalWindowFunctionAdapters {

    static class ListCollector<T> implements Collector<T> {
        final List<T> collected = new ArrayList<>();

        @Override
        public void collect(T record) {
            collected.add(record);
        }

        @Override
        public void close() {
        }
    }

    static class TestInternalWindowContext implements InternalWindowFunction.InternalWindowContext {
        private static final long serialVersionUID = 1L;

        @Override
        public long currentProcessingTime() {
            return 1000L;
        }

        @Override
        public long currentWatermark() {
            return 999L;
        }

        @Override
        public KeyedStateStore windowState() {
            return null;
        }

        @Override
        public KeyedStateStore globalState() {
            return null;
        }

        @Override
        public <X> void output(OutputTag<X> outputTag, X value) {
        }
    }

    @Test
    void testInternalSingleValueWindowFunction() throws Exception {
        InternalSingleValueWindowFunction<String, String, String, TimeWindow> fn =
                new InternalSingleValueWindowFunction<>((input, unused) -> "result:" + input);

        ListCollector<String> collector = new ListCollector<>();
        TimeWindow window = new TimeWindow(0, 1000);
        TestInternalWindowContext context = new TestInternalWindowContext();

        fn.process("key1", window, context, "hello", collector);

        assertEquals(1, collector.collected.size());
        assertEquals("result:hello", collector.collected.get(0));
    }

    @Test
    void testInternalSingleValueProcessWindowFunction() throws Exception {
        ProcessWindowFunction<String, String, String, TimeWindow> pwf =
                new ProcessWindowFunction<>() {
                    private static final long serialVersionUID = 1L;

                    @Override
                    public void process(String key, TimeWindow window, Iterable<String> input,
                                        Context context, Collector<String> out) {
                        StringBuilder sb = new StringBuilder();
                        sb.append("key=").append(key);
                        sb.append(",processingTime=").append(context.currentProcessingTime());
                        sb.append(",watermark=").append(context.currentWatermark());
                        sb.append(",values=");
                        for (String s : input) {
                            sb.append(s).append(",");
                        }
                        out.collect(sb.toString());
                    }
                };

        InternalSingleValueProcessWindowFunction<String, String, String, TimeWindow> fn =
                new InternalSingleValueProcessWindowFunction<>(pwf);

        ListCollector<String> collector = new ListCollector<>();
        TimeWindow window = new TimeWindow(0, 1000);
        TestInternalWindowContext context = new TestInternalWindowContext();

        fn.process("key1", window, context, "hello", collector);

        assertEquals(1, collector.collected.size());
        String result = collector.collected.get(0);
        assertTrue(result.contains("key=key1"));
        assertTrue(result.contains("processingTime=1000"));
        assertTrue(result.contains("watermark=999"));
        assertTrue(result.contains("values=hello,"));
    }

    @Test
    void testInternalIterableWindowFunction() throws Exception {
        WindowFunction<String, String, String, TimeWindow> wf =
                (key, window, input, out) -> {
                    StringBuilder sb = new StringBuilder();
                    sb.append("key=").append(key);
                    sb.append(",values=");
                    for (String s : input) {
                        sb.append(s).append(",");
                    }
                    out.collect(sb.toString());
                };

        InternalIterableWindowFunction<String, String, String, TimeWindow> fn =
                new InternalIterableWindowFunction<>(wf);

        ListCollector<String> collector = new ListCollector<>();
        TimeWindow window = new TimeWindow(0, 1000);
        TestInternalWindowContext context = new TestInternalWindowContext();

        fn.process("key1", window, context, Arrays.asList("a", "b", "c"), collector);

        assertEquals(1, collector.collected.size());
        assertEquals("key=key1,values=a,b,c,", collector.collected.get(0));
    }

    @Test
    void testInternalIterableProcessWindowFunction() throws Exception {
        ProcessWindowFunction<String, String, String, TimeWindow> pwf =
                new ProcessWindowFunction<>() {
                    private static final long serialVersionUID = 1L;

                    @Override
                    public void process(String key, TimeWindow window, Iterable<String> input,
                                        Context context, Collector<String> out) {
                        StringBuilder sb = new StringBuilder();
                        sb.append("key=").append(key);
                        sb.append(",processingTime=").append(context.currentProcessingTime());
                        sb.append(",watermark=").append(context.currentWatermark());
                        sb.append(",values=");
                        for (String s : input) {
                            sb.append(s).append(",");
                        }
                        out.collect(sb.toString());
                    }
                };

        InternalIterableProcessWindowFunction<String, String, String, TimeWindow> fn =
                new InternalIterableProcessWindowFunction<>(pwf);

        ListCollector<String> collector = new ListCollector<>();
        TimeWindow window = new TimeWindow(0, 1000);
        TestInternalWindowContext context = new TestInternalWindowContext();

        fn.process("key1", window, context, Arrays.asList("x", "y"), collector);

        assertEquals(1, collector.collected.size());
        String result = collector.collected.get(0);
        assertTrue(result.contains("key=key1"));
        assertTrue(result.contains("processingTime=1000"));
        assertTrue(result.contains("watermark=999"));
        assertTrue(result.contains("values=x,y,"));
    }
}

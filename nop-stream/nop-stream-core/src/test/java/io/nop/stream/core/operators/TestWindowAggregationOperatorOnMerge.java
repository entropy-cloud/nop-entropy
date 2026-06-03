package io.nop.stream.core.operators;

import io.nop.commons.tuple.Tuple2;
import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.test.TestOutput;
import io.nop.stream.core.util.Collector;
import io.nop.stream.core.windowing.assigners.MergingWindowAssigner;
import io.nop.stream.core.windowing.assigners.WindowAssigner;
import io.nop.stream.core.windowing.triggers.Trigger;
import io.nop.stream.core.windowing.triggers.TriggerResult;
import io.nop.stream.core.windowing.windows.Window;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class TestWindowAggregationOperatorOnMerge {

    static class TestWindow extends Window implements Comparable<TestWindow> {
        final long start;
        final long end;

        TestWindow(long start, long end) {
            this.start = start;
            this.end = end;
        }

        @Override public long maxTimestamp() { return end - 1; }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TestWindow)) return false;
            TestWindow that = (TestWindow) o;
            return start == that.start && end == that.end;
        }

        @Override public int hashCode() { return Objects.hash(start, end); }

        @Override public int compareTo(TestWindow o) {
            return Long.compare(start, o.start);
        }

        @Override public String toString() { return "[" + start + "-" + end + ")"; }
    }

    static class TestMergingAssigner extends MergingWindowAssigner<Object, TestWindow> {
        @Override
        public Collection<TestWindow> assignWindows(Object element, long timestamp, WindowAssignerContext context) {
            return Collections.singleton(new TestWindow(timestamp, timestamp + 10));
        }

        @Override
        public void mergeWindows(Collection<TestWindow> windows, MergeCallback<TestWindow> callback) {
            List<TestWindow> sorted = new ArrayList<>(windows);
            sorted.sort(Comparator.comparingLong(w -> w.start));
            if (sorted.size() > 1) {
                TestWindow merged = new TestWindow(sorted.get(0).start, sorted.get(sorted.size() - 1).end);
                callback.merge(sorted, merged);
            }
        }

        @Override public Trigger<Object, TestWindow> getDefaultTrigger(io.nop.core.context.IServiceContext svcCtx) {
            return new Trigger<Object, TestWindow>() {
                boolean mergeCalled = false;

                @Override public TriggerResult onElement(Object element, long timestamp, TestWindow window, TriggerContext ctx) {
                    return TriggerResult.CONTINUE;
                }

                @Override public TriggerResult onEventTime(long time, TestWindow window, TriggerContext ctx) {
                    return TriggerResult.CONTINUE;
                }

                @Override public TriggerResult onProcessingTime(long time, TestWindow window, TriggerContext ctx) {
                    return TriggerResult.CONTINUE;
                }

                @Override public void clear(TestWindow window, TriggerContext ctx) {}

                @Override public boolean canMerge() { return true; }

                @Override public void onMerge(TestWindow window, OnMergeContext ctx) {
                    mergeCalled = true;
                }
            };
        }

        @Override public boolean isEventTime() { return true; }
    }

    static class ListAgg implements WindowAggregationFunction<String, List<String>, String, String, TestWindow> {
        @Override public List<String> createAccumulator() { return new ArrayList<>(); }
        @Override public List<String> add(String value, List<String> acc) { acc.add(value); return acc; }
        @Override public List<String> merge(List<String> a, List<String> b) { a.addAll(b); return a; }
        @Override public void emitResult(String key, TestWindow window, List<String> acc, Collector<String> out) {
            out.collect(String.join(",", acc));
        }
    }

    @Test
    void onMergeIsCalledDuringWindowMerge() throws Exception {
        TestMergingAssigner assigner = new TestMergingAssigner();
        Trigger<Object, TestWindow> trigger = assigner.getDefaultTrigger(null);

        WindowAggregationOperator<String, List<String>, String, String, TestWindow> operator =
                new WindowAggregationOperator<>(assigner, trigger, new ListAgg(), Object::toString);

        TestOutput<String> output = new TestOutput<>();
        operator.setOutput((io.nop.stream.core.operators.Output) output);
        operator.open();
        operator.setCurrentKey("k1");

        operator.processElement(new StreamRecord<>("a", 5));
        operator.processElement(new StreamRecord<>("b", 12));

        operator.close();
    }
}

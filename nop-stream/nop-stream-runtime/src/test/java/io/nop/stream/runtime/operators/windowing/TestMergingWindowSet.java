package io.nop.stream.runtime.operators.windowing;

import io.nop.commons.tuple.Tuple2;
import io.nop.core.context.IServiceContext;
import io.nop.stream.core.common.state.ListState;
import io.nop.stream.core.common.state.State;
import io.nop.stream.core.windowing.assigners.MergingWindowAssigner;
import io.nop.stream.core.windowing.assigners.WindowAssigner;
import io.nop.stream.core.windowing.triggers.Trigger;
import io.nop.stream.core.windowing.windows.TimeWindow;
import io.nop.stream.core.windowing.windows.Window;
import jakarta.annotation.Nullable;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class TestMergingWindowSet {

    static class TestMergingWindowAssigner extends MergingWindowAssigner<Object, TimeWindow> {
        private static final long serialVersionUID = 1L;

        @Override
        public void mergeWindows(Collection<TimeWindow> windows, MergeCallback<TimeWindow> callback) {
            TimeWindow.mergeWindows(windows, callback);
        }

        @Override
        public Collection<TimeWindow> assignWindows(Object element, long timestamp, WindowAssignerContext context) {
            return Collections.emptyList();
        }

        @Override
        public Trigger<Object, TimeWindow> getDefaultTrigger(@Nullable IServiceContext serviceContext) {
            return null;
        }

        @Override
        public boolean isEventTime() {
            return true;
        }
    }

    static class InMemoryListState<T> implements ListState<T> {
        private final List<T> list = new ArrayList<>();

        @Override
        public Iterable<T> get() {
            return list;
        }

        @Override
        public void add(T value) {
            list.add(value);
        }

        @Override
        public void addAll(Iterable<T> values) {
            for (T v : values) {
                list.add(v);
            }
        }

        @Override
        public void update(Iterable<T> values) {
            list.clear();
            for (T v : values) {
                list.add(v);
            }
        }

        @Override
        public void clear() {
            list.clear();
        }

        public List<T> getList() {
            return list;
        }
    }

    private static MergingWindowSet<TimeWindow> createWindowSet(InMemoryListState<Tuple2<TimeWindow, TimeWindow>> state) throws Exception {
        return new MergingWindowSet<>(new TestMergingWindowAssigner(), state);
    }

    private static final MergingWindowSet.MergeFunction<TimeWindow> NOOP_MERGE_FUNCTION =
            (mergeResult, mergedWindows, stateWindowResult, mergedStateWindows) -> {};

    @Test
    public void testNoMergeNonOverlappingWindows() throws Exception {
        InMemoryListState<Tuple2<TimeWindow, TimeWindow>> state = new InMemoryListState<>();
        MergingWindowSet<TimeWindow> windowSet = createWindowSet(state);

        TimeWindow w1 = new TimeWindow(0, 10);
        TimeWindow w2 = new TimeWindow(20, 30);

        TimeWindow result1 = windowSet.addWindow(w1, NOOP_MERGE_FUNCTION);
        TimeWindow result2 = windowSet.addWindow(w2, NOOP_MERGE_FUNCTION);

        assertEquals(w1, result1);
        assertEquals(w2, result2);

        assertEquals(w1, windowSet.getStateWindow(w1));
        assertEquals(w2, windowSet.getStateWindow(w2));
    }

    @Test
    public void testIncrementalMerge() throws Exception {
        InMemoryListState<Tuple2<TimeWindow, TimeWindow>> state = new InMemoryListState<>();
        MergingWindowSet<TimeWindow> windowSet = createWindowSet(state);

        TimeWindow w1 = new TimeWindow(0, 10);
        TimeWindow w2 = new TimeWindow(5, 15);

        AtomicInteger mergeCount = new AtomicInteger(0);
        MergingWindowSet.MergeFunction<TimeWindow> mergeFn =
                (mergeResult, mergedWindows, stateWindowResult, mergedStateWindows) -> {
                    mergeCount.incrementAndGet();
                    assertEquals(new TimeWindow(0, 15), mergeResult);
                };

        windowSet.addWindow(w1, NOOP_MERGE_FUNCTION);
        TimeWindow result = windowSet.addWindow(w2, mergeFn);

        assertEquals(1, mergeCount.get());
        assertEquals(new TimeWindow(0, 15), result);

        TimeWindow stateWindow = windowSet.getStateWindow(new TimeWindow(0, 15));
        assertNotNull(stateWindow);
    }

    @Test
    public void testLateElementMerge() throws Exception {
        InMemoryListState<Tuple2<TimeWindow, TimeWindow>> state = new InMemoryListState<>();
        MergingWindowSet<TimeWindow> windowSet = createWindowSet(state);

        TimeWindow w1 = new TimeWindow(0, 10);
        TimeWindow w2 = new TimeWindow(3, 7);

        AtomicBoolean mergeCalled = new AtomicBoolean(false);
        MergingWindowSet.MergeFunction<TimeWindow> mergeFn =
                (mergeResult, mergedWindows, stateWindowResult, mergedStateWindows) -> {
                    mergeCalled.set(true);
                };

        windowSet.addWindow(w1, NOOP_MERGE_FUNCTION);
        TimeWindow result = windowSet.addWindow(w2, mergeFn);

        assertEquals(new TimeWindow(0, 10), result);
        assertFalse(mergeCalled.get());

        assertNotNull(windowSet.getStateWindow(new TimeWindow(0, 10)));
        assertNull(windowSet.getStateWindow(w2));
    }

    @Test
    public void testLargeWindowCoversSingleWindow() throws Exception {
        InMemoryListState<Tuple2<TimeWindow, TimeWindow>> state = new InMemoryListState<>();
        MergingWindowSet<TimeWindow> windowSet = createWindowSet(state);

        TimeWindow w1 = new TimeWindow(0, 10);
        TimeWindow w2 = new TimeWindow(0, 20);

        AtomicInteger mergeCount = new AtomicInteger(0);
        MergingWindowSet.MergeFunction<TimeWindow> mergeFn =
                (mergeResult, mergedWindows, stateWindowResult, mergedStateWindows) -> {
                    mergeCount.incrementAndGet();
                    assertEquals(new TimeWindow(0, 20), mergeResult);
                };

        windowSet.addWindow(w1, NOOP_MERGE_FUNCTION);
        TimeWindow result = windowSet.addWindow(w2, mergeFn);

        assertEquals(new TimeWindow(0, 20), result);
        assertEquals(1, mergeCount.get());
        assertNotNull(windowSet.getStateWindow(new TimeWindow(0, 20)));
    }

    @Test
    public void testLargeWindowCoversMultipleWindows() throws Exception {
        InMemoryListState<Tuple2<TimeWindow, TimeWindow>> state = new InMemoryListState<>();
        MergingWindowSet<TimeWindow> windowSet = createWindowSet(state);

        TimeWindow w1 = new TimeWindow(0, 10);
        TimeWindow w2 = new TimeWindow(15, 25);
        TimeWindow w3 = new TimeWindow(30, 40);
        TimeWindow wLarge = new TimeWindow(0, 50);

        AtomicInteger mergeCount = new AtomicInteger(0);
        MergingWindowSet.MergeFunction<TimeWindow> mergeFn =
                (mergeResult, mergedWindows, stateWindowResult, mergedStateWindows) -> {
                    mergeCount.incrementAndGet();
                };

        windowSet.addWindow(w1, NOOP_MERGE_FUNCTION);
        windowSet.addWindow(w2, NOOP_MERGE_FUNCTION);
        windowSet.addWindow(w3, NOOP_MERGE_FUNCTION);
        TimeWindow result = windowSet.addWindow(wLarge, mergeFn);

        assertEquals(new TimeWindow(0, 50), result);
        assertEquals(1, mergeCount.get());
        assertNotNull(windowSet.getStateWindow(new TimeWindow(0, 50)));
        assertNull(windowSet.getStateWindow(w1));
        assertNull(windowSet.getStateWindow(w2));
        assertNull(windowSet.getStateWindow(w3));
    }

    @Test
    public void testAddSameWindowIsIdempotent() throws Exception {
        InMemoryListState<Tuple2<TimeWindow, TimeWindow>> state = new InMemoryListState<>();
        MergingWindowSet<TimeWindow> windowSet = createWindowSet(state);

        TimeWindow w1 = new TimeWindow(0, 10);

        AtomicBoolean mergeCalled = new AtomicBoolean(false);
        MergingWindowSet.MergeFunction<TimeWindow> mergeFn =
                (mergeResult, mergedWindows, stateWindowResult, mergedStateWindows) -> {
                    mergeCalled.set(true);
                };

        windowSet.addWindow(w1, mergeFn);
        assertFalse(mergeCalled.get());

        windowSet.addWindow(w1, mergeFn);
        assertFalse(mergeCalled.get());

        assertEquals(w1, windowSet.getStateWindow(w1));
    }

    @Test
    public void testStateRestoreConsistency() throws Exception {
        InMemoryListState<Tuple2<TimeWindow, TimeWindow>> state = new InMemoryListState<>();

        MergingWindowSet<TimeWindow> windowSet1 = createWindowSet(state);
        windowSet1.addWindow(new TimeWindow(0, 10), NOOP_MERGE_FUNCTION);
        windowSet1.addWindow(new TimeWindow(5, 15), NOOP_MERGE_FUNCTION);
        windowSet1.persist();

        MergingWindowSet<TimeWindow> windowSet2 = createWindowSet(state);
        TimeWindow merged = new TimeWindow(0, 15);
        assertEquals(windowSet2.getStateWindow(merged), windowSet1.getStateWindow(merged));
    }

    @Test
    public void testPersistOnlyOnChanges() throws Exception {
        InMemoryListState<Tuple2<TimeWindow, TimeWindow>> state = new InMemoryListState<>();

        MergingWindowSet<TimeWindow> windowSet = createWindowSet(state);
        windowSet.persist();
        assertTrue(state.getList().isEmpty());

        windowSet.addWindow(new TimeWindow(0, 10), NOOP_MERGE_FUNCTION);
        windowSet.persist();
        assertFalse(state.getList().isEmpty());
        assertEquals(1, state.getList().size());
    }

    @Test
    public void testRetireWindow() throws Exception {
        InMemoryListState<Tuple2<TimeWindow, TimeWindow>> state = new InMemoryListState<>();
        MergingWindowSet<TimeWindow> windowSet = createWindowSet(state);

        TimeWindow w1 = new TimeWindow(0, 10);
        windowSet.addWindow(w1, NOOP_MERGE_FUNCTION);
        assertNotNull(windowSet.getStateWindow(w1));

        windowSet.retireWindow(w1);
        assertNull(windowSet.getStateWindow(w1));

        assertThrows(IllegalStateException.class, () -> windowSet.retireWindow(w1));
    }
}

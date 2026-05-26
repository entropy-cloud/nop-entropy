package io.nop.stream.core.windowing.assigners;

import io.nop.stream.core.windowing.windows.TimeWindow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import io.nop.stream.core.exceptions.StreamException;

public class TestEventTimeSessionWindows {

    private static final long SESSION_GAP = 5000;
    private EventTimeSessionWindows assigner;
    private MockWindowAssignerContext context;

    @BeforeEach
    public void setUp() {
        assigner = EventTimeSessionWindows.withGap(SESSION_GAP);
        context = new MockWindowAssignerContext();
    }

    @Test
    public void testSingleElementWindow() {
        long timestamp = 1000;
        Collection<TimeWindow> windows = assigner.assignWindows(null, timestamp, context);

        assertEquals(1, windows.size());
        TimeWindow window = windows.iterator().next();
        assertEquals(1000, window.getStart());
        assertEquals(1000 + SESSION_GAP, window.getEnd());
    }

    @Test
    public void testMergeOverlappingWindows() {
        TimeWindow w1 = new TimeWindow(1000, 6000);
        TimeWindow w2 = new TimeWindow(3000, 8000);

        List<TimeWindow> windows = Arrays.asList(w1, w2);
        List<Collection<TimeWindow>> mergedSets = new ArrayList<>();
        List<TimeWindow> mergedResults = new ArrayList<>();

        assigner.mergeWindows(windows, new MergingWindowAssigner.MergeCallback<TimeWindow>() {
            @Override
            public void merge(Collection<TimeWindow> toBeMerged, TimeWindow mergeResult) {
                mergedSets.add(toBeMerged);
                mergedResults.add(mergeResult);
            }
        });

        assertEquals(1, mergedSets.size());
        assertEquals(2, mergedSets.get(0).size());
        assertEquals(new TimeWindow(1000, 8000), mergedResults.get(0));
    }

    @Test
    public void testNoMergeWhenDisjoint() {
        TimeWindow w1 = new TimeWindow(1000, 6000);
        TimeWindow w2 = new TimeWindow(7000, 12000);

        List<TimeWindow> windows = Arrays.asList(w1, w2);
        List<Collection<TimeWindow>> mergedSets = new ArrayList<>();

        assigner.mergeWindows(windows, new MergingWindowAssigner.MergeCallback<TimeWindow>() {
            @Override
            public void merge(Collection<TimeWindow> toBeMerged, TimeWindow mergeResult) {
                mergedSets.add(toBeMerged);
            }
        });

        assertEquals(0, mergedSets.size());
    }

    @Test
    public void testLargeWindowCoversMultiple() {
        TimeWindow w1 = new TimeWindow(1000, 10000);
        TimeWindow w2 = new TimeWindow(3000, 8000);
        TimeWindow w3 = new TimeWindow(5000, 12000);

        List<TimeWindow> windows = Arrays.asList(w1, w2, w3);
        List<Collection<TimeWindow>> mergedSets = new ArrayList<>();
        List<TimeWindow> mergedResults = new ArrayList<>();

        assigner.mergeWindows(windows, new MergingWindowAssigner.MergeCallback<TimeWindow>() {
            @Override
            public void merge(Collection<TimeWindow> toBeMerged, TimeWindow mergeResult) {
                mergedSets.add(toBeMerged);
                mergedResults.add(mergeResult);
            }
        });

        assertEquals(1, mergedSets.size());
        assertEquals(3, mergedSets.get(0).size());
        assertEquals(new TimeWindow(1000, 12000), mergedResults.get(0));
    }

    @Test
    public void testIsEventTimeTrue() {
        assertTrue(assigner.isEventTime());
    }

    @Test
    public void testInvalidGapRejected() {
        assertThrows(StreamException.class, () -> EventTimeSessionWindows.withGap(0));
        assertThrows(StreamException.class, () -> EventTimeSessionWindows.withGap(-1));
        assertThrows(StreamException.class, () -> EventTimeSessionWindows.withGap(-1000));
    }

    @Test
    public void testWithGapDuration() {
        EventTimeSessionWindows durationAssigner = EventTimeSessionWindows.withGap(java.time.Duration.ofMillis(3000));
        assertEquals(3000, durationAssigner.getSessionTimeout());
    }

    @Test
    public void testMergeChainOfThree() {
        TimeWindow w1 = new TimeWindow(0, 5000);
        TimeWindow w2 = new TimeWindow(3000, 8000);
        TimeWindow w3 = new TimeWindow(7000, 12000);

        List<TimeWindow> windows = Arrays.asList(w1, w2, w3);
        List<Collection<TimeWindow>> mergedSets = new ArrayList<>();
        List<TimeWindow> mergedResults = new ArrayList<>();

        assigner.mergeWindows(windows, new MergingWindowAssigner.MergeCallback<TimeWindow>() {
            @Override
            public void merge(Collection<TimeWindow> toBeMerged, TimeWindow mergeResult) {
                mergedSets.add(toBeMerged);
                mergedResults.add(mergeResult);
            }
        });

        assertEquals(1, mergedSets.size());
        assertEquals(3, mergedSets.get(0).size());
        assertEquals(new TimeWindow(0, 12000), mergedResults.get(0));
    }

    private static class MockWindowAssignerContext implements WindowAssigner.WindowAssignerContext {
        @Override
        public long getCurrentProcessingTime() {
            return System.currentTimeMillis();
        }
    }
}

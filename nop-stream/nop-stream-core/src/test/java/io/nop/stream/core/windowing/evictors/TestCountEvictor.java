package io.nop.stream.core.windowing.evictors;

import io.nop.stream.core.windowing.utils.TimestampedValue;
import io.nop.stream.core.windowing.windows.TimeWindow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedList;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestCountEvictor {

    private TimeWindow window;
    private Evictor.EvictorContext ctx;

    @BeforeEach
    void setUp() {
        window = new TimeWindow(0, 1000);
        ctx = new Evictor.EvictorContext() {
            @Override
            public long getCurrentProcessingTime() {
                return 100;
            }

            @Override
            public long getCurrentWatermark() {
                return 50;
            }
        };
    }

    @Test
    void testEvictBeforeKeepsMaxCount() {
        CountEvictor<TimeWindow> evictor = CountEvictor.of(3);

        LinkedList<TimestampedValue<Object>> elements = new LinkedList<>(Arrays.asList(
                new TimestampedValue<>("a", 1),
                new TimestampedValue<>("b", 2),
                new TimestampedValue<>("c", 3),
                new TimestampedValue<>("d", 4),
                new TimestampedValue<>("e", 5)
        ));

        evictor.evictBefore(elements, 5, window, ctx);

        assertEquals(3, elements.size());
        assertEquals("c", elements.get(0).getValue());
        assertEquals("d", elements.get(1).getValue());
        assertEquals("e", elements.get(2).getValue());
    }

    @Test
    void testEvictBeforeNoEvictionBelowMax() {
        CountEvictor<TimeWindow> evictor = CountEvictor.of(5);

        LinkedList<TimestampedValue<Object>> elements = new LinkedList<>(Arrays.asList(
                new TimestampedValue<>("a", 1),
                new TimestampedValue<>("b", 2),
                new TimestampedValue<>("c", 3)
        ));

        evictor.evictBefore(elements, 3, window, ctx);

        assertEquals(3, elements.size());
    }

    @Test
    void testEvictAfterMode() {
        CountEvictor<TimeWindow> evictor = CountEvictor.of(2, true);

        LinkedList<TimestampedValue<Object>> elements = new LinkedList<>(Arrays.asList(
                new TimestampedValue<>("a", 1),
                new TimestampedValue<>("b", 2),
                new TimestampedValue<>("c", 3),
                new TimestampedValue<>("d", 4)
        ));

        evictor.evictBefore(elements, 4, window, ctx);
        assertEquals(4, elements.size());

        evictor.evictAfter(elements, 4, window, ctx);
        assertEquals(2, elements.size());
        assertEquals("c", elements.get(0).getValue());
        assertEquals("d", elements.get(1).getValue());
    }

    @Test
    void testEvictExactlyAtMaxCount() {
        CountEvictor<TimeWindow> evictor = CountEvictor.of(3);

        LinkedList<TimestampedValue<Object>> elements = new LinkedList<>(Arrays.asList(
                new TimestampedValue<>("a", 1),
                new TimestampedValue<>("b", 2),
                new TimestampedValue<>("c", 3)
        ));

        evictor.evictBefore(elements, 3, window, ctx);

        assertEquals(3, elements.size());
    }
}

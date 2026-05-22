package io.nop.stream.core.windowing.evictors;

import io.nop.stream.core.windowing.utils.TimestampedValue;
import io.nop.stream.core.windowing.windows.TimeWindow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedList;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestTimeEvictor {

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
    void testEvictOldElements() {
        TimeEvictor<TimeWindow> evictor = TimeEvictor.of(Duration.ofMillis(8));

        LinkedList<TimestampedValue<Object>> elements = new LinkedList<>(Arrays.asList(
                new TimestampedValue<>("a", 1),
                new TimestampedValue<>("b", 5),
                new TimestampedValue<>("c", 10),
                new TimestampedValue<>("d", 15)
        ));

        evictor.evictBefore(elements, 4, window, ctx);

        assertEquals(2, elements.size());
        assertEquals("c", elements.get(0).getValue());
        assertEquals("d", elements.get(1).getValue());
    }

    @Test
    void testNoEvictWhenAllWithinWindow() {
        TimeEvictor<TimeWindow> evictor = TimeEvictor.of(Duration.ofMillis(10));

        LinkedList<TimestampedValue<Object>> elements = new LinkedList<>(Arrays.asList(
                new TimestampedValue<>("a", 10),
                new TimestampedValue<>("b", 12),
                new TimestampedValue<>("c", 14)
        ));

        evictor.evictBefore(elements, 3, window, ctx);

        assertEquals(3, elements.size());
    }

    @Test
    void testEvictAfterMode() {
        TimeEvictor<TimeWindow> evictor = TimeEvictor.of(Duration.ofMillis(5), true);

        LinkedList<TimestampedValue<Object>> elements = new LinkedList<>(Arrays.asList(
                new TimestampedValue<>("a", 1),
                new TimestampedValue<>("b", 10),
                new TimestampedValue<>("c", 20)
        ));

        evictor.evictBefore(elements, 3, window, ctx);
        assertEquals(3, elements.size());

        evictor.evictAfter(elements, 3, window, ctx);
        assertEquals(1, elements.size());
        assertEquals("c", elements.get(0).getValue());
    }

    @Test
    void testNoTimestampElementsNotEvicted() {
        TimeEvictor<TimeWindow> evictor = TimeEvictor.of(Duration.ofMillis(5));

        LinkedList<TimestampedValue<Object>> elements = new LinkedList<>(Arrays.asList(
                new TimestampedValue<>("a"),
                new TimestampedValue<>("b"),
                new TimestampedValue<>("c")
        ));

        evictor.evictBefore(elements, 3, window, ctx);

        assertEquals(3, elements.size());
    }
}

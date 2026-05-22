package io.nop.stream.core.windowing.evictors;

import io.nop.stream.core.windowing.delta.DeltaFunction;
import io.nop.stream.core.windowing.utils.TimestampedValue;
import io.nop.stream.core.windowing.windows.TimeWindow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedList;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestDeltaEvictor {

    private TimeWindow window;
    private Evictor.EvictorContext ctx;
    private DeltaFunction<Double> deltaFunction;

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
        deltaFunction = (oldVal, newVal) -> Math.abs(newVal - oldVal);
    }

    @Test
    void testEvictAboveThreshold() {
        DeltaEvictor<Double, TimeWindow> evictor = DeltaEvictor.of(3.0, deltaFunction);

        LinkedList<TimestampedValue<Double>> elements = new LinkedList<>(Arrays.asList(
                new TimestampedValue<>(1.0),
                new TimestampedValue<>(5.0),
                new TimestampedValue<>(10.0)
        ));

        evictor.evictBefore(elements, 3, window, ctx);

        assertEquals(1, elements.size());
        assertEquals(10.0, elements.get(0).getValue());
    }

    @Test
    void testNoEvictBelowThreshold() {
        DeltaEvictor<Double, TimeWindow> evictor = DeltaEvictor.of(3.0, deltaFunction);

        LinkedList<TimestampedValue<Double>> elements = new LinkedList<>(Arrays.asList(
                new TimestampedValue<>(9.0),
                new TimestampedValue<>(10.0),
                new TimestampedValue<>(11.0)
        ));

        evictor.evictBefore(elements, 3, window, ctx);

        assertEquals(3, elements.size());
    }

    @Test
    void testEvictAfterMode() {
        DeltaEvictor<Double, TimeWindow> evictor = DeltaEvictor.of(3.0, deltaFunction, true);

        LinkedList<TimestampedValue<Double>> elements = new LinkedList<>(Arrays.asList(
                new TimestampedValue<>(1.0),
                new TimestampedValue<>(5.0),
                new TimestampedValue<>(10.0)
        ));

        evictor.evictBefore(elements, 3, window, ctx);
        assertEquals(3, elements.size());

        evictor.evictAfter(elements, 3, window, ctx);
        assertEquals(1, elements.size());
        assertEquals(10.0, elements.get(0).getValue());
    }

    @Test
    void testMixedThreshold() {
        DeltaEvictor<Double, TimeWindow> evictor = DeltaEvictor.of(3.0, deltaFunction);

        LinkedList<TimestampedValue<Double>> elements = new LinkedList<>(Arrays.asList(
                new TimestampedValue<>(1.0),
                new TimestampedValue<>(3.0),
                new TimestampedValue<>(5.0)
        ));

        evictor.evictBefore(elements, 3, window, ctx);

        assertEquals(2, elements.size());
        assertEquals(3.0, elements.get(0).getValue());
        assertEquals(5.0, elements.get(1).getValue());
    }
}

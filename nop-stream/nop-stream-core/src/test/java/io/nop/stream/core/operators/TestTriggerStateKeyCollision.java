package io.nop.stream.core.operators;

import org.junit.jupiter.api.Test;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

public class TestTriggerStateKeyCollision {

    static class TestWindow extends io.nop.stream.core.windowing.windows.Window {
        private final long start;
        private final long end;

        TestWindow(long start, long end) {
            this.start = start;
            this.end = end;
        }

        @Override
        public long maxTimestamp() {
            return end - 1;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TestWindow)) return false;
            TestWindow that = (TestWindow) o;
            return start == that.start && end == that.end;
        }

        @Override
        public int hashCode() {
            return Objects.hash(start, end);
        }

        @Override
        public String toString() {
            return "TestWindow[" + start + ", " + end + ")";
        }
    }

    @Test
    void testKeysWithHashSeparatorDoNotCollide() {
        String keyA = "user#1";
        String keyB = "user";
        TestWindow w1 = new TestWindow(0, 1000);
        TestWindow w2 = new TestWindow(0, 1000);
        String descriptor = "count";

        WindowAggregationOperator.WindowKey<String, TestWindow> wkA =
                new WindowAggregationOperator.WindowKey<>(keyA, w1);
        WindowAggregationOperator.WindowKey<String, TestWindow> wkB =
                new WindowAggregationOperator.WindowKey<>(keyB, w2);

        WindowAggregationOperator.TriggerStateKey<String, TestWindow> skA =
                new WindowAggregationOperator.TriggerStateKey<>(wkA, descriptor);
        WindowAggregationOperator.TriggerStateKey<String, TestWindow> skB =
                new WindowAggregationOperator.TriggerStateKey<>(wkB, descriptor);

        assertNotEquals(skA, skB,
                "Keys 'user#1' and 'user' with descriptor 'count' must not collide");
        assertNotEquals(skA.hashCode(), skB.hashCode(),
                "Hash codes should differ for structurally different keys");
    }

    @Test
    void testSameKeyDifferentDescriptorNoCollision() {
        String key = "user1";
        TestWindow w = new TestWindow(0, 1000);

        WindowAggregationOperator.WindowKey<String, TestWindow> wk =
                new WindowAggregationOperator.WindowKey<>(key, w);

        WindowAggregationOperator.TriggerStateKey<String, TestWindow> sk1 =
                new WindowAggregationOperator.TriggerStateKey<>(wk, "count");
        WindowAggregationOperator.TriggerStateKey<String, TestWindow> sk2 =
                new WindowAggregationOperator.TriggerStateKey<>(wk, "sum");

        assertNotEquals(sk1, sk2);
    }

    @Test
    void testSameKeyDifferentWindowNoCollision() {
        String key = "user1";
        TestWindow w1 = new TestWindow(0, 1000);
        TestWindow w2 = new TestWindow(1000, 2000);

        WindowAggregationOperator.TriggerStateKey<String, TestWindow> sk1 =
                new WindowAggregationOperator.TriggerStateKey<>(
                        new WindowAggregationOperator.WindowKey<>(key, w1), "count");
        WindowAggregationOperator.TriggerStateKey<String, TestWindow> sk2 =
                new WindowAggregationOperator.TriggerStateKey<>(
                        new WindowAggregationOperator.WindowKey<>(key, w2), "count");

        assertNotEquals(sk1, sk2);
    }

    @Test
    void testIdenticalKeysAreEqual() {
        String key = "user#1";
        TestWindow w = new TestWindow(0, 1000);

        WindowAggregationOperator.TriggerStateKey<String, TestWindow> sk1 =
                new WindowAggregationOperator.TriggerStateKey<>(
                        new WindowAggregationOperator.WindowKey<>(key, w), "count");
        WindowAggregationOperator.TriggerStateKey<String, TestWindow> sk2 =
                new WindowAggregationOperator.TriggerStateKey<>(
                        new WindowAggregationOperator.WindowKey<>(key, w), "count");

        assertEquals(sk1, sk2);
        assertEquals(sk1.hashCode(), sk2.hashCode());
    }

    @Test
    void testComplexKeyWithMultipleSeparators() {
        String keyA = "a#b#c#d";
        String keyB = "a#b#c";
        TestWindow w = new TestWindow(0, 1000);

        WindowAggregationOperator.TriggerStateKey<String, TestWindow> skA =
                new WindowAggregationOperator.TriggerStateKey<>(
                        new WindowAggregationOperator.WindowKey<>(keyA, w), "state");
        WindowAggregationOperator.TriggerStateKey<String, TestWindow> skB =
                new WindowAggregationOperator.TriggerStateKey<>(
                        new WindowAggregationOperator.WindowKey<>(keyB, w), "d#state");

        assertNotEquals(skA, skB,
                "Complex separator-based keys must not collide");
    }
}

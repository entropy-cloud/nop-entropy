package io.nop.stream.core.common.state.simple;

import io.nop.stream.core.common.accumulators.SimpleAccumulator;
import io.nop.stream.core.common.accumulators.Accumulator;
import io.nop.stream.core.common.functions.AggregateFunction;
import io.nop.stream.core.common.state.AggregatingState;
import io.nop.stream.core.common.state.AggregatingStateDescriptor;
import io.nop.stream.core.common.state.ListState;
import io.nop.stream.core.common.state.ListStateDescriptor;
import io.nop.stream.core.common.state.ReducingState;
import io.nop.stream.core.common.state.ReducingStateDescriptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestSimpleKeyedStateStore {

    private SimpleKeyedStateStore store;

    @BeforeEach
    void setUp() {
        store = new SimpleKeyedStateStore();
    }

    // --- ListState tests ---

    @Test
    void testListStateAddGet() throws Exception {
        ListState<String> state = store.getListState(new ListStateDescriptor<>("test", String.class));
        state.add("a");
        state.add("b");
        state.add("c");

        List<String> result = new ArrayList<>();
        state.get().forEach(result::add);
        assertEquals(Arrays.asList("a", "b", "c"), result);
    }

    @Test
    void testListStateClear() throws Exception {
        ListState<Integer> state = store.getListState(new ListStateDescriptor<>("test", Integer.class));
        state.add(1);
        state.add(2);
        state.clear();

        List<Integer> result = new ArrayList<>();
        state.get().forEach(result::add);
        assertTrue(result.isEmpty());
    }

    @Test
    void testListStateMultiKeyIsolation() throws Exception {
        ListState<String> state1 = store.getListState(new ListStateDescriptor<>("s1", String.class));
        ListState<String> state2 = store.getListState(new ListStateDescriptor<>("s2", String.class));

        state1.add("x");
        state2.add("y");

        List<String> r1 = new ArrayList<>();
        state1.get().forEach(r1::add);
        List<String> r2 = new ArrayList<>();
        state2.get().forEach(r2::add);

        assertEquals(List.of("x"), r1);
        assertEquals(List.of("y"), r2);
    }

    @Test
    void testListStateUpdate() throws Exception {
        ListState<Integer> state = store.getListState(new ListStateDescriptor<>("test", Integer.class));
        state.add(1);
        state.update(Arrays.asList(10, 20, 30));

        List<Integer> result = new ArrayList<>();
        state.get().forEach(result::add);
        assertEquals(Arrays.asList(10, 20, 30), result);
    }

    @Test
    void testListStateAddAll() throws Exception {
        ListState<Integer> state = store.getListState(new ListStateDescriptor<>("test", Integer.class));
        state.add(1);
        state.addAll(Arrays.asList(2, 3));

        List<Integer> result = new ArrayList<>();
        state.get().forEach(result::add);
        assertEquals(Arrays.asList(1, 2, 3), result);
    }

    // --- ReducingState tests ---

    static class SumAccumulator implements SimpleAccumulator<Integer> {
        private int sum = 0;

        @Override
        public void add(Integer value) { sum += value; }

        @Override
        public Integer getLocalValue() { return sum; }

        @Override
        public void resetLocal() { sum = 0; }

        @Override
        public void merge(Accumulator<Integer, Integer> other) { sum += other.getLocalValue(); }

        @Override
        public Accumulator<Integer, Integer> clone() {
            SumAccumulator c = new SumAccumulator();
            c.sum = this.sum;
            return c;
        }
    }

    @Test
    void testReducingStateAddGet() throws Exception {
        ReducingState<Integer> state = store.getReducingState(
                new ReducingStateDescriptor<>("sum", Integer.class, SumAccumulator.class));
        state.add(1);
        state.add(2);
        state.add(3);
        assertEquals(6, state.get());
    }

    @Test
    void testReducingStateClear() throws Exception {
        ReducingState<Integer> state = store.getReducingState(
                new ReducingStateDescriptor<>("sum", Integer.class, SumAccumulator.class));
        state.add(10);
        state.clear();
        assertNull(state.get());
    }

    @Test
    void testReducingStateGetBeforeAdd() throws Exception {
        ReducingState<Integer> state = store.getReducingState(
                new ReducingStateDescriptor<>("sum", Integer.class, SumAccumulator.class));
        assertNull(state.get());
    }

    // --- AggregatingState tests ---

    static class AvgAggregateFunction implements AggregateFunction<Integer, int[], Double> {
        @Override
        public int[] createAccumulator() { return new int[]{0, 0}; }

        @Override
        public int[] add(Integer value, int[] acc) {
            acc[0] += value;
            acc[1]++;
            return acc;
        }

        @Override
        public Double getResult(int[] acc) {
            return acc[1] == 0 ? 0.0 : (double) acc[0] / acc[1];
        }

        @Override
        public int[] merge(int[] a, int[] b) {
            a[0] += b[0];
            a[1] += b[1];
            return a;
        }
    }

    @Test
    void testAggregatingStateAddResult() throws Exception {
        AggregatingState<Integer, Double> state = store.getAggregatingState(
                new AggregatingStateDescriptor<>("avg", new AvgAggregateFunction(), int[].class));
        state.add(10);
        state.add(20);
        state.add(30);
        assertEquals(20.0, state.get(), 0.001);
    }

    @Test
    void testAggregatingStateClear() throws Exception {
        AggregatingState<Integer, Double> state = store.getAggregatingState(
                new AggregatingStateDescriptor<>("avg", new AvgAggregateFunction(), int[].class));
        state.add(10);
        state.clear();
        assertNull(state.get());
    }

    @Test
    void testAggregatingStateGetBeforeAdd() throws Exception {
        AggregatingState<Integer, Double> state = store.getAggregatingState(
                new AggregatingStateDescriptor<>("avg", new AvgAggregateFunction(), int[].class));
        assertNull(state.get());
    }
}

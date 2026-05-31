package io.nop.stream.core.operators;

import io.nop.stream.core.util.Collector;
import io.nop.stream.core.windowing.windows.GlobalWindow;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestWindowAggregationFunction {

    @Test
    void defaultMergeThrowsUnsupportedOperationException() {
        WindowAggregationFunction<Integer, Integer, Integer, String, GlobalWindow> func =
                new WindowAggregationFunction<>() {
                    @Override
                    public Integer createAccumulator() { return 0; }

                    @Override
                    public Integer add(Integer value, Integer accumulator) { return accumulator + value; }

                    @Override
                    public void emitResult(String key, GlobalWindow window, Integer accumulator, Collector<Integer> out) {
                        out.collect(accumulator);
                    }
                };

        assertThrows(UnsupportedOperationException.class, () -> func.merge(1, 2));
    }

    @Test
    void customMergeWorksCorrectly() throws Exception {
        WindowAggregationFunction<Integer, int[], Integer, String, GlobalWindow> sumFunc =
                new WindowAggregationFunction<>() {
                    @Override
                    public int[] createAccumulator() { return new int[]{0}; }

                    @Override
                    public int[] add(Integer value, int[] accumulator) {
                        accumulator[0] += value;
                        return accumulator;
                    }

                    @Override
                    public void emitResult(String key, GlobalWindow window, int[] accumulator, Collector<Integer> out) {
                        out.collect(accumulator[0]);
                    }

                    @Override
                    public int[] merge(int[] acc1, int[] acc2) {
                        acc1[0] += acc2[0];
                        return acc1;
                    }
                };

        int[] acc1 = sumFunc.createAccumulator();
        sumFunc.add(10, acc1);

        int[] acc2 = sumFunc.createAccumulator();
        sumFunc.add(20, acc2);

        int[] merged = sumFunc.merge(acc1, acc2);
        assertEquals(30, merged[0]);
    }
}

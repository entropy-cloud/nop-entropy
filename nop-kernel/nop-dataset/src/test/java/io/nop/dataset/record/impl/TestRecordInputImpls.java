package io.nop.dataset.record.impl;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestRecordInputImpls {

    @Test
    public void testDefaultReadBatchZeroMaxCountReadsNothing() {
        BaseRecordInput<String> input = new BaseRecordInput<>(List.of("a", "b"), null);

        List<String> ret = new ArrayList<>();
        Function<String, String> fn = s -> s;
        Consumer<String> consumer = ret::add;

        // 修复前：先消费 1 条再检查上限，maxCount=0 时返回 1 条且该条被迭代器吞掉
        RecordInputImpls.defaultReadBatch(input, 0, fn, consumer);

        assertEquals(0, ret.size());
        assertEquals(2, input.getRemainingCount());

        // 正常上限行为不变
        RecordInputImpls.defaultReadBatch(input, 1, fn, consumer);
        assertEquals(List.of("a"), ret);
        assertEquals(1, input.getRemainingCount());
    }

    @Test
    public void testDefaultReadBatchWithFilterZeroMaxCountReadsNothing() {
        BaseRecordInput<String> input = new BaseRecordInput<>(List.of("a", "b"), null);

        List<String> ret = new ArrayList<>();
        RecordInputImpls.defaultReadBatch(input, 0, (String s) -> true, s -> s, ret::add);

        assertEquals(0, ret.size());
        assertEquals(2, input.getRemainingCount());
    }
}

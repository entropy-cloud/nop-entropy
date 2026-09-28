/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:   https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.rocksdb;

import io.nop.stream.core.common.functions.AggregateFunction;
import io.nop.stream.core.common.state.AggregatingStateDescriptor;
import io.nop.stream.core.common.state.InternalAppendingState;
import io.nop.stream.core.common.state.backend.IKeyedStateBackend;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * B3 regression (plan 01 quality-perf Phase 2): with an Object-typed
 * AggregatingStateDescriptor (the window-operator shape where ACC is only
 * known at runtime), {@code getAccumulator()} used to deserialize with
 * {@code descriptor.getValueType()} while {@code get()} used the
 * constructor-resolved {@code storageValueType} — the two accessors of the
 * SAME state returned different runtime types (wrapper map vs accumulator).
 */
class TestRocksDBInternalAggregatingAccessorType {

    @TempDir
    File tempDir;

    /** Accumulates into a Long; ACC declared as Object to trigger the drift. */
    static class LongSumObjectAcc implements AggregateFunction<Long, Object, Long> {
        private static final long serialVersionUID = 1L;

        @Override
        public Object createAccumulator() {
            return 0L;
        }

        @Override
        public Object add(Long value, Object accumulator) {
            return ((Long) accumulator) + value;
        }

        @Override
        public Long getResult(Object accumulator) {
            return (Long) accumulator;
        }

        @Override
        public Object merge(Object a, Object b) {
            return ((Long) a) + (Long) b;
        }
    }

    @Test
    void getAccumulatorMatchesGetType() throws Exception {
        RocksDBStateBackend stateBackend = new RocksDBStateBackend(tempDir.getAbsolutePath());
        IKeyedStateBackend<Long> keyed = stateBackend.createKeyedStateBackend(Long.class);
        try {
            InternalAppendingState<Long, String, Long, Object, Long> state =
                    ((io.nop.stream.core.common.state.backend.IInternalStateBackend<Long>) keyed)
                            .getInternalAppendingState(
                                    new AggregatingStateDescriptor<>("acc-type-drift", new LongSumObjectAcc(), Object.class));
            state.setCurrentNamespace("ns");

            keyed.setCurrentKey(1L);
            state.add(2L);
            state.add(3L);

            Object viaGet = state.get();
            Object viaAccumulator = state.getAccumulator();

            assertInstanceOf(Long.class, viaGet, "get() resolves the storage type");
            assertInstanceOf(Long.class, viaAccumulator,
                    "B3: getAccumulator() must deserialize with the same storageValueType as get() "
                            + "(pre-fix: Object-typed descriptors returned the JSON wrapper map here)");
            assertEquals(5L, viaAccumulator, "the accumulator carries the summed value");
        } finally {
            keyed.close();
        }
    }
}

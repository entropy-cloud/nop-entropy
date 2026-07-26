/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state;

import io.nop.stream.core.checkpoint.SerializerFingerprint;
import io.nop.stream.core.common.accumulators.LongCounter;
import org.junit.jupiter.api.Test;

import static io.nop.stream.core.common.state.StateSchemaResolver.STATE_TYPE_AGGREGATING;
import static io.nop.stream.core.common.state.StateSchemaResolver.STATE_TYPE_INTERNAL_LIST;
import static io.nop.stream.core.common.state.StateSchemaResolver.STATE_TYPE_LIST;
import static io.nop.stream.core.common.state.StateSchemaResolver.STATE_TYPE_MAP;
import static io.nop.stream.core.common.state.StateSchemaResolver.STATE_TYPE_REDUCING;
import static io.nop.stream.core.common.state.StateSchemaResolver.STATE_TYPE_VALUE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestStateSchemaResolver {

    static class TestAggregateFunction implements io.nop.stream.core.common.functions.AggregateFunction<Long, long[], Long> {
        private static final long serialVersionUID = 1L;

        @Override
        public long[] createAccumulator() {
            return new long[]{0L};
        }

        @Override
        public long[] add(Long value, long[] accumulator) {
            accumulator[0] += value;
            return accumulator;
        }

        @Override
        public Long getResult(long[] accumulator) {
            return accumulator[0];
        }

        @Override
        public long[] merge(long[] a, long[] b) {
            a[0] += b[0];
            return a;
        }
    }

    static class AnotherAggregateFunction extends TestAggregateFunction {
        private static final long serialVersionUID = 1L;
    }

    @Test
    void valueStateDeterministic() {
        ValueStateDescriptor<Integer> d1 = new ValueStateDescriptor<>("v", Integer.class);
        ValueStateDescriptor<Integer> d2 = new ValueStateDescriptor<>("v-renamed-but-same-type", Integer.class);
        SerializerFingerprint fp1 = StateSchemaResolver.fromDescriptor(STATE_TYPE_VALUE, d1);
        SerializerFingerprint fp2 = StateSchemaResolver.fromDescriptor(STATE_TYPE_VALUE, d2);

        // Different stateName should not change the checksum (checksum is type-based)
        assertEquals(fp1.getSchemaChecksum(), fp2.getSchemaChecksum());
        // Schema version defaults to 1
        assertEquals(1, fp1.getSchemaVersion());
    }

    @Test
    void differentValueTypeProducesDifferentChecksum() {
        ValueStateDescriptor<Integer> intDesc = new ValueStateDescriptor<>("v", Integer.class);
        ValueStateDescriptor<Long> longDesc = new ValueStateDescriptor<>("v", Long.class);
        SerializerFingerprint fpInt = StateSchemaResolver.fromDescriptor(STATE_TYPE_VALUE, intDesc);
        SerializerFingerprint fpLong = StateSchemaResolver.fromDescriptor(STATE_TYPE_VALUE, longDesc);

        assertNotEquals(fpInt.getSchemaChecksum(), fpLong.getSchemaChecksum());
    }

    @Test
    void differentStateTypeProducesDifferentChecksum() {
        ValueStateDescriptor<Integer> valueDesc = new ValueStateDescriptor<>("v", Integer.class);
        ListStateDescriptor<Integer> listDesc = new ListStateDescriptor<>("v", Integer.class);
        SerializerFingerprint fpValue = StateSchemaResolver.fromDescriptor(STATE_TYPE_VALUE, valueDesc);
        SerializerFingerprint fpList = StateSchemaResolver.fromDescriptor(STATE_TYPE_LIST, listDesc);

        assertNotEquals(fpValue.getSchemaChecksum(), fpList.getSchemaChecksum());
    }

    @Test
    void mapStateDifferentKeyTypesProduceDifferentChecksum() {
        MapStateDescriptor<String, Long> mapStr = new MapStateDescriptor<>("m", String.class, Long.class);
        MapStateDescriptor<Integer, Long> mapInt = new MapStateDescriptor<>("m", Integer.class, Long.class);
        SerializerFingerprint fpStr = StateSchemaResolver.fromDescriptor(STATE_TYPE_MAP, mapStr);
        SerializerFingerprint fpInt = StateSchemaResolver.fromDescriptor(STATE_TYPE_MAP, mapInt);

        assertNotEquals(fpStr.getSchemaChecksum(), fpInt.getSchemaChecksum());
    }

    @Test
    void reducingStateDifferentAccumulatorProducesDifferentChecksum() {
        ReducingStateDescriptor<Long> r1 = new ReducingStateDescriptor<>("r", Long.class, LongCounter.class);
        ReducingStateDescriptor<Long> r2 = new ReducingStateDescriptor<>("r", Long.class,
                io.nop.stream.core.common.accumulators.LongMaximum.class);
        SerializerFingerprint fp1 = StateSchemaResolver.fromDescriptor(STATE_TYPE_REDUCING, r1);
        SerializerFingerprint fp2 = StateSchemaResolver.fromDescriptor(STATE_TYPE_REDUCING, r2);

        assertNotEquals(fp1.getSchemaChecksum(), fp2.getSchemaChecksum());
    }

    @Test
    void aggregatingStateDifferentAggregateFunctionProducesDifferentChecksum() {
        AggregatingStateDescriptor<Long, long[], Long> a1 =
                new AggregatingStateDescriptor<>("a", new TestAggregateFunction(), long[].class);
        AggregatingStateDescriptor<Long, long[], Long> a2 =
                new AggregatingStateDescriptor<>("a", new AnotherAggregateFunction(), long[].class);
        SerializerFingerprint fp1 = StateSchemaResolver.fromDescriptor(STATE_TYPE_AGGREGATING, a1);
        SerializerFingerprint fp2 = StateSchemaResolver.fromDescriptor(STATE_TYPE_AGGREGATING, a2);

        assertNotEquals(fp1.getSchemaChecksum(), fp2.getSchemaChecksum());
    }

    @Test
    void listVsInternalListProduceDifferentChecksums() {
        // Same descriptor, different stateType → different checksum.
        // This is the key reason fromDescriptor requires an explicit stateType.
        ListStateDescriptor<Integer> desc = new ListStateDescriptor<>("l", Integer.class);
        SerializerFingerprint fpList = StateSchemaResolver.fromDescriptor(STATE_TYPE_LIST, desc);
        SerializerFingerprint fpInternalList = StateSchemaResolver.fromDescriptor(STATE_TYPE_INTERNAL_LIST, desc);

        assertNotEquals(fpList.getSchemaChecksum(), fpInternalList.getSchemaChecksum());
    }

    @Test
    void rejectsNullStateTypeOrDescriptor() {
        assertThrows(IllegalArgumentException.class,
                () -> StateSchemaResolver.fromDescriptor(null, new ValueStateDescriptor<>("v", Integer.class)));
        assertThrows(IllegalArgumentException.class,
                () -> StateSchemaResolver.fromDescriptor("", new ValueStateDescriptor<>("v", Integer.class)));
        assertThrows(IllegalArgumentException.class,
                () -> StateSchemaResolver.fromDescriptor(STATE_TYPE_VALUE, null));
    }

    // ========== descriptor-mode vs string-mode parity ==========

    @Test
    void descriptorAndStringModesProduceIdenticalChecksumsForValueState() {
        ValueStateDescriptor<Integer> desc = new ValueStateDescriptor<>("v", Integer.class);
        SerializerFingerprint fpDesc = StateSchemaResolver.fromDescriptor(STATE_TYPE_VALUE, desc);
        SerializerFingerprint fpStr = StateSchemaResolver.fromTypeMetadata(
                "v", STATE_TYPE_VALUE, Integer.class.getName(), null, null, null);

        assertEquals(fpDesc.getSchemaChecksum(), fpStr.getSchemaChecksum());
    }

    @Test
    void descriptorAndStringModesProduceIdenticalChecksumsForMapState() {
        MapStateDescriptor<String, Long> desc = new MapStateDescriptor<>("m", String.class, Long.class);
        SerializerFingerprint fpDesc = StateSchemaResolver.fromDescriptor(STATE_TYPE_MAP, desc);
        SerializerFingerprint fpStr = StateSchemaResolver.fromTypeMetadata(
                "m", STATE_TYPE_MAP, Long.class.getName(), String.class.getName(), null, null);

        assertEquals(fpDesc.getSchemaChecksum(), fpStr.getSchemaChecksum());
    }

    @Test
    void descriptorAndStringModesProduceIdenticalChecksumsForListState() {
        ListStateDescriptor<Integer> desc = new ListStateDescriptor<>("l", Integer.class);
        SerializerFingerprint fpDesc = StateSchemaResolver.fromDescriptor(STATE_TYPE_LIST, desc);
        SerializerFingerprint fpStr = StateSchemaResolver.fromTypeMetadata(
                "l", STATE_TYPE_LIST, Integer.class.getName(), null, null, null);

        assertEquals(fpDesc.getSchemaChecksum(), fpStr.getSchemaChecksum());
    }

    @Test
    void descriptorAndStringModesProduceIdenticalChecksumsForInternalListState() {
        ListStateDescriptor<Integer> desc = new ListStateDescriptor<>("il", Integer.class);
        SerializerFingerprint fpDesc = StateSchemaResolver.fromDescriptor(STATE_TYPE_INTERNAL_LIST, desc);
        SerializerFingerprint fpStr = StateSchemaResolver.fromTypeMetadata(
                "il", STATE_TYPE_INTERNAL_LIST, Integer.class.getName(), null, null, null);

        assertEquals(fpDesc.getSchemaChecksum(), fpStr.getSchemaChecksum());
    }

    @Test
    void descriptorAndStringModesProduceIdenticalChecksumsForReducingState() {
        ReducingStateDescriptor<Long> desc = new ReducingStateDescriptor<>("r", Long.class, LongCounter.class);
        SerializerFingerprint fpDesc = StateSchemaResolver.fromDescriptor(STATE_TYPE_REDUCING, desc);
        SerializerFingerprint fpStr = StateSchemaResolver.fromTypeMetadata(
                "r", STATE_TYPE_REDUCING, Long.class.getName(), null,
                LongCounter.class.getName(), null);

        assertEquals(fpDesc.getSchemaChecksum(), fpStr.getSchemaChecksum());
    }

    @Test
    void descriptorAndStringModesProduceIdenticalChecksumsForAggregatingState() {
        AggregatingStateDescriptor<Long, long[], Long> desc =
                new AggregatingStateDescriptor<>("a", new TestAggregateFunction(), long[].class);
        SerializerFingerprint fpDesc = StateSchemaResolver.fromDescriptor(STATE_TYPE_AGGREGATING, desc);
        SerializerFingerprint fpStr = StateSchemaResolver.fromTypeMetadata(
                "a", STATE_TYPE_AGGREGATING, long[].class.getName(), null, null,
                TestAggregateFunction.class.getName());

        assertEquals(fpDesc.getSchemaChecksum(), fpStr.getSchemaChecksum());
    }

    @Test
    void checksumIsStableAcrossCalls() {
        ValueStateDescriptor<Integer> desc = new ValueStateDescriptor<>("v", Integer.class);
        String cs1 = StateSchemaResolver.fromDescriptor(STATE_TYPE_VALUE, desc).getSchemaChecksum();
        String cs2 = StateSchemaResolver.fromDescriptor(STATE_TYPE_VALUE, desc).getSchemaChecksum();
        String cs3 = StateSchemaResolver.fromDescriptor(STATE_TYPE_VALUE,
                new ValueStateDescriptor<>("v", Integer.class)).getSchemaChecksum();
        assertEquals(cs1, cs2);
        assertEquals(cs1, cs3);
        // SHA-256 hex digest is 64 chars
        assertEquals(64, cs1.length());
    }
}

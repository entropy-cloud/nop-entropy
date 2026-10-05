/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.backend.memory;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.core.lang.json.JsonTool;
import io.nop.stream.core.common.accumulators.Accumulator;
import io.nop.stream.core.common.accumulators.LongCounter;
import io.nop.stream.core.common.accumulators.SimpleAccumulator;
import io.nop.stream.core.common.functions.AggregateFunction;
import io.nop.stream.core.common.state.AggregatingState;
import io.nop.stream.core.common.state.AggregatingStateDescriptor;
import io.nop.stream.core.common.state.MapState;
import io.nop.stream.core.common.state.MapStateDescriptor;
import io.nop.stream.core.common.state.ReducingStateDescriptor;
import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.core.common.state.backend.StateSnapshot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * R5 ST-01 regression pins: a checkpoint snapshot must be a point-in-time
 * view. Mutations performed on live state AFTER snapshotState() must never
 * leak into the serialized snapshot that the persist thread writes.
 * Removing the SnapshotCopyUtil.defensiveCopy wiring in MemoryStateSerDe
 * (the serializer==null branch, serializeKey, the appending payload and the
 * map-key path) turns these tests red.
 */
class TestMemoryStateSerDePointInTimeSnapshot {

    @DataBean
    public static class MutableValue {
        private int v;

        public MutableValue() {
        }

        public MutableValue(int v) {
            this.v = v;
        }

        public int getV() {
            return v;
        }

        public void setV(int v) {
            this.v = v;
        }
    }

    @DataBean
    public static class MutableKey {
        private String id;

        public MutableKey() {
        }

        public MutableKey(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }
    }

    /** Mutable element for the appending payload pin. */
    @DataBean
    public static class AccumulatingValue {
        private int v;

        public AccumulatingValue() {
        }

        public AccumulatingValue(int v) {
            this.v = v;
        }

        public int getV() {
            return v;
        }

        public void setV(int v) {
            this.v = v;
        }
    }

    /** Wrapper accumulator so the appending state can hold AccumulatingValue elements. */
    public static class AccumulatingValueAcc implements SimpleAccumulator<AccumulatingValue> {
        private static final long serialVersionUID = 1L;
        private AccumulatingValue value;

        @Override
        public AccumulatingValue getLocalValue() {
            return value;
        }

        @Override
        public void add(AccumulatingValue value) {
            this.value = value;
        }

        @Override
        public void merge(Accumulator<AccumulatingValue, AccumulatingValue> other) {
            this.value = other.getLocalValue();
        }

        @Override
        public void resetLocal() {
            this.value = null;
        }

        @Override
        public Accumulator<AccumulatingValue, AccumulatingValue> clone() {
            AccumulatingValueAcc copy = new AccumulatingValueAcc();
            copy.value = value;
            return copy;
        }
    }

    /** Mutates the ACC in place on add(), the ST-01 hazard shape. */
    public static class InPlaceSumFunction implements AggregateFunction<Long, long[], Long> {
        @Override
        public long[] createAccumulator() {
            return new long[] {0L};
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

    @Test
    void testAggregatingAccMutationDoesNotLeakIntoSnapshot() throws Exception {
        MemoryKeyedStateBackend<String> backend = new MemoryKeyedStateBackend<>(String.class);
        AggregatingStateDescriptor<Long, long[], Long> desc =
                new AggregatingStateDescriptor<>("agg-sum", new InPlaceSumFunction(), long[].class);

        backend.setCurrentKey("key1");
        AggregatingState<Long, Long> state = backend.getAggregatingState(desc);
        state.add(10L);
        state.add(20L);

        StateSnapshot snapshot = backend.snapshotState();
        String before = JsonTool.serialize(snapshot.getStateData(), false);

        // Post-barrier in-place mutation on the same live ACC
        state.add(999L);

        String after = JsonTool.serialize(snapshot.getStateData(), false);
        assertEquals(before, after, "post-snapshot ACC mutation leaked into the snapshot");

        // The persisted snapshot must still restore the pre-mutation sum
        MemoryKeyedStateBackend<String> restored = new MemoryKeyedStateBackend<>(String.class);
        restored.restoreState(snapshot);
        restored.setCurrentKey("key1");
        AggregatingStateDescriptor<Long, long[], Long> desc2 =
                new AggregatingStateDescriptor<>("agg-sum", new InPlaceSumFunction(), long[].class);
        assertEquals(Long.valueOf(30L), restored.getAggregatingState(desc2).get());
    }

    @Test
    void testValueStatePojoMutationDoesNotLeakIntoSnapshot() throws Exception {
        MemoryKeyedStateBackend<String> backend = new MemoryKeyedStateBackend<>(String.class);
        ValueStateDescriptor<MutableValue> desc = new ValueStateDescriptor<>("v", MutableValue.class);

        backend.setCurrentKey("key1");
        ValueState<MutableValue> state = backend.getState(desc);
        state.update(new MutableValue(7));

        StateSnapshot snapshot = backend.snapshotState();
        String before = JsonTool.serialize(snapshot.getStateData(), false);

        state.value().setV(1234);

        String after = JsonTool.serialize(snapshot.getStateData(), false);
        assertEquals(before, after, "post-snapshot POJO value mutation leaked into the snapshot");
    }

    @Test
    void testMapKeyPojoMutationDoesNotLeakIntoSnapshot() throws Exception {
        MemoryKeyedStateBackend<String> backend = new MemoryKeyedStateBackend<>(String.class);
        MapStateDescriptor<MutableKey, String> desc = new MapStateDescriptor<>("m", MutableKey.class, String.class);

        backend.setCurrentKey("key1");
        MapState<MutableKey, String> state = backend.getMapState(desc);
        MutableKey key = new MutableKey("a");
        state.put(key, "v1");

        StateSnapshot snapshot = backend.snapshotState();
        String before = JsonTool.serialize(snapshot.getStateData(), false);

        key.setId("mutated-after-snapshot");

        String after = JsonTool.serialize(snapshot.getStateData(), false);
        assertEquals(before, after, "post-snapshot map key mutation leaked into the snapshot");
    }


    @Test
    void testStateKeyPojoMutationDoesNotLeakIntoSnapshot() throws Exception {
        MemoryKeyedStateBackend<MutableKey> backend = new MemoryKeyedStateBackend<>(MutableKey.class);
        ValueStateDescriptor<Long> desc = new ValueStateDescriptor<>("n", Long.class);

        MutableKey stateKey = new MutableKey("k1");
        backend.setCurrentKey(stateKey);
        backend.getState(desc).update(42L);

        StateSnapshot snapshot = backend.snapshotState();
        String before = JsonTool.serialize(snapshot.getStateData(), false);

        stateKey.setId("mutated-after-snapshot");

        String after = JsonTool.serialize(snapshot.getStateData(), false);
        assertEquals(before, after, "post-snapshot state key mutation leaked into the snapshot");
    }

    @Test
    void testAppendingElementMutationDoesNotLeakIntoSnapshot() throws Exception {
        MemoryKeyedStateBackend<String> backend = new MemoryKeyedStateBackend<>(String.class);
        ReducingStateDescriptor<Long> desc = new ReducingStateDescriptor<>("app", Long.class, LongCounter.class);

        MemoryInternalAppendingState<String, Object, Long, Object> state =
                new MemoryInternalAppendingState<>(backend, desc);
        state.setCurrentNamespace("default");
        backend.setCurrentKey("key1");

        ReducingStateDescriptor<AccumulatingValue> pojoDesc =
                new ReducingStateDescriptor<>("app-pojo", AccumulatingValue.class, AccumulatingValueAcc.class);
        MemoryInternalAppendingState<String, Object, AccumulatingValue, Object> pojoState =
                new MemoryInternalAppendingState<>(backend, pojoDesc);
        pojoState.setCurrentNamespace("default");

        List<AccumulatingValue> live = new ArrayList<>();
        live.add(new AccumulatingValue(1));
        live.add(new AccumulatingValue(2));
        pojoState.storage.put(new TypedNamespaceAndKey("default", "key1"), live);

        MemoryStateSerDe serDe = new MemoryStateSerDe(backend);
        Map<String, Object> states = new java.util.LinkedHashMap<>();
        states.put("app", pojoState);
        StateSnapshot snapshot = serDe.snapshotState(states);
        assertNotNull(snapshot);

        String before = JsonTool.serialize(snapshot.getStateData(), false);

        // Mutate the element object itself (not the list slot)
        live.get(0).setV(999);

        String after = JsonTool.serialize(snapshot.getStateData(), false);
        assertEquals(before, after, "post-snapshot appending element mutation leaked into the snapshot");
    }
}

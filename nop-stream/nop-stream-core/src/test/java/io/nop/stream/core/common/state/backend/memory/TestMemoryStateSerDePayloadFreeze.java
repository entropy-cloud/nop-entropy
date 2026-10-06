/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.backend.memory;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.core.lang.json.JsonTool;
import io.nop.stream.core.common.state.ListState;
import io.nop.stream.core.common.state.ListStateDescriptor;
import io.nop.stream.core.common.state.MapState;
import io.nop.stream.core.common.state.MapStateDescriptor;
import io.nop.stream.core.common.state.backend.ContainerValueCodec;
import io.nop.stream.core.common.state.backend.StateSnapshot;
import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R6 ST-01 regression pins (plan 2323): the ListState element and the MapState
 * value payload leaves must be point-in-time frozen at snapshotState — the
 * async persist thread serializes the snapshot after processBarrier returns,
 * so live leaf references let post-barrier mutations tear the durable JSON.
 *
 * <p>Two-segment contract probe: the container wrapper's TYPE fields must still
 * carry the live bean class name (this is what distinguishes the frozen encode
 * from the forbidden {@code encode(defensiveCopy(v))} composition, which would
 * misread the copy's JSON-native Map shape as a container and double-wrap).
 * Removing the freeze in {@code writeListPayload}/{@code writeMapPayload} (or
 * replacing it with the copy-then-encode composition) turns these tests red.
 */
class TestMemoryStateSerDePayloadFreeze {

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

    @Test
    void listStateElementMutationDoesNotLeakIntoSnapshot() throws Exception {
        MemoryKeyedStateBackend<String> backend = new MemoryKeyedStateBackend<>(String.class);
        ListStateDescriptor<MutableValue> desc = new ListStateDescriptor<>("list-payload", MutableValue.class);

        backend.setCurrentKey("key1");
        ListState<MutableValue> state = backend.getListState(desc);
        state.add(new MutableValue(7));

        StateSnapshot snapshot = backend.snapshotState();
        String before = JsonTool.serialize(snapshot.getStateData(), false);

        // Post-barrier mutation of the live element behind the snapshot
        for (MutableValue mv : state.get()) {
            mv.setV(99);
        }

        String after = JsonTool.serialize(snapshot.getStateData(), false);
        assertEquals(before, after, "post-snapshot ListState element mutation leaked into the snapshot");

        MemoryKeyedStateBackend<String> restored = new MemoryKeyedStateBackend<>(String.class);
        restored.restoreState(snapshot);
        restored.setCurrentKey("key1");
        int seen = 0;
        for (MutableValue mv : restored.getListState(desc).get()) {
            seen++;
            assertEquals(7, mv.getV(), "restored element must carry the pre-mutation value");
            assertInstanceOf(MutableValue.class, mv, "restored element must re-materialize as the declared type");
        }
        assertEquals(1, seen);
    }

    @Test
    void mapStateValueMutationDoesNotLeakAndWrapperTypeStaysLive() throws Exception {
        MemoryKeyedStateBackend<String> backend = new MemoryKeyedStateBackend<>(String.class);
        MapStateDescriptor<String, MutableValue> desc =
                new MapStateDescriptor<>("map-payload", String.class, MutableValue.class);

        backend.setCurrentKey("key1");
        MapState<String, MutableValue> state = backend.getMapState(desc);
        state.put("k", new MutableValue(7));

        StateSnapshot snapshot = backend.snapshotState();
        String before = JsonTool.serialize(snapshot.getStateData(), false);

        // Bean values pass through the codec unwrapped (no container wrapper); the
        // declared type lives in the state header. Isolation is the pin here.
        assertTrue(before.contains("\"v\":7"), () -> "persisted JSON must carry the bean payload, got: " + before);

        // Post-barrier in-place mutation of the live value behind the snapshot
        state.get("k").setV(99);

        String after = JsonTool.serialize(snapshot.getStateData(), false);
        assertEquals(before, after, "post-snapshot MapState value mutation leaked into the snapshot");

        MemoryKeyedStateBackend<String> restored = new MemoryKeyedStateBackend<>(String.class);
        restored.restoreState(snapshot);
        restored.setCurrentKey("key1");
        MutableValue restoredValue = restored.getMapState(desc).get("k");
        assertInstanceOf(MutableValue.class, restoredValue,
                "restored value must re-materialize as the declared type (not a JSON-native map)");
        assertEquals(7, restoredValue.getV(), "restored value must carry the pre-mutation state");
    }

    /**
     * Nested-container leaf freeze: the frozen encode must keep recursing with
     * copied leaves while every wrapper level records the live runtime class.
     */
    @Test
    void mapStateNestedContainerFreezeKeepsTypeFieldsAndIsolatesMutations() throws Exception {
        MemoryKeyedStateBackend<String> backend = new MemoryKeyedStateBackend<>(String.class);
        MapStateDescriptor<String, java.util.List<MutableValue>> desc =
                new MapStateDescriptor<>("map-list-payload", String.class,
                        (Class<java.util.List<MutableValue>>) (Class<?>) java.util.List.class);

        backend.setCurrentKey("key1");
        MapState<String, java.util.List<MutableValue>> state = backend.getMapState(desc);
        java.util.List<MutableValue> list = new java.util.ArrayList<>();
        list.add(new MutableValue(7));
        state.put("k", list);

        StateSnapshot snapshot = backend.snapshotState();
        String before = JsonTool.serialize(snapshot.getStateData(), false);

        // Two-segment probe: the container wrapper must record the LIVE first
        // element class. The forbidden encode(defensiveCopy(v)) composition would
        // misread the copy's LinkedHashMap shape and emit nopContainerType=map with
        // String/Integer element types instead.
        assertTrue(before.contains(ContainerValueCodec.CONTAINER_TYPE_KEY + "\":\"list\""),
                () -> "container wrapper must be present, got: " + before);
        assertTrue(before.contains(ContainerValueCodec.ELEMENT_TYPE_KEY + "\":\""
                        + MutableValue.class.getName() + "\""),
                () -> "wrapper element type must be the live bean class, got: " + before);

        // Post-barrier mutations on both the container and the leaf
        list.add(new MutableValue(1000));
        list.get(0).setV(99);

        String after = JsonTool.serialize(snapshot.getStateData(), false);
        assertEquals(before, after,
                "post-snapshot nested container/leaf mutation leaked into the snapshot");

        assertTrue(before.contains("\"v\":7") && !before.contains("\"v\":1000"),
                "persisted JSON must keep the pre-mutation bean payload");
    }
}

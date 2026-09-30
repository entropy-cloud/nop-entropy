package io.nop.stream.core.common.state.backend.memory;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.json.JsonWhitelist;
import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.core.common.state.backend.StateSnapshot;
import io.nop.stream.core.common.state.shard.KeyGroupAssignment;
import io.nop.stream.core.common.state.shard.KeyGroupRange;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * ST-02 regression (memory rescale filter path): the Stage 35 partial restore
 * used to compute each entry's key-group from the JSON-native key form BEFORE
 * re-materialization, so non-primitive keys (Date, {@code @DataBean} beans)
 * whose JSON round trip changes their runtime type/hash were filtered by a
 * hash that differs from the live {@code routeKey} decision — entries landed
 * on the wrong subtask (or were dropped by every subtask). After the fix the
 * filter re-materializes the key first, so restore placement matches the live
 * typed-key group and every key remains readable and writable.
 */
class TestMemoryRescaleNonPrimitiveKeyRestore {

    private static final int MAX_P = 16;
    private static final int NEW_PARALLELISM = 4;
    private static final ValueStateDescriptor<Long> COUNT =
            new ValueStateDescriptor<>("count", Long.class, 0L);

    @DataBean
    public static class BeanKey {
        private String id;
        private int salt;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public int getSalt() {
            return salt;
        }

        public void setSalt(int salt) {
            this.salt = salt;
        }

        // Value semantics: keyed-state storage keeps keys in hash maps, so a
        // key class must implement equals/hashCode (unrelated to sharding,
        // which uses the JSON-canonical stableHash, not identity hashCode).
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof BeanKey)) return false;
            BeanKey other = (BeanKey) o;
            return salt == other.salt && java.util.Objects.equals(id, other.id);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(id, salt);
        }
    }

    @BeforeAll
    static void whitelistDate() {
        JsonWhitelist.add("java.util.Date");
    }

    /** What the local-storage checkpoint persist does to the snapshot data. */
    private static StateSnapshot jsonRoundTrip(StateSnapshot snapshot) {
        String json = JsonTool.serialize(snapshot.getStateData(), false);
        Map<String, Object> parsed = JsonTool.parseMap(json);
        return new StateSnapshot(parsed);
    }

    @Test
    void dateKeyRescaleRestore_keysRemainReadableAndWritable() throws Exception {
        MemoryKeyedStateBackend<Date> source = new MemoryKeyedStateBackend<>(Date.class, MAX_P);
        Map<Date, Long> allKeys = new LinkedHashMap<>();
        ValueState<Long> state = source.getState(COUNT);
        for (int i = 0; i < 20; i++) {
            Date key = new Date(1690000000000L + i * 3_600_000L);
            source.setCurrentKey(key);
            state.update(1000L + i);
            allKeys.put(key, 1000L + i);
        }
        StateSnapshot snapshot = jsonRoundTrip(source.snapshotState());
        source.close();

        // Rescale restore: each new subtask restores only its KeyGroupRange slice.
        int keptTotal = 0;
        for (int sub = 0; sub < NEW_PARALLELISM; sub++) {
            MemoryKeyedStateBackend<Date> backend = new MemoryKeyedStateBackend<>(Date.class, MAX_P);
            KeyGroupRange range = KeyGroupAssignment.computeKeyGroupRangeForSubtaskIndex(MAX_P, NEW_PARALLELISM, sub);
            backend.setTargetKeyGroupRange(range);
            backend.restoreState(snapshot);

            ValueState<Long> restoredState = backend.getState(COUNT);
            for (Map.Entry<Date, Long> e : allKeys.entrySet()) {
                backend.setCurrentKey(e.getKey());
                if (range.contains(KeyGroupAssignment.assignToKeyGroup(e.getKey(), MAX_P))) {
                    assertEquals(e.getValue(), restoredState.value(),
                            "owned Date key must be restored with its value on subtask " + sub);
                    keptTotal++;
                    // 可读写: restore must leave the key writable with stable placement.
                    restoredState.update(e.getValue() + 1);
                    assertEquals(Long.valueOf(e.getValue() + 1), restoredState.value(),
                            "restored Date key must remain writable on subtask " + sub);
                } else {
                    assertNull(restoredState.value(),
                            "non-owned Date key must NOT appear on subtask " + sub);
                }
            }
            backend.close();
        }
        assertEquals(20, keptTotal, "the 4 ranges must partition all 20 Date keys (no drop, no dup)");
    }

    @Test
    void beanKeyRescaleRestore_keysRemainReadableAndWritable() throws Exception {
        MemoryKeyedStateBackend<BeanKey> source = new MemoryKeyedStateBackend<>(BeanKey.class, MAX_P);
        Map<String, BeanKey> allKeys = new LinkedHashMap<>();
        Map<String, Long> values = new LinkedHashMap<>();
        ValueState<Long> state = source.getState(COUNT);
        for (int i = 0; i < 20; i++) {
            BeanKey key = new BeanKey();
            key.setId("bean-" + i);
            key.setSalt(i);
            source.setCurrentKey(key);
            state.update(2000L + i);
            allKeys.put(key.getId(), key);
            values.put(key.getId(), 2000L + i);
        }
        StateSnapshot snapshot = jsonRoundTrip(source.snapshotState());
        source.close();

        int keptTotal = 0;
        for (int sub = 0; sub < NEW_PARALLELISM; sub++) {
            MemoryKeyedStateBackend<BeanKey> backend = new MemoryKeyedStateBackend<>(BeanKey.class, MAX_P);
            KeyGroupRange range = KeyGroupAssignment.computeKeyGroupRangeForSubtaskIndex(MAX_P, NEW_PARALLELISM, sub);
            backend.setTargetKeyGroupRange(range);
            backend.restoreState(snapshot);

            ValueState<Long> restoredState = backend.getState(COUNT);
            for (BeanKey key : allKeys.values()) {
                backend.setCurrentKey(key);
                if (range.contains(KeyGroupAssignment.assignToKeyGroup(key, MAX_P))) {
                    Long value = restoredState.value();
                    assertNotNull(value, "owned bean key " + key.getId() + " must be restored on subtask " + sub);
                    assertEquals(values.get(key.getId()), value);
                    keptTotal++;
                    restoredState.update(value + 1);
                    assertEquals(Long.valueOf(value + 1), restoredState.value(),
                            "restored bean key must remain writable on subtask " + sub);
                } else {
                    assertNull(restoredState.value(),
                            "non-owned bean key " + key.getId() + " must NOT appear on subtask " + sub);
                }
            }
            backend.close();
        }
        assertEquals(20, keptTotal, "the 4 ranges must partition all 20 bean keys (no drop, no dup)");
    }
}

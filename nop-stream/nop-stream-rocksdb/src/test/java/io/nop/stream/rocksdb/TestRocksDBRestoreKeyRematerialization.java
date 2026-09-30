package io.nop.stream.rocksdb;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.json.JsonWhitelist;
import io.nop.stream.core.common.state.MapState;
import io.nop.stream.core.common.state.MapStateDescriptor;
import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.core.common.state.backend.StateSnapshot;
import io.nop.stream.core.common.state.shard.KeyGroupAssignment;
import io.nop.stream.core.common.state.shard.KeyGroupRange;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * ST-02 regression (RocksDB full-JSON restore path): the three restore entry
 * points used to compute the key-group prefix from the raw JSON-native key
 * ({@code e.get("key")}) without re-materializing it to the backend's declared
 * key class. For keys whose JSON round trip changes runtime type/hash (Date,
 * {@code @DataBean} beans) the restored rows carried a group prefix that live
 * typed-key reads never ask for — silent read miss on every restored key.
 * After the fix the restore re-materializes the key first (from the declared
 * keyType / snapshot-header {@code keyType}), so restored state is readable
 * and writable again, and partial (KeyGroupRange) restore conserves keys.
 */
class TestRocksDBRestoreKeyRematerialization {

    private static final int MAX_P = 16;
    private static final ValueStateDescriptor<Long> COUNT =
            new ValueStateDescriptor<>("count", Long.class, 0L);
    private static final MapStateDescriptor<String, Long> MAP =
            new MapStateDescriptor<>("map", String.class, Long.class);

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

        // Value semantics expected of any keyed-state key class (unrelated to
        // sharding, which uses the JSON-canonical stableHash).
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

    @TempDir
    Path tempDir;

    private RocksDBKeyedStateBackend<?> newBackend(String name, Class<?> keyType) {
        java.io.File dir = tempDir.resolve(name).toFile();
        dir.mkdirs();
        return new RocksDBKeyedStateBackend<>(dir.getAbsolutePath(), keyType, MAX_P, null);
    }

    @Test
    public void beanKeyValueState_checkpointRestoreReadWrite() throws Exception {
        RocksDBKeyedStateBackend<BeanKey> source = new RocksDBKeyedStateBackend<>(
                tempDir.resolve("bean-src").toFile().getAbsolutePath(), BeanKey.class, MAX_P, null);
        Map<String, BeanKey> allKeys = new LinkedHashMap<>();
        Map<String, Long> values = new LinkedHashMap<>();
        ValueState<Long> state = source.getState(COUNT);
        for (int i = 0; i < 10; i++) {
            BeanKey key = new BeanKey();
            key.setId("bean-" + i);
            key.setSalt(i);
            source.setCurrentKey(key);
            state.update(1000L + i);
            allKeys.put(key.getId(), key);
            values.put(key.getId(), 1000L + i);
        }
        StateSnapshot snapshot = jsonRoundTrip(source.snapshotState());
        source.close();

        @SuppressWarnings("unchecked")
        RocksDBKeyedStateBackend<BeanKey> restored = (RocksDBKeyedStateBackend<BeanKey>) newBackend("bean-dst", BeanKey.class);
        restored.restoreState(snapshot);

        ValueState<Long> restoredState = restored.getState(COUNT);
        for (BeanKey key : allKeys.values()) {
            restored.setCurrentKey(key);
            assertEquals(values.get(key.getId()), restoredState.value(),
                    "restored bean key " + key.getId() + " must be readable after checkpoint restore");
        }
        // 可读写: the restored key stays writable with stable placement.
        restored.setCurrentKey(allKeys.get("bean-3"));
        restoredState.update(9999L);
        assertEquals(Long.valueOf(9999L), restoredState.value());
        restored.close();
    }

    @Test
    public void dateKeyMapState_checkpointRestoreReadWrite() throws Exception {
        RocksDBKeyedStateBackend<Date> source = new RocksDBKeyedStateBackend<>(
                tempDir.resolve("date-src").toFile().getAbsolutePath(), Date.class, MAX_P, null);
        Map<Date, Long> allKeys = new LinkedHashMap<>();
        for (int i = 0; i < 8; i++) {
            Date key = new Date(1690000000000L + i * 86_400_000L);
            source.setCurrentKey(key);
            source.getMapState(MAP).put("day-" + i, (long) i);
            allKeys.put(key, (long) i);
        }
        StateSnapshot snapshot = jsonRoundTrip(source.snapshotState());
        source.close();

        @SuppressWarnings("unchecked")
        RocksDBKeyedStateBackend<Date> restored = (RocksDBKeyedStateBackend<Date>) newBackend("date-dst", Date.class);
        restored.restoreState(snapshot);

        MapState<String, Long> mapState = restored.getMapState(MAP);
        for (Map.Entry<Date, Long> e : allKeys.entrySet()) {
            restored.setCurrentKey(e.getKey());
            assertEquals(e.getValue(), mapState.get("day-" + e.getValue()),
                    "restored Date key must keep its map state readable after checkpoint restore");
        }
        // 可读写: restored key accepts new map writes.
        Date first = allKeys.keySet().iterator().next();
        restored.setCurrentKey(first);
        mapState.put("extra", 42L);
        assertEquals(Long.valueOf(42L), mapState.get("extra"));
        restored.close();
    }

    @Test
    public void beanKeyPartialRestore_conservesKeysAcrossRanges() throws Exception {
        RocksDBKeyedStateBackend<BeanKey> source = new RocksDBKeyedStateBackend<>(
                tempDir.resolve("bean-partial-src").toFile().getAbsolutePath(), BeanKey.class, MAX_P, null);
        Map<String, BeanKey> allKeys = new LinkedHashMap<>();
        Map<String, Long> values = new LinkedHashMap<>();
        ValueState<Long> state = source.getState(COUNT);
        for (int i = 0; i < 30; i++) {
            BeanKey key = new BeanKey();
            key.setId("partial-" + i);
            key.setSalt(i);
            source.setCurrentKey(key);
            state.update(3000L + i);
            allKeys.put(key.getId(), key);
            values.put(key.getId(), 3000L + i);
        }
        StateSnapshot snapshot = jsonRoundTrip(source.snapshotState());
        source.close();

        int keptTotal = 0;
        for (int sub = 0; sub < 4; sub++) {
            @SuppressWarnings("unchecked")
            RocksDBKeyedStateBackend<BeanKey> backend = (RocksDBKeyedStateBackend<BeanKey>)
                    newBackend("bean-partial-dst-" + sub, BeanKey.class);
            KeyGroupRange range = KeyGroupAssignment.computeKeyGroupRangeForSubtaskIndex(MAX_P, 4, sub);
            backend.setTargetKeyGroupRange(range);
            backend.restoreState(snapshot);

            ValueState<Long> restoredState = backend.getState(COUNT);
            for (BeanKey key : allKeys.values()) {
                backend.setCurrentKey(key);
                if (range.contains(KeyGroupAssignment.assignToKeyGroup(key, MAX_P))) {
                    assertEquals(values.get(key.getId()), restoredState.value(),
                            "owned bean key " + key.getId() + " must be restored on subtask " + sub);
                    keptTotal++;
                } else {
                    assertNull(restoredState.value(),
                            "non-owned bean key " + key.getId() + " must NOT appear on subtask " + sub);
                }
            }
            backend.close();
        }
        assertEquals(30, keptTotal, "the 4 subtask ranges must partition all 30 bean keys");
    }
}

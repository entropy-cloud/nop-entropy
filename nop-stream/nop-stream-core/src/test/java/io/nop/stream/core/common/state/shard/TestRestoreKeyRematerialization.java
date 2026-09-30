package io.nop.stream.core.common.state.shard;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.json.JsonWhitelist;
import io.nop.stream.core.exceptions.StreamException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ST-02 regression: raw snapshot keys are JSON-native forms whose hash differs
 * from the live typed key (a Date round-trips as a field map, a
 * {@code @DataBean} key as a LinkedHashMap). Every key-group decision on the
 * restore path — the {@link KeyGroupRangeRestoreFilter} rescale filter and the
 * {@link KeyGroupReshard} redistribution — must re-materialize the key to the
 * declared key class BEFORE hashing, so the restored placement matches the
 * live {@code routeKey}/storage-key group.
 */
class TestRestoreKeyRematerialization {

    private static final int MAX_P = 16;

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
        // java.util.Date is not @DataBean; production jobs with Date keys must
        // register it in the JSON whitelist to checkpoint at all.
        JsonWhitelist.add("java.util.Date");
    }

    /** The storage-layer round trip a checkpoint persist performs on every key. */
    private static Object jsonRoundTrip(Object typedKey) {
        return JsonTool.parseNonStrict(JsonTool.serialize(typedKey, false));
    }

    private static BeanKey beanKey(String id, int salt) {
        BeanKey key = new BeanKey();
        key.setId(id);
        key.setSalt(salt);
        return key;
    }

    // ------------------------------------------------------------------
    //  StateKeyRematerializer
    // ------------------------------------------------------------------

    @Test
    void rematerializeKey_recoversDateAndBeanFromJsonNativeForm() {
        Date date = new Date(1700000000000L + 42);
        Object rawDate = jsonRoundTrip(date);
        assertTrue(rawDate instanceof Map, "Date must degrade to a JSON map: " + rawDate.getClass());
        assertNotEquals(date, rawDate);

        Object rematerializedDate = StateKeyRematerializer.rematerializeKey(rawDate, Date.class);
        assertEquals(date, rematerializedDate);
        assertEquals(KeyGroupAssignment.stableHash(date), KeyGroupAssignment.stableHash(rematerializedDate),
                "rematerialized Date must hash like the live typed key");

        BeanKey bean = beanKey("k1", 7);
        Object rawBean = jsonRoundTrip(bean);
        assertTrue(rawBean instanceof Map, "bean key must degrade to a LinkedHashMap");

        Object rematerializedBean = StateKeyRematerializer.rematerializeKey(rawBean, BeanKey.class);
        assertEquals(bean.getId(), ((BeanKey) rematerializedBean).getId());
        assertEquals(bean.getSalt(), ((BeanKey) rematerializedBean).getSalt());
        assertEquals(KeyGroupAssignment.stableHash(bean), KeyGroupAssignment.stableHash(rematerializedBean),
                "rematerialized bean must hash like the live typed key");
    }

    @Test
    void rematerializeKey_isIdentityForMatchingTypesAndNull() {
        String s = "raw";
        assertSame(s, StateKeyRematerializer.rematerializeKey(s, String.class));
        assertSame(s, StateKeyRematerializer.rematerializeKey(s, null));
        assertNull(StateKeyRematerializer.rematerializeKey(null, Date.class));
        Long v = 123L;
        assertSame(v, StateKeyRematerializer.rematerializeKey(v, Long.class));
    }

    @Test
    void rematerializeKey_failsFastOnMismatchedKeyType() {
        // A String-keyed snapshot restored into a bean-keyed backend must fail
        // loudly instead of silently keeping the mismatched key.
        assertThrows(StreamException.class,
                () -> StateKeyRematerializer.rematerializeKey("not-a-bean", BeanKey.class));
    }

    // ------------------------------------------------------------------
    //  KeyGroupRangeRestoreFilter
    // ------------------------------------------------------------------

    @Test
    void keyOwnedByRange_usesRematerializedKeyGroup() {
        Date date = new Date(1690000000000L);
        Object raw = jsonRoundTrip(date);
        int typedGroup = KeyGroupAssignment.assignToKeyGroup(date, MAX_P);
        KeyGroupRange ownerRange = KeyGroupAssignment.computeKeyGroupRangeForSubtaskIndex(MAX_P, 4,
                KeyGroupAssignment.assignKeyGroupToSubtask(typedGroup, MAX_P, 4));

        assertTrue(KeyGroupRangeRestoreFilter.keyOwnedByRange(raw, ownerRange, MAX_P, Date.class),
                "raw JSON form must be owned where the typed key is owned");
        // A range that excludes the typed group must exclude the raw form too.
        KeyGroupRange otherRange = KeyGroupAssignment.computeKeyGroupRangeForSubtaskIndex(MAX_P, 4,
                (KeyGroupAssignment.assignKeyGroupToSubtask(typedGroup, MAX_P, 4) + 1) % 4);
        if (!otherRange.contains(typedGroup)) {
            assertFalse(KeyGroupRangeRestoreFilter.keyOwnedByRange(raw, otherRange, MAX_P, Date.class));
        }
    }

    @Test
    void filterKeyedStates_withKeyType_conservesEntriesAcrossRanges() {
        Map<String, Object> stateInfo = new LinkedHashMap<>();
        stateInfo.put("stateType", "ValueState");
        stateInfo.put("valueType", "java.lang.Long");
        List<Map<String, Object>> entries = new ArrayList<>();
        Map<Object, Long> typedValues = new LinkedHashMap<>();
        for (int i = 0; i < 20; i++) {
            Date key = new Date(1690000000000L + i * 3_600_000L);
            typedValues.put(key, 1000L + i);
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("namespace", "_default_");
            e.put("key", jsonRoundTrip(key));
            e.put("value", 1000L + i);
            entries.add(e);
        }
        stateInfo.put("entries", entries);
        Map<String, Object> states = new LinkedHashMap<>();
        states.put("counter", stateInfo);

        int keptTotal = 0;
        for (int sub = 0; sub < 4; sub++) {
            KeyGroupRange range = KeyGroupAssignment.computeKeyGroupRangeForSubtaskIndex(MAX_P, 4, sub);
            Map<String, Object> filtered = KeyGroupRangeRestoreFilter.filterKeyedStates(states, range, MAX_P, Date.class);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> kept = (List<Map<String, Object>>)
                    ((Map<String, Object>) filtered.get("counter")).get("entries");
            for (Map<String, Object> e : kept) {
                Date typed = (Date) StateKeyRematerializer.rematerializeKey(e.get("key"), Date.class);
                assertTrue(range.contains(KeyGroupAssignment.assignToKeyGroup(typed, MAX_P)),
                        "kept entry must belong to the target range");
                assertEquals(typedValues.get(typed), e.get("value"));
                keptTotal++;
            }
        }
        assertEquals(20, keptTotal, "the 4 subtask ranges must partition all 20 entries (no drop, no dup)");
    }

    // ------------------------------------------------------------------
    //  KeyGroupReshard
    // ------------------------------------------------------------------

    @Test
    void redistributeStates_withKeyType_placesByTypedKeyGroup() {
        Map<String, Object> stateInfo = new LinkedHashMap<>();
        stateInfo.put("stateType", "ValueState");
        stateInfo.put("valueType", "java.lang.Long");
        List<Map<String, Object>> entries = new ArrayList<>();
        Map<String, BeanKey> typedKeys = new LinkedHashMap<>();
        for (int i = 0; i < 12; i++) {
            BeanKey key = beanKey("reshard-" + i, i);
            typedKeys.put("reshard-" + i, key);
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("namespace", "_default_");
            e.put("key", jsonRoundTrip(key));
            e.put("value", (long) i);
            entries.add(e);
        }
        stateInfo.put("entries", entries);
        Map<String, Object> globalStates = new LinkedHashMap<>();
        globalStates.put("counter", stateInfo);

        int newMaxP = 8;
        int newParallelism = 2;
        Map<Integer, Map<String, Object>> redistributed =
                KeyGroupReshard.redistributeStates(globalStates, newMaxP, newParallelism, BeanKey.class);

        int total = 0;
        for (Map.Entry<Integer, Map<String, Object>> bucket : redistributed.entrySet()) {
            int subtaskIndex = bucket.getKey();
            @SuppressWarnings("unchecked")
            Map<String, Object> info = (Map<String, Object>) bucket.getValue().get("counter");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> kept = (List<Map<String, Object>>) info.get("entries");
            for (Map<String, Object> e : kept) {
                BeanKey typed = (BeanKey) StateKeyRematerializer.rematerializeKey(e.get("key"), BeanKey.class);
                int expectedSubtask = KeyGroupAssignment.assignToSubtask(typed, newMaxP, newParallelism);
                assertEquals(expectedSubtask, subtaskIndex,
                        "entry for key " + typed.getId() + " must land on its typed-key owner subtask");
                total++;
            }
        }
        assertEquals(12, total, "redistribution must conserve every entry");
    }
}

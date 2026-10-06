/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.buffer;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * WI12: the per-key event-time ordered element buffer backing the analysis window
 * (OVER) operator — an independently testable component (roadmap A5: the "per-key
 * ordered buffer" keyed-state usage evidence).
 *
 * <p>Each key holds a TreeMap keyed by (timestamp, insertion-seq) so out-of-order
 * elements are sorted by event time and same-timestamp insertions keep arrival
 * order. {@link #trimToWatermark} drops buffered elements at or below the
 * watermark once their frame result has been emitted (D1=(a): the last computed
 * frame value is the result — no retract markers, re-open is event-time
 * recomputation per Q2).
 *
 * <p>Serializable so it can ride inside operators that are deep-copied per subtask.
 */
public class PerKeyOrderedBuffer<K, V> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** insertion counter disambiguates same-timestamp elements (stable order). */
    private long insertionSeq;

    /** key → (ts, seq) → value */
    private final Map<K, TreeMap<long[], V>> buffers = new HashMap<>();

    /** named serializable comparator (lambda comparators are not serializable). */
    static final class TsSeqComparator implements java.util.Comparator<long[]>, Serializable {
        private static final long serialVersionUID = 1L;

        static final TsSeqComparator INSTANCE = new TsSeqComparator();

        @Override
        public int compare(long[] a, long[] b) {
            int c = Long.compare(a[0], b[0]);
            return c != 0 ? c : Long.compare(a[1], b[1]);
        }
    }

    /**
     * merge another buffer's entries into this one (restore path).
     * R5 CC-11: insertionSeq advances past the largest merged entry seq, so
     * post-merge {@link #add} allocations can never collide with (and silently
     * overwrite) a merged (ts, seq) key.
     *
     * <p>R6 REG-01 (plan 2323): the advance consumes the other buffer's exact
     * {@code insertionSeq} counter. The previous {@code lastKey()[1]} estimate read
     * the seq of the largest-TIMESTAMP entry, not the largest seq — with out-of-order
     * event timestamps ({@code TsSeqComparator} orders by ts first) the estimate
     * under-counted and a post-restore {@link #add} could collide with (and silently
     * overwrite) a merged key.
     */
    public void putAll(PerKeyOrderedBuffer<K, V> other) {
        for (Map.Entry<K, TreeMap<long[], V>> e : other.buffers.entrySet()) {
            TreeMap<long[], V> tree = buffers.computeIfAbsent(e.getKey(), k -> new TreeMap<>(TsSeqComparator.INSTANCE));
            tree.putAll(e.getValue());
        }
        insertionSeq = Math.max(insertionSeq, other.insertionSeq);
    }

    /**
     * R6 ST-02 (plan 2323): point-in-time frozen copy for operator-state snapshots.
     * Round-trips through the serializable form so post-snapshot mutations of the
     * live buffer cannot leak into the state handed to the async persist thread.
     * The copy is a full {@code PerKeyOrderedBuffer}, so in-memory restore paths
     * that match on the concrete type keep working (WindowOperator
     * clone-accumulator discipline; the JSON format is unchanged).
     */
    @SuppressWarnings("unchecked")
    public PerKeyOrderedBuffer<K, V> freeze() {
        return (PerKeyOrderedBuffer<K, V>) fromSerializableForm(toSerializableForm());
    }

    /**
     * R5 KR-7: JSON-safe serializable form (the operator-state transport for
     * the join/over-window working views). Keys and values carry their class
     * names; {@link #fromSerializableForm} re-hydrates them so no JSON-native
     * drift survives the round trip (CepOperator ledger keyClass precedent).
     */
    public Map<String, Object> toSerializableForm() {
        Map<String, Object> form = new LinkedHashMap<>();
        form.put("@type", "PerKeyOrderedBuffer");
        form.put("insertionSeq", insertionSeq);
        List<Map<String, Object>> keyEntries = new ArrayList<>();
        for (Map.Entry<K, TreeMap<long[], V>> e : buffers.entrySet()) {
            Map<String, Object> ke = new LinkedHashMap<>();
            // R6 plan 2323: the form must be SELF-CONTAINED — keys and values are
            // defensively copied at form-build time. Callers freeze the form at
            // snapshot time (EquiJoin/OverWindow snapshotState -> freeze()) and the
            // async persist thread serializes it later; live leaf references would
            // let post-barrier mutations (in-place matched flags) tear the durable
            // payload. The copy is JSON-native, so the persisted bytes are
            // unchanged (this is exactly the shape JSON serialization produced).
            ke.put("key", io.nop.stream.core.util.SnapshotCopyUtil.defensiveCopy(e.getKey()));
            ke.put("keyClassName", e.getKey() != null ? e.getKey().getClass().getName() : null);
            String valueClassName = null;
            List<List<Object>> items = new ArrayList<>();
            for (Map.Entry<long[], V> te : e.getValue().entrySet()) {
                if (valueClassName == null && te.getValue() != null) {
                    valueClassName = te.getValue().getClass().getName();
                }
                items.add(java.util.Arrays.asList(te.getKey()[0], te.getKey()[1],
                        io.nop.stream.core.util.SnapshotCopyUtil.defensiveCopy(te.getValue())));
            }
            ke.put("valueClassName", valueClassName);
            ke.put("items", items);
            keyEntries.add(ke);
        }
        form.put("keys", keyEntries);
        return form;
    }

    /**
     * R5 KR-7: rebuild a live buffer from {@link #toSerializableForm()}. Keys
     * and values whose runtime class drifted to their JSON-native shape are
     * re-hydrated via the recorded class names. Malformed forms fail fast with
     * a typed error (a deliberate deviation from the TimerSnapshot null-return
     * precedent — silence here would hide a corrupt working view).
     */
    @SuppressWarnings("unchecked")
    public static PerKeyOrderedBuffer<?, ?> fromSerializableForm(Map<String, Object> form) {
        if (form == null || !"PerKeyOrderedBuffer".equals(form.get("@type"))) {
            throw new io.nop.stream.core.exceptions.StreamException(io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_STATE_ERROR)
                    .param(io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL,
                            "PerKeyOrderedBuffer form is missing @type=PerKeyOrderedBuffer; snapshot is corrupt or foreign");
        }
        PerKeyOrderedBuffer<Object, Object> buffer = new PerKeyOrderedBuffer<>();
        Object seqObj = form.get("insertionSeq");
        if (seqObj instanceof Number) {
            buffer.insertionSeq = ((Number) seqObj).longValue();
        }
        Object keysObj = form.get("keys");
        if (!(keysObj instanceof List)) {
            throw new io.nop.stream.core.exceptions.StreamException(io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_STATE_ERROR)
                    .param(io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL,
                            "PerKeyOrderedBuffer form has no keys list; snapshot is corrupt or foreign");
        }
        long maxItemSeq = -1L;
        for (Object keObj : (List<Object>) keysObj) {
            if (!(keObj instanceof Map)) {
                throw new io.nop.stream.core.exceptions.StreamException(io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_STATE_ERROR)
                        .param(io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL,
                                "PerKeyOrderedBuffer form key entry is not a map; snapshot is corrupt or foreign");
            }
            Map<String, Object> ke = (Map<String, Object>) keObj;
            Object key = rehydrate(ke.get("key"), (String) ke.get("keyClassName"));
            String valueClassName = (String) ke.get("valueClassName");
            TreeMap<long[], Object> tree = buffer.buffers.computeIfAbsent(key, k -> new TreeMap<>(TsSeqComparator.INSTANCE));
            Object itemsObj = ke.get("items");
            if (!(itemsObj instanceof List)) {
                throw new io.nop.stream.core.exceptions.StreamException(io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_STATE_ERROR)
                        .param(io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL,
                                "PerKeyOrderedBuffer form key entry has no items list; snapshot is corrupt or foreign");
            }
            for (Object itemObj : (List<Object>) itemsObj) {
                if (!(itemObj instanceof List) || ((List<Object>) itemObj).size() != 3) {
                    throw new io.nop.stream.core.exceptions.StreamException(io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_STATE_ERROR)
                            .param(io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL,
                                    "PerKeyOrderedBuffer form item must be [ts, seq, value]; snapshot is corrupt or foreign");
                }
                List<Object> item = (List<Object>) itemObj;
                long ts = ((Number) item.get(0)).longValue();
                long seq = ((Number) item.get(1)).longValue();
                Object value = rehydrate(item.get(2), valueClassName);
                tree.put(new long[] {ts, seq}, value);
                maxItemSeq = Math.max(maxItemSeq, seq);
            }
        }
        buffer.insertionSeq = Math.max(buffer.insertionSeq, maxItemSeq + 1);
        return buffer;
    }

    private static Object rehydrate(Object value, String className) {
        if (value == null) {
            return null;
        }
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        String target = className != null ? className : value.getClass().getName();
        if (target.equals(value.getClass().getName())) {
            return value;
        }
        try {
            return io.nop.core.lang.json.JsonTool.parseBeanFromText(
                    io.nop.core.lang.json.JsonTool.serialize(value, false), Class.forName(target));
        } catch (ClassNotFoundException e) {
            throw new io.nop.stream.core.exceptions.StreamException(io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_STATE_ERROR, e)
                    .param(io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL,
                            "PerKeyOrderedBuffer form references unknown class " + target);
        }
    }

    /**
     * Adds one element for the key at the given event time. Out-of-order inserts are
     * sorted by timestamp; equal timestamps keep arrival order.
     *
     * @return the allocated (timestamp, seq) key — callers use it as the exact
     *         keyed-state entry key suffix (same-ts inserts get distinct seqs, so
     *         the keyed mirror never collides)
     */
    public long[] add(K key, long timestamp, V value) {
        TreeMap<long[], V> tree = buffers.computeIfAbsent(key, k -> new TreeMap<>(TsSeqComparator.INSTANCE));
        long[] tsKey = new long[]{timestamp, insertionSeq++};
        tree.put(tsKey, value);
        return tsKey;
    }

    /**
     * The buffered elements for the key, ordered by (event time, arrival).
     */
    public List<V> sortedView(K key) {
        TreeMap<long[], V> tree = buffers.get(key);
        if (tree == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(tree.values());
    }

    /**
     * The buffered (timestamp, value) pairs for the key, ordered by event time.
     */
    public List<long[]> sortedTimestamps(K key) {
        TreeMap<long[], V> tree = buffers.get(key);
        if (tree == null) {
            return Collections.emptyList();
        }
        List<long[]> keys = new ArrayList<>(tree.keySet());
        keys.sort(TsSeqComparator.INSTANCE);
        return keys;
    }

    public V valueAt(K key, long[] tsKey) {
        TreeMap<long[], V> tree = buffers.get(key);
        return tree == null ? null : tree.get(tsKey);
    }

    /**
     * Drops all buffered elements with timestamp &lt;= watermark. Elements at exactly
     * the watermark are dropped too — their frame result has been emitted by the
     * operator before this call (final-value semantics, no retract).
     *
     * @return the number of dropped elements
     */
    public int trimToWatermark(K key, long watermark) {
        TreeMap<long[], V> tree = buffers.get(key);
        if (tree == null) {
            return 0;
        }
        int before = tree.size();
        tree.headMap(new long[]{watermark, Long.MAX_VALUE}).clear();
        if (tree.isEmpty()) {
            buffers.remove(key);
        }
        return before - tree.size();
    }

    /**
     * Sliding-frame trim: keeps only the newest {@code count} elements for the key
     * (older elements outside the sliding frame are dropped).
     *
     * @return the number of dropped elements
     */
    public int trimToCount(K key, int count) {
        TreeMap<long[], V> tree = buffers.get(key);
        if (tree == null || tree.size() <= count) {
            return 0;
        }
        int drop = tree.size() - count;
        List<long[]> keys = new ArrayList<>(tree.keySet());
        keys.sort(TsSeqComparator.INSTANCE);
        for (int i = 0; i < drop; i++) {
            tree.remove(keys.get(i));
        }
        return drop;
    }

    public int size(K key) {
        TreeMap<long[], V> tree = buffers.get(key);
        return tree == null ? 0 : tree.size();
    }

    /** The set of keys with buffered elements. */
    public Iterable<K> keys() {
        return new ArrayList<>(buffers.keySet());
    }

    /**
     * trimToWatermark variant returning the exact (ts, seq) keys of the dropped
     * entries — the operator uses them to remove the matching keyed-state entries.
     */
    public List<long[]> trimToWatermarkWithKeys(K key, long watermark) {
        TreeMap<long[], V> tree = buffers.get(key);
        if (tree == null) {
            return Collections.emptyList();
        }
        List<long[]> dropped = new ArrayList<>();
        for (long[] tsKey : tree.keySet()) {
            if (tsKey[0] <= watermark) {
                dropped.add(tsKey);
            }
        }
        for (long[] tsKey : dropped) {
            tree.remove(tsKey);
        }
        if (tree.isEmpty()) {
            buffers.remove(key);
        }
        return dropped;
    }

    /** trimToCount variant returning the exact (ts, seq) keys of the dropped entries. */
    public List<long[]> trimToCountWithKeys(K key, int count) {
        TreeMap<long[], V> tree = buffers.get(key);
        if (tree == null || tree.size() <= count) {
            return Collections.emptyList();
        }
        List<long[]> keys = new ArrayList<>(tree.keySet());
        keys.sort(TsSeqComparator.INSTANCE);
        List<long[]> dropped = keys.subList(0, tree.size() - count);
        List<long[]> copy = new ArrayList<>(dropped);
        for (long[] tsKey : copy) {
            tree.remove(tsKey);
        }
        if (tree.isEmpty()) {
            buffers.remove(key);
        }
        return copy;
    }

    /** the insertion-seq recorded for a (key, ts) entry — keyed-state key suffix. */
    public long seqFor(K key, long ts) {
        TreeMap<long[], V> tree = buffers.get(key);
        if (tree != null) {
            for (long[] tsKey : tree.keySet()) {
                if (tsKey[0] == ts) {
                    return tsKey[1];
                }
            }
        }
        return -1L;
    }

}

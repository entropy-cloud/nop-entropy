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

    /** merge another buffer's entries into this one (restore path). */
    public void putAll(PerKeyOrderedBuffer<K, V> other) {
        for (Map.Entry<K, TreeMap<long[], V>> e : other.buffers.entrySet()) {
            TreeMap<long[], V> tree = buffers.computeIfAbsent(e.getKey(), k -> new TreeMap<>(TsSeqComparator.INSTANCE));
            tree.putAll(e.getValue());
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

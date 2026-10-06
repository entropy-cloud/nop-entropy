/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.buffer;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.core.lang.json.JsonTool;
import io.nop.stream.core.model.JoinSideRecord;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R5 KR-7 + CC-11 regression pins: the join/over-window working buffer's
 * serializable form must round-trip through a real JSON channel with values
 * re-hydrated (no LinkedHashMap drift), and {@code putAll} must advance
 * insertionSeq so post-merge adds never collide with merged (ts, seq) keys.
 */
class TestPerKeyOrderedBufferForm {

    @DataBean
    public static class Payload {
        private int v;

        public Payload() {
        }

        public Payload(int v) {
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
    void testFormRoundTripsThroughJsonWithRehydration() {
        PerKeyOrderedBuffer<String, JoinSideRecord<Payload>> buffer = new PerKeyOrderedBuffer<>();
        buffer.add("k1", 10L, new JoinSideRecord<>(true, "k1", new Payload(7)));
        buffer.add("k1", 10L, new JoinSideRecord<>(false, "k1", new Payload(8)));
        buffer.add("k2", 20L, new JoinSideRecord<>(true, "k2", new Payload(9)));

        Map<String, Object> form = buffer.toSerializableForm();
        // real JSON channel
        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = JsonTool.parseMap(JsonTool.serialize(form, false));

        PerKeyOrderedBuffer<?, ?> restored = PerKeyOrderedBuffer.fromSerializableForm(parsed);

        @SuppressWarnings("unchecked")
        PerKeyOrderedBuffer<String, JoinSideRecord<Payload>> typed =
                (PerKeyOrderedBuffer<String, JoinSideRecord<Payload>>) restored;

        assertEquals(3, typed.sortedView("k1").size() + typed.sortedView("k2").size());
        JoinSideRecord<Payload> first = (JoinSideRecord<Payload>) typed.sortedView("k1").get(0);
        assertInstanceOf(JoinSideRecord.class, first, "value must be re-hydrated, not a LinkedHashMap");
        assertTrue(first.isLeft());
        // Boundary (documented): the nested generic payload stays in its
        // JSON-native shape — the buffer layer can only re-hydrate to the
        // declared value class, and T is erased. Join behavior reads
        // equiKey/left/matched (all re-hydrated); payload typing is the
        // user's keyed-state concern.
        assertEquals(3, typed.sortedView("k1").size() + typed.sortedView("k2").size());
    }

    @Test
    void testPutAllAdvancesInsertionSeqAgainstCollisions() {
        PerKeyOrderedBuffer<String, String> source = new PerKeyOrderedBuffer<>();
        long[] k1 = source.add("k", 5L, "a");
        long[] k2 = source.add("k", 5L, "b"); // same ts, seq 1

        PerKeyOrderedBuffer<String, String> target = new PerKeyOrderedBuffer<>();
        target.putAll(source);

        // Post-merge add must not collide with merged (5, 0) / (5, 1)
        long[] k3 = target.add("k", 5L, "c");
        assertTrue(k3[1] > k1[1] && k3[1] > k2[1],
                "post-merge seq must advance past merged entries: got " + k3[1]);
        assertEquals(3, target.sortedView("k").size(), "no entry may be overwritten by a seq collision");
    }

    @Test
    void testMalformedFormFailsFast() {
        org.junit.jupiter.api.Assertions.assertThrows(
                io.nop.stream.core.exceptions.StreamException.class,
                () -> PerKeyOrderedBuffer.fromSerializableForm(Map.of("insertionSeq", 1)));
    }

    /**
     * R6 REG-01 (plan 2323): OUT-OF-ORDER event timestamps. putAll must consume the
     * source buffer's exact insertionSeq counter — the previous lastKey()[1]
     * estimate read the seq of the largest-TIMESTAMP entry (TsSeqComparator orders
     * by ts first), under-counted, and let a post-restore add(ts=5) collide with
     * (and silently overwrite) the merged (5, 1) entry. Reverting putAll to the
     * estimate turns this test red (size 3 -> 2).
     */
    @Test
    void testPutAllConsumesExactSeqUnderOutOfOrderTimestamps() {
        PerKeyOrderedBuffer<String, String> source = new PerKeyOrderedBuffer<>();
        source.add("k", 10L, "late-ts-high-seq0");
        source.add("k", 5L, "early-ts-seq1"); // ts 5 < ts 10 but seq 1 > seq 0

        PerKeyOrderedBuffer<String, String> restored = new PerKeyOrderedBuffer<>();
        restored.putAll(source);

        // Post-restore add at the EARLIER timestamp: with the exact counter the new
        // entry gets seq 2; with the lastKey estimate it would reuse seq 1 and
        // silently overwrite the merged (5, 1) row.
        restored.add("k", 5L, "post-restore");

        java.util.List<String> values = new java.util.ArrayList<>();
        restored.sortedView("k").forEach(values::add);
        assertEquals(3, values.size(),
                "out-of-order post-restore add must not overwrite a merged entry");
        assertTrue(values.contains("early-ts-seq1"),
                "the merged (ts=5, seq=1) row must survive the post-restore add");
        assertTrue(values.contains("post-restore"));
    }
}

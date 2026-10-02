/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.operators.join;

import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.common.state.backend.IStateBackend;
import io.nop.stream.core.common.state.backend.memory.MemoryStateBackend;
import io.nop.stream.core.model.JoinMatch;
import io.nop.stream.core.model.JoinSideRecord;
import io.nop.stream.core.model.JoinType;
import io.nop.stream.core.operators.Output;
import io.nop.stream.core.operators.join.EquiJoinOperator;
import io.nop.stream.core.streamrecord.LatencyMarker;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.streamrecord.watermark.WatermarkStatus;
import io.nop.stream.core.util.OutputTag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI13: the four JoinType semantics of the hash equi-join (join-operator.md §3) —
 * discriminating scenarios: pairs emit eagerly on the later arrival (cross product
 * per key); outer completions emit AT THE WATERMARK and only for never-matched
 * records (an arrival-time completion would double-emit when a partner arrives
 * later out of order); INNER completes nothing (isOuter() branch consumed by the
 * completion pass); buffers trim at the watermark (bounded state — no matches
 * across the watermark frontier).
 */
public class TestEquiJoinHashSemantics {

    private static EquiJoinOperator<String, String> join(JoinType type) throws Exception {
        EquiJoinOperator<String, String> op = new EquiJoinOperator<>(type, null, 0L);
        IStateBackend stateBackend = new MemoryStateBackend();
        op.setStateBackend(stateBackend);
        op.setOutput(new ListOutput());
        op.open();
        return op;
    }

    private static void feed(EquiJoinOperator<String, String> op, boolean left, String key,
                             String payload, long ts) throws Exception {
        op.processElement(new StreamRecord<>(new JoinSideRecord<Object>(left, key, payload), ts));
    }

    private static void wm(EquiJoinOperator<String, String> op, long ts) throws Exception {
        op.processWatermark(new Watermark(ts));
    }

    private static List<String> outs(EquiJoinOperator<String, String> op) {
        return ((ListOutput) op.getOutput()).records;
    }

    @Test
    public void innerEmitsEagerPairsOnly() throws Exception {
        EquiJoinOperator<String, String> op = join(JoinType.INNER);
        feed(op, true, "k", "a", 10);
        assertEquals(List.of(), outs(op), "no right side yet — nothing emitted");
        feed(op, false, "k", "b", 20);
        assertEquals(List.of("J|a|b"), outs(op), "pair emitted, left first");
        feed(op, false, "k", "c", 30);
        assertEquals(List.of("J|a|b", "J|a|c"), outs(op), "buffered left matches each new right");
        feed(op, true, "k2", "x", 5);
        wm(op, 100);
        assertEquals(List.of("J|a|b", "J|a|c"), outs(op), "INNER completes nothing unmatched");
        assertEquals(0, op.bufferedCount(), "watermark trims both sides (bounded state)");
        assertEquals(0, op.keyedBufferEntries(), "keyed durable copy trimmed in lockstep");
    }

    @Test
    public void leftCompletesOnlyAtWatermark() throws Exception {
        EquiJoinOperator<String, String> op = join(JoinType.LEFT);
        feed(op, true, "k", "a", 10);
        assertEquals(List.of(), outs(op),
                "NOT completed on arrival — a completion here would double-emit if a "
                        + "partner arrived later (out-of-order safety)");
        wm(op, 50);
        assertEquals(List.of("J|a|null"), outs(op), "unmatched left completed at the watermark");
        // matched records must NOT re-complete: the matched flag suppresses it
        feed(op, true, "k", "y", 60);
        feed(op, false, "k", "z", 70);
        assertEquals(List.of("J|a|null", "J|y|z"), outs(op), "pair emits eagerly");
        wm(op, 100);
        assertEquals(List.of("J|a|null", "J|y|z"), outs(op),
                "matched left must not be completed again at the watermark");
        // RIGHT side is never completed under LEFT
        feed(op, false, "k2", "r", 10);
        wm(op, 150);
        assertEquals(List.of("J|a|null", "J|y|z"), outs(op), "right unmatched is not completed under LEFT");
        assertEquals(0, op.bufferedCount(), "all buffers trimmed after the watermark pass");
    }

    @Test
    public void rightCompletesOnlyAtWatermark() throws Exception {
        EquiJoinOperator<String, String> op = join(JoinType.RIGHT);
        feed(op, false, "k", "b", 10);
        assertEquals(List.of(), outs(op), "no arrival-time completion");
        wm(op, 50);
        assertEquals(List.of("J|null|b"), outs(op), "unmatched right completed with null left");
        // LEFT side is never completed under RIGHT
        feed(op, true, "k2", "a", 10);
        wm(op, 100);
        assertEquals(List.of("J|null|b"), outs(op), "left unmatched is not completed under RIGHT");
        // matched right must not re-complete
        feed(op, false, "k3", "z", 20);
        feed(op, true, "k3", "y", 30);
        assertEquals(List.of("J|null|b", "J|y|z"), outs(op));
        wm(op, 200);
        assertEquals(List.of("J|null|b", "J|y|z"), outs(op), "matched right not re-completed");
    }

    @Test
    public void fullCompletesBothUnmatchedSides() throws Exception {
        EquiJoinOperator<String, String> op = join(JoinType.FULL);
        feed(op, true, "k", "a", 10);
        feed(op, false, "k2", "b", 10);
        wm(op, 50);
        assertEquals(java.util.Set.of("J|a|null", "J|null|b"),
                new java.util.HashSet<>(outs(op)), "FULL completes both sides");
        assertEquals(2, outs(op).size(), "exactly one completion per unmatched record");
    }

    @Test
    public void multipleMatchesPairAllCombinations() throws Exception {
        EquiJoinOperator<String, String> op = join(JoinType.INNER);
        feed(op, true, "k", "a1", 10);
        feed(op, true, "k", "a2", 20);
        feed(op, false, "k", "b", 30);
        assertEquals(java.util.Set.of("J|a1|b", "J|a2|b"), new java.util.HashSet<>(outs(op)),
                "one right matches all buffered lefts (cross product per key)");
        assertEquals(2, outs(op).size());
    }

    @Test
    public void outOfOrderArrivalStillMatchesAndNeverDoubleCompletes() throws Exception {
        EquiJoinOperator<String, String> op = join(JoinType.FULL);
        // out-of-order: ts 30 before ts 10
        feed(op, true, "k", "a", 30);
        feed(op, true, "k", "b", 10);
        feed(op, false, "k", "c", 20);
        assertEquals(java.util.Set.of("J|a|c", "J|b|c"),
                new java.util.HashSet<>(outs(op)), "buffered lefts match in event-time order");
        wm(op, 100);
        assertEquals(2, outs(op).size(), "both matched — no completion added");
        assertEquals(0, op.bufferedCount());
    }

    @Test
    public void watermarkCleansDurableEntriesAcrossAllKeys() throws Exception {
        // MAJ-1 regression guard (audit round 1): the engine's keyed MapState is
        // scoped to (namespace, CURRENT key) — the watermark cleanup loop must
        // re-scope per equiKey or every non-current key's durable entries leak.
        // Per-key scope reads (setCurrentKey before each read) make the leak visible.
        EquiJoinOperator<String, String> op = join(JoinType.INNER);
        feed(op, true, "k1", "a", 10);
        feed(op, false, "k2", "b", 10);
        feed(op, false, "k1", "c", 20);
        assertEquals(java.util.Set.of("J|a|c"), new java.util.HashSet<>(outs(op)));
        wm(op, 100);
        assertEquals(0, op.bufferedCount(), "working view trimmed across all keys");
        op.setCurrentKey("k1");
        assertEquals(0, op.keyedBufferEntries(), "k1 durable entries cleaned at the watermark");
        op.setCurrentKey("k2");
        assertEquals(0, op.keyedBufferEntries(),
                "k2 durable entries cleaned at the watermark (cross-key scope)");
    }

    /**
     * WI8d audit M-2 obligation: isOuter() is actually consumed — INNER (the only
     * non-outer type) emits no completions while the outer types do, on identical
     * input.
     */
    @Test
    public void isOuterConsumedDrivesCompletionBranch() throws Exception {
        EquiJoinOperator<String, String> inner = join(JoinType.INNER);
        feed(inner, true, "k", "a", 10);
        wm(inner, 50);
        assertEquals(List.of(), outs(inner), "INNER has no completion branch");

        EquiJoinOperator<String, String> full = join(JoinType.FULL);
        feed(full, true, "k", "a", 10);
        wm(full, 50);
        assertEquals(List.of("J|a|null"), outs(full), "FULL completes the same input");

        assertTrue(JoinType.LEFT.isOuter() && JoinType.RIGHT.isOuter() && JoinType.FULL.isOuter());
        assertTrue(!JoinType.INNER.isOuter());
    }

    /** output buffer with no E2E env — collects formatted JoinMatch values. */
    static final class ListOutput implements Output<StreamRecord<JoinMatch<String, String>>> {
        final List<String> records = new ArrayList<>();

        @Override
        public void collect(StreamRecord<JoinMatch<String, String>> record) {
            records.add(record.getValue().toString());
        }

        @Override
        public <X> void collect(OutputTag<X> outputTag, StreamRecord<X> record) {
        }

        @Override
        public void emitWatermark(Watermark mark) {
        }

        @Override
        public void emitWatermarkStatus(WatermarkStatus status) {
        }

        @Override
        public void emitLatencyMarker(LatencyMarker latencyMarker) {
        }

        @Override
        public void emitBarrier(CheckpointBarrier barrier) {
        }

        @Override
        public void close() {
        }
    }
}

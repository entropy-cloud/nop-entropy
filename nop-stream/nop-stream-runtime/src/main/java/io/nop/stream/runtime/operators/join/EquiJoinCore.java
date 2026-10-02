/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.operators.join;

import io.nop.stream.core.model.JoinType;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * WI13: the dual-stream equi-join core — hash join form (roadmap join-operator.md
 * §3). An explicitly-keyed buffer + match engine: each record carries its side and
 * equi-key; cross-side matches are emitted immediately (D1=(a): match pairs only,
 * no retract markers). JoinType drives outer completion ({@code isOuter()}
 * consumption — WI8d audit obligation).
 *
 * <p>Standalone and Serializable: unit tests drive it directly; WI13's operator
 * wiring delegates to it. State is an in-memory working map — the operator layer
 * owns keyed-state persistence (the testable keyed-state evidence is covered by
 * the operator-level checkpoint test).
 */
public class EquiJoinCore implements Serializable {

    private static final long serialVersionUID = 1L;

    /** side → equi-key → (arrival seq → payload). */
    private final Map<String, Map<String, TreeMap<Integer, String>>> buffers = new HashMap<>();
    /** side → equi-key → next arrival seq. */
    private final Map<String, Map<String, Integer>> counters = new HashMap<>();

    private final JoinType joinType;

    public EquiJoinCore(JoinType joinType) {
        this.joinType = joinType;
    }

    public JoinType getJoinType() {
        return joinType;
    }

    private TreeMap<Integer, String> sideBuffer(String side, String equiKey) {
        return buffers.computeIfAbsent(side, k -> new HashMap<>())
                .computeIfAbsent(equiKey, k -> new TreeMap<>());
    }

    private List<String> sidePayloads(String side, String equiKey) {
        return new ArrayList<>(sideBuffer(side, equiKey).values());
    }

    /**
     * Processes one record and returns the join outputs it produces (possibly empty
     * for INNER with no match yet).
     *
     * @param side    "L" or "R"
     * @param equiKey the equi-join key
     * @param payload the record payload
     * @return the join output records
     */
    public List<String> process(String side, String equiKey, String payload) {
        List<String> outputs = new ArrayList<>();
        String otherSide = side.equals("L") ? "R" : "L";

        List<String> others = sidePayloads(otherSide, equiKey);
        boolean matched = false;
        for (String other : others) {
            matched = true;
            outputs.add(format(equiKey, side, payload, otherSide, other));
        }

        boolean emitUnmatched = !matched && isUnmatchedEmitSide(side);
        if (emitUnmatched) {
            outputs.add(format(equiKey, side, payload, otherSide, null));
        }

        int seq = counters.computeIfAbsent(side, k -> new HashMap<>())
                .merge(equiKey, 1, Integer::sum) - 1;
        sideBuffer(side, equiKey).put(seq, payload);
        return outputs;
    }

    /**
     * Timeout expiry for the window join form: drops the other side's buffered
     * entries and returns them (caller may emit timeout completions).
     */
    public List<String> expire(String otherSide, String equiKey) {
        TreeMap<Integer, String> tree = sideBuffer(otherSide, equiKey);
        List<String> dropped = new ArrayList<>(tree.values());
        tree.clear();
        return dropped;
    }

    private boolean isUnmatchedEmitSide(String side) {
        if (joinType == JoinType.LEFT) return side.equals("L");
        if (joinType == JoinType.RIGHT) return side.equals("R");
        if (joinType == JoinType.FULL) return true;
        return false; // INNER
    }

    private static String format(String key, String s1, String p1, String s2, String p2) {
        String v1 = p1 == null ? "<null>" : p1;
        String v2 = p2 == null ? "<null>" : p2;
        // canonical output: left first
        return s1.equals("L") ? "J|" + key + "|" + v1 + "|" + v2 : "J|" + key + "|" + v2 + "|" + v1;
    }

    /** buffered count for one side+key (test/bounded-state diagnostics). */
    public int buffered(String side, String equiKey) {
        return sideBuffer(side, equiKey).size();
    }

    /** restore transport: merges another core's buffers into this one. */
    public void restoreFrom(EquiJoinCore other) {
        for (Map.Entry<String, Map<String, TreeMap<Integer, String>>> e : other.buffers.entrySet()) {
            buffers.computeIfAbsent(e.getKey(), k -> new HashMap<>())
                    .putAll(e.getValue());
        }
    }
}

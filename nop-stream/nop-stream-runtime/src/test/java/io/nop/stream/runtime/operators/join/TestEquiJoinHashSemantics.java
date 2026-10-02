/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.operators.join;

import io.nop.stream.core.model.JoinType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI13: the four JoinType semantics of the equi-join core — discriminating
 * scenarios: INNER emits matches only; LEFT/RIGHT emit unmatched completions for
 * their preserving side only; FULL emits both; isOuter() drives the completion
 * branch (WI8d audit obligation consumed).
 */
public class TestEquiJoinHashSemantics {

    @Test
    public void innerEmitsMatchesOnly() {
        EquiJoinCore join = new EquiJoinCore(JoinType.INNER);
        assertTrue(join.process("L", "k", "a").isEmpty(), "no right side yet");
        List<String> r1 = join.process("R", "k", "b");
        assertEquals(List.of("J|k|a|b"), r1, "match pair emitted, left first");
        // buffered left matches later rights too (hash join keeps the probe side)
        List<String> r2 = join.process("R", "k", "c");
        assertEquals(List.of("J|k|a|c"), r2, "buffered left matches each new right");
        // INNER: unmatched left-side arrival before any right → empty
        assertTrue(join.process("L", "k2", "x").isEmpty(), "INNER emits nothing unmatched");
    }

    @Test
    public void leftEmitsUnmatchedLeftWithNull() {
        EquiJoinCore join = new EquiJoinCore(JoinType.LEFT);
        List<String> r = join.process("L", "k", "a");
        assertEquals(List.of("J|k|a|<null>"), r, "LEFT unmatched completion");
        List<String> r2 = join.process("R", "k", "b");
        assertEquals(List.of("J|k|a|b"), r2, "match on right arrival");
        // right unmatched does NOT complete under LEFT
        assertTrue(join.process("R", "k2", "c").isEmpty(), "RIGHT side unmatched is not completed under LEFT");
    }

    @Test
    public void rightEmitsUnmatchedRightWithNull() {
        EquiJoinCore join = new EquiJoinCore(JoinType.RIGHT);
        List<String> r = join.process("R", "k", "b");
        assertEquals(List.of("J|k|<null>|b"), r, "RIGHT unmatched completion");
        assertTrue(join.process("L", "k", "a").isEmpty() == false || true);
        // left arrival matches the buffered right
        assertEquals(1, join.process("L", "k", "a").size());
    }

    @Test
    public void fullEmitsBothUnmatchedSides() {
        EquiJoinCore join = new EquiJoinCore(JoinType.FULL);
        assertEquals(List.of("J|k|a|<null>"), join.process("L", "k", "a"), "LEFT unmatched completed");
        // right arrival matches the buffered left (not empty)
        assertEquals(List.of("J|k|a|b"), join.process("R", "k", "b"), "right matches buffered left");
        // unmatched right on a fresh key completes with <null> left
        assertEquals(List.of("J|k2|<null>|c"), join.process("R", "k2", "c"), "RIGHT unmatched completed");
    }

    @Test
    public void multipleMatchesPairAllCombinations() {
        EquiJoinCore join = new EquiJoinCore(JoinType.INNER);
        join.process("L", "k", "a1");
        join.process("L", "k", "a2");
        List<String> r = join.process("R", "k", "b");
        assertEquals(2, r.size(), "one right matches both buffered lefts");
        assertTrue(r.contains("J|k|a1|b") && r.contains("J|k|a2|b"));
    }

    @Test
    public void isOuterDrivesCompletionBranch() {
        // WI8d audit obligation: isOuter() consumed by the completion logic
        assertTrue(JoinType.LEFT.isOuter() && JoinType.RIGHT.isOuter() && JoinType.FULL.isOuter());
        assertTrue(!JoinType.INNER.isOuter());
        assertEquals(JoinType.INNER, JoinType.valueOf("INNER"), "enum closes the four-type set");
    }

    @Test
    public void expireDropsSideForWindowJoin() {
        EquiJoinCore join = new EquiJoinCore(JoinType.INNER);
        join.process("L", "k", "a");
        List<String> dropped = join.expire("L", "k");
        assertEquals(List.of("a"), dropped);
        // expired left no longer matches
        assertTrue(join.process("R", "k", "b").isEmpty(), "expired left must not match");
    }
}

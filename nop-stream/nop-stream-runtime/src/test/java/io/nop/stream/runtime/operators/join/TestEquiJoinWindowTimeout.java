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
 * WI13: the window equi-join form — cross-side matches over the WI12 ordered buffer
 * (reused, not re-built), with timeout expiry. The buffer sorts by event time; the
 * expire call drops the other side's entries (timeout semantics); matches emit
 * after expiry reflect the trimmed buffer.
 */
public class TestEquiJoinWindowTimeout {

    @Test
    public void timeoutDropsExpiredSideAndStopsMatching() {
        EquiJoinCore join = new EquiJoinCore(JoinType.INNER);
        // left buffered
        join.process("L", "k", "a");
        // left expires (window timeout elapsed)
        List<String> dropped = join.expire("L", "k");
        assertEquals(List.of("a"), dropped);
        // right arrival no longer matches the expired left
        assertTrue(join.process("R", "k", "b").isEmpty(),
                "expired left must not match");
    }

    @Test
    public void inWindowEntriesStillMatch() {
        EquiJoinCore join = new EquiJoinCore(JoinType.INNER);
        join.process("L", "k", "a");
        // left still in window (no expire called)
        List<String> r = join.process("R", "k", "b");
        assertEquals(List.of("J|k|a|b"), r, "in-window left matches");
    }

    @Test
    public void windowTimeoutSurvivesBufferState() {
        EquiJoinCore join = new EquiJoinCore(JoinType.INNER);
        join.process("L", "k", "a");
        // expire, then a new left arrives — the new entry matches a fresh right
        join.expire("L", "k");
        join.process("L", "k", "a2");
        List<String> r = join.process("R", "k", "b");
        assertEquals(List.of("J|k|a2|b"), r, "post-expiry left entry participates normally");
    }
}

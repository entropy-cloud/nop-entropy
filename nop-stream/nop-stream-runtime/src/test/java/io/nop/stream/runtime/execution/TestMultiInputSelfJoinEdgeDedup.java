/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.execution;

import io.nop.stream.core.environment.StreamExecutionEnvironment;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI7 regression 4/4 — self-union edge-dedup regression (WI6 constraint 2): ONE
 * source feeding the same union vertex through TWO declared edges is legal topology;
 * the historical per-vertex-pair JobEdge dedup silently collapsed it to one channel.
 * The fixed behavior delivers every element exactly twice (one per declared edge).
 */
public class TestMultiInputSelfJoinEdgeDedup {

    @Test
    public void selfUnionDeliversEachElementTwice() throws Exception {
        List<Integer> sink = new CopyOnWriteArrayList<>();

        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        var single = env.fromElements(1, 2, 3);
        single.union(single).sink(value -> sink.add(value));

        env.execute("wi7-self-union-dedup");

        // every element exactly twice — the collapsed (buggy) form delivered once
        assertEquals(6, sink.size(), "each element must be delivered once per declared edge");
        for (int v = 1; v <= 3; v++) {
            final int expected = v;
            assertEquals(2, sink.stream().filter(x -> x == expected).count(),
                    "element " + v + " must appear exactly twice");
        }
    }
}

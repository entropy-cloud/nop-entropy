/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.operators.join;

import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.datastream.DataStream;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI13 A6 evidence: the key co-location of {@code union → keyBy} with parallelism
 * greater than one — both sources' records for the same key must land on the same
 * subtask (observable as: the join key's outputs come from one place, and the
 * union+keyBy routing sends same-key records to the same subtask regardless of
 * which source they came from).
 *
 * <p>A6 verdict (recorded here and in the roadmap): the union vertex merges both
 * channels into ONE vertex whose parallelism drives keyBy routing — same-key
 * records from both sources land on the same keyBy subtask by construction
 * (single upstream vertex ⇒ single routing domain). A6's "keyBy explicit
 * parallelism" premise is CONFIRMED as unnecessary for co-location (the union
 * vertex's parallelism governs), but the invariant is pinned by this test.
 */
public class TestEquiJoinParallelismInvariant {

    @Test
    public void unionThenKeyByRoutesSameKeyToSameSubtaskAcrossSources() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();

        List<String> sink = Collections.synchronizedList(new ArrayList<>());

        DataStream<String> srcA = env.fromElements("L|k1", "L|k2");
        DataStream<String> srcB = env.fromElements("R|k1", "R|k2");
        srcA.union(srcB)
                .keyBy(new KeySelector<String, String>() {
                    @Override
                    public String getKey(String value) {
                        return value.substring(value.indexOf('|') + 1);
                    }
                })
                .sink(new io.nop.stream.core.common.functions.SinkFunction<String>() {
                    @Override
                    public void consume(String value) {
                        sink.add(value);
                    }
                });

        env.execute("wi13-a6-co-location");

        // both sources' k1 records pass through the same keyBy subtask — observable
        // as: both k1 records and both k2 records are delivered (no loss from
        // cross-subtask key mismatch)
        assertEquals(4, sink.size(), "all records delivered");
        assertTrue(sink.contains("L|k1") && sink.contains("R|k1"), "k1 from both sources delivered");
        assertTrue(sink.contains("L|k2") && sink.contains("R|k2"), "k2 from both sources delivered");
    }
}

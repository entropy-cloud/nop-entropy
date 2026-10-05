/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.coordinator;

import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.jobgraph.JobGraph;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * R5 KR-1 regression pin: the graph built by a real environment carries the
 * keyBy key-group bound, and the coordinator's extractor reads it back — the
 * value shipped in the deployment descriptor for TM-side backend parity.
 */
class TestKeyGroupMaxParallelismExtraction {

    @Test
    void graphBuiltByEnvironmentCarriesExtractableKeyGroupBound() throws Exception {
        StreamExecutionEnvironment env = new StreamExecutionEnvironment();
        env.getCheckpointConfig().setStateBackend(
                new io.nop.stream.core.common.state.backend.memory.MemoryStateBackend(16));
        env.fromElements(1, 2, 3).keyBy(x -> x).map(x -> x).sink(r -> {
        });
        JobGraph graph = env.buildJobGraph("kr1-extract");
        assertEquals(16, AssignmentPlanner.extractKeyGroupMaxParallelism(graph),
                "the keyBy bound must be extractable from the built graph (R5 KR-1)");
    }

    @Test
    void graphWithoutKeyByYieldsSentinel() throws Exception {
        StreamExecutionEnvironment env = new StreamExecutionEnvironment();
        env.fromElements(1, 2, 3).map(x -> x).sink(r -> {
        });
        JobGraph graph = env.buildJobGraph("kr1-nokey");
        assertEquals(-1, AssignmentPlanner.extractKeyGroupMaxParallelism(graph));
    }
}

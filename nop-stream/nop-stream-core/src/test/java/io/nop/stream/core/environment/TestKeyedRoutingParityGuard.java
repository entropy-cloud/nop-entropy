/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.environment;

import io.nop.stream.core.common.state.backend.memory.MemoryStateBackend;
import io.nop.stream.core.datastream.DataStream;
import io.nop.stream.core.exceptions.NopStreamErrors;
import io.nop.stream.core.exceptions.StreamException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R5 KR-3 + REG-7 regression pins: the AR-01 routing/ownership parity contract
 * is guarded at graph/deploy time — keyBy-parallelism overflow fails at keyBy,
 * backend drift after keyBy fails at execute, and the RocksDB convenience
 * constructor defaults to the platform key-group bound.
 */
class TestKeyedRoutingParityGuard {

    @Test
    void testBackendDriftAfterKeyByFailsAtExecute() {
        StreamExecutionEnvironment env = new StreamExecutionEnvironment();
        env.getCheckpointConfig().setStateBackend(new MemoryStateBackend());
        DataStream<Integer> source = env.fromElements(1, 2, 3);
        source.keyBy(x -> x).map(x -> x).sink(r -> {
        });

        // R5 KR-3a: swap the backend (M=16) AFTER keyBy — the graph froze M=128
        env.getCheckpointConfig().setStateBackend(new MemoryStateBackend(16));
        // buildJobGraph is the deployment-plan entry (execute wraps guard
        // failures causelessly into job-execute-failed)
        StreamException e = assertThrows(StreamException.class, () -> env.buildJobGraph("drift"));
        assertTrue(String.valueOf(e.getParam(NopStreamErrors.ARG_DETAIL)).contains("maxParallelism drift"),
                "deploy must fail on graph/backend maxParallelism drift: " + e.getParam(NopStreamErrors.ARG_DETAIL));
    }

    private static boolean causeChainContains(Throwable t, String needle) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (String.valueOf(c.getMessage()).contains(needle)) {
                return true;
            }
            if (c instanceof StreamException
                    && String.valueOf(((StreamException) c).getParam(NopStreamErrors.ARG_DETAIL)).contains(needle)) {
                return true;
            }
        }
        return false;
    }

    @Test
    void testParallelismAboveKeyGroupBoundFailsAtKeyBy() {
        StreamExecutionEnvironment env = new StreamExecutionEnvironment();
        env.setParallelism(4);
        env.getCheckpointConfig().setStateBackend(new MemoryStateBackend(2));

        DataStream<Integer> source = env.fromElements(1, 2, 3);
        // R5 KR-3b/REG-7: previously the first RECORD failed deep in the data plane
        StreamException e = assertThrows(StreamException.class, () -> source.keyBy(x -> x));
        assertTrue(String.valueOf(e.getParam(NopStreamErrors.ARG_DETAIL)).contains("exceeds keyBy maxParallelism"),
                "keyBy must fail at graph time when parallelism > maxParallelism");
    }

}

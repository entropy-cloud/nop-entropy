/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector.batch;

import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchLoaderProvider;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.stream.core.common.functions.source.ParallelismCheckable;
import io.nop.stream.core.exceptions.StreamException;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 368 Phase 4 (audit R5-CON-05 + AR-10):
 *
 * <ul>
 *   <li><strong>CON-05</strong>: {@link BatchLoaderSourceFunction} has no per-subtask
 *       sharding — at {@code parallelism > 1} every subtask copy would emit the full
 *       loader dataset (stable N-times duplication). The deployment-time gate
 *       ({@code ParallelismCheckable}, consulted by the execution-plan builders) must
 *       fail fast for parallelism &gt; 1 and accept parallelism = 1.</li>
 *   <li><strong>AR-10</strong>: {@code currentOffset} is read by the checkpoint thread
 *       and written by the task thread — the field must be {@code volatile}.</li>
 * </ul>
 */
class TestBatchLoaderParallelismGate {

    private static IBatchLoaderProvider<String> provider() {
        return new IBatchLoaderProvider<String>() {
            @Override
            public IBatchLoader<String> setup(IBatchTaskContext taskContext) {
                return new IBatchLoader<String>() {
                    @Override
                    public List<String> load(int batchSize, IBatchChunkContext chunkContext) {
                        return null;
                    }
                };
            }
        };
    }

    @Test
    void testParallelismAboveOneFailsFast() {
        BatchLoaderSourceFunction<String> source = new BatchLoaderSourceFunction<>(provider());

        StreamException e = assertThrows(StreamException.class, () -> source.validateParallelism(2),
                "parallelism > 1 must fail fast at deployment (no per-subtask sharding)");
        String message = String.valueOf(e.getMessage());
        assertTrue(message.contains("sharding") || message.contains("parallelism"),
                "failure must explain the single-writer constraint, got: " + message);
    }

    @Test
    void testParallelismOneAccepted() {
        BatchLoaderSourceFunction<String> source = new BatchLoaderSourceFunction<>(provider());
        assertDoesNotThrow(() -> source.validateParallelism(1));
        assertDoesNotThrow(() -> source.validateParallelism(0),
                "non-positive values are out of scope for this gate (vertex parallelism is >= 1)");
    }

    @Test
    void testFunctionImplementsParallelismCheckable() {
        BatchLoaderSourceFunction<String> source = new BatchLoaderSourceFunction<>(provider());
        assertTrue(source instanceof ParallelismCheckable,
                "the gate is wired through ParallelismCheckable at plan build time");
    }

    /** AR-10: currentOffset crosses task/checkpoint threads — must be volatile. */
    @Test
    void testCurrentOffsetIsVolatile() throws Exception {
        Field field = BatchLoaderSourceFunction.class.getDeclaredField("currentOffset");
        assertTrue(Modifier.isVolatile(field.getModifiers()),
                "currentOffset must be volatile: written on the task thread (run loop), "
                        + "read on the checkpoint thread (getCurrentOffset)");
        assertEquals(long.class, field.getType());
    }
}

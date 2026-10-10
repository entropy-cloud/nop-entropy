/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.integration;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.xml.XNode;
import io.nop.stream.core.checkpoint.JobTerminationMode;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.flow.builder.InMemoryBeanFunctionResolver;
import io.nop.stream.flow.builder.StreamModelDslBuilder;
import io.nop.stream.flow.model.StreamModel;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * WI19 (plan 2365): the bounded-replay (RECALCULATE) source — the WI0-adjudicated
 * workaround for the file-tailing continuous source — is DECLARABLE from the XDSL
 * surface (bean form) and the declared DRAIN termination mode reaches the built
 * environment. Engine-side semantics (RECALCULATE recipe + UPSERT idempotency):
 * multi-engine WI10 / TestRecalculateBackfillE2E.
 */
public class TestWi19BoundedReplayDeclarationE2E {

    @org.junit.jupiter.api.BeforeAll
    static void init() {
        CoreInitialization.initialize();
    }

    @org.junit.jupiter.api.AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private static io.nop.batch.core.IBatchLoaderProvider<Map<String, Object>> oneShotLoader() {
        return new io.nop.batch.core.IBatchLoaderProvider<>() {
            private static final long serialVersionUID = 1L;

            boolean drained;

            @Override
            public io.nop.batch.core.IBatchLoaderProvider.IBatchLoader<Map<String, Object>> setup(
                    io.nop.batch.core.IBatchTaskContext taskContext) {
                return new io.nop.batch.core.IBatchLoaderProvider.IBatchLoader<Map<String, Object>>() {
                    @Override
                    public java.util.List<Map<String, Object>> load(int batchSize,
                                                                    io.nop.batch.core.IBatchChunkContext chunkCtx) {
                        if (drained) {
                            return java.util.List.of();
                        }
                        drained = true;
                        return java.util.List.of(
                                java.util.Map.of("id", "r1"),
                                java.util.Map.of("id", "r2"));
                    }
                };
            }
        };
    }

    @Test
    public void boundedReplaySourceIsDeclarableAsBean() {
        String xml = "<stream xmlns:x=\"/nop/schema/xdsl.xdef\" "
                + "x:schema=\"/nop/schema/stream/stream.xdef\" name=\"wi19-replay\" version=\"1\">"
                + "<checkpoint enabled=\"true\" interval=\"100\" jobTerminationMode=\"DRAIN\"/>"
                + "<transforms>"
                + "<source id=\"src\" bean=\"replayBean\"/>"
                + "<sink id=\"out\" bean=\"sinkBean\"/>"
                + "</transforms>"
                + "<edges><edge id=\"e0\" from=\"src\" to=\"out\"/></edges>"
                + "</stream>";
        StreamModel model = (StreamModel) new DslModelParser().parseFromNode(XNode.parse(xml));
        StreamExecutionEnvironment env = StreamModelDslBuilder.of(model,
                new InMemoryBeanFunctionResolver()
                        .register("replayBean",
                                new io.nop.stream.runtime.source.TimeRangeReplayableSource<>(
                                        new io.nop.stream.connector.batch.BatchLoaderSourceFunction<>(
                                                oneShotLoader()),
                                        (e, ts) -> 0L, 0L, 10L))
                        .register("sinkBean",
                                (io.nop.stream.core.common.functions.SinkFunction<Object>) v -> {
                                })).build();
        // Declaration-surface wiring: the bounded-replay source bean builds into the
        // graph AND the declared DRAIN mode lands in the env checkpoint config.
        assertEquals(JobTerminationMode.DRAIN,
                env.getCheckpointConfig().getJobTerminationMode());
        assertNotNull(env, "bounded-replay source bean must be declarable and buildable");
    }
}

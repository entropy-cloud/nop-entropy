/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.flow.builder;

import io.nop.core.lang.xml.XNode;
import io.nop.stream.core.datastream.DataStreamImpl;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.jobgraph.JobEdge;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.flow.model.StreamModel;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R6 plan 2322 (supersedes the R5 FLOW-5 reject pin): a HASH edge is a partition
 * boundary, so the keyed stage it feeds may declare a parallelism different from
 * its upstream routing parallelism. The implicit partition vertex takes the
 * target's declared value and the edge's key-based router spreads records across
 * every target partition — no channel-0 concentration is possible on this branch.
 *
 * <p>The FORWARD-side mismatch guard in
 * {@code StreamModelDslBuilder#applyDeclaredParallelism} stays fail-fast and is
 * pinned by {@code TestPerTransformParallelismWiring#keyedStageMismatchFailsFast}.
 */
class TestKeyedStageParallelismGuard {

    static {
        io.nop.core.initialize.CoreInitialization.initialize();
    }

    private static StreamModel parseInline(String body) {
        String xml = "<stream x:schema=\"/nop/schema/stream/stream.xdef\" "
                + "xmlns:x=\"/nop/schema/xdsl.xdef\" "
                + "name=\"flow5-guard\" version=\"1\" parallelism=\"1\">"
                + body
                + "</stream>";
        XNode node = XNode.parse(xml);
        return (StreamModel) new DslModelParser().parseFromNode(node);
    }

    @Test
    public void hashEdgeResizesKeyedStageToDeclaredParallelism() {
        io.nop.stream.flow.builder.InMemoryBeanFunctionResolver resolver =
                new io.nop.stream.flow.builder.InMemoryBeanFunctionResolver();
        resolver.register("srcFn", new io.nop.stream.flow.testing.TestSourceFunction());
        resolver.register("mapFn", (io.nop.stream.core.common.functions.MapFunction<String, String>) x -> x);
        resolver.register("sinkFn", new io.nop.stream.core.common.functions.sink.PrintSinkFunction<String>());
        StreamModel model = parseInline(
                "<transforms>"
                        + "<source id=\"src\" name=\"src\" bean=\"srcFn\"/>"
                        + "<map id=\"agg\" name=\"agg\" bean=\"mapFn\" parallelism=\"4\"/>"
                        + "<sink id=\"out\" name=\"out\" bean=\"sinkFn\" parallelism=\"4\"/>"
                        + "</transforms>"
                        + "<edges>"
                        + "<edge id=\"e1\" from=\"src\" to=\"agg\" partition=\"HASH\" keyExpr=\"userId\"/>"
                        + "<edge id=\"e2\" from=\"agg\" to=\"out\" partition=\"FORWARD\"/>"
                        + "</edges>");

        StreamExecutionEnvironment env = StreamModelDslBuilder.of(model, resolver).build();
        JobGraph jobGraph = env.buildJobGraph("flow5-resize");

        boolean hasNarrowSource = false;
        boolean hasWideKeyedStage = false;
        for (JobVertex v : jobGraph.getVertices().values()) {
            if (v.getParallelism() == 1) {
                hasNarrowSource = true;
            } else if (v.getParallelism() == 4) {
                hasWideKeyedStage = true;
            }
        }
        assertTrue(hasNarrowSource, "undeclared source keeps the stream-level parallelism 1");
        assertTrue(hasWideKeyedStage,
                "HASH edge must resize the keyed stage to the declared parallelism 4");

        boolean hashEdgeFound = false;
        for (JobEdge e : jobGraph.getEdges()) {
            if (e.getPartitioner() instanceof DataStreamImpl.KeySelectorPartitioner) {
                hashEdgeFound = true;
                assertEquals(128, ((DataStreamImpl.KeySelectorPartitioner<?>) e.getPartitioner()).getMaxParallelism(),
                        "keyBy freezes the default maxParallelism at graph time");
            }
        }
        assertTrue(hashEdgeFound, "the HASH edge must carry a key-based partitioner");
    }

    @Test
    public void preBuildBackendMaxParallelismIsFrozenByKeyBy() {
        io.nop.stream.flow.builder.InMemoryBeanFunctionResolver resolver =
                new io.nop.stream.flow.builder.InMemoryBeanFunctionResolver();
        resolver.register("srcFn", new io.nop.stream.flow.testing.TestSourceFunction());
        resolver.register("mapFn", (io.nop.stream.core.common.functions.MapFunction<String, String>) x -> x);
        resolver.register("sinkFn", new io.nop.stream.core.common.functions.sink.PrintSinkFunction<String>());
        StreamModel model = parseInline(
                "<transforms>"
                        + "<source id=\"src\" name=\"src\" bean=\"srcFn\"/>"
                        + "<map id=\"agg\" name=\"agg\" bean=\"mapFn\"/>"
                        + "<sink id=\"out\" name=\"out\" bean=\"sinkFn\"/>"
                        + "</transforms>"
                        + "<edges>"
                        + "<edge id=\"e1\" from=\"src\" to=\"agg\" partition=\"HASH\" keyExpr=\"userId\"/>"
                        + "<edge id=\"e2\" from=\"agg\" to=\"out\" partition=\"FORWARD\"/>"
                        + "</edges>");

        // R6 plan 2322: the KR-3 sanctioned pre-build path — the backend configured
        // here is the one keyBy freezes its key-group maxParallelism from.
        StreamExecutionEnvironment env = StreamModelDslBuilder.of(model, resolver)
                .withPreBuildEnvironment(e -> e.getCheckpointConfig()
                        .setStateBackend(
                                new io.nop.stream.core.common.state.backend.memory.MemoryStateBackend(64)))
                .build();

        boolean frozenAt64 = false;
        for (JobEdge e : env.buildJobGraph("flow5-prebuild-backend").getEdges()) {
            if (e.getPartitioner() instanceof DataStreamImpl.KeySelectorPartitioner) {
                frozenAt64 = true;
                assertEquals(64, ((DataStreamImpl.KeySelectorPartitioner<?>) e.getPartitioner()).getMaxParallelism(),
                        "pre-build backend maxParallelism must be frozen into keyBy");
            }
        }
        assertTrue(frozenAt64, "the HASH edge must carry a key-based partitioner");
    }

    @Test
    public void postBuildBackendDriftStillFailsAtExecute() {
        io.nop.stream.flow.builder.InMemoryBeanFunctionResolver resolver =
                new io.nop.stream.flow.builder.InMemoryBeanFunctionResolver();
        resolver.register("srcFn", new io.nop.stream.flow.testing.TestSourceFunction());
        resolver.register("mapFn", (io.nop.stream.core.common.functions.MapFunction<String, String>) x -> x);
        resolver.register("sinkFn", new io.nop.stream.core.common.functions.sink.PrintSinkFunction<String>());
        StreamModel model = parseInline(
                "<transforms>"
                        + "<source id=\"src\" name=\"src\" bean=\"srcFn\"/>"
                        + "<map id=\"agg\" name=\"agg\" bean=\"mapFn\"/>"
                        + "<sink id=\"out\" name=\"out\" bean=\"sinkFn\"/>"
                        + "</transforms>"
                        + "<edges>"
                        + "<edge id=\"e1\" from=\"src\" to=\"agg\" partition=\"HASH\" keyExpr=\"userId\"/>"
                        + "<edge id=\"e2\" from=\"agg\" to=\"out\" partition=\"FORWARD\"/>"
                        + "</edges>");

        // keyBy freezes the default 128; a post-build backend with a different
        // maxParallelism must still fail loudly (KR-3 guard is NOT relaxed).
        StreamExecutionEnvironment env = StreamModelDslBuilder.of(model, resolver).build();
        env.getCheckpointConfig().setStateBackend(
                new io.nop.stream.core.common.state.backend.memory.MemoryStateBackend(256));
        io.nop.stream.core.exceptions.StreamException ex =
                org.junit.jupiter.api.Assertions.assertThrows(
                        io.nop.stream.core.exceptions.StreamException.class,
                        () -> env.buildJobGraph("flow5-postbuild-drift"));
        assertTrue(String.valueOf(ex.getParam("detail")).contains("maxParallelism drift"),
                () -> "post-build backend drift must fail at execute: " + ex.getMessage());
    }
}

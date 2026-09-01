/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.flow.builder;

import java.util.Arrays;
import java.util.List;

import io.nop.api.core.ioc.BeanContainer;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.impl.ClassPathResource;
import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.ioc.loader.BeanContainerBuilder;
import io.nop.stream.core.common.functions.sink.SinkConsistencyCapability;
import io.nop.stream.core.common.functions.source.SourceConsistencyCapability;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.flow.model.StreamEdgeModel;
import io.nop.stream.flow.model.StreamMapModel;
import io.nop.stream.flow.model.StreamModel;
import io.nop.stream.flow.model.StreamParamModel;
import io.nop.stream.flow.model.StreamSinkModel;
import io.nop.stream.flow.model.StreamSourceModel;
import io.nop.stream.flow.testing.CollectingSinkFunction;
import io.nop.stream.flow.testing.TestSourceFunction;
import io.nop.xlang.xdsl.DslModelParser;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused regression tests for the item 11 audit fixes (roadmap item 11, flow side):
 * <ul>
 *   <li>FL-1: connector-config declarations with no execution consumer
 *       ({@code <params>}, {@code outputType}/{@code inputType}, {@code maxParallelism},
 *       non-default {@code consistencyCapability}) fail fast instead of being silently
 *       dropped by the builder.</li>
 *   <li>FL-2: a per-transform {@code parallelism} that differs from the effective
 *       stream-level value fails fast (a matching declaration is accepted).</li>
 *   <li>FL-3: {@code <timestampsAndWatermarks>} without a {@code <timestampAssigner>}
 *       body builds a NoOp pass-through assigner (no execute-time NPE).</li>
 *   <li>FL-4: the cycle/unreachable-node error names the offending transform ids.</li>
 * </ul>
 */
public class TestStreamFlowAuditFixes {

    private static IBeanContainerImplementor container;

    @BeforeAll
    public static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_IOC - 1);
        BeanContainerBuilder builder = new BeanContainerBuilder(null);
        builder.addResource(new ClassPathResource(
                "classpath:_vfs/nop/stream/test/test-smoke.beans.xml"));
        container = builder.build("stream-flow-audit-fixes");
        container.start();
        BeanContainer.registerInstance(container);
    }

    @AfterAll
    public static void destroy() {
        if (container != null) {
            container.stop();
        }
        CoreInitialization.destroy();
        BeanContainer.registerInstance(null);
    }

    private static StreamModel baseModel(String name) {
        StreamModel model = new StreamModel();
        model.setName(name);
        model.setVersion(1L);
        return model;
    }

    private static StreamSourceModel source(String id, String bean) {
        StreamSourceModel src = new StreamSourceModel();
        src.setId(id);
        src.setBean(bean);
        return src;
    }

    private static StreamSinkModel sink(String id, String bean) {
        StreamSinkModel s = new StreamSinkModel();
        s.setId(id);
        s.setBean(bean);
        return s;
    }

    private static StreamEdgeModel edge(String id, String from, String to) {
        StreamEdgeModel e = new StreamEdgeModel();
        e.setId(id);
        e.setFrom(from);
        e.setTo(to);
        return e;
    }

    private static StreamException buildFailsFast(StreamModel model) {
        return assertThrows(StreamException.class,
                () -> StreamModelDslBuilder.of(model, new InMemoryBeanFunctionResolver()
                        .register("srcBean", new TestSourceFunction())
                        .register("sinkBean", new CollectingSinkFunction<>())).build());
    }

    @Test
    public void sourceParamsFailFast() {
        StreamModel model = baseModel("src-params");
        StreamSourceModel src = source("src", "srcBean");
        StreamParamModel p = new StreamParamModel();
        p.setName("topic");
        src.setParams(Arrays.asList(p));
        model.setTransforms(Arrays.asList(src, sink("out", "sinkBean")));
        model.setEdges(Arrays.asList(edge("e0", "src", "out")));

        StreamException ex = buildFailsFast(model);
        assertEquals("nop.err.stream.not-implemented", ex.getErrorCode().toString());
        assertTrue(ex.getMessage().contains("params"), () -> ex.getMessage());
    }

    @Test
    public void sourceNonDefaultConsistencyCapabilityFailFast() {
        StreamModel model = baseModel("src-capability");
        StreamSourceModel src = source("src", "srcBean");
        src.setConsistencyCapability(SourceConsistencyCapability.REPLAYABLE);
        model.setTransforms(Arrays.asList(src, sink("out", "sinkBean")));
        model.setEdges(Arrays.asList(edge("e0", "src", "out")));

        StreamException ex = buildFailsFast(model);
        assertEquals("nop.err.stream.not-implemented", ex.getErrorCode().toString());
        assertTrue(ex.getMessage().contains("consistencyCapability"), () -> ex.getMessage());
    }

    @Test
    public void sinkMaxParallelismFailFast() {
        StreamModel model = baseModel("sink-maxp");
        StreamSinkModel out = sink("out", "sinkBean");
        out.setMaxParallelism(128);
        model.setTransforms(Arrays.asList(source("src", "srcBean"), out));
        model.setEdges(Arrays.asList(edge("e0", "src", "out")));

        StreamException ex = buildFailsFast(model);
        assertTrue(ex.getMessage().contains("maxParallelism"), () -> ex.getMessage());
    }

    @Test
    public void perTransformParallelismMismatchFailFast() {
        StreamModel model = baseModel("parallelism-mismatch");
        model.setParallelism(2);
        StreamSinkModel out = sink("out", "sinkBean");
        // declared 4 vs effective (stream-level) 2 -> silently ignored today, must fail fast
        out.setParallelism(4);
        model.setTransforms(Arrays.asList(source("src", "srcBean"), out));
        model.setEdges(Arrays.asList(edge("e0", "src", "out")));

        StreamException ex = buildFailsFast(model);
        assertEquals("nop.err.stream.not-implemented", ex.getErrorCode().toString());
        assertTrue(ex.getMessage().contains("parallelism"), () -> ex.getMessage());
        assertTrue(ex.getMessage().contains("declared=4"), () -> ex.getMessage());
    }

    @Test
    public void perTransformParallelismMatchingEffectiveValueIsAccepted() {
        StreamModel model = baseModel("parallelism-match");
        model.setParallelism(2);
        StreamSourceModel src = source("src", "srcBean");
        StreamSinkModel out = sink("out", "sinkBean");
        src.setParallelism(2);
        out.setParallelism(2);
        model.setTransforms(Arrays.asList(src, out));
        model.setEdges(Arrays.asList(edge("e0", "src", "out")));

        StreamExecutionEnvironment env = StreamModelDslBuilder.of(model,
                new InMemoryBeanFunctionResolver()
                        .register("srcBean", new TestSourceFunction())
                        .register("sinkBean", new CollectingSinkFunction<>())).build();
        assertNotNull(env, "a declaration that matches the effective value must build normally");
    }

    @Test
    public void cycleErrorNamesUnprocessedTransformIds() {
        StreamModel model = baseModel("cycle-ids");
        StreamSourceModel src = source("cycSrc", "srcBean");
        StreamMapModel map = new StreamMapModel();
        map.setId("cycMap");
        model.setTransforms(Arrays.asList(src, map));
        model.setEdges(Arrays.asList(edge("e0", "cycSrc", "cycMap"), edge("e1", "cycMap", "cycSrc")));

        StreamException ex = buildFailsFast(model);
        assertEquals("nop.err.stream.cyclic-job-graph", ex.getErrorCode().toString());
        assertTrue(ex.getMessage().contains("cycSrc"), () -> ex.getMessage());
        assertTrue(ex.getMessage().contains("cycMap"), () -> ex.getMessage());
    }

    @Test
    public void timestampsWithoutAssignerExecutesWithoutNpe() throws Exception {
        StreamModel model = parseStreamXml("/nop/stream/test/test-timestamps-no-assigner.stream.xml");
        StreamExecutionEnvironment env = StreamModelDslBuilder.of(model).build();

        env.execute("test-timestamps-no-assigner");

        CollectingSinkFunction<String> collecting = (CollectingSinkFunction<String>)
                BeanContainer.instance().getBean("collectingSinkFunction");
        List<String> collected = collecting.getCollected();
        assertEquals(Arrays.asList("a", "b", "c"), collected,
                "records must flow through timestampsAndWatermarks with the NoOp assigner");
    }

    private static StreamModel parseStreamXml(String vfsPath) {
        IResource resource = VirtualFileSystem.instance().getResource(vfsPath);
        return (StreamModel) new DslModelParser().parseFromResource(resource);
    }
}

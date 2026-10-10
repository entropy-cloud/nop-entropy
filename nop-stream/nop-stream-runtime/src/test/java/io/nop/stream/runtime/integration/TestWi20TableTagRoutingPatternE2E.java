/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.integration;

import io.nop.api.core.ioc.BeanContainer;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.xml.XNode;
import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.flow.builder.InMemoryBeanFunctionResolver;
import io.nop.stream.flow.builder.StreamModelDslBuilder;
import io.nop.stream.flow.model.StreamModel;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * WI20 (plan 2366): the WI0-adjudicated DECLARATIVE DEGRADATION shape for multi-table
 * streams — a single physical stream whose records carry a TABLE-TAG FIELD, routed
 * per table by {@code <filter>} splits into per-table sinks. No tableId declaration
 * surface: the engine-level record metadata ({@code StreamRecord.tableId}, written
 * by CDC connectors via {@code withChangeMetadata}) is a connector-layer concern;
 * the DSL routing uses the value-carried tag field.
 *
 * <p>Data-plane pin: a mixed stream [t1,t2,t1,t2] routes t1 records to sink1 and t2
 * records to sink2 with zero cross-contamination.
 */
public class TestWi20TableTagRoutingPatternE2E {

    private static final List<Object> SINK1 = new ArrayList<>();
    private static final List<Object> SINK2 = new ArrayList<>();

    private static IBeanContainerImplementor container;

    @BeforeAll
    public static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_IOC - 1);
        container = new io.nop.ioc.loader.BeanContainerBuilder(null).build("wi20-table-tag");
        container.start();
        BeanContainer.registerInstance(container);
    }

    @AfterAll
    public static void destroy() {
        if (container != null)
            container.stop();
        CoreInitialization.destroy();
        BeanContainer.registerInstance(null);
    }

    @Test
    public void tableTagFieldRoutesPerTableWithoutCrossContamination() throws Exception {
        SINK1.clear();
        SINK2.clear();

        String xml = "<stream xmlns:x=\"/nop/schema/xdsl.xdef\" "
                + "x:schema=\"/nop/schema/stream/stream.xdef\" name=\"wi20-table-tag\" version=\"1\">"
                + "<transforms>"
                + "<source id=\"src\" bean=\"wi20MixedSource\"/>"
                + "<filter id=\"t1Route\"><source>event.tableTag == 't1'</source></filter>"
                + "<filter id=\"t2Route\"><source>event.tableTag == 't2'</source></filter>"
                + "<sink id=\"sink1\" bean=\"wi20Sink1\"/>"
                + "<sink id=\"sink2\" bean=\"wi20Sink2\"/>"
                + "</transforms>"
                + "<edges>"
                + "<edge id=\"e0\" from=\"src\" to=\"t1Route\"/>"
                + "<edge id=\"e1\" from=\"src\" to=\"t2Route\"/>"
                + "<edge id=\"e2\" from=\"t1Route\" to=\"sink1\"/>"
                + "<edge id=\"e3\" from=\"t2Route\" to=\"sink2\"/>"
                + "</edges>"
                + "</stream>";
        StreamModel model = (StreamModel) new DslModelParser().parseFromNode(XNode.parse(xml));
        InMemoryBeanFunctionResolver resolver = new InMemoryBeanFunctionResolver()
                .register("wi20MixedSource", new MixedTableSource())
                .register("wi20Sink1",
                        (io.nop.stream.core.common.functions.SinkFunction<Object>) v -> SINK1.add(v))
                .register("wi20Sink2",
                        (io.nop.stream.core.common.functions.SinkFunction<Object>) v -> SINK2.add(v));

        StreamExecutionEnvironment env = StreamModelDslBuilder.of(model, resolver).build();
        env.execute("wi20-table-tag-routing");

        // Per-table routing with zero cross-contamination: every record lands in its
        // own table's sink, exactly once.
        assertEquals(List.of(Map.of("tableTag", "t1", "id", 1), Map.of("tableTag", "t1", "id", 3)),
                SINK1, () -> "sink1: " + SINK1);
        assertEquals(List.of(Map.of("tableTag", "t2", "id", 2), Map.of("tableTag", "t2", "id", 4)),
                SINK2, () -> "sink2: " + SINK2);
    }

    /** Mixed-table records: [t1#1, t2#2, t1#3, t2#4]. */
    static class MixedTableSource
            implements io.nop.stream.core.common.functions.source.SourceFunction<Map<String, Object>> {
        private boolean running = true;

        @Override
        public void run(io.nop.stream.core.common.functions.source.SourceFunction.SourceContext<Map<String, Object>> ctx)
                throws Exception {
            String[] tags = {"t1", "t2", "t1", "t2"};
            for (int i = 0; i < tags.length; i++) {
                if (!running)
                    return;
                ctx.collect(Map.of("tableTag", tags[i], "id", i + 1));
            }
        }

        @Override
        public void cancel() {
            running = false;
        }
    }
}

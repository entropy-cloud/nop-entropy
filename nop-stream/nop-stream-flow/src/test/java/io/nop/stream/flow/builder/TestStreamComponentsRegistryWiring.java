/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.flow.builder;

import io.nop.api.core.ioc.BeanContainer;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.impl.ClassPathResource;
import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.ioc.loader.BeanContainerBuilder;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.model.StreamComponentEntry;
import io.nop.stream.core.model.StreamComponents;
import io.nop.stream.flow.model.StreamModel;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI21 (§八 11): the DSL layer's registry declarations — aggregators / joins /
 * schemas (xdef faces landed by WI8b/WI8c/WI8d) — must reach the core
 * {@link StreamComponents} registry of the materialized job model. Before this
 * wiring the xdef declarations were parsed, validated, and then dropped: the
 * core components carried none of them (the gap this test pins, red-first).
 */
public class TestStreamComponentsRegistryWiring {

    private static IBeanContainerImplementor container;

    @BeforeAll
    public static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_IOC - 1);
        BeanContainerBuilder builder = new BeanContainerBuilder(null);
        builder.addResource(new ClassPathResource(
                "classpath:_vfs/nop/stream/test/test-join-pipeline.beans.xml"));
        container = builder.build("stream-flow-registry-wiring");
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

    private static final String REGISTRY_MODEL = "<stream xmlns:x=\"/nop/schema/xdsl.xdef\" "
            + "x:schema=\"/nop/schema/stream/stream.xdef\" "
            + "name=\"wi21-registry-wiring\" version=\"1\">"
            + "<schemas>"
            + "<schema id=\"sch1\"><fields>"
            + "<field name=\"k\" type=\"string\"/><field name=\"v\" type=\"long\"/>"
            + "</fields></schema>"
            + "</schemas>"
            + "<windowingStrategies>"
            + "<strategy strategyId=\"ws1\" windowFnId=\"tumbling-event-time\" duration=\"30s\"/>"
            + "</windowingStrategies>"
            + "<aggregators>"
            + "<aggregator aggregatorId=\"agg1\" fnId=\"sum\" expr=\"event.v\" schemaId=\"sch1\"/>"
            + "</aggregators>"
            + "<joins>"
            + "<joinSpec joinId=\"j1\" joinType=\"INNER\""
            + " leftKeyExprs=\"event.k\" rightKeyExprs=\"event.k\""
            + " windowStrategyRef=\"ws1\" timeout=\"10s\"/>"
            + "</joins>"
            + "<transforms>"
            + "<source id=\"s1\" bean=\"joinLeftSource\"/>"
            + "<source id=\"s2\" bean=\"joinRightSource\"/>"
            + "<union id=\"u\"/>"
            + "<sink id=\"out\" bean=\"joinCollectingSink\"/>"
            + "</transforms>"
            + "<edges>"
            + "<edge id=\"e0\" from=\"s1\" to=\"u\"/>"
            + "<edge id=\"e1\" from=\"s2\" to=\"u\"/>"
            + "<edge id=\"e2\" from=\"u\" to=\"out\"/>"
            + "</edges>"
            + "</stream>";

    @Test
    public void declaredRegistriesFlowIntoStreamComponents() throws Exception {
        IResource resource = VirtualFileSystem.instance()
                .getResource("/nop/stream/test/wi21-registry-wiring.stream.xml");
        StreamModel model = (StreamModel) new DslModelParser().parseFromResource(resource);
        StreamExecutionEnvironment env = StreamModelDslBuilder.of(model).build();
        JobGraph jobGraph = env.buildJobGraph("wi21-registry-wiring");

        io.nop.stream.core.model.StreamModel attached = jobGraph.getStreamModel();
        assertNotNull(attached, "job graph must carry the materialized stream model");
        StreamComponents components = attached.getComponents();

        // aggregators (WI8c declaration face -> core registry)
        assertTrue(components.hasDeclarativeRegistry("aggregators"),
                "aggregators registry must reach StreamComponents (§八 11)");
        StreamComponentEntry agg = components.getDeclarativeEntry("aggregators", "agg1");
        assertNotNull(agg, "aggregator entry agg1 must be present");
        assertEquals("sum", agg.getAttributes().get("fnId"));
        assertEquals("event.v", agg.getAttributes().get("expr"));
        assertEquals("sch1", agg.getAttributes().get("schemaId"));

        // joins (WI8d declaration face -> core registry)
        assertTrue(components.hasDeclarativeRegistry("joins"),
                "joins registry must reach StreamComponents (§八 11)");
        StreamComponentEntry join = components.getDeclarativeEntry("joins", "j1");
        assertNotNull(join, "join entry j1 must be present");
        assertEquals("INNER", join.getAttributes().get("joinType"));
        assertEquals("event.k", join.getAttributes().get("leftKeyExprs"));
        assertEquals("event.k", join.getAttributes().get("rightKeyExprs"));
        assertEquals("ws1", join.getAttributes().get("windowStrategyRef"));
        assertEquals("10s", join.getAttributes().get("timeout"));

        // schemas (WI8b declaration face -> core registry)
        assertTrue(components.hasDeclarativeRegistry("schemas"),
                "schemas registry must reach StreamComponents (§八 11)");
        StreamComponentEntry schema = components.getDeclarativeEntry("schemas", "sch1");
        assertNotNull(schema, "schema entry sch1 must be present");
        assertEquals("k:string,v:bigint", schema.getAttributes().get("fields"));
    }
}

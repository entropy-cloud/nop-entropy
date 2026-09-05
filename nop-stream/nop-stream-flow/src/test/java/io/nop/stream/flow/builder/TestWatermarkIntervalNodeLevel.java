/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.flow.builder;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.stream.core.datastream.SingleOutputStreamOperator;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.transformation.TimestampsAndWatermarksTransformation;
import io.nop.stream.core.transformation.Transformation;
import io.nop.stream.flow.model.StreamModel;
import io.nop.stream.flow.testing.TestSourceFunction;
import io.nop.xlang.xdsl.DslModelParser;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F-04 regression proofs (plan 1326-2 Phase 4): the node-level
 * {@code <timestampsAndWatermarks watermarkInterval="...">} declaration must
 * actually reach the operator (previously parsed and silently dropped — every
 * job ran with the env-level interval, so quickstart topologies declaring
 * {@code watermarkInterval="0"} with a "per-event watermark" comment actually
 * got the 200ms env default), and a root-level {@code watermarkInterval="0"}
 * must not be swallowed by the {@code > 0} guard.
 */
public class TestWatermarkIntervalNodeLevel {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private static InMemoryBeanFunctionResolver resolver() {
        InMemoryBeanFunctionResolver resolver = new InMemoryBeanFunctionResolver();
        resolver.register("srcFn", new TestSourceFunction());
        return resolver;
    }

    private static StreamModel parseInline(String transformsXml) {
        String xml = "<stream xmlns:x=\"/nop/schema/xdsl.xdef\" "
                + "x:schema=\"/nop/schema/stream/stream.xdef\" "
                + "name=\"wm-interval-test\" version=\"1\">"
                + "<transforms>" + transformsXml + "</transforms>"
                + "<edges>"
                + "<edge id=\"e0\" from=\"src\" to=\"ts\" partition=\"FORWARD\"/>"
                + "</edges>"
                + "</stream>";
        XNode node = XNode.parse(xml);
        IResource resource = VirtualFileSystem.instance().getResource("/nop/schema/stream/stream.xdef");
        assertTrue(resource.exists(), "stream.xdef must be on the test classpath");
        return (StreamModel) new DslModelParser().parseFromNode(node);
    }

    /** Node-level interval 0 (per-event) reaches the transformation. */
    @Test
    public void nodeLevelZeroIntervalIsWired() {
        StreamModel model = parseInline(
                "<source id=\"src\" bean=\"srcFn\"/>"
                        + "<timestampsAndWatermarks id=\"ts\" watermarkInterval=\"0\">"
                        + "<timestampAssigner>return 0L;</timestampAssigner>"
                        + "</timestampsAndWatermarks>");

        StreamModelDslBuilder builder = StreamModelDslBuilder.of(model, resolver());
        builder.build();
        Object out = builder.registeredStream("ts");
        assertInstanceOf(SingleOutputStreamOperator.class, out);

        Transformation<?> transformation = ((io.nop.stream.core.datastream.DataStreamImpl<?>) out).getTransformation();
        TimestampsAndWatermarksTransformation<?> tsTransformation =
                assertInstanceOf(TimestampsAndWatermarksTransformation.class, transformation);
        assertEquals(0L, tsTransformation.getWatermarkInterval(),
                "node-level watermarkInterval=\"0\" (per-event emission) must reach the "
                        + "operator instead of being silently replaced by the env default 200");
    }

    /** Node-level non-default interval reaches the transformation. */
    @Test
    public void nodeLevelNonDefaultIntervalIsWired() {
        StreamModel model = parseInline(
                "<source id=\"src\" bean=\"srcFn\"/>"
                        + "<timestampsAndWatermarks id=\"ts\" watermarkInterval=\"333\">"
                        + "<timestampAssigner>return 0L;</timestampAssigner>"
                        + "</timestampsAndWatermarks>");

        StreamModelDslBuilder builder = StreamModelDslBuilder.of(model, resolver());
        builder.build();
        Object out = builder.registeredStream("ts");
        Transformation<?> transformation = ((io.nop.stream.core.datastream.DataStreamImpl<?>) out).getTransformation();
        TimestampsAndWatermarksTransformation<?> tsTransformation =
                assertInstanceOf(TimestampsAndWatermarksTransformation.class, transformation);
        assertEquals(333L, tsTransformation.getWatermarkInterval(),
                "node-level non-default watermarkInterval must be wired (previously "
                        + "silently dropped)");
    }

    /** Root-level watermarkInterval="0" (per-event) is not swallowed by the > 0 guard. */
    @Test
    public void rootLevelZeroIntervalIsNotSwallowed() {
        StreamModel model = parseInline(
                "<source id=\"src\" bean=\"srcFn\"/>"
                        + "<timestampsAndWatermarks id=\"ts\">"
                        + "<timestampAssigner>return 0L;</timestampAssigner>"
                        + "</timestampsAndWatermarks>");
        model.setWatermarkInterval(0L);

        StreamModelDslBuilder builder = StreamModelDslBuilder.of(model, resolver());
        StreamExecutionEnvironment env = builder.build();
        assertEquals(0L, env.getWatermarkInterval(),
                "root-level watermarkInterval=0 (per-event emission) must reach the env "
                        + "(previously the `> 0` guard silently dropped it and the 200ms "
                        + "default kept running)");
        assertTrue(env.getWatermarkInterval() >= 0);
    }
}

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
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.impl.ClassPathResource;
import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.ioc.loader.BeanContainerBuilder;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.flow.model.StreamModel;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI17 (plan 25 r2 B4/B5) layer-1: the flow-side {@code <sql>} expansion with a FAKE
 * {@code ISqlStreamCompiler} — the builder's preprocessing (unique-content guard,
 * provider lookup, DslModelParser read-back, parent content replacement) is proven in
 * isolation from the real sql module (absent from this module's test classpath).
 * The no-provider branch is pinned the same way WI8c pinned
 * {@code aggregateRefWithoutProvider} — an empty container must fail fast naming the
 * nop-stream-sql dependency.
 */
public class TestSqlModelExpansion {

    private static IBeanContainerImplementor container;

    @BeforeAll
    public static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_IOC - 1);
        container = startContainer("classpath:_vfs/nop/stream/test/test-sql-expansion.beans.xml",
                "sql-expansion");
    }

    @AfterAll
    public static void destroy() {
        stopContainer(container);
        CoreInitialization.destroy();
        BeanContainer.registerInstance(null);
    }

    private static IBeanContainerImplementor startContainer(String resource, String name) {
        BeanContainerBuilder builder = new BeanContainerBuilder(null);
        builder.addResource(new ClassPathResource(resource));
        IBeanContainerImplementor c = builder.build(name);
        c.start();
        BeanContainer.registerInstance(c);
        return c;
    }

    private static void stopContainer(IBeanContainerImplementor c) {
        if (c != null)
            c.stop();
    }

    private static StreamModel parseInline(String topLevelXml) {
        String xml = "<stream xmlns:x=\"/nop/schema/xdsl.xdef\" "
                + "x:schema=\"/nop/schema/stream/stream.xdef\" "
                + "name=\"inline-sql\" version=\"1\">"
                + topLevelXml
                + "</stream>";
        XNode node = XNode.parse(xml);
        return (StreamModel) new DslModelParser().parseFromNode(node);
    }

    @Test
    public void fakeProviderExpandsModelContent() throws Exception {
        StreamModel model = parseInline(
                "<sql sinkBean=\"fakeSinkFn\">"
                        + "<source>ANY SQL TEXT — the fake compiler ignores it</source>"
                        + "</sql>");
        io.nop.stream.flow.testing.CollectingSinkFunction<String> sink =
                new io.nop.stream.flow.testing.CollectingSinkFunction<>();
        InMemoryBeanFunctionResolver resolver = new InMemoryBeanFunctionResolver()
                .register("fakeSrcFn", new io.nop.stream.flow.testing.TestSourceFunction())
                .register("fakeSinkFn", sink);

        StreamModelDslBuilder.of(model, resolver).build().execute("fake-provider-product");

        // B4, reshaped for CRA 8.45 R4-05 W-07 (zero-consumption contract): the
        // caller-held model stays pristine — the <sql> element is NOT consumed (the
        // expansion product lives on the builder's internal working copy). The
        // former assertion observed the in-place expansion (post-build getSql()
        // == null on the caller model), which the contract deliberately removed.
        assertNotNull(model.getSql(), "build() must not consume the <sql> declaration");

        // the SPI product still fully replaces the parent content — proven at the
        // output level: the fake product's pipeline (fakeSrc → fakeMap → sink)
        // assembled and carried the full fixed record sequence
        assertEquals(io.nop.stream.flow.testing.TestSourceFunction.FIXED_DATA, sink.getCollected(),
                "the fake SPI product's pipeline must replace the parent content and run");
    }

    @Test
    public void sqlEmitAttributeFlowsToCompiler() {
        // WI37 (closure audit M-3): the <sql emit> attribute must reach the SPI —
        // a null regression would be silently normalized to CLOSE at compile time
        StreamModel model = parseInline(
                "<sql sinkBean=\"fakeSinkFn\" emit=\"UPDATE\">"
                        + "<source>ANY SQL TEXT — the fake compiler ignores it</source>"
                        + "</sql>");
        StreamModelDslBuilder.of(model).build();

        FakeSqlStreamCompiler fake =
                (FakeSqlStreamCompiler) BeanContainer.instance().getBean("fakeSqlCompiler");
        assertEquals(io.nop.stream.core.windowing.StreamEmitMode.UPDATE, fake.lastEmit,
                "the declared <sql emit> must be passed to the SPI compiler");
    }

    @Test
    public void sqlFieldLatenessFlowsToCompiler() {
        // WI25: the <field lateness> annotation must reach the SPI (empty map when
        // no field declares lateness)
        StreamModel model = parseInline(
                "<sql sinkBean=\"fakeSinkFn\" emit=\"CLOSE\">"
                        + "<schemas><field name=\"ts\" type=\"bigint\" lateness=\"20000\"/></schemas>"
                        + "<source>ANY SQL TEXT</source>"
                        + "</sql>");
        StreamModelDslBuilder.of(model).build();

        FakeSqlStreamCompiler fake =
                (FakeSqlStreamCompiler) BeanContainer.instance().getBean("fakeSqlCompiler");
        assertEquals(java.util.Collections.singletonMap("ts", 20000L), fake.lastFieldLateness,
                "the declared <field lateness> must be passed to the SPI compiler");

        // the no-declaration path passes an empty map (never null)
        StreamModel plain = parseInline(
                "<sql sinkBean=\"fakeSinkFn\"><source>ANY SQL TEXT</source></sql>");
        StreamModelDslBuilder.of(plain).build();
        assertEquals(java.util.Collections.emptyMap(), fake.lastFieldLateness,
                "no lateness declaration means an empty map reaches the compiler");
    }

    @Test
    public void sqlCoexistingWithOtherContentFailsFast() {
        StreamModel model = parseInline(
                "<sql sinkBean=\"fakeSinkFn\"><source>SELECT 1</source></sql>"
                        + "<transforms><source id=\"extra\" bean=\"fakeSrcFn\"/></transforms>"
                        + "<edges><edge id=\"e0\" from=\"extra\" to=\"extra\"/></edges>");

        StreamException ex = assertThrows(StreamException.class,
                () -> StreamModelDslBuilder.of(model).build());
        assertEquals("nop.err.stream.invalid-arg", ex.getErrorCode().toString());
        assertTrue(ex.getMessage().contains("only content"),
                () -> "Expected unique-content error, got: " + ex.getMessage());
    }

    @Test
    public void noProviderFailsFastNamingDependency() {
        // an initialized container WITHOUT a compiler bean (empty beans file) — the
        // provider lookup must fail fast naming nop-stream-sql, never silently skip
        IBeanContainerImplementor empty = startContainer(
                "classpath:_vfs/nop/stream/test/test-empty.beans.xml", "sql-no-provider");
        try {
            StreamModel model = parseInline(
                    "<sql sinkBean=\"fakeSinkFn\"><source>SELECT 1</source></sql>");
            StreamException ex = assertThrows(StreamException.class,
                    () -> StreamModelDslBuilder.of(model).build());
            assertEquals("nop.err.stream.invalid-arg", ex.getErrorCode().toString());
            assertTrue(ex.getMessage().contains("nop-stream-sql"),
                    () -> "Expected no-provider error naming nop-stream-sql, got: "
                            + ex.getMessage());
        } finally {
            stopContainer(empty);
            BeanContainer.registerInstance(container);
        }
    }
}

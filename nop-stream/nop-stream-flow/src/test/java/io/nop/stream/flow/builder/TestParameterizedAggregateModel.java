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
import io.nop.core.resource.impl.ClassPathResource;
import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.ioc.loader.BeanContainerBuilder;
import io.nop.stream.core.common.functions.AggregateFunction;
import io.nop.stream.core.common.typeinfo.BasicTypeInfo;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.flow.model.StreamModel;
import io.nop.stream.flow.spi.IAggregatorFunctionResolver;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI8c wiring proof: a legal {@code <aggregate aggregatorRef>} declaration reaches
 * the {@code IAggregatorFunctionResolver} provider through the BeanContainer (rule
 * #23 — the SPI is really invoked at build time, not just resolvable), and the
 * resolved function flows into the aggregate call (build then hits the known
 * nop-stream-runtime window-operator-factory gap, which proves the builder kept
 * going past resolution).
 *
 * <p>Manual container form (TestAdvancedPipelineE2E precedent): initializeTo(IOC-1)
 * + BeanContainerBuilder + registerInstance, so the fake resolver is injectable
 * without touching production code.
 */
public class TestParameterizedAggregateModel {

    private static IBeanContainerImplementor container;

    /** Counts invocations and returns a working sum function — proves real wiring. */
    public static final class CountingFakeResolver implements IAggregatorFunctionResolver {
        static final AtomicInteger CALLS = new AtomicInteger();

        public CountingFakeResolver() {
        }

        @Override
        public AggregateFunction<Object, Object, Object> resolve(String fnId, String expr,
                                                                 Function<String, BasicTypeInfo<?>> columnTypes) {
            CALLS.incrementAndGet();
            assertEquals("sum", fnId);
            assertEquals("amount", expr);
            return new AggregateFunction<Object, Object, Object>() {
                private static final long serialVersionUID = 1L;

                @Override
                public Object createAccumulator() {
                    return 0L;
                }

                @Override
                public Object add(Object value, Object accumulator) {
                    return (Long) accumulator + 1L;
                }

                @Override
                public Object getResult(Object accumulator) {
                    return accumulator;
                }

                @Override
                public Object merge(Object a, Object b) {
                    return (Long) a + (Long) b;
                }
            };
        }
    }

    @BeforeAll
    public static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_IOC - 1);
        BeanContainerBuilder builder = new BeanContainerBuilder(null);
        builder.addResource(new ClassPathResource(
                "classpath:_vfs/nop/stream/test/test-param-agg.beans.xml"));
        builder.addResource(new ClassPathResource(
                "classpath:_vfs/nop/stream/test/test-reduce-pipeline.beans.xml"));
        container = builder.build("stream-flow-param-agg");
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

    @Test
    public void aggregatorRefResolutionWiresThroughBeanContainer() throws Exception {
        CountingFakeResolver.CALLS.set(0);

        StreamModel model = parseStreamXml("/nop/stream/test/test-param-agg.stream.xml");
        StreamModelDslBuilder builder = StreamModelDslBuilder.of(model);

        // The resolution succeeds; the pipeline then hits the known runtime
        // window-operator-factory gap (no nop-stream-runtime on this classpath) —
        // proving the builder kept going past resolution with the resolved function.
        StreamException ex = assertThrows(StreamException.class, builder::build);
        assertTrue(ex.getMessage().contains("nop-stream-runtime"),
                () -> "Expected runtime-factory gap after resolution, got: " + ex.getMessage());
        assertEquals(1, CountingFakeResolver.CALLS.get(),
                "the BeanContainer-registered resolver must be invoked exactly once at build time");
    }

    private static StreamModel parseStreamXml(String vfsPath) throws Exception {
        io.nop.core.resource.IResource resource =
                io.nop.core.resource.VirtualFileSystem.instance().getResource(vfsPath);
        return (StreamModel) new DslModelParser().parseFromResource(resource);
    }
}

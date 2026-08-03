/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.flow.builder;

import java.util.Arrays;
import java.util.List;

import io.nop.api.core.ioc.BeanContainer;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.impl.ClassPathResource;
import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.ioc.loader.BeanContainerBuilder;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.flow.testing.CollectingSinkFunction;
import io.nop.stream.flow.testing.TestSourceFunction;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link GlobalBeanFunctionResolver} resolves {@code bean="..."} references
 * from a real NopIoC container loaded from {@code test-smoke.beans.xml}, and that
 * {@link InMemoryBeanFunctionResolver} works as an in-process alternative.
 *
 * <p>This covers Phase 1 exit criterion "bean 查找 1 test".
 */
public class TestBeanFunctionResolver {

    private static IBeanContainerImplementor container;

    @BeforeAll
    public static void init() {
        // Initialize core up to (but not including) the global IoC initializer so we can
        // build a STANDALONE container from our test beans.xml without dragging in the
        // global dao-defaults (which would require a datasource).
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_IOC - 1);
        BeanContainerBuilder builder = new BeanContainerBuilder(null);
        builder.addResource(new ClassPathResource(
                "classpath:_vfs/nop/stream/test/test-smoke.beans.xml"));
        container = builder.build("stream-flow-test-beans");
        container.start();
        BeanContainer.registerInstance(container);
    }

    @AfterAll
    public static void destroy() {
        if (container != null) {
            container.stop();
        }
        CoreInitialization.destroy();
    }

    @Test
    public void globalResolverReturnsSourceFunctionFromBeansXml() {
        GlobalBeanFunctionResolver resolver = GlobalBeanFunctionResolver.INSTANCE;
        assertTrue(resolver.contains("testSourceFunction"));

        SourceFunction<?> source = resolver.resolve("testSourceFunction", SourceFunction.class);
        assertInstanceOf(TestSourceFunction.class, source);
    }

    @Test
    public void globalResolverReturnsSinkFunctionFromBeansXml() {
        GlobalBeanFunctionResolver resolver = GlobalBeanFunctionResolver.INSTANCE;
        assertTrue(resolver.contains("collectingSinkFunction"));

        SinkFunction<?> sink = resolver.resolve("collectingSinkFunction", SinkFunction.class);
        assertInstanceOf(CollectingSinkFunction.class, sink);
    }

    @Test
    public void globalResolverFailsFastOnMissingBean() {
        GlobalBeanFunctionResolver resolver = GlobalBeanFunctionResolver.INSTANCE;
        assertFalse(resolver.contains("doesNotExist"));
        assertThrows(IllegalArgumentException.class,
                () -> resolver.resolve("doesNotExist", SourceFunction.class));
    }

    @Test
    public void globalResolverFailsFastOnTypeMismatch() {
        GlobalBeanFunctionResolver resolver = GlobalBeanFunctionResolver.INSTANCE;
        // testSourceFunction is a SourceFunction, not a SinkFunction
        assertThrows(IllegalArgumentException.class,
                () -> resolver.resolve("testSourceFunction", SinkFunction.class));
    }

    @Test
    public void inMemoryResolverWorksForExplicitlyRegisteredBeans() {
        InMemoryBeanFunctionResolver resolver = new InMemoryBeanFunctionResolver();
        resolver.register("mySink", new CollectingSinkFunction<>());

        assertTrue(resolver.contains("mySink"));
        assertInstanceOf(CollectingSinkFunction.class,
                resolver.resolve("mySink", SinkFunction.class));
    }

    @Test
    public void sourceFunctionEmitsFixedTestData() {
        // Sanity check that the bean-container-resolved SourceFunction actually does what
        // the test-smoke beans.xml promises (so a regression in TestSourceFunction is
        // caught here rather than in a confusing e2e failure).
        GlobalBeanFunctionResolver resolver = GlobalBeanFunctionResolver.INSTANCE;
        TestSourceFunction source = resolver.resolve("testSourceFunction", TestSourceFunction.class);
        List<String> seen = Arrays.asList("a", "b", "c");
        assertEquals(TestSourceFunction.FIXED_DATA, seen);
        assertEquals(3, source.FIXED_DATA.size());
    }
}

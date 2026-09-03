/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.flow.builder;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.impl.ClassPathResource;
import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.ioc.loader.BeanContainerBuilder;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.flow.testing.CollectingSinkFunction;
import io.nop.stream.flow.testing.TestSourceFunction;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D2 bean-source form 2 (pre-submit-validation-design.md §3): an explicitly assembled
 * NopIoC container — NOT registered as the global instance — backs the
 * {@link BeanContainerFunctionResolver}. Conf-validate hosts in independent-deployment
 * shape pass such a resolver instead of polluting the global container.
 */
public class TestBeanContainerFunctionResolver {

    private static IBeanContainerImplementor container;
    private static BeanContainerFunctionResolver resolver;

    @BeforeAll
    public static void init() {
        // Standalone container (no global registration): exactly the assembly shape the
        // item 19 connector registry uses, and the shape form 2 wraps.
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_IOC - 1);
        BeanContainerBuilder builder = new BeanContainerBuilder(null);
        builder.addResource(new ClassPathResource(
                "classpath:_vfs/nop/stream/test/test-smoke.beans.xml"));
        container = builder.build("conf-validate-form2-test-beans");
        container.start();
        resolver = BeanContainerFunctionResolver.of(container);
    }

    @AfterAll
    public static void destroy() {
        if (container != null) {
            container.stop();
        }
        CoreInitialization.destroy();
    }

    @Test
    public void resolvesBeansFromStandaloneContainer() {
        assertTrue(resolver.contains("testSourceFunction"));
        SourceFunction<?> source = resolver.resolve("testSourceFunction", SourceFunction.class);
        assertInstanceOf(TestSourceFunction.class, source);

        assertTrue(resolver.contains("collectingSinkFunction"));
        SinkFunction<?> sink = resolver.resolve("collectingSinkFunction", SinkFunction.class);
        assertInstanceOf(CollectingSinkFunction.class, sink);
    }

    @Test
    public void failsFastOnMissingBeanWithTypeErrorCode() {
        StreamException ex = assertThrows(StreamException.class,
                () -> resolver.resolve("noSuchBean", SourceFunction.class));
        assertTrue(ex.getErrorCode().contains("bean-not-found"),
                "expected bean-not-found error code: " + ex.getErrorCode());
    }

    @Test
    public void failsFastOnTypeMismatchWithTypeErrorCode() {
        StreamException ex = assertThrows(StreamException.class,
                () -> resolver.resolve("testSourceFunction", SinkFunction.class));
        assertTrue(ex.getErrorCode().contains("bean-type-mismatch"),
                "expected bean-type-mismatch error code: " + ex.getErrorCode());
    }

    @Test
    public void rejectsNullContainer() {
        assertThrows(StreamException.class, () -> BeanContainerFunctionResolver.of(null));
    }
}

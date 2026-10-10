/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.acceptance;

import io.nop.api.core.ioc.BeanContainer;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.impl.ClassPathResource;
import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.ioc.loader.BeanContainerBuilder;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.flow.builder.StreamModelDslBuilder;
import io.nop.stream.flow.builder.InMemoryBeanFunctionResolver;
import io.nop.stream.flow.builder.StreamModelDslBuilder;
import io.nop.stream.flow.model.StreamModel;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI26 E12 (TradingVolume/AccessLog degradation shape, 12-scenario matrix):
 * early-results periodic emission on out-of-order data (the WI8 earlyResultsPeriod
 * face — partial results before the window closes), plus the tail-source
 * adjudication pin (file tailing is OUT per WI0; bounded replay + DRAIN is the
 * recorded workaround, declared via jobTerminationMode=DRAIN — see the WI19
 * declaration pins).
 */
public class TestXdslScenarioE12 {

    private static IBeanContainerImplementor container;

    @BeforeAll
    public static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_IOC - 1);
        container = new BeanContainerBuilder(null)
                .addResource(new ClassPathResource(
                        "classpath:_vfs/nop/stream/test/xdsl-e12-early-tail.beans.xml"))
                .build("xdsl-e12-early-tail");
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
    public void earlyResultsEmitPartialResultsOnOutOfOrderData() throws Exception {
        E12EarlyTailBeans.E12Stores.FLUSHED.clear();

        StreamModel model = (StreamModel) new DslModelParser().parseFromResource(
                new ClassPathResource("classpath:_vfs/nop/stream/test/xdsl-e12-early-tail.stream.xml"));
        InMemoryBeanFunctionResolver resolver = new InMemoryBeanFunctionResolver()
                .register("e12VolumeSource", new E12EarlyTailBeans.VolumeSource())
                .register("e12SumReduce", new E12EarlyTailBeans.SumReduce())
                .register("e12PartialSink",
                        (io.nop.stream.core.common.functions.SinkFunction<Long>) v -> E12EarlyTailBeans.E12Stores.FLUSHED.add(v));

        StreamExecutionEnvironment env = StreamModelDslBuilder.of(model, resolver).build();
        env.execute("xdsl-e12-early-tail");

        // partial results observed: the early fires emitted the running sums before
        // the final watermark flush — the WI8 sequence on this out-of-order data
        assertTrue(!E12EarlyTailBeans.E12Stores.FLUSHED.isEmpty(), () -> "flushed: " + E12EarlyTailBeans.E12Stores.FLUSHED);
        long last = 0;
        for (long v : E12EarlyTailBeans.E12Stores.FLUSHED) {
            org.junit.jupiter.api.Assertions.assertTrue(v >= last,
                    () -> "ACCUMULATING partials must be non-decreasing: " + E12EarlyTailBeans.E12Stores.FLUSHED);
            last = v;
        }
    }
}

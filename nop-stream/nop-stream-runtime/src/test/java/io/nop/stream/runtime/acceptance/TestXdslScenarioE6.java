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
 * WI23 E6 (Flink AsyncIOExample degradation shape): the WI17 synchronous-replacement
 * contract (process bean with bounded capacity + deadline + explicit fallback)
 * fixture-pinned as the E6 scenario. The slow item exceeds the deadline → explicit
 * TIMEOUT fallback; fast items complete as DONE; the in-flight peak stays within
 * the declared capacity.
 */
public class TestXdslScenarioE6 {

    private static IBeanContainerImplementor container;

    @BeforeAll
    public static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_IOC - 1);
        container = new BeanContainerBuilder(null)
                .addResource(new ClassPathResource(
                        "classpath:_vfs/nop/stream/test/xdsl-e6-async.beans.xml"))
                .build("xdsl-e6-async");
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
    public void syncReplacementCarriesCapacityTimeoutFallbackContract() throws Exception {
        E6AsyncBeans.RESULTS.clear();
        E6AsyncBeans.PEAK.set(0);
        E6AsyncBeans.SLOW_MS = 500;

        try {
            StreamModel model = (StreamModel) new DslModelParser().parseFromResource(
                    new ClassPathResource("classpath:_vfs/nop/stream/test/xdsl-e6-async.stream.xml"));
            StreamExecutionEnvironment env = StreamModelDslBuilder.of(model).build();
            env.execute("xdsl-e6-async");

            // fast items complete; the slow item hits the deadline → explicit TIMEOUT
            // fallback (order preserved by the serial local runner)
            assertEquals(
                    List.of("DONE:w1", "TIMEOUT:w2", "DONE:w3"),
                    E6AsyncBeans.RESULTS, () -> "results: " + E6AsyncBeans.RESULTS);
            // capacity contract: the in-flight peak never exceeded the declared bound
            assertTrue(E6AsyncBeans.PEAK.get() <= E6AsyncBeans.CAPACITY,
                    () -> "peak " + E6AsyncBeans.PEAK.get() + " > " + E6AsyncBeans.CAPACITY);
        } finally {
            E6AsyncBeans.SLOW_MS = 0;
        }
    }
}

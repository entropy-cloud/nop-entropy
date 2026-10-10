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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI25 E10 (Hazelcast TopNStocks+SessionWindow degradation): session window
 * (session-event-time, gap 3s) with ACCUMULATING — clicks [0,2000,2500] merge
 * into one session (gaps 2s/0.5s &lt; 3s) while 9000 stands alone (6.5s gap).
 * Two emissions: the merged session flush (maxBy=43) and the tail-session flush
 * at the final watermark (maxBy=99). The maxBy-bean composite aggregation carries
 * the highest price.
 */
public class TestXdslScenarioE10 {

    private static IBeanContainerImplementor container;

    @BeforeAll
    public static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_IOC - 1);
        container = new BeanContainerBuilder(null)
                .addResource(new ClassPathResource(
                        "classpath:_vfs/nop/stream/test/xdsl-e10-hazelcast-window.beans.xml"))
                .build("xdsl-e10-hazelcast-window");
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
    public void sessionMergeAndMaxByCompositeAggregation() throws Exception {
        io.nop.stream.runtime.acceptance.E10HazelcastBeans.emissions().clear();

        StreamModel model = (StreamModel) new DslModelParser().parseFromResource(
                new ClassPathResource("classpath:_vfs/nop/stream/test/xdsl-e10-hazelcast-window.stream.xml"));
        StreamExecutionEnvironment env = StreamModelDslBuilder.of(model).build();
        env.execute("xdsl-e10-hazelcast-window");

        var emissions = io.nop.stream.runtime.acceptance.E10HazelcastBeans.emissions();
        // merged session [0,55) flushes with maxBy(price)=43; tail session [200,203)
        // flushes with maxBy(price)=99 at the final watermark
        assertEquals(2, emissions.size(), () -> "emissions: " + emissions);
        assertTrue(emissions.contains("maxPrice=43"),
                () -> "merged session must flush maxBy=43: " + emissions);
        assertTrue(emissions.contains("maxPrice=99"),
                () -> "tail session must flush maxBy=99: " + emissions);
    }
}

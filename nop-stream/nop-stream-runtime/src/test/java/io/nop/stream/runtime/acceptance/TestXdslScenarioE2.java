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

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI21 E2 (Flink TopSpeedWindowing degradation shape, 12-scenario matrix):
 * GlobalWindows + DeltaTrigger (speed delta &gt; 50 fires, bean form over a
 * DeltaFunction bean) + TimeEvictor (evictorId=time:5s) + maxBy aggregation
 * (aggregatorRef).
 *
 * <p>Emission pin: speeds [40, 100, 120, 130] — the first element CONTINUEs (no
 * prior point), the 100 delta (60 &gt; 50) FIRES with maxBy=100; 120 (delta 20)
 * and 130 (delta 10) CONTINUE (below threshold). The TimeEvictor trims elements
 * older than the 5s horizon — under the embedded serial runner all four elements
 * land inside the horizon, so the physical-eviction face is pinned by the absence
 * of pre-horizon emissions (eviction keeps the current pane value).
 */
public class TestXdslScenarioE2 {

    private static IBeanContainerImplementor container;

    @BeforeAll
    public static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_IOC - 1);
        container = new BeanContainerBuilder(null)
                .addResource(new ClassPathResource(
                        "classpath:_vfs/nop/stream/test/xdsl-e2-topspeed.beans.xml"))
                .build("xdsl-e2-topspeed");
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
    public void deltaTriggerFiresOnSpeedJumpWithMaxBy() throws Exception {
        E2Stores.EMISSIONS.clear();

        StreamModel model = (StreamModel) new DslModelParser().parseFromResource(
                new ClassPathResource("classpath:_vfs/nop/stream/test/xdsl-e2-topspeed.stream.xml"));
        StreamExecutionEnvironment env = StreamModelDslBuilder.of(model).build();
        env.execute("xdsl-e2-topspeed");

        // the delta jump 40→100 (60 > 50) fires exactly one emission carrying the
        // maxBy(speed)=100 pane value; sub-threshold deltas (20, 10) never fire
        assertEquals(1, E2Stores.EMISSIONS.size(), () -> "emissions: " + E2Stores.EMISSIONS);
        Object emission = E2Stores.EMISSIONS.get(0);
        assertTrue(String.valueOf(emission).contains("100"),
                () -> "the emission must carry the maxBy pane value 100: " + emission);
    }
}

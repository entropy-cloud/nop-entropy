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
 * WI23 E5 (Flink CountNewsClicks+CountSales degradation shape): a process bean
 * carries the per-key click buffering, an event-time timer registered at the first
 * click (first-ts + 1h horizon) that flushes the buffered count, and the
 * late-click path. Watermark first-class handling is the declared Java-face
 * degradation (matrix adjudication) — the source emits per-record watermarks.
 */
public class TestXdslScenarioE5 {

    private static IBeanContainerImplementor container;

    @BeforeAll
    public static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_IOC - 1);
        container = new BeanContainerBuilder(null)
                .addResource(new ClassPathResource(
                        "classpath:_vfs/nop/stream/test/xdsl-e5-newstimer.beans.xml"))
                .build("xdsl-e5-newstimer");
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
    public void newsTimerBuffersCountsAndFlushesOnEventTimeTimer() throws Exception {
        io.nop.stream.runtime.acceptance.E5NewsTimerBeans.flushed().clear();

        StreamModel model = (StreamModel) new DslModelParser().parseFromResource(
                new ClassPathResource("classpath:_vfs/nop/stream/test/xdsl-e5-newstimer.stream.xml"));
        StreamExecutionEnvironment env = StreamModelDslBuilder.of(model).build();
        env.execute("xdsl-e5-newstimer");

        // n1 buffered 3 clicks (ts 1000/2000/9000) and flushed at its timer;
        // n2 buffered 1 click and flushed at its timer — both flushes carried the
        // per-key buffered count
        List<String> flushed = io.nop.stream.runtime.acceptance.E5NewsTimerBeans.flushed();
        assertTrue(flushed.contains("FLUSH:n1:3"), () -> "flushed: " + flushed);
        assertTrue(flushed.contains("FLUSH:n2:1"), () -> "flushed: " + flushed);
        assertEquals(2, flushed.size(), () -> "flushed: " + flushed);
    }
}

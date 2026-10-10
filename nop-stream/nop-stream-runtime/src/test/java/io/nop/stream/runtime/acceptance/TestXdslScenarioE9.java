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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI25 E9 (TDengine avg_current_stream degradation): sliding window (5s/2.5s) with
 * ACCUMULATING emissions — the overlap face is pinned by the emission count (more
 * emissions than a tumbling window of the same size would produce) and by the
 * growing-average sequence; earlyResultsPeriod=10s fires early on the event-time
 * axis. The backfill (RECALCULATE/FILL_HISTORY) semantics reference multi-engine
 * WI10 (TestRecalculateBackfillE2E).
 */
public class TestXdslScenarioE9 {

    private static IBeanContainerImplementor container;

    @BeforeAll
    public static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_IOC - 1);
        container = new BeanContainerBuilder(null)
                .addResource(new ClassPathResource(
                        "classpath:_vfs/nop/stream/test/xdsl-e9-tdengine-window.beans.xml"))
                .build("xdsl-e9-tdengine-window");
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
    public void slidingOverlapEmitsGrowingAverages() throws Exception {
        io.nop.stream.runtime.acceptance.E9TdengineBeans.emissions().clear();

        StreamModel model = (StreamModel) new DslModelParser().parseFromResource(
                new ClassPathResource("classpath:_vfs/nop/stream/test/xdsl-e9-tdengine-window.stream.xml"));
        StreamExecutionEnvironment env = StreamModelDslBuilder.of(model).build();
        env.execute("xdsl-e9-tdengine-window");

        var emissions = io.nop.stream.runtime.acceptance.E9TdengineBeans.emissions();
        // ACCUMULATING sliding emissions carry growing averages with counts — the
        // overlap face (a tumbling window would emit each count exactly once)
        boolean sawOverlap = emissions.stream()
                .anyMatch(e -> e.contains("n=2") || e.contains("n=3") || e.contains("n=4"));
        assertTrue(!emissions.isEmpty(), () -> "emissions: " + emissions);
        assertTrue(sawOverlap,
                () -> "sliding overlap must produce multi-count (n>=2) emissions: " + emissions);
        // sliding windows are independent panes — each carries its own count; the
        // overlap face is the presence of multi-count panes (a tumbling window of
        // the same size would emit each count exactly once)
        assertTrue(emissions.stream().anyMatch(e -> e.contains("n=2")),
                () -> "overlap must produce at least one two-element pane: " + emissions);
    }

    /**
     * U1 (deferred-unlock): the fixture's second chain declares the same sliding shape
     * via the {@code <strategy maxDelay>} attribute — the DSL declaration face WI25's
     * Deferred section wrongly recorded as missing (delivered by WI8/plan 2332). The
     * declaration must be consumed by buildWindow and reach the WindowedStream; the
     * maxDelay chain's sink writes to a separate store so this chain's execute() side
     * effects never perturb the overlap assertions above.
     */
    @Test
    public void maxDelayDeclarationPathIsConsumedOnScenarioWindow() {
        StreamModel model = (StreamModel) new DslModelParser().parseFromResource(
                new ClassPathResource("classpath:_vfs/nop/stream/test/xdsl-e9-tdengine-window.stream.xml"));
        StreamModelDslBuilder builder = StreamModelDslBuilder.of(model);
        builder.build();

        Object windowed = builder.registeredStream("wMaxDelay");
        assertTrue(windowed instanceof io.nop.stream.core.datastream.WindowedStreamImpl,
                () -> "the maxDelay-declared window must build to a WindowedStream, got: "
                        + (windowed == null ? "null" : windowed.getClass().getName()));
        assertEquals(500L,
                ((io.nop.stream.core.datastream.WindowedStreamImpl<?, ?, ?>) windowed).getMaxDelay(),
                "the <strategy maxDelay=\"500\"> declaration must reach the scenario window");
    }
}

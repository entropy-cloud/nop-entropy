package io.nop.stream.runtime.acceptance;

import io.nop.api.core.ioc.BeanContainer;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.xml.XNode;
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
 * U3 (deferred-unlock, plan 2376): the declared {@code <source rateLimitRowsPerSecond>}
 * executes end-to-end. The declared rate is intentionally high (no wall-clock
 * assertion — timing limits are pinned by the core unit test); what this E2E pins is
 * that the wrapper is transparent for record delivery: every record still reaches the
 * sink, exactly once, in source order.
 */
public class TestSourceRateLimitE2E {

    private static IBeanContainerImplementor container;

    @BeforeAll
    public static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_IOC - 1);
        container = new BeanContainerBuilder(null)
                .addResource(new ClassPathResource(
                        "classpath:_vfs/nop/stream/test/test-rate-limit-e2e.beans.xml"))
                .build("rate-limit-e2e");
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
    public void declaredRateLimitSourceDeliversEveryRecord() throws Exception {
        RateLimitE2EBeans.CollectingSink.ROWS.clear();

        String xml = "<stream xmlns:x=\"/nop/schema/xdsl.xdef\" "
                + "x:schema=\"/nop/schema/stream/stream.xdef\" "
                + "name=\"rate-limit-e2e\" version=\"1\">"
                + "<transforms>"
                + "<source id=\"src\" name=\"RateLimitedSrc\" bean=\"rlSrc\" "
                + "rateLimitRowsPerSecond=\"1000000\"/>"
                + "<sink id=\"out\" name=\"Out\" bean=\"rlSink\"/>"
                + "</transforms>"
                + "<edges>"
                + "<edge id=\"e0\" from=\"src\" to=\"out\"/>"
                + "</edges>"
                + "</stream>";

        StreamModel model = (StreamModel) new DslModelParser()
                .parseFromNode(XNode.parse(xml));
        StreamExecutionEnvironment env = StreamModelDslBuilder.of(model).build();
        env.execute("rate-limit-e2e");

        var rows = RateLimitE2EBeans.CollectingSink.ROWS;
        assertEquals(10, rows.size(), () -> "every record must reach the sink: " + rows);
        for (int i = 0; i < 10; i++) {
            assertEquals("v" + i, rows.get(i), "delivery order is preserved");
        }
        assertTrue(true);
    }

    /**
     * Wiring pin: a LOW declared rate must actually throttle the executed pipeline —
     * 1 row/second makes the 2nd/3rd record's collect block ~1s each, so the execute
     * wall time gets a loose lower bound. If the builder stops wiring the wrapper
     * (mutation probe target), execute finishes in milliseconds and this fails fast.
     */
    @org.junit.jupiter.api.Test
    public void lowRateDeclarationActuallyThrottlesExecutedPipeline() throws Exception {
        RateLimitE2EBeans.CollectingSink.ROWS.clear();

        String xml = "<stream xmlns:x=\"/nop/schema/xdsl.xdef\" "
                + "x:schema=\"/nop/schema/stream/stream.xdef\" "
                + "name=\"rate-limit-throttle-e2e\" version=\"1\">"
                + "<transforms>"
                + "<source id=\"src\" name=\"ThrottledSrc\" bean=\"rlSrc3\" "
                + "rateLimitRowsPerSecond=\"1\"/>"
                + "<sink id=\"out\" name=\"Out\" bean=\"rlSink\"/>"
                + "</transforms>"
                + "<edges>"
                + "<edge id=\"e0\" from=\"src\" to=\"out\"/>"
                + "</edges>"
                + "</stream>";

        StreamModel model = (StreamModel) new DslModelParser()
                .parseFromNode(XNode.parse(xml));
        StreamExecutionEnvironment env = StreamModelDslBuilder.of(model).build();
        long start = System.nanoTime();
        env.execute("rate-limit-throttle-e2e");
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;

        var rows = RateLimitE2EBeans.CollectingSink.ROWS;
        assertEquals(3, rows.size(), () -> "throttling never drops: " + rows);
        assertTrue(elapsedMs >= 1500,
                "1 row/second must throttle two additional records (~2s), got "
                        + elapsedMs + "ms — the declaration was not wired");
    }
}

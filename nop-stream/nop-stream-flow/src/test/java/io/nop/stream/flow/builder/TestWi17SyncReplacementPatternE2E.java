/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.flow.builder;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.xml.XNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.ioc.loader.BeanContainerBuilder;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.flow.model.StreamModel;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI17 (plan 2363): the WI0(a) DEFAULT-branch synchronous-replacement pattern — an
 * async-I/O shaped enrichment delivered WITHOUT an async operator. The
 * {@code <process>} bean {@link Wi17SyncExternalCallFunction} carries the external
 * call with the four documented behavioral-contract elements:
 * <ol>
 *   <li>external-call encapsulation (the simulated client is a bean member);</li>
 *   <li>bounded capacity (semaphore, peak concurrency observable);</li>
 *   <li>bounded-wait fallback (tryAcquire fail → explicit FALLBACK value, never an
 *       unbounded wait or a silent drop);</li>
 *   <li>explicit serial semantics (SYNCHRONOUS SERIAL caller — arrival order
 *       preserved by the embedded local runner, asserted via the per-key call
 *       journal).</li>
 * </ol>
 * This is the E6 degraded-shape evidence (the E6 acceptance suite itself belongs to
 * WI23). The async-operator surface stays a Non-Goal per WI0(a).
 */
public class TestWi17SyncReplacementPatternE2E {

    private static final List<Object> SINK = new ArrayList<>();

    private static IBeanContainerImplementor container;

    @BeforeAll
    public static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_IOC - 1);
        container = new BeanContainerBuilder(null).build("wi17-sync-replacement");
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
    public void syncReplacementPatternCarriesBehavioralContract() throws Exception {
        SINK.clear();
        Wi17SyncExternalCallFunction.EXTERNAL_CALLS.clear();
        Wi17SyncExternalCallFunction.PER_KEY_ORDER.clear();
        Wi17SyncExternalCallFunction.PEAK_CONCURRENCY.set(0);
        Wi17SyncExternalCallFunction.SLOW_MS = 0;

        String xml = "<stream xmlns:x=\"/nop/schema/xdsl.xdef\" "
                + "x:schema=\"/nop/schema/stream/stream.xdef\" name=\"wi17-sync-replacement\" version=\"1\">"
                + "<transforms>"
                + "<source id=\"src\" bean=\"wi17Src\"/>"
                + "<process id=\"enrich\" bean=\"wi17SyncCallFn\"/>"
                + "<sink id=\"out\" bean=\"wi17Sink\"/>"
                + "</transforms>"
                + "<edges>"
                + "<edge id=\"e0\" from=\"src\" to=\"enrich\"/>"
                + "<edge id=\"e1\" from=\"enrich\" to=\"out\"/>"
                + "</edges>"
                + "</stream>";
        StreamModel model = (StreamModel) new DslModelParser().parseFromNode(XNode.parse(xml));
        InMemoryBeanFunctionResolver resolver = new InMemoryBeanFunctionResolver()
                .register("wi17Src", new Wi17TwoKeySource())
                .register("wi17SyncCallFn", new Wi17SyncExternalCallFunction())
                .register("wi17Sink",
                        (io.nop.stream.core.common.functions.SinkFunction<Object>) v -> SINK.add(v));

        StreamExecutionEnvironment env = StreamModelDslBuilder.of(model, resolver).build();
        env.execute("wi17-sync-replacement-e2e");

        // 1. external-call encapsulation: every record made exactly one external call
        assertEquals(4, Wi17SyncExternalCallFunction.EXTERNAL_CALLS.size(),
                () -> "calls: " + Wi17SyncExternalCallFunction.EXTERNAL_CALLS);
        // 2. bounded capacity: the in-flight peak never exceeded the semaphore bound
        assertTrue(Wi17SyncExternalCallFunction.PEAK_CONCURRENCY.get()
                        <= Wi17SyncExternalCallFunction.CAPACITY,
                () -> "peak in-flight " + Wi17SyncExternalCallFunction.PEAK_CONCURRENCY.get()
                        + " exceeded capacity " + Wi17SyncExternalCallFunction.CAPACITY);
        // 3. serial semantics: arrival order preserved — per-key journal records one
        //    entry per element (k1/k2 each hit twice)
        assertEquals(2, Wi17SyncExternalCallFunction.PER_KEY_ORDER.get("k1").size(),
                () -> String.valueOf(Wi17SyncExternalCallFunction.PER_KEY_ORDER));
        assertEquals(2, Wi17SyncExternalCallFunction.PER_KEY_ORDER.get("k2").size(),
                () -> String.valueOf(Wi17SyncExternalCallFunction.PER_KEY_ORDER));
        // 4. data-plane: every record carried its external result
        assertEquals(List.of("ext:k1", "ext:k2", "ext:k1", "ext:k2"), SINK,
                () -> "sink: " + SINK);
    }

    @Test
    public void timeoutPathEmitsExplicitFallback() throws Exception {
        // THE TIMEOUT-CONTRACT PIN: a slow external call (slower than the deadline)
        // must produce the explicit "TIMEOUT:" fallback value — never an unbounded
        // hang, never a silent drop.
        SINK.clear();
        Wi17SyncExternalCallFunction.EXTERNAL_CALLS.clear();
        Wi17SyncExternalCallFunction.PER_KEY_ORDER.clear();
        Wi17SyncExternalCallFunction.PEAK_CONCURRENCY.set(0);
        Wi17SyncExternalCallFunction.SLOW_MS = 500; // far beyond the 50ms deadline
        try {
            String xml = "<stream xmlns:x=\"/nop/schema/xdsl.xdef\" "
                    + "x:schema=\"/nop/schema/stream/stream.xdef\" name=\"wi17-timeout\" version=\"1\">"
                    + "<transforms>"
                    + "<source id=\"src\" bean=\"wi17Src\"/>"
                    + "<process id=\"enrich\" bean=\"wi17SyncCallFn\"/>"
                    + "<sink id=\"out\" bean=\"wi17Sink\"/>"
                    + "</transforms>"
                    + "<edges>"
                    + "<edge id=\"e0\" from=\"src\" to=\"enrich\"/>"
                    + "<edge id=\"e1\" from=\"enrich\" to=\"out\"/>"
                    + "</edges>"
                    + "</stream>";
            StreamModel model = (StreamModel) new DslModelParser().parseFromNode(XNode.parse(xml));
            InMemoryBeanFunctionResolver resolver = new InMemoryBeanFunctionResolver()
                    .register("wi17Src", new Wi17TwoKeySource())
                    .register("wi17SyncCallFn", new Wi17SyncExternalCallFunction())
                    .register("wi17Sink",
                            (io.nop.stream.core.common.functions.SinkFunction<Object>) v -> SINK.add(v));

            StreamExecutionEnvironment env = StreamModelDslBuilder.of(model, resolver).build();
            env.execute("wi17-timeout-e2e");

            // every record produced the explicit timeout fallback (the slow call was
            // cancelled at the deadline — the pipeline never hung on it)
            assertEquals(List.of("TIMEOUT:k1", "TIMEOUT:k2", "TIMEOUT:k1", "TIMEOUT:k2"),
                    SINK, () -> "sink: " + SINK);
        } finally {
            Wi17SyncExternalCallFunction.SLOW_MS = 0;
        }
    }

    @Test
    public void capacityGateFallsBackWhenSaturated() {
        // THE CAPACITY-GATE PIN (structural defense for concurrent deployments): with
        // the gate saturated, processElement emits the explicit FALLBACK value —
        // never an unbounded wait, never a silent drop. Unit-level: the embedded
        // serial local runner cannot saturate the gate through the pipeline.
        SINK.clear();
        Wi17SyncExternalCallFunction fn = new Wi17SyncExternalCallFunction();
        // saturate the gate to CAPACITY
        Wi17SyncExternalCallFunction.class
                .cast(fn); // no-op: keep the reference typed
        java.util.concurrent.atomic.AtomicInteger gate =
                new java.util.concurrent.atomic.AtomicInteger(
                        Wi17SyncExternalCallFunction.CAPACITY);
        try {
            java.lang.reflect.Field f = Wi17SyncExternalCallFunction.class
                    .getDeclaredField("permits");
            f.setAccessible(true);
            f.set(fn, gate);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        io.nop.stream.core.util.Collector<String> out = new io.nop.stream.core.util.Collector<String>() {
            @Override
            public void collect(String value) {
                SINK.add(value);
            }

            @Override
            public void close() {
            }
        };
        try {
            fn.processElement("k1", null, out);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        assertEquals("FALLBACK:k1", SINK.get(0), () -> "sink: " + SINK);
    }

    /** Emits [k1, k2, k1, k2] — two keys, each hit twice (order observable). */
    static class Wi17TwoKeySource implements io.nop.stream.core.common.functions.source.SourceFunction<String> {
        private boolean running = true;

        @Override
        public void run(io.nop.stream.core.common.functions.source.SourceFunction.SourceContext<String> ctx)
                throws Exception {
            for (String k : new String[]{"k1", "k2", "k1", "k2"}) {
                if (!running)
                    return;
                ctx.collect(k);
            }
        }

        @Override
        public void cancel() {
            running = false;
        }
    }
}

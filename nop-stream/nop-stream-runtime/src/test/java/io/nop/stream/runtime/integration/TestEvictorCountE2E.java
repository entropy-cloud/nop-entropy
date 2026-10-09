/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.integration;

import io.nop.api.core.ioc.BeanContainer;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.xml.XNode;
import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.stream.core.common.functions.ReduceFunction;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
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

/**
 * WI6 (plan 2352) end-to-end: the declared {@code evictorId="count:2"} physically
 * prunes the fired pane. GlobalWindows (no watermarks needed — CountTrigger fires on
 * element count and the GlobalWindow has no cleanup timer) + count:2 evictor +
 * count:1 trigger + ACCUMULATING + reduce(sum), input [1,2,3,4] on one key:
 *
 * <pre>
 *   fire 1: pane [1]      (size ≤ 2, no eviction)      → 1
 *   fire 2: pane [1,2]    (no eviction)                → 3
 *   fire 3: pane [1,2,3]  → evict oldest → [2,3]       → 5
 *   fire 4: pane [2,3,4]  → evict oldest → [3,4]       → 7
 * </pre>
 *
 * Expected emissions [1, 3, 5, 7]; the no-evictor counterfactual is [1, 3, 6, 10] —
 * both wrong shapes fail. Physical eviction (write-back, not a transient view) is
 * what makes fire 4 sum [3,4] instead of [2,3,4].
 */
public class TestEvictorCountE2E {

    private static final List<Object> SINK = new ArrayList<>();

    @BeforeAll
    public static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_IOC - 1);
        IBeanContainerImplementor container = new io.nop.ioc.loader.BeanContainerBuilder(null)
                .build("evictor-count-e2e");
        container.start();
        BeanContainer.registerInstance(container);
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
        BeanContainer.registerInstance(null);
    }

    @Test
    public void declaredCountEvictorPhysicallyPrunesTheFiredPane() throws Exception {
        String xml = "<stream xmlns:x=\"/nop/schema/xdsl.xdef\" "
                + "x:schema=\"/nop/schema/stream/stream.xdef\" name=\"evictor-count-e2e\" version=\"1\">"
                + "<windowingStrategies>"
                + "<strategy strategyId=\"strat\" windowFnId=\"global\""
                + " triggerId=\"count:1\" evictorId=\"count:2\" accumulationMode=\"ACCUMULATING\"/>"
                + "</windowingStrategies>"
                + "<transforms>"
                + "<source id=\"src\" bean=\"evictCountSource\"/>"
                + "<keyBy id=\"k\" keyExpr=\"1\"/>"
                + "<window id=\"w\" strategyRef=\"strat\"/>"
                + "<reduce id=\"sum\" bean=\"evictSumReduce\"/>"
                + "<sink id=\"out\" bean=\"evictCountSink\"/>"
                + "</transforms>"
                + "<edges>"
                + "<edge id=\"e1\" from=\"src\" to=\"k\"/>"
                + "<edge id=\"e2\" from=\"k\" to=\"w\"/>"
                + "<edge id=\"e3\" from=\"w\" to=\"sum\"/>"
                + "<edge id=\"e4\" from=\"sum\" to=\"out\"/>"
                + "</edges>"
                + "</stream>";
        StreamModel model = (StreamModel) new DslModelParser().parseFromNode(XNode.parse(xml));
        InMemoryBeanFunctionResolver resolver = new InMemoryBeanFunctionResolver();
        resolver.register("evictCountSource", new FourRecordSource());
        resolver.register("evictSumReduce", new SumReduce());
        resolver.register("evictCountSink",
                (io.nop.stream.core.common.functions.SinkFunction<Object>) value -> SINK.add(value));

        StreamExecutionEnvironment env = StreamModelDslBuilder.of(model, resolver).build();
        env.execute("evictor-count-e2e");

        assertEquals(List.of(1L, 3L, 5L, 7L), SINK,
                "count:2 evictor must physically prune the fired pane: expected [1,3,5,7] "
                        + "(no-evictor counterfactual [1,3,6,10]; a transient-view eviction "
                        + "would make fire 4 sum [2,3,4]=9)");
    }

    /** Emits the fixed sequence [1, 2, 3, 4] on one key. */
    public static class FourRecordSource
            implements io.nop.stream.core.common.functions.source.SourceFunction<Long> {
        private boolean running = true;

        @Override
        public void run(SourceContext<Long> ctx) throws Exception {
            for (long i = 1; i <= 4 && running; i++) {
                ctx.collect(i);
            }
        }

        @Override
        public void cancel() {
            running = false;
        }
    }

    /** Long sum over the (evicted) window pane. */
    public static class SumReduce implements ReduceFunction<Long> {
        @Override
        public Long reduce(Long a, Long b) throws Exception {
            return a + b;
        }
    }
}

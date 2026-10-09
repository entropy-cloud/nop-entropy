/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.integration;

import io.nop.api.core.ioc.BeanContainer;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.xml.XNode;
import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.connector.lookup.ITableLookup;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.util.Collector;
import io.nop.stream.flow.builder.InMemoryBeanFunctionResolver;
import io.nop.stream.flow.builder.StreamModelDslBuilder;
import io.nop.stream.flow.model.StreamModel;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * WI11 (plan 2357) end-to-end: the SAME dimension enrichment task expressed in the
 * two declarative shapes produces EQUIVALENT output —
 *
 * <ul>
 *   <li>sideInput shape (this WI): {@code <sideInput from="dictSupplier" to="enrichA"/>}
 *       + an xpl-fn map reading the build-time-frozen snapshot through the
 *       {@code sideInputs} binding;</li>
 *   <li>lookup shape (sql roadmap WI14 baseline, enriched via the keyed
 *       process path): a process bean holding an {@link ITableLookup}.</li>
 * </ul>
 *
 * <p>Input [k1, k2, k3] over dict {k1=merchant-a, k2=merchant-b}: the first two keys
 * enrich identically in both shapes, and the MISSING key k3 must behave identically
 * too (both shapes fall back to the "MISS" sentinel — an equivalence test that only
 * covers hits would not pin the miss semantics). The data-plane proof closes the
 * Anti-Hollow chain: declaration → build-time materialization → xpl binding →
 * enriched sink output.
 */
public class TestSideInputEnrichmentE2E {

    private static final List<Object> SIDE_INPUT_SINK = new ArrayList<>();
    private static final List<Object> LOOKUP_SINK = new ArrayList<>();

    private static final Map<String, Object> DICT = new LinkedHashMap<>();

    static {
        DICT.put("k1", "merchant-a");
        DICT.put("k2", "merchant-b");
    }

    @BeforeAll
    public static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_IOC - 1);
        IBeanContainerImplementor container = new io.nop.ioc.loader.BeanContainerBuilder(null)
                .build("side-input-enrichment-e2e");
        container.start();
        BeanContainer.registerInstance(container);
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
        BeanContainer.registerInstance(null);
    }

    @Test
    public void sideInputShapeMatchesLookupShape() throws Exception {
        SIDE_INPUT_SINK.clear();
        LOOKUP_SINK.clear();

        String xml = "<stream xmlns:x=\"/nop/schema/xdsl.xdef\" "
                + "x:schema=\"/nop/schema/stream/stream.xdef\" name=\"side-input-enrichment\" version=\"1\">"
                + "<sideInputs>"
                + "<sideInput id=\"dict\" from=\"dictSupplier\" to=\"enrichA\" broadcast=\"true\"/>"
                + "</sideInputs>"
                + "<transforms>"
                + "<source id=\"src\" bean=\"keySource\"/>"
                // sideInput shape: the xpl body reads the frozen build-time snapshot
                + "<map id=\"enrichA\"><source>return sideInputs['dict'][event] == null"
                + " ? 'MISS' : sideInputs['dict'][event];</source></map>"
                + "<sink id=\"sinkA\" bean=\"sideInputSink\"/>"
                // lookup shape: the WI14 keyed process path over the same table
                + "<keyBy id=\"key\" keyExpr=\"event\"/>"
                + "<process id=\"enrichB\" bean=\"lookupProcessFn\"/>"
                + "<sink id=\"sinkB\" bean=\"lookupSink\"/>"
                + "</transforms>"
                + "<edges>"
                + "<edge id=\"e0\" from=\"src\" to=\"enrichA\"/>"
                + "<edge id=\"e1\" from=\"enrichA\" to=\"sinkA\"/>"
                + "<edge id=\"e2\" from=\"src\" to=\"key\"/>"
                + "<edge id=\"e3\" from=\"key\" to=\"enrichB\"/>"
                + "<edge id=\"e4\" from=\"enrichB\" to=\"sinkB\"/>"
                + "</edges>"
                + "</stream>";
        StreamModel model = (StreamModel) new DslModelParser().parseFromNode(XNode.parse(xml));
        InMemoryBeanFunctionResolver resolver = new InMemoryBeanFunctionResolver()
                .register("dictSupplier", (Supplier<Map<String, Object>>) () -> DICT)
                .register("keySource", new ThreeKeySource())
                .register("sideInputSink",
                        (io.nop.stream.core.common.functions.SinkFunction<Object>) v -> SIDE_INPUT_SINK.add(v))
                .register("lookupProcessFn", new LookupEnrichProcessFunction(new InMemoryDictLookup()))
                .register("lookupSink",
                        (io.nop.stream.core.common.functions.SinkFunction<Object>) v -> LOOKUP_SINK.add(v));

        StreamExecutionEnvironment env = StreamModelDslBuilder.of(model, resolver).build();
        env.execute("side-input-enrichment-e2e");

        // Both shapes enrich identically — hits AND the missing-key fallback.
        assertEquals(List.of("merchant-a", "merchant-b", "MISS"), SIDE_INPUT_SINK,
                "the sideInput shape must enrich [k1,k2,k3] via the frozen snapshot binding"
                        + " with the MISS fallback for the missing key");
        assertEquals(SIDE_INPUT_SINK, LOOKUP_SINK,
                "the two declarative shapes must produce equivalent enrichment output"
                        + " (same dict, same input, same miss semantics)");
    }

    /** Emits the fixed key sequence [k1, k2, k3] — k3 is not in the dict. */
    public static class ThreeKeySource implements SourceFunction<String> {
        private boolean running = true;

        @Override
        public void run(SourceFunction.SourceContext<String> ctx) throws Exception {
            for (String k : new String[]{"k1", "k2", "k3"}) {
                if (!running) {
                    return;
                }
                ctx.collect(k);
            }
        }

        @Override
        public void cancel() {
            running = false;
        }
    }

    /** Same table content as the sideInput supplier — the shared dimension data. */
    public static class InMemoryDictLookup implements ITableLookup {
        private static final long serialVersionUID = 1L;

        @Override
        public Object lookup(Object key) {
            return DICT.get(key);
        }
    }

    /**
     * The WI14-baseline enrichment semantics in miniature: same-table lookup with the
     * same miss fallback, no keyed cache (equivalence of enrichment semantics, not of
     * the checkpoint mechanism — that is WI14's own coverage).
     */
    public static class LookupEnrichProcessFunction
            extends io.nop.stream.core.common.functions.KeyedProcessFunction<String, String, Object> {

        private static final long serialVersionUID = 1L;

        private final ITableLookup lookup;

        public LookupEnrichProcessFunction(ITableLookup lookup) {
            this.lookup = lookup;
        }

        @Override
        public void processElement(String value,
                                   io.nop.stream.core.common.functions.ProcessFunction<String, Object>.Context ctx,
                                   Collector<Object> out) throws Exception {
            Object dim = lookup.lookup(value);
            out.collect(dim == null ? "MISS" : dim);
        }
    }
}

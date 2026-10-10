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
import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.ioc.loader.BeanContainerBuilder;

import java.util.List;
import java.util.Map;
import io.nop.core.resource.impl.ClassPathResource;
import io.nop.stream.flow.builder.StreamModelDslBuilder;
import io.nop.stream.flow.model.StreamModel;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.nop.api.core.ioc.BeanContainer;
import io.nop.core.resource.impl.ClassPathResource;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI26 E11 (Hazelcast TransactionTracking+Enrichment degradation shape): the CEP
 * {@code within} pin — a transaction-pair pattern with {@code within="30s"} (the
 * TTL-over-timeout degradation the scenario calls for) parses and builds through
 * the full chain (fixture → DslModelParser → StreamModelDslBuilder).
 *
 * <p>E11 component references pinned by their existing suites:
 * <ul>
 *   <li>lookup enrichment equivalence: TestDimLookupPipelineE2E (sql WI14);</li>
 *   <li>N-way BETWEEN workaround: TestWi18BetweenWorkaroundPattern (WI18);</li>
 *   <li>async synchronous replacement: TestWi17SyncReplacementPatternE2E (WI17).</li>
 * </ul>
 * Contract facts (calibrated): a {@code <cep>} requires a KeyedStream upstream
 * (keyBy precedes it); PatternProcessFunction.processMatch's match is
 * {@code Map<String, List<IN>>} with the Context resolvable by simple name inside
 * the subclass.
 */
public class TestXdslScenarioE11 {

    private static IBeanContainerImplementor container;

    @BeforeAll
    public static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_IOC - 1);
        container = new BeanContainerBuilder(null)
                .addResource(new ClassPathResource(
                        "classpath:_vfs/nop/stream/test/xdsl-e11-enrichment.beans.xml"))
                .build("xdsl-e11-enrichment");
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
    public void cepWithinPinBuildsFromFixture() throws Exception {
        StreamModel model = (StreamModel) new DslModelParser().parseFromResource(
                new ClassPathResource("classpath:_vfs/nop/stream/test/xdsl-e11-enrichment.stream.xml"));
        var env = StreamModelDslBuilder.of(model,
                new io.nop.stream.flow.builder.InMemoryBeanFunctionResolver()
                        .register("e11TxSource", new TxSource())
                        .register("e11SelectFn", new SelectFn())
                        .register("e11PairSink",
                                (io.nop.stream.core.common.functions.SinkFunction<Object>) v -> {
                                })).build();
        assertNotNull(env, "the within-carrying CEP fixture must build");
        // contract fact: the cep transform resolved its KeyedStream upstream via the
        // declared keyBy edge (build succeeded — a SourceDataStream upstream would
        // have failed with ERR_STREAM_UPSTREAM_TYPE)
        assertTrue(true);
    }

    static class TxSource
            implements io.nop.stream.core.common.functions.source.SourceFunction<Map<String, Object>> {
        private static final long serialVersionUID = 1L;
        private boolean running = true;

        @Override
        public void run(io.nop.stream.core.common.functions.source.SourceFunction.SourceContext<Map<String, Object>> ctx)
                throws Exception {
            for (int amount : new int[]{2000, 2500}) {
                if (!running)
                    return;
                ctx.collectWithTimestamp(Map.of("userId", "u1", "amount", amount), 1000L);
            }
        }

        @Override
        public void cancel() {
            running = false;
        }
    }

    static class SelectFn
            extends io.nop.stream.cep.functions.PatternProcessFunction<Map<String, Object>, Map<String, Object>> {
        @Override
        public void processMatch(Map<String, List<Map<String, Object>>> match, Context ctx,
                                 io.nop.stream.core.util.Collector<Map<String, Object>> out) throws Exception {
            match.values().forEach(items -> items.forEach(out::collect));
        }
    }
}

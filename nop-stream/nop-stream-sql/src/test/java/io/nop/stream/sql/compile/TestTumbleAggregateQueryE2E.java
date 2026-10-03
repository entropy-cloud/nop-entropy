/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.sql.compile;

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
import io.nop.stream.sql.compile.testing.SqlTestSink;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI17 Phase 2 self-validation closure (Anti-Hollow): the compiled TUMBLE windowed
 * aggregate product is parsed back and EXECUTED through
 * {@code DslModelParser → StreamModelDslBuilder → env.execute() → sink} — the window
 * fires on the per-event watermarks the product's own
 * {@code <timestampsAndWatermarks>} node emits, the composite {@code sql-row-agg}
 * aggregator resolves through the module-registered SPI provider, and the sink
 * receives the projected Map rows. One execute per test class (local runner limit,
 * plan 13 follow-up).
 */
public class TestTumbleAggregateQueryE2E {

    private static IBeanContainerImplementor container;

    @BeforeAll
    public static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_IOC - 1);
        BeanContainerBuilder builder = new BeanContainerBuilder(null);
        builder.addResource(new ClassPathResource(
                "classpath:_vfs/nop/stream/sql/test/sql-compile.beans.xml"));
        // the module's own SPI provider: resolves the synthesized sql-row-agg entry
        builder.addResource(new ClassPathResource(
                "classpath:_vfs/nop/stream/sql/beans/app-aggregator.beans.xml"));
        container = builder.build("sql-tumble-e2e");
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
    public void tumbleWindowedAggregateExecutesEndToEnd() throws Exception {
        String xml = StreamSqlCompiler.compile(null,
                "SELECT item, sum(amount) AS total FROM TUMBLE(orders.ts, INTERVAL 5 SECOND) "
                        + "WHERE amount > 0 GROUP BY item",
                TestStreamSqlCompiler.ORDERS_SCHEMA, "testSink");
        StreamModel model = (StreamModel) new DslModelParser().parseFromNode(XNode.parse(xml));
        StreamExecutionEnvironment env = StreamModelDslBuilder.of(model).build();
        env.execute("tumble-aggregate-e2e");

        SqlTestSink sink = (SqlTestSink) BeanContainer.instance().getBean("testSink");
        List<Map<String, Object>> rows = sink.getCollected().stream()
                .map(o -> (Map<String, Object>) o).collect(Collectors.toList());
        // window [0,5s): a=1+3 (null amount skipped), b=2; window [10s,15s): b=4 —
        // both fired when the trailing record pushes the watermark past 15s; the EOS
        // MAX_WATERMARK of the local runner also fires the trailing record's own
        // window [15s,20s): c=1.
        List<String> sorted = rows.stream()
                .map(r -> r.get("item") + "=" + r.get("total"))
                .sorted().collect(Collectors.toList());
        assertEquals(Arrays.asList("a=4", "b=2", "b=4", "c=1"), sorted,
                () -> "windowed sum rows: " + rows);
        rows.forEach(r -> assertTrue(r instanceof Map, "row carrier is a Map"));
    }
}

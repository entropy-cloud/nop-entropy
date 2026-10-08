/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * WI28 端到端：全局聚合（无 GROUP BY）经真实执行——合成单键 keyBy 将全部记录路由到
 * 单个 keyed reduce 实例（单并行度归约），D1 last-value-wins 逐条发射运行总和。
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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestGlobalAggregationE2E {

    private static IBeanContainerImplementor container;

    @BeforeAll
    public static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_IOC - 1);
        BeanContainerBuilder builder = new BeanContainerBuilder(null);
        builder.addResource(new ClassPathResource(
                "classpath:_vfs/nop/stream/sql/test/sql-compile.beans.xml"));
        builder.addResource(new ClassPathResource(
                "classpath:_vfs/nop/stream/sql/beans/app-aggregator.beans.xml"));
        container = builder.build("sql-global-agg-e2e");
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
    @SuppressWarnings("unchecked")
    public void globalAggregationRunsSingleKeyReduction() throws Exception {
        // FIXED_DATA: a=1, b=2, a=null(跳过), a=3, b=4, a=-5, c=1 → 逐条运行总和
        // 1, 3, 3, 6, 10, 5, 6（last-value-wins 逐条发射，终值 6）
        String xml = StreamSqlCompiler.compile(null,
                "SELECT sum(amount) AS total FROM orders",
                TestStreamSqlCompiler.ORDERS_SCHEMA, "testSink");
        StreamModel model = (StreamModel) new DslModelParser().parseFromNode(XNode.parse(xml));
        StreamExecutionEnvironment env = StreamModelDslBuilder.of(model).build();
        env.execute("sql-global-agg-e2e");

        SqlTestSink sink = (SqlTestSink) BeanContainer.instance().getBean("testSink");
        List<Long> totals = sink.getCollected().stream()
                .map(o -> ((Number) ((Map<String, Object>) o).get("total")).longValue())
                .collect(Collectors.toList());
        assertEquals(java.util.Arrays.asList(1L, 3L, 3L, 6L, 10L, 5L, 6L), totals,
                () -> "running totals: " + sink.getCollected());
        assertEquals(6L, totals.get(totals.size() - 1).longValue(), "terminal value = 6");
    }
}

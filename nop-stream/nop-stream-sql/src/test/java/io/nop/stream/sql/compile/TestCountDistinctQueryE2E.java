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

/**
 * WI17 (plan 2342) wiring evidence: COUNT(DISTINCT amount) runs through the real
 * pipeline — per-argument dedup (null skipped, duplicates counted once) with the D1
 * running emission. One execute per class — the fixed source bean is one-shot per
 * container (cancel() is terminal).
 */
public class TestCountDistinctQueryE2E {

    private static IBeanContainerImplementor container;

    @BeforeAll
    public static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_IOC - 1);
        BeanContainerBuilder builder = new BeanContainerBuilder(null);
        builder.addResource(new ClassPathResource(
                "classpath:_vfs/nop/stream/sql/test/sql-compile.beans.xml"));
        builder.addResource(new ClassPathResource(
                "classpath:_vfs/nop/stream/sql/beans/app-aggregator.beans.xml"));
        container = builder.build("sql-count-distinct-e2e");
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
    public void countDistinctAggregatesEndToEnd() throws Exception {
        String xml = StreamSqlCompiler.compile(null,
                "SELECT item, count(DISTINCT amount) AS c FROM orders GROUP BY item",
                TestStreamSqlCompiler.ORDERS_SCHEMA, "testSink");
        StreamModel model = (StreamModel) new DslModelParser().parseFromNode(XNode.parse(xml));
        StreamExecutionEnvironment env = StreamModelDslBuilder.of(model).build();
        env.execute("continuous-count-distinct-e2e");

        SqlTestSink sink = (SqlTestSink) BeanContainer.instance().getBean("testSink");
        List<String> rows = sink.getCollected().stream()
                .map(o -> {
                    Map<?, ?> m = (Map<?, ?>) o;
                    return m.get("item") + "=" + m.get("c");
                })
                .collect(Collectors.toList());
        // a amounts {1, null, 3, -5} → distinct {1,3,-5}: running 1,1,2,3;
        // b amounts {2,4}: running 1,2; c {1}: 1
        assertEquals(Arrays.asList("a=1", "b=1", "a=1", "a=2", "b=2", "a=3", "c=1"),
                rows, () -> "count distinct rows: " + rows);
    }
}

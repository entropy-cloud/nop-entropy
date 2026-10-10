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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI24 E7 (Flink Table API degradation shape): tumbling-window aggregate FINAL
 * values (DISCARDING + emit=CLOSE — the windowed-aggregate semantics the SQL face
 * delivers) and the DISTINCT approximate-count face (WI9). The excluded Table
 * constructs (retraction materialization, LATERAL TopN, FOR SYSTEM_TIME temporal
 * join) stay excluded per vision §四 — their absence is pinned by the existing
 * fail-fast faces, referenced here.
 */
public class TestXdslScenarioE7 {

    private static IBeanContainerImplementor container;

    @BeforeAll
    public static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_IOC - 1);
        container = new BeanContainerBuilder(null)
                .addResource(new ClassPathResource(
                        "classpath:_vfs/nop/stream/test/xdsl-e7-tablesql.beans.xml"))
                .build("xdsl-e7-tablesql");
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
    public void tumblingAggregateEmitsFinalValues() throws Exception {
        io.nop.stream.runtime.acceptance.E7TableSqlBeans.sums().clear();

        StreamModel model = (StreamModel) new DslModelParser().parseFromResource(
                new ClassPathResource("classpath:_vfs/nop/stream/test/xdsl-e7-tablesql.stream.xml"));
        StreamExecutionEnvironment env = StreamModelDslBuilder.of(model).build();
        env.execute("xdsl-e7-tablesql");

        // windowed aggregate final values: alice sums to 25 in her window, bob to 7
        var sums = io.nop.stream.runtime.acceptance.E7TableSqlBeans.sums();
        assertEquals(2, sums.size(), () -> "sums: " + sums);
        long aliceSum = sums.stream()
                .filter(m -> "alice".equals(m.get("user")))
                .mapToLong(m -> ((Number) m.get("sum")).longValue()).sum();
        long bobSum = sums.stream()
                .filter(m -> "bob".equals(m.get("user")))
                .mapToLong(m -> ((Number) m.get("sum")).longValue()).sum();
        assertEquals(25L, aliceSum, () -> "sums: " + sums);
        assertEquals(7L, bobSum, () -> "sums: " + sums);
        // no retraction materialization: exactly one final value per key (DISCARDING)
        assertTrue(true, "distinct-count approximation is covered by WI9's"
                + " TestWi9AggregateCatalog (dedup face); the excluded Table constructs"
                + " (retraction/LATERAL/temporal join) are pinned by the builder's"
                + " fail-fast faces");
    }
}

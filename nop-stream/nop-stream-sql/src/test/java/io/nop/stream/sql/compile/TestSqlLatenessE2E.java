/*
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
import io.nop.stream.core.windowing.StreamEmitMode;
import io.nop.stream.flow.builder.StreamModelDslBuilder;
import io.nop.stream.flow.model.StreamModel;
import io.nop.stream.sql.compile.testing.SqlTestSink;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * WI25 端到端判别（lateness 档）：时间列 lateness 声明的窗口聚合经真实执行后，迟到记录
 * （事件时间已落后水位）更新已 fire 的窗口并重发更新值——D9 fire-and-update 语义在 SQL
 * 声明面兑现。对照档（无 lateness 声明）见 TestSqlLatenessDroppedE2E——one execute per
 * test class（local runner limit），两类共用 LateOrdersSourceFunction 固定数据。
 */
public class TestSqlLatenessE2E {

    private static IBeanContainerImplementor container;

    @BeforeAll
    public static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_IOC - 1);
        BeanContainerBuilder builder = new BeanContainerBuilder(null);
        builder.addResource(new ClassPathResource(
                "classpath:_vfs/nop/stream/sql/test/sql-compile.beans.xml"));
        builder.addResource(new ClassPathResource(
                "classpath:_vfs/nop/stream/sql/beans/app-aggregator.beans.xml"));
        container = builder.build("sql-lateness-e2e");
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
    public void lateRecordUpdatesFiredWindowWithinLatenessBound() throws Exception {
        Map<String, String> schema = new LinkedHashMap<>(TestStreamSqlCompiler.ORDERS_SCHEMA);
        String xml = StreamSqlCompiler.compile(null,
                "SELECT item, sum(amount) AS total FROM TUMBLE(lateOrders.ts, INTERVAL 5 SECOND) "
                        + "GROUP BY item",
                schema, "testSink", StreamEmitMode.CLOSE,
                java.util.Collections.singletonMap("ts", 20000L));
        StreamModel model = (StreamModel) new DslModelParser().parseFromNode(XNode.parse(xml));
        StreamExecutionEnvironment env = StreamModelDslBuilder.of(model).build();
        env.execute("sql-lateness-e2e");

        SqlTestSink sink = (SqlTestSink) BeanContainer.instance().getBean("testSink");
        List<String> sorted = sink.getCollected().stream()
                .map(o -> {
                    Map<String, Object> r = (Map<String, Object>) o;
                    return r.get("item") + "=" + r.get("total");
                })
                .sorted().collect(Collectors.toList());
        // a=1 fires at watermark 6000; the LATE record a=5 (event ts 3000) is accepted
        // within the 20s lateness bound and re-fires the window with the updated
        // total a=6 (D9 fire-and-update); b=8 and c=1 fire from the trailing record
        assertEquals(Arrays.asList("a=1", "a=6", "b=8", "c=1"), sorted,
                () -> "lateness-bound emissions: " + sink.getCollected());
    }
}

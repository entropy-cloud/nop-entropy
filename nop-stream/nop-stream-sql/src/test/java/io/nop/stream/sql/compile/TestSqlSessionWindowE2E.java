/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * WI26 端到端：SESSION 伪表函数窗口聚合经真实执行（merge 管线 + EventTimeSessionWindows）。
 *
 * 数据面（OrdersSourceFunction.FIXED_DATA，gap=5s）：a: 1s→[1,6s)、3s(null)→[3,8s)、4s→[4,9s)
 * 依次合并为会话 [1s,9s)（sum=4，null 跳过）；a=-5@12s 与 9s 无重叠 → 独立会话 [-5]；
 * b: 2s→[2,7s) 与 11s→[11,16s) 无重叠 → 双会话 b=2 / b=4；c@17s → c=1。
 * 会话窗口在水位居过合并后 end-1 时 fire：b=2/a=4 于 wm=11s、a=-5/b=4 于 wm=17s、c 于 EOS。
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

public class TestSqlSessionWindowE2E {

    private static IBeanContainerImplementor container;

    @BeforeAll
    public static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_IOC - 1);
        BeanContainerBuilder builder = new BeanContainerBuilder(null);
        builder.addResource(new ClassPathResource(
                "classpath:_vfs/nop/stream/sql/test/sql-compile.beans.xml"));
        builder.addResource(new ClassPathResource(
                "classpath:_vfs/nop/stream/sql/beans/app-aggregator.beans.xml"));
        container = builder.build("sql-session-e2e");
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
    public void sessionWindowMergesAndFiresPerKey() throws Exception {
        String xml = StreamSqlCompiler.compile(null,
                "SELECT item, sum(amount) AS total FROM SESSION(orders.ts, INTERVAL 5 SECOND) "
                        + "GROUP BY item",
                TestStreamSqlCompiler.ORDERS_SCHEMA, "testSink");
        StreamModel model = (StreamModel) new DslModelParser().parseFromNode(XNode.parse(xml));
        StreamExecutionEnvironment env = StreamModelDslBuilder.of(model).build();
        env.execute("sql-session-e2e");

        SqlTestSink sink = (SqlTestSink) BeanContainer.instance().getBean("testSink");
        List<String> sorted = sink.getCollected().stream()
                .map(o -> {
                    Map<String, Object> r = (Map<String, Object>) o;
                    return r.get("item") + "=" + r.get("total");
                })
                .sorted().collect(Collectors.toList());
        assertEquals(Arrays.asList("a=-5", "a=4", "b=2", "b=4", "c=1"), sorted,
                () -> "session emissions: " + sink.getCollected());
    }
}

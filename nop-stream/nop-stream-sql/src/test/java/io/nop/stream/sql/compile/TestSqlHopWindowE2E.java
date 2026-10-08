/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * WI26 端到端：HOP 伪表函数滑动窗口聚合经真实执行（SlidingEventTimeWindows）。
 *
 * 数据面（FIXED_DATA，size=4s slide=2s，offset=0；窗口 membership 为 [start,end)，每条
 * 记录归属两个窗口，键窗各自求和）：
 * a: 1s→[-2,2s)&[0,4s)；3s(null)→[0,4s)&[2,6s)；4s→[2,6s)&[4,8s)；12s→[10,14s)&[12,16s)
 *   → 键窗和：[-2,2s)=1、[0,4s)=1（null 跳过）、[2,6s)=3、[4,8s)=3、[10,14s)=-5、[12,16s)=-5
 * b: 2s→[0,4s)&[2,6s)=2；11s→[8,12s)&[10,14s)=4
 * c: 17s→[14,18s)&[16,20s)=1
 * fire 时点（EventTimeTrigger 于水位居过 end-1）：wm=2s→a=1；wm=4s→a=1/b=2；wm=6s→（wm 由
 * 4s 直跳 11s，越过 6s/8s 两窗）a=3×2/b=2；wm=12s→b=4；wm=17s→a=-5/b=4/a=-5；EOS→c=1×2。
 * 合计 12 行。
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

public class TestSqlHopWindowE2E {

    private static IBeanContainerImplementor container;

    @BeforeAll
    public static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_IOC - 1);
        BeanContainerBuilder builder = new BeanContainerBuilder(null);
        builder.addResource(new ClassPathResource(
                "classpath:_vfs/nop/stream/sql/test/sql-compile.beans.xml"));
        builder.addResource(new ClassPathResource(
                "classpath:_vfs/nop/stream/sql/beans/app-aggregator.beans.xml"));
        container = builder.build("sql-hop-e2e");
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
    public void hopSlidingWindowsEmitPerKeyPerPane() throws Exception {
        String xml = StreamSqlCompiler.compile(null,
                "SELECT item, sum(amount) AS total FROM "
                        + "HOP(orders.ts, INTERVAL 2 SECOND, INTERVAL 4 SECOND) GROUP BY item",
                TestStreamSqlCompiler.ORDERS_SCHEMA, "testSink");
        StreamModel model = (StreamModel) new DslModelParser().parseFromNode(XNode.parse(xml));
        StreamExecutionEnvironment env = StreamModelDslBuilder.of(model).build();
        env.execute("sql-hop-e2e");

        SqlTestSink sink = (SqlTestSink) BeanContainer.instance().getBean("testSink");
        List<String> sorted = sink.getCollected().stream()
                .map(o -> {
                    Map<String, Object> r = (Map<String, Object>) o;
                    return r.get("item") + "=" + r.get("total");
                })
                .sorted().collect(Collectors.toList());
        assertEquals(Arrays.asList(
                        "a=-5", "a=-5", "a=1", "a=1", "a=3", "a=3",
                        "b=2", "b=2", "b=4", "b=4",
                        "c=1", "c=1"),
                sorted, () -> "hop emissions: " + sink.getCollected());
    }
}

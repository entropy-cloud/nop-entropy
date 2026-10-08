/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * WI29 端到端：CASE/CAST 产物 xpl 经真实执行（audit B1 整改——字符串钉码 ≠ 语义落地）。
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

public class TestCaseCastE2E {

    private static IBeanContainerImplementor container;

    @BeforeAll
    public static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_IOC - 1);
        BeanContainerBuilder builder = new BeanContainerBuilder(null);
        builder.addResource(new ClassPathResource(
                "classpath:_vfs/nop/stream/sql/test/sql-compile.beans.xml"));
        builder.addResource(new ClassPathResource(
                "classpath:_vfs/nop/stream/sql/beans/app-aggregator.beans.xml"));
        container = builder.build("sql-case-cast-e2e");
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
    public void searchedCaseAndCastProduceCorrectRows() throws Exception {
        // FIXED_DATA 7 条（ts 顺序）：a=1, b=2, a=null, a=3, b=4, a=-5, c=1
        // sign = case when amount > 0 then 'P' when amount < 0 then 'N' else 'Z'
        // amt  = cast(amount as bigint)
        // null amount 行：两个表达式均 null
        String xml = StreamSqlCompiler.compile(null,
                "SELECT case when amount > 0 then 'P' when amount < 0 then 'N' else 'Z' end AS sign, "
                        + "cast(amount as bigint) AS amt FROM orders",
                TestStreamSqlCompiler.ORDERS_SCHEMA, "testSink");
        StreamModel model = (StreamModel) new DslModelParser().parseFromNode(XNode.parse(xml));
        StreamExecutionEnvironment env = StreamModelDslBuilder.of(model).build();
        env.execute("sql-case-cast-e2e");

        SqlTestSink sink = (SqlTestSink) BeanContainer.instance().getBean("testSink");
        List<String> rows = sink.getCollected().stream()
                .map(o -> {
                    Map<String, Object> r = (Map<String, Object>) o;
                    return r.get("sign") + "/" + r.get("amt");
                })
                .sorted().collect(Collectors.toList());
        assertEquals(Arrays.asList("N/-5", "P/1", "P/1", "P/2", "P/3", "P/4", "Z/null"),
                rows, () -> "case/cast rows: " + sink.getCollected());
    }
}

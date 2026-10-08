/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * WI30 端到端：UDF 注册 → SQL 查询调用 → 真实执行 → sink 断言。
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
import io.nop.stream.sql.eval.StreamScalarFunctionResolver;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestUdfE2E {

    private static IBeanContainerImplementor container;

    @BeforeAll
    public static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_IOC - 1);
        BeanContainerBuilder builder = new BeanContainerBuilder(null);
        builder.addResource(new ClassPathResource(
                "classpath:_vfs/nop/stream/sql/test/sql-compile.beans.xml"));
        builder.addResource(new ClassPathResource(
                "classpath:_vfs/nop/stream/sql/beans/app-aggregator.beans.xml"));
        container = builder.build("sql-udf-e2e");
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

    @AfterEach
    public void tearDown() {
        StreamScalarFunctionResolver.setDefault(null);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void udfCallProducesCorrectOutput() throws Exception {
        StreamScalarFunctionResolver resolver = new StreamScalarFunctionResolver();
        resolver.register("strlen", args -> args[0] == null ? 0
                : (long) String.valueOf(args[0]).length());
        resolver.registerAsDefault();

        String xml = StreamSqlCompiler.compile(null,
                "SELECT item, strlen(item) AS len FROM orders",
                TestStreamSqlCompiler.ORDERS_SCHEMA, "testSink");
        StreamModel model = (StreamModel) new DslModelParser().parseFromNode(XNode.parse(xml));
        StreamExecutionEnvironment env = StreamModelDslBuilder.of(model).build();
        env.execute("sql-udf-e2e");

        SqlTestSink sink = (SqlTestSink) BeanContainer.instance().getBean("testSink");
        List<String> rows = sink.getCollected().stream()
                .map(o -> {
                    Map<String, Object> r = (Map<String, Object>) o;
                    return r.get("item") + "=" + r.get("len");
                })
                .sorted().collect(Collectors.toList());
        // FIXED_DATA items: a, b, a, a, b, a, c → lengths 1,1,1,1,1,1,1
        assertEquals(Arrays.asList("a=1", "a=1", "a=1", "a=1", "b=1", "b=1", "c=1"), rows,
                () -> "UDF rows: " + sink.getCollected());
    }
}

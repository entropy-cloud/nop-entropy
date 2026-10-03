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
import io.nop.stream.flow.spi.ISqlStreamCompiler;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI17 Phase 3 (plan 25 r2 B4/B5 layer 3): the REAL wiring — a declarative
 * {@code <sql>} model (xdef-validated) flows through
 * {@code DslModelParser → StreamModelDslBuilder.build() → <sql> expansion (module
 * app-beans provider) → execute() → sink}. This is the entry-point-to-output proof
 * that the compiled SQL product is genuinely executable through the declaration
 * surface. One execute per test class (local runner limit).
 */
public class TestSqlModelDeclarationE2E {

    private static IBeanContainerImplementor container;

    @BeforeAll
    public static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_IOC - 1);
        BeanContainerBuilder builder = new BeanContainerBuilder(null);
        builder.addResource(new ClassPathResource(
                "classpath:_vfs/nop/stream/sql/test/sql-compile.beans.xml"));
        // the module's own app-beans registrations: the sql-row-agg resolver AND the
        // <sql> compiler provider — both discovered through the module mechanism
        builder.addResource(new ClassPathResource(
                "classpath:_vfs/nop/stream/sql/beans/app-aggregator.beans.xml"));
        builder.addResource(new ClassPathResource(
                "classpath:_vfs/nop/stream/sql/beans/app-sql-compiler.beans.xml"));
        container = builder.build("sql-declaration-e2e");
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
    public void sqlDeclarationCompilesAndExecutesEndToEnd() throws Exception {
        // anti-hollow pin: the app-beans registration really auto-assembles the
        // <sql> compiler provider (module mechanism, lookup by type)
        ISqlStreamCompiler compiler =
                BeanContainer.instance().tryGetBeanByType(ISqlStreamCompiler.class);
        assertNotNull(compiler, "the module app-beans registration must auto-assemble "
                + "the <sql> compiler provider");
        assertTrue(compiler instanceof StreamSqlCompilerProvider,
                "the registered provider is the sql-side implementation");

        String modelXml = "<stream xmlns:x=\"/nop/schema/xdsl.xdef\" "
                + "x:schema=\"/nop/schema/stream/stream.xdef\" name=\"sql-declared\" version=\"1\">"
                + "<sql sinkBean=\"testSink\">"
                + "<schemas>"
                + "<field name=\"ts\" type=\"bigint\"/>"
                + "<field name=\"item\" type=\"string\"/>"
                + "<field name=\"amount\" type=\"int\"/>"
                + "</schemas>"
                + "<source><![CDATA[SELECT item, sum(amount) AS total "
                + "FROM TUMBLE(orders.ts, INTERVAL 5 SECOND) WHERE amount > 0 GROUP BY item]]>"
                + "</source>"
                + "</sql>"
                + "</stream>";

        StreamModel model = (StreamModel) new DslModelParser()
                .parseFromNode(XNode.parse(modelXml));
        assertNotNull(model.getSql(), "the xdef-validated model carries the <sql> element");

        StreamExecutionEnvironment env = StreamModelDslBuilder.of(model).build();
        // expansion consumed the declaration and replaced the content
        assertEquals(null, model.getSql(), "the <sql> element is consumed by build()");
        assertTrue(model.getTransforms().size() > 1, "compiled transforms replaced the model");
        env.execute("sql-declaration-e2e");

        SqlTestSink sink = (SqlTestSink) BeanContainer.instance().getBean("testSink");
        List<String> rows = sink.getCollected().stream()
                .map(o -> {
                    Map<?, ?> m = (Map<?, ?>) o;
                    return m.get("item") + "=" + m.get("total");
                })
                .sorted()
                .collect(Collectors.toList());
        assertEquals(Arrays.asList("a=4", "b=2", "b=4", "c=1"), rows,
                () -> "declared SQL rows: " + rows);
    }
}

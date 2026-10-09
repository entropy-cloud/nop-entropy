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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI16 (plan 2362): the WI0(e) Java-only path for transform-level SQL, pinned through
 * the SPI CONTRACT FACE — the test calls {@code ISqlStreamCompiler.compileSql(...)}
 * DIRECTLY (resolved by type from the module container) instead of the static
 * {@code StreamSqlCompiler.compile} facade used by the sibling E2E tests
 * ({@code TestTumbleAggregateQueryE2E} etc.). Delta vs the siblings: the SPI
 * interface signature (sql/schema/sinkBean/emit/fieldLateness) is the locked
 * contract for the Java-only path — WI0(e) adjudicated that transform-level SQL
 * never enters the XDSL declaration surface (the top-level-only constraint stays);
 * Java consumers compose compiled products programmatically.
 *
 * <p>Full chain: SPI type-resolved call → model XML → DslModelParser →
 * StreamModelDslBuilder → env.execute() → sink assertion. The top-level-only
 * constraint (a {@code <sql>} beside other content fails fast) is pinned in the
 * flow module's {@code TestSqlModelExpansion} and stays untouched.
 */
public class TestWi16TransformSqlJavaPath {

    private static IBeanContainerImplementor container;

    @BeforeAll
    public static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_IOC - 1);
        BeanContainerBuilder builder = new BeanContainerBuilder(null);
        builder.addResource(new ClassPathResource(
                "classpath:_vfs/nop/stream/sql/test/sql-compile.beans.xml"));
        builder.addResource(new ClassPathResource(
                "classpath:_vfs/nop/stream/sql/beans/app-aggregator.beans.xml"));
        // the WI0(e) Java-only path resolves the compiler BY TYPE — the SPI provider
        // bean lives in the app-sql-compiler beans file
        builder.addResource(new ClassPathResource(
                "classpath:_vfs/nop/stream/sql/beans/app-sql-compiler.beans.xml"));
        container = builder.build("wi16-java-path");
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

    private static Map<String, String> ordersSchema() {
        Map<String, String> schema = new LinkedHashMap<>();
        schema.put("ts", "bigint");
        schema.put("item", "string");
        schema.put("amount", "int");
        return schema;
    }

    @Test
    public void spiTypeResolvedCompileSqlExecutesEndToEnd() throws Exception {
        // THE WI0(e) Java-only path: resolve the compiler BY THE SPI INTERFACE and
        // call the full-signature compileSql (including the WI37 emit and WI25
        // lateness parameters the static facade hides behind overloads).
        ISqlStreamCompiler compiler = BeanContainer.instance()
                .getBeanByType(ISqlStreamCompiler.class);
        assertNotNull(compiler, "the sql module must provide the ISqlStreamCompiler SPI");

        String xml = compiler.compileSql(
                "SELECT item, sum(amount) AS total FROM TUMBLE(orders.ts, INTERVAL 5 SECOND) "
                        + "WHERE amount > 0 GROUP BY item",
                ordersSchema(), "testSink",
                io.nop.stream.core.windowing.StreamEmitMode.CLOSE,
                java.util.Collections.emptyMap());

        StreamModel model = (StreamModel) new DslModelParser().parseFromNode(XNode.parse(xml));
        StreamExecutionEnvironment env = StreamModelDslBuilder.of(model).build();
        env.execute("wi16-java-path-e2e");

        SqlTestSink sink = (SqlTestSink) BeanContainer.instance().getBean("testSink");
        Map<String, String> totals = sink.getCollected().stream()
                .map(row -> (Map<String, Object>) row)
                .map(m -> m.get("item") + "=" + m.get("total"))
                .collect(Collectors.toMap(v -> v.split("=")[0], v -> v.split("=")[1],
                        (a, b) -> b));
        assertEquals("4", totals.get("a"), () -> "collected: " + sink.getCollected());
        assertEquals("4", totals.get("b"), () -> "collected: " + sink.getCollected());
        assertEquals("1", totals.get("c"), () -> "collected: " + sink.getCollected());
    }

    @Test
    public void spiRejectsUnsupportedConstruct() {
        // The SPI contract face also carries the fail-fast side: a construct the
        // compiler cannot honor rejects through the same interface (never a silent
        // fallback). The emit gate on a plain-TUMBLE pipeline is the documented
        // example (TestSqlEmitMode precedent): UPDATE on a pipeline with no strategy
        // would be silently dropped, so the compiler rejects it.
        ISqlStreamCompiler compiler = BeanContainer.instance()
                .getBeanByType(ISqlStreamCompiler.class);
        Exception ex = assertThrows(Exception.class,
                () -> compiler.compileSql(
                        // plain aggregate pipeline (no TUMBLE source): UPDATE would be
                        // silently dropped — the documented emit gate (TestSqlEmitMode
                        // plainPipelineRejectsEmitUpdate precedent)
                        "SELECT item, sum(amount) AS total FROM orders GROUP BY item",
                        ordersSchema(), "testSink",
                        io.nop.stream.core.windowing.StreamEmitMode.UPDATE,
                        java.util.Collections.emptyMap()),
                "UPDATE on a plain aggregate pipeline must fail fast through the SPI face");
        assertTrue(String.valueOf(ex).contains("emit=update"),
                () -> "expected the emit gate, got: " + ex);
    }
}

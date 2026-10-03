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
import io.nop.core.resource.impl.ClassPathResource;
import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.ioc.loader.BeanContainerBuilder;
import io.nop.orm.eql.ast.SqlProgram;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.flow.builder.StreamModelDslBuilder;
import io.nop.stream.flow.model.StreamModel;
import io.nop.stream.sql.compile.testing.SqlTestSink;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * WI20: dual-target consistency for the FILTER/PROJECTION query — exact result
 * set equality between the nop-stream target (compiled product executed against
 * the orders stub source) and the RDBMS target (toSQL() text on an H2 fixture
 * preloaded with the same records). NULL three-valued logic is exercised by the
 * fixture's amount=NULL record: excluded on BOTH targets (the compiled xpl emits
 * an explicit null guard; SQL WHERE excludes NULL).
 *
 * <p>Separate class from {@link TestDualTargetConsistency}: one execute per test
 * class (local runner limit, plan 13).
 */
public class TestDualTargetFilterConsistency {

    private static final String QUERY =
            "SELECT item, amount FROM orders WHERE amount > 0";

    private static IBeanContainerImplementor container;
    private static Connection h2;

    @BeforeAll
    public static void init() throws Exception {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_IOC - 1);
        BeanContainerBuilder builder = new BeanContainerBuilder(null);
        builder.addResource(new ClassPathResource(
                "classpath:_vfs/nop/stream/sql/test/sql-compile.beans.xml"));
        container = builder.build("wi20-dual-filter");
        container.start();
        BeanContainer.registerInstance(container);

        h2 = DriverManager.getConnection("jdbc:h2:mem:wi20f;DB_CLOSE_DELAY=-1");
        try (Statement st = h2.createStatement()) {
            st.execute("CREATE TABLE orders(ts BIGINT, item VARCHAR(50), amount INT)");
            st.execute("INSERT INTO orders VALUES (1000,'a',1),(2000,'b',2),"
                    + "(3000,'a',NULL),(4000,'a',3),(11000,'b',4),"
                    + "(12000,'a',-5),(17000,'c',1)");
        }
    }

    @AfterAll
    public static void destroy() throws Exception {
        if (h2 != null)
            h2.close();
        if (container != null)
            container.stop();
        CoreInitialization.destroy();
        BeanContainer.registerInstance(null);
    }

    private static Map<String, String> schema() {
        Map<String, String> schema = new LinkedHashMap<>();
        schema.put("item", "string");
        schema.put("amount", "int");
        return schema;
    }

    @Test
    public void filterProjectionQueryMatchesH2Exactly() throws Exception {
        // RDBMS target
        SqlProgram program = new io.nop.orm.eql.parse.EqlASTParser().parseFromText(null, QUERY);
        String sql = program.toSQL().getText();
        List<String> h2Rows = new java.util.ArrayList<>();
        try (Statement st = h2.createStatement();
             var rs = st.executeQuery(sql)) {
            while (rs.next()) {
                Integer amount = rs.getObject("amount") == null ? null : rs.getInt("amount");
                h2Rows.add(rs.getString("item") + "=" + amount);
            }
        }
        h2Rows.sort(String::compareTo);

        // stream target: compile → execute (same stub fixture)
        StreamModel model = (StreamModel) new DslModelParser().parseFromNode(
                io.nop.core.lang.xml.XNode.parse(StreamSqlCompiler.compile(null, QUERY,
                        schema(), "testSink")));
        StreamExecutionEnvironment env = StreamModelDslBuilder.of(model).build();
        env.execute("wi20-dual-filter");

        SqlTestSink sink = (SqlTestSink) BeanContainer.instance().getBean("testSink");
        List<String> streamRows = sink.getCollected().stream()
                .map(o -> {
                    Map<?, ?> m = (Map<?, ?>) o;
                    return m.get("item") + "=" + m.get("amount");
                })
                .sorted()
                .collect(Collectors.toList());

        assertEquals(h2Rows, streamRows,
                "both targets must produce the identical filtered set (NULL excluded by "
                        + "three-valued logic on both sides): h2=" + h2Rows);
        assertEquals(Arrays.asList("a=1", "a=3", "b=2", "b=4", "c=1"), streamRows,
                "the fixture's expected filtered rows");
    }
}

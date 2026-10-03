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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI20: dual-target consistency for the CONTINUOUS AGGREGATE query — the same
 * SQL run on both targets:
 *
 * <ul>
 *   <li><b>nop-stream target</b>: StreamSqlCompiler → stream model → execute →
 *       the sink collects the RUNNING aggregate rows.</li>
 *   <li><b>RDBMS target (H2)</b>: {@code EqlASTParser.parse(sql).toSQL()} → the
 *       same text executed on an in-memory H2 table preloaded with the SAME
 *       seven-record fixture as the stream source stub.</li>
 * </ul>
 *
 * <p><b>D1=(a) explicit semantic annotation (non-append-only)</b>: the stream
 * target emits the RUNNING aggregate value per record — key "a" is emitted four
 * times (1, 1 on the null re-emit, 4, then -1 after the -5 record) while H2
 * returns only the final group sums (a=-1: 1+3-5).
 * Only the FINAL value per group is comparable; this test asserts the multi-emit
 * (the non-append-only evidence) AND the final-state equality.
 *
 * <p>D15 scope: H2 is the default physical-run target; other dialects remain
 * opt-in and are not exercised here.
 */
public class TestDualTargetConsistency {

    private static final String QUERY =
            "SELECT item, sum(amount) AS total FROM orders GROUP BY item";

    private static IBeanContainerImplementor container;
    private static Connection h2;

    @BeforeAll
    public static void init() throws Exception {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_IOC - 1);
        BeanContainerBuilder builder = new BeanContainerBuilder(null);
        builder.addResource(new ClassPathResource(
                "classpath:_vfs/nop/stream/sql/test/sql-compile.beans.xml"));
        container = builder.build("wi20-dual-target");
        container.start();
        BeanContainer.registerInstance(container);

        // H2 fixture: the SAME seven records as OrdersSourceFunction.FIXED_DATA
        h2 = DriverManager.getConnection("jdbc:h2:mem:wi20;DB_CLOSE_DELAY=-1");
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

    /** runs the EQL query text on the H2 fixture and returns item→total rows. */
    private Map<String, Integer> h2GroupSums() throws Exception {
        SqlProgram program = new io.nop.orm.eql.parse.EqlASTParser()
                .parseFromText(null, QUERY);
        String sql = program.toSQL().getText();
        Map<String, Integer> out = new LinkedHashMap<>();
        try (Statement st = h2.createStatement();
             var rs = st.executeQuery(sql)) {
            while (rs.next()) {
                Integer total = rs.getObject("total") == null ? null : rs.getInt("total");
                out.put(rs.getString("item"), total);
            }
        }
        return out;
    }

    @Test
    public void continuousAggregateFinalStateMatchesH2GroupSums() throws Exception {
        // stream target: compile → execute → running rows
        StreamModel model = (StreamModel) new DslModelParser().parseFromNode(
                io.nop.core.lang.xml.XNode.parse(StreamSqlCompiler.compile(null, QUERY,
                        schema(), "testSink")));
        StreamExecutionEnvironment env = StreamModelDslBuilder.of(model).build();
        env.execute("wi20-dual-agg");

        SqlTestSink sink = (SqlTestSink) BeanContainer.instance().getBean("testSink");
        Map<String, Integer> finals = new LinkedHashMap<>();
        Map<String, Integer> emits = new LinkedHashMap<>();
        for (Object o : sink.getCollected()) {
            Map<?, ?> m = (Map<?, ?>) o;
            String item = (String) m.get("item");
            Integer total = ((Number) m.get("total")).intValue();
            finals.put(item, total);
            emits.merge(item, 1, Integer::sum);
        }

        // D1=(a) non-append-only EVIDENCE: key "a" (with a null amount in the
        // middle) is emitted multiple times — the stream emits running values,
        // unlike H2's single final row per group
        assertTrue(emits.get("a") > 1,
                "the stream target must emit running values for key a (D1 non-append-only "
                        + "semantic difference vs the RDBMS single-row result): " + emits);

        // final state per group == H2 group sums (NULL amount skipped on both sides)
        Map<String, Integer> expected = h2GroupSums();
        assertEquals(expected, finals,
                "stream final state must equal the H2 group sums: expected=" + expected
                        + " actual=" + finals);
    }

    private static Map<String, String> schema() {
        Map<String, String> schema = new LinkedHashMap<>();
        schema.put("item", "string");
        schema.put("amount", "int");
        return schema;
    }
}

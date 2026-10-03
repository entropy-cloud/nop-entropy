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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI23: the SQL quickstart — the user's journey starts from a plain {@code .sql}
 * FILE (not an XML model): read the file, wrap it in the declarative
 * {@code <sql>} model (schema + sink bean), build, execute, assert the sink.
 * The .sql resource is the artifact a user writes and keeps; the model wrapping
 * is the only mechanical step. Pinned in quickstart verify.sh (step 4).
 *
 * <p>D1=(a) annotation: the continuous aggregate emits RUNNING values; the final
 * value per group is the comparable state.
 */
public class TestStreamSqlQuickstart {

    private static IBeanContainerImplementor container;

    @BeforeAll
    public static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_IOC - 1);
        BeanContainerBuilder builder = new BeanContainerBuilder(null);
        builder.addResource(new ClassPathResource(
                "classpath:_vfs/nop/stream/sql/test/sql-compile.beans.xml"));
        builder.addResource(new ClassPathResource(
                "classpath:_vfs/nop/stream/sql/beans/app-aggregator.beans.xml"));
        builder.addResource(new ClassPathResource(
                "classpath:_vfs/nop/stream/sql/beans/app-sql-compiler.beans.xml"));
        container = builder.build("sql-quickstart");
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
    public void sqlFileDrivesPipelineToSink() throws Exception {
        // 1. the user artifact: a plain .sql file
        String sql = io.nop.core.resource.VirtualFileSystem.instance()
                .getResource("/nop/stream/sql/test/sql-quickstart.query.sql").readText().trim();
        assertTrue(sql.contains("SELECT item, sum(amount) AS total FROM orders GROUP BY item"),
                "the .sql quickstart resource must hold the query: " + sql);

        // 2. wrap into the declarative <sql> model (schema + sink bean)
        Map<String, String> schema = new LinkedHashMap<>();
        schema.put("item", "string");
        schema.put("amount", "int");
        String modelXml = StreamSqlCompiler.compile(null, sql, schema, "testSink");
        StreamModel model = (StreamModel) new DslModelParser()
                .parseFromNode(XNode.parse(modelXml));

        // 3. build + execute
        StreamExecutionEnvironment env = StreamModelDslBuilder.of(model).build();
        env.execute("sql-quickstart");

        // 4. sink: final per-group state (running values converge to group sums)
        SqlTestSink sink = (SqlTestSink) BeanContainer.instance().getBean("testSink");
        Map<String, Integer> finals = new LinkedHashMap<>();
        for (Object o : sink.getCollected()) {
            Map<?, ?> m = (Map<?, ?>) o;
            finals.put((String) m.get("item"), ((Number) m.get("total")).intValue());
        }
        assertEquals(new LinkedHashMap<String, Integer>() {{
            put("a", -1);
            put("b", 6);
            put("c", 1);
        }}, finals, () -> "quickstart final per-group state: " + finals);
    }
}

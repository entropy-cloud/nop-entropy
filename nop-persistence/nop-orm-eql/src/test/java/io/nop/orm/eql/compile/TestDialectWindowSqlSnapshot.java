/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.compile;

import io.nop.core.initialize.CoreInitialization;
import io.nop.dao.dialect.DialectManager;
import io.nop.dao.dialect.IDialect;
import io.nop.orm.eql.compile.TestEqlCompileSql.TestCompileContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI3: 11 个独立方言的窗口子句 SQL 生成快照比对（D15 降级——执行未实测，golden 入库）。
 * 查询为无 frame 的 over + WINDOW 子句（不受能力位门限，features 全 false 也可编译）；
 * 带 frame 的生成文本由 WI2 合成方言单测覆盖。golden 文件：
 * src/test/resources/__snapshot/<dialect>.window.sql，文件内以 `-- query: <名称>` 分隔。
 */
public class TestDialectWindowSqlSnapshot {

    // db2 缺 driverClassName 等必备节点，无法独立加载（生产经 selector 机制注入）——快照不适用，
    // 矩阵文档单列裁定；postgresql/h2 已升级为 WI3 实跑，不在快照清单
    static Stream<String> dialects() {
        return Stream.of("dm", "duckdb", "mariadb", "mssql", "mysql", "mysql5.7",
                "oracle", "postgis", "postgresql", "h2gis");
    }

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    static TestCompileContext newContext(IDialect dialect) {
        TestCompileContext ctx = new TestCompileContext();
        ctx.entities.put("AppUser", TestEqlCompileSql.entity("AppUser", "APP_USER", "spaceA",
                TestEqlCompileSql.col("id", "SID", 1, true),
                TestEqlCompileSql.col("name", "NAME", 2, false)));
        ctx.effectiveDialect = dialect;
        return ctx;
    }

    String snapshot(String dialectName) throws java.io.IOException {
        try (java.io.InputStream in = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("__snapshot/" + dialectName + ".window.sql")) {
            assertNotNull(in, "golden not found: " + dialectName);
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    @ParameterizedTest
    @MethodSource("dialects")
    public void testWindowSqlSnapshot(String dialectName) throws java.io.IOException {
        IDialect dialect = DialectManager.instance().getDialect(dialectName);
        assertNotNull(dialect, "dialect not loaded: " + dialectName);
        TestCompileContext ctx = newContext(dialect);

        String sqlOver = new EqlCompiler().compile("test",
                "select o.id, sum(o.id) over (partition by o.name order by o.id) as s from AppUser o",
                ctx).getSql().getText();
        String sqlWindow = new EqlCompiler().compile("test",
                "select o.id, sum(o.id) over w as s from AppUser o "
                        + "window w as (partition by o.name order by o.id)",
                ctx).getSql().getText();

        String golden = snapshot(dialectName);
        // contains 关键片段断言（pretty-print 缩进使逐字 equals 易碎）
        assertTrue(golden.contains("-- dialect: " + dialectName), "golden missing dialect header: " + dialectName);
        assertTrue(golden.contains("-- query: over"), "golden missing over query marker");
        assertTrue(golden.contains("-- query: window"), "golden missing window query marker");

        String overPart = golden.substring(golden.indexOf("-- query: over"), golden.indexOf("-- query: window"));
        String windowPart = golden.substring(golden.indexOf("-- query: window"));

        for (String frag : normalize(sqlOver).split("\\n")) {
            if (frag.trim().isEmpty()) continue;
            assertTrue(normalize(overPart).contains(frag.trim()),
                    dialectName + " over golden mismatch. golden=[" + normalize(overPart) + "] actual-frag=[" + frag.trim() + "]");
        }
        for (String frag : normalize(sqlWindow).split("\\n")) {
            if (frag.trim().isEmpty()) continue;
            assertTrue(normalize(windowPart).contains(frag.trim()),
                    dialectName + " window golden mismatch. golden=[" + normalize(windowPart) + "] actual-frag=[" + frag.trim() + "]");
        }
    }

    String normalize(String text) {
        return text.toLowerCase().replaceAll("\\s+", " ");
    }
}

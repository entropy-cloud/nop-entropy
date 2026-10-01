/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.compile;

import io.nop.api.core.exceptions.NopException;
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
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI3/WI5: 窗口子句 SQL 生成快照比对（D15 降级——执行未实测，golden 入库）。
 * W2 时间切片窗口（TUMBLE/HOP/SESSION 伪表函数）尚未进入 EQL 语法层，无翻译 golden（见 plan 09 Deferred）。
 * 查询为无 frame 的 over + WINDOW 子句（不受能力位门限，features 全 false 也可编译）；
 * 带 frame 的生成文本由 WI2 合成方言单测覆盖。golden 文件：
 * src/test/resources/__snapshot/<dialect>.window.sql，文件内以 `-- query: <名称>` 分隔。
 */
public class TestDialectWindowSqlSnapshot {

    // 无 frame 的 over/WINDOW 快照方言集（10 个，含 postgresql）。db2 缺 driverClassName 等必备节点
    // 无法独立加载（生产经 selector 注入）——不适用快照；h2 走 WI3 端到端测试不在本清单
    static Stream<String> dialects() {
        return Stream.of("dm", "duckdb", "mariadb", "mssql", "mysql", "mysql5.7",
                "oracle", "postgis", "postgresql", "h2gis");
    }

    // WI5: frame 可编译方言集（features true×3，含继承）——frame golden 断言
    static Stream<String> frameDialects() {
        return Stream.of("h2", "postgresql", "duckdb", "postgis", "h2gis");
    }

    // WI5: 能力位关闭方言集——frame 查询编译期 fail-fast 断言
    static Stream<String> frameClosedDialects() {
        return Stream.of("dm", "mariadb", "mssql", "mysql", "mysql5.7", "oracle");
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

    String framesSnapshot(String dialectName) throws java.io.IOException {
        try (java.io.InputStream in = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("__snapshot/" + dialectName + ".frames.sql")) {
            assertNotNull(in, "frame golden not found: " + dialectName);
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    @ParameterizedTest
    @MethodSource("frameDialects")
    public void testWindowFrameGolden(String dialectName) throws java.io.IOException {
        IDialect dialect = DialectManager.instance().getDialect(dialectName);
        assertNotNull(dialect);
        TestCompileContext ctx = newContext(dialect);

        String[] frames = {
                "rows between unbounded preceding and current row",
                "range between unbounded preceding and current row",
                "groups between 1 preceding and current row",
        };
        StringBuilder actual = new StringBuilder();
        for (int i = 0; i < frames.length; i++) {
            String kind = frames[i].split(" ")[0];
            actual.append("-- query: ").append(kind).append(" inline\n");
            actual.append(new EqlCompiler().compile("test",
                    "select o.id, sum(o.id) over (partition by o.name order by o.id " + frames[i] + ") as s from AppUser o",
                    ctx).getSql().getText()).append('\n');
            actual.append("-- query: ").append(kind).append(" named\n");
            actual.append(new EqlCompiler().compile("test",
                    "select o.id, sum(o.id) over w as s from AppUser o window w as (partition by o.name order by o.id " + frames[i] + ")",
                    ctx).getSql().getText()).append('\n');
        }
        actual.append("-- query: named no frame\n");
        actual.append(new EqlCompiler().compile("test",
                "select o.id, sum(o.id) over w as s from AppUser o window w as (partition by o.name order by o.id)",
                ctx).getSql().getText()).append('\n');

        String golden = framesSnapshot(dialectName);
        assertTrue(golden.contains("-- query: rows inline"), "golden missing marker: " + dialectName);
        for (String frag : normalize(actual.toString()).split("\n")) {
            if (frag.trim().isEmpty()) continue;
            assertTrue(normalize(golden).contains(frag.trim()),
                    dialectName + " frame golden mismatch. frag=[" + frag.trim() + "]");
        }
    }

    @ParameterizedTest
    @MethodSource("frameClosedDialects")
    public void testWindowFrameFailFast(String dialectName) {
        IDialect dialect = DialectManager.instance().getDialect(dialectName);
        assertNotNull(dialect);
        TestCompileContext ctx = newContext(dialect);

        String[][] cases = {
                {"supportWindowFrameRows", "rows between unbounded preceding and current row"},
                {"supportWindowFrameRange", "range between unbounded preceding and current row"},
                {"supportWindowFrameGroups", "groups between 1 preceding and current row"},
        };
        for (String[] c : cases) {
            NopException e = assertThrows(NopException.class,
                    () -> new EqlCompiler().compile("test",
                            "select o.id, sum(o.id) over (partition by o.name order by o.id " + c[1] + ") as s from AppUser o",
                            ctx));
            assertEquals(ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE.getErrorCode(), e.getErrorCode(),
                    dialectName + " " + c[0]);
            assertEquals(c[0], e.getParam("feature"));
        }
    }
}

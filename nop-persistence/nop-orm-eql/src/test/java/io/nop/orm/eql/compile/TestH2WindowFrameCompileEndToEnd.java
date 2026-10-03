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
import io.nop.orm.eql.ICompiledSql;
import io.nop.orm.eql.compile.TestEqlCompileSql.TestCompileContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI3: 真实 h2 方言编译链端到端——EqlTransformVisitor 能力门（h2 三能力位经 WI3 实跑开启）放行，
 * AstToSqlGenerator 生成的 frame SQL 打回内存 H2 执行并断言结果。把 DB 能力证明（TestH2WindowFrameMatrix）
 * 与编译链接成端到端 Proof（plan guide 接线验证规则 #23）。
 */
public class TestH2WindowFrameCompileEndToEnd {
    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testH2DialectCompileAndExecute() throws Exception {
        IDialect dialect = DialectManager.instance().getDialect("h2");
        assertNotNull(dialect);
        assertTrue(dialect.isSupportWindowFrameRows(), "h2 rows feature should be on (WI3 matrix)");

        TestCompileContext ctx = newContext(dialect);
        ICompiledSql compiled = new EqlCompiler().compile("test",
                "select o.id, sum(o.id) over (partition by o.name order by o.id "
                        + "rows between unbounded preceding and current row) as s "
                        + "from AppUser o",
                ctx);
        String sql = compiled.getSql().getText();
        assertTrue(sql.contains("rows between unbounded preceding and current row"), sql);

        // 生成 SQL 打回内存 H2 执行
        try (Connection con = DriverManager.getConnection("jdbc:h2:mem:e2e_win;DB_CLOSE_DELAY=-1")) {
            try (Statement st = con.createStatement()) {
                st.execute("create table APP_USER (SID int primary key, NAME varchar(64))");
                st.execute("insert into APP_USER values (1,'g'),(2,'g'),(3,'g')");
            }
            List<BigDecimal> sums = new ArrayList<>();
            try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) {
                    sums.add(rs.getBigDecimal(rs.getMetaData().getColumnCount()));
                }
            }
            assertEquals(3, sums.size());
            // 全表分区 running sum: 1,3,6（SID 排序 a,b,c）
            assertEquals(bd(1), sums.get(0));
            assertEquals(bd(3), sums.get(1));
            assertEquals(bd(6), sums.get(2));
        }
    }

    static TestCompileContext newContext(IDialect dialect) {
        TestCompileContext ctx = new TestCompileContext();
        ctx.entities.put("AppUser", TestEqlCompileSql.entity("AppUser", "APP_USER", "spaceA",
                TestEqlCompileSql.col("id", "SID", 1, true),
                TestEqlCompileSql.col("name", "NAME", 2, false)));
        ctx.effectiveDialect = dialect;
        return ctx;
    }

    static BigDecimal bd(int v) {
        return BigDecimal.valueOf(v);
    }
}

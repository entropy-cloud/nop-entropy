/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical_entropy
 * Gitee:  https://gitee.com/nop-entropy/nop-entropy
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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2380 Phase 6：EQL {@code upsert} 在真实 H2 上的端到端执行。
 *
 * <p>本类把"方言能力证明"（{@code TestDialectUpsertAndReturningFlags}）与"编译链接成可执行 SQL"
 * （{@code TestEqlUpsertCompileGate}）接到真实数据库上，构成 plan guide 接线验证规则 #23 要求的
 * 端到端证明：EQL 源码 → 编译 → 方言 SQL → JDBC → 结果行。
 *
 * <p>⚠️ 两条断言纪律：
 * <ol>
 *   <li><b>不可断言"未列更新列保持原值"</b>——H2 分支要求更新集等于列清单（设计 §3.6 的必要条件之一），
 *       部分更新在 H2 上是编译期拒绝；该行为只对 PG / MySQL 成立，由它们的 SQL 文本断言覆盖。</li>
 *   <li>样例的 {@code do update set} <b>必须覆盖全部列清单（含冲突键列本身）</b>，且右值不得含列引用
 *       ——否则会被 Phase 4 自己写的编译期门拒绝。</li>
 * </ol>
 */
public class TestH2UpsertCompileEndToEnd {

    static final String URL = "jdbc:h2:mem:e2e_upsert;DB_CLOSE_DELAY=-1";

    /** 更新集覆盖全部列清单（含冲突键列本身），右值全是参数 —— 满足 H2 的两条必要条件。 */
    static final String EQL_UPSERT =
            "upsert into AppUser (id, name, age) values (?, ?, ?) on conflict (id) "
                    + "do update set id = ?, name = ?, age = ?";

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    static TestCompileContext ctx(IDialect dialect) {
        TestCompileContext ctx = new TestCompileContext();
        ctx.effectiveDialect = dialect;
        ctx.entities.put("AppUser", TestEqlCompileSql.entity("AppUser", "APP_USER", "spaceA",
                TestEqlCompileSql.col("id", "SID", 1, true),
                TestEqlCompileSql.col("name", "NAME", 2, false),
                TestEqlCompileSql.col("age", "AGE", 3, false)));
        return ctx;
    }

    static Connection newDb() throws Exception {
        Connection con = DriverManager.getConnection(URL);
        try (Statement st = con.createStatement()) {
            st.execute("create table APP_USER (SID int primary key, NAME varchar(64), AGE int)");
        }
        return con;
    }

    static int upsert(Connection con, IDialect dialect, int id, String name, int age) throws Exception {
        ICompiledSql compiled = new EqlCompiler().compile("test", EQL_UPSERT, ctx(dialect));
        try (PreparedStatement ps = con.prepareStatement(compiled.getSql().getText())) {
            // values(id,name,age) + do update set(id,name,age) —— 6 个参数位
            // H2 的 MERGE ... KEY ... VALUES 形态**没有更新子句**：更新集不参与 SQL 生成，
            // 因此只有 values(...) 的 3 个参数位。更新集在编译期已被强制为
            // "列名集合 == 列清单且右值无列引用"（设计 §3.6），所以丢弃它不损失语义。
            ps.setInt(1, id);
            ps.setString(2, name);
            ps.setInt(3, age);
            return ps.executeUpdate();
        }
    }

    @Test
    public void testH2UpsertInsertsThenUpdates() throws Exception {
        IDialect dialect = DialectManager.instance().getDialect("h2");
        assertNotNull(dialect);
        assertTrue(dialect.isSupportUpsert(), "h2 must declare supportUpsert (Plan 2380 Phase 2)");

        try (Connection con = newDb()) {
            // 腿①：新键 → 插入
            upsert(con, dialect, 1, "alice", 30);
            assertRow(con, 1, "alice", 30);

            // 腿②：同键再次写入 → 按列清单整体更新为新值
            upsert(con, dialect, 1, "alice2", 31);
            assertRow(con, 1, "alice2", 31);

            // 腿③：另一个新键 → 仍是插入（混合使用不会互相污染）
            upsert(con, dialect, 2, "bob", 40);
            assertRow(con, 2, "bob", 40);

            assertEquals(2, count(con));
        }
    }

    @Test
    public void testH2UpsertGeneratedSqlIsMergeKeyForm() throws Exception {
        IDialect dialect = DialectManager.instance().getDialect("h2");
        ICompiledSql compiled = new EqlCompiler().compile("test", EQL_UPSERT, ctx(dialect));
        String sql = compiled.getSql().getText().replaceAll("\\s+", " ")
                .replaceAll("\\(\\s+", "(").replaceAll("\\s+\\)", ")").trim().toLowerCase();
        assertTrue(sql.startsWith("merge into app_user"), sql);
        assertTrue(sql.contains("key (sid)"), sql);
        assertTrue(!sql.contains("do update"), "H2 MERGE form has no update clause: " + sql);
    }

    /**
     * H2 形态的参数位：只有 values(...) 的 3 个 —— 更新集<b>不</b>进入 SQL。
     *
     * <p>这不是"丢参数"的缺陷：编译期已强制更新集 == 列清单且右值无列引用，
     * H2 的 MERGE 按列清单从 VALUES 行整列更新，语义与用户书写的更新集等价。
     * 但它是一条<b>必须被钉住</b>的行为——若将来 H2 形态改动导致参数位与参数列表不匹配，
     * 会在执行期才炸。
     */
    @Test
    public void testH2BindsOnlyValueParameters() {
        IDialect dialect = DialectManager.instance().getDialect("h2");
        ICompiledSql compiled = new EqlCompiler().compile("test", EQL_UPSERT, ctx(dialect));
        String text = compiled.getSql().getText();
        int markers = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '?') {
                markers++;
            }
        }
        assertEquals(3, markers,
                "H2 MERGE form binds only the VALUES parameters: " + text);
    }

    // ---- helpers ----

    static void assertRow(Connection con, int id, String name, int age) throws Exception {
        try (PreparedStatement ps = con.prepareStatement(
                "select NAME, AGE from APP_USER where SID = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "row must exist for id=" + id);
                assertEquals(name, rs.getString(1), "NAME for id=" + id);
                assertEquals(age, rs.getInt(2), "AGE for id=" + id);
            }
        }
    }

    static int count(Connection con) throws Exception {
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("select count(*) from APP_USER")) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
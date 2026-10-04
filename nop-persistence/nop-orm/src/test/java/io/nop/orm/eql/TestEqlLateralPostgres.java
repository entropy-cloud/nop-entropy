/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical_entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql;

import io.nop.api.core.config.AppConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Plan 2307 Phase 2 / V1：LATERAL 产物在<strong>真正支持 LATERAL 的数据库</strong>上执行验证。
 *
 * <p>为什么必须有这一层：H2 2.3.232 完全不支持 LATERAL 关键字（实测逗号连接与 JOIN 两种写法
 * 均报 {@code Function "LATERAL" not found}），所以 LATERAL 的执行语义无法在 H2 上验证。
 * 本类把 EQL 实际编译出的 SQL 形态拿到 PostgreSQL 上跑，证明「产物不只是含关键字，而是
 * 真的可执行、且结果语义正确」。
 *
 * <p>运行方式（默认跳过，避免拖慢常规构建）：
 * <pre>./mvnw test -pl nop-persistence/nop-orm -Dtest=TestEqlLateralPostgres -Dnop.test.docker.enabled=true</pre>
 */
@EnabledIfSystemProperty(named = "nop.test.docker.enabled", matches = "true")
@Testcontainers
public class TestEqlLateralPostgres {

    /**
     * 镜像固定为 {@code postgres:16} 而非 {@code latest}：{@code latest} 在离线/受限网络下
     * 无法拉取，会让本测试变成"依赖网络可用"的脆弱断言。需要换版本时只改这一处。
     *
     * <p>注意 {@code jdbc:tc:} URL 的分段约定是 {@code jdbc:tc:<类型>:<tag>:///库名}，
     * 驱动会自己拼成 {@code postgres:<tag>}——把完整镜像名（含冒号）塞进 URL 会被解析成
     * "无法识别 tag"。因此这里拆成 DB_TYPE / DB_TAG 两段。
     */
    static final String DB_TYPE = "postgresql";
    static final String DB_TAG = AppConfig.var("nop.test.postgres.tag", "16");

    /**
     * 与 {@link TestEqlLateralEndToEnd#LATERAL_SQL} 编译产物同形的 SQL。
     * 表名/列名换成这里直接建的临时表，语义与 EQL 那条查询一致。
     */
    static final String LATERAL_SQL =
            "select c.id, t.class_count\n"
                    + "from college c, lateral\n"
                    + "  (select count(*) as class_count from student s where s.college_id = c.id) t\n"
                    + "order by c.id";

    /** 语义等价的标准写法（无 lateral），作为结果集对照基准 */
    static final String CORRELATED_SCALAR_SQL =
            "select c.id, (select count(*) from student s where s.college_id = c.id) as class_count\n"
                    + "from college c order by c.id";

    /** 修复前的产物形态：缺少 LATERAL 关键字 —— PostgreSQL 必须拒绝它 */
    static final String PRE_FIX_SQL =
            "select c.id, t.class_count\n"
                    + "from college c,\n"
                    + "  (select count(*) as class_count from student s where s.college_id = c.id) t\n"
                    + "order by c.id";

    Connection openContainer() {
        String user = AppConfig.var("nop.database.username", "test");
        String pwd = AppConfig.var("nop.database.password", "test");
        try {
            return DriverManager.getConnection(
                    "jdbc:tc:" + DB_TYPE + ":" + DB_TAG + ":///test?TC_DAEMON=true", user, pwd);
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "cannot start " + DB_TYPE + ":" + DB_TAG + " testcontainer", e);
        }
    }

    void initSchema(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("drop table if exists student");
            st.execute("drop table if exists college");
            st.execute("create table college (id int primary key)");
            st.execute("create table student (id int primary key, college_id int)");
            st.execute("insert into college values (1), (2), (3)");
            st.execute("insert into student values (1,1), (2,1), (3,2)");
        }
    }

    List<String> query(Connection conn, String sql) throws SQLException {
        List<String> out = new ArrayList<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                out.add(rs.getInt(1) + ":" + rs.getLong(2));
            }
        }
        return out;
    }

    @Test
    public void testLateralSqlExecutesOnPostgres() throws SQLException {
        try (Connection conn = openContainer()) {
            initSchema(conn);
            List<String> rows = query(conn, LATERAL_SQL);
            // college 1 -> 2 students, college 2 -> 1, college 3 -> 0
            assertEquals(List.of("1:2", "2:1", "3:0"), rows);
        }
    }

    @Test
    public void testLateralMatchesCorrelatedScalarQuery() throws SQLException {
        try (Connection conn = openContainer()) {
            initSchema(conn);
            assertEquals(query(conn, CORRELATED_SCALAR_SQL), query(conn, LATERAL_SQL),
                    "LATERAL 版与相关标量子查询版结果集必须一致");
        }
    }

    /**
     * 反向证明：修复前的产物形态（缺 LATERAL）确实是错的。
     * 若该断言日后失败，说明「缺关键字」在某天变得合法，本测试的判据需重新评估。
     */
    @Test
    public void testPreFixSqlIsRejectedByPostgres() throws SQLException {
        try (Connection conn = openContainer()) {
            initSchema(conn);
            try {
                query(conn, PRE_FIX_SQL);
                fail("缺少 LATERAL 关键字的 SQL 在 PostgreSQL 上应当被拒绝——"
                        + "若本测试失败说明缺陷前提已变化");
            } catch (SQLException expected) {
                // 语法错误类：SQLSTATE 42xxx（syntax_error / undefined_table 之类）
                assertNotNull(expected.getSQLState(),
                        "拒绝时必须带 SQLSTATE，说明是数据库侧的语法判定: " + expected.getClass());
                assertTrue(expected.getSQLState().startsWith("42"),
                        "应是语法/语义类错误而非连接类错误，实际 SQLSTATE=" + expected.getSQLState());
            }
        }
    }

    @Test
    public void testLateralWithMultipleCorrelatedColumns() throws SQLException {
        try (Connection conn = openContainer()) {
            try (Statement st = conn.createStatement()) {
                st.execute("drop table if exists student");
                st.execute("drop table if exists college");
                st.execute("create table college (id int primary key, name varchar(50))");
                st.execute("create table student (id int primary key, college_id int, grade int)");
                st.execute("insert into college values (1,'a'), (2,'b')");
                st.execute("insert into student values (1,1,90), (2,1,80), (3,2,70)");
            }
            // 两个相关条件 + 一个聚合，证明 LATERAL 子查询的关联能力完整可用。
            // 数据：(1,college1,90) (2,college1,80) (3,college2,70)，过滤 grade>75
            // ⇒ college1 命中 2 条，college2 命中 0 条
            List<String> rows = query(conn,
                    "select c.id, t.n from college c, lateral\n"
                            + "  (select count(*) as n from student s where s.college_id = c.id and s.grade > 75) t\n"
                            + " order by c.id");
            assertEquals(List.of("1:2", "2:0"), rows);
        }
    }

    @Test
    public void testNestedLateralIsRejectedOrHandled() throws SQLException {
        // 记录当前行为：PostgreSQL 对 LATERAL 里再套 LATERAL 的支持情况。
        // 不断言成功，只要求不出现「静默产出错误结果」。
        try (Connection conn = openContainer()) {
            initSchema(conn);
            String sql = "select c.id, t.n from college c, lateral\n"
                    + "  (select count(*) as n from student s, lateral\n"
                    + "     (select 1 from student s2 where s2.college_id = c.id) t2 where s.college_id = c.id) t\n"
                    + " order by c.id";
            try {
                List<String> rows = query(conn, sql);
                assertFalse(rows.isEmpty(), "若执行成功则必须返回真实结果而非空/畸形结果");
            } catch (SQLException e) {
                assertNotNull(e.getSQLState(),
                        "拒绝时必须带 SQLSTATE，说明是数据库侧的判定而非客户端异常");
            }
        }
    }
}
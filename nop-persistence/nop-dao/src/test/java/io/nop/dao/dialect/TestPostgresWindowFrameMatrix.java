/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.dialect;

import com.zaxxer.hikari.HikariDataSource;
import io.nop.commons.metrics.GlobalMeterRegistry;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.Test;
import io.nop.dao.jdbc.JdbcTestCase;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI3: PostgreSQL 真实执行窗口 frame 矩阵（docker opt-in）。自备镜像 postgres:16-alpine——
 * 既有 TestPostgreDialect 钉的 9.6.12 镜像已从 Docker Hub 移除（拉取超时实测），且其继承的
 * TestDialect 旧套件与 PG16 存在版本错配（VARBINARY 等），故本类独立建数据源不继承旧套件。
 * PG 16 支持 GROUPS；结论作为 postgresql.dialect.xml features 填写依据，版本差异在矩阵文档记录。
 */
@EnabledIfSystemProperty(named = "nop.test.docker.enabled", matches = "true")
@Testcontainers
public class TestPostgresWindowFrameMatrix extends JdbcTestCase {

    @Override
    protected HikariDataSource createDataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setMetricRegistry(GlobalMeterRegistry.instance());
        ds.setDriverClassName("org.testcontainers.jdbc.ContainerDatabaseDriver");
        ds.setJdbcUrl("jdbc:tc:postgresql:16-alpine:///test?TC_DAEMON=true");
        ds.setUsername("test");
        ds.setPassword("test");
        ds.setMaximumPoolSize(maxPoolSize);
        return ds;
    }

    static BigDecimal bd(int v) {
        return BigDecimal.valueOf(v);
    }

    void setupTable(Connection con) throws Exception {
        try (Statement st = con.createStatement()) {
            try {
                st.execute("drop table t_win_matrix");
            } catch (Exception e) {
                // ignore not exists
            }
            st.execute("create table t_win_matrix (id int primary key, grp varchar(10), val int)");
            st.execute("insert into t_win_matrix values (1,'A',1),(2,'A',2),(3,'A',3),(4,'B',10),(5,'B',20)");
        }
    }

    List<BigDecimal> querySums(Connection con, String sql) throws Exception {
        List<BigDecimal> sums = new ArrayList<>();
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                sums.add(rs.getBigDecimal(2));
            }
        }
        return sums;
    }

    boolean tryQuery(Connection con, String sql, List<BigDecimal> out) {
        try {
            out.addAll(querySums(con, sql));
            return true;
        } catch (Exception e) {
            System.out.println("WI3 matrix postgresql group failed: " + e.toString());
            return false;
        }
    }

    @Test
    public void testWindowFrameMatrix() throws Exception {
        try (Connection con = getDataSource().getConnection()) {
            setupTable(con);

            List<BigDecimal> sums = new ArrayList<>();
            boolean rowsOk = tryQuery(con,
                    "select id, sum(val) over (partition by grp order by id rows between unbounded preceding and current row) from t_win_matrix order by id",
                    sums);
            if (rowsOk) {
                assertEquals(bd(1), sums.get(0));
                assertEquals(bd(6), sums.get(2));
                assertEquals(bd(30), sums.get(4));
            }

            sums = new ArrayList<>();
            boolean rangeOk = tryQuery(con,
                    "select id, sum(val) over (partition by grp order by id range between unbounded preceding and current row) from t_win_matrix order by id",
                    sums);
            if (rangeOk) {
                assertEquals(bd(6), sums.get(2));
            }

            sums = new ArrayList<>();
            boolean groupsOk = tryQuery(con,
                    "select id, sum(val) over (order by id groups between 1 preceding and current row) from t_win_matrix order by id",
                    sums);

            sums = new ArrayList<>();
            boolean namedRowsOk = tryQuery(con,
                    "select id, sum(val) over w from t_win_matrix "
                            + "window w as (partition by grp order by id rows between unbounded preceding and current row) order by id",
                    sums);
            if (namedRowsOk) {
                assertEquals(bd(6), sums.get(2));
            }

            sums = new ArrayList<>();
            boolean namedRangeOk = tryQuery(con,
                    "select id, sum(val) over w from t_win_matrix "
                            + "window w as (partition by grp order by id range between unbounded preceding and current row) order by id",
                    sums);

            sums = new ArrayList<>();
            boolean namedGroupsOk = tryQuery(con,
                    "select id, sum(val) over w from t_win_matrix "
                            + "window w as (order by id groups between 1 preceding and current row) order by id",
                    sums);

            sums = new ArrayList<>();
            boolean namedNoFrameOk = tryQuery(con,
                    "select id, sum(val) over w from t_win_matrix "
                            + "window w as (partition by grp order by id) order by id",
                    sums);
            if (namedNoFrameOk) {
                assertEquals(bd(30), sums.get(4));
            }

            String verdict = "WI3 matrix postgresql:16-alpine: rows=" + pass(rowsOk) + " range=" + pass(rangeOk)
                    + " groups=" + pass(groupsOk)
                    + " namedRows=" + pass(namedRowsOk) + " namedRange=" + pass(namedRangeOk)
                    + " namedGroups=" + pass(namedGroupsOk) + " namedNoFrame=" + pass(namedNoFrameOk);
            System.out.println(verdict);

            assertTrue(rowsOk && rangeOk && namedRowsOk && namedRangeOk && namedNoFrameOk,
                    verdict);
        }
    }

    static String pass(boolean ok) {
        return ok ? "PASS" : "FAIL";
    }
}

/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.dialect;

import io.nop.dao.jdbc.JdbcTestCase;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI3: H2 真实执行窗口 frame 矩阵——{rows, range, groups} × {inline, named window} 6 组
 * + 第 7 组 named-window 无 frame 对照轴（WINDOW 子句语法支持独立于 frame 单位）。
 * 内存 H2（JdbcTestCase 默认数据源），无 docker 依赖；结论作为 h2.dialect.xml features 填写依据，
 * 并以 System.out 输出矩阵行供 ai-dev/design/nop-stream/sql-window-dialect-matrix.md 引用。
 */
public class TestH2WindowFrameMatrix extends JdbcTestCase {

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
            // grp=A: val 1,2,3 ; grp=B: val 10,20
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
            System.out.println("WI3 matrix h2 group failed: " + e.toString());
            return false;
        }
    }

    @Test
    public void testWindowFrameMatrix() throws Exception {
        try (Connection con = getDataSource().getConnection()) {
            setupTable(con);

            // 1 rows inline：partition by grp 分区累计 A:1,3,6 B:10,30
            List<BigDecimal> sums = new ArrayList<>();
            boolean rowsOk = tryQuery(con,
                    "select id, sum(val) over (partition by grp order by id rows between unbounded preceding and current row) from t_win_matrix order by id",
                    sums);
            if (rowsOk) {
                assertEquals(bd(1), sums.get(0));
                assertEquals(bd(6), sums.get(2));
                assertEquals(bd(30), sums.get(4));
            }

            // 2 range inline：等价 range unbounded preceding -> current row
            sums = new ArrayList<>();
            boolean rangeOk = tryQuery(con,
                    "select id, sum(val) over (partition by grp order by id range between unbounded preceding and current row) from t_win_matrix order by id",
                    sums);
            if (rangeOk) {
                assertEquals(bd(6), sums.get(2));
                assertEquals(bd(30), sums.get(4));
            }

            // 3 groups inline：H2 2.4 是否支持 GROUPS 由本组实证（不支持即语法异常，矩阵记录失败形态）
            sums = new ArrayList<>();
            boolean groupsOk = tryQuery(con,
                    "select id, sum(val) over (order by id groups between 1 preceding and current row) from t_win_matrix order by id",
                    sums);

            // 4 named window + rows
            sums = new ArrayList<>();
            boolean namedRowsOk = tryQuery(con,
                    "select id, sum(val) over w from t_win_matrix "
                            + "window w as (partition by grp order by id rows between unbounded preceding and current row) order by id",
                    sums);
            if (namedRowsOk) {
                assertEquals(bd(6), sums.get(2));
            }

            // 5 named window + range
            sums = new ArrayList<>();
            boolean namedRangeOk = tryQuery(con,
                    "select id, sum(val) over w from t_win_matrix "
                            + "window w as (partition by grp order by id range between unbounded preceding and current row) order by id",
                    sums);
            if (namedRangeOk) {
                assertEquals(bd(30), sums.get(4));
            }

            // 6 named window + groups
            sums = new ArrayList<>();
            boolean namedGroupsOk = tryQuery(con,
                    "select id, sum(val) over w from t_win_matrix "
                            + "window w as (order by id groups between 1 preceding and current row) order by id",
                    sums);

            // 7 对照轴：named window 无 frame（WINDOW 子句语法支持独立于 frame 单位）
            sums = new ArrayList<>();
            boolean namedNoFrameOk = tryQuery(con,
                    "select id, sum(val) over w from t_win_matrix "
                            + "window w as (partition by grp order by id) order by id",
                    sums);
            if (namedNoFrameOk) {
                assertEquals(bd(30), sums.get(4));
            }

            String verdict = "WI3 matrix h2: rows=" + pass(rowsOk) + " range=" + pass(rangeOk)
                    + " groups=" + pass(groupsOk)
                    + " namedRows=" + pass(namedRowsOk) + " namedRange=" + pass(namedRangeOk)
                    + " namedGroups=" + pass(namedGroupsOk) + " namedNoFrame=" + pass(namedNoFrameOk);
            System.out.println(verdict);

            // 基础能力断言：ROWS/RANGE 与 WINDOW 子句（对照轴）必须通过，否则 h2 features 不应开启
            assertTrue(rowsOk && rangeOk && namedRowsOk && namedRangeOk && namedNoFrameOk,
                    verdict);
        }
    }

    static String pass(boolean ok) {
        return ok ? "PASS" : "FAIL";
    }
}

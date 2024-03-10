/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package org.beetl.sql.jmh;

import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class DataSourceHelper {
    static DataSource ds = datasource();

    public static DataSource ins() {
        return ds;
    }

    private static DataSource datasource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl("jdbc:h2:mem:dbtest;DB_CLOSE_ON_EXIT=FALSE");
        ds.setUsername("sa");
        ds.setPassword("");
        ds.setDriverClassName("org.h2.Driver");
        initData(ds);
        return ds;
    }

    private static void initData(DataSource ds) {
        Connection conn = null;
        try {
            conn = ds.getConnection();
            String[] sqls = getSqlFromFile();
            for (String sql : sqls) {
                runSql(conn, sql);
            }

        } catch (SQLException sqlException) {
            throw new RuntimeException(sqlException);
        } finally {
            try {
                if (conn != null)
                    conn.close();
            } catch (SQLException sqlException) {
                // ignore
            }
        }
    }

    private static String[] getSqlFromFile() {
        try {
            InputStream ins = DataSourceHelper.class.getResourceAsStream("/db/schema.sql");
            int len = ins.available();
            byte[] bs = new byte[len];
            ins.read(bs);
            String str = new String(bs, "UTF-8");
            String[] sql = str.split(";");
            return sql;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

    }

    private static void runSql(Connection conn, String sql) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.executeUpdate();
        ps.close();
    }
}

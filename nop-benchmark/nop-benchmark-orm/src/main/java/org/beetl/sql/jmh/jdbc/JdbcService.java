/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package org.beetl.sql.jmh.jdbc;

import org.beetl.sql.jmh.BaseService;
import org.beetl.sql.jmh.DataSourceHelper;
import org.beetl.sql.jmh.beetl.vo.BeetlSQLSysUser;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

public class JdbcService implements BaseService {
    DataSource dataSource = null;
    AtomicInteger idGen = new AtomicInteger(1000);

    public void init() {
        dataSource = DataSourceHelper.ins();
    }

    @Override
    public void addEntity() {

        BeetlSQLSysUser beetlSQLSysUser = new BeetlSQLSysUser();
        beetlSQLSysUser.setId(idGen.getAndIncrement());
        beetlSQLSysUser.setCode("abc");
        Connection conn = null;
        try {
            conn = dataSource.getConnection();
            PreparedStatement ps = conn.prepareStatement("insert into sys_user  (id,code) values (?,?)");
            ps.setInt(1, beetlSQLSysUser.getId());
            ps.setString(2, beetlSQLSysUser.getCode());
            ps.executeUpdate();
            ps.close();
            conn.commit();
            conn.close();

        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public Object getEntity() {
        Connection conn = null;
        try {
            conn = dataSource.getConnection();
            PreparedStatement ps = conn.prepareStatement("select id,code from sys_user where id=?");
            ps.setInt(1, 1);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                int id = rs.getInt(1);
                String code = rs.getString(2);
                BeetlSQLSysUser beetlSQLSysUser = new BeetlSQLSysUser();
                beetlSQLSysUser.setId(id);
                beetlSQLSysUser.setCode(code);
                return beetlSQLSysUser;
            } else {
                return null;
            }

        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException sqlException) {
                    sqlException.printStackTrace();
                }
            }
        }
    }

    @Override
    public void lambdaQuery() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void executeJdbcSql() {
        getEntity();
    }

    @Override
    public void executeTemplateSql() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void sqlFile() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void one2Many() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void pageQuery() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void complexMapping() {
        throw new UnsupportedOperationException();
    }
}

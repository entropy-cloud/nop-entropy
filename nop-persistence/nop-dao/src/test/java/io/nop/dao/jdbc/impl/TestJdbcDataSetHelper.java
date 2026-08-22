/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.jdbc.impl;

import io.nop.core.lang.sql.SQL;
import io.nop.dao.jdbc.JdbcTestCase;
import io.nop.dataset.IDataSet;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JdbcDataSetHelper.newDataSet返回的数据集close时必须一并关闭创建它的PreparedStatement，
 * 调用方没有其他途径关闭该语句
 */
public class TestJdbcDataSetHelper extends JdbcTestCase {

    static class StatementTracker implements InvocationHandler {
        private final Statement target;
        private boolean closed;

        StatementTracker(Statement target) {
            this.target = target;
        }

        boolean isClosed() {
            return closed;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getName().equals("close"))
                closed = true;
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }

    static Connection trackingConnection(Connection conn, List<StatementTracker> trackers) {
        return (Connection) Proxy.newProxyInstance(TestJdbcDataSetHelper.class.getClassLoader(),
                new Class[]{Connection.class}, new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        Object ret;
                        try {
                            ret = method.invoke(conn, args);
                        } catch (InvocationTargetException e) {
                            throw e.getCause();
                        }
                        if (ret instanceof PreparedStatement) {
                            StatementTracker tracker = new StatementTracker((PreparedStatement) ret);
                            trackers.add(tracker);
                            return Proxy.newProxyInstance(TestJdbcDataSetHelper.class.getClassLoader(),
                                    new Class[]{PreparedStatement.class}, tracker);
                        }
                        return ret;
                    }
                });
    }

    @Test
    public void testNewDataSetCloseClosesStatement() throws Exception {
        jdbc().executeUpdate(new SQL("create table ds_helper_entity(id int primary key, v int)"));
        jdbc().executeUpdate(new SQL("insert into ds_helper_entity(id, v) values(1, 11)"));

        List<StatementTracker> trackers = new ArrayList<>();

        try (Connection conn = trackingConnection(getDataSource().getConnection(), trackers)) {
            IDataSet ds = JdbcDataSetHelper.newDataSet(conn, new SQL("select id, v from ds_helper_entity"));

            // 读出一行数据验证数据集可用
            assertTrue(ds.hasNext());
            assertEquals(1L, ds.next().getLong(0));

            ds.close();
        }

        assertEquals(1, trackers.size());
        assertTrue(trackers.get(0).isClosed(), "statement must be closed together with the dataset");
    }

    @Test
    public void testNewDataSetClosesStatementOnSqlError() throws Exception {
        // 注意：H2对不存在的表在prepareStatement阶段即抛错（无statement需要关闭），
        // 因此用编译通过但执行失败的SQL（除零）触发executeQuery失败路径
        jdbc().executeUpdate(new SQL("create table ds_helper_entity2(id int primary key, v int)"));
        jdbc().executeUpdate(new SQL("insert into ds_helper_entity2(id, v) values(1, 11)"));

        List<StatementTracker> trackers = new ArrayList<>();

        try (Connection conn = trackingConnection(getDataSource().getConnection(), trackers)) {
            try {
                JdbcDataSetHelper.newDataSet(conn, new SQL("select id/0 from ds_helper_entity2"));
                throw new AssertionError("division by zero query should fail");
            } catch (RuntimeException e) {
                // 预期失败：方言翻译后的异常，具体类型不重要
            }
        }

        assertEquals(1, trackers.size());
        assertTrue(trackers.get(0).isClosed(), "statement must be closed when executeQuery fails");
    }
}

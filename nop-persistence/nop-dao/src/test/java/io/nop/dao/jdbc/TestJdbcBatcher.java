/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.jdbc;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.sql.SQL;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestJdbcBatcher extends JdbcTestCase {

    /**
     * 记录PreparedStatement是否被close
     */
    static class StatementTracker implements InvocationHandler {
        private final PreparedStatement target;
        private boolean closed;

        StatementTracker(PreparedStatement target) {
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

    /**
     * 包装Connection，将其中创建的所有PreparedStatement替换为可跟踪close调用的代理
     */
    static Connection trackingConnection(Connection conn, List<StatementTracker> trackers) {
        return (Connection) Proxy.newProxyInstance(TestJdbcBatcher.class.getClassLoader(),
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
                    return Proxy.newProxyInstance(TestJdbcBatcher.class.getClassLoader(),
                            new Class[]{PreparedStatement.class}, tracker);
                }
                return ret;
            }
        });
    }

    static SQL insertSql(int id, int v) {
        return SQL.begin().append("insert into batcher_entity(id, v) values(").param(id).append(',')
                .param(v).append(")").end();
    }

    @Test
    public void testFlushBatchClosesStatement() throws Exception {
        jdbc().executeUpdate(new SQL("create table batcher_entity(id int primary key, v int)"));

        List<StatementTracker> trackers = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();

        try (Connection conn = trackingConnection(getDataSource().getConnection(), trackers)) {
            JdbcBatcher batcher = new JdbcBatcher(conn, getDialect(), null);
            batcher.setBatchSize(10);

            // 同一SQL文本才会走批量路径
            batcher.addCommand(insertSql(1, 11), true, (count, e) -> {
                if (e != null)
                    throw NopException.adapt(e);
                counts.add(count);
            });
            batcher.addCommand(insertSql(2, 22), true, (count, e) -> {
                if (e != null)
                    throw NopException.adapt(e);
                counts.add(count);
            });
            batcher.flush();
        }

        // 批量执行确实成功
        assertEquals(2, counts.size());
        assertEquals(1, counts.get(0).intValue());
        assertEquals(1, counts.get(1).intValue());
        assertEquals(2L, jdbc().findLong(new SQL("select count(*) from batcher_entity"), 0L));

        // 批量路径只应创建一个PreparedStatement，且flush结束后必须关闭
        assertEquals(1, trackers.size());
        assertTrue(trackers.get(0).isClosed(), "PreparedStatement created by flush() must be closed");
    }

    @Test
    public void testFlushBatchFailClosesStatement() throws Exception {
        jdbc().executeUpdate(new SQL("create table batcher_entity(id int primary key, v int)"));
        jdbc().executeUpdate(new SQL("insert into batcher_entity(id, v) values(1, 11)"));

        List<StatementTracker> trackers = new ArrayList<>();

        try (Connection conn = trackingConnection(getDataSource().getConnection(), trackers)) {
            JdbcBatcher batcher = new JdbcBatcher(conn, getDialect(), null);
            batcher.setBatchSize(10);

            // 同一SQL文本才会走批量路径，两条都与已存在的id=1主键冲突，executeBatch时失败
            batcher.addCommand(insertSql(1, 33), true, null);
            batcher.addCommand(insertSql(1, 44), true, null);

            assertThrows(NopException.class, batcher::flush);
        }

        assertEquals(1, trackers.size());
        assertTrue(trackers.get(0).isClosed(), "PreparedStatement must be closed even if executeBatch fails");

        // 失败批次不提交任何数据
        assertEquals(1L, jdbc().findLong(new SQL("select count(*) from batcher_entity"), 0L));
    }

    @Test
    public void testFlushNoBatchClosesStatement() throws Exception {
        List<StatementTracker> trackers = new ArrayList<>();

        try (Connection conn = trackingConnection(getDataSource().getConnection(), trackers)) {
            JdbcBatcher batcher = new JdbcBatcher(conn, getDialect(), null);
            batcher.setBatchSize(10);

            batcher.addCommand(new SQL("update my_entity set a = 10 where id = 1"), true, null);
            batcher.flush();
        }

        assertEquals(1, trackers.size());
        assertTrue(trackers.get(0).isClosed());

        assertEquals(10L, jdbc().findLong(new SQL("select a from my_entity where id = 1"), 0L));
    }
}

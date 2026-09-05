/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.jdbc.txn;

import io.nop.core.initialize.CoreInitialization;
import io.nop.dao.dialect.DialectManager;
import io.nop.dao.dialect.IDialect;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 回归覆盖审查报告 DAO-03：eagerReleaseConnection=false 时，commit/rollback 之后连接被保留，
 * autoCommit 仍为 false；此后若复用该事务对象的连接执行写操作，语句运行在永不提交的隐式事务中，
 * 连接关闭即丢失。修复要求 commit/rollback 完成后将连接恢复为 autoCommit=true。
 */
public class TestJdbcTransactionAutoCommitRestore {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private Connection newH2Connection() throws Exception {
        Class.forName("org.h2.Driver");
        return java.sql.DriverManager.getConnection("jdbc:h2:mem:" + java.util.UUID.randomUUID());
    }

    @Test
    public void testCommitRestoresAutoCommitWhenConnectionRetained() throws Exception {
        IDialect dialect = DialectManager.instance().getDialect("h2");
        Connection conn = newH2Connection();
        Supplier<Connection> supplier = () -> conn;

        // eagerReleaseConnection=false：提交后连接保留
        JdbcTransaction txn = new JdbcTransaction("test-qs", supplier, dialect, false, null);
        try {
            txn.open();
            assertTrue(conn.getTransactionIsolation() > 0, "连接应可用");
            assertTrue(!conn.getAutoCommit(), "open后autoCommit应为false");

            txn.commit();

            assertTrue(conn.getAutoCommit(),
                    "commit后（连接保留场景）必须恢复autoCommit=true，否则后续复用连接的写操作永不提交");
        } finally {
            conn.close();
        }
    }

    @Test
    public void testRollbackRestoresAutoCommitWhenConnectionRetained() throws Exception {
        IDialect dialect = DialectManager.instance().getDialect("h2");
        Connection conn = newH2Connection();
        Supplier<Connection> supplier = () -> conn;

        JdbcTransaction txn = new JdbcTransaction("test-qs", supplier, dialect, false, null);
        try {
            txn.open();
            txn.rollback(new RuntimeException("biz-error"));

            assertTrue(conn.getAutoCommit(),
                    "rollback后（连接保留场景）必须恢复autoCommit=true");
        } finally {
            conn.close();
        }
    }
}

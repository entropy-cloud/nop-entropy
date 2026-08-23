/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.jdbc.datasource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * runWith必须在任务结束后恢复原数据源（无原值时清理ThreadLocal），
 * 防止线程池复用时跨请求数据串写
 */
public class TestDynamicDataSource {

    @AfterEach
    public void tearDown() {
        // 清理静态ThreadLocal，避免影响其他测试
        DynamicDataSource.runWith(null, () -> null);
    }

    static class FakeDataSource implements DataSource {
        final String name;
        // 记录最近一次被resolve的数据源，避免用异常消息承载判定信息
        static FakeDataSource lastResolved;

        FakeDataSource(String name) {
            this.name = name;
        }

        @Override
        public Connection getConnection() throws SQLException {
            lastResolved = this;
            throw new SQLException("fake-data-source-has-no-connection");
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return getConnection();
        }

        @Override
        public <T> T unwrap(Class<T> iface) {
            return null;
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            return null;
        }
    }

    /**
     * 通过FakeDataSource.lastResolved识别当前实际生效的数据源；
     * 无当前数据源且无缺省值时resolveDataSource抛NopException，lastResolved保持不变
     */
    private String resolveCurrent(DynamicDataSource ds) {
        FakeDataSource.lastResolved = null;
        try {
            ds.getConnection();
        } catch (Exception e) {
            // 预期：fake数据源不返回连接
        }
        FakeDataSource resolved = FakeDataSource.lastResolved;
        return resolved == null ? "none" : resolved.name;
    }

    @Test
    public void testRunWithRestoresPreviousDataSource() {
        DynamicDataSource ds = new DynamicDataSource();
        FakeDataSource a = new FakeDataSource("a");
        FakeDataSource b = new FakeDataSource("b");

        // 初始无ThreadLocal值
        assertEquals("none", resolveCurrent(ds));

        // runWith结束后恢复为空
        String used = DynamicDataSource.runWith(a, () -> resolveCurrent(ds));
        assertEquals("a", used);
        assertEquals("none", resolveCurrent(ds));

        // 嵌套切换：内层结束后恢复外层的值
        String inner = DynamicDataSource.runWith(a, () -> DynamicDataSource.runWith(b, () -> resolveCurrent(ds)));
        assertEquals("b", inner);
        assertEquals("none", resolveCurrent(ds));
    }

    @Test
    public void testRunWithRestoresOnException() {
        DynamicDataSource ds = new DynamicDataSource();
        FakeDataSource a = new FakeDataSource("a");

        assertThrows(IllegalStateException.class, () -> DynamicDataSource.runWith(a, () -> {
            throw new IllegalStateException("boom");
        }));

        assertEquals("none", resolveCurrent(ds), "ThreadLocal must be cleaned even if task throws");
    }

    @Test
    public void testRunWithKeepsPreexistingSwitch() {
        DynamicDataSource ds = new DynamicDataSource();
        FakeDataSource a = new FakeDataSource("a");
        FakeDataSource b = new FakeDataSource("b");

        // 已有手工switch的值时，runWith结束后恢复该值而不是清理
        ds.switchDataSource(a);
        try {
            DynamicDataSource.runWith(b, () -> "ok");
            assertEquals("a", resolveCurrent(ds));
        } finally {
            new DynamicDataSource().switchDataSource(null);
        }
    }
}

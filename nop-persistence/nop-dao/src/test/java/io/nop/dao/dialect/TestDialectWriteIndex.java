/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:   https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.dialect;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.initialize.CoreInitialization;
import io.nop.dao.jdbc.dataset.JdbcDataSet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * check2 处置新增：方言/数据集写路径的 0-based 索引契约与空名守卫（不依赖数据库，
 * 用 ResultSet 动态代理记录调用）。
 */
public class TestDialectWriteIndex {

    private static IDialect dialect;

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
        dialect = DialectManager.instance().getDialect("h2");
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    /** 记录 updateNull/updateObject 调用的 ResultSet 桩。 */
    static class RecordingResultSet implements InvocationHandler {
        final List<String> calls = new ArrayList<>();

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if (method.getName().startsWith("update"))
                calls.add(method.getName() + ":" + (args == null ? "" : String.valueOf(args[0])));
            return null;
        }

        static ResultSet create(RecordingResultSet handler) {
            return (ResultSet) Proxy.newProxyInstance(TestDialectWriteIndex.class.getClassLoader(),
                    new Class[]{ResultSet.class}, handler);
        }
    }

    /**
     * DialectImpl.jdbcSet 空字符串转 null（convertStringToNull 缺省 true）时，
     * updateNull 必须与 updateObject 一样做 0-based → 1-based 转换（此前少 +1 写错列）。
     */
    @Test
    public void testJdbcSetEmptyStringNullIndexConverted() {
        RecordingResultSet recorder = new RecordingResultSet();
        dialect.jdbcSet(RecordingResultSet.create(recorder), 0, "");
        assertEquals(List.of("updateNull:1"), recorder.calls,
                "0-based index 0 must map to JDBC column 1 for updateNull");
    }

    /** 非空字符串走 updateObject 正常路径，同样 +1。 */
    @Test
    public void testJdbcSetNormalValueIndexConverted() {
        RecordingResultSet recorder = new RecordingResultSet();
        dialect.jdbcSet(RecordingResultSet.create(recorder), 2, "abc");
        assertEquals(List.of("updateObject:3"), recorder.calls);
    }

    /** JdbcDataSet.setJsonString 与其它 set 方法一样 0-based → 1-based。 */
    @Test
    public void testSetJsonStringIndexConverted() {
        RecordingResultSet recorder = new RecordingResultSet();
        JdbcDataSet dataSet = new JdbcDataSet(dialect, RecordingResultSet.create(recorder));
        dataSet.setJsonString(1, "{\"a\":1}");
        assertEquals(1, recorder.calls.size());
        assertEquals("updateObject:2", recorder.calls.get(0));
    }

    /** escapeSQLName 空串抛带参数的 NopException，而非裸 StringIndexOutOfBoundsException。 */
    @Test
    public void testEscapeSQLNameEmptyRejected() {
        NopException ex = assertThrows(NopException.class, () -> dialect.escapeSQLName(""));
        assertEquals("nop.err.dao.dialect.invalid-sql-name", ex.getErrorCode());
        assertTrue(ex.getParam("name") == null || "".equals(ex.getParam("name")));
    }
}

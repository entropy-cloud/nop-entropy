/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.jdbc;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.dialect.IDialect;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.BatchUpdateException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 回归覆盖审查报告 DAO-01：批处理部分成功时回调的计数语义。
 * <p>
 * 1. BatchUpdateException 处理循环中 SUCCESS_NO_INFO 命令回调收到 null 计数，
 * 上层 EntityPersisterImpl.checkUpdateResult(int) 自动拆箱直接 NPE；
 * 2. 正常成功路径驱动返回 SUCCESS_NO_INFO(-2) 且 dialect 声称支持批计数时，
 * checkUpdateResult(-2) 因 count&gt;1 误抛 ERR_ORM_UPDATE_ENTITY_MULTIPLE_ROWS。
 */
public class TestJdbcBatcherNoInfo {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    /** 模拟上层回调：与 EntityPersisterImpl.checkUpdateResult 一样立即拆箱 int */
    static final class RecordingCallback implements java.util.function.BiConsumer<Integer, Throwable> {
        final List<Integer> counts = new ArrayList<>();
        final List<Throwable> errors = new ArrayList<>();

        @Override
        public void accept(Integer count, Throwable e) {
            if (e != null) {
                errors.add(e);
                return;
            }
            // 复现生产路径的自动拆箱：null 计数在此处抛 NPE
            counts.add(count == null ? -999 : count);
        }
    }

    private static IDialect dialectWithCountSupport(IDialect base, final boolean supportCount) {
        return (IDialect) Proxy.newProxyInstance(TestJdbcBatcherNoInfo.class.getClassLoader(),
                new Class[]{IDialect.class}, new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        if (method.getName().equals("isSupportBatchUpdateCount"))
                            return supportCount;
                        if (method.getName().equals("isSupportBatchUpdate"))
                            return true;
                        try {
                            return method.invoke(base, args);
                        } catch (java.lang.reflect.InvocationTargetException e) {
                            throw e.getCause();
                        }
                    }
                });
    }

    private static PreparedStatement statementReturning(final int[] updateCounts, final BatchUpdateException batchError) {
        return (PreparedStatement) Proxy.newProxyInstance(TestJdbcBatcherNoInfo.class.getClassLoader(),
                new Class[]{PreparedStatement.class}, new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        if (method.getName().equals("executeBatch")) {
                            if (batchError != null)
                                throw batchError;
                            return updateCounts;
                        }
                        if (method.getName().equals("close") || method.getName().equals("addBatch")
                                || method.getName().equals("setObject") || method.getName().equals("setNull"))
                            return null;
                        if (method.getName().equals("toString"))
                            return "stub-statement";
                        return null;
                    }
                });
    }

    private static Connection connectionReturning(final PreparedStatement ps) {
        return (Connection) Proxy.newProxyInstance(TestJdbcBatcherNoInfo.class.getClassLoader(),
                new Class[]{Connection.class}, new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        if (method.getName().equals("prepareStatement"))
                            return ps;
                        if (method.getName().equals("toString"))
                            return "stub-connection";
                        return null;
                    }
                });
    }

    private static String plainSql(String tag) {
        // 无参数标记的SQL，避免JdbcHelper.setParameters触碰statement
        return "update t_stub set v = 1 where id = " + tag;
    }

    @Test
    public void testBatchUpdateExceptionNoInfoCallbackGetsNormalizedCount() {
        RecordingCallback cb1 = new RecordingCallback();
        RecordingCallback cb2 = new RecordingCallback();

        BatchUpdateException batchError = new BatchUpdateException("mock partial failure",
                new int[]{Statement.SUCCESS_NO_INFO, Statement.EXECUTE_FAILED});
        PreparedStatement ps = statementReturning(null, batchError);
        Connection conn = connectionReturning(ps);

        JdbcBatcher batcher = new JdbcBatcher(conn, dialectWithCountSupport(getH2Dialect(), true), null);
        batcher.addCommand(new SQL(plainSql("1")), true, cb1);
        batcher.addCommand(new SQL(plainSql("1")), true, cb2);

        // stopOnError=true 时批处理失败最终抛出，此前逐条回调已派发
        assertThrows(RuntimeException.class, batcher::flush);

        assertEquals(1, cb2.errors.size(), "EXECUTE_FAILED命令应收到错误回调");
        assertEquals(0, cb2.counts.size());
        assertEquals(1, cb1.counts.size(), "SUCCESS_NO_INFO命令应收到成功回调");
        // 修复前此处收到 -999（null计数拆箱NPE的等价标记）
        assertEquals(1, cb1.counts.get(0), "SUCCESS_NO_INFO应归一化为单行成功计数1");
    }

    @Test
    public void testBatchSuccessNoInfoNormalizedEvenWhenDialectClaimsCountSupport() {
        RecordingCallback cb1 = new RecordingCallback();
        RecordingCallback cb2 = new RecordingCallback();

        PreparedStatement ps = statementReturning(
                new int[]{Statement.SUCCESS_NO_INFO, 2}, null);
        Connection conn = connectionReturning(ps);

        JdbcBatcher batcher = new JdbcBatcher(conn, dialectWithCountSupport(getH2Dialect(), true), null);
        batcher.addCommand(new SQL(plainSql("1")), true, cb1);
        batcher.addCommand(new SQL(plainSql("1")), true, cb2);

        batcher.flush();

        assertEquals(1, cb1.counts.get(0),
                "SUCCESS_NO_INFO(-2)不能原样传给上层，checkUpdateResult(-2)会误报多行更新");
        assertEquals(2, cb2.counts.get(0));
        assertTrue(cb1.errors.isEmpty());
    }

    private IDialect getH2Dialect() {
        return io.nop.dao.dialect.DialectManager.instance().getDialect("h2");
    }
}

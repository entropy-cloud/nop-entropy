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
import io.nop.dao.metrics.IDaoMetrics;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.BatchUpdateException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    /**
     * 批量失败时的回调契约：驱动确认成功的命令必须回调(count, null)，
     * 未确认/失败的命令在stopOnError时必须回调(null, error)，不允许静默丢失
     */
    @Test
    public void testFlushBatchFailCallbackSemantics() throws Exception {
        jdbc().executeUpdate(new SQL("create table batcher_entity(id int primary key, v int)"));
        jdbc().executeUpdate(new SQL("insert into batcher_entity(id, v) values(1, 11)"));

        List<Integer> counts = new ArrayList<>();
        List<Throwable> errors = new ArrayList<>();

        try (Connection conn = getDataSource().getConnection()) {
            JdbcBatcher batcher = new JdbcBatcher(conn, getDialect(), null);
            batcher.setBatchSize(10);

            // 第一条成功，第二条与已存在主键冲突，第三条因stopOnError不再执行
            batcher.addCommand(insertSql(2, 22), true, (count, e) -> {
                counts.add(count);
                errors.add(e);
            });
            batcher.addCommand(insertSql(1, 33), true, (count, e) -> {
                counts.add(count);
                errors.add(e);
            });
            batcher.addCommand(insertSql(3, 44), true, (count, e) -> {
                counts.add(count);
                errors.add(e);
            });

            assertThrows(NopException.class, batcher::flush);
        }

        assertEquals(3, counts.size(), "all three commands must receive exactly one callback");
        assertEquals(3, errors.size());

        // H2在批量中遇到失败后仍会继续执行后续语句，updateCounts为[1,-3,1]：
        // 驱动确认成功的两条命令必须回调(count, null)，失败的那条回调(null, cause)
        assertEquals(1, counts.get(0).intValue());
        assertNull(errors.get(0));
        assertNotNull(errors.get(1));
        assertEquals(1, counts.get(2).intValue());
        assertNull(errors.get(2));

        // 成功的两条插入生效，冲突的一条不生效
        assertEquals(3L, jdbc().findLong(new SQL("select count(*) from batcher_entity"), 0L));
    }

    // ------------------------------------------------------------------
    // check2 处置新增：计数语义 / 回调契约 / forceTxn 原子性 / metrics 计时
    // （stub Connection + PreparedStatement，不依赖真实数据库行为）
    // ------------------------------------------------------------------

    /**
     * 可配置行为的 PreparedStatement 桩：executeBatch 返回两条命令的结果数组（值由外部指定），
     * 其余方法均为无操作（参数绑定经 JdbcHelper 调 setXXX，无需真实语义）。
     */
    static PreparedStatement stubPreparedStatement(java.util.function.IntSupplier executeBatchValue) {
        return (PreparedStatement) Proxy.newProxyInstance(TestJdbcBatcher.class.getClassLoader(),
                new Class[]{PreparedStatement.class}, new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        switch (method.getName()) {
                            case "executeBatch":
                                return new int[]{executeBatchValue.getAsInt(), executeBatchValue.getAsInt()};
                            case "executeUpdate":
                                return 1;
                            default:
                                return null;
                        }
                    }
                });
    }

    /** prepareStatement 工厂：允许抛 SQLException 以模拟驱动失败。 */
    interface PsFactory {
        PreparedStatement prepare() throws SQLException;
    }

    static PsFactory failingPrepare() {
        return () -> {
            throw new SQLException("stub-prepare-fail");
        };
    }

    /**
     * Connection 桩：prepareStatement 委托外部工厂，getAutoCommit=true，commit/rollback 被记录。
     */
    static Connection stubConnection(PsFactory psFactory, List<String> txnActions) {
        return (Connection) Proxy.newProxyInstance(TestJdbcBatcher.class.getClassLoader(),
                new Class[]{Connection.class}, new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        switch (method.getName()) {
                            case "prepareStatement":
                                return psFactory.prepare();
                            case "getAutoCommit":
                                return true;
                            case "setAutoCommit":
                                if (txnActions != null)
                                    txnActions.add("setAutoCommit:" + args[0]);
                                return null;
                            case "commit":
                                if (txnActions != null)
                                    txnActions.add("commit");
                                return null;
                            case "rollback":
                                if (txnActions != null)
                                    txnActions.add("rollback");
                                return null;
                            default:
                                if (method.getReturnType() == boolean.class)
                                    return false;
                                return null;
                        }
                    }
                });
    }

    private static void addRecordingCommand(JdbcBatcher batcher, List<Integer> counts, List<Throwable> errors) {
        batcher.addCommand(insertSql(counts.size() + 1, counts.size() + 11), true, (count, e) -> {
            counts.add(count);
            errors.add(e);
        });
    }

    /** EXECUTE_FAILED(-3) 不伪造为成功：按影响0行回调，让乐观锁检查能发现失败。 */
    @Test
    public void testExecuteFailedReportedAsZeroNotSuccess() {
        List<Integer> counts = new ArrayList<>();
        List<Throwable> errors = new ArrayList<>();
        // 两条命令才走批量路径（单条走 executeOne）
        Connection conn = stubConnection(() -> stubPreparedStatement(() -> Statement.EXECUTE_FAILED), null);

        JdbcBatcher batcher = new JdbcBatcher(conn, getDialect(), null);
        batcher.setBatchSize(10);
        addRecordingCommand(batcher, counts, errors);
        addRecordingCommand(batcher, counts, errors);
        batcher.flush();

        assertEquals(2, counts.size());
        assertEquals(0, counts.get(0).intValue(), "EXECUTE_FAILED must not be faked as 1-row success");
        assertEquals(0, counts.get(1).intValue());
        assertNull(errors.get(0));
        assertNull(errors.get(1));
    }

    /** singleChange + SUCCESS_NO_INFO(-2)：计数不可信时保守假定为1行成功（既有约定显式化）。 */
    @Test
    public void testSuccessNoInfoAssumedSingleRowForSingleChange() {
        List<Integer> counts = new ArrayList<>();
        List<Throwable> errors = new ArrayList<>();
        Connection conn = stubConnection(() -> stubPreparedStatement(() -> Statement.SUCCESS_NO_INFO), null);

        JdbcBatcher batcher = new JdbcBatcher(conn, getDialect(), null);
        batcher.setBatchSize(10);
        addRecordingCommand(batcher, counts, errors);
        addRecordingCommand(batcher, counts, errors);
        batcher.flush();

        assertEquals(2, counts.size());
        assertEquals(1, counts.get(0).intValue(), "SUCCESS_NO_INFO for singleChange assumed as 1");
        assertEquals(1, counts.get(1).intValue());
        assertNull(errors.get(0));
        assertNull(errors.get(1));
    }

    /**
     * stopOnError=false 且驱动中途停止（updateCounts 少于命令数）：残留命令必须回调失败并清空，
     * 不得混入后续批次（旧实现残留命令的旧参数会塞进新 SQL 的 PreparedStatement）。
     */
    @Test
    public void testResidualCommandsFailedAndClearedWhenDriverStops() {
        List<Integer> counts = new ArrayList<>();
        List<Throwable> errors = new ArrayList<>();
        // 3条命令，executeBatch 抛 BatchUpdateException 且 updateCounts 只有1条（驱动确认第1条后停止）
        PreparedStatement throwing = (PreparedStatement) Proxy.newProxyInstance(
                TestJdbcBatcher.class.getClassLoader(), new Class[]{PreparedStatement.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        if (method.getName().equals("executeBatch"))
                            throw new BatchUpdateException(new int[]{1});
                        return null;
                    }
                });
        Connection conn = stubConnection(() -> throwing, null);

        JdbcBatcher batcher = new JdbcBatcher(conn, getDialect(), null);
        batcher.setBatchSize(10);
        batcher.setStopOnError(false);
        addRecordingCommand(batcher, counts, errors);
        addRecordingCommand(batcher, counts, errors);
        addRecordingCommand(batcher, counts, errors);
        batcher.flush();

        // 第1条被驱动确认成功；第2、3条是残留命令：必须回调(null, error)而非静默丢失
        assertEquals(3, counts.size(), "every command must receive exactly one callback");
        assertEquals(1, counts.get(0).intValue());
        assertNull(errors.get(0));
        assertNotNull(errors.get(1), "residual command #2 must be failed");
        assertNotNull(errors.get(2), "residual command #3 must be failed");

        // 队列已清空：新命令进入新批次正常执行，不携带残留
        List<Integer> counts2 = new ArrayList<>();
        List<Throwable> errors2 = new ArrayList<>();
        Connection conn3 = stubConnection(() -> stubPreparedStatement(() -> 1), null);
        JdbcBatcher batcher3 = new JdbcBatcher(conn3, getDialect(), null);
        batcher3.setBatchSize(10);
        batcher3.setStopOnError(false);
        addRecordingCommand(batcher3, counts2, errors2);
        batcher3.flush();
        assertEquals(1, counts2.size());
        assertNull(errors2.get(0));
    }

    /** 批准备阶段（prepareStatement/addBatch）失败：所有命令统一回调失败并清空，随后批次不受残留污染。 */
    @Test
    public void testPrepareFailFailsAllCommandsAndKeepsQueueClean() {
        List<Integer> counts = new ArrayList<>();
        List<Throwable> errors = new ArrayList<>();
        Connection conn = stubConnection(TestJdbcBatcher.failingPrepare(), null);

        JdbcBatcher batcher = new JdbcBatcher(conn, getDialect(), null);
        batcher.setBatchSize(10);
        addRecordingCommand(batcher, counts, errors);
        addRecordingCommand(batcher, counts, errors);
        assertThrows(Exception.class, batcher::flush);

        assertEquals(2, counts.size(), "all commands must be failed on prepare error");
        assertNotNull(errors.get(0));
        assertNotNull(errors.get(1));

        // 同一 batcher 复用：新命令正常执行（残留命令已清空）
        List<Integer> counts2 = new ArrayList<>();
        List<Throwable> errors2 = new ArrayList<>();
        Connection conn2 = stubConnection(() -> stubPreparedStatement(() -> 1), null);
        JdbcBatcher batcher2 = new JdbcBatcher(conn2, getDialect(), null);
        batcher2.setBatchSize(10);
        addRecordingCommand(batcher2, counts2, errors2);
        batcher2.flush();
        assertEquals(1, counts2.size());
        assertNull(errors2.get(0));
    }

    /**
     * forceTxn 批处理是原子单元：失败时整体回滚，所有命令（含驱动已确认成功的）统一回调失败——
     * 不允许"先回调成功、随后被整体回滚"的内存/数据库状态背离。
     */
    @Test
    public void testForceTxnBatchFailRollsBackAndFailsAllCommands() {
        List<Integer> counts = new ArrayList<>();
        List<Throwable> errors = new ArrayList<>();
        List<String> txnActions = new ArrayList<>();
        PreparedStatement throwing = (PreparedStatement) Proxy.newProxyInstance(
                TestJdbcBatcher.class.getClassLoader(), new Class[]{PreparedStatement.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        if (method.getName().equals("executeBatch"))
                            throw new BatchUpdateException(new int[]{1});
                        return null;
                    }
                });
        Connection conn = stubConnection(() -> throwing, txnActions);

        JdbcBatcher batcher = new JdbcBatcher(conn, getDialect(), null);
        batcher.setBatchSize(10);
        batcher.setForceTxn(true);
        addRecordingCommand(batcher, counts, errors);
        addRecordingCommand(batcher, counts, errors);
        assertThrows(Exception.class, batcher::flush);

        // 全部命令回调失败（第1条虽被驱动确认 ret[0]=1，但事务已整体回滚）
        assertEquals(2, counts.size());
        assertNotNull(errors.get(0), "driver-confirmed command must also be failed after rollback");
        assertNotNull(errors.get(1));
        assertTrue(txnActions.contains("rollback"), "forceTxn failure must roll back: " + txnActions);
        assertTrue(txnActions.contains("setAutoCommit:false"), txnActions.toString());
    }

    /** prepareStatement 抛异常时 daoMetrics 计时器必须关闭（finally 兜底）。 */
    @Test
    public void testMetricsEndedWhenPrepareFails() {
        List<String> meterActions = new ArrayList<>();
        IDaoMetrics metrics = (IDaoMetrics) Proxy.newProxyInstance(TestJdbcBatcher.class.getClassLoader(),
                new Class[]{IDaoMetrics.class}, (proxy, method, args) -> {
                    meterActions.add(method.getName());
                    return null;
                });
        Connection conn = stubConnection(TestJdbcBatcher.failingPrepare(), null);

        JdbcBatcher batcher = new JdbcBatcher(conn, getDialect(), metrics);
        batcher.setBatchSize(10);
        // 两条命令才走批量路径（计量修复点在批量分支的 prepareStatement 边界）
        batcher.addCommand(insertSql(1, 1), true, null);
        batcher.addCommand(insertSql(2, 2), true, null);
        assertThrows(Exception.class, batcher::flush);

        assertTrue(meterActions.contains("beginBatchUpdate"), meterActions.toString());
        assertTrue(meterActions.contains("endBatchUpdate"),
                "endBatchUpdate must be called even when prepareStatement fails: " + meterActions);
    }

    /** flushNoBatch（单条路径）stopOnError 中断时：剩余命令统一回调失败，不静默丢失。 */
    @Test
    public void testExecuteOneStopOnErrorFailsRemainingCommands() {
        List<Integer> counts = new ArrayList<>();
        List<Throwable> errors = new ArrayList<>();
        PreparedStatement failing = (PreparedStatement) Proxy.newProxyInstance(
                TestJdbcBatcher.class.getClassLoader(), new Class[]{PreparedStatement.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        if (method.getName().equals("executeUpdate"))
                            throw new SQLException("stub-update-fail");
                        return null;
                    }
                });
        Connection conn = stubConnection(() -> failing, null);

        JdbcBatcher batcher = new JdbcBatcher(conn, getDialect(), null);
        batcher.setBatchSize(10);
        batcher.disableBatch(); // 强制 flushNoBatch 逐条路径（executeOne）
        addRecordingCommand(batcher, counts, errors);
        addRecordingCommand(batcher, counts, errors);
        assertThrows(Exception.class, batcher::flush);

        assertEquals(2, counts.size(), "remaining command must be failed when stopOnError interrupts");
        assertNotNull(errors.get(0));
        assertNotNull(errors.get(1));
    }
}

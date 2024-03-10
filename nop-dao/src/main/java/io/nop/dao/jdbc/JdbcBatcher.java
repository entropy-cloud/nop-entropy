/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.jdbc;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.commons.util.IoHelper;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.jdbc.impl.JdbcHelper;
import io.nop.dao.metrics.IDaoMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.BatchUpdateException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.function.BiConsumer;

import static io.nop.dao.DaoConfigs.CFG_DAO_JDBC_DISABLE_BATCH_UPDATE;
import static io.nop.dao.DaoConfigs.CFG_DAO_JDBC_MAX_BATCH_UPDATE_SIZE;

/**
 * 辅助实现jdbc批量处理的帮助类。具体用法如下：
 *
 * <pre>
 * <code>
 *   batcher = new JdbcBatcher(conn, dialect);
 *   for(...){
 *   	batcher.addSql(sql,singleChange,callback);
 *   }
 *   batcher.flush();
 * </code>
 * </pre>
 *
 * @author canonical_entropy@163.com
 */
public class JdbcBatcher {
    static final Logger LOG = LoggerFactory.getLogger(JdbcBatcher.class);

    private final Connection conn;
    private final IDialect dialect;
    private final IDaoMetrics daoMetrics;

    private String sql;

    private Deque<BatchCommand> commands = new ArrayDeque<>();

    private int batchSize = CFG_DAO_JDBC_MAX_BATCH_UPDATE_SIZE.get();

    /**
     * 是否禁用批处理机制。可能通过全局开关禁止。当dialect不支持batchUpdate时也会自动禁止。
     */
    private boolean batchDisabled;

    /**
     * 是否强制打开事务，在事务中执行批量提交
     */
    private boolean forceTxn = false;

    /**
     * 当出现异常时是否不再执行后续的语句。一般情况下为true。 作为测试数据插入时可能会设置stopOnError=false
     */
    private boolean stopOnError = true;

    private boolean checkSingleChange = true;

    public JdbcBatcher(Connection conn, IDialect dialect, IDaoMetrics daoMetrics) {
        this.conn = conn;
        this.dialect = dialect;
        this.daoMetrics = daoMetrics;
        if (CFG_DAO_JDBC_DISABLE_BATCH_UPDATE.get() || !dialect.isSupportBatchUpdate())
            this.batchDisabled = true;

        if (!dialect.isSupportBatchUpdateCount())
            this.checkSingleChange = dialect.isSupportBatchUpdateCount();
    }

    private static class BatchCommand {
        final SQL sql;
        final boolean singleChange;
        final BiConsumer<Integer, Throwable> callback;

        public BatchCommand(SQL sql, boolean singleChange, BiConsumer<Integer, Throwable> callback) {
            this.sql = sql;
            this.singleChange = singleChange;
            this.callback = callback;
        }

        public void onComplete(Integer o, Throwable exception) {
            if (callback != null)
                callback.accept(o, exception);
        }
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public void disableBatch() {
        this.batchDisabled = true;
    }

    public boolean isForceTxn() {
        return forceTxn;
    }

    public void setForceTxn(boolean forceTxn) {
        this.forceTxn = forceTxn;
    }

    public boolean isStopOnError() {
        return stopOnError;
    }

    public void setStopOnError(boolean stopOnError) {
        this.stopOnError = stopOnError;
    }

    public void addCommand(SQL sql, boolean singleChange, BiConsumer<Integer, Throwable> callback) {
        if (this.sql != null && !Objects.equals(this.sql, sql.getText())) {
            flush();
        }
        this.sql = sql.getText();

        commands.addLast(new BatchCommand(sql, singleChange, callback));
        if (commands.size() >= batchSize) {
            this.flush();
        }
    }

    public void flush() {
        if (this.commands.isEmpty())
            return;

        boolean resetAutoCommit = false;

        SQL batchSql = commands.getFirst().sql;
        try {
            if (batchDisabled || commands.size() == 1) {
                this.flushNoBatch();
            } else {
                if (forceTxn && conn.getAutoCommit()) {
                    conn.setAutoCommit(false);
                    resetAutoCommit = true;
                }

                LOG.trace("jdbc.executeBatch_begin:count={}", commands.size());
                long beginTime = CoreMetrics.nanoTime();
                Object meter = daoMetrics == null ? null : daoMetrics.beginBatchUpdate(sql);
                int commandCount = commands.size();
                PreparedStatement ps = conn.prepareStatement(sql);
                try {
                    for (BatchCommand params : commands) {
                        setParams(ps, params);
                        dump(params, "jdbcBatcher.addBatch");
                        ps.addBatch();
                    }
                    int[] ret = ps.executeBatch();
                    int i = 0;
                    for (BatchCommand params : commands) {
                        onSuccess(params, ret[i]);
                        i++;
                    }

                    commands.clear();
                    long diffTime = CoreMetrics.nanoTimeDiff(beginTime);

                    LOG.info("nop.jdbc.execute-batch-success:count={},usedTime={},sql={}", i,
                            CoreMetrics.nanoToMillis(diffTime), sql);

                } catch (BatchUpdateException e) {
                    SQLException cause = getCause(e);

                    long diffTime = CoreMetrics.nanoTimeDiff(beginTime);
                    LOG.info("nop.jdbc.execute-batch-fail:usedTime={},sql={}", CoreMetrics.nanoToMillis(diffTime), sql);

                    // 返回的数组个数可能小于批量命令数
                    int[] ret = e.getUpdateCounts();

                    try {
                        int i = 0;
                        BatchCommand params;
                        while (i < ret.length && (params = commands.pollFirst()) != null) {
                            if (ret[i] >= 0) {
                                LOG.debug("nop.jdbc.execute-batch-result-success:sql={}", params.sql);
                            } else {
                                LOG.error("nop.jdbc.execute-batch-result-fail:sql={}", params.sql);
                            }
                            params.onComplete(null, cause);
                            i++;
                        }
                    } catch (Exception e2) {
                        if (resetAutoCommit)
                            conn.rollback();
                        throw NopException.adapt(e2);
                    }

                    if (resetAutoCommit)
                        conn.rollback();

                    if (stopOnError)
                        throw cause;
                } finally {
                    if (daoMetrics != null)
                        daoMetrics.endExecuteUpdate(meter, commandCount);
                }

                // 如果是batch主动打开的transaction,它需要主动commit。setAutoCommit(true)不一定自动调用commit
                if (resetAutoCommit)
                    conn.commit();
            }
        } catch (SQLException e) {
            throw dialect.getSQLExceptionTranslator().translate(batchSql, e);
        } finally {
            if (resetAutoCommit) {
                try {
                    conn.setAutoCommit(true);
                } catch (Exception e) {
                    LOG.info("nop.jdbc.set-auto-commit-fail", e);
                }
            }
            this.sql = null;
        }
    }

    SQLException getCause(BatchUpdateException e) {
        if (e.getCause() instanceof SQLException)
            return (SQLException) e.getCause();
        return e;
    }

    void setParams(PreparedStatement ps, BatchCommand params) throws SQLException {
        JdbcHelper.setParameters(dialect, ps, params.sql);
    }

    void flushNoBatch() {
        do {
            BatchCommand params = this.commands.pollFirst();
            if (params == null)
                break;

            executeOne(params);
        } while (true);
    }

    void executeOne(BatchCommand params) {
        dump(params, "jdbcBatcher.executeOne");
        long beginTime = CoreMetrics.nanoTime();

        Object meter = daoMetrics == null ? null : daoMetrics.beginBatchUpdate(sql);
        PreparedStatement ps = null;
        try {
            ps = conn.prepareStatement(sql);
            setParams(ps, params);
            int count = ps.executeUpdate();
            long diffTime = CoreMetrics.nanoTimeDiff(beginTime);

            LOG.info("nop.jdbc.flush-execute-update-result:count={},usedTime={},sql={}", count,
                    CoreMetrics.nanoToMillis(diffTime), sql);
            onSuccess(params, count);
        } catch (SQLException e) {
            params.onComplete(null, e);

            long diffTime = CoreMetrics.nanoTimeDiff(beginTime);

            LOG.error("nop.jdbc.flush-execute-update-fail:usedTime={},sql={}", CoreMetrics.nanoToMillis(diffTime), sql);
            if (stopOnError)
                throw dialect.getSQLExceptionTranslator().translate(params.sql, e);
        } finally {
            IoHelper.safeClose(ps);
            if (daoMetrics != null)
                daoMetrics.endExecuteUpdate(meter, 1);
        }
    }

    void onSuccess(BatchCommand command, int updateCount) {
        if (command.singleChange && !checkSingleChange) {
            if (updateCount < 0)
                updateCount = 1;
        }
        command.onComplete(updateCount, null);
    }

    void dump(BatchCommand params, String title) {
        params.sql.dump(title);
    }
}
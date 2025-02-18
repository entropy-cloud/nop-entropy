/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.nop.api.core.beans.LongRangeBean;
import io.nop.commons.metrics.GlobalMeterRegistry;
import io.nop.core.lang.sql.SQL;
import io.nop.core.stat.JdbcSqlStat;
import io.nop.core.stat.JdbcStatManager;
import io.nop.core.stat.StatementExecuteType;

public class DaoMetricsImpl implements IDaoMetrics {
    private final MeterRegistry registry;
    private final String prefix;

    private final Counter transactionOpen;
    private final Counter transactionSuccess;
    private final Counter transactionFail;
    private final Counter connectionsObtained;

    private final Timer queryTimer;
    private final Timer updateTimer;
    private final Timer batchUpdateTimer;

    private final Counter rowReadCount;
    private final Counter rowUpdateCount;
    private final JdbcStatManager statManager;

    public DaoMetricsImpl() {
        this(GlobalMeterRegistry.instance(), JdbcStatManager.global(), null);
    }

    public DaoMetricsImpl(MeterRegistry registry, JdbcStatManager statManager, String prefix) {
        this.statManager = statManager;
        this.registry = registry;
        this.prefix = prefix;

        transactionOpen = registry.counter("nop.dao.transactions.open");
        transactionSuccess = registry.counter("nop.dao.transactions.success");
        transactionFail = registry.counter("nop.dao.transactions.failure");
        connectionsObtained = registry.counter("nop.dao.connections.obtained");

        queryTimer = registry.timer("nop.dao.query.timer");
        updateTimer = registry.timer("nop.dao.update.timer");
        batchUpdateTimer = registry.timer("nop.dao.batch-update.timer");

        rowReadCount = registry.counter("nop.dao.rows.read-count");
        rowUpdateCount = registry.counter("nop.dao.rows.update-count");
    }

    public MeterRegistry getRegistry() {
        return registry;
    }

    public String getPrefix() {
        return prefix;
    }


    @Override
    public void onTransactionOpen(String txnGroup) {
        transactionOpen.increment();
    }

    @Override
    public void onTransactionSuccess(String txnGroup) {
        transactionSuccess.increment();
    }

    @Override
    public void onTransactionFailure(String txnGroup) {
        transactionFail.increment();
    }

    @Override
    public void onObtainConnection() {
        connectionsObtained.increment();
    }

    @Override
    public Object beginQuery(SQL sql, LongRangeBean range) {
        return Timer.start(registry);
    }

    @Override
    public void endQuery(SQL sql, Object meter, long readCount, Exception error) {
        long time = ((Timer.Sample) meter).stop(queryTimer);
        this.rowReadCount.increment(readCount);

        if (sql != null && !sql.isEmpty()) {
            JdbcSqlStat stat = statManager.getSqlStat(sql.getText());
            stat.addExecuteTime(StatementExecuteType.ExecuteQuery, false, time);
            if (readCount > 0)
                stat.addFetchRowCount(readCount);

            if (error == null) {
                stat.incrementExecuteSuccessCount();
            } else {
                stat.error(error);
            }
        }
    }

    @Override
    public Object beginExecuteUpdate(SQL sql) {
        return Timer.start(registry);
    }

    @Override
    public void endExecuteUpdate(SQL sql, Object meter, long updateCount, Exception error) {
        Timer.Sample sample = (Timer.Sample) meter;
        long time = sample.stop(updateTimer);
        if (updateCount > 0) {
            this.rowUpdateCount.increment(updateCount);
        }

        if (sql != null && !sql.isEmpty()) {
            JdbcSqlStat stat = statManager.getSqlStat(sql.getText());
            stat.addExecuteTime(StatementExecuteType.ExecuteUpdate, false, time);
            int delta = (int) updateCount;
            if (delta > 0)
                stat.addUpdateCount((int) updateCount);

            if (error == null) {
                stat.incrementExecuteSuccessCount();
            } else {
                stat.error(error);
            }
        }
    }

    @Override
    public Object beginBatchUpdate(String sql) {
        return Timer.start(registry);
    }

    @Override
    public void endBatchUpdate(String sql, Object meter, long count, Exception error) {
        long time = ((Timer.Sample) meter).stop(batchUpdateTimer);
        if (count > 0)
            this.rowUpdateCount.increment(count);

        if (sql != null && !sql.isEmpty()) {
            JdbcSqlStat stat = statManager.getSqlStat(sql);
            stat.addExecuteTime(StatementExecuteType.ExecuteBatch, false, time);
            int delta = (int) count;
            if (delta > 0)
                stat.addExecuteBatchCount(count);

            if (error == null) {
                stat.incrementExecuteSuccessCount();
            } else {
                stat.error(error);
            }
        }
    }
}

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

    public DaoMetricsImpl() {
        this(GlobalMeterRegistry.instance(), null);
    }

    public DaoMetricsImpl(MeterRegistry registry, String prefix) {
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
    public void endQuery(Object meter, long readCount, boolean success) {
        ((Timer.Sample) meter).stop(queryTimer);
        this.rowReadCount.increment(readCount);
    }

    @Override
    public Object beginExecuteUpdate(SQL sql) {
        return Timer.start(registry);
    }

    @Override
    public void endExecuteUpdate(Object meter, long updateCount) {
        ((Timer.Sample) meter).stop(updateTimer);
        if (updateCount > 0) {
            this.rowUpdateCount.increment(updateCount);
        }
    }

    @Override
    public Object beginBatchUpdate(String sql) {
        return Timer.start(registry);
    }

    @Override
    public void endBatchUpdate(Object meter, long count) {
        ((Timer.Sample) meter).stop(batchUpdateTimer);
        if (count > 0)
            this.rowUpdateCount.increment(count);
    }
}

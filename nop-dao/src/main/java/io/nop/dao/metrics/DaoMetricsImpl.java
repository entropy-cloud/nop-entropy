/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.dao.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.nop.api.core.beans.LongRangeBean;
import io.nop.core.lang.sql.SQL;

public class DaoMetricsImpl implements IDaoMetrics {
    private final MeterRegistry registry;
    private final String prefix;

    public DaoMetricsImpl(MeterRegistry registry, String prefix) {
        this.registry = registry;
        this.prefix = prefix;
    }

    public MeterRegistry getRegistry() {
        return registry;
    }

    public String getPrefix() {
        return prefix;
    }

    @Override
    public void onTransactionSuccess() {

    }

    @Override
    public void onTransactionFailure() {

    }

    @Override
    public void onObtainConnection() {

    }

    @Override
    public Object beginQuery(SQL sql, LongRangeBean range) {
        return null;
    }

    @Override
    public void endQuery(Object meter, long readCount, boolean success) {

    }

    @Override
    public Object beginExecuteUpdate(SQL sql) {
        return null;
    }

    @Override
    public void endExecuteUpdate(Object meter, long updateCount) {

    }

    @Override
    public Object beginBatchUpdate(String sql) {
        return null;
    }

    @Override
    public void endBatchUpdate(Object meter, long count) {

    }
}

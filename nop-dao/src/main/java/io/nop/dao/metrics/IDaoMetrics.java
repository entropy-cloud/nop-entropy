/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.dao.metrics;

import io.nop.api.core.beans.LongRangeBean;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.txn.ITransactionMetrics;

public interface IDaoMetrics extends ITransactionMetrics {


    /**
     * Counter: nop.dao.connections.obtained
     */
    void onObtainConnection();

    /**
     * Counter: nop.dao.query.executions
     */
    Object beginQuery(SQL sql, LongRangeBean range);

    void endQuery(Object meter, long readCount, boolean success);

    /**
     * Counter: nop.dao.query.execute-updates
     */
    Object beginExecuteUpdate(SQL sql);

    void endExecuteUpdate(Object meter, long updateCount);

    /**
     * Counter: nop.dao.query.batch-updates
     */
    Object beginBatchUpdate(String sql);

    void endBatchUpdate(Object meter, long count);
}
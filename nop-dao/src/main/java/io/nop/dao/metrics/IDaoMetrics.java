/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.metrics;

import io.nop.api.core.beans.LongRangeBean;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.exceptions.JdbcException;
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

    void endQuery(SQL sql, Object meter, long readCount, Exception error);

    /**
     * Counter: nop.dao.query.execute-updates
     */
    Object beginExecuteUpdate(SQL sql);

    void endExecuteUpdate(SQL sql, Object meter, long updateCount, Exception error);

    /**
     * Counter: nop.dao.query.batch-updates
     */
    Object beginBatchUpdate(String sql);

    void endBatchUpdate(String sql, Object meter, long batchCount, Exception error);
}
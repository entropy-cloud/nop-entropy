/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.dao.jdbc;

import io.nop.api.core.time.IEstimatedClock;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.api.ISqlExecutor;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.dialect.IDialectProvider;
import io.nop.dao.metrics.IDaoMetrics;
import io.nop.dao.txn.ITransactionTemplate;

import jakarta.annotation.Nonnull;
import java.sql.Connection;
import java.sql.Timestamp;
import java.util.function.Function;

public interface IJdbcTemplate extends ISqlExecutor, IDialectProvider {

    IDialect getDialectForQuerySpace(String querySpace);

    IDaoMetrics getDaoMetrics();

    ITransactionTemplate txn();

    Connection currentConnection(String querySpace);

    <T> T runWithConnection(SQL sql, Function<Connection, T> callback);

    Object callFunc(@Nonnull SQL sql);

    boolean existsTable(String querySpace, String tableName);

    /**
     * 调用数据库的current_timestamp函数来返回数据库当前时间
     *
     * @return 当前时间戳
     */
    Timestamp getDbCurrentTimestamp(String querySpace);

    /**
     * 从数据库获取时间戳本身需要消耗一定的时间，因此返回的是一个时间范围。time = dbTime + now - [fetchBeginTime, fetchEndTime]
     */
    IEstimatedClock getDbEstimatedClock(String querySpace);

    boolean isQuerySpaceDefined(String querySpace);
}
/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.db.migration.core;

import io.nop.dao.jdbc.IJdbcTemplate;

/**
 * mysql 咨询锁守卫（plan 2518 裁定⑧方言矩阵）：{@code GET_LOCK(锁名, 超时)}（连接级）/
 * {@code RELEASE_LOCK}（同连接 finally）。超时/失败类型化异常，启动重试面承接，非静默继续。
 * SQL 文本级离线可测；真库行为用例 docker-gated（勘误 9 三联口径）。
 */
public class MySqlAdvisoryLockGuard extends AbstractAdvisoryLockGuard {

    /** 互斥锁获取 SQL（连接级；超时秒为参数）。 */
    public static final String ACQUIRE_SQL = "SELECT GET_LOCK(?, ?)";
    /** 互斥锁释放 SQL（同连接）。 */
    public static final String RELEASE_SQL = "SELECT RELEASE_LOCK(?)";

    public MySqlAdvisoryLockGuard(IJdbcTemplate jdbcTemplate, String querySpace,
                                  long lockTimeoutMillis) {
        super(jdbcTemplate, querySpace, lockTimeoutMillis);
    }

    @Override
    protected String acquireSql() {
        return ACQUIRE_SQL;
    }

    @Override
    protected String releaseSql() {
        return RELEASE_SQL;
    }

    @Override
    protected boolean isAcquireSuccess(Object result) {
        // GET_LOCK: 1 = success, 0 = timeout, NULL = error
        return result != null && "1".equals(String.valueOf(result));
    }

    @Override
    protected int acquireTimeoutParamIndex() {
        return 2;
    }
}

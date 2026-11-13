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
 * postgresql 咨询锁守卫（plan 2518 裁定⑧方言矩阵）：{@code pg_advisory_lock(key)}（session 级，
 * 同连接）/{@code pg_advisory_unlock(key)}（finally）。key 取锁名字符串（pg 对 text key 有
 * 原生重载）。超时经由 statement timeout 承接（pg_advisory_lock 无内建等待上界参数——
 * 超时面由连接 soTimeout/语句超时兜底，获取失败类型化异常，非静默继续）。
 * SQL 文本级离线可测；真库行为用例 docker-gated（勘误 9 三联口径）。
 */
public class PostgresAdvisoryLockGuard extends AbstractAdvisoryLockGuard {

    /** 互斥锁获取 SQL（session 级 text key）。 */
    public static final String ACQUIRE_SQL = "SELECT pg_advisory_lock(?)";
    /** 互斥锁释放 SQL（同连接）。 */
    public static final String RELEASE_SQL = "SELECT pg_advisory_unlock(?)";

    public PostgresAdvisoryLockGuard(IJdbcTemplate jdbcTemplate, String querySpace,
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
        // pg_advisory_lock 成功返回一行（key 本身）；无行/异常 = 失败
        return result != null;
    }
}

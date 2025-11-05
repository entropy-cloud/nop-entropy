/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.utils;

import io.nop.api.core.beans.LongRangeBean;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.time.IEstimatedClock;
import io.nop.dao.jdbc.IJdbcTemplate;

/**
 * 获取数据库时间，并通过本地时钟进行差量缓存。超过一定的超时时间后从数据库再次获取
 */
public class DbEstimatedClock implements IEstimatedClock {
    private final String querySpace;
    private final IJdbcTemplate jdbc;
    private final IConfigReference<Integer> cacheTimeout;
    private volatile TimeData timeData; // NOSONAR

    private volatile boolean fetching;

    static class TimeData {
        final long fetchBeginNanos;
        final long fetchEndNanos;
        final long dbTime;

        volatile long elapsedTime;

        public TimeData(long fetchBeginNanos, long fetchEndNanos, long dbTime) {
            this.fetchBeginNanos = fetchBeginNanos;
            this.fetchEndNanos = fetchEndNanos;
            this.dbTime = dbTime;
        }

        long getMax() {
            return dbTime + Math.max(0, CoreMetrics.nanoToMillis(CoreMetrics.nanoTime() - fetchBeginNanos));
        }

        long getMin() {
            return dbTime + Math.max(0, CoreMetrics.nanoToMillis(CoreMetrics.nanoTime() - fetchEndNanos));
        }
    }

    public DbEstimatedClock(String querySpace, IJdbcTemplate jdbc, IConfigReference<Integer> cacheTimeout) {
        this.querySpace = querySpace;
        this.jdbc = jdbc;
        this.cacheTimeout = cacheTimeout;
    }

    @Override
    public long getMinCurrentTimeMillis() {
        return fetch().getMin();
    }

    @Override
    public long getMaxCurrentTimeMillis() {
        return fetch().getMax();
    }

    @Override
    public LongRangeBean getCurrentTimeRange() {
        TimeData data = fetch();
        return LongRangeBean.of(data.getMin(), data.getMax() - data.getMin() + 1);
    }

    boolean isExpired(TimeData data) {
        if (data == null)
            return true;
        // 如果获取数据库耗费的时间过长，则认为它已经超时，重新获取
        if (CoreMetrics.nanoToMillis(data.fetchEndNanos - data.fetchBeginNanos) > 200)
            return true;

        // 如果出现时钟回拨，或者超过允许的最大缓存时间，则重新获取
        long diff = CoreMetrics.nanoToMillis(CoreMetrics.nanoTime() - data.fetchBeginNanos);
        long elapsed = data.elapsedTime;
        if (diff < elapsed || diff > cacheTimeout.get())
            return true;
        // diff表示从上次获取以来流逝的时间，它应该是单向增大的
        data.elapsedTime = diff;
        return false;
    }

    private TimeData fetch() {
        TimeData data = timeData;
        if (isExpired(data)) {
            // 如果有多线程并发获取，则只有一个线程会真正执行数据库请求，其他线程会继续使用上次的缓存
            if (fetching && data != null)
                return data;

            synchronized (this) {
                if (timeData == data) {
                    fetching = true;
                    try {
                        data = _fetchFromDb();
                    } finally {
                        fetching = false;
                    }
                } else {
                    return timeData;
                }
            }
        }
        return data;
    }

    private TimeData _fetchFromDb() {
        long beginTime = CoreMetrics.nanoTime();
        long dbTime = jdbc.getDbCurrentTimestamp(querySpace).getTime();
        long endTime = CoreMetrics.nanoTime();

        return this.timeData = new TimeData(beginTime, endTime, dbTime);
    }
}
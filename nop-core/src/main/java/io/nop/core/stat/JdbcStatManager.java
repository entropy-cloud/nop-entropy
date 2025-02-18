package io.nop.core.stat;

import io.nop.commons.cache.CacheConfig;
import io.nop.commons.cache.ICache;
import io.nop.commons.cache.LocalCache;

import java.util.concurrent.atomic.AtomicLong;

public class JdbcStatManager {
    static AtomicLong s_seq = new AtomicLong();
    static final JdbcStatManager _instance = new JdbcStatManager();

    public static JdbcStatManager global() {
        return _instance;
    }

    public static long createStatId() {
        return s_seq.incrementAndGet();
    }

    private ICache<String, JdbcSqlStat> sqlStats;

    private synchronized ICache<String, JdbcSqlStat> getCache() {
        if (sqlStats == null)
            sqlStats = LocalCache.newCache("jdbc-stat-cache", CacheConfig.newConfig(1000));
        return sqlStats;
    }

    public JdbcSqlStat getSqlStat(String sql) {
        return getCache().computeIfAbsent(sql, JdbcSqlStat::new);
    }
}
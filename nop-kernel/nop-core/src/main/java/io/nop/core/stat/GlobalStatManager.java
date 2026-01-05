package io.nop.core.stat;

import io.nop.commons.cache.CacheConfig;
import io.nop.commons.cache.ICache;
import io.nop.commons.cache.LocalCache;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static io.nop.core.CoreConfigs.CFG_CORE_STAT_RPC_CLIENT_CACHE_MAX_SIZE;
import static io.nop.core.CoreConfigs.CFG_CORE_STAT_RPC_SERVER_CACHE_MAX_SIZE;
import static io.nop.core.CoreConfigs.CFG_CORE_STAT_SQL_CACHE_MAX_SIZE;

public class GlobalStatManager implements IJdbcStatManager, IRpcClientStatManager, IRpcServerStatManager {
    static final AtomicLong s_seq = new AtomicLong();
    static final GlobalStatManager _INSTANCE = new GlobalStatManager();

    public static GlobalStatManager instance() {
        return _INSTANCE;
    }

    public static long createStatId() {
        return s_seq.incrementAndGet();
    }

    private ICache<String, JdbcSqlStat> sqlStats;
    private ICache<String, RpcClientStat> rpcClientStats;

    private ICache<String, RpcServerStat> rpcServerStats;

    private synchronized ICache<String, JdbcSqlStat> getSqlStats() {
        if (sqlStats == null)
            sqlStats = LocalCache.newCache("jdbc-sql-stat-cache", CacheConfig.newConfig(CFG_CORE_STAT_SQL_CACHE_MAX_SIZE.get()));
        return sqlStats;
    }

    private synchronized ICache<String, RpcClientStat> getRpcClientStats() {
        if (rpcClientStats == null)
            rpcClientStats = LocalCache.newCache("rpc-client-stat-cache", CacheConfig.newConfig(CFG_CORE_STAT_RPC_CLIENT_CACHE_MAX_SIZE.get()));
        return rpcClientStats;
    }

    private synchronized ICache<String, RpcServerStat> getRpcServerStats() {
        if (rpcServerStats == null)
            rpcServerStats = LocalCache.newCache("rpc-server-stat-cache", CacheConfig.newConfig(CFG_CORE_STAT_RPC_SERVER_CACHE_MAX_SIZE.get()));
        return rpcServerStats;
    }

    public void clear() {
        getSqlStats().clear();
        getRpcClientStats().clear();
        getRpcServerStats().clear();
    }

    @Override
    public JdbcSqlStat getJdbcSqlStat(String sql) {
        return getSqlStats().computeIfAbsent(sql, JdbcSqlStat::new);
    }

    @Override
    public List<JdbcSqlStatValue> getAllJdbcSqlStat(boolean orderByAvgTime) {
        List<JdbcSqlStatValue> ret = new ArrayList<>();
        getSqlStats().forEachEntry((k, v) -> {
            ret.add(v.getValue(false));
        });
        if (orderByAvgTime) {
            ret.sort((a, b) -> -Long.compare(b.getExecuteAvgTime(), a.getExecuteAvgTime()));
        } else {
            ret.sort(Comparator.comparing(JdbcSqlStatValue::getSql));
        }
        return ret;
    }

    @Override
    public RpcClientStat getRpcClientStat(String serviceName, String serviceAction) {
        return getRpcClientStats().computeIfAbsent(RpcClientStat.buildFullServiceName(serviceName, serviceAction),
                k -> new RpcClientStat(serviceName, serviceAction));
    }

    @Override
    public List<RpcClientStat> getAllRpcClientStats(boolean orderByAvgTime) {
        List<RpcClientStat> ret = new ArrayList<>();
        getRpcClientStats().forEachEntry((k, v) -> {
            ret.add(v);
        });
        if (orderByAvgTime) {
            ret.sort((a, b) -> -Long.compare(b.getExecuteAvgTime(), a.getExecuteAvgTime()));
        } else {
            ret.sort(Comparator.comparing(RpcClientStat::getFullServiceName));
        }
        return ret;
    }

    @Override
    public RpcServerStat getRpcServerStat(String operationName) {
        return getRpcServerStats().computeIfAbsent(operationName, RpcServerStat::new);
    }

    @Override
    public List<RpcServerStat> getAllRpcServerStats(boolean orderByAvgTime) {
        List<RpcServerStat> ret = new ArrayList<>();
        getRpcServerStats().forEachEntry((k, v) -> {
            ret.add(v);
        });
        if (orderByAvgTime) {
            ret.sort((a, b) -> -Long.compare(b.getExecuteAvgTime(), a.getExecuteAvgTime()));
        } else {
            ret.sort(Comparator.comparing(RpcServerStat::getOperationName));
        }
        return ret;
    }
}
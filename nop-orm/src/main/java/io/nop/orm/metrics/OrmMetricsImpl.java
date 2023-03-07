/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.metrics;

public class OrmMetricsImpl implements IOrmMetrics {

    public OrmMetricsImpl() {
        /*
         * // Session statistics counter(registry, "hibernate.sessions.open", "Sessions opened",
         * Statistics::getSessionOpenCount ); counter(registry, "hibernate.sessions.closed", "Sessions closed",
         * Statistics::getSessionCloseCount );
         *
         * // Transaction statistics counter(registry, "hibernate.transactions",
         * "The number of transactions we know to have been successful", Statistics::getSuccessfulTransactionCount,
         * "result", "success" ); counter(registry, "hibernate.transactions",
         * "The number of transactions we know to have failed", s -> s.getTransactionCount() -
         * s.getSuccessfulTransactionCount(), "result", "failure" ); counter(registry, "hibernate.optimistic.failures",
         * "The number of StaleObjectStateExceptions that have occurred", Statistics::getOptimisticFailureCount );
         *
         * counter(registry, "hibernate.flushes",
         * "The global number of flushes executed by sessions (either implicit or explicit)", Statistics::getFlushCount
         * ); counter(registry, "hibernate.connections.obtained",
         * "Get the global number of connections asked by the sessions " +
         * "(the actual number of connections used may be much smaller depending " +
         * "whether you use a connection pool or not)", Statistics::getConnectCount );
         *
         * // Statements counter(registry, "hibernate.statements",
         * "The number of prepared statements that were acquired", Statistics::getPrepareStatementCount, "status",
         * "prepared" ); counter(registry, "hibernate.statements",
         * "The number of prepared statements that were released", Statistics::getCloseStatementCount, "status",
         * "closed" );
         *
         * // Second Level Caching // AWKWARD: getSecondLevelCacheRegionNames is the only way to retrieve a list of
         * names // The returned names are all qualified. // getDomainDataRegionStatistics wants unqualified names, //
         * there are no "public" methods to unqualify the names Arrays.stream(
         * statistics.getSecondLevelCacheRegionNames() ) .map(s -> { if ( cacheFactoryPrefix != null ) { return
         * s.replaceAll( cacheFactoryPrefix + ".", "" ); } return s; }) .filter( this::hasDomainDataRegionStatistics )
         * .forEach( regionName -> { counter(registry, "hibernate.second.level.cache.requests",
         * "The number of cacheable entities/collections successfully retrieved from the cache", stats ->
         * stats.getDomainDataRegionStatistics( regionName ).getHitCount(), "region", regionName, "result", "hit" );
         * counter(registry, "hibernate.second.level.cache.requests",
         * "The number of cacheable entities/collections not found in the cache and loaded from the database", stats ->
         * stats.getDomainDataRegionStatistics( regionName ).getMissCount(), "region", regionName, "result", "miss" );
         * counter( registry, "hibernate.second.level.cache.puts",
         * "The number of cacheable entities/collections put in the cache", stats ->
         * stats.getDomainDataRegionStatistics( regionName ).getPutCount(), "region", regionName ); } );
         *
         * // Entity information counter(registry, "hibernate.entities.deletes", "The number of entity deletes",
         * Statistics::getEntityDeleteCount ); counter(registry, "hibernate.entities.fetches",
         * "The number of entity fetches", Statistics::getEntityFetchCount ); counter(registry,
         * "hibernate.entities.inserts", "The number of entity inserts", Statistics::getEntityInsertCount );
         * counter(registry, "hibernate.entities.loads", "The number of entity loads", Statistics::getEntityLoadCount );
         * counter(registry, "hibernate.entities.updates", "The number of entity updates",
         * Statistics::getEntityUpdateCount );
         *
         * // Collections counter(registry, "hibernate.collections.deletes", "The number of collection deletes",
         * Statistics::getCollectionRemoveCount ); counter(registry, "hibernate.collections.fetches",
         * "The number of collection fetches", Statistics::getCollectionFetchCount ); counter(registry,
         * "hibernate.collections.loads", "The number of collection loads", Statistics::getCollectionLoadCount );
         * counter(registry, "hibernate.collections.recreates", "The number of collections recreated",
         * Statistics::getCollectionRecreateCount ); counter(registry, "hibernate.collections.updates",
         * "The number of collection updates", Statistics::getCollectionUpdateCount );
         *
         * // Natural Id cache counter(registry, "hibernate.cache.natural.id.requests",
         * "The number of cached naturalId lookups successfully retrieved from cache",
         * Statistics::getNaturalIdCacheHitCount, "result", "hit" ); counter(registry,
         * "hibernate.cache.natural.id.requests", "The number of cached naturalId lookups not found in cache",
         * Statistics::getNaturalIdCacheMissCount, "result", "miss" ); counter(registry,
         * "hibernate.cache.natural.id.puts", "The number of cacheable naturalId lookups put in cache",
         * Statistics::getNaturalIdCachePutCount );
         *
         * counter(registry, "hibernate.query.natural.id.executions",
         * "The number of naturalId queries executed against the database", Statistics::getNaturalIdQueryExecutionCount
         * );
         *
         * TimeGauge.builder( "hibernate.query.natural.id.executions.max", statistics, TimeUnit.MILLISECONDS,
         * Statistics::getNaturalIdQueryExecutionMaxTime ) .description(
         * "The maximum query time for naturalId queries executed against the database" ) .tags( tags ) .register(
         * registry );
         *
         * // Query statistics counter(registry, "hibernate.query.executions", "The number of executed queries",
         * Statistics::getQueryExecutionCount );
         *
         * TimeGauge.builder( "hibernate.query.executions.max", statistics, TimeUnit.MILLISECONDS,
         * Statistics::getQueryExecutionMaxTime ) .description( "The time of the slowest query" ) .tags( tags )
         * .register( registry );
         *
         * // Update timestamp cache counter(registry, "hibernate.cache.update.timestamps.requests",
         * "The number of timestamps successfully retrieved from cache", Statistics::getUpdateTimestampsCacheHitCount,
         * "result", "hit" ); counter(registry, "hibernate.cache.update.timestamps.requests",
         * "The number of tables for which no update timestamps was not found in cache",
         * Statistics::getUpdateTimestampsCacheMissCount, "result", "miss" ); counter(registry,
         * "hibernate.cache.update.timestamps.puts", "The number of timestamps put in cache",
         * Statistics::getUpdateTimestampsCachePutCount );
         *
         * // Query Caching counter(registry, "hibernate.cache.query.requests",
         * "The number of cached queries successfully retrieved from cache", Statistics::getQueryCacheHitCount,
         * "result", "hit" ); counter(registry, "hibernate.cache.query.requests",
         * "The number of cached queries not found in cache", Statistics::getQueryCacheMissCount, "result", "miss" );
         * counter(registry, "hibernate.cache.query.puts", "The number of cacheable queries put in cache",
         * Statistics::getQueryCachePutCount ); counter(registry, "hibernate.cache.query.plan",
         * "The global number of query plans successfully retrieved from cache", Statistics::getQueryPlanCacheHitCount,
         * "result", "hit" ); counter(registry, "hibernate.cache.query.plan",
         * "The global number of query plans lookups not found in cache", Statistics::getQueryPlanCacheMissCount,
         * "result", "miss" );
         *
         */
    }

    @Override
    public void onSessionOpen() {

    }

    @Override
    public void onSessionClosed() {

    }

    @Override
    public void onFlush() {

    }

    @Override
    public void onLogicalLoadEntity(String entityName) {

    }

    @Override
    public void onLogicalDeleteEntity(String entityName) {

    }

    @Override
    public void onLogicalUpdateEntity(String entityName) {

    }

    @Override
    public void onLogicalSaveEntity(String entityName) {

    }
}

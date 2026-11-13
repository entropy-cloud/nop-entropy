package io.nop.code.service.impl;

import io.nop.api.core.time.CoreMetrics;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.code.core.graph.CallGraph;
import io.nop.code.core.graph.SymbolTable;
import io.nop.code.core.model.CodeSymbol;
import io.nop.code.dao.entity.NopCodeCall;
import io.nop.code.dao.entity.NopCodeDependency;
import io.nop.code.dao.entity.NopCodeSymbol;
import io.nop.code.flow.FlowDetector;
import io.nop.code.flow.IFlowDetector;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;

/**
 * Read-through cache of DB-derived views ({@link AnalysisCache}: SymbolTable / CallGraph /
 * dependencies). The database is the source of truth; entries are projections rebuilt from
 * nop_code_* tables and invalidated exclusively by index write paths (indexDirectory /
 * indexFile / triggerIncrementalIndex on actual changes / batchDeleteFileRecords /
 * deleteIndex). LRU (MAX_CACHE_ENTRIES) and TTL (CACHE_TTL_MS) are memory guards only, not
 * freshness mechanisms. Materialized metric rows (nop_code_graph_metric) are NOT cached here —
 * they are read through GraphMetricStore directly from the DB.
 */
class CodeCacheManager {

    private static final Logger LOG = LoggerFactory.getLogger(CodeCacheManager.class);

    static final int MAX_CACHE_ENTRIES = 20;
    static final long CACHE_TTL_MS = 3600_000L;
    private static final int BATCH_SIZE = 5000;
    private static final int MAX_CACHE_SYMBOLS = 100000;
    private static final int MAX_CACHE_EDGES = 500000;
    // W-14 (plan 2565, adjudication I / F2): dependency cache upper bound — scale between
    // symbols (1e5) and edges (5e5); breaks the "20 entries x per-entry bound" LRU sizing
    // assumption otherwise
    static final int MAX_CACHE_DEPENDENCIES = 200000;
    // F2 seam: instance field + package setter, default = compile-time constant (zero diff)
    private volatile int maxCacheDependencies = MAX_CACHE_DEPENDENCIES;

    void setMaxCacheDependenciesForTest(int max) {
        this.maxCacheDependencies = max;
    }

    static class AnalysisCache {
        SymbolTable symbolTable;
        CallGraph callGraph;
        List<NopCodeDependency> dependencies;
        // W-14: observable truncation flag
        boolean dependenciesTruncated;
    }

    static class CacheEntry {
        final AnalysisCache cache = new AnalysisCache();
        long lastAccessTime;

        CacheEntry() {
            this.lastAccessTime = CoreMetrics.currentTimeMillis();
        }

        void touch() {
            this.lastAccessTime = CoreMetrics.currentTimeMillis();
        }

        boolean isExpired() {
            return CoreMetrics.currentTimeMillis() - lastAccessTime > CACHE_TTL_MS;
        }
    }

    private final Map<String, CacheEntry> analysisCacheMap = new LinkedHashMap<>(16, 0.75f, true);

    private final ReentrantLock lock = new ReentrantLock();

    CacheEntry getValidEntry(String indexId) {
        CacheEntry entry = analysisCacheMap.get(indexId);
        if (entry == null)
            return null;
        if (entry.isExpired()) {
            analysisCacheMap.remove(indexId);
            return null;
        }
        return entry;
    }

    CacheEntry getOrCreateEntry(String indexId) {
        CacheEntry entry = getValidEntry(indexId);
        if (entry != null)
            return entry;
        entry = new CacheEntry();
        analysisCacheMap.put(indexId, entry);
        evictIfNeeded();
        return entry;
    }

    private void evictIfNeeded() {
        Iterator<CacheEntry> it = analysisCacheMap.values().iterator();
        while (analysisCacheMap.size() > MAX_CACHE_ENTRIES && it.hasNext()) {
            CacheEntry candidate = it.next();
            if (candidate.isExpired()) {
                it.remove();
            }
        }
        while (analysisCacheMap.size() > MAX_CACHE_ENTRIES) {
            Iterator<Map.Entry<String, CacheEntry>> mapIt = analysisCacheMap.entrySet().iterator();
            if (mapIt.hasNext()) {
                mapIt.next();
                mapIt.remove();
            }
        }
    }

    SymbolTable getOrRebuildSymbolTable(String indexId, IDaoProvider daoProvider,
                                         Function<NopCodeSymbol, CodeSymbol> converter) {
        lock.lock();
        try {
            CacheEntry entry = getOrCreateEntry(indexId);
            if (entry.cache.symbolTable != null) {
                entry.touch();
                return entry.cache.symbolTable;
            }
            entry.cache.symbolTable = rebuildSymbolTable(indexId, daoProvider, converter);
            return entry.cache.symbolTable;
        } finally {
            lock.unlock();
        }
    }

    CallGraph getOrRebuildCallGraph(String indexId, IDaoProvider daoProvider,
                                     BiConsumer<CallGraph, NopCodeCall> edgeConsumer) {
        lock.lock();
        try {
            CacheEntry entry = getOrCreateEntry(indexId);
            if (entry.cache.callGraph != null) {
                entry.touch();
                return entry.cache.callGraph;
            }
            entry.cache.callGraph = rebuildCallGraph(indexId, daoProvider, edgeConsumer);
            return entry.cache.callGraph;
        } finally {
            lock.unlock();
        }
    }

    void invalidateAnalysisCache(String indexId, IFlowDetector flowDetector) {
        lock.lock();
        try {
            analysisCacheMap.remove(indexId);
        } finally {
            lock.unlock();
        }
        if (flowDetector instanceof FlowDetector) {
            ((FlowDetector) flowDetector).invalidateCache(indexId);
        }
    }

    void addToSymbolTableCache(String indexId, SymbolTable newSymbols) {
        lock.lock();
        try {
            CacheEntry entry = getValidEntry(indexId);
            if (entry != null && entry.cache.symbolTable != null) {
                for (CodeSymbol sym : newSymbols.getAll()) {
                    // W-11: atomic dedup via putIfAbsent (get-then-add check-then-act closed, G11 alias)
                    entry.cache.symbolTable.putIfAbsent(sym);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    List<NopCodeDependency> getOrRebuildDependencies(String indexId, IDaoProvider daoProvider) {
        lock.lock();
        try {
            CacheEntry entry = getOrCreateEntry(indexId);
            if (entry.cache.dependencies != null) {
                entry.touch();
                return entry.cache.dependencies;
            }
            entry.cache.dependencies = rebuildDependencies(indexId, daoProvider);
            return entry.cache.dependencies;
        } finally {
            lock.unlock();
        }
    }

    int cacheSize() {
        return analysisCacheMap.size();
    }

    private SymbolTable rebuildSymbolTable(String indexId, IDaoProvider daoProvider,
                                            Function<NopCodeSymbol, CodeSymbol> converter) {
        IEntityDao<NopCodeSymbol> symbolDao = daoProvider.daoFor(NopCodeSymbol.class);
        SymbolTable table = new SymbolTable();
        long offset = 0;
        int totalLoaded = 0;
        while (true) {
            QueryBean query = new QueryBean();
            query.addFilter(FilterBeans.eq("indexId", indexId));
            query.setOffset(offset);
            query.setLimit(BATCH_SIZE);
            List<NopCodeSymbol> batch = symbolDao.findPageByQuery(query);
            if (batch.isEmpty())
                break;
            for (NopCodeSymbol entity : batch) {
                table.add(converter.apply(entity));
                totalLoaded++;
            }
            if (totalLoaded >= MAX_CACHE_SYMBOLS) {
                LOG.warn("Symbol cache for index {} exceeded MAX_CACHE_SYMBOLS({}), returning partial data ({} symbols loaded)",
                        indexId, MAX_CACHE_SYMBOLS, totalLoaded);
                table.setTruncated(true);
                return table;
            }
            if (batch.size() < BATCH_SIZE)
                break;
            offset += BATCH_SIZE;
        }
        return table;
    }

    private CallGraph rebuildCallGraph(String indexId, IDaoProvider daoProvider,
                                        BiConsumer<CallGraph, NopCodeCall> edgeConsumer) {
        IEntityDao<NopCodeCall> callDao = daoProvider.daoFor(NopCodeCall.class);
        CallGraph callGraph = new CallGraph();
        long offset = 0;
        int totalLoaded = 0;
        while (true) {
            QueryBean query = new QueryBean();
            query.addFilter(FilterBeans.eq("indexId", indexId));
            query.setLimit(BATCH_SIZE);
            query.setOffset(offset);
            List<NopCodeCall> batch = callDao.findPageByQuery(query);
            if (batch.isEmpty())
                break;
            for (NopCodeCall entity : batch) {
                edgeConsumer.accept(callGraph, entity);
                totalLoaded++;
            }
            if (totalLoaded >= MAX_CACHE_EDGES) {
                LOG.warn("Call graph cache for index {} exceeded MAX_CACHE_EDGES({}), returning partial data ({} edges loaded)",
                        indexId, MAX_CACHE_EDGES, totalLoaded);
                callGraph.setTruncated(true);
                return callGraph;
            }
            if (batch.size() < BATCH_SIZE)
                break;
            offset += BATCH_SIZE;
        }
        return callGraph;
    }

    private List<NopCodeDependency> rebuildDependencies(String indexId, IDaoProvider daoProvider) {
        IEntityDao<NopCodeDependency> dao = daoProvider.daoFor(NopCodeDependency.class);
        List<NopCodeDependency> result = new ArrayList<>();
        boolean truncated = false;
        long offset = 0;
        while (true) {
            QueryBean q = new QueryBean();
            q.addFilter(FilterBeans.eq("indexId", indexId));
            q.setOffset(offset);
            q.setLimit(BATCH_SIZE);
            List<NopCodeDependency> batch = dao.findPageByQuery(q);
            if (batch.isEmpty()) break;
            result.addAll(batch);
            if (result.size() >= maxCacheDependencies) {
                // W-14: truncate + structured WARN + observable flag (form aligned with the
                // symbol-side MAX_CACHE_SYMBOLS guard); slice to the exact cap
                result = new ArrayList<>(result.subList(0, maxCacheDependencies));
                truncated = true;
                LOG.warn("nop.code.dependencies-cache-capped:indexId={},cap={},loaded={}",
                        indexId, maxCacheDependencies, result.size());
                markDependenciesTruncated(indexId, true);
                break;
            }
            if (batch.size() < BATCH_SIZE) break;
            offset += BATCH_SIZE;
        }
        if (!truncated) {
            markDependenciesTruncated(indexId, false);
        }
        return result;
    }

    private void markDependenciesTruncated(String indexId, boolean truncated) {
        CacheEntry entry = analysisCacheMap.get(indexId);
        if (entry != null) {
            entry.cache.dependenciesTruncated = truncated;
        }
    }

    /** W-14 observable flag (package getter, F2 seam) */
    boolean isDependenciesTruncated(String indexId) {
        CacheEntry entry = getValidEntry(indexId);
        return entry != null && entry.cache.dependenciesTruncated;
    }
}

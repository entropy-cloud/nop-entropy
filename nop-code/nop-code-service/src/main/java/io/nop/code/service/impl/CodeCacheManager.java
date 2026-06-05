package io.nop.code.service.impl;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import io.nop.code.dao.entity.NopCodeSymbol;
import io.nop.code.flow.FlowDetector;
import io.nop.code.flow.IFlowDetector;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;

class CodeCacheManager {

    private static final Logger LOG = LoggerFactory.getLogger(CodeCacheManager.class);

    static final int MAX_CACHE_ENTRIES = 20;
    static final long CACHE_TTL_MS = 3600_000L;
    private static final int BATCH_SIZE = 5000;
    private static final int MAX_CACHE_SYMBOLS = 100000;
    private static final int MAX_CACHE_EDGES = 500000;

    static class AnalysisCache {
        SymbolTable symbolTable;
        CallGraph callGraph;
    }

    static class CacheEntry {
        final AnalysisCache cache = new AnalysisCache();
        long lastAccessTime;

        CacheEntry() {
            this.lastAccessTime = System.currentTimeMillis();
        }

        void touch() {
            this.lastAccessTime = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - lastAccessTime > CACHE_TTL_MS;
        }
    }

    private final Map<String, CacheEntry> analysisCacheMap = new LinkedHashMap<>(16, 0.75f, true);

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

    synchronized SymbolTable getOrRebuildSymbolTable(String indexId, IDaoProvider daoProvider,
                                                      Function<NopCodeSymbol, CodeSymbol> converter) {
        CacheEntry entry = getOrCreateEntry(indexId);
        if (entry.cache.symbolTable != null) {
            entry.touch();
            return entry.cache.symbolTable;
        }
        entry.cache.symbolTable = rebuildSymbolTable(indexId, daoProvider, converter);
        return entry.cache.symbolTable;
    }

    synchronized CallGraph getOrRebuildCallGraph(String indexId, IDaoProvider daoProvider,
                                                  BiConsumer<CallGraph, NopCodeCall> edgeConsumer) {
        CacheEntry entry = getOrCreateEntry(indexId);
        if (entry.cache.callGraph != null) {
            entry.touch();
            return entry.cache.callGraph;
        }
        entry.cache.callGraph = rebuildCallGraph(indexId, daoProvider, edgeConsumer);
        return entry.cache.callGraph;
    }

    synchronized void invalidateAnalysisCache(String indexId, IFlowDetector flowDetector) {
        analysisCacheMap.remove(indexId);
        if (flowDetector instanceof FlowDetector) {
            ((FlowDetector) flowDetector).invalidateCache(indexId);
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
            List<NopCodeSymbol> batch = symbolDao.findAllByQuery(query);
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
            List<NopCodeCall> batch = callDao.findAllByQuery(query);
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
}

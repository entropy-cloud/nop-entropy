package io.nop.code.service.impl;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
    private static final int BATCH_SIZE = 5000;
    private static final int MAX_CACHE_SYMBOLS = 100000;
    private static final int MAX_CACHE_EDGES = 500000;

    static class AnalysisCache {
        SymbolTable symbolTable;
        CallGraph callGraph;
    }

    private final Map<String, AnalysisCache> analysisCacheMap = new ConcurrentHashMap<>();

    synchronized SymbolTable getOrRebuildSymbolTable(String indexId, IDaoProvider daoProvider,
                                                      Function<NopCodeSymbol, CodeSymbol> converter) {
        AnalysisCache cache = analysisCacheMap.get(indexId);
        if (cache != null && cache.symbolTable != null) {
            return cache.symbolTable;
        }
        if (cache == null) {
            cache = new AnalysisCache();
            analysisCacheMap.put(indexId, cache);
        }
        cache.symbolTable = rebuildSymbolTable(indexId, daoProvider, converter);
        return cache.symbolTable;
    }

    synchronized CallGraph getOrRebuildCallGraph(String indexId, IDaoProvider daoProvider,
                                                  BiConsumer<CallGraph, NopCodeCall> edgeConsumer) {
        AnalysisCache cache = analysisCacheMap.get(indexId);
        if (cache != null && cache.callGraph != null) {
            return cache.callGraph;
        }
        if (cache == null) {
            cache = new AnalysisCache();
            analysisCacheMap.put(indexId, cache);
        }
        cache.callGraph = rebuildCallGraph(indexId, daoProvider, edgeConsumer);
        return cache.callGraph;
    }

    synchronized void invalidateAnalysisCache(String indexId, IFlowDetector flowDetector) {
        analysisCacheMap.remove(indexId);
        if (flowDetector instanceof FlowDetector) {
            ((FlowDetector) flowDetector).invalidateCache(indexId);
        }
        while (analysisCacheMap.size() > MAX_CACHE_ENTRIES) {
            Iterator<String> it = analysisCacheMap.keySet().iterator();
            if (it.hasNext()) {
                it.next();
                it.remove();
            }
        }
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
                LOG.warn("Symbol cache for index {} exceeded MAX_CACHE_SYMBOLS({}), degrading to empty cache",
                        indexId, MAX_CACHE_SYMBOLS);
                return new SymbolTable();
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
            query.setOffset(offset);
            query.setLimit(BATCH_SIZE);
            List<NopCodeCall> batch = callDao.findAllByQuery(query);
            if (batch.isEmpty())
                break;
            for (NopCodeCall entity : batch) {
                edgeConsumer.accept(callGraph, entity);
                totalLoaded++;
            }
            if (totalLoaded >= MAX_CACHE_EDGES) {
                LOG.warn("Call graph cache for index {} exceeded MAX_CACHE_EDGES({}), degrading to empty cache",
                        indexId, MAX_CACHE_EDGES);
                return new CallGraph();
            }
            if (batch.size() < BATCH_SIZE)
                break;
            offset += BATCH_SIZE;
        }
        return callGraph;
    }
}

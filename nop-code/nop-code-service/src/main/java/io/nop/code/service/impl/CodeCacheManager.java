package io.nop.code.service.impl;

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

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;

class CodeCacheManager {

    static final int MAX_CACHE_ENTRIES = 20;

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
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("indexId", indexId));
        List<NopCodeSymbol> entities = symbolDao.findAllByQuery(query);
        SymbolTable table = new SymbolTable();
        for (NopCodeSymbol entity : entities) {
            table.add(converter.apply(entity));
        }
        return table;
    }

    private CallGraph rebuildCallGraph(String indexId, IDaoProvider daoProvider,
                                        BiConsumer<CallGraph, NopCodeCall> edgeConsumer) {
        IEntityDao<NopCodeCall> callDao = daoProvider.daoFor(NopCodeCall.class);
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("indexId", indexId));
        List<NopCodeCall> callEntities = callDao.findAllByQuery(query);
        CallGraph callGraph = new CallGraph();
        for (NopCodeCall entity : callEntities) {
            edgeConsumer.accept(callGraph, entity);
        }
        return callGraph;
    }
}

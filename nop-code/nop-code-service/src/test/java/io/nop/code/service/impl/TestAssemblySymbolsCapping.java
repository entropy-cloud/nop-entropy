package io.nop.code.service.impl;

import io.nop.code.dao.entity.NopCodeGraphMetric;
import io.nop.code.dao.entity.NopCodeSymbol;
import io.nop.code.service.graph.GraphMetricStore;
import io.nop.dao.api.IDaoEntity;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * W-17 (plan 2565, adjudication L / F2, R1-B3+R2-3 re-adjudication, TP-010 remaining half):
 * - loadSymbolsByIds carries the MAX_ASSEMBLY_SYMBOLS hard cap (independent single-source
 *   constant, cross-referenced against — not copied from — GraphMetricStore's finder row cap);
 * - the :263 entryPoints input is trimmed to the topN window (counting stub asserts the
 *   requested id-set size == topN); :148/:864 keep full input (frozen, out of the pin face).
 * Zero coupling with the plan 2530 single-flight gate (TestGraphMetricsSingleFlight_W_03):
 * no materialize override anywhere in this test.
 */
class TestAssemblySymbolsCapping {

    private CodeGraphService newService(IDaoProvider provider, GraphMetricStore store) throws Exception {
        CodeGraphService service = new CodeGraphService(provider, new CodeCacheManager(), null);
        if (store != null) {
            Field f = CodeGraphService.class.getDeclaredField("metricStore");
            f.setAccessible(true);
            f.set(service, store);
        }
        return service;
    }

    private Map<String, NopCodeSymbol> loadSymbols(CodeGraphService service, List<String> ids) throws Exception {
        Method m = CodeGraphService.class.getDeclaredMethod("loadSymbolsByIds", java.util.Collection.class);
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, NopCodeSymbol> result = (Map<String, NopCodeSymbol>) m.invoke(service, ids);
        return result;
    }

    private int lastRequestSize(CodeGraphService service) throws Exception {
        Field f = CodeGraphService.class.getDeclaredField("lastAssemblyRequestSize");
        f.setAccessible(true);
        return f.getInt(service);
    }

    private boolean lastTruncated(CodeGraphService service) throws Exception {
        Field f = CodeGraphService.class.getDeclaredField("lastAssemblyTruncated");
        f.setAccessible(true);
        return f.getBoolean(service);
    }

    /** dao stub: symbol lookup returns an entity per requested id (chunked IN query) */
    private IDaoProvider symbolProvider() {
        return new IDaoProvider() {
            @Override
            public java.util.Set<String> getEntityNames() {
                return java.util.Set.of();
            }

            @Override
            public String normalizeEntityName(String entityName) {
                return entityName;
            }

            @Override
            public boolean hasDao(String entityName) {
                return true;
            }

            @SuppressWarnings("unchecked")
            @Override
            public <T extends IDaoEntity> IEntityDao<T> dao(String entityName) {
                return (IEntityDao<T>) java.lang.reflect.Proxy.newProxyInstance(
                        IEntityDao.class.getClassLoader(), new Class<?>[]{IEntityDao.class},
                        (proxy, method, args) -> {
                            if (method.getName().equals("findAllByQuery")
                                    && args[0] instanceof io.nop.api.core.beans.query.QueryBean q) {
                                List<NopCodeSymbol> rows = new ArrayList<>();
                                List<String> ids = extractInValues(q);
                                if (ids == null) ids = java.util.Collections.emptyList();
                                for (String id : ids) {
                                    NopCodeSymbol sym = new NopCodeSymbol();
                                    sym.setId(id);
                                    sym.setQualifiedName("com.example." + id);
                                    rows.add(sym);
                                }
                                return rows;
                            }
                            if (method.getReturnType() == boolean.class) return false;
                            if (method.getReturnType() == int.class) return 0;
                            if (method.getReturnType() == long.class) return 0L;
                            return null;
                        });
            }

            @Override
            public <T extends IDaoEntity> IEntityDao<T> daoForTable(String tableName) {
                return dao(null);
            }
        };
    }

    @Test
    void testLoadSymbolsByIdsCappedWithFlag() throws Exception {
        CodeGraphService service = newService(symbolProvider(), null);
        service.setMaxAssemblySymbolsForTest(5);

        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            ids.add("id-" + i);
        }
        Map<String, NopCodeSymbol> result = loadSymbols(service, ids);
        assertTrue(result.size() <= 5, "assembly entities must be capped, got " + result.size());
        assertTrue(lastTruncated(service), "observable truncation flag must be set");
        assertEquals(12, lastRequestSize(service), "request size recorded before truncation");
    }

    @Test
    void testLoadSymbolsByIdsNotTruncatedBelowCap() throws Exception {
        CodeGraphService service = newService(symbolProvider(), null);
        List<String> ids = List.of("a", "b");
        Map<String, NopCodeSymbol> result = loadSymbols(service, ids);
        assertEquals(2, result.size());
        assertFalse(lastTruncated(service));
    }

    /**
     * R2-3 counting stub: assembleGraphAnalysisFromMetrics must request exactly the topN
     * window of entryPoint ids (pre-fix: the full entryPoint set was materialized).
     */
    @Test
    void testEntryPointInputTrimmedToTopNWindow() throws Exception {
        final int topN = 5;
        final List<String> entryPointIds = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            entryPointIds.add("ep-" + i);
        }

        GraphMetricStore store = new GraphMetricStore(null) {
            @Override
            public Map<String, Object> loadSummary(String indexId) {
                return Map.of("symbolCount", 500);
            }

            @Override
            public List<NopCodeGraphMetric> loadEntryPoints(String indexId) {
                List<NopCodeGraphMetric> rows = new ArrayList<>();
                for (int i = 0; i < entryPointIds.size(); i++) {
                    NopCodeGraphMetric row = new NopCodeGraphMetric();
                    row.setSymbolId(entryPointIds.get(i));
                    row.setEntryPointType("HOTSPOT");
                    row.setRankNo(i);
                    rows.add(row);
                }
                return rows;
            }

            @Override
            public Map<String, int[]> loadHubs(String indexId) {
                return Map.of();
            }
        };

        // symbol dao reports the id-set size it was asked to materialize
        final AtomicInteger requested = new AtomicInteger(-1);
        IDaoProvider provider = new IDaoProvider() {
            @Override
            public java.util.Set<String> getEntityNames() {
                return java.util.Set.of();
            }

            @Override
            public String normalizeEntityName(String entityName) {
                return entityName;
            }

            @Override
            public boolean hasDao(String entityName) {
                return true;
            }

            @SuppressWarnings("unchecked")
            @Override
            public <T extends IDaoEntity> IEntityDao<T> dao(String entityName) {
                return (IEntityDao<T>) java.lang.reflect.Proxy.newProxyInstance(
                        IEntityDao.class.getClassLoader(), new Class<?>[]{IEntityDao.class},
                        (proxy, method, args) -> {
                            if (method.getName().equals("findAllByQuery")
                                    && args[0] instanceof io.nop.api.core.beans.query.QueryBean q) {
                                List<String> inIds = extractInValues(q);
                                if (inIds != null) {
                                    requested.set(inIds.size());
                                }
                                return new ArrayList<>();
                            }
                            if (method.getReturnType() == boolean.class) return false;
                            if (method.getReturnType() == int.class) return 0;
                            if (method.getReturnType() == long.class) return 0L;
                            return null;
                        });
            }

            @Override
            public <T extends IDaoEntity> IEntityDao<T> daoForTable(String tableName) {
                return dao(null);
            }
        };

        CodeGraphService service = newService(provider, store);
        Method m = CodeGraphService.class.getDeclaredMethod("assembleGraphAnalysisFromMetrics",
                String.class, int.class);
        m.setAccessible(true);
        m.invoke(service, "w17-idx", topN);

        assertEquals(topN, requested.get(),
                ":263 entryPoints input must be trimmed to the topN window (pre-fix: full set of 30)");
        assertEquals(topN, lastRequestSize(service));
    }

    /** extract the values of an in(...) filter from the query bean (defensive traversal) */
    private static List<String> extractInValues(io.nop.api.core.beans.query.QueryBean q) {
        return extractIn(q.getFilter());
    }

    private static List<String> extractIn(io.nop.api.core.beans.ITreeBean node) {
        if (node == null) return null;
        if ("in".equals(node.getTagName())) {
            List<String> values = new ArrayList<>();
            Object v = node.getAttr("value");
            if (v == null) v = node.getValue();
            if (v instanceof java.util.Collection<?> col) {
                for (Object o : col) {
                    values.add(String.valueOf(o));
                }
                return values;
            }
            return values;
        }
        for (io.nop.api.core.beans.ITreeBean child : node.getChildren()) {
            List<String> found = extractIn(child);
            if (found != null) return found;
        }
        return null;
    }
}

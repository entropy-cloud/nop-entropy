package io.nop.code.service.impl;

import io.nop.api.core.beans.query.QueryBean;
import io.nop.code.dao.entity.NopCodeFile;
import io.nop.code.dao.entity.NopCodeSymbol;
import io.nop.dao.api.IDaoEntity;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * W-18 (plan 2565, adjudication M, TP-011) + W-19 (adjudication N, TP-012):
 * - W-18: getPublicSurface's symbol query was the file's ONLY unbounded query — it must carry
 *   setLimit(publicSurfaceCap) + warnIfCapped, with the F2 seam (instance field + package
 *   setter, default = MAX_QUERY_RESULTS, zero diff);
 * - W-19: getFileTree must go through the selectFieldsByQuery projection variant (never load
   full entities / the sourceCode CLOB); getFiles body stays frozen.
 */
class TestCodeQueryServiceCapAndFileTree {

    private IDaoProvider provider(IEntityDao<NopCodeFile> fileDao, IEntityDao<NopCodeSymbol> symDao) {
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
                if (NopCodeFile.class.getName().equals(entityName)) {
                    return (IEntityDao<T>) fileDao;
                }
                if (NopCodeSymbol.class.getName().equals(entityName)) {
                    return (IEntityDao<T>) symDao;
                }
                throw new IllegalStateException("unexpected dao: " + entityName);
            }

            @Override
            public <T extends IDaoEntity> IEntityDao<T> daoForTable(String tableName) {
                throw new IllegalStateException("unexpected table: " + tableName);
            }
        };
    }

    private IEntityDao<NopCodeSymbol> symbolDaoReturning(int count, AtomicReference<QueryBean> captured) {
        return (IEntityDao<NopCodeSymbol>) java.lang.reflect.Proxy.newProxyInstance(
                IEntityDao.class.getClassLoader(), new Class<?>[]{IEntityDao.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("findAllByQuery")) {
                        captured.set((QueryBean) args[0]);
                        List<NopCodeSymbol> rows = new ArrayList<>();
                        Integer limit = captured.get().getLimit();
                        int n = (limit != null && limit > 0) ? Math.min(count, limit) : count;
                        for (int i = 0; i < n; i++) {
                            NopCodeSymbol s = new NopCodeSymbol();
                            s.setId("sym-" + i);
                            s.setName("m" + i);
                            s.setQualifiedName("com.example.C.m" + i);
                            s.setKind("METHOD");
                            s.setAccessModifier("PUBLIC");
                            s.setFileId("file-1");
                            rows.add(s);
                        }
                        return rows;
                    }
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == int.class) return 0;
                    if (method.getReturnType() == long.class) return 0L;
                    return null;
                });
    }

    // ==================== W-18 ====================

    @Test
    void testPublicSurfaceSymbolQueryCarriesCap() {
        AtomicReference<QueryBean> captured = new AtomicReference<>();
        CodeQueryService service = new CodeQueryService(provider(fileDaoStub(), symbolDaoReturning(50, captured)), new CodeCacheManager(), null);
        service.setPublicSurfaceCapForTest(7);

        List<?> result = service.getPublicSurface("w18-idx", null);

        assertNotNull(captured.get(), "symbol query executed");
        Integer limit = captured.get().getLimit();
        assertEquals(7, limit != null ? limit.intValue() : 0,
                "symQuery must carry setLimit(publicSurfaceCap) (pre-fix: unbounded)");
        assertEquals(7, result.size(), "result capped at the seam value");
    }

    @Test
    void testPublicSurfaceDefaultCapEqualsMaxQueryResults() {
        AtomicReference<QueryBean> captured = new AtomicReference<>();
        CodeQueryService service = new CodeQueryService(provider(fileDaoStub(), symbolDaoReturning(3, captured)), new CodeCacheManager(), null);
        service.getPublicSurface("w18-idx", null);
        Integer limit = captured.get().getLimit();
        assertEquals(CodeIndexService.MAX_QUERY_RESULTS, limit != null ? limit.intValue() : 0,
                "default cap = MAX_QUERY_RESULTS (production zero diff)");
    }

    private IEntityDao<NopCodeFile> fileDaoStub() {
        return (IEntityDao<NopCodeFile>) java.lang.reflect.Proxy.newProxyInstance(
                IEntityDao.class.getClassLoader(), new Class<?>[]{IEntityDao.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("selectFieldsByQuery")) {
                        Map<String, Object> row = new java.util.LinkedHashMap<>();
                        row.put("id", "file-1");
                        row.put("filePath", "demo/A.java");
                        row.put("packageName", "demo");
                        List<Map<String, Object>> rows = new ArrayList<>();
                        rows.add(row);
                        return rows;
                    }
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == int.class) return 0;
                    if (method.getReturnType() == long.class) return 0L;
                    return null;
                });
    }

    // ==================== W-19 ====================

    @Test
    void testFileTreeUsesProjectionNotFullEntities() {
        AtomicInteger selectCalls = new AtomicInteger();
        AtomicInteger findAllCalls = new AtomicInteger();
        IEntityDao<NopCodeFile> fileDao = (IEntityDao<NopCodeFile>) java.lang.reflect.Proxy.newProxyInstance(
                IEntityDao.class.getClassLoader(), new Class<?>[]{IEntityDao.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("selectFieldsByQuery")) {
                        selectCalls.incrementAndGet();
                        List<Map<String, Object>> rows = new ArrayList<>();
                        Map<String, Object> r1 = new java.util.LinkedHashMap<>();
                        r1.put("id", "f1");
                        r1.put("filePath", "demo/util/A.java");
                        r1.put("packageName", "demo.util");
                        rows.add(r1);
                        Map<String, Object> r2 = new java.util.LinkedHashMap<>();
                        r2.put("id", "f2");
                        r2.put("filePath", "demo/svc/B.java");
                        r2.put("packageName", "demo.svc");
                        rows.add(r2);
                        return rows;
                    }
                    if (method.getName().equals("findAllByQuery")) {
                        findAllCalls.incrementAndGet();
                        return new ArrayList<>();
                    }
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == int.class) return 0;
                    if (method.getReturnType() == long.class) return 0L;
                    return null;
                });

        CodeQueryService service = new CodeQueryService(provider(fileDao, symbolDaoReturning(0, new AtomicReference<>())), new CodeCacheManager(), null);
        var tree = service.getFileTree("w19-idx");

        assertTrue(selectCalls.get() >= 1, "getFileTree must go through the projection (pre-fix: zero calls)");
        assertEquals(0, findAllCalls.get(),
                "fileTree must not load full entities via findAllByQuery (sourceCode CLOB)");
        assertEquals(1, tree.size(), "single root package node built from projected rows");
        assertEquals(2, tree.get(0).getChildren().size(), "two package children under demo");
    }
}

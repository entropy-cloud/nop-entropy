package io.nop.code.service.impl;

import io.nop.api.core.beans.query.QueryBean;
import io.nop.code.dao.entity.NopCodeDependency;
import io.nop.dao.api.IDaoEntity;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * W-14 (plan 2565, adjudication I / F2, TP-007): rebuildDependencies must enforce
 * MAX_CACHE_DEPENDENCIES (2e5 scale) with truncate + structured WARN + observable flag;
 * the injection seam (instance field + package setter) lets tests prove truncation with a
 * low value. Form aligned with the symbol-side MAX_CACHE_SYMBOLS guard.
 */
class TestCodeCacheManagerDepsCap {

    /** dao stub: yields fixed-size pages of synthetic NopCodeDependency rows forever */
    private IEntityDao<NopCodeDependency> pagingDao(int pageSize) {
        return (IEntityDao<NopCodeDependency>) java.lang.reflect.Proxy.newProxyInstance(
                IEntityDao.class.getClassLoader(), new Class<?>[]{IEntityDao.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("findPageByQuery")
                            || method.getName().equals("findAllByQuery")) {
                        List<NopCodeDependency> batch = new ArrayList<>();
                        for (int i = 0; i < pageSize; i++) {
                            batch.add(new NopCodeDependency());
                        }
                        return batch;
                    }
                    if (method.getName().equals("batchDeleteEntities")) {
                        return null;
                    }
                    if (method.getReturnType() == boolean.class) {
                        return false;
                    }
                    if (method.getReturnType() == int.class) {
                        return 0;
                    }
                    if (method.getReturnType() == long.class) {
                        return 0L;
                    }
                    return null;
                });
    }

    private IDaoProvider providerReturning(IEntityDao<?> dao) {
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
                return (IEntityDao<T>) dao;
            }

            @Override
            public <T extends IDaoEntity> IEntityDao<T> daoForTable(String tableName) {
                return dao(entityNameForTable(tableName));
            }

            private String entityNameForTable(String tableName) {
                return tableName;
            }
        };
    }

    private List<NopCodeDependency> rebuild(CodeCacheManager mgr, IDaoProvider provider) throws Exception {
        Method m = CodeCacheManager.class.getDeclaredMethod("rebuildDependencies", String.class, IDaoProvider.class);
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<NopCodeDependency> result = (List<NopCodeDependency>) m.invoke(mgr, "w14-idx", provider);
        return result;
    }

    @Test
    void testDependenciesTruncatedAtCapWithFlag() throws Exception {
        CodeCacheManager mgr = new CodeCacheManager();
        mgr.getOrCreateEntry("w14-idx");
        mgr.setMaxCacheDependenciesForTest(10);

        // endless pages of 4 rows -> unbounded pre-fix; capped at 10 post-fix
        List<NopCodeDependency> result = rebuild(mgr, providerReturning(pagingDao(5000)));

        assertEquals(10, result.size(), "dependencies must be truncated at the injected cap");
        assertTrue(mgr.isDependenciesTruncated("w14-idx"), "observable truncation flag must be set");
    }

    @Test
    void testDependenciesNotTruncatedBelowCap() throws Exception {
        CodeCacheManager mgr = new CodeCacheManager();
        mgr.getOrCreateEntry("w14-idx-small");
        // single short page (2 < page size 4) terminates the loop normally
        IEntityDao<NopCodeDependency> dao = (IEntityDao<NopCodeDependency>) java.lang.reflect.Proxy.newProxyInstance(
                IEntityDao.class.getClassLoader(), new Class<?>[]{IEntityDao.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("findPageByQuery")
                            || method.getName().equals("findAllByQuery")) {
                        List<NopCodeDependency> batch = new ArrayList<>();
                        for (int i = 0; i < 2; i++) {
                            batch.add(new NopCodeDependency());
                        }
                        return batch;
                    }
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == int.class) return 0;
                    if (method.getReturnType() == long.class) return 0L;
                    return null;
                });

        List<NopCodeDependency> result = rebuild(mgr, providerReturning(dao));
        assertEquals(2, result.size());
        assertFalse(mgr.isDependenciesTruncated("w14-idx-small"));
    }

    @Test
    void testDefaultCapConstant() {
        assertEquals(200000, CodeCacheManager.MAX_CACHE_DEPENDENCIES,
                "2e5 scale between symbols (1e5) and edges (5e5)");
    }
}

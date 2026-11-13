package io.nop.code.service.impl;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.code.dao.entity.NopCodeGraphMetric;
import io.nop.code.service.api.ICodeIndexService;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.code.core.graph.SymbolTable;
import io.nop.code.service.util.CodeSymbolConverter;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.*;

/**
 * N1.4: invalidation policy aligned with incremental semantics — actual content changes
 * invalidate both the analysis cache and materialized metric rows; no-op incrementals keep
 * both. AnalysisCache is a read-through cache of DB-derived views (DB = source of truth).
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        enableActionAuth = OptionalBoolean.FALSE)
public class TestCacheInvalidationAlignment extends JunitAutoTestCase {

    @Inject
    ICodeIndexService codeIndexService;

    @Inject
    IDaoProvider daoProvider;

    @TempDir
    Path tempDir;

    // proven call-resolving fixture (same shape as TestGraphMetricMaterialization):
    // local-var call `app.run(...)` and constructor call produce NopCodeCall edges
    private static final String ALPHA_JAVA = """
            package demo;

            import demo.util.NameUtil;
            import demo.svc.GreeterService;

            public class Alpha {
                private GreeterService greeter = new GreeterService();

                public String run(String name) {
                    String normalized = NameUtil.normalize(name);
                    return greeter.greet(normalized);
                }

                public static void main(String[] args) {
                    Alpha alpha = new Alpha();
                    alpha.run("world");
                }
            }
            """;

    private static final String BETA_JAVA = """
            package demo.util;

            public class NameUtil {
                public static String normalize(String name) {
                    return name.trim().toLowerCase();
                }
            }
            """;

    private CodeCacheManager cacheManagerOf(ICodeIndexService service) throws Exception {
        Field f = service.getClass().getDeclaredField("cacheManager");
        f.setAccessible(true);
        return (CodeCacheManager) f.get(service);
    }

    private static final String GREETER_JAVA = """
            package demo.svc;

            import demo.util.NameUtil;

            public class GreeterService {
                public String greet(String name) {
                    return "hello " + NameUtil.normalize(name);
                }
            }
            """;

    private Path writeProject() throws Exception {
        Path src = tempDir.resolve("src");
        writeJavaFile(src.resolve("demo"), "Alpha.java", ALPHA_JAVA);
        writeJavaFile(src.resolve("demo/util"), "NameUtil.java", BETA_JAVA);
        writeJavaFile(src.resolve("demo/svc"), "GreeterService.java", GREETER_JAVA);
        return src;
    }

    private void writeJavaFile(Path dir, String name, String content) throws Exception {
        Path file = dir.resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private long countRows(String indexId, String metricType) {
        IEntityDao<NopCodeGraphMetric> dao = daoProvider.daoFor(NopCodeGraphMetric.class);
        return dao.findAllByQuery(new io.nop.api.core.beans.query.QueryBean()
                        .addFilter(io.nop.api.core.beans.FilterBeans.and(
                                io.nop.api.core.beans.FilterBeans.eq("indexId", indexId),
                                io.nop.api.core.beans.FilterBeans.eq("metricType", metricType))))
                .size();
    }

    private void seedMaterialized(String indexId, Path src) {
        codeIndexService.indexDirectory(indexId, src.toString(), null);
        codeIndexService.materializeGraphMetrics(indexId);
        assertTrue(countRows(indexId, GraphMetricStoreHelper.METRIC_HUB) > 0, "pre-materialization required");
    }

    /** helper to avoid import cycle noise: metric type constants mirrored locally */
    static final class GraphMetricStoreHelper {
        static final String METRIC_HUB = "HUB";
    }

    private boolean cacheEntryAlive(ICodeIndexService service, String indexId) throws Exception {
        CodeCacheManager mgr = cacheManagerOf(service);
        return mgr.getValidEntry(indexId) != null;
    }

    @Test
    void testNoopIncrementalKeepsCacheAndMetrics() throws Exception {
        Path src = writeProject();
        String indexId = "n14-noop";

        codeIndexService.indexDirectory(indexId, src.toString(), null);
        // warm the cache via a structure query path (impact analysis triggers getOrRebuildCallGraph)
        codeIndexService.getImpactAnalysis(indexId, "demo.Alpha", 2);
        assertTrue(cacheEntryAlive(codeIndexService, indexId), "cache warmed before incremental");

        int changed = codeIndexService.triggerIncrementalIndex(indexId,
                "file:" + src.toAbsolutePath().toString().replace('\\', '/'), null);
        assertEquals(0, changed);

        assertTrue(cacheEntryAlive(codeIndexService, indexId),
                "no-op incremental must keep the analysis cache");

        codeIndexService.deleteIndex(indexId);
    }

    @Test
    void testActualChangeInvalidatesCache() throws Exception {
        Path src = writeProject();
        String indexId = "n14-actual";

        codeIndexService.indexDirectory(indexId, src.toString(), null);
        codeIndexService.getImpactAnalysis(indexId, "demo.Alpha", 2);
        assertTrue(cacheEntryAlive(codeIndexService, indexId));

        writeJavaFile(src.resolve("demo/util"), "NameUtil.java", """
                package demo.util;

                public class NameUtil {
                    public static String normalize(String name) {
                        return name.trim().toLowerCase().intern();
                    }
                }
                """);
        int changed = codeIndexService.triggerIncrementalIndex(indexId,
                "file:" + src.toAbsolutePath().toString().replace('\\', '/'), null);
        assertTrue(changed > 0);

        assertFalse(cacheEntryAlive(codeIndexService, indexId),
                "actual-change incremental must invalidate the analysis cache (asserted after method returns; persist may rebuild mid-flight)");

        codeIndexService.deleteIndex(indexId);
    }

    @Test
    void testIndexFileInvalidatesMaterializedRows() throws Exception {
        Path src = writeProject();
        String indexId = "n14-indexfile";

        seedMaterialized(indexId, src);

        codeIndexService.indexFile(indexId, "demo/Gamma.java",
                "package demo;\npublic class Gamma { public String id(String s) { return s; } }");

        assertEquals(0, countRows(indexId, GraphMetricStoreHelper.METRIC_HUB),
                "indexFile content change must invalidate materialized metric rows");

        codeIndexService.deleteIndex(indexId);
    }

    @Test
    void testBatchDeleteInvalidatesMaterializedRows() throws Exception {
        Path src = writeProject();
        String indexId = "n14-batchdelete";

        seedMaterialized(indexId, src);

        codeIndexService.batchDeleteFileRecords(indexId, java.util.List.of("demo/util/NameUtil.java"));

        assertEquals(0, countRows(indexId, GraphMetricStoreHelper.METRIC_HUB),
                "file deletion must invalidate materialized metric rows");

        codeIndexService.deleteIndex(indexId);
    }

    @Test
    void testDeleteIndexClearsCacheAndMetrics() throws Exception {
        Path src = writeProject();
        String indexId = "n14-delete";

        codeIndexService.indexDirectory(indexId, src.toString(), null);
        codeIndexService.materializeGraphMetrics(indexId);
        codeIndexService.getImpactAnalysis(indexId, "demo.Alpha", 2);
        assertTrue(cacheEntryAlive(codeIndexService, indexId));
        assertTrue(countRows(indexId, GraphMetricStoreHelper.METRIC_HUB) > 0);

        codeIndexService.deleteIndex(indexId);

        assertFalse(cacheEntryAlive(codeIndexService, indexId));
        assertEquals(0, countRows(indexId, GraphMetricStoreHelper.METRIC_HUB));
    }

    // ==================== W-12 (plan 2565, adjudication B) ====================
    // The four pre-commit invalidation sites must invalidate at the COMMIT point: rollback
    // keeps the cache (no over-invalidation), and a concurrent rebuild that backfills from the
    // not-yet-committed snapshot is cleared by the commit-point invalidation.

    private IDaoProvider swapDaoProvider(IDaoProvider replacement) throws Exception {
        Field f = codeIndexService.getClass().getDeclaredField("daoProvider");
        f.setAccessible(true);
        IDaoProvider original = (IDaoProvider) f.get(codeIndexService);
        f.set(codeIndexService, replacement);
        return original;
    }

    /** manual delegation proxy (zero-mockito convention): throws on NopCodeIndex deleteEntityById */
    static final class ThrowingDaoProvider implements IDaoProvider {
        private final IDaoProvider delegate;
        private final String failIndexId;

        ThrowingDaoProvider(IDaoProvider delegate, String failIndexId) {
            this.delegate = delegate;
            this.failIndexId = failIndexId;
        }

        @Override
        public java.util.Set<String> getEntityNames() {
            return delegate.getEntityNames();
        }

        @Override
        public String normalizeEntityName(String entityName) {
            return delegate.normalizeEntityName(entityName);
        }

        @Override
        public boolean hasDao(String entityName) {
            return delegate.hasDao(entityName);
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T extends io.nop.dao.api.IDaoEntity> IEntityDao<T> dao(String entityName) {
            IEntityDao<T> real = delegate.dao(entityName);
            if (!io.nop.code.dao.entity.NopCodeIndex.class.getName().equals(entityName)) {
                return real;
            }
            return (IEntityDao<T>) java.lang.reflect.Proxy.newProxyInstance(
                    IEntityDao.class.getClassLoader(), new Class<?>[]{IEntityDao.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("deleteEntityById")
                                && failIndexId.equals(args != null && args.length > 0 ? args[0] : null)) {
                            throw new IllegalStateException("w12-injected delete failure: " + failIndexId);
                        }
                        try {
                            return method.invoke(real, args);
                        } catch (java.lang.reflect.InvocationTargetException e) {
                            throw e.getCause();
                        }
                    });
        }

        @Override
        public <T extends io.nop.dao.api.IDaoEntity> IEntityDao<T> daoForTable(String tableName) {
            return delegate.daoForTable(tableName);
        }
    }

    /** manual delegation proxy: blocks the writer at NopCodeIndex deleteEntityById (backfill window) */
    static final class BlockingDaoProvider implements IDaoProvider {
        private final IDaoProvider delegate;
        private final String blockIndexId;
        private final CountDownLatch entered;
        private final CountDownLatch release;

        BlockingDaoProvider(IDaoProvider delegate, String blockIndexId,
                            CountDownLatch entered, CountDownLatch release) {
            this.delegate = delegate;
            this.blockIndexId = blockIndexId;
            this.entered = entered;
            this.release = release;
        }

        @Override
        public java.util.Set<String> getEntityNames() {
            return delegate.getEntityNames();
        }

        @Override
        public String normalizeEntityName(String entityName) {
            return delegate.normalizeEntityName(entityName);
        }

        @Override
        public boolean hasDao(String entityName) {
            return delegate.hasDao(entityName);
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T extends io.nop.dao.api.IDaoEntity> IEntityDao<T> dao(String entityName) {
            IEntityDao<T> real = delegate.dao(entityName);
            if (!io.nop.code.dao.entity.NopCodeIndex.class.getName().equals(entityName)) {
                return real;
            }
            return (IEntityDao<T>) java.lang.reflect.Proxy.newProxyInstance(
                    IEntityDao.class.getClassLoader(), new Class<?>[]{IEntityDao.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("deleteEntityById")
                                && blockIndexId.equals(args != null && args.length > 0 ? args[0] : null)) {
                            entered.countDown();
                            release.await(30, java.util.concurrent.TimeUnit.SECONDS);
                        }
                        try {
                            return method.invoke(real, args);
                        } catch (java.lang.reflect.InvocationTargetException e) {
                            throw e.getCause();
                        }
                    });
        }

        @Override
        public <T extends io.nop.dao.api.IDaoEntity> IEntityDao<T> daoForTable(String tableName) {
            return delegate.daoForTable(tableName);
        }
    }

    /** W-12 leg 1: rollback must NOT invalidate the analysis cache (commit-point semantics). */
    @Test
    void testW12RollbackKeepsCacheEntry() throws Exception {
        Path src = writeProject();
        String indexId = "w12-rollback";
        codeIndexService.indexDirectory(indexId, src.toString(), null);

        CodeCacheManager mgr = cacheManagerOf(codeIndexService);
        mgr.getOrRebuildSymbolTable(indexId, daoProvider, CodeSymbolConverter::toCodeSymbol);
        assertTrue(cacheEntryAlive(codeIndexService, indexId), "cache warmed before delete");

        IDaoProvider original = swapDaoProvider(new ThrowingDaoProvider(daoProvider, indexId));
        try {
            assertThrows(Exception.class, () -> codeIndexService.deleteIndex(indexId),
                    "injected delete failure must roll the transaction back");
        } finally {
            swapDaoProvider(original);
        }

        assertTrue(cacheEntryAlive(codeIndexService, indexId),
                "W-12: rollback must not invalidate the cache — invalidation runs at commit point only");
    }

    /** W-12 leg 2: successful delete invalidates after commit (green leg). */
    @Test
    void testW12InvalidateExecutesAfterCommit() throws Exception {
        Path src = writeProject();
        String indexId = "w12-commit";
        codeIndexService.indexDirectory(indexId, src.toString(), null);

        CodeCacheManager mgr = cacheManagerOf(codeIndexService);
        mgr.getOrRebuildSymbolTable(indexId, daoProvider, CodeSymbolConverter::toCodeSymbol);
        assertTrue(cacheEntryAlive(codeIndexService, indexId));

        codeIndexService.deleteIndex(indexId);

        assertFalse(cacheEntryAlive(codeIndexService, indexId),
                "commit-point invalidation must have executed");
    }

    /** W-12 leg 3: concurrent backfill window — a reader rebuilding from the not-yet-committed
     * snapshot is cleared by the commit-point invalidation (pre-fix: stale entry survives TTL). */
    @Test
    void testW12ConcurrentBackfillWindowCleared() throws Exception {
        Path src = writeProject();
        String indexId = "w12-window";
        codeIndexService.indexDirectory(indexId, src.toString(), null);

        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        IDaoProvider original = swapDaoProvider(
                new BlockingDaoProvider(daoProvider, indexId, entered, release));
        java.util.concurrent.atomic.AtomicReference<Exception> writerError =
                new java.util.concurrent.atomic.AtomicReference<>();
        Thread writer = new Thread(() -> {
            try {
                codeIndexService.deleteIndex(indexId);
            } catch (Exception e) {
                writerError.set(e);
            }
        });
        writer.start();
        try {
            assertTrue(entered.await(15, java.util.concurrent.TimeUnit.SECONDS),
                    "writer must reach the blocked delete point with the txn open");
            // reader rebuilds from the uncommitted-visible (stale) snapshot — backfill window
            CodeCacheManager mgr = cacheManagerOf(codeIndexService);
            SymbolTable stale = mgr.getOrRebuildSymbolTable(indexId, daoProvider,
                    CodeSymbolConverter::toCodeSymbol);
            assertTrue(stale.size() > 0, "reader must have backfilled from the stale snapshot");

            release.countDown();
            writer.join(30000);
            assertNull(writerError.get(), "deleteIndex must succeed after release");
            assertNull(mgr.getValidEntry(indexId),
                    "W-12: commit-point invalidation must clear the stale backfilled entry");
        } finally {
            release.countDown();
            writer.join(5000);
            swapDaoProvider(original);
        }
    }
}

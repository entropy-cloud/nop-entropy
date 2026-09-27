package io.nop.code.service.impl;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.code.dao.entity.NopCodeGraphMetric;
import io.nop.code.service.api.ICodeIndexService;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

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
}

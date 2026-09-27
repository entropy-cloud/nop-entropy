package io.nop.code.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.code.dao.entity.NopCodeGraphMetric;
import io.nop.code.service.api.ICodeIndexService;
import io.nop.code.service.graph.GraphMetricStore;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.graphql.core.engine.IGraphQLEngine;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for N1.2 global graph metric materialization: full index -> materialize
 * -> persisted rows -> read-only store -> invalidation semantics.
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        enableActionAuth = OptionalBoolean.FALSE)
public class TestGraphMetricMaterialization extends JunitAutoTestCase {

    @Inject
    ICodeIndexService codeIndexService;

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IGraphQLEngine graphQLEngine;

    @TempDir
    Path tempDir;

    // fixture: real call edges across classes/directories so the call graph has
    // >= 2 nodes (Leiden), betweenness/pagerank signal, and METHOD symbols (entry points)
    private static final String APP_JAVA = """
            package demo.app;

            import demo.svc.GreeterService;
            import demo.util.NameUtil;

            public class App {
                private GreeterService greeter = new GreeterService();

                public String run(String name) {
                    String normalized = NameUtil.normalize(name);
                    return greeter.greet(normalized);
                }

                public static void main(String[] args) {
                    App app = new App();
                    app.run("world");
                }
            }
            """;

    private static final String GREETER_JAVA = """
            package demo.svc;

            import demo.util.NameUtil;

            public class GreeterService {
                public String greet(String name) {
                    return "hello " + NameUtil.normalize(name);
                }
            }
            """;

    private static final String NAME_UTIL_JAVA = """
            package demo.util;

            public class NameUtil {
                public static String normalize(String name) {
                    return name.trim().toLowerCase();
                }
            }
            """;

    private Path writeProject() throws Exception {
        Path src = tempDir.resolve("src");
        writeJavaFile(src.resolve("demo/app"), "App.java", APP_JAVA);
        writeJavaFile(src.resolve("demo/svc"), "GreeterService.java", GREETER_JAVA);
        writeJavaFile(src.resolve("demo/util"), "NameUtil.java", NAME_UTIL_JAVA);
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

    @Test
    void testFullIndexMaterializesAllMetricFamilies() throws Exception {
        Path src = writeProject();
        String indexId = "graph-metrics-test";

        codeIndexService.indexDirectory(indexId, src.toString(), null);
        codeIndexService.materializeGraphMetrics(indexId);

        // COMMUNITY: call-graph nodes assigned to communities
        assertTrue(countRows(indexId, GraphMetricStore.METRIC_COMMUNITY) >= 2,
                "community rows should cover call-graph nodes");
        GraphMetricStore store = new GraphMetricStore(daoProvider);
        Map<String, Integer> communities = store.loadCommunities(indexId);
        assertFalse(communities.isEmpty());
        assertTrue(communities.values().stream().allMatch(java.util.Objects::nonNull));

        // BETWEENNESS + PAGE_RANK: scored rows
        assertTrue(countRows(indexId, GraphMetricStore.METRIC_BETWEENNESS) >= 2);
        assertTrue(countRows(indexId, GraphMetricStore.METRIC_PAGE_RANK) >= 2);
        Map<String, Double> pageRank = store.loadScores(indexId, GraphMetricStore.METRIC_PAGE_RANK);
        assertFalse(pageRank.isEmpty());
        assertTrue(pageRank.values().stream().allMatch(v -> v != null && v >= 0));

        // ENTRY_POINT: METHOD/CONSTRUCTOR symbols scored with a type
        assertTrue(countRows(indexId, GraphMetricStore.METRIC_ENTRY_POINT) >= 2);
        IEntityDao<NopCodeGraphMetric> dao = daoProvider.daoFor(NopCodeGraphMetric.class);
        boolean hasEntryPointType = dao.findAllByQuery(new io.nop.api.core.beans.query.QueryBean()
                        .addFilter(io.nop.api.core.beans.FilterBeans.and(
                                io.nop.api.core.beans.FilterBeans.eq("indexId", indexId),
                                io.nop.api.core.beans.FilterBeans.eq("metricType", GraphMetricStore.METRIC_ENTRY_POINT))))
                .stream().anyMatch(r -> r.getEntryPointType() != null);
        assertTrue(hasEntryPointType, "entry point rows should carry an entry point type");

        // read-only surface agrees with DB
        assertTrue(store.hasMaterialized(indexId, GraphMetricStore.METRIC_COMMUNITY));
        assertTrue(store.hasMaterialized(indexId, GraphMetricStore.METRIC_ENTRY_POINT));
        assertFalse(store.hasMaterialized(indexId, "NON_EXISTENT"));

        codeIndexService.deleteIndex(indexId);
    }

    @Test
    void testActualIncrementalInvalidatesButNoopDoesNot() throws Exception {
        Path src = writeProject();
        String indexId = "graph-metrics-incremental";

        codeIndexService.indexDirectory(indexId, src.toString(), null);
        codeIndexService.materializeGraphMetrics(indexId);
        assertTrue(countRows(indexId, GraphMetricStore.METRIC_COMMUNITY) > 0);

        // no-op incremental: nothing changed -> materialized rows must survive
        // (incremental requires the file: URI form for VFS resource collection)
        String vfsPath = "file:" + src.toAbsolutePath().toString().replace('\\', '/');
        int changed = codeIndexService.triggerIncrementalIndex(indexId, vfsPath, null);
        assertEquals(0, changed);
        assertTrue(countRows(indexId, GraphMetricStore.METRIC_COMMUNITY) > 0,
                "no-op incremental must not invalidate materialized metrics");

        // actual change -> rows invalidated
        writeJavaFile(src.resolve("demo/util"), "NameUtil.java", """
                package demo.util;

                public class NameUtil {
                    public static String normalize(String name) {
                        return name.trim().toLowerCase().intern();
                    }
                }
                """);
        changed = codeIndexService.triggerIncrementalIndex(indexId, vfsPath, null);
        assertTrue(changed > 0);
        assertEquals(0, countRows(indexId, GraphMetricStore.METRIC_COMMUNITY),
                "actual incremental change must invalidate materialized metrics");

        // next full index materializes again
        codeIndexService.materializeGraphMetrics(indexId);
        assertTrue(countRows(indexId, GraphMetricStore.METRIC_COMMUNITY) > 0);

        codeIndexService.deleteIndex(indexId);
    }

    @Test
    void testTriggerFullIndexMutationMaterializesEndToEnd() throws Exception {
        Path src = writeProject();
        String indexId = "graph-metrics-graphql";

        io.nop.api.core.beans.ApiRequest<java.util.Map<String, Object>> request =
                new io.nop.api.core.beans.ApiRequest<>();
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("indexId", indexId);
        data.put("projectPath", src.toAbsolutePath().toString());
        request.setData(data);
        io.nop.graphql.core.IGraphQLExecutionContext ctx = graphQLEngine.newRpcContext(
                io.nop.graphql.core.ast.GraphQLOperationType.mutation,
                "NopCodeIndex__triggerFullIndex", request);
        io.nop.api.core.beans.ApiResponse<?> response =
                io.nop.api.core.util.FutureHelper.syncGet(graphQLEngine.executeRpcAsync(ctx));

        assertTrue(response.isOk(), "triggerFullIndex should succeed, got: " + response.getMsg());
        // biz wiring materializes as part of the mutation -- rows must exist with no explicit call
        assertTrue(countRows(indexId, GraphMetricStore.METRIC_COMMUNITY) > 0,
                "triggerFullIndex should materialize graph metrics end to end");
        assertTrue(countRows(indexId, GraphMetricStore.METRIC_ENTRY_POINT) > 0);

        codeIndexService.deleteIndex(indexId);
    }

    @Test
    void testDeleteIndexClearsMaterializedRows() throws Exception {
        Path src = writeProject();
        String indexId = "graph-metrics-delete";

        codeIndexService.indexDirectory(indexId, src.toString(), null);
        codeIndexService.materializeGraphMetrics(indexId);
        assertTrue(countRows(indexId, GraphMetricStore.METRIC_COMMUNITY) > 0);

        codeIndexService.deleteIndex(indexId);
        assertEquals(0, countRows(indexId, GraphMetricStore.METRIC_COMMUNITY));
        assertEquals(0, countRows(indexId, GraphMetricStore.METRIC_PAGE_RANK));
    }
}

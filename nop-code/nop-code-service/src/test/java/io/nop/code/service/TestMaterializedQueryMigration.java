package io.nop.code.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.code.dao.entity.NopCodeGraphMetric;
import io.nop.code.service.api.ICodeIndexService;
import io.nop.code.service.graph.GraphMetricStore;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * N1.3 behavior-migration proof: the global analysis queries read materialized metric rows
 * (mutated rows are returned verbatim), self-heal by materializing on first query, and
 * re-index invalidates stale rows.
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        enableActionAuth = OptionalBoolean.FALSE)
public class TestMaterializedQueryMigration extends JunitAutoTestCase {

    @Inject
    ICodeIndexService codeIndexService;

    @Inject
    IDaoProvider daoProvider;

    @Inject
    io.nop.orm.IOrmTemplate ormTemplate;

    @TempDir
    Path tempDir;

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

    private String singleSymbolId(String indexId, String metricType) {
        IEntityDao<NopCodeGraphMetric> dao = daoProvider.daoFor(NopCodeGraphMetric.class);
        return dao.findAllByQuery(new io.nop.api.core.beans.query.QueryBean()
                        .addFilter(io.nop.api.core.beans.FilterBeans.and(
                                io.nop.api.core.beans.FilterBeans.eq("indexId", indexId),
                                io.nop.api.core.beans.FilterBeans.eq("metricType", metricType))))
                .get(0).getSymbolId();
    }

    @Test
    void testQueryReadsMutatedMaterializedRows() throws Exception {
        Path src = writeProject();
        String indexId = "n13-mutation-proof";

        codeIndexService.indexDirectory(indexId, src.toString(), null);
        codeIndexService.materializeGraphMetrics(indexId);

        // mutate a betweenness row: if the query recomputed, the mutation would be invisible
        IEntityDao<NopCodeGraphMetric> dao = daoProvider.daoFor(NopCodeGraphMetric.class);
        NopCodeGraphMetric row = dao.findAllByQuery(new io.nop.api.core.beans.query.QueryBean()
                        .addFilter(io.nop.api.core.beans.FilterBeans.and(
                                io.nop.api.core.beans.FilterBeans.eq("indexId", indexId),
                                io.nop.api.core.beans.FilterBeans.eq("metricType", GraphMetricStore.METRIC_BETWEENNESS))))
                .get(0);
        row.setScore(1234.5);
        ormTemplate.runInSession(session -> {
            dao.updateEntityDirectly(row);
            return null;
        });

        var result = codeIndexService.getCriticalNodes(indexId, 20);
        assertNotNull(result);
        assertTrue(result.getBridgeNodes().stream()
                        .anyMatch(b -> Double.compare(b.getScore(), 1234.5) == 0),
                "critical nodes must read the mutated materialized betweenness row, proving no recompute");

        codeIndexService.deleteIndex(indexId);
    }

    @Test
    void testSelfHealOnUnmaterializedIndex() throws Exception {
        Path src = writeProject();
        String indexId = "n13-self-heal";

        codeIndexService.indexDirectory(indexId, src.toString(), null);
        assertEquals(0, countRows(indexId, GraphMetricStore.METRIC_COMMUNITY), "no rows before query");

        // first query self-heals: computes once, persists, returns the read result
        var result = codeIndexService.detectCommunities(indexId);
        assertNotNull(result);
        assertTrue(result.getTotalSymbols() > 0);
        assertTrue(countRows(indexId, GraphMetricStore.METRIC_COMMUNITY) > 0,
                "self-heal must persist materialized rows");
        // the returned snapshot equals the persisted one: totalSymbols == community member rows
        long memberRows = countRows(indexId, GraphMetricStore.METRIC_COMMUNITY);
        assertEquals(memberRows, result.getTotalSymbols(),
                "returned totalSymbols must equal persisted COMMUNITY rows (compute-once)");

        // second query is served from the materialized rows (same result guaranteed)
        var second = codeIndexService.detectCommunities(indexId);
        assertEquals(result.getTotalSymbols(), second.getTotalSymbols());
        assertEquals(result.getTotalCommunities(), second.getTotalCommunities());
        // materialized reads carry no per-query processing time by contract (N1.3)
        assertEquals(0, second.getProcessingTimeMs());

        codeIndexService.deleteIndex(indexId);
    }

    @Test
    void testReIndexInvalidatesStaleMaterializedRows() throws Exception {
        Path src = writeProject();
        String indexId = "n13-reindex";

        codeIndexService.indexDirectory(indexId, src.toString(), null);
        codeIndexService.materializeGraphMetrics(indexId);
        long rowsBefore = countRows(indexId, GraphMetricStore.METRIC_HUB);
        assertTrue(rowsBefore > 0);

        // change content: NameUtil gains a second method -> symbol/call-graph change
        writeJavaFile(src.resolve("demo/app"), "App.java", """
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
                        app.run("changed-world");
                    }
                }
                """);
        codeIndexService.indexDirectory(indexId, src.toString(), null);

        assertEquals(0, countRows(indexId, GraphMetricStore.METRIC_HUB),
                "re-index must invalidate materialized rows");

        // next query self-heals from the NEW content
        var result = codeIndexService.getGraphAnalysis(indexId, 20);
        assertNotNull(result);
        assertTrue(countRows(indexId, GraphMetricStore.METRIC_HUB) > 0);

        codeIndexService.deleteIndex(indexId);
    }
}

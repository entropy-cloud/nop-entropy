package io.nop.code.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.autotest.NopTestProperty;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.util.FutureHelper;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.code.service.api.ICodeIndexService;
import io.nop.code.service.impl.CodeIndexService;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.search.lucene.LuceneSearchEngine;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.HashMap;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * N4.1: production default assembly of the real Lucene engine + engine-path e2e.
 *
 * <p>nop-search-lucene on the (test) classpath registers {@code nopSearchEngine} via the
 * module autoconfig file; CoreInitialization's app container then wires it into
 * {@code CodeIndexService} by type. These tests pin that wiring and prove the engine path
 * end to end through the GraphQL {@code searchCode} entry point. Every hit is asserted to
 * carry {@code matchType=SEARCH_ENGINE} — the only discriminator against the silent
 * DB-LIKE fallback in {@code CodeSearchService.searchViaEngine}.
 *
 * <p>index-dir is overridden to ./target (see nop-code-search-engine-test.yaml): the
 * default value resolves to a filesystem-root absolute path.
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        enableActionAuth = OptionalBoolean.FALSE)
@NopTestProperty(name = "nop.search.index-dir", value = "./target/nop-code-search-engine-test-indices")
public class TestCodeSearchEngineAssembly extends JunitAutoTestCase {

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    ICodeIndexService codeIndexService;

    @TempDir
    Path tempDir;

    private static final String ZEBRA_V1 = """
            package demo;

            public class Zebra {
                public String gallop(int speed) { return "fast"; }
            }
            """;

    // same class, method gallop removed
    private static final String ZEBRA_V2 = """
            package demo;

            public class Zebra {
                public String graze(String grass) { return grass; }
            }
            """;

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> searchCode(String indexId, String query) {
        Map<String, Object> data = new HashMap<>();
        data.put("indexId", indexId);
        data.put("query", query);
        data.put("searchType", "COMBINED");
        ApiResponse<?> response = rpcQuery("NopCodeSymbol__searchCode", data);
        assertTrue(response.isOk(), "searchCode should succeed, got: " + response.getMsg());
        return (List<Map<String, Object>>) response.getData();
    }

    private ApiResponse<?> rpcQuery(String operation, Map<String, Object> data) {
        ApiRequest<Map<String, Object>> request = new ApiRequest<>();
        request.setData(data);
        IGraphQLExecutionContext ctx = graphQLEngine.newRpcContext(
                GraphQLOperationType.query, operation, request);
        return FutureHelper.syncGet(graphQLEngine.executeRpcAsync(ctx));
    }

    /**
     * Wiring evidence: the app container's autoconfig + by-type injection must have wired the
     * real Lucene engine into CodeIndexService without any explicit test-side set.
     */
    @Test
    void testEngineAutoWiredIntoIndexService() throws Exception {
        Field field = CodeIndexService.class.getDeclaredField("searchEngine");
        field.setAccessible(true);
        Object engine = field.get(codeIndexService);
        assertNotNull(engine, "searchEngine must be auto-wired when nop-search-lucene is present");
        assertInstanceOf(LuceneSearchEngine.class, engine);
    }

    /**
     * Engine e2e: index a project, then searchCode must return an engine-backed hit
     * (matchType=SEARCH_ENGINE), not the DB-LIKE fallback.
     */
    @Test
    void testSearchCodeHitsViaRealEngine() throws Exception {
        Path src = tempDir.resolve("src");
        Files.createDirectories(src.resolve("demo"));
        Files.writeString(src.resolve("demo/Zebra.java"), ZEBRA_V1);

        String indexId = "n41_engine_e2e";
        assertTrue(codeIndexService.indexDirectory(indexId, src.toString(), null) >= 1);

        List<Map<String, Object>> hits = searchCode(indexId, "gallop");
        assertFalse(hits.isEmpty(), "engine search must find the indexed symbol");
        assertTrue(hits.stream().allMatch(h -> "SEARCH_ENGINE".equals(h.get("matchType"))),
                "all hits must come from the search engine, got: " + hits);
        assertTrue(hits.stream().anyMatch(h -> String.valueOf(h.get("matchedQualifiedName"))
                        .contains("gallop")),
                "hit must be the gallop symbol, got: " + hits);

        codeIndexService.deleteIndex(indexId);
    }

    /**
     * Engine e2e (incremental sync): re-indexing the file without the symbol must remove its
     * doc from the real Lucene index (removeDocs), so searchCode stops returning it.
     */
    @Test
    void testIncrementalDeleteSyncedToEngine() throws Exception {
        Path src = tempDir.resolve("src");
        Files.createDirectories(src.resolve("demo"));
        Files.writeString(src.resolve("demo/Zebra.java"), ZEBRA_V1);

        String indexId = "n41_engine_sync";
        assertTrue(codeIndexService.indexDirectory(indexId, src.toString(), null) >= 1);
        assertFalse(searchCode(indexId, "gallop").isEmpty(), "precondition: hit via engine");

        codeIndexService.indexFile(indexId, "demo/Zebra.java", ZEBRA_V2);

        assertTrue(searchCode(indexId, "gallop").isEmpty(),
                "removed symbol must disappear from engine search results");
        codeIndexService.deleteIndex(indexId);
    }
}

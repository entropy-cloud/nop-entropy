package io.nop.code.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;

import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.util.FutureHelper;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.code.service.api.ICodeIndexService;
import io.nop.dao.api.IDaoProvider;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.engine.IGraphQLEngine;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * N2.1 end-to-end: full index -> GraphQL getSurprisingConnections -> scored connections
 * with reasons from the typed relation graph.
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        enableActionAuth = OptionalBoolean.FALSE)
public class TestSurprisingConnections extends JunitAutoTestCase {

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    ICodeIndexService codeIndexService;

    @Inject
    IDaoProvider daoProvider;

    @TempDir
    Path tempDir;

    private static final String ALPHA_JAVA = """
            package demo;

            import demo.svc.GreeterService;
            import demo.util.NameUtil;

            public class Alpha extends NameUtil {
                private GreeterService greeter = new GreeterService();

                public String run(String name) {
                    String normalized = normalize(name);
                    return greeter.greet(normalized);
                }

                public static void main(String[] args) {
                    Alpha alpha = new Alpha();
                    alpha.run("world");
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
        writeJavaFile(src.resolve("demo/util"), "NameUtil.java", NAME_UTIL_JAVA);
        writeJavaFile(src.resolve("demo/svc"), "GreeterService.java", GREETER_JAVA);
        return src;
    }

    private void writeJavaFile(Path dir, String name, String content) throws Exception {
        Path file = dir.resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> queryViaGraphQL(String indexId, Integer topN, Integer minScore) {
        ApiRequest<Map<String, Object>> request = new ApiRequest<>();
        Map<String, Object> data = new HashMap<>();
        data.put("indexId", indexId);
        if (topN != null) data.put("topN", topN);
        if (minScore != null) data.put("minScore", minScore);
        request.setData(data);
        IGraphQLExecutionContext ctx = graphQLEngine.newRpcContext(
                GraphQLOperationType.query, "NopCodeIndex__getSurprisingConnections", request);
        ApiResponse<?> response = FutureHelper.syncGet(graphQLEngine.executeRpcAsync(ctx));
        assertTrue(response.isOk(), "getSurprisingConnections should succeed, got: " + response.getMsg());
        return (List<Map<String, Object>>) response.getData();
    }

    @Test
    void testSurprisingConnectionsEndToEnd() throws Exception {
        Path src = writeProject();
        String indexId = "n21-e2e";

        // triggerFullIndex materializes communities (self-heal source) and indexes the project
        codeIndexService.indexDirectory(indexId, src.toString(), null);
        codeIndexService.materializeGraphMetrics(indexId);

        List<Map<String, Object>> connections = queryViaGraphQL(indexId, null, null);
        assertNotNull(connections);
        assertFalse(connections.isEmpty(), "fixture must produce scored connections");

        for (Map<String, Object> conn : connections) {
            assertNotNull(conn.get("sourceSymbolId"));
            assertNotNull(conn.get("targetSymbolId"));
            assertNotNull(conn.get("relation"));
            assertNotNull(conn.get("reasons"));
            assertTrue(((Number) conn.get("score")).intValue() >= 1,
                    "default minScore 1 must hold");
        }

        // scores sorted descending
        int prev = Integer.MAX_VALUE;
        for (Map<String, Object> conn : connections) {
            int score = ((Number) conn.get("score")).intValue();
            assertTrue(score <= prev, "scores must be descending");
            prev = score;
        }

        // minScore filter
        List<Map<String, Object>> filtered = queryViaGraphQL(indexId, null, 5);
        for (Map<String, Object> conn : filtered) {
            assertTrue(((Number) conn.get("score")).intValue() >= 5);
        }

        codeIndexService.deleteIndex(indexId);
    }

    @Test
    void testSelfHealOnUnmaterializedIndex() throws Exception {
        Path src = writeProject();
        String indexId = "n21-selfheal";

        codeIndexService.indexDirectory(indexId, src.toString(), null);
        // no explicit materialization: the query self-heals via N1.3 gating
        List<Map<String, Object>> connections = queryViaGraphQL(indexId, 5, null);
        assertNotNull(connections);
        assertTrue(connections.size() <= 5, "topN must cap the result");

        codeIndexService.deleteIndex(indexId);
    }
}

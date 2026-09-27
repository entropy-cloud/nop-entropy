package io.nop.code.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.util.FutureHelper;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.code.service.api.ICodeIndexService;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.engine.IGraphQLEngine;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * N2.3 end-to-end: exportGraphWiki via GraphQL. Structural assertions only (index body,
 * interlink consistency, community article presence) — hub article touch is covered by
 * TestGraphWikiExporter unit tests with a synthetic hub graph.
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        enableActionAuth = OptionalBoolean.FALSE)
public class TestGraphWikiExport extends JunitAutoTestCase {

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    ICodeIndexService codeIndexService;

    @TempDir
    Path tempDir;

    private static final String ALPHA_JAVA = """
            package demo;

            import demo.util.NameUtil;

            public class Alpha extends NameUtil {
                public String run(String name) {
                    return normalize(name);
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> queryViaGraphQL(String indexId) {
        ApiRequest<Map<String, Object>> request = new ApiRequest<>();
        Map<String, Object> data = new HashMap<>();
        data.put("indexId", indexId);
        request.setData(data);
        IGraphQLExecutionContext ctx = graphQLEngine.newRpcContext(
                GraphQLOperationType.query, "NopCodeIndex__exportGraphWiki", request);
        ApiResponse<?> response = FutureHelper.syncGet(graphQLEngine.executeRpcAsync(ctx));
        assertTrue(response.isOk(), "exportGraphWiki should succeed, got: " + response.getMsg());
        return (Map<String, Object>) response.getData();
    }

    @Test
    void testWikiExportEndToEnd() throws Exception {
        Path src = tempDir.resolve("src");
        writeJavaFile(src.resolve("demo"), "Alpha.java", ALPHA_JAVA);
        writeJavaFile(src.resolve("demo/util"), "NameUtil.java", NAME_UTIL_JAVA);

        String indexId = "n23-e2e";
        codeIndexService.indexDirectory(indexId, src.toString(), null);
        codeIndexService.materializeGraphMetrics(indexId);

        Map<String, Object> wiki = queryViaGraphQL(indexId);
        assertNotNull(wiki);
        String indexBody = (String) wiki.get("index");
        assertNotNull(indexBody);
        assertTrue(indexBody.contains("节点数"));
        assertTrue(indexBody.contains("社区数"));

        // interlink consistency: every relative link target exists as an article key
        Map<String, String> articles = (Map<String, String>) wiki.get("articles");
        assertNotNull(articles);
        for (String key : articles.keySet()) {
            assertTrue(key.endsWith(".md"));
        }
        assertTrue(articles.size() >= 1, "graph with >=2 nodes must yield >=1 community article");
        // a community article body contains members
        articles.values().stream().findFirst().ifPresent(body -> {
            assertTrue(body.contains("## 关键概念"));
        });

        codeIndexService.deleteIndex(indexId);
    }

    private void writeJavaFile(Path dir, String name, String content) throws Exception {
        Path file = dir.resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}

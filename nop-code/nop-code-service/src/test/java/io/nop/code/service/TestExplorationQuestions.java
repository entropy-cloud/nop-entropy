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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * N2.2 end-to-end: the exploration questions pipeline through GraphQL. Type coverage is
 * asserted by TestGraphQuestionGenerator unit tests; this test verifies the wiring (GraphQL
 * -> BizModel -> generator) and the no_signal contract on an empty index.
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        enableActionAuth = OptionalBoolean.FALSE)
public class TestExplorationQuestions extends JunitAutoTestCase {

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

    private void writeJavaFile(Path dir, String name, String content) throws Exception {
        Path file = dir.resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> queryViaGraphQL(String indexId, Integer topN) {
        ApiRequest<Map<String, Object>> request = new ApiRequest<>();
        Map<String, Object> data = new HashMap<>();
        data.put("indexId", indexId);
        if (topN != null) data.put("topN", topN);
        request.setData(data);
        IGraphQLExecutionContext ctx = graphQLEngine.newRpcContext(
                GraphQLOperationType.query, "NopCodeIndex__getExplorationQuestions", request);
        ApiResponse<?> response = FutureHelper.syncGet(graphQLEngine.executeRpcAsync(ctx));
        assertTrue(response.isOk(), "getExplorationQuestions should succeed, got: " + response.getMsg());
        return (List<Map<String, Object>>) response.getData();
    }

    @Test
    void testExplorationQuestionsEndToEnd() throws Exception {
        Path src = tempDir.resolve("src1");
        writeJavaFile(src.resolve("demo"), "Alpha.java", ALPHA_JAVA);
        writeJavaFile(src.resolve("demo/util"), "NameUtil.java", NAME_UTIL_JAVA);

        String indexId = "n22-e2e";
        codeIndexService.indexDirectory(indexId, src.toString(), null);
        codeIndexService.materializeGraphMetrics(indexId);

        List<Map<String, Object>> questions = queryViaGraphQL(indexId, null);
        assertNotNull(questions);
        assertFalse(questions.isEmpty());

        boolean hasNoSignal = false;
        for (Map<String, Object> q : questions) {
            assertNotNull(q.get("type"));
            if ("no_signal".equals(q.get("type"))) {
                hasNoSignal = true;
                assertNull(q.get("question"));
            } else {
                String suggested = (String) q.get("suggestedQuery");
                assertNotNull(suggested, "non-no_signal questions must carry an executable query");
                assertTrue(suggested.contains("idx1") || suggested.contains(indexId),
                        "suggestedQuery must embed the indexId");
            }
        }
        if (questions.size() == 1) {
            assertTrue(hasNoSignal, "single-item result must be no_signal");
        }

        codeIndexService.deleteIndex(indexId);
    }

    @Test
    void testEmptyIndexReturnsNoSignal() throws Exception {
        String indexId = "n22-empty";
        // create the index entity with nothing indexed: query must return explicit no_signal
        codeIndexService.indexFile(indexId, "demo/Empty.java",
                "package demo;\npublic class Empty { int x; }");

        List<Map<String, Object>> questions = queryViaGraphQL(indexId, null);
        assertNotNull(questions);
        assertEquals(1, questions.size(), "no-signal result is a single explicit item");
        assertEquals("no_signal", questions.get(0).get("type"));

        codeIndexService.deleteIndex(indexId);
    }
}

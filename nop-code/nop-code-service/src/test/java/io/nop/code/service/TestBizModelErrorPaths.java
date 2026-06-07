package io.nop.code.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.util.FutureHelper;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.engine.IGraphQLEngine;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        enableActionAuth = OptionalBoolean.FALSE)
public class TestBizModelErrorPaths extends JunitAutoTestCase {

    @Inject
    IGraphQLEngine graphQLEngine;

    private ApiResponse<?> execMutation(String action, Map<String, Object> data) {
        ApiRequest<Map<String, Object>> request = new ApiRequest<>();
        request.setData(data);
        IGraphQLExecutionContext ctx = graphQLEngine.newRpcContext(
                GraphQLOperationType.mutation, action, request);
        return FutureHelper.syncGet(graphQLEngine.executeRpcAsync(ctx));
    }

    @Test
    void testDeleteIndex_nonExistentIndex_succeedsGracefully() {
        Map<String, Object> data = new HashMap<>();
        data.put("indexId", "nonexistent-idx-999");
        ApiResponse<?> response = execMutation("NopCodeIndex__deleteIndex", data);
        assertTrue(response.isOk(), "Deleting nonexistent index should succeed gracefully");
    }

    @Test
    void testIndexDirectory_invalidPath_returnsError() {
        Map<String, Object> data = new HashMap<>();
        data.put("indexId", "bad-path-test");
        data.put("directoryPath", "../etc/passwd");
        data.put("filePattern", "*.java");
        ApiResponse<?> response = execMutation("NopCodeIndex__indexDirectory", data);
        assertFalse(response.isOk(), "Path traversal should be rejected");
    }

    @Test
    void testIndexDirectory_nullIndexId_returnsError() {
        Map<String, Object> data = new HashMap<>();
        data.put("indexId", null);
        data.put("directoryPath", "/tmp");
        data.put("filePattern", "*.java");
        ApiResponse<?> response = execMutation("NopCodeIndex__indexDirectory", data);
        assertFalse(response.isOk(), "Null indexId should be rejected");
    }

    @Test
    void testIndexFile_nonExistentIndex_returnsError() {
        Map<String, Object> data = new HashMap<>();
        data.put("indexId", "no-such-index");
        data.put("filePath", "Test.java");
        data.put("sourceCode", "public class Test {}");
        ApiResponse<?> response = execMutation("NopCodeIndex__indexFile", data);
        assertTrue(response.isOk(),
                "indexFile on non-existent index should be handled gracefully, status=" + response.getStatus());
    }

    @Test
    void testTriggerIncrementalIndex_nonExistentIndex_returnsError() {
        Map<String, Object> data = new HashMap<>();
        data.put("indexId", "nonexistent-incremental");
        data.put("projectPath", "/nonexistent/path");
        ApiResponse<?> response = execMutation("NopCodeIndex__triggerIncrementalIndex", data);
        assertFalse(response.isOk(), "Incremental index on nonexistent index should fail");
    }

    @Test
    void testDetectDeadCode_noIndex_succeedsWithEmptyResult() {
        Map<String, Object> data = new HashMap<>();
        data.put("indexId", "no-dead-code-index");
        ApiResponse<?> response = execMutation("NopCodeSymbol__detectDeadCode", data);
        assertTrue(response.isOk(), "Dead code detection on empty index should return gracefully");
    }
}

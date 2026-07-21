package io.nop.metadata.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.engine.IGraphQLEngine;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopMetaClassificationBizModelIntegration extends JunitBaseTestCase {

    public TestNopMetaClassificationBizModelIntegration() {
        setTestConfig("nop.orm.init-database-schema", true);
    }

    @Inject
    IGraphQLEngine graphQLEngine;

    @SuppressWarnings("unchecked")
    private <T> ApiResponse<T> executeRpc(GraphQLOperationType opType, String action, ApiRequest<?> request) {
        IGraphQLExecutionContext ctx = graphQLEngine.newRpcContext(opType, action, request);
        return (ApiResponse<T>) graphQLEngine.executeRpc(ctx);
    }

    private GraphQLRequestBean req(String query) {
        GraphQLRequestBean request = new GraphQLRequestBean();
        request.setQuery(query);
        return request;
    }

    private static String getMsg(ApiResponse<?> resp) {
        return resp == null ? "null" : resp.getMsg();
    }

    private static final String PROVIDER = "system";

    @Test
    public void testSaveAndGet() {
        ApiResponse<?> saveResp = executeRpc(GraphQLOperationType.mutation, "NopMetaClassification__save",
                ApiRequest.build(Map.of("data", Map.of(
                        "classificationId", "cls-it-sg-001",
                        "name", "save-get-cls",
                        "displayName", "Save Get Test",
                        "provider", PROVIDER
                ))));
        assertTrue(saveResp.isOk(), "save must succeed: " + getMsg(saveResp));

        ApiResponse<?> getResp = executeRpc(GraphQLOperationType.query, "NopMetaClassification__get",
                ApiRequest.build(Map.of("id", "cls-it-sg-001")));
        assertTrue(getResp.isOk(), "get must succeed: " + getMsg(getResp));
    }

    @Test
    public void testDelete() {
        // save via RPC
        ApiResponse<?> saveResp = executeRpc(GraphQLOperationType.mutation, "NopMetaClassification__save",
                ApiRequest.build(Map.of("data", Map.of(
                        "classificationId", "cls-it-del-001",
                        "name", "del-cls",
                        "displayName", "Delete Test",
                        "provider", PROVIDER
                ))));
        assertTrue(saveResp.isOk(), "save must succeed before delete: " + getMsg(saveResp));

        // delete via RPC
        ApiResponse<?> delResp = executeRpc(GraphQLOperationType.mutation, "NopMetaClassification__delete",
                ApiRequest.build(Map.of("id", "cls-it-del-001")));
        assertTrue(delResp.isOk(), "delete must succeed: " + getMsg(delResp));

        // verify deleted
        ApiResponse<?> getResp = executeRpc(GraphQLOperationType.query, "NopMetaClassification__get",
                ApiRequest.build(Map.of("id", "cls-it-del-001")));
        assertFalse(getResp.isOk(), "get after delete must fail");
    }
}

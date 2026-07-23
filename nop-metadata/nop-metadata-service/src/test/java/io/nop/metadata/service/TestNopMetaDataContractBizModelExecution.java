package io.nop.metadata.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.engine.IGraphQLEngine;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopMetaDataContractBizModelExecution extends JunitBaseTestCase {

    public TestNopMetaDataContractBizModelExecution() {
        setTestConfig("nop.orm.init-database-schema", true);
    }

    @Inject
    IGraphQLEngine graphQLEngine;

    @SuppressWarnings("unchecked")
    private <T> ApiResponse<T> executeRpc(GraphQLOperationType opType, String action, ApiRequest<?> request) {
        IGraphQLExecutionContext ctx = graphQLEngine.newRpcContext(opType, action, request);
        return (ApiResponse<T>) graphQLEngine.executeRpc(ctx);
    }

    @Test
    public void testCheckContract_notFound() {
        ApiResponse<?> resp = executeRpc(GraphQLOperationType.mutation, "NopMetaDataContract__checkContract",
                ApiRequest.build(Map.of("contractId", "__not_exist__")));
        assertFalse(resp.isOk(), "must fail for non-existent contract");
        assertTrue(resp.getCode() != null && (resp.getCode().contains("ERR_DAO_UNKNOWN_ENTITY") || resp.getCode().contains("unknown-entity")),
                "error must use unknown-entity code: " + resp.getCode());
    }
}

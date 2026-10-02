package io.nop.biz.impl;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.exceptions.NopException;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.engine.IGraphQLEngine;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 通过 {@link IGraphQLEngine}（newRpcContext + executeRpc）验证 nop-biz 反射式
 * BizModel 调用语义：@BizLoader 字段加载、xbiz 脚本 action + 状态机流转、
 * debug 模式下 DevDoc 内省操作，以及未知操作的 schema 校验失败路径。
 * <p>
 * 全部用例走 GraphQL 引擎完整管道，不直接调用 bizObj.method（testing.md 禁令）。
 * 被测对象：MyObject（MyObjectBizModel + MyObject.xbiz 状态机）、DevDoc（DevDocBizModel，
 * 测试 application.yaml 中 nop.debug=true 使其注册）。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestGraphQLBizActionSemantics extends JunitBaseTestCase {

    @Inject
    IGraphQLEngine graphQLEngine;

    /**
     * MyObject__get 经引擎返回的字段必须包含 @BizLoader 计算字段 extValue：
     * name="ret_{id}" 且 extValue="ext_" + name，证明 loader 链路在引擎管道内生效。
     */
    @Test
    public void testMyObjectGetAppliesBizLoaderField() {
        ApiResponse<?> response = executeRpc(GraphQLOperationType.query, "MyObject__get",
                Map.of("id", "123"));

        assertEquals(0, response.getStatus(), "MyObject__get should succeed, got: " + response);
        Map<String, Object> data = dataMap(response);
        assertEquals("ret_123", data.get("name"), "get must return name built from id");
        assertEquals("ext_ret_123", data.get("extValue"),
                "@BizLoader extValue must be computed from the loaded name");
    }

    /**
     * MyObject__approve 走 xbiz 脚本 action + 状态机：实体状态从 default 流转到 approved。
     */
    @Test
    public void testMyObjectApproveTransitionsStateMachine() {
        ApiResponse<?> response = executeRpc(GraphQLOperationType.mutation, "MyObject__approve",
                Map.of("id", "sm-1"));

        assertEquals(0, response.getStatus(), "approve should succeed, got: " + response);
        Map<String, Object> data = dataMap(response);
        assertEquals("approved", data.get("status"),
                "state machine must transition status to the approved final state");
        assertEquals("ret_sm-1", data.get("name"), "approve must return the entity loaded by id");
    }

    /**
     * debug 模式下 DevDoc__graphql 暴露全量 schema：应包含测试模块注册的业务对象。
     */
    @Test
    public void testDevDocGraphqlExposesRegisteredBizObjects() {
        ApiResponse<?> response = executeRpc(GraphQLOperationType.query, "DevDoc__graphql", Map.of());

        assertEquals(0, response.getStatus(), "DevDoc__graphql should succeed, got: " + response);
        String schema = String.valueOf(response.getData());
        assertTrue(schema.contains("TestIndex"),
                "schema dump must contain the crud biz object TestIndex");
        assertTrue(schema.contains("MyObject"),
                "schema dump must contain the reflection biz object MyObject");
    }

    /**
     * DevDoc__configVars 返回配置变量清单，包含测试数据源配置项，且条目结构完整。
     */
    @Test
    public void testDevDocConfigVarsListsDatasourceConfig() {
        ApiResponse<?> response = executeRpc(GraphQLOperationType.query, "DevDoc__configVars", Map.of());

        assertEquals(0, response.getStatus(), "DevDoc__configVars should succeed, got: " + response);
        List<Map<String, Object>> vars = listRows(response);
        assertNotNull(vars);
        assertTrue(!vars.isEmpty(), "config var list must not be empty");

        boolean hasDatasourceUrl = vars.stream()
                .anyMatch(v -> String.valueOf(v.get("name")).contains("datasource"));
        assertTrue(hasDatasourceUrl, "test datasource config must be listed, got names: "
                + vars.stream().map(v -> v.get("name")).limit(20).toList());
    }

    /**
     * 未知操作在 schema 层被拒绝：newRpcContext 阶段抛 nop.err.graphql.unknown-operation，
     * 引擎不做静默放行（schema 暴露校验语义）。
     */
    @Test
    public void testUnknownOperationRejectedBySchema() {
        NopException ex = assertThrows(NopException.class,
                () -> executeRpc(GraphQLOperationType.query, "TestIndex__noSuchAction", Map.of()));
        assertEquals("nop.err.graphql.unknown-operation", ex.getErrorCode(),
                "unknown action must fail with unknown-operation error, got: " + ex.getErrorCode());
    }

    // ==================== helpers ====================

    private ApiResponse<?> executeRpc(GraphQLOperationType opType, String action, Map<String, Object> data) {
        ApiRequest<Map<String, Object>> request = new ApiRequest<>();
        request.setData(data);
        return graphQLEngine.executeRpc(graphQLEngine.newRpcContext(opType, action, request));
    }

    private Map<String, Object> dataMap(ApiResponse<?> response) {
        assertEquals(0, response.getStatus(), "expected successful response, got: " + response);
        assertNotNull(response.getData(), "response data must not be null");
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getData();
        return data;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listRows(ApiResponse<?> response) {
        return (List<Map<String, Object>>) response.getData();
    }
}

/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.jsonrpc;

import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.util.FutureHelper;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.unittest.BaseTestCase;
import io.nop.graphql.core.engine.GraphQLEngine;
import io.nop.graphql.core.engine.MockGraphQLSchemaLoader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JSON-RPC batch语义：每个entry独立成功或失败（JSON-RPC 2.0规范），
 * notification失败可观测且不拖垮整批，批量上限为包含语义（=max允许），空批返回类型与声明一致。
 */
public class TestJsonRpcService extends BaseTestCase {
    JsonRpcService service;

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @BeforeEach
    public void setUp() {
        GraphQLEngine engine = new GraphQLEngine();
        engine.setSchemaLoader(new MockGraphQLSchemaLoader());
        engine.init();
        service = new JsonRpcService();
        service.setGraphQLEngine(engine);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> parseBatch(ApiResponse<String> response) {
        return (List<Map<String, Object>>) (Object) JsonTool.parseBeanFromText(response.getData(), List.class);
    }

    /**
     * 单个entry参数校验失败（unknownArg触发newRpcContext同步抛NopException）只影响该entry，
     * 合法entry的结果不丢失。修复前整批同步抛出异常，executeAsync直接失败。
     */
    @Test
    public void testBatchEntryFailureIsolated() {
        String body = "[{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"method\":\"MyEntity__get\",\"params\":{\"id\":\"a\"},\"selection\":\"id,name\"},"
                + "{\"jsonrpc\":\"2.0\",\"id\":\"2\",\"method\":\"MyEntity__get\",\"params\":{\"unknownArg\":1}}]";

        ApiResponse<String> response = FutureHelper.syncGet(service.executeAsync(body, null));
        assertEquals(200, response.getHttpStatus());

        List<Map<String, Object>> list = parseBatch(response);
        assertEquals(2, list.size());
        assertNotNull(list.get(0).get("result"), "valid entry must return result");
        assertEquals("a", ((Map<String, Object>) list.get(0).get("result")).get("id"));
        assertNotNull(list.get(1).get("error"), "invalid entry must return error response");
        assertEquals(JsonRpcErrorCodes.INVALID_REQUEST,
                ((Number) ((Map<String, Object>) list.get(1).get("error")).get("code")).intValue());
    }

    /**
     * notification（id==null）执行失败不拖垮整批：修复前notification的同步异常直接从循环中抛出。
     */
    @Test
    public void testNotificationFailureDoesNotKillBatch() {
        String body = "[{\"jsonrpc\":\"2.0\",\"method\":\"MyEntity__get\",\"params\":{\"bad\":1}},"
                + "{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"method\":\"MyEntity__get\",\"params\":{\"id\":\"a\"},\"selection\":\"id,name\"}]";

        ApiResponse<String> response = FutureHelper.syncGet(service.executeAsync(body, null));
        assertEquals(200, response.getHttpStatus());

        List<Map<String, Object>> list = parseBatch(response);
        assertEquals(1, list.size());
        assertNotNull(list.get(0).get("result"));
    }

    /**
     * 批量个数为包含语义：恰好等于上限（默认10）的批量被允许。
     * 修复前判断为 <=，10个请求的批量被拒。
     */
    @Test
    public void testBatchOfExactlyMaxCountAllowed() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < 10; i++) {
            if (i > 0)
                sb.append(',');
            sb.append("{\"jsonrpc\":\"2.0\",\"id\":\"").append(i).append("\",\"method\":\"NotExists__op\"}");
        }
        sb.append(']');

        ApiResponse<String> response = FutureHelper.syncGet(service.executeAsync(sb.toString(), null));
        assertEquals(200, response.getHttpStatus());

        List<Map<String, Object>> list = parseBatch(response);
        assertEquals(10, list.size(), "batch of exactly max count must be accepted");
        for (Map<String, Object> entry : list) {
            assertNotNull(entry.get("error"), "unknown method entries resolve to METHOD_NOT_FOUND per-entry");
        }
    }

    /**
     * 空批的对外响应保持JSON-RPC规范形态：单个Invalid Request错误对象（非数组）。
     */
    @Test
    public void testEmptyBatchWireFormatIsSingleObject() {
        ApiResponse<String> response = FutureHelper.syncGet(service.executeAsync("[]", null));
        assertEquals(200, response.getHttpStatus());

        Object parsed = JsonTool.parseBeanFromText(response.getData(), Object.class);
        assertTrue(parsed instanceof Map, "empty batch response must be a single JSON object");
        Map<String, Object> map = (Map<String, Object>) parsed;
        assertNotNull(map.get("error"));
        assertEquals(JsonRpcErrorCodes.INVALID_REQUEST,
                ((Number) ((Map<String, Object>) map.get("error")).get("code")).intValue());
    }

    /**
     * batchExecuteCommandAsync的返回类型与声明一致：空批返回List（单元素Invalid Request），
     * 不再把单个JsonRpcResponse塞进List泛型（潜伏ClassCastException）。
     */
    @Test
    public void testBatchExecuteEmptyReturnsTypedList() {
        List<JsonRpcResponse<?>> list = FutureHelper.syncGet(
                service.batchExecuteCommandAsync(List.of(), new ServiceContextImpl()));
        assertEquals(1, list.size());
        assertNotNull(list.get(0).getError());
        assertFalse(list.isEmpty());
    }
}

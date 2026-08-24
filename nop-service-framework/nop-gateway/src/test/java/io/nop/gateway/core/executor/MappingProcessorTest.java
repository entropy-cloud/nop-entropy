/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.gateway.core.executor;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.gateway.core.context.GatewayContextImpl;
import io.nop.gateway.core.context.IGatewayContext;
import io.nop.gateway.model.GatewayMessageMappingModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MappingProcessor单元测试
 */
class MappingProcessorTest {

    private MappingProcessor processor;
    private IGatewayContext context;

    @BeforeEach
    void setUp() {
        processor = new MappingProcessor(null);
        context = new GatewayContextImpl();
    }

    @Test
    void testMapRequest_nullMapping_returnsOriginalRequest() {
        ApiRequest<Object> request = new ApiRequest<>();
        request.setData("test-data");
        request.setHeaders(new HashMap<>());
        request.getHeaders().put("X-Test", "value");

        ApiRequest<?> result = processor.mapRequest(null, request, context);

        assertSame(request, result);
    }

    @Test
    void testMapRequest_allowHeaders_filtersHeaders() {
        ApiRequest<Object> request = new ApiRequest<>();
        request.setData("test-data");
        Map<String, Object> headers = new HashMap<>();
        headers.put("X-Allowed", "value1");
        headers.put("X-Blocked", "value2");
        request.setHeaders(headers);

        GatewayMessageMappingModel mapping = new GatewayMessageMappingModel();
        Set<String> allowHeaders = new HashSet<>();
        allowHeaders.add("X-Allowed");
        mapping.setAllowHeaders(allowHeaders);

        ApiRequest<?> result = processor.mapRequest(mapping, request, context);

        assertTrue(result.getHeaders().containsKey("X-Allowed"));
        assertFalse(result.getHeaders().containsKey("X-Blocked"));
    }

    @Test
    void testMapRequest_disallowHeaders_removesHeaders() {
        ApiRequest<Object> request = new ApiRequest<>();
        request.setData("test-data");
        Map<String, Object> headers = new HashMap<>();
        headers.put("X-Keep", "value1");
        headers.put("X-Remove", "value2");
        request.setHeaders(headers);

        GatewayMessageMappingModel mapping = new GatewayMessageMappingModel();
        Set<String> disallowHeaders = new HashSet<>();
        disallowHeaders.add("X-Remove");
        mapping.setDisallowHeaders(disallowHeaders);

        ApiRequest<?> result = processor.mapRequest(mapping, request, context);

        assertTrue(result.getHeaders().containsKey("X-Keep"));
        assertFalse(result.getHeaders().containsKey("X-Remove"));
    }

    @Test
    void testMapResponse_nullMapping_returnsOriginalResponse() {
        ApiResponse<Object> response = new ApiResponse<>();
        response.setData("test-data");
        response.setHeaders(new HashMap<>());
        response.getHeaders().put("X-Test", "value");

        ApiResponse<?> result = processor.mapResponse(null, response, context);

        assertSame(response, result);
    }

    @Test
    void testMapResponse_allowHeaders_filtersHeaders() {
        ApiResponse<Object> response = new ApiResponse<>();
        response.setData("test-data");
        Map<String, Object> headers = new HashMap<>();
        headers.put("X-Allowed", "value1");
        headers.put("X-Blocked", "value2");
        response.setHeaders(headers);

        GatewayMessageMappingModel mapping = new GatewayMessageMappingModel();
        Set<String> allowHeaders = new HashSet<>();
        allowHeaders.add("X-Allowed");
        mapping.setAllowHeaders(allowHeaders);

        ApiResponse<?> result = processor.mapResponse(mapping, response, context);

        assertTrue(result.getHeaders().containsKey("X-Allowed"));
        assertFalse(result.getHeaders().containsKey("X-Blocked"));
    }

    // ======================= 大小写漂移（运行时小写 vs 配置原样大小写） =======================

    @Test
    void testMapRequest_allowHeaders_matchesCaseInsensitively() {
        // 生产链路 header key 已被 HTTP 层小写化，配置里常按习惯写 "X-Custom-Header"：
        // 白名单匹配不得因大小写不同而把头全部清掉
        ApiRequest<Object> request = new ApiRequest<>();
        request.setData("test-data");
        Map<String, Object> headers = new HashMap<>();
        headers.put("x-custom-header", "value1");
        headers.put("x-other-header", "value2");
        request.setHeaders(headers);

        GatewayMessageMappingModel mapping = new GatewayMessageMappingModel();
        mapping.setAllowHeaders(new HashSet<>(java.util.Set.of("X-Custom-Header")));

        ApiRequest<?> result = processor.mapRequest(mapping, request, context);

        assertTrue(result.getHeaders().containsKey("x-custom-header"),
                "配置大小写与运行时小写 key 不同时白名单仍需命中");
        assertFalse(result.getHeaders().containsKey("x-other-header"));
    }

    @Test
    void testMapRequest_disallowHeaders_matchesCaseInsensitively() {
        // 黑名单同理：大小写不一致时该删的头必须被删（含 hop-by-hop 安全头场景）
        ApiRequest<Object> request = new ApiRequest<>();
        request.setData("test-data");
        Map<String, Object> headers = new HashMap<>();
        headers.put("x-keep", "value1");
        headers.put("connection", "keep-alive");
        request.setHeaders(headers);

        GatewayMessageMappingModel mapping = new GatewayMessageMappingModel();
        mapping.setDisallowHeaders(new HashSet<>(java.util.Set.of("Connection")));

        ApiRequest<?> result = processor.mapRequest(mapping, request, context);

        assertFalse(result.getHeaders().containsKey("connection"),
                "配置 'Connection' 必须命中运行时小写 key 'connection'");
        assertTrue(result.getHeaders().containsKey("x-keep"));
    }
}

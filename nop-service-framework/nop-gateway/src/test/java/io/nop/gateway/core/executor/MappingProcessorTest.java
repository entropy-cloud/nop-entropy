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
}

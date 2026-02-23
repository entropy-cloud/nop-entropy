/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.gateway.impl;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.rpc.IRpcServiceInvoker;
import io.nop.api.core.util.FutureHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.core.unittest.BaseTestCase;
import io.nop.gateway.core.context.GatewayContextImpl;
import io.nop.gateway.core.context.IGatewayContext;
import io.nop.gateway.model.GatewayModel;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpClient;
import io.nop.http.api.client.IHttpResponse;
import io.nop.rpc.core.utils.RpcHelper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.*;

class TestGatewayHandler extends BaseTestCase {

    private GatewayHandler handler;
    private GatewayModel model;

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @BeforeEach
    void setUp() {
        model = loadGatewayModel();
        handler = new GatewayHandler(
                model,
                new MockRpcServiceInvoker(),
                new MockHttpClient(),
                null
        );
    }

    private GatewayModel loadGatewayModel() {
        return (GatewayModel) ResourceComponentManager.instance()
                .loadComponentModel("/nop/test/test.gateway.xml");
    }

    @Test
    void testHandle_simpleRoute_returnsResponse() {
        ApiRequest<Object> request = new ApiRequest<>();
        request.setData(Map.of("input", "test"));

        IGatewayContext context = createTestContext("/test/simple", request);

        CompletionStage<ApiResponse<?>> future = handler.handle(request, context);
        assertNotNull(future, "Handler should return a future for matching route");

        ApiResponse<?> response = FutureHelper.syncGet(future);
        assertNotNull(response, "Response should not be null");
        assertEquals(0, response.getStatus(), "Status should be 0 (success)");

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getData();
        assertNotNull(data, "Response data should not be null");
        assertEquals(Boolean.TRUE, data.get("mocked"), "Data should contain mocked=true");
        assertEquals("test-service", data.get("service"), "Data should contain service name");
        assertEquals("simpleMethod", data.get("method"), "Data should contain method name");
    }

    @Test
    void testHandle_noMatchingRoute_returnsNull() {
        ApiRequest<Object> request = new ApiRequest<>();
        request.setData("test");

        IGatewayContext context = createTestContext("/nonexistent/path", request);

        CompletionStage<ApiResponse<?>> future = handler.handle(request, context);
        assertNull(future, "Handler should return null for non-matching route");
    }

    @Test
    void testHandle_httpMethodMismatch_returnsNull() {
        ApiRequest<Object> request = new ApiRequest<>();
        request.setData("test");
        RpcHelper.setHttpMethod(request, "GET");

        IGatewayContext context = createTestContext("/test/post-only", request);

        CompletionStage<ApiResponse<?>> future = handler.handle(request, context);
        assertNull(future, "Handler should not match route for wrong HTTP method");
    }

    @Test
    void testHandle_httpMethodMatch_returnsResponse() {
        ApiRequest<Object> request = new ApiRequest<>();
        request.setData(Map.of("key", "value"));
        RpcHelper.setHttpMethod(request, "POST");

        IGatewayContext context = createTestContext("/test/post-only", request);

        CompletionStage<ApiResponse<?>> future = handler.handle(request, context);
        assertNotNull(future, "Handler should match route for correct HTTP method");

        ApiResponse<?> response = FutureHelper.syncGet(future);
        assertNotNull(response, "Response should not be null");
        assertEquals(0, response.getStatus(), "Status should be 0 (success)");

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getData();
        assertNotNull(data, "Response data should not be null");
        assertEquals(Boolean.TRUE, data.get("mocked"), "Data should contain mocked=true");
        assertEquals("test-service", data.get("service"), "Data should contain service name");
        assertEquals("postMethod", data.get("method"), "Data should contain method name");
    }

    @Test
    void testHandle_getMethodMatch_returnsResponse() {
        ApiRequest<Object> request = new ApiRequest<>();
        request.setData("test");
        RpcHelper.setHttpMethod(request, "GET");

        IGatewayContext context = createTestContext("/test/get-only", request);

        CompletionStage<ApiResponse<?>> future = handler.handle(request, context);
        assertNotNull(future, "Handler should match route for GET method");

        ApiResponse<?> response = FutureHelper.syncGet(future);
        assertNotNull(response, "Response should not be null");
        assertEquals(0, response.getStatus(), "Status should be 0 (success)");

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getData();
        assertNotNull(data, "Response data should not be null");
        assertEquals(Boolean.TRUE, data.get("mocked"), "Data should contain mocked=true");
        assertEquals("test-service", data.get("service"), "Data should contain service name");
        assertEquals("getMethod", data.get("method"), "Data should contain method name");
    }

    @Test
    void testGetModel_returnsConfiguredModel() {
        GatewayModel returnedModel = handler.getModel();
        assertSame(model, returnedModel, "getModel should return the same model instance");
    }

    private IGatewayContext createTestContext(String path, ApiRequest<?> request) {
        GatewayContextImpl context = new GatewayContextImpl();
        context.setRequestPath(path);
        context.setRequest(request);
        return context;
    }

    static class MockRpcServiceInvoker implements IRpcServiceInvoker {
        @Override
        public CompletionStage<ApiResponse<?>> invokeAsync(String serviceName, String serviceMethod,
                                                           ApiRequest<?> request,
                                                           io.nop.api.core.util.ICancelToken cancelToken) {
            ApiResponse<Object> response = new ApiResponse<>();
            response.setStatus(0);
            response.setData(Map.of("mocked", true, "service", serviceName, "method", serviceMethod));
            return FutureHelper.toCompletionStage(response);
        }
    }

    static class MockHttpClient implements IHttpClient {
        @Override
        public CompletionStage<IHttpResponse> fetchAsync(HttpRequest request,
                                                         io.nop.api.core.util.ICancelToken cancelToken) {
            return FutureHelper.toCompletionStage(new MockHttpResponse());
        }

        @Override
        public CompletionStage<IHttpResponse> downloadAsync(HttpRequest request,
                                                            io.nop.http.api.client.IHttpOutputFile targetFile,
                                                            io.nop.http.api.client.DownloadOptions options,
                                                            io.nop.api.core.util.ICancelToken cancelToken) {
            return FutureHelper.toCompletionStage(new MockHttpResponse());
        }

        @Override
        public CompletionStage<IHttpResponse> uploadAsync(HttpRequest request,
                                                          io.nop.http.api.client.IHttpInputFile inputFile,
                                                          io.nop.http.api.client.UploadOptions options,
                                                          io.nop.api.core.util.ICancelToken cancelToken) {
            return FutureHelper.toCompletionStage(new MockHttpResponse());
        }
    }

    static class MockHttpResponse implements IHttpResponse {
        @Override
        public int getHttpStatus() {
            return 200;
        }

        @Override
        public String getContentType() {
            return "application/json";
        }

        @Override
        public String getCharset() {
            return StandardCharsets.UTF_8.name();
        }

        @Override
        public Map<String, String> getHeaders() {
            return new HashMap<>();
        }

        @Override
        public byte[] getBodyAsBytes() {
            return "{\"mocked\":true}".getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public String getBodyAsString() {
            return "{\"mocked\":true}";
        }

        @Override
        public Object getBody() {
            return Map.of("mocked", true);
        }

        @Override
        public <T> T getBodyAsBean(Class<T> beanClass) {
            return null;
        }
    }
}

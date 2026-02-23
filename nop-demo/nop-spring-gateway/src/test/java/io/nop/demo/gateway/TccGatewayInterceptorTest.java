/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.demo.gateway;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.util.ApiHeaders;
import io.nop.gateway.core.context.IGatewayContext;
import io.nop.gateway.core.interceptor.IGatewayInvocation;
import io.nop.tcc.api.ITccEngine;
import io.nop.tcc.api.ITccTransaction;
import io.nop.tcc.integration.gateway.TccGatewayInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * TccGatewayInterceptor单元测试
 * 测试新的invoke方法模式
 */
@ExtendWith(MockitoExtension.class)
class TccGatewayInterceptorTest {

    @Mock
    private ITccEngine tccEngine;

    @Mock
    private ITccTransaction tccTransaction;

    @Mock
    private IGatewayContext gatewayContext;

    @Mock
    private IGatewayInvocation invocation;

    private TccGatewayInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new TccGatewayInterceptor();
        interceptor.setTccEngine(tccEngine);
        interceptor.setDefaultTxnGroup("test-group");
        interceptor.setAutoCreateTransaction(true);
    }

    @Test
    void testInvoke_WithExistingTxnId_ShouldParticipateInTransaction() {
        // 准备
        ApiRequest<Object> request = new ApiRequest<>();
        request.setHeaders(new HashMap<>());
        ApiHeaders.setTxnId(request, "existing-txn-id");
        ApiHeaders.setTxnGroup(request, "test-group");

        ApiResponse<Object> expectedResponse = new ApiResponse<>();
        expectedResponse.setStatus(0);

        when(gatewayContext.getRequestPath()).thenReturn("/api/tx/test");
        when(tccEngine.runInTransactionAsync(anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    java.util.function.Function<ITccTransaction, CompletionStage<ApiResponse<?>>> task = 
                        invocation.getArgument(2);
                    return task.apply(tccTransaction);
                });
        doReturn(CompletableFuture.completedFuture(expectedResponse))
                .when(invocation).proceedInvoke(any(), any());

        // 执行
        CompletionStage<ApiResponse<?>> resultFuture = interceptor.invoke(invocation, request, gatewayContext);
        ApiResponse<?> result = resultFuture.toCompletableFuture().join();

        // 验证
        assertNotNull(result);
        assertEquals(0, result.getStatus());
        verify(tccEngine).runInTransactionAsync(eq("test-group"), eq("existing-txn-id"), any());
    }

    @Test
    void testInvoke_WithoutTxnId_ShouldCreateNewTransaction() {
        // 准备
        ApiRequest<Object> request = new ApiRequest<>();
        request.setHeaders(new HashMap<>());

        ApiResponse<Object> expectedResponse = new ApiResponse<>();
        expectedResponse.setStatus(0);

        when(gatewayContext.getRequestPath()).thenReturn("/api/tx/test");
        when(tccTransaction.getTxnId()).thenReturn("new-txn-id");
        when(tccEngine.runInTransactionAsync(anyString(), any()))
                .thenAnswer(invocation -> {
                    java.util.function.Function<ITccTransaction, CompletionStage<ApiResponse<?>>> task = 
                        invocation.getArgument(1);
                    return task.apply(tccTransaction);
                });
        doReturn(CompletableFuture.completedFuture(expectedResponse))
                .when(invocation).proceedInvoke(any(), any());

        // 执行
        CompletionStage<ApiResponse<?>> resultFuture = interceptor.invoke(invocation, request, gatewayContext);
        ApiResponse<?> result = resultFuture.toCompletableFuture().join();

        // 验证
        assertNotNull(result);
        assertEquals(0, result.getStatus());
        verify(tccEngine).runInTransactionAsync(eq("test-group"), any());
        // 验证事务ID已设置到请求头
        assertEquals("new-txn-id", ApiHeaders.getTxnId(request));
    }

    @Test
    void testInvoke_AutoCreateDisabled_ShouldNotCreateTransaction() {
        // 准备
        interceptor.setAutoCreateTransaction(false);
        ApiRequest<Object> request = new ApiRequest<>();
        request.setHeaders(new HashMap<>());

        ApiResponse<Object> expectedResponse = new ApiResponse<>();
        expectedResponse.setStatus(0);

        when(gatewayContext.getRequestPath()).thenReturn("/api/tx/test");
        doReturn(CompletableFuture.completedFuture(expectedResponse))
                .when(invocation).proceedInvoke(any(), any());

        // 执行
        CompletionStage<ApiResponse<?>> resultFuture = interceptor.invoke(invocation, request, gatewayContext);
        ApiResponse<?> result = resultFuture.toCompletableFuture().join();

        // 验证
        assertNotNull(result);
        assertEquals(0, result.getStatus());
        verify(tccEngine, never()).runInTransactionAsync(anyString(), any());
        verify(invocation).proceedInvoke(request, gatewayContext);
    }

    @Test
    void testInvoke_WithException_ShouldPropagate() {
        // 准备
        ApiRequest<Object> request = new ApiRequest<>();
        request.setHeaders(new HashMap<>());

        when(gatewayContext.getRequestPath()).thenReturn("/api/tx/test");
        when(tccEngine.runInTransactionAsync(anyString(), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Test error")));

        // 执行 & 验证
        CompletionStage<ApiResponse<?>> resultFuture = interceptor.invoke(invocation, request, gatewayContext);
        assertThrows(RuntimeException.class, () -> {
            resultFuture.toCompletableFuture().join();
        });
    }
}

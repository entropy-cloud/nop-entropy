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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * TccGatewayInterceptor单元测试
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
        ApiRequest<Object> request = new ApiRequest<>();
        request.setHeaders(new HashMap<>());
        ApiHeaders.setTxnId(request, "existing-txn-id");
        ApiHeaders.setTxnGroup(request, "test-group");

        ApiResponse<Object> expectedResponse = new ApiResponse<>();
        expectedResponse.setStatus(0);

        when(tccEngine.runInTransactionAsync(eq("test-group"), eq("existing-txn-id"), any()))
                .thenAnswer(inv -> {
                    java.util.function.Function<ITccTransaction, CompletionStage<ApiResponse<?>>> task = inv.getArgument(2);
                    return task.apply(tccTransaction);
                });
        doReturn(CompletableFuture.completedFuture(expectedResponse)).when(invocation).proceedInvoke(any(), any());

        CompletionStage<ApiResponse<?>> resultFuture = interceptor.invoke(invocation, request, gatewayContext);
        ApiResponse<?> result = resultFuture.toCompletableFuture().join();

        assertNotNull(result);
        assertEquals(0, result.getStatus());
        verify(tccEngine).runInTransactionAsync(eq("test-group"), eq("existing-txn-id"), any());
    }

    @Test
    void testInvoke_WithoutTxnId_ShouldCreateNewTransaction() {
        ApiRequest<Object> request = new ApiRequest<>();
        request.setHeaders(new HashMap<>());

        ApiResponse<Object> expectedResponse = new ApiResponse<>();
        expectedResponse.setStatus(0);

        when(tccEngine.runInTransactionAsync(eq("test-group"), eq(null), any()))
                .thenAnswer(inv -> {
                    java.util.function.Function<ITccTransaction, CompletionStage<ApiResponse<?>>> task = inv.getArgument(2);
                    return task.apply(tccTransaction);
                });
        doReturn(CompletableFuture.completedFuture(expectedResponse)).when(invocation).proceedInvoke(any(), any());

        CompletionStage<ApiResponse<?>> resultFuture = interceptor.invoke(invocation, request, gatewayContext);
        ApiResponse<?> result = resultFuture.toCompletableFuture().join();

        assertNotNull(result);
        assertEquals(0, result.getStatus());
        verify(tccEngine).runInTransactionAsync(eq("test-group"), eq(null), any());
    }

    @Test
    void testInvoke_AutoCreateDisabled_ShouldNotCreateTransaction() {
        interceptor.setAutoCreateTransaction(false);
        ApiRequest<Object> request = new ApiRequest<>();
        request.setHeaders(new HashMap<>());

        ApiResponse<Object> expectedResponse = new ApiResponse<>();
        expectedResponse.setStatus(0);

        when(tccEngine.runInTransactionAsync(eq("test-group"), eq(null), any()))
                .thenAnswer(inv -> {
                    java.util.function.Function<ITccTransaction, CompletionStage<ApiResponse<?>>> task = inv.getArgument(2);
                    return task.apply(tccTransaction);
                });
        doReturn(CompletableFuture.completedFuture(expectedResponse)).when(invocation).proceedInvoke(any(), any());

        CompletionStage<ApiResponse<?>> resultFuture = interceptor.invoke(invocation, request, gatewayContext);
        ApiResponse<?> result = resultFuture.toCompletableFuture().join();

        assertNotNull(result);
        assertEquals(0, result.getStatus());
        verify(tccEngine).runInTransactionAsync(eq("test-group"), eq(null), any());
        verify(invocation).proceedInvoke(request, gatewayContext);
    }

    @Test
    void testInvoke_WithException_ShouldPropagate() {
        ApiRequest<Object> request = new ApiRequest<>();
        request.setHeaders(new HashMap<>());

        when(tccEngine.runInTransactionAsync(eq("test-group"), eq(null), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Test error")));

        CompletionStage<ApiResponse<?>> resultFuture = interceptor.invoke(invocation, request, gatewayContext);
        assertThrows(RuntimeException.class, () -> {
            resultFuture.toCompletableFuture().join();
        });
    }
}

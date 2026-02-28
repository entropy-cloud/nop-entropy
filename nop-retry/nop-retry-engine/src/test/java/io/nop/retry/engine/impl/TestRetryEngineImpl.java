/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.retry.engine.impl;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.ErrorBean;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.retry.api.IRetryTask;
import io.nop.retry.dao.entity.NopRetryDeadLetter;
import io.nop.retry.dao.entity.NopRetryPolicy;
import io.nop.retry.dao.entity.NopRetryRecord;
import io.nop.retry.engine.store.IRetryRecordStore;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static io.nop.retry.dao._NopRetryDaoConstants.*;
import static io.nop.retry.api.NopRetryApiConstants.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * RetryEngineImpl 核心业务逻辑单元测试
 */
@NopTestConfig(
        localDb = true,
        initDatabaseSchema = OptionalBoolean.TRUE
)
public class TestRetryEngineImpl extends JunitAutoTestCase {

    @Inject
    RetryEngineImpl retryEngine;

    @Inject
    IRetryRecordStore recordStore;

    @Inject
    MockRpcServiceInvoker rpcInvoker;

    @BeforeEach
    void setUp() {
        rpcInvoker.reset();
    }

    // ==================== newRetryTask Tests ====================

    @Test
    void testNewRetryTask_shouldCreateTaskWithCorrectServiceInfo() {
        IRetryTask task = retryEngine.newRetryTask("test-service", "test-method");

        assertEquals("test-service", task.getServiceName());
        assertEquals("test-method", task.getServiceMethod());
    }

    @Test
    void testNewRetryTask_shouldSupportFluentConfiguration() {
        IRetryTask task = retryEngine.newRetryTask("svc", "method")
                .withPolicyId("policy-1")
                .withIdempotentId("idem-123")
                .withExecutorId("exec-1")
                .withCallback("callback-svc", "callback-method");

        assertEquals("policy-1", task.getPolicyId());
        assertEquals("idem-123", task.getIdempotentId());
        assertEquals("exec-1", task.getExecutorId());
        assertEquals("callback-svc", task.getCallbackService());
        assertEquals("callback-method", task.getCallbackMethod());
    }

    // ==================== pause/resume Tests ====================

    @Test
    void testPause_shouldSucceedForPendingRecord() {
        NopRetryRecord record = createTestRecord("record-1", RETRY_RECORD_STATUS_PENDING);
        recordStore.saveRecord(record);

        retryEngine.pause("record-1");

        assertEquals(RETRY_RECORD_STATUS_SUSPENDED, recordStore.loadRecord("record-1").getStatus());
    }

    @Test
    void testResume_shouldSucceedForSuspendedRecord() {
        NopRetryRecord record = createTestRecord("record-1", RETRY_RECORD_STATUS_SUSPENDED);
        recordStore.saveRecord(record);

        retryEngine.resume("record-1");

        NopRetryRecord resumed = recordStore.loadRecord("record-1");
        assertEquals(RETRY_RECORD_STATUS_PENDING, resumed.getStatus());
        assertNotNull(resumed.getNextTriggerTime());
    }

    // ==================== retryFromDeadLetter Tests ====================

    @Test
    void testRetryFromDeadLetter_shouldInvokeRpcWhenValid() throws Exception {
        NopRetryDeadLetter deadLetter = createTestDeadLetter("dl-1");
        deadLetter.setServiceName("test-svc");
        deadLetter.setServiceMethod("test-method");
        deadLetter.setRequestPayload("{\"data\":\"test\"}");
        recordStore.saveDeadLetter(deadLetter);

        rpcInvoker.setResponse(ApiResponse.success("result"));

        CompletableFuture<ApiResponse<?>> future = retryEngine.retryFromDeadLetter("dl-1", null)
                .toCompletableFuture();

        assertTrue(future.isDone());
        ApiResponse<?> response = future.get();
        assertTrue(response.isOk());
        assertEquals("result", response.getData());

        assertEquals("test-svc", rpcInvoker.getLastServiceName());
        assertEquals("test-method", rpcInvoker.getLastServiceMethod());
    }

    // ==================== executeTask Tests ====================

    @Test
    void testExecuteTask_shouldSucceedOnFirstTry() throws Exception {
        NopRetryPolicy policy = createTestPolicy("policy-1");
        recordStore.savePolicy(policy);

        rpcInvoker.setResponse(ApiResponse.success("success-result"));

        IRetryTask task = retryEngine.newRetryTask("svc", "method")
                .withPolicyId("policy-1")
                .withIdempotentId("idem-1");

        ApiRequest<Object> request = new ApiRequest<>();
        request.setData(Map.of("key", "value"));

        CompletableFuture<ApiResponse<?>> future = retryEngine.executeTask(task, request, null)
                .toCompletableFuture();

        ApiResponse<?> response = future.get();
        assertTrue(response.isOk());
        assertEquals("success-result", response.getData());

        // 成功后记录不应再处于 pending 状态
        NopRetryRecord record = recordStore.findPendingRecordByIdempotentId("idem-1");
        assertNull(record);
    }

    @Test
    void testExecuteTask_shouldHandleBlockStrategyDiscard() throws Exception {
        NopRetryRecord existingRecord = createTestRecord("existing-1", RETRY_RECORD_STATUS_PENDING);
        existingRecord.setIdempotentId("idem-same");
        recordStore.saveRecord(existingRecord);

        NopRetryPolicy policy = createTestPolicy("policy-1");
        policy.setBlockStrategy(BLOCK_STRATEGY_DISCARD);
        recordStore.savePolicy(policy);

        IRetryTask task = retryEngine.newRetryTask("svc", "method")
                .withPolicyId("policy-1")
                .withIdempotentId("idem-same");

        ApiRequest<Object> request = new ApiRequest<>();

        CompletableFuture<ApiResponse<?>> future = retryEngine.executeTask(task, request, null)
                .toCompletableFuture();

        // DISCARD 策略应直接返回成功，不调用 RPC
        ApiResponse<?> response = future.get();
        assertTrue(response.isOk());
        assertNull(response.getData());
        assertEquals(0, rpcInvoker.getInvocationCount());
    }

    // ==================== Immediate Retry Tests ====================

    /**
     * 测试立即重试机制：第一次失败，第二次成功
     * 验证重试次数和最终状态
     */
    @Test
    void testExecuteTask_shouldRetryImmediatelyAndSucceed() throws Exception {
        // 设置策略：允许 3 次立即重试，间隔 10ms
        NopRetryPolicy policy = createTestPolicy("policy-immediate-retry");
        policy.setMaxRetryCount(5);
        policy.setImmediateRetryCount(3);  // 允许 3 次立即重试
        policy.setImmediateRetryIntervalMs(10L);  // 10ms 间隔
        recordStore.savePolicy(policy);

        // Mock: 第一次失败，第二次成功
        rpcInvoker.setResponses(
                createErrorResponse("temporary-error"),  // 第 1 次：失败
                ApiResponse.success("success-after-retry") // 第 2 次：成功
        );

        IRetryTask task = retryEngine.newRetryTask("svc", "method")
                .withPolicyId("policy-immediate-retry")
                .withIdempotentId("idem-immediate-retry");

        ApiRequest<Object> request = new ApiRequest<>();
        request.setData(Map.of("key", "value"));

        // 执行任务
        CompletableFuture<ApiResponse<?>> future = retryEngine.executeTask(task, request, null)
                .toCompletableFuture();

        // 等待完成
        ApiResponse<?> response = future.get();

        // 验证：RPC 被调用了 2 次（第一次失败 + 第二次成功）
        assertEquals(2, rpcInvoker.getInvocationCount());

        // 验证：最终响应是成功的
        assertTrue(response.isOk());
        assertEquals("success-after-retry", response.getData());

        // 验证：记录不在 pending 状态（已成功完成）
        NopRetryRecord pendingRecord = recordStore.findPendingRecordByIdempotentId("idem-immediate-retry");
        assertNull(pendingRecord);
    }

    /**
     * 测试立即重试：所有立即重试次数用完后，记录进入延迟重试状态
     */
    @Test
    void testExecuteTask_shouldEnterDelayedRetryAfterImmediateRetriesExhausted() throws Exception {
        // 设置策略：2 次立即重试，最大 5 次
        NopRetryPolicy policy = createTestPolicy("policy-delayed-retry");
        policy.setMaxRetryCount(5);
        policy.setImmediateRetryCount(2);  // 只允许 2 次立即重试
        policy.setImmediateRetryIntervalMs(10L);
        recordStore.savePolicy(policy);

        // Mock: 前两次都失败（用完立即重试次数）
        rpcInvoker.setResponses(
                createErrorResponse("error-1"),
                createErrorResponse("error-2")
        );

        IRetryTask task = retryEngine.newRetryTask("svc", "method")
                .withPolicyId("policy-delayed-retry")
                .withIdempotentId("idem-delayed-retry");

        ApiRequest<Object> request = new ApiRequest<>();
        request.setData(Map.of("key", "value"));

        // 执行任务
        CompletableFuture<ApiResponse<?>> future = retryEngine.executeTask(task, request, null)
                .toCompletableFuture();

        // 等待完成（立即重试阶段结束）
        ApiResponse<?> response = future.get();

        // 验证：RPC 被调用了 2 次（用完立即重试次数）
        assertEquals(2, rpcInvoker.getInvocationCount());

        // 验证：响应是失败的
        assertFalse(response.isOk());

        // 验证：记录仍在 pending 状态，等待 scanner 触发延迟重试
        NopRetryRecord pendingRecord = recordStore.findPendingRecordByIdempotentId("idem-delayed-retry");
        assertNotNull(pendingRecord);
        assertEquals(2, pendingRecord.getRetryCount());  // 两次立即重试失败
        assertNotNull(pendingRecord.getNextTriggerTime());  // 已设置下次触发时间
    }

    // ==================== Helper Methods ====================

    private NopRetryRecord createTestRecord(String sid, int status) {
        NopRetryRecord record = new NopRetryRecord();
        record.setSid(sid);
        record.setNamespaceId("default");
        record.setGroupId("default");
        record.setStatus(status);
        record.setIdempotentId("idem-" + sid);
        record.setServiceName("test-service");
        record.setServiceMethod("test-method");
        record.setPolicyId("default-policy");
        record.setCreateTime(new Timestamp(System.currentTimeMillis()));
        return record;
    }

    private NopRetryPolicy createTestPolicy(String sid) {
        NopRetryPolicy policy = new NopRetryPolicy();
        policy.setSid(sid);
        policy.setName("Test Policy " + sid);
        policy.setNamespaceId("default");
        policy.setGroupId("default");
        policy.setMaxRetryCount(DEFAULT_MAX_RETRY_COUNT);
        policy.setInitialIntervalMs(DEFAULT_INITIAL_INTERVAL_MS);
        policy.setMaxIntervalMs(DEFAULT_MAX_INTERVAL_MS);
        policy.setJitterRatio(DEFAULT_JITTER_RATIO);
        policy.setDeadlineTimeoutMs(DEFAULT_DEADLINE_TIMEOUT_MS);
        policy.setBackoffStrategy(BACKOFF_STRATEGY_EXPONENTIAL_BACKOFF);
        policy.setBlockStrategy(BLOCK_STRATEGY_PARALLEL);
        policy.setImmediateRetryCount(DEFAULT_IMMEDIATE_RETRY_COUNT);
        return policy;
    }

    private ApiResponse<?> createErrorResponse(String message) {
        ErrorBean error = new ErrorBean();
        error.setErrorCode("TEST_ERROR");
        error.setDescription(message);
        return ApiResponse.error(error);
    }

    private NopRetryDeadLetter createTestDeadLetter(String sid) {
        NopRetryDeadLetter deadLetter = new NopRetryDeadLetter();
        deadLetter.setSid(sid);
        deadLetter.setNamespaceId("default");
        deadLetter.setGroupId("default");
        deadLetter.setRecordId("test-record-" + sid);
        deadLetter.setIdempotentId("idem-" + sid);
        return deadLetter;
    }
}

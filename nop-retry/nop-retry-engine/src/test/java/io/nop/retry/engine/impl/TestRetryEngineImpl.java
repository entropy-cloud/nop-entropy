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
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.retry.api.IRetryTask;
import io.nop.retry.dao.entity.NopRetryAttempt;
import io.nop.retry.dao.entity.NopRetryDeadLetter;
import io.nop.retry.dao.entity.NopRetryPolicy;
import io.nop.retry.dao.entity.NopRetryRecord;
import io.nop.retry.engine.store.IRetryRecordStore;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static io.nop.retry.dao._NopRetryDaoConstants.*;
import static io.nop.retry.api.NopRetryApiConstants.*;
import static io.nop.retry.dao.entity._gen._NopRetryAttempt.*;
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

    @Inject
    IDaoProvider daoProvider;

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
                .withExecutorName("exec-1");

        assertEquals("policy-1", task.getPolicyId());
        assertEquals("idem-123", task.getIdempotentId());
        assertEquals("exec-1", task.getExecutorName());
    }

    // ==================== pause/resume Tests ====================

    @Test
    void testPause_shouldSucceedForPendingRecord() {
        NopRetryRecord record = createTestRecord("pause-record", RETRY_RECORD_STATUS_PENDING);
        recordStore.saveRecord(record);

        retryEngine.pause("pause-record");

        assertEquals(RETRY_RECORD_STATUS_SUSPENDED, recordStore.loadRecord("pause-record").getStatus());
    }

    @Test
    void testResume_shouldSucceedForSuspendedRecord() {
        NopRetryRecord record = createTestRecord("resume-record", RETRY_RECORD_STATUS_SUSPENDED);
        recordStore.saveRecord(record);

        retryEngine.resume("resume-record");

        NopRetryRecord resumed = recordStore.loadRecord("resume-record");
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

    // ==================== Attempt Persistence Tests ====================

    @Test
    void testExecuteTask_shouldPersistAttemptsForEachExecution() throws Exception {
        NopRetryPolicy policy = createTestPolicy("policy-attempts");
        policy.setMaxRetryCount(5);
        policy.setImmediateRetryCount(2);
        policy.setImmediateRetryIntervalMs(10L);
        recordStore.savePolicy(policy);

        rpcInvoker.setResponses(
                createErrorResponse("temporary-error"),
                ApiResponse.success("success-after-retry")
        );

        IRetryTask task = retryEngine.newRetryTask("svc", "method")
                .withPolicyId("policy-attempts")
                .withIdempotentId("idem-attempts");

        ApiRequest<Object> request = new ApiRequest<>();
        CompletableFuture<ApiResponse<?>> future = retryEngine.executeTask(task, request, null)
                .toCompletableFuture();
        ApiResponse<?> response = future.get();

        assertTrue(response.isOk());
        assertEquals(2, rpcInvoker.getInvocationCount());

        NopRetryRecord record = findRecordByIdempotentId("idem-attempts");
        assertNotNull(record);

        List<NopRetryAttempt> attempts = findAttempts(record.getSid());
        assertEquals(2, attempts.size());

        // attemptNo 递增：1, 2
        assertEquals(1, attempts.get(0).getAttemptNo());
        assertEquals(RETRY_ATTEMPT_STATUS_FAILED, attempts.get(0).getStatus());
        assertEquals("TEST_ERROR", attempts.get(0).getErrorCode());
        assertNotNull(attempts.get(0).getRequestPayloadSnapshot());

        assertEquals(2, attempts.get(1).getAttemptNo());
        assertEquals(RETRY_ATTEMPT_STATUS_SUCCESS, attempts.get(1).getStatus());

        // 时间与耗时断言
        for (NopRetryAttempt attempt : attempts) {
            assertNotNull(attempt.getStartTime());
            assertNotNull(attempt.getEndTime());
            assertNotNull(attempt.getDurationMs());
            assertTrue(attempt.getDurationMs() >= 0);
        }
    }

    @Test
    void testExecuteTask_shouldRecordFailedAttemptForDelayedRetryPath() throws Exception {
        NopRetryPolicy policy = createTestPolicy("policy-delayed-attempt");
        policy.setMaxRetryCount(3);
        policy.setImmediateRetryCount(0);
        policy.setJitterRatio(0d);
        recordStore.savePolicy(policy);

        rpcInvoker.setResponses(createErrorResponse("error-1"));

        IRetryTask task = retryEngine.newRetryTask("svc", "method")
                .withPolicyId("policy-delayed-attempt")
                .withIdempotentId("idem-delayed-attempt");

        ApiRequest<Object> request = new ApiRequest<>();
        CompletableFuture<ApiResponse<?>> future = retryEngine.executeTask(task, request, null)
                .toCompletableFuture();
        future.get();

        NopRetryRecord record = findRecordByIdempotentId("idem-delayed-attempt");
        assertNotNull(record);
        assertEquals(RETRY_RECORD_STATUS_PENDING, record.getStatus());

        List<NopRetryAttempt> attempts = findAttempts(record.getSid());
        assertEquals(1, attempts.size());
        assertEquals(RETRY_ATTEMPT_STATUS_FAILED, attempts.get(0).getStatus());
    }

    // ==================== Partition Index Tests ====================

    @Test
    void testExecuteTask_shouldAssignDeterministicPartitionIndex() throws Exception {
        NopRetryPolicy policy = createTestPolicy("policy-partition");
        recordStore.savePolicy(policy);

        rpcInvoker.setResponse(ApiResponse.success("ok"));

        IRetryTask task1 = retryEngine.newRetryTask("svc", "method")
                .withPolicyId("policy-partition")
                .withIdempotentId("idem-partition-1");
        IRetryTask task2 = retryEngine.newRetryTask("svc", "method")
                .withPolicyId("policy-partition")
                .withIdempotentId("idem-partition-2");
        IRetryTask taskSame = retryEngine.newRetryTask("svc", "method")
                .withPolicyId("policy-partition")
                .withIdempotentId("idem-partition-1");

        ApiRequest<Object> request = new ApiRequest<>();
        retryEngine.executeTask(task1, request, null).toCompletableFuture().get();
        retryEngine.executeTask(task2, request, null).toCompletableFuture().get();

        NopRetryRecord record1 = findRecordByIdempotentId("idem-partition-1");
        NopRetryRecord record2 = findRecordByIdempotentId("idem-partition-2");

        assertNotNull(record1);
        assertNotNull(record2);
        assertNotNull(record1.getPartitionIndex());
        assertNotNull(record2.getPartitionIndex());

        // 分区在有效范围内
        assertTrue(record1.getPartitionIndex() >= 0 && record1.getPartitionIndex() < DEFAULT_PARTITION_COUNT);
        assertTrue(record2.getPartitionIndex() >= 0 && record2.getPartitionIndex() < DEFAULT_PARTITION_COUNT);

        // 同一幂等键落到同一分区（taskSame 未实际执行，验证计算一致性）
        IRetryTask taskNew = retryEngine.newRetryTask("svc", "method")
                .withPolicyId("policy-partition")
                .withIdempotentId("idem-partition-new");
        NopRetryRecord newRecord = recordStore.newRecord(taskNew, request);
        NopRetryRecord sameKeyRecord = recordStore.newRecord(task1, request);
        assertEquals(newRecord.getPartitionIndex(), record1.getPartitionIndex());
        assertEquals(sameKeyRecord.getPartitionIndex(), record1.getPartitionIndex());
    }

    // ==================== Dead Letter & Idempotent Reuse Tests ====================

    @Test
    void testExecuteTask_shouldMoveToDeadLetterWhenMaxRetryExhausted() throws Exception {
        NopRetryPolicy policy = createTestPolicy("policy-exhaust");
        policy.setMaxRetryCount(1);
        policy.setImmediateRetryCount(0);
        recordStore.savePolicy(policy);

        rpcInvoker.setResponses(createErrorResponse("final-error"));

        IRetryTask task = retryEngine.newRetryTask("svc", "method")
                .withPolicyId("policy-exhaust")
                .withIdempotentId("idem-exhaust");

        ApiRequest<Object> request = new ApiRequest<>();
        CompletableFuture<ApiResponse<?>> future = retryEngine.executeTask(task, request, null)
                .toCompletableFuture();

        ApiResponse<?> response = future.get();
        assertFalse(response.isOk());

        // 原 record 行已被删除（死信后幂等键可复用）
        assertNull(findRecordByIdempotentId("idem-exhaust"));

        // 死信行存在，含全量快照
        List<NopRetryDeadLetter> deadLetters = findDeadLetters("idem-exhaust");
        assertEquals(1, deadLetters.size());
        assertEquals("svc", deadLetters.get(0).getServiceName());
        assertEquals("method", deadLetters.get(0).getServiceMethod());
        assertNotNull(deadLetters.get(0).getRequestPayload());
        assertEquals("TEST_ERROR", deadLetters.get(0).getFailureCode());
        assertEquals("final-error", deadLetters.get(0).getFailureMessage());
    }

    @Test
    void testExecuteTask_shouldMoveToDeadLetterDirectlyOnBizFatal() throws Exception {
        NopRetryPolicy policy = createTestPolicy("policy-bizfatal");
        policy.setMaxRetryCount(5);
        policy.setImmediateRetryCount(3);
        recordStore.savePolicy(policy);

        // bizFatal 错误不重试，直接进死信
        ErrorBean error = new ErrorBean();
        error.setErrorCode("BIZ_FATAL_ERROR");
        error.setDescription("fatal business error");
        error.setBizFatal(true);
        rpcInvoker.setResponse(ApiResponse.error(error));

        IRetryTask task = retryEngine.newRetryTask("svc", "method")
                .withPolicyId("policy-bizfatal")
                .withIdempotentId("idem-bizfatal");

        ApiRequest<Object> request = new ApiRequest<>();
        CompletableFuture<ApiResponse<?>> future = retryEngine.executeTask(task, request, null)
                .toCompletableFuture();
        future.get();

        // 只调用一次（不重试）
        assertEquals(1, rpcInvoker.getInvocationCount());
        assertNull(findRecordByIdempotentId("idem-bizfatal"));

        List<NopRetryDeadLetter> deadLetters = findDeadLetters("idem-bizfatal");
        assertEquals(1, deadLetters.size());
        assertEquals("BIZ_FATAL_ERROR", deadLetters.get(0).getFailureCode());
    }

    @Test
    void testDeadLetter_shouldReuseIdempotentKeyAndAllowSecondCycle() throws Exception {
        NopRetryPolicy policy = createTestPolicy("policy-reuse");
        policy.setMaxRetryCount(1);
        policy.setImmediateRetryCount(0);
        recordStore.savePolicy(policy);

        ApiRequest<Object> request = new ApiRequest<>();

        // 第一个周期：失败 → 死信
        rpcInvoker.setResponses(createErrorResponse("cycle-1-error"));
        IRetryTask task1 = retryEngine.newRetryTask("svc", "method")
                .withPolicyId("policy-reuse")
                .withIdempotentId("idem-reuse");
        retryEngine.executeTask(task1, request, null).toCompletableFuture().get();

        // 同一幂等键重新提交：第二个周期 → 第二条死信，无唯一约束异常
        rpcInvoker.setResponses(createErrorResponse("cycle-2-error"));
        IRetryTask task2 = retryEngine.newRetryTask("svc", "method")
                .withPolicyId("policy-reuse")
                .withIdempotentId("idem-reuse");
        retryEngine.executeTask(task2, request, null).toCompletableFuture().get();

        List<NopRetryDeadLetter> deadLetters = findDeadLetters("idem-reuse");
        assertEquals(2, deadLetters.size());
        assertEquals("cycle-1-error", deadLetters.get(0).getFailureMessage());
        assertEquals("cycle-2-error", deadLetters.get(1).getFailureMessage());
        assertNotEquals(deadLetters.get(0).getSid(), deadLetters.get(1).getSid());
    }

    @Test
    void testExecuteTask_shouldHandleDeadlineExceeded() throws Exception {
        NopRetryPolicy policy = createTestPolicy("policy-deadline");
        policy.setMaxRetryCount(5);
        policy.setImmediateRetryCount(0);
        policy.setDeadlineTimeoutMs(100L);
        recordStore.savePolicy(policy);

        // 直接创建一条老记录（createTime 在 1 秒前，超过 100ms 截止）
        NopRetryRecord oldRecord = createTestRecord("old-record", RETRY_RECORD_STATUS_PENDING);
        oldRecord.setIdempotentId("idem-deadline");
        oldRecord.setPolicyId("policy-deadline");
        oldRecord.setCreateTime(new Timestamp(System.currentTimeMillis() - 1000));
        oldRecord.setRetryCount(1);
        recordStore.saveRecord(oldRecord);

        IRetryTask task = retryEngine.newRetryTask("svc", "method")
                .withPolicyId("policy-deadline")
                .withIdempotentId("idem-deadline");

        ApiRequest<Object> request = new ApiRequest<>();
        CompletableFuture<ApiResponse<?>> future = retryEngine.executeTask(task, request, null)
                .toCompletableFuture();

        assertThrows(NopException.class, future::get);
        assertEquals(0, rpcInvoker.getInvocationCount());

        List<NopRetryDeadLetter> deadLetters = findDeadLetters("idem-deadline");
        assertEquals(1, deadLetters.size());
        assertEquals("DEADLINE_EXCEEDED", deadLetters.get(0).getFailureCode());
    }

    // ==================== Block Strategy Tests ====================

    @Test
    void testExecuteTask_shouldHandleOverwriteStrategy() throws Exception {
        NopRetryPolicy policy = createTestPolicy("policy-overwrite");
        policy.setBlockStrategy(BLOCK_STRATEGY_OVERWRITE);
        recordStore.savePolicy(policy);

        NopRetryRecord existingRecord = createTestRecord("existing-overwrite", RETRY_RECORD_STATUS_PENDING);
        existingRecord.setIdempotentId("idem-overwrite");
        existingRecord.setPolicyId("policy-overwrite");
        existingRecord.setRequestPayload("{\"data\":\"old\"}");
        recordStore.saveRecord(existingRecord);

        rpcInvoker.setResponse(ApiResponse.success("new-result"));

        IRetryTask task = retryEngine.newRetryTask("svc", "method")
                .withPolicyId("policy-overwrite")
                .withIdempotentId("idem-overwrite");

        ApiRequest<Object> request = new ApiRequest<>();
        request.setData(Map.of("data", "new"));

        CompletableFuture<ApiResponse<?>> future = retryEngine.executeTask(task, request, null)
                .toCompletableFuture();
        ApiResponse<?> response = future.get();
        assertTrue(response.isOk());

        // 旧记录被删除，新记录保存了新的请求载荷
        NopRetryRecord newRecord = findRecordByIdempotentId("idem-overwrite");
        assertNotNull(newRecord);
        assertNotEquals("existing-overwrite", newRecord.getSid());
        assertTrue(newRecord.getRequestPayload().contains("\"new\""));
    }

    @Test
    void testExecuteTask_shouldHandleParallelStrategy() throws Exception {
        NopRetryPolicy policy = createTestPolicy("policy-parallel");
        policy.setBlockStrategy(BLOCK_STRATEGY_PARALLEL);
        policy.setMaxRetryCount(5);
        policy.setImmediateRetryCount(0);
        recordStore.savePolicy(policy);

        NopRetryRecord existingRecord = createTestRecord("existing-parallel", RETRY_RECORD_STATUS_PENDING);
        existingRecord.setIdempotentId("idem-parallel");
        existingRecord.setPolicyId("policy-parallel");
        recordStore.saveRecord(existingRecord);

        rpcInvoker.setResponse(ApiResponse.success("ok"));

        IRetryTask task = retryEngine.newRetryTask("svc", "method")
                .withPolicyId("policy-parallel")
                .withIdempotentId("idem-parallel");

        ApiRequest<Object> request = new ApiRequest<>();
        CompletableFuture<ApiResponse<?>> future = retryEngine.executeTask(task, request, null)
                .toCompletableFuture();
        ApiResponse<?> response = future.get();
        assertTrue(response.isOk());

        // PARALLEL 复用已有记录执行
        NopRetryRecord record = findRecordByIdempotentId("idem-parallel");
        assertNotNull(record);
        assertEquals("existing-parallel", record.getSid());
    }

    // ==================== Backoff Tests ====================

    @Test
    void testExecuteTask_shouldComputeDeterministicBackoffWithoutJitter() throws Exception {
        NopRetryPolicy policy = createTestPolicy("policy-backoff");
        policy.setMaxRetryCount(3);
        policy.setImmediateRetryCount(0);
        policy.setBackoffStrategy(BACKOFF_STRATEGY_EXPONENTIAL_BACKOFF);
        policy.setInitialIntervalMs(100L);
        policy.setMaxIntervalMs(10000L);
        policy.setJitterRatio(0d); // 无抖动，确定性断言
        recordStore.savePolicy(policy);

        rpcInvoker.setResponses(createErrorResponse("error-1"));

        long before = System.currentTimeMillis();
        IRetryTask task = retryEngine.newRetryTask("svc", "method")
                .withPolicyId("policy-backoff")
                .withIdempotentId("idem-backoff");

        ApiRequest<Object> request = new ApiRequest<>();
        retryEngine.executeTask(task, request, null).toCompletableFuture().get();

        NopRetryRecord record = findRecordByIdempotentId("idem-backoff");
        assertNotNull(record);
        assertNotNull(record.getNextTriggerTime());

        // 第一次失败后 retryCount=1，指数退避 initial * 2^(1-1) = 100ms
        long expected = before + 100;
        long actual = record.getNextTriggerTime().getTime();
        assertTrue(actual >= expected - 50 && actual <= expected + 50);
    }

    // ==================== Callback Tests ====================

    @Test
    void testCallback_shouldTriggerOnSuccess() throws Exception {
        NopRetryPolicy policy = createTestPolicy("policy-callback-success");
        policy.setMaxRetryCount(3);
        policy.setImmediateRetryCount(0);
        policy.setCallbackEnabled(BOOL_YES);
        policy.setCallbackTriggerType(CALLBACK_TRIGGER_TYPE_ON_SUCCESS);
        policy.setCallbackPolicyId("policy-callback-success");
        recordStore.savePolicy(policy);

        rpcInvoker.setResponse(ApiResponse.success("ok"));

        IRetryTask task = retryEngine.newRetryTask("svc", "method")
                .withPolicyId("policy-callback-success")
                .withIdempotentId("idem-callback-success");

        ApiRequest<Object> request = new ApiRequest<>();
        CompletableFuture<ApiResponse<?>> future = retryEngine.executeTask(task, request, null)
                .toCompletableFuture();
        future.get();

        // 原任务 + 回调任务 = 2 次调用
        assertEquals(2, rpcInvoker.getInvocationCount());

        // 回调任务 idempotentId 追加 _callback 后缀
        NopRetryRecord callbackRecord = findRecordByIdempotentId("idem-callback-success_callback");
        assertNotNull(callbackRecord);
        assertEquals(RETRY_RECORD_STATUS_COMPLETED, callbackRecord.getStatus());
    }

    @Test
    void testCallback_shouldTriggerOnFailure() throws Exception {
        NopRetryPolicy policy = createTestPolicy("policy-callback-failure");
        policy.setMaxRetryCount(1);
        policy.setImmediateRetryCount(0);
        policy.setCallbackEnabled(BOOL_YES);
        policy.setCallbackTriggerType(CALLBACK_TRIGGER_TYPE_ON_FAILURE);
        policy.setCallbackPolicyId("policy-callback-failure");
        recordStore.savePolicy(policy);

        rpcInvoker.setResponses(createErrorResponse("fail-1"));

        IRetryTask task = retryEngine.newRetryTask("svc", "method")
                .withPolicyId("policy-callback-failure")
                .withIdempotentId("idem-callback-failure");

        ApiRequest<Object> request = new ApiRequest<>();
        CompletableFuture<ApiResponse<?>> future = retryEngine.executeTask(task, request, null)
                .toCompletableFuture();
        future.get();

        // 原任务失败(1) + 回调任务(1) = 2 次调用
        assertEquals(2, rpcInvoker.getInvocationCount());
        NopRetryRecord callbackRecord = findRecordByIdempotentId("idem-callback-failure_callback");
        assertNotNull(callbackRecord);
    }

    @Test
    void testCallback_shouldNotTriggerWhenDisabled() throws Exception {
        NopRetryPolicy policy = createTestPolicy("policy-callback-disabled");
        policy.setMaxRetryCount(3);
        policy.setImmediateRetryCount(0);
        policy.setCallbackEnabled(BOOL_NO);
        recordStore.savePolicy(policy);

        rpcInvoker.setResponse(ApiResponse.success("ok"));

        IRetryTask task = retryEngine.newRetryTask("svc", "method")
                .withPolicyId("policy-callback-disabled")
                .withIdempotentId("idem-callback-disabled");

        ApiRequest<Object> request = new ApiRequest<>();
        retryEngine.executeTask(task, request, null).toCompletableFuture().get();

        assertEquals(1, rpcInvoker.getInvocationCount());
        assertNull(findRecordByIdempotentId("idem-callback-disabled_callback"));
    }

    // ==================== Error Path Tests ====================

    @Test
    void testPause_shouldFailForMissingRecord() {
        assertThrows(NopException.class, () -> retryEngine.pause("missing-record"));
    }

    @Test
    void testResume_shouldFailForMissingRecord() {
        assertThrows(NopException.class, () -> retryEngine.resume("missing-record"));
    }

    @Test
    void testRetryFromDeadLetter_shouldFailWhenDeadLetterMissing() throws Exception {
        CompletableFuture<ApiResponse<?>> future = retryEngine.retryFromDeadLetter("missing-dl", null)
                .toCompletableFuture();
        assertThrows(NopException.class, future::get);
        assertEquals(0, rpcInvoker.getInvocationCount());
    }

    @Test
    void testRetryFromDeadLetter_shouldFailWhenExecutorMissing() throws Exception {
        NopRetryDeadLetter deadLetter = createTestDeadLetter("dl-no-executor");
        deadLetter.setServiceName(null);
        deadLetter.setServiceMethod(null);
        deadLetter.setRequestPayload("{\"data\":\"test\"}");
        recordStore.saveDeadLetter(deadLetter);

        CompletableFuture<ApiResponse<?>> future = retryEngine.retryFromDeadLetter("dl-no-executor", null)
                .toCompletableFuture();
        assertThrows(NopException.class, future::get);
        assertEquals(0, rpcInvoker.getInvocationCount());
    }

    @Test
    void testRetryFromDeadLetter_shouldFailWhenRequestPayloadMissing() throws Exception {
        NopRetryDeadLetter deadLetter = createTestDeadLetter("dl-no-payload");
        deadLetter.setServiceName("test-svc");
        deadLetter.setServiceMethod("test-method");
        deadLetter.setRequestPayload(null);
        recordStore.saveDeadLetter(deadLetter);

        CompletableFuture<ApiResponse<?>> future = retryEngine.retryFromDeadLetter("dl-no-payload", null)
                .toCompletableFuture();
        assertThrows(NopException.class, future::get);
        assertEquals(0, rpcInvoker.getInvocationCount());
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

    private NopRetryRecord findRecordByIdempotentId(String idempotentId) {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("idempotentId", idempotentId));
        query.setLimit(1);
        return daoProvider.daoFor(NopRetryRecord.class).findFirstByQuery(query);
    }

    private List<NopRetryAttempt> findAttempts(String recordId) {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq(PROP_NAME_recordId, recordId));
        query.addOrderField(PROP_NAME_attemptNo, true);
        return daoProvider.daoFor(NopRetryAttempt.class).findAllByQuery(query);
    }

    private List<NopRetryDeadLetter> findDeadLetters(String idempotentId) {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("idempotentId", idempotentId));
        query.addOrderField("createTime", true);
        return daoProvider.daoFor(NopRetryDeadLetter.class).findAllByQuery(query);
    }
}

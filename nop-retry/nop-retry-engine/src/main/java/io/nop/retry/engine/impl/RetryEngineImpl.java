/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.retry.engine.impl;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.exceptions.NopRebuildException;
import io.nop.api.core.rpc.IRpcServiceInvoker;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.commons.service.LifeCycleSupport;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.retry.api.IRetryEngine;
import io.nop.retry.api.IRetryTask;
import io.nop.retry.dao.entity.NopRetryDeadLetter;
import io.nop.retry.dao.entity.NopRetryPolicy;
import io.nop.retry.dao.entity.NopRetryRecord;
import io.nop.retry.engine.NopRetryConstants;
import io.nop.retry.engine.scanner.IRetryScanner;
import io.nop.retry.engine.store.IRetryRecordStore;
import io.nop.api.core.beans.ErrorBean;
import io.nop.core.exceptions.ErrorMessageManager;

import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static io.nop.retry.engine.NopRetryErrors.*;

public class RetryEngineImpl extends LifeCycleSupport implements IRetryEngine {

    static final Logger LOG = LoggerFactory.getLogger(RetryEngineImpl.class);

    private IRpcServiceInvoker rpcServiceInvoker;
    private IRetryRecordStore recordStore;
    private IRetryScanner retryScanner;

    @Inject
    public void setRpcServiceInvoker(IRpcServiceInvoker rpcServiceInvoker) {
        this.rpcServiceInvoker = rpcServiceInvoker;
    }

    @Inject
    public void setRecordStore(IRetryRecordStore recordStore) {
        this.recordStore = recordStore;
    }

    @Inject
    public void setRetryScanner(IRetryScanner retryScanner) {
        this.retryScanner = retryScanner;
    }

    @Override
    public IRetryTask newRetryTask(String serviceName, String serviceMethod) {
        return new RetryTaskImpl(this, serviceName, serviceMethod);
    }

    @Override
    public CompletionStage<ApiResponse<?>> retryFromDeadLetter(String deadLetterId, ICancelToken cancelToken) {
        NopRetryDeadLetter deadLetter = recordStore.loadDeadLetter(deadLetterId);

        if (deadLetter == null) {
            CompletableFuture<ApiResponse<?>> future = new CompletableFuture<>();
            future.completeExceptionally(
                    new NopException(ERR_RETRY_DEAD_LETTER_NOT_FOUND)
                            .param(ARG_DEAD_LETTER_ID, deadLetterId));
            return future;
        }

        String serviceName = deadLetter.getServiceName();
        String serviceMethod = deadLetter.getServiceMethod();
        String requestPayload = deadLetter.getRequestPayload();

        if (StringHelper.isEmpty(serviceName) || StringHelper.isEmpty(serviceMethod)) {
            CompletableFuture<ApiResponse<?>> future = new CompletableFuture<>();
            future.completeExceptionally(
                    new NopException(ERR_RETRY_DEAD_LETTER_INVALID_EXECUTOR)
                            .param(ARG_DEAD_LETTER_ID, deadLetterId));
            return future;
        }

        if (StringHelper.isEmpty(requestPayload)) {
            CompletableFuture<ApiResponse<?>> future = new CompletableFuture<>();
            future.completeExceptionally(
                    new NopException(ERR_RETRY_DEAD_LETTER_INVALID_REQUEST)
                            .param(ARG_DEAD_LETTER_ID, deadLetterId));
            return future;
        }

        ApiRequest<?> request = JsonTool.parseBeanFromText(requestPayload, ApiRequest.class);
        return rpcServiceInvoker.invokeAsync(serviceName, serviceMethod, request, null);
    }

    @Override
    public void pause(String recordId) {
        NopRetryRecord record = recordStore.loadRecord(recordId);

        if (record == null) {
            throw new NopException(ERR_RETRY_RECORD_NOT_FOUND)
                    .param(ARG_RECORD_ID, recordId);
        }

        int status = record.getStatus() != null ? record.getStatus() : 0;

        if (status == NopRetryConstants.RETRY_RECORD_STATUS_SUSPENDED) {
            throw new NopException(ERR_RETRY_RECORD_ALREADY_SUSPENDED)
                    .param(ARG_RECORD_ID, recordId);
        }

        if (status == NopRetryConstants.RETRY_RECORD_STATUS_COMPLETED) {
            throw new NopException(ERR_RETRY_RECORD_ALREADY_COMPLETED)
                    .param(ARG_RECORD_ID, recordId);
        }

        record.setStatus(NopRetryConstants.RETRY_RECORD_STATUS_SUSPENDED);
        recordStore.updateRecord(record);
    }

    @Override
    public void resume(String recordId) {
        NopRetryRecord record = recordStore.loadRecord(recordId);

        if (record == null) {
            throw new NopException(ERR_RETRY_RECORD_NOT_FOUND)
                    .param(ARG_RECORD_ID, recordId);
        }

        int status = record.getStatus() != null ? record.getStatus() : 0;

        if (status != NopRetryConstants.RETRY_RECORD_STATUS_SUSPENDED) {
            throw new NopException(ERR_RETRY_RECORD_NOT_SUSPENDED)
                    .param(ARG_RECORD_ID, recordId);
        }

        record.setStatus(NopRetryConstants.RETRY_RECORD_STATUS_PENDING);
        record.setNextTriggerTime(new Timestamp(recordStore.getCurrentTime()));
        recordStore.updateRecord(record);
    }

    CompletionStage<ApiResponse<?>> executeTask(IRetryTask task, ApiRequest<?> request, ICancelToken cancelToken) {
        NopRetryPolicy policy = recordStore.loadPolicy(task.getPolicyId());

        NopRetryRecord existingRecord = recordStore.findPendingRecordByIdempotentId(task.getIdempotentId());
        if (existingRecord != null) {
            return handleBlockStrategy(existingRecord, policy, request, cancelToken, task);
        }

        NopRetryRecord record = recordStore.newRecord(task, request);
        recordStore.saveRecord(record);
        return executeWithRetry(record, policy, request, cancelToken, task);
    }

    private CompletionStage<ApiResponse<?>> handleBlockStrategy(
            NopRetryRecord existingRecord,
            NopRetryPolicy policy,
            ApiRequest<?> request,
            ICancelToken cancelToken,
            IRetryTask task) {

        switch (policy.getBlockStrategyOrDefault()) {
            case NopRetryConstants.BLOCK_STRATEGY_DISCARD:
                LOG.info("nop.retry.block-strategy-discard:recordId={}", existingRecord.getSid());
                return FutureHelper.success(ApiResponse.success(null));

            case NopRetryConstants.BLOCK_STRATEGY_OVERWRITE:
                LOG.info("nop.retry.block-strategy-overwrite:recordId={}", existingRecord.getSid());
                recordStore.deleteRecord(existingRecord);
                NopRetryRecord newRecord = recordStore.newRecord(task, request);
                recordStore.saveRecord(newRecord);
                return executeWithRetry(newRecord, policy, request, cancelToken, task);

            case NopRetryConstants.BLOCK_STRATEGY_PARALLEL:
            default:
                LOG.debug("nop.retry.block-strategy-parallel:recordId={}", existingRecord.getSid());
                return executeWithRetry(existingRecord, policy, request, cancelToken, null);
        }
    }

    CompletionStage<ApiResponse<?>> executeRetryFromScanner(NopRetryRecord record, ICancelToken cancelToken) {
        String serviceName = record.getServiceName();
        String serviceMethod = record.getServiceMethod();
        String requestPayload = record.getRequestPayload();

        if (StringHelper.isEmpty(serviceName) || StringHelper.isEmpty(serviceMethod)) {
            return CompletableFuture.failedStage(
                    new NopException(ERR_RETRY_DEAD_LETTER_INVALID_EXECUTOR)
                            .param(ARG_RECORD_ID, record.getSid()));
        }

        if (StringHelper.isEmpty(requestPayload)) {
            return CompletableFuture.failedStage(
                    new NopException(ERR_RETRY_DEAD_LETTER_INVALID_REQUEST)
                            .param(ARG_RECORD_ID, record.getSid()));
        }

        NopRetryPolicy policy = recordStore.loadPolicy(record.getPolicyId());
        ApiRequest<?> request = ApiRequest.build(JsonTool.parseMap(requestPayload));

        return executeWithRetry(record, policy, request, cancelToken, null);
    }

    private CompletionStage<ApiResponse<?>> executeWithRetry(
            NopRetryRecord record,
            NopRetryPolicy policy,
            ApiRequest<?> request,
            ICancelToken cancelToken,
            IRetryTask task) {

        long deadlineTimeoutMs = policy.getDeadlineTimeoutMsOrDefault();
        if (deadlineTimeoutMs > 0) {
            Timestamp createTime = record.getCreateTime();
            if (createTime != null) {
                long elapsed = recordStore.getCurrentTime() - createTime.getTime();
                if (elapsed > deadlineTimeoutMs) {
                    return handleDeadlineExceeded(record);
                }
            }
        }

        if (isImmediateRetryPhase(record, policy)) {
            return executeImmediateRetry(record, policy, request, cancelToken, task);
        } else {
            return executeSingleAttempt(record, policy, request, cancelToken, task);
        }
    }

    private boolean isImmediateRetryPhase(NopRetryRecord record, NopRetryPolicy policy) {
        int immediateRetryCount = policy.getImmediateRetryCountOrDefault();
        if (immediateRetryCount <= 0) {
            return false;
        }
        int retryCount = record.getRetryCount() != null ? record.getRetryCount() : 0;
        return retryCount < immediateRetryCount;
    }

    private CompletionStage<ApiResponse<?>> executeImmediateRetry(
            NopRetryRecord record,
            NopRetryPolicy policy,
            ApiRequest<?> request,
            ICancelToken cancelToken,
            IRetryTask task) {

        CompletableFuture<ApiResponse<?>> result = new CompletableFuture<>();
        int immediateRetryCount = policy.getImmediateRetryCountOrDefault();
        long immediateRetryIntervalMs = policy.getImmediateRetryIntervalMsOrDefault();

        doExecute(record, request, cancelToken)
                .whenComplete((response, ex) -> {
                    if (ex != null) {
                        int retryCount = (record.getRetryCount() != null ? record.getRetryCount() : 0) + 1;
                        if (retryCount < immediateRetryCount) {
                            record.setRetryCount(retryCount);
                            recordStore.updateRecord(record);
                            scheduleImmediateRetry(record, policy, request, cancelToken, task, immediateRetryIntervalMs)
                                    .whenComplete((r, e) -> {
                                        if (e != null) {
                                            result.completeExceptionally(e);
                                        } else {
                                            result.complete(r);
                                        }
                                    });
                        } else {
                            handleExecutionFailure(record, policy, ex, task);
                            result.completeExceptionally(ex);
                        }
                    } else if (response.isOk()) {
                        handleExecutionSuccess(record, policy, task);
                        result.complete(response);
                    } else {
                        NopException failureEx = NopRebuildException.rebuild(response);
                        if (failureEx.isBizFatal()) {
                            // bizFatal 不重试，直接移入死信
                            moveToDeadLetter(record, failureEx);
                            result.complete(response);
                        } else {
                            int retryCount = (record.getRetryCount() != null ? record.getRetryCount() : 0) + 1;
                            if (retryCount < immediateRetryCount) {
                                record.setRetryCount(retryCount);
                                recordStore.updateRecord(record);
                                scheduleImmediateRetry(record, policy, request, cancelToken, task, immediateRetryIntervalMs)
                                        .whenComplete((r, e) -> {
                                            if (e != null) {
                                                result.completeExceptionally(e);
                                            } else {
                                                result.complete(r);
                                            }
                                        });
                            } else {
                                handleExecutionFailure(record, policy, failureEx, task);
                                result.complete(response);
                            }
                        }
                    }
                });

        return result;
    }

    private CompletionStage<ApiResponse<?>> scheduleImmediateRetry(
            NopRetryRecord record,
            NopRetryPolicy policy,
            ApiRequest<?> request,
            ICancelToken cancelToken,
            IRetryTask task,
            long immediateRetryIntervalMs) {

        CompletableFuture<ApiResponse<?>> future = new CompletableFuture<>();

        getExecutor().schedule(() -> {
            executeImmediateRetry(record, policy, request, cancelToken, task)
                    .whenComplete((resp, ex) -> {
                        if (ex != null) {
                            future.completeExceptionally(ex);
                        } else {
                            future.complete(resp);
                        }
                    });
            return null;
        }, immediateRetryIntervalMs, TimeUnit.MILLISECONDS);

        return future;
    }

    private CompletionStage<ApiResponse<?>> executeSingleAttempt(
            NopRetryRecord record,
            NopRetryPolicy policy,
            ApiRequest<?> request,
            ICancelToken cancelToken,
            IRetryTask task) {

        return doExecute(record, request, cancelToken)
                .whenComplete((response, ex) -> {
                    if (ex != null) {
                        handleExecutionFailure(record, policy, ex, task);
                    } else if (response.isOk()) {
                        handleExecutionSuccess(record, policy, task);
                    } else {
                        NopException failureEx = NopRebuildException.rebuild(response);
                        if (failureEx.isBizFatal()) {
                            // bizFatal 不重试，直接移入死信
                            moveToDeadLetter(record, failureEx);
                        } else {
                            handleExecutionFailure(record, policy, failureEx, task);
                        }
                    }
                });
    }

    private CompletionStage<ApiResponse<?>> doExecute(
            NopRetryRecord record,
            ApiRequest<?> request,
            ICancelToken cancelToken) {

        return rpcServiceInvoker.invokeAsync(
                record.getServiceName(),
                record.getServiceMethod(),
                request,
                cancelToken
        );
    }

    private CompletionStage<ApiResponse<?>> handleDeadlineExceeded(NopRetryRecord record) {
        LOG.warn("nop.retry.deadline-exceeded:recordId={}", record.getSid());
        moveToDeadLetter(record, "DEADLINE_EXCEEDED", "Deadline timeout exceeded", null);
        return CompletableFuture.failedStage(
                new NopException(ERR_RETRY_DEADLINE_EXCEEDED)
                        .param(ARG_RECORD_ID, record.getSid()));
    }

    private void handleExecutionSuccess(NopRetryRecord record, NopRetryPolicy policy, IRetryTask task) {
        record.setStatus(NopRetryConstants.RETRY_RECORD_STATUS_COMPLETED);
        recordStore.updateRecord(record);
        LOG.info("nop.retry.task-completed:recordId={}", record.getSid());

        if (policy.hasCallbackPolicy() && shouldTriggerCallback(policy, true)) {
            triggerCallback(record, policy, true, null);
        }
    }

    private void handleExecutionFailure(NopRetryRecord record, NopRetryPolicy policy, Throwable ex, IRetryTask task) {
        LOG.error("nop.retry.task-failed:recordId={}", record.getSid(), ex);

        int retryCount = (record.getRetryCount() != null ? record.getRetryCount() : 0) + 1;
        record.setRetryCount(retryCount);
        int maxRetryCount = record.getMaxRetryCount() != null ? record.getMaxRetryCount() : policy.getMaxRetryCountOrDefault();

        if (retryCount >= maxRetryCount) {
            moveToDeadLetter(record, ex);
            if (policy.hasCallbackPolicy() && shouldTriggerCallback(policy, false)) {
                triggerCallback(record, policy, false, ex);
            }
        } else {
            long nextTriggerTime = calculateNextTriggerTime(record, policy);
            record.setNextTriggerTime(new Timestamp(nextTriggerTime));
            record.setStatus(NopRetryConstants.RETRY_RECORD_STATUS_PENDING);
            recordStore.updateRecord(record);
        }
    }

    private boolean shouldTriggerCallback(NopRetryPolicy policy, boolean success) {
        switch (policy.getCallbackTriggerTypeOrDefault()) {
            case NopRetryConstants.CALLBACK_TRIGGER_TYPE_ON_SUCCESS:
                return success;
            case NopRetryConstants.CALLBACK_TRIGGER_TYPE_ON_FAILURE:
                return !success;
            case NopRetryConstants.CALLBACK_TRIGGER_TYPE_ALWAYS:
            default:
                return true;
        }
    }

    private void triggerCallback(NopRetryRecord record, NopRetryPolicy policy, boolean success, Throwable error) {
        if (!policy.hasCallbackPolicy()) {
            LOG.debug("nop.retry.callback-policy-not-configured:recordId={}", record.getSid());
            return;
        }

        String callbackService = record.getServiceName();
        String callbackMethod = record.getServiceMethod();
        if (StringHelper.isEmpty(callbackService) || StringHelper.isEmpty(callbackMethod)) {
            LOG.warn("nop.retry.callback-service-not-configured:recordId={}", record.getSid());
            return;
        }

        // 创建回调任务，使用 callbackPolicyId 作为策略
        IRetryTask callbackTask = newRetryTask(callbackService, callbackMethod)
                .withPolicyId(policy.getCallbackPolicyId())
                .withIdempotentId(record.getIdempotentId() + "_callback");

        ApiRequest<Object> callbackRequest = new ApiRequest<>();
        callbackRequest.setData(buildCallbackData(record, success, error));

        // 异步执行回调任务
        executeTask(callbackTask, callbackRequest, null)
                .whenComplete((resp, ex) -> {
                    if (ex != null) {
                        LOG.error("nop.retry.callback-failed:recordId={}", record.getSid(), ex);
                    } else if (resp != null && resp.isOk()) {
                        LOG.info("nop.retry.callback-success:recordId={}", record.getSid());
                    } else {
                        LOG.warn("nop.retry.callback-response-error:recordId={},status={}",
                                record.getSid(), resp != null ? resp.getStatus() : null);
                    }
                });
    }

    private Object buildCallbackData(NopRetryRecord record, boolean success, Throwable error) {
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("recordId", record.getSid());
        data.put("idempotentId", record.getIdempotentId());
        data.put("success", success);
        data.put("retryCount", record.getRetryCount());
        if (error != null) {
            data.put("errorCode", error instanceof NopException ? ((NopException) error).getErrorCode() : "UNKNOWN");
            data.put("errorMessage", error.getMessage());
        }
        return data;
    }

    private void moveToDeadLetter(NopRetryRecord record, Throwable ex) {
        ErrorBean errorBean = ErrorMessageManager.instance().buildErrorMessage(null, ex);
        recordStore.moveToDeadLetter(record, errorBean.getErrorCode(), errorBean.getDescription(), errorBean.getErrorStack());
        LOG.warn("nop.retry.task-moved-to-dead-letter:recordId={}", record.getSid());
    }

    private void moveToDeadLetter(NopRetryRecord record, String errorCode, String errorMessage, String errorStack) {
        recordStore.moveToDeadLetter(record, errorCode, errorMessage, errorStack);
        LOG.warn("nop.retry.task-moved-to-dead-letter:recordId={}", record.getSid());
    }

    private long calculateNextTriggerTime(NopRetryRecord record, NopRetryPolicy policy) {
        int retryCount = record.getRetryCount() != null ? record.getRetryCount() : 0;

        long interval;
        if (policy.getBackoffStrategyOrDefault() == NopRetryConstants.BACKOFF_STRATEGY_EXPONENTIAL_BACKOFF) {
            interval = policy.getInitialIntervalMsOrDefault() * (1L << Math.min(retryCount - 1, 10));
        } else {
            interval = policy.getInitialIntervalMsOrDefault();
        }

        interval = Math.min(interval, policy.getMaxIntervalMsOrDefault());

        double jitterRatio = policy.getJitterRatioOrDefault();
        if (jitterRatio > 0) {
            double jitterFactor = 1.0 + (Math.random() * 2 - 1) * jitterRatio;
            interval = (long) (interval * jitterFactor);
        }

        return recordStore.getCurrentTime() + interval;
    }

    protected IScheduledExecutor getExecutor() {
        return GlobalExecutors.globalTimer().executeOn(GlobalExecutors.globalWorker());
    }

    @Override
    protected void doStart() {
        if (retryScanner != null) {
            retryScanner.startScanning(records -> {
                // 内部并发执行每个 record
                CompletableFuture<?>[] futures = records.stream()
                        .map(record -> executeRetryFromScanner(record, null)
                                .whenComplete((resp, ex) -> {
                                    if (ex != null) {
                                        LOG.error("nop.retry.scanner.execute-failed:recordId={}", record.getSid(), ex);
                                    }
                                })
                                .toCompletableFuture())
                        .toArray(CompletableFuture[]::new);

                // 等待所有 record 执行完成
                CompletableFuture.allOf(futures).join();
            });
            LOG.info("nop.retry.scanner-started");
        }
    }

    @Override
    protected void doStop() {
        if (retryScanner != null) {
            retryScanner.stopScanning();
            LOG.info("nop.retry.scanner-stopped");
        }
    }
}

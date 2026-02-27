/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.retry.service.impl;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.util.ICancelToken;
import io.nop.retry.api.IRetryTask;

import java.util.concurrent.CompletionStage;

public class RetryTaskImpl implements IRetryTask {

    private final RetryServiceImpl retryService;
    private final String serviceName;
    private final String serviceMethod;

    private String executorId;
    private String policyId;
    private String idempotentId;
    private String callbackService;
    private String callbackMethod;
    private String namespaceId;
    private String groupId;
    public RetryTaskImpl(RetryServiceImpl retryService, String serviceName, String serviceMethod) {
        this.retryService = retryService;
        this.serviceName = serviceName;
        this.serviceMethod = serviceMethod;
    }

    @Override
    public String getServiceName() {
        return serviceName;
    }

    @Override
    public String getServiceMethod() {
        return serviceMethod;
    }

    @Override
    public String getExecutorId() {
        return executorId;
    }

    @Override
    public IRetryTask withExecutorId(String executorId) {
        this.executorId = executorId;
        return this;
    }

    @Override
    public String getPolicyId() {
        return policyId;
    }

    @Override
    public IRetryTask withPolicyId(String policyId) {
        this.policyId = policyId;
        return this;
    }

    @Override
    public String getIdempotentId() {
        return idempotentId;
    }

    @Override
    public IRetryTask withIdempotentId(String idempotentId) {
        this.idempotentId = idempotentId;
        return this;
    }

    @Override
    public String getCallbackService() {
        return callbackService;
    }

    @Override
    public String getCallbackMethod() {
        return callbackMethod;
    }

    @Override
    public IRetryTask withCallback(String callbackService, String callbackMethod) {
        this.callbackService = callbackService;
        this.callbackMethod = callbackMethod;
        return this;
    }

    @Override
    public String getNamespaceId() {
        return namespaceId;
    }

    @Override
    public IRetryTask withNamespaceId(String namespaceId) {
        this.namespaceId = namespaceId;
        return this;
    }

    @Override
    public String getGroupId() {
        return groupId;
    }

    @Override
    public IRetryTask withGroupId(String groupId) {
        this.groupId = groupId;
        return this;
    }

    @Override
    public CompletionStage<ApiResponse<?>> callAsync(ApiRequest<?> request, ICancelToken cancelToken) {
        return retryService.executeTask(this, request, cancelToken);
    }
}

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
import io.nop.api.core.util.ICancelToken;
import io.nop.retry.api.IRetryTask;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class RetryTaskImpl implements IRetryTask {

    private final RetryEngineImpl retryEngine;
    private final String serviceName;
    private final String serviceMethod;

    private String executorName;
    private String policyId;
    private String idempotentId;
    private String namespaceId;
    private String groupId;
    public RetryTaskImpl(RetryEngineImpl retryEngine, String serviceName, String serviceMethod) {
        this.retryEngine = retryEngine;
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
    public String getExecutorName() {
        return executorName;
    }

    @Override
    public IRetryTask withExecutorName(String executorName) {
        this.executorName = executorName;
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
        // 1.6-C8-017（plan 2515 裁定⑥契约半边，R21 同 diff 搭车）：提交面失败交付三通道
        // （同步抛/future 异常/永不完成）收敛为 future 单通道——executeTask 同步抛（loadPolicy/
        // 幂等查重等 DB 读故障）转 failedStage，等待非必需、fire-and-forget 受支持的调用方
        // 也可经 future 感知失败。契约文本落 docs-for-ai/03-modules/nop-retry.md（Phase 3 落账）
        try {
            return retryEngine.executeTask(this, request, cancelToken);
        } catch (Throwable t) {
            return CompletableFuture.failedStage(t);
        }
    }
}

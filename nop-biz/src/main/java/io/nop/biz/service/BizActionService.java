/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.biz.service;

import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.biz.IBizActionService;
import io.nop.api.core.biz.IBizHashFunction;
import io.nop.api.core.ioc.IBeanContainer;
import io.nop.api.core.util.FutureHelper;
import io.nop.biz.BizConstants;
import io.nop.biz.api.IBizActionModel;
import io.nop.biz.api.IBizObject;
import io.nop.biz.api.IBizObjectManager;
import io.nop.commons.concurrent.executor.IPartitionedExecutor;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.rpc.api.IRpcServiceInvocation;

import jakarta.inject.Inject;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

public class BizActionService implements IBizActionService {
    private IBizObjectManager bizObjManager;
    private IBizHashFunction bizHashFunction = DefaultBizHashFunction.INSTANCE;
    private IBeanContainer beanContainer;

    public void setBizHashFunction(IBizHashFunction bizHashFunction) {
        this.bizHashFunction = bizHashFunction;
    }

    @InjectValue(InjectValue.VALUE_BEAN_CONTAINER)
    public void setBeanContainer(IBeanContainer beanContainer) {
        this.beanContainer = beanContainer;
    }

    @Inject
    public void setBizObjManager(IBizObjectManager bizObjManager) {
        this.bizObjManager = bizObjManager;
    }

    @Override
    public CompletionStage<ApiResponse<?>> callActionAsync(String bizObjName, String bizAction, ApiRequest<?> request) {

        IBizObject actor = bizObjManager.getBizObject(bizObjName);
        IBizActionModel actionModel = actor.getActionModel(bizAction);

        // 选择action在哪个线程池上执行
        String executorName = getWorkExecutorBean(actionModel);

        CompletableFuture<ApiResponse<?>> future = new CompletableFuture<>();

        Runnable task = () -> {
            IRpcServiceInvocation inv = newInvocation(actor, bizAction, request);

            inv.proceedAsync().whenComplete((res, err) -> {
                FutureHelper.complete(future, res, err);
            });
        };

        try {
            // 如果要求顺序执行，则根据bizKey选择一个固定的Executor来执行
            if (actionModel.isBizSequential()) {
                int hash = bizHashFunction.getBizHash(request);
                IPartitionedExecutor executor = (IPartitionedExecutor) beanContainer.getBean(executorName);
                executor.executeForPartition(hash, task);
            } else {
                Executor executor = (Executor) beanContainer.getBean(executorName);
                executor.execute(task);
            }
        } catch (Throwable e) {
            future.completeExceptionally(e);
        }

        return future;
    }

    private IRpcServiceInvocation newInvocation(IBizObject bizObj, String bizAction, ApiRequest<?> request) {
        IServiceContext rt = new ServiceContextImpl();
        rt.setRequest(request.getData());
        rt.setRequestHeaders(request.getHeaders());

        IRpcServiceInvocation inv = new BizActionInvocation(bizObj, bizAction, request, rt);
        return inv;
    }

    protected String getWorkExecutorBean(IBizActionModel actionModel) {
        String executor = actionModel.getExecutor();
        if (executor == null)
            return BizConstants.DEFAULT_WORK_EXECUTOR_NAME;
        return executor;
    }
}

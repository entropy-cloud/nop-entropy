/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.biz.service;

import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.biz.IBizActionService;
import io.nop.api.core.biz.IBizHashFunction;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.context.IContext;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.ioc.IBeanContainer;
import io.nop.api.core.util.FutureHelper;
import io.nop.biz.BizConstants;
import io.nop.biz.api.IBizActionModel;
import io.nop.biz.api.IBizModel;
import io.nop.biz.api.IBizObject;
import io.nop.biz.api.IBizObjectManager;
import io.nop.commons.concurrent.executor.IPartitionedExecutor;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.api.core.rpc.IRpcServiceInvocation;

import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

import static io.nop.biz.BizErrors.ARG_ACTION_NAME;
import static io.nop.biz.BizErrors.ARG_BIZ_OBJ_NAME;
import static io.nop.biz.BizErrors.ERR_BIZ_EXECUTOR_NOT_PARTITIONED;
import static io.nop.biz.BizErrors.ERR_BIZ_UNKNOWN_EXECUTOR;
import static io.nop.biz.BizErrors.ERR_BIZ_OBJECT_NOT_SUPPORT_ACTION;

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
        // 纯Java注解注册的biz model可能没有xbiz模型（getBizModel()==null），未知的action名也会返回null，
        // 这里统一转换为携带bizObjName/actionName的语义化异常，而不是后续解引用时抛无上下文的NPE
        IBizModel bizModel = actor == null ? null : actor.getBizModel();
        IBizActionModel actionModel = bizModel == null ? null : bizModel.getAction(bizAction);
        if (actionModel == null)
            throw new NopException(ERR_BIZ_OBJECT_NOT_SUPPORT_ACTION)
                    .param(ARG_BIZ_OBJ_NAME, bizObjName)
                    .param(ARG_ACTION_NAME, bizAction);

        // 选择action在哪个线程池上执行
        String executorName = getWorkExecutorBean(actionModel);

        CompletableFuture<ApiResponse<?>> future = new CompletableFuture<>();

        // C3-007：受管提交（裁定③快照+executeWithContext 形态）——提交线程快照调用方 IContext，
        // 任务体内经捕获 context 的 executeWithContext 附着执行（finally 语义恢复原附着，restore
        // 由原语承载）。调用方 tenant/locale/trace/callExpireTime 传播到 worker 线程；任务体内的
        // ServiceContextImpl 字段初始化（getOrCreateContext）捕获的是调用方 context 而非新建/残留
        // context，消除池线程永久附着与跨任务串号。双分支（executeForPartition/execute）同批闭合。
        IContext callerContext = ContextProvider.currentContext();

        Runnable task = () -> {
            try {
                if (callerContext != null) {
                    callerContext.executeWithContext(() -> {
                        proceedInvocation(actor, bizAction, request, future);
                        return null;
                    });
                } else {
                    proceedInvocation(actor, bizAction, request, future);
                }
            } catch (Throwable e) {
                FutureHelper.complete(future, null, e);
            }
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

    /** 测试缝：按名解析执行器（与 callActionAsync 同路径） */
    Object getBeanForTest(String name) {
        return beanContainer.getBean(name);
    }

    private void proceedInvocation(IBizObject actor, String bizAction, ApiRequest<?> request,
                                   CompletableFuture<ApiResponse<?>> future) {
        IRpcServiceInvocation inv = newInvocation(actor, bizAction, request);

        inv.proceedAsync().whenComplete((res, err) -> {
            FutureHelper.complete(future, res, err);
        });
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

    /**
     * 2.3-C4-001（plan 2551 裁定 A②）：启动期执行器校验（类型感知）。遍历全部 biz 对象的
     * actionModel：①executor 名必须可解析（beanContainer 内存在）；②bizSequential 动作解析到的
     * executor 须实现 {@link IPartitionedExecutor}（缺省执行器无分区串行档，非法组合启动期即拒，
     * 列全违例动作名——运行期不可达）。容器未装配（纯 JVM 测试语境）时跳过（无动作即无守卫面）。
     * 由 IoC 容器 @PostConstruct / BizObjectManager delayInit 阶段调用（beans 装配挂接）。
     */
    public void validateExecutors() {
        if (bizObjManager == null || beanContainer == null)
            return;

        List<String> unknownExecutors = null;
        List<String> nonPartitionedUsages = null;

        for (String objName : bizObjManager.getBizObjNames()) {
            IBizObject actor = bizObjManager.getBizObject(objName);
            IBizModel bizModel = actor == null ? null : actor.getBizModel();
            if (bizModel == null)
                continue;
            List<? extends IBizActionModel> actions = bizModel.getActions();
            if (actions == null)
                continue;
            for (IBizActionModel actionModel : actions) {
                String executorName = getWorkExecutorBean(actionModel);
                Object executor;
                try {
                    executor = beanContainer.getBean(executorName);
                } catch (Throwable e) {
                    if (unknownExecutors == null)
                        unknownExecutors = new ArrayList<>();
                    unknownExecutors.add(objName + "__" + actionModel.getName() + " -> " + executorName);
                    continue;
                }
                if (actionModel.isBizSequential() && !(executor instanceof IPartitionedExecutor)) {
                    if (nonPartitionedUsages == null)
                        nonPartitionedUsages = new ArrayList<>();
                    nonPartitionedUsages.add(objName + "__" + actionModel.getName() + " -> " + executorName);
                }
            }
        }

        if (unknownExecutors != null) {
            throw new NopException(ERR_BIZ_UNKNOWN_EXECUTOR)
                    .param("executors", String.join("; ", unknownExecutors));
        }
        if (nonPartitionedUsages != null) {
            throw new NopException(ERR_BIZ_EXECUTOR_NOT_PARTITIONED)
                    .param("actions", String.join("; ", nonPartitionedUsages));
        }
    }
}

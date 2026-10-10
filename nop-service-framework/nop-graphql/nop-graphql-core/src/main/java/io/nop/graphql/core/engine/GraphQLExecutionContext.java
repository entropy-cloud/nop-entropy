/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.engine;

import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.Guard;
import io.nop.auth.api.mfa.IOperationMfaChecker;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.graphql.core.GraphQLConstants;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.graphql.core.ParsedGraphQLRequest;
import io.nop.graphql.core.ast.GraphQLOperation;
import org.dataloader.DataLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.nop.graphql.core.GraphQLErrors.ARG_LOADER_NAME;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_DUPLICATED_LOADER;

public class GraphQLExecutionContext implements IGraphQLExecutionContext {
    static final Logger LOG = LoggerFactory.getLogger(GraphQLExecutionContext.class);

    private final IServiceContext serviceContext;

    private GraphQLOperation operation;

    private final Map<String, DataLoader> loaders = new ConcurrentHashMap<>();

    // P1-2.6-004（plan 2376）：调度周期排定标志——同一时刻至多一个周期在途/已排定
    private final AtomicBoolean dispatchScheduled = new AtomicBoolean();

    private FieldSelectionBean fieldSelection;

    private boolean makerCheckerEnabled;

    private IOperationMfaChecker operationMfaChecker;

    public GraphQLExecutionContext(IServiceContext serviceContext) {
        this.serviceContext = serviceContext == null ? new ServiceContextImpl() : serviceContext;
        this.serviceContext.getEvalScope().setLocalValue(GraphQLConstants.VAR_GQL_CTX, this);
    }

    public GraphQLExecutionContext() {
        this(new ServiceContextImpl());
    }

    @Override
    public String getExecutionId() {
        return serviceContext.getExecutionId();
    }

    @Override
    public void setExecutionId(String executionId) {
        serviceContext.setExecutionId(executionId);
    }

    @Override
    public IServiceContext getServiceContext() {
        return serviceContext;
    }

    @Override
    public Object getResponse() {
        return serviceContext.getResponse();
    }

    @Override
    public void setResponse(Object response) {
        serviceContext.setResponse(response);
    }

    @Override
    public Map<String, Object> getRequestHeaders() {
        return serviceContext.getRequestHeaders();
    }

    @Override
    public boolean isMakerCheckerEnabled() {
        return makerCheckerEnabled;
    }

    @Override
    public void setMakerCheckerEnabled(boolean makerCheckerEnabled) {
        this.makerCheckerEnabled = makerCheckerEnabled;
    }

    @Override
    public IOperationMfaChecker getOperationMfaChecker() {
        return operationMfaChecker;
    }

    @Override
    public void setOperationMfaChecker(IOperationMfaChecker operationMfaChecker) {
        this.operationMfaChecker = operationMfaChecker;
    }

    public FieldSelectionBean getFieldSelection() {
        return fieldSelection;
    }

    public void setFieldSelection(FieldSelectionBean fieldSelection) {
        this.fieldSelection = fieldSelection;
    }

    @Override
    public GraphQLOperation getOperation() {
        return operation;
    }

    public void setOperation(GraphQLOperation operation) {
        this.operation = operation;
    }

    @Override
    public synchronized ParsedGraphQLRequest getRequest() {
        return (ParsedGraphQLRequest) serviceContext.getRequest();
    }

    @Override
    public void setRequest(ParsedGraphQLRequest request) {
        serviceContext.setRequest(request);
    }

    @Override
    public <K, V> DataLoader getDataLoader(String loaderName) {
        return loaders.get(loaderName);
    }

    @Override
    public <K, V> void registerDataLoader(String loaderName, DataLoader<K, V> loader) {
        Guard.notNull(loader, "loader");

        DataLoader old = loaders.put(loaderName, loader);
        if (old != null && old != loader)
            throw new NopException(ERR_GRAPHQL_DUPLICATED_LOADER).param(ARG_LOADER_NAME, loaderName);
    }

    /**
     * P1-2.6-004（plan 2376）：注册触发的异步 dispatch 周期。周期提交到全局 worker 池
     * （batch biz 方法可含阻塞 IO，不经 globalTimer），并在请求 context 绑定下执行
     * （batch load 经 FutureHelper.futureCall 内联运行于周期线程，需请求 thread-local
     * context）。异步提交保证注册线程的同步 load burst 聚合在同一批（批保护）。
     */
    @Override
    public void requestDispatch() {
        if (!dispatchScheduled.compareAndSet(false, true))
            return;

        try {
            GlobalExecutors.getExecutor(GlobalExecutors.NOP_GLOBAL_WORKER).execute(this::runDispatchCycle);
        } catch (Throwable e) {
            dispatchScheduled.set(false);
            throw NopException.adapt(e);
        }
    }

    private void runDispatchCycle() {
        try {
            getContext().execute(this::dispatchCycleInContext);
        } finally {
            // 终止不变式（plan 2376 约束 2）：必须先清调度标志再查注册表——顺序颠倒时
            // 窗口内注册方因标志仍为 true 放弃提交、周期已停机 → 丢唤醒 → 挂死复现
            dispatchScheduled.set(false);
            if (!loaders.isEmpty())
                requestDispatch();
        }
    }

    private void dispatchCycleInContext() {
        CompletionStage<Void> promise = dispatchAll();
        if (promise != null) {
            promise.whenComplete((v, e) -> {
                if (e != null)
                    LOG.debug("nop.graphql.dispatch-cycle-error", e);
            });
        }
    }

    @Override
    public synchronized CompletionStage<Void> dispatchAll() {
        if (loaders.isEmpty())
            return null;

        Map<String, DataLoader> loaders = new HashMap<>(this.loaders);
        this.loaders.clear();

        List<CompletionStage<?>> futures = new ArrayList<>();
        for (Map.Entry<String, DataLoader> entry : loaders.entrySet()) {
            DataLoader loader = entry.getValue();
            LOG.debug("loader.dispatch:{}", entry.getKey());
            futures.add(loader.dispatch());
        }
        return FutureHelper.waitAll(futures);
    }

}

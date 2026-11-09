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

import static io.nop.graphql.core.GraphQLErrors.ARG_LOADER_NAME;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_DUPLICATED_LOADER;

public class GraphQLExecutionContext implements IGraphQLExecutionContext {
    static final Logger LOG = LoggerFactory.getLogger(GraphQLExecutionContext.class);

    private final IServiceContext serviceContext;

    private GraphQLOperation operation;

    private final Map<String, DataLoader> loaders = new ConcurrentHashMap<>();

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
    public synchronized void setRequest(ParsedGraphQLRequest request) {
        // 2.3-C1-004（plan 2550 裁定 A）：与 getRequest 同监视器——消除读写监视器失配。
        // 契约残注：本类外其他 serviceContext.setRequest 写入方不在此同步域内。
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
     * P1-2.6-004：dispatchAll由GraphQLExecutor在调度点统一调用（发起时点名+游走回合出口
     * 泵+批次完成递归，见GraphQLExecutor.dispatchAll）。快照并清空loader注册表后逐个
     * dispatch，返回各批完成的聚合promise；注册表为空返回null（本轮调度链终止）。
     *
     * <p>2.3-C2-001（plan 2550 裁定 B，←并 C9-007）：loader.dispatch() 涉及批次数据装载
     * （DB/远程 I/O），必须在本上下文监视器外执行——快照+清空在 synchronized 域内完成，
     * dispatch 循环出锁。契约：快照后新注册的 loader 归下一轮 dispatchAll（本轮不感知）。
     */
    @Override
    public CompletionStage<Void> dispatchAll() {
        Map<String, DataLoader> snapshot;
        synchronized (this) {
            if (loaders.isEmpty())
                return null;
            snapshot = new HashMap<>(loaders);
            loaders.clear();
        }

        List<CompletionStage<?>> futures = new ArrayList<>();
        for (Map.Entry<String, DataLoader> entry : snapshot.entrySet()) {
            DataLoader loader = entry.getValue();
            LOG.debug("loader.dispatch:{}", entry.getKey());
            futures.add(loader.dispatch());
        }
        return FutureHelper.waitAll(futures);
    }

}

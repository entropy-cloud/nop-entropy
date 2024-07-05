/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core;

import io.nop.api.core.auth.IActionAuthChecker;
import io.nop.api.core.auth.IDataAuthChecker;
import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.context.IContext;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.graphql.core.ast.GraphQLOperation;
import org.dataloader.DataLoader;

import java.util.Map;
import java.util.concurrent.CompletionStage;

import static io.nop.graphql.core.GraphQLErrors.ARG_LOADER_NAME;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_UNKNOWN_LOADER;

public interface IGraphQLExecutionContext {
    IServiceContext getServiceContext();

    default IContext getContext() {
        return getServiceContext().getContext();
    }

    /**
     * 如果启用makerChecker机制，则实际执行的是tryAction，而不是原始的action本身
     */
    boolean isMakerCheckerEnabled();

    void setMakerCheckerEnabled(boolean makerCheckerEnabled);

    String getExecutionId();

    void setExecutionId(String executionId);

    GraphQLOperation getOperation();

    void setOperation(GraphQLOperation operation);

    ParsedGraphQLRequest getRequest();

    void setRequest(ParsedGraphQLRequest request);

    FieldSelectionBean getFieldSelection();

    Object getResponse();

    void setResponse(Object response);

    Map<String, Object> getRequestHeaders();

    void setFieldSelection(FieldSelectionBean selection);

    default Object getVariable(String name) {
        ParsedGraphQLRequest request = getRequest();
        return request.getVariable(name);
    }

    default Object getExtension(String name) {
        ParsedGraphQLRequest request = getRequest();
        return request.getExtension(name);
    }

    <K, V> DataLoader<K, V> getDataLoader(String loaderName);

    default <K, V> DataLoader<K, V> requireDataLoader(String loaderName) {
        DataLoader<K, V> loader = getDataLoader(loaderName);
        if (loader == null)
            throw new NopException(ERR_GRAPHQL_UNKNOWN_LOADER).param(ARG_LOADER_NAME, loaderName);
        return loader;
    }

    <K, V> void registerDataLoader(String loaderName, DataLoader<K, V> loader);

    /**
     * 调用所有DataLoader的dispatch函数，并等待它们返回
     */
    CompletionStage<Void> dispatchAll();

    default void setActionAuthChecker(IActionAuthChecker actionAuthChecker) {
        getServiceContext().setActionAuthChecker(actionAuthChecker);
    }

    default void setDataAuthChecker(IDataAuthChecker dataAuthChecker) {
        getServiceContext().setDataAuthChecker(dataAuthChecker);
    }

    default void setRequestHeaders(Map<String, Object> requestHeaders) {
        getServiceContext().setRequestHeaders(requestHeaders);
    }

    default void setResponseHeaders(Map<String, Object> responseHeaders) {
        getServiceContext().setResponseHeaders(responseHeaders);
    }

    default Map<String, Object> getResponseHeaders() {
        return getServiceContext().getResponseHeaders();
    }

    default void complete() {
        getServiceContext().complete();
    }

    default void completeExceptionally(Throwable e) {
        getServiceContext().completeExceptionally(e);
    }

    default IEvalScope getEvalScope() {
        return getServiceContext().getEvalScope();
    }

    default void cancel(String reason){
        getServiceContext().cancel(reason);
    }

    default IActionAuthChecker getActionAuthChecker(){
        return getServiceContext().getActionAuthChecker();
    }

    default IUserContext getUserContext(){
        return getServiceContext().getUserContext();
    }
}
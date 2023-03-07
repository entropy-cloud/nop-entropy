/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.graphql.core;

import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.context.IServiceContext;
import io.nop.graphql.core.ast.GraphQLOperation;
import org.dataloader.DataLoader;

import java.util.concurrent.CompletionStage;

import static io.nop.graphql.core.GraphQLErrors.ARG_LOADER_NAME;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_UNKNOWN_LOADER;

public interface IGraphQLExecutionContext extends IServiceContext {
    /**
     * 如果启用makerChecker机制，则实际执行的是tryAction，而不是原始的action本身
     */
    boolean isMakerCheckerEnabled();

    void setMakerCheckerEnabled(boolean makerCheckerEnabled);

    String getExecutionId();

    GraphQLOperation getOperation();

    ParsedGraphQLRequest getRequest();

    FieldSelectionBean getFieldSelection();

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
}
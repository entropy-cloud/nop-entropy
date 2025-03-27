/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.engine;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.api.core.util.FutureHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IServiceContext;
import io.nop.core.model.selection.FieldSelectionBeanParser;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.ParsedGraphQLRequest;
import io.nop.graphql.core.ast.GraphQLDirectiveDefinition;
import io.nop.graphql.core.ast.GraphQLDocument;
import io.nop.graphql.core.ast.GraphQLFieldDefinition;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.ast.GraphQLSelectionSet;
import io.nop.graphql.core.ast.GraphQLTypeDefinition;
import io.nop.graphql.core.ast.GraphQLVariableDefinition;
import io.nop.graphql.core.schema.IGraphQLSchemaLoader;

import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

public interface IGraphQLEngine {
    IGraphQLSchemaLoader getSchemaLoader();

    void clearCache();

    ICancelTokenManger getCancelTokenManager();

    GraphQLDocument parseOperation(String query, boolean skipCache);

    GraphQLTypeDefinition getTypeDefinition(String typeName);

    GraphQLFieldDefinition getOperationDefinition(GraphQLOperationType opType, String name);

    GraphQLDirectiveDefinition getDirective(String name);

    /**
     * 检查selectionSet中用到的字段在模型中都已经定义
     */
    void resolveSelection(String objName, GraphQLSelectionSet selectionSet,
                          Map<String, GraphQLVariableDefinition> vars);

    /**
     * 根据GraphQLSelectionSet构造FieldSelectionBean对象。将GraphQLVariable替换为vars集合中指定的值
     */
    FieldSelectionBean buildSelectionBean(String name, GraphQLSelectionSet selectionSet, Map<String, Object> vars);

    IGraphQLExecutionContext newGraphQLContextFromContext(IServiceContext context);

    void initGraphQLContext(IGraphQLExecutionContext context, ParsedGraphQLRequest request);

    default ParsedGraphQLRequest parseRequest(GraphQLRequestBean request) {
        GraphQLDocument doc = parseOperation(request.getQuery(), false);
        ParsedGraphQLRequest parsed = new ParsedGraphQLRequest();
        parsed.setOperationId(request.getOperationId());
        parsed.setDocument(doc);
        parsed.setExtensions(request.getExtensions());
        parsed.setVariables(request.getVariables());
        return parsed;
    }

    default IGraphQLExecutionContext newGraphQLContextForParsedRequest(ParsedGraphQLRequest request, IServiceContext ctx) {
        IGraphQLExecutionContext context = newGraphQLContextFromContext(ctx);
        initGraphQLContext(context, request);
        return context;
    }

    default IGraphQLExecutionContext newGraphQLContext(GraphQLRequestBean request, IServiceContext ctx) {
        GraphQLDocument doc = parseOperation(request.getQuery(), false);
        ParsedGraphQLRequest parsed = new ParsedGraphQLRequest();
        parsed.setOperationId(request.getOperationId());
        parsed.setDocument(doc);
        parsed.setExtensions(request.getExtensions());
        parsed.setVariables(request.getVariables());
        return newGraphQLContextForParsedRequest(parsed, ctx);
    }

    default IGraphQLExecutionContext newGraphQLContext(GraphQLRequestBean request) {
        return newGraphQLContext(request, null);
    }

    boolean cancel(String requestId);

    CompletionStage<GraphQLResponseBean> executeGraphQLAsync(IGraphQLExecutionContext context);

    Flow.Publisher<GraphQLResponseBean> subscribeGraphQL(IGraphQLExecutionContext context);

    default GraphQLResponseBean executeGraphQL(IGraphQLExecutionContext context) {
        return FutureHelper.syncGet(executeGraphQLAsync(context));
    }

    /**
     * 通过Restful调用方式执行单个operation
     */
    CompletionStage<ApiResponse<?>> executeRpcAsync(IGraphQLExecutionContext context);

    default ApiResponse<?> executeRpc(IGraphQLExecutionContext context) {
        return FutureHelper.syncGet(executeRpcAsync(context));
    }

    void initRpcContext(IGraphQLExecutionContext context, GraphQLOperationType opType,
                        String operationName, ApiRequest<?> request);

    default IGraphQLExecutionContext newRpcContext(GraphQLOperationType opType, String operationName,
                                                   ApiRequest<?> request, IServiceContext ctx) {
        IGraphQLExecutionContext context = newGraphQLContextFromContext(ctx);
        initRpcContext(context, opType, operationName, request);
        return context;
    }

    default IGraphQLExecutionContext newRpcContext(GraphQLOperationType opType, String operationName,
                                                   ApiRequest<?> request) {
        return newRpcContext(opType, operationName, request, null);
    }

    CompletionStage<Object> fetchResultWithSelection(Object result, String resultType,
                                                     FieldSelectionBean selectionBean, IServiceContext ctx);

    default CompletionStage<Object> fetchResult(Object result, String resultType, String selection, IServiceContext ctx) {
        FieldSelectionBean selectionBean = null;
        if (!StringHelper.isBlank(selection)) {
            selectionBean = new FieldSelectionBeanParser().parseFromText(null, selection);
        }
        return fetchResultWithSelection(result, resultType, selectionBean, ctx);
    }

    GraphQLResponseBean buildGraphQLResponse(Object result, Throwable err, IGraphQLExecutionContext context);

    ApiResponse<?> buildRpcResponse(Object result, Throwable err, IGraphQLExecutionContext context);

    <T> T makeRpcProxy(Class<T> rpcClass);
}
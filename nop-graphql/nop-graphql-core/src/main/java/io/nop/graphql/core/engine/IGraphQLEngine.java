/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.graphql.core.engine;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.api.core.util.FutureHelper;
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

    IGraphQLExecutionContext newGraphQLContext();

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

    default IGraphQLExecutionContext newGraphQLContext(ParsedGraphQLRequest request) {
        IGraphQLExecutionContext context = newGraphQLContext();
        initGraphQLContext(context, request);
        return context;
    }

    default IGraphQLExecutionContext newGraphQLContext(GraphQLRequestBean request) {
        GraphQLDocument doc = parseOperation(request.getQuery(), false);
        ParsedGraphQLRequest parsed = new ParsedGraphQLRequest();
        parsed.setOperationId(request.getOperationId());
        parsed.setDocument(doc);
        parsed.setExtensions(request.getExtensions());
        parsed.setVariables(request.getVariables());
        return newGraphQLContext(parsed);
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
                                                   ApiRequest<?> request) {
        IGraphQLExecutionContext context = newGraphQLContext();
        initRpcContext(context, opType, operationName, request);
        return context;
    }

    GraphQLResponseBean buildGraphQLResponse(Object result, Throwable err, IGraphQLExecutionContext context);

    ApiResponse<?> buildRpcResponse(Object result, Throwable err, IGraphQLExecutionContext context);

    <T> T makeRpcProxy(Class<T> rpcClass);
}
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
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.context.IContext;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.ApiHeaders;
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
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

import static io.nop.api.core.ApiConfigs.CFG_RPC_PROPAGATE_HEADERS;

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

    /**
     * 通过RPC方式订阅，返回Flow.Publisher。类似于executeRpcAsync，但用于subscription操作。
     */
    Flow.Publisher<ApiResponse<?>> subscribeRpc(IGraphQLExecutionContext context);

    void initRpcContext(IGraphQLExecutionContext context, GraphQLOperationType opType,
                        String operationName, ApiRequest<?> request);

    /**
     * C3-012：五入口（gRPC/quarkus WS/spring WS/消息/文件）共用的统一 binder chokepoint
     * （CRA R1-04 裁定③内置统一接入形态，设计权威来源
     * {@code ai-dev/design/concurrency/bindcancel-context-unification.md} §3.3）。
     * 语义与 ContextBinder 同构：线程已有 open context 则复用（HTTP filter 链入口的既有
     * 附着不被二次建 context 分叉，头部落盘由既有入口履行），无则按请求头新建并附着
     * （tenant/locale/timezone/callExpireTime/propagateHeaders），随请求终态关闭。
     * <p>
     * binder 必须先于执行上下文构造执行：ServiceContextImpl 字段初始化经
     * {@code getOrCreateContext()} 捕获线程附着 context——binder 前置使新建 context 先行
     * 附着，执行上下文捕获的即是携带请求头的 context（也是订阅/执行同步段的线程附着，
     * 2.3-C3-017 同修面）。
     */
    default IGraphQLExecutionContext newRpcContext(GraphQLOperationType opType, String operationName,
                                                   ApiRequest<?> request) {
        IContext current = ContextProvider.currentContext();
        if (current != null && !current.isClosed()) {
            // 复用既有 open context：头部落盘由既有入口（ContextHttpServerFilter/
            // GraphQLWebService 的 ContextBinder）履行，不二次建 context 分叉
            return newRpcContext(opType, operationName, request, null);
        }

        // 无 open context（gRPC/WS/消息/文件入口）：按请求头新建并附着
        IContext created = ContextProvider.newContext(true);
        initContextFromRequest(created, request);
        try {
            IGraphQLExecutionContext context = newRpcContext(opType, operationName, request, null);
            // 请求终态关闭：随 GraphQL 执行上下文完成而关闭（订阅路径在流结束时 complete，
            // 一次性请求在 buildRpcResponse/buildGraphQLResponse 收尾时 complete）
            context.getServiceContext().onAfterComplete(e -> created.close());
            return context;
        } catch (RuntimeException e) {
            created.close();
            throw e;
        }
    }

    /**
     * 与 ContextBinder.initContext 同构的请求头落盘（tenant/locale/timezone/callExpireTime/
     * propagateHeaders）。
     */
    private void initContextFromRequest(IContext context, ApiRequest<?> request) {
        String timezone = ApiHeaders.getTimeZone(request);
        String locale = ApiHeaders.getLocale(request);
        String tenantId = ApiHeaders.getTenant(request);
        long timeout = ApiHeaders.getTimeout(request, -1);

        context.setLocale(locale);
        context.setTenantId(tenantId);
        context.setTimezone(timezone);
        Set<String> headers = ConvertHelper.toCsvSet(CFG_RPC_PROPAGATE_HEADERS.get());
        context.setPropagateRpcHeaders(ApiHeaders.getHeaders(request.getHeaders(), headers));

        if (timeout > 0) {
            context.setCallExpireTime(CoreMetrics.currentTimeMillis() + timeout);
        }
    }

    default IGraphQLExecutionContext newRpcContext(GraphQLOperationType opType, String operationName,
                                                   ApiRequest<?> request, IServiceContext ctx) {
        IGraphQLExecutionContext context = newGraphQLContextFromContext(ctx);
        initRpcContext(context, opType, operationName, request);
        return context;
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
package io.nop.graphql.core.jsonrpc;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.exceptions.ErrorMessageManager;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.type.utils.GenericTypeHelper;
import io.nop.graphql.core.GraphQLConfigs;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.ast.GraphQLFieldDefinition;
import io.nop.graphql.core.engine.IGraphQLEngine;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import static io.nop.graphql.core.GraphQLErrors.ARG_MAX_COUNT;
import static io.nop.graphql.core.GraphQLErrors.ERR_JSONRPC_EXCEED_MAX_COMMAND_COUNT;

public class JsonRpcService {
    static final Logger LOG = LoggerFactory.getLogger(JsonRpcService.class);

    private IGraphQLEngine graphQLEngine;

    @Inject
    public void setGraphQLEngine(IGraphQLEngine graphQLEngine) {
        this.graphQLEngine = graphQLEngine;
    }

    public CompletionStage<ApiResponse<String>> executeAsync(String body, Map<String, Object> headers) {
        if (StringHelper.isBlank(body)) {
            return FutureHelper.success(JsonRpcResponse.INVALID_REQUEST(null));
        }

        Object req;
        try {
            if (body.startsWith("[")) {
                req = JsonTool.parseBeanFromText(body, GenericTypeHelper.buildListType(ReflectionManager.instance().buildRawType(JsonRpcRequest.class)));
            } else {
                req = JsonTool.parseBeanFromText(body, JsonRpcRequest.class);
            }
        } catch (Exception err) {
            return FutureHelper.success(buildResult(
                    400, null, buildResponseForException(JsonRpcErrorCodes.PARSE_ERROR, null, err)));
        }

        IServiceContext context = new ServiceContextImpl();
        context.setRequestHeaders(headers);

        if (req instanceof List) {
            List<JsonRpcRequest> requests = (List<JsonRpcRequest>) req;
            if (requests.isEmpty()) {
                // JSON-RPC 2.0规范：空批返回单个Invalid Request错误对象（非数组）
                return FutureHelper.success(buildResult(
                        200, null, JsonRpcResponse.INVALID_REQUEST(null)));
            }

            if (GraphQLConfigs.CFG_GRAPHQL_QUERY_MAX_OPERATION_COUNT.get() < requests.size()) {
                // maxCount应报告配置的上限值而非请求中的实际数量，客户端/运维才能得知允许上限
                NopException err = new NopException(ERR_JSONRPC_EXCEED_MAX_COMMAND_COUNT)
                        .param(ARG_MAX_COUNT, GraphQLConfigs.CFG_GRAPHQL_QUERY_MAX_OPERATION_COUNT.get());
                return FutureHelper.success(buildResult(
                        400, null, buildResponseForException(JsonRpcErrorCodes.INVALID_REQUEST, null, err)));
            }

            return batchExecuteCommandAsync((List<JsonRpcRequest>) req, context).thenApply(
                    r -> buildResult(200, context.getResponseHeaders(), r));
        } else {
            return executeCommandAsync((JsonRpcRequest) req, context).thenApply(
                    r -> buildResult(200, context.getResponseHeaders(), r));
        }
    }

    static ApiResponse<String> buildResult(int httpStatus, Map<String, Object> headers, Object body) {
        ApiResponse<String> ret = new ApiResponse<>();
        ret.setHttpStatus(httpStatus);
        ret.setHeaders(headers);
        if (body != null)
            ret.setData(JsonTool.stringify(body));
        return ret;
    }

    JsonRpcResponse<?> buildResponseForException(int code, String id, Throwable err) {
        ApiResponse<?> res = ErrorMessageManager.instance().buildResponseForException(null, err);
        JsonRpcResponse<?> ret = JsonRpcHelper.buildJsonRpcResponse(res,id);
        if (code != 0)
            ret.getError().setCode(code);
        return ret;
    }

    public CompletionStage<JsonRpcResponse<?>> executeCommandAsync(JsonRpcRequest request, IServiceContext context) {
        GraphQLFieldDefinition op = graphQLEngine.getOperationDefinition(null, request.getMethod());
        if (op == null) {
            return FutureHelper.success(JsonRpcResponse.METHOD_NOT_FOUND(request.getMethod(), request.getId()));
        }

        ApiRequest<Map<String, Object>> req;
        try {
            req = JsonRpcHelper.buildApiRequest(request);
            req.addHeadersIfAbsent(context.getRequestHeaders());
        } catch (Exception err) {
            return FutureHelper.success(buildResponseForException(JsonRpcErrorCodes.INVALID_REQUEST, request.getId(), err));
        }

        IGraphQLExecutionContext gqlCtx = graphQLEngine.newRpcContext(null, request.getMethod(), req, context);
        return graphQLEngine.executeRpcAsync(gqlCtx).thenApply(
                ret-> JsonRpcHelper.buildJsonRpcResponse(ret,request.getId()));
    }

    public CompletionStage<List<JsonRpcResponse<?>>> batchExecuteCommandAsync(List<JsonRpcRequest> requests, IServiceContext context) {
        if (requests.isEmpty()) {
            // 返回类型必须与声明的List一致：单个JsonRpcResponse塞进List泛型是潜伏的ClassCastException
            return FutureHelper.success(List.of(JsonRpcResponse.INVALID_REQUEST(null)));
        }

        List<CompletionStage<JsonRpcResponse<?>>> promises = new ArrayList<>();
        for (JsonRpcRequest request : requests) {
            if (request.getId() == null) {
                // notification按规范无需响应，但失败必须可观测，且不得拖垮同批其他entry
                executeCommandAsyncSafely(request, context).whenComplete((r, e) -> {
                    if (e != null) {
                        LOG.warn("nop.jsonrpc.notification-execute-fail:method={}", request.getMethod(), e);
                    } else if (r != null && r.getError() != null) {
                        LOG.warn("nop.jsonrpc.notification-execute-fail:method={},errorCode={},errorMsg={}",
                                request.getMethod(), r.getError().getCode(), r.getError().getMessage());
                    }
                });
            } else {
                promises.add(executeCommandAsyncSafely(request, context));
            }
        }

        return FutureHelper.waitAll(promises).thenApply(r -> {
            return FutureHelper.getResults(promises);
        });
    }

    /**
     * 单个entry的失败只影响该entry（JSON-RPC 2.0 batch语义）：newRpcContext对未知参数/selection
     * 校验失败会同步抛出NopException，异步执行失败会使promise异常完成，两处都收敛为该entry的错误响应。
     */
    private CompletionStage<JsonRpcResponse<?>> executeCommandAsyncSafely(JsonRpcRequest request, IServiceContext context) {
        try {
            return executeCommandAsync(request, context).exceptionally(err -> {
                Throwable e = err instanceof java.util.concurrent.CompletionException && err.getCause() != null
                        ? err.getCause() : err;
                return buildResponseForException(JsonRpcErrorCodes.INVALID_REQUEST, request.getId(), e);
            });
        } catch (Exception err) {
            return FutureHelper.success(buildResponseForException(
                    JsonRpcErrorCodes.INVALID_REQUEST, request.getId(), err));
        }
    }

}
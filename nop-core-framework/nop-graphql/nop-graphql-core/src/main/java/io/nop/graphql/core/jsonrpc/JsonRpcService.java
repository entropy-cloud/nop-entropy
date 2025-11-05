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
            if (GraphQLConfigs.CFG_GRAPHQL_QUERY_MAX_OPERATION_COUNT.get() <= requests.size()) {
                NopException err = new NopException(ERR_JSONRPC_EXCEED_MAX_COMMAND_COUNT)
                        .param(ARG_MAX_COUNT, requests.size());
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
            return FutureHelper.success(JsonRpcResponse.INVALID_REQUEST(null));
        }

        List<CompletionStage<JsonRpcResponse<?>>> promises = new ArrayList<>();
        for (JsonRpcRequest request : requests) {
            if (request.getId() == null) {
                executeCommandAsync(request, context);
            } else {
                promises.add(executeCommandAsync(request, context));
            }
        }

        return FutureHelper.waitAll(promises).thenApply(r -> {
            return FutureHelper.getResults(promises);
        });
    }

}
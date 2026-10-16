/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.quarkus.web.service;

import io.nop.core.lang.json.JsonTool;
import io.nop.xlang.debug.web.DebugGatewayRuntime;
import io.nop.xlang.debug.web.ReplAccessException;
import io.nop.xlang.debug.web.ReplService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * XLang REPL HTTP 端点薄壳（D6 §3.1，plan 2473）：`POST /r/nop-xlang-debug/compile|execute`
 * ——鉴权/权限/沙箱/取消全在容器无关核心 {@link ReplService}，本壳只做参数/头抽取与
 * 状态码映射（401/403/400/413/429）。网关未启用时按 503 拒绝。
 */
@Path("/r/nop-xlang-debug")
public class NopXlangDebugReplWebService {

    @POST
    @Path("/{action}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response invoke(@PathParam("action") String action, @Context HttpHeaders httpHeaders, String body) {
        DebugGatewayRuntime runtime = DebugGatewayRuntime.singleton();
        if (runtime == null || runtime.getReplService() == null)
            return json(Response.Status.SERVICE_UNAVAILABLE, errorBody(null, "xlang debug web disabled"));

        Map<String, Object> params;
        try {
            params = body == null || body.isEmpty() ? Map.of() : JsonTool.parseMap(body);
        } catch (Exception e) {
            return json(Response.Status.BAD_REQUEST, errorBody(null, "invalid json body"));
        }

        Map<String, Object> headers = extractHeaders(httpHeaders);
        try {
            Map<String, Object> result;
            if ("compile".equals(action)) {
                result = runtime.getReplService().compile(
                        (String) params.get("sourcePath"), (String) params.get("source"), headers);
            } else if ("execute".equals(action)) {
                Object timeout = params.get("timeoutMs");
                result = runtime.getReplService().execute(
                        (String) params.get("sessionId"), (String) params.get("source"),
                        timeout instanceof Number ? ((Number) timeout).intValue() : null, headers);
            } else {
                return json(Response.Status.NOT_FOUND, errorBody(null, "unknown action: " + action));
            }
            return json(Response.Status.OK, result);
        } catch (ReplAccessException e) {
            return json(Response.Status.fromStatusCode(e.getHttpStatus()),
                    errorBody(e.getErrorCode(), e.getResponseMessage()));
        } catch (Exception e) {
            return json(Response.Status.INTERNAL_SERVER_ERROR,
                    errorBody(null, "internal error: " + e.getClass().getSimpleName()));
        }
    }

    static Map<String, Object> extractHeaders(HttpHeaders httpHeaders) {
        Map<String, Object> headers = new HashMap<>();
        if (httpHeaders != null) {
            for (Map.Entry<String, List<String>> entry : httpHeaders.getRequestHeaders().entrySet()) {
                List<String> values = entry.getValue();
                if (values != null && !values.isEmpty())
                    headers.put(entry.getKey().toLowerCase(), values.get(0));
            }
        }
        return headers;
    }

    static Map<String, Object> errorBody(String errorCode, String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("message", message);
        if (errorCode != null)
            error.put("errorCode", errorCode);
        Map<String, Object> body = new HashMap<>();
        body.put("error", error);
        return body;
    }

    private Response json(Response.Status status, Map<String, Object> body) {
        return Response.status(status).type(MediaType.APPLICATION_JSON)
                .entity(JsonTool.stringify(body)).build();
    }
}

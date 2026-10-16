/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.spring.web.service;

import io.nop.core.lang.json.JsonTool;
import io.nop.xlang.debug.web.DebugGatewayRuntime;
import io.nop.xlang.debug.web.ReplAccessException;
import io.nop.xlang.debug.web.ReplService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * XLang REPL HTTP 端点薄壳（D6 §3.1，plan 2473，spring 形态）：照
 * {@link SpringJsonRpcWebService} 先例，开关门复用平台调试通道开关
 * {@code nop.xlang.debug-web.enabled}（禁用态 = 端点不注册）；运行态 runtime 判空
 * 兜底 503。鉴权/权限/沙箱/取消全在容器无关核心，本壳只做参数/头抽取与状态码映射。
 */
@RestController
@ConditionalOnProperty(name = "nop.xlang.debug-web.enabled", havingValue = "true")
public class NopXlangDebugReplWebService {

    @PostMapping(value = "/r/nop-xlang-debug/{action}", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> invoke(@PathVariable("action") String action,
                                         @RequestBody(required = false) String body) {
        DebugGatewayRuntime runtime = DebugGatewayRuntime.singleton();
        if (runtime == null || runtime.getReplService() == null)
            return json(HttpStatus.SERVICE_UNAVAILABLE, errorBody(null, "xlang debug web disabled"));

        Map<String, Object> params;
        try {
            params = body == null || body.isEmpty() ? Map.of() : JsonTool.parseMap(body);
        } catch (Exception e) {
            return json(HttpStatus.BAD_REQUEST, errorBody(null, "invalid json body"));
        }

        Map<String, Object> headers = extractHeaders();
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
                return json(HttpStatus.NOT_FOUND, errorBody(null, "unknown action: " + action));
            }
            return json(HttpStatus.OK, result);
        } catch (ReplAccessException e) {
            return json(HttpStatus.resolve(e.getHttpStatus()) == null ? HttpStatus.INTERNAL_SERVER_ERROR
                    : HttpStatus.valueOf(e.getHttpStatus()), errorBody(e.getErrorCode(), e.getResponseMessage()));
        } catch (Exception e) {
            return json(HttpStatus.INTERNAL_SERVER_ERROR,
                    errorBody(null, "internal error: " + e.getClass().getSimpleName()));
        }
    }

    static Map<String, Object> extractHeaders() {
        Map<String, Object> headers = new HashMap<>();
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest request = attrs.getRequest();
            Enumeration<String> names = request.getHeaderNames();
            while (names.hasMoreElements()) {
                String name = names.nextElement();
                String value = request.getHeader(name);
                if (name != null && value != null)
                    headers.put(name.toLowerCase(), value);
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

    private ResponseEntity<String> json(HttpStatus status, Map<String, Object> body) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON)
                .body(JsonTool.stringify(body));
    }
}

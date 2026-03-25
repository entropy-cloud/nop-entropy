package io.nop.ai.toolkit.tools;

import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolExecutor;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.api.core.util.FutureHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.http.api.HttpApiConstants;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpClient;
import io.nop.http.api.client.IHttpResponse;
import jakarta.inject.Inject;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;

public class HttpRequestExecutor implements IToolExecutor {
    public static final String TOOL_NAME = "http-request";

    private IHttpClient httpClient;

    @Inject
    public void setHttpClient(IHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public String getToolName() {
        return TOOL_NAME;
    }

    @Override
    public CompletionStage<AiToolCallResult> executeAsync(AiToolCall call, IToolExecuteContext context) {
        if (httpClient == null) {
            return FutureHelper.success(
                AiToolCallResult.errorResult(call.getId(), "HTTP client not available")
            );
        }

        String url = call.attrText("url", "");
        String method = call.attrText("method", "GET").toUpperCase();
        int timeoutMs = call.attrInt("timeoutMs", call.getTimeoutMs() != null ? call.getTimeoutMs() : 30000);

        if (url.isEmpty()) {
            return FutureHelper.success(
                AiToolCallResult.errorResult(call.getId(), "URL is required")
            );
        }

        return context.getExecutor().submit(() -> doExecute(call, url, method, timeoutMs));
    }

    private AiToolCallResult doExecute(AiToolCall call, String url, String method, int timeoutMs) {
        try {
            HttpRequest request = new HttpRequest();
            request.setUrl(url);
            request.setMethod(method);
            request.setTimeout(timeoutMs);

            Map<String, Object> headers = parseHeaders(call);
            if (headers != null && !headers.isEmpty()) {
                request.setHeaders(headers);
            }

            Map<String, Object> params = parseParams(call);
            if (params != null && !params.isEmpty()) {
                request.setParams(params);
            }

            String body = call.childText("body");
            if (body != null && !body.isEmpty()) {
                request.setBody(body);
            }

            applyAuth(call, request);

            IHttpResponse response = httpClient.fetch(request, null);

            return buildSuccessResult(call, response);
        } catch (Exception e) {
            return AiToolCallResult.errorResult(call.getId(), e.getMessage());
        }
    }

    private Map<String, Object> parseHeaders(AiToolCall call) {
        Map<String, Object> headers = new LinkedHashMap<>();
        XNode headersNode = call.childNode("headers");
        if (headersNode != null) {
            for (XNode headerNode : headersNode.getChildren()) {
                if ("header".equals(headerNode.getTagName())) {
                    String name = headerNode.attrText("name");
                    String value = headerNode.attrText("value");
                    if (name != null && value != null) {
                        headers.put(name, StringHelper.unescapeXml(value));
                    }
                }
            }
        }
        return headers;
    }

    private Map<String, Object> parseParams(AiToolCall call) {
        Map<String, Object> params = new LinkedHashMap<>();
        XNode paramsNode = call.childNode("params");
        if (paramsNode != null) {
            for (XNode paramNode : paramsNode.getChildren()) {
                if ("param".equals(paramNode.getTagName())) {
                    String name = paramNode.attrText("name");
                    String value = paramNode.attrText("value");
                    if (name != null) {
                        params.put(name, value != null ? StringHelper.unescapeXml(value) : "");
                    }
                }
            }
        }
        return params;
    }

    private void applyAuth(AiToolCall call, HttpRequest request) {
        XNode authNode = call.childNode("auth");
        if (authNode == null) return;

        String authType = authNode.attrText("type", "none");
        if ("basic".equals(authType)) {
            XNode usernameNode = authNode.childByTag("username");
            XNode passwordNode = authNode.childByTag("password");
            String username = usernameNode != null ? usernameNode.contentText() : "";
            String password = passwordNode != null ? passwordNode.contentText() : "";
            String credentials = Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
            request.header(HttpApiConstants.HEADER_AUTHORIZATION, "Basic " + credentials);
        } else if ("bearer".equals(authType)) {
            XNode tokenNode = authNode.childByTag("token");
            String token = tokenNode != null ? tokenNode.contentText() : "";
            if (!StringHelper.isEmpty(token)) {
                request.bearerToken(token);
            }
        }
    }

    private AiToolCallResult buildSuccessResult(AiToolCall call, IHttpResponse response) {
        int status = response.getHttpStatus();
        String body = response.getBodyAsString();
        Map<String, String> headers = response.getHeaders();

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"status\": ").append(status).append(",\n");
        sb.append("  \"headers\": {\n");
        if (headers != null) {
            boolean first = true;
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                if (!first) sb.append(",\n");
                first = false;
                sb.append("    \"").append(entry.getKey()).append("\": \"")
                        .append(StringHelper.escapeJson(entry.getValue())).append("\"");
            }
        }
        sb.append("\n  },\n");
        sb.append("  \"body\": ");
        if (body != null && body.trim().startsWith("{")) {
            sb.append(body);
        } else {
            sb.append("\"").append(body != null ? StringHelper.escapeJson(body) : "").append("\"");
        }
        sb.append("\n}");

        return AiToolCallResult.successResult(call.getId(), sb.toString());
    }
}

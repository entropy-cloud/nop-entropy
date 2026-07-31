package io.nop.ai.toolkit.tools;

import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolExecutor;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.api.core.util.FutureHelper;
import io.nop.commons.util.StringHelper;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpClient;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.regex.Pattern;

public class GraphqlQueryExecutor implements IToolExecutor {
    static final Logger LOG = LoggerFactory.getLogger(GraphqlQueryExecutor.class);
    public static final String TOOL_NAME = "graphql-query";

    private IHttpClient httpClient;

    @Inject
    public void setHttpClient(IHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    private String validateUrl(String url) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            if (host == null || host.isEmpty()) {
                return "No host in URL";
            }
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equals("http") && !scheme.equals("https"))) {
                return "Only http and https schemes are allowed";
            }
            String lowerHost = host.toLowerCase();
            String strippedHost = lowerHost.replaceAll("\\[|\\]", "");
            if (BLOCKED_HOSTS.contains(strippedHost)) {
                return "Blocked host: " + host;
            }
            if (strippedHost.equals("localhost") || isPrivateIp(strippedHost)) {
                return "Internal/private IP addresses are not allowed: " + host;
            }
            return null;
        } catch (Exception e) {
            LOG.warn("Invalid URL: {}", url, e);
            return "Invalid URL: " + e.toString();
        }
    }

    private boolean isPrivateIp(String host) {
        if (host == null) return false;
        return host.matches("^(127\\..*|10\\..*|172\\.(1[6-9]|2\\d|3[01])\\..*|192\\.168\\..*|0\\.0\\.0\\.0|::1|fc00:.*|fe80:.*|169\\.254\\..*)$");
    }

    @Override
    public String getToolName() {
        return TOOL_NAME;
    }

    private static final Pattern URL_PATTERN = Pattern.compile(
            "^https?://[a-zA-Z0-9.-]+(:\\d+)?(/.*)?$");

    private static final Set<String> BLOCKED_HOSTS = Set.of(
            "169.254.169.254", "169.254.170.2", "fd00:ec2::23", "100.100.100.200",
            "metadata.google.internal", "169.254.169.253"
    );

    @Override
    public CompletionStage<AiToolCallResult> executeAsync(AiToolCall call, IToolExecuteContext context) {
        if (httpClient == null) {
            return FutureHelper.success(
                AiToolCallResult.errorResult(call.getId(), "HTTP client not available")
            );
        }

        String query = call.childText("input", "");
        String endpoint = call.attrText("endpoint", null);
        int timeoutMs = call.attrInt("timeoutMs", call.getTimeoutMs() != null ? call.getTimeoutMs() : 30000);

        if (endpoint == null || endpoint.isEmpty()) {
            return FutureHelper.success(
                    AiToolCallResult.errorResult(call.getId(), "Endpoint URL is required")
            );
        }

        String validationError = validateUrl(endpoint);
        if (validationError != null) {
            return FutureHelper.success(
                    AiToolCallResult.errorResult(call.getId(), "Endpoint blocked: " + validationError)
            );
        }

        if (query.isEmpty()) {
            return FutureHelper.success(
                AiToolCallResult.errorResult(call.getId(), "GraphQL query is required")
            );
        }

        String requestBody = "{\"query\":" + StringHelper.escapeJson(query) + "}";

        HttpRequest request = new HttpRequest();
        request.setUrl(endpoint);
        request.setMethod("POST");
        request.setTimeout(timeoutMs);
        request.setBody(requestBody);
        request.header("Content-Type", "application/json");
        request.header("Accept", "application/json");

        return httpClient.fetchAsync(request, null)
            .thenApply(response -> {
                if (response.getHttpStatus() >= 400) {
                    return AiToolCallResult.errorResult(call.getId(),
                        "HTTP " + response.getHttpStatus() + ": " + response.getBodyAsString());
                }
                return AiToolCallResult.successResult(call.getId(), response.getBodyAsString());
            })
            .exceptionally(e -> AiToolCallResult.errorResult(call.getId(), e.getMessage()));
    }
}
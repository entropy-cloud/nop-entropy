package io.nop.ai.toolkit.tools;

import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolExecutor;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.ai.toolkit.tools.ssrf.SsrfAddressGuard;
import io.nop.api.core.util.FutureHelper;
import io.nop.commons.util.StringHelper;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpClient;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.concurrent.CompletionStage;

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
            return SsrfAddressGuard.validateHost(host);
        } catch (Exception e) {
            LOG.warn("Invalid URL: {}", url, e);
            return "Invalid URL: " + e;
        }
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
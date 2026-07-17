package io.nop.gateway.core.interceptor;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.gateway.GatewayRejectException;
import io.nop.gateway.core.context.IGatewayContext;
import io.nop.http.api.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class AiAuthGatewayInterceptor implements IGatewayInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(AiAuthGatewayInterceptor.class);

    private List<String> validKeys = new ArrayList<>();
    private List<String> skipPathPatterns = new ArrayList<>();

    public void setValidKeys(List<String> validKeys) {
        this.validKeys = validKeys;
    }

    public void setSkipPathPatterns(List<String> skipPathPatterns) {
        this.skipPathPatterns = skipPathPatterns;
    }

    @Override
    public ApiRequest<?> onRequest(ApiRequest<?> request, IGatewayContext svcCtx) {
        // 白名单路径跳过认证
        if (isSkipPath(svcCtx.getRequestPath())) {
            return request;
        }

        String authHeader = request.getHeaders() != null
                ? (String) request.getHeaders().get("Authorization")
                : null;

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            LOG.warn("Missing or invalid Authorization header");
            ApiResponse<?> rejected = ApiResponse.buildSuccess(null);
            rejected.setHttpStatus(HttpStatus.SC_UNAUTHORIZED);
            throw new GatewayRejectException(rejected);
        }

        String token = authHeader.substring(7).trim();
        if (!validKeys.contains(token)) {
            LOG.warn("Invalid API key: {}", token);
            ApiResponse<?> rejected = ApiResponse.buildSuccess(null);
            rejected.setHttpStatus(HttpStatus.SC_UNAUTHORIZED);
            throw new GatewayRejectException(rejected);
        }

        return request;
    }

    private boolean isSkipPath(String path) {
        if (skipPathPatterns == null || path == null) {
            return false;
        }
        for (String pattern : skipPathPatterns) {
            if (path.startsWith(pattern) || path.equals(pattern)) {
                return true;
            }
        }
        return false;
    }
}

package io.nop.gateway.core.interceptor;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.util.ApiHeaders;
import io.nop.gateway.GatewayRejectException;
import io.nop.gateway.core.context.IGatewayContext;
import io.nop.http.api.HttpStatus;
import io.nop.http.api.server.IHttpServerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class AiAuthGatewayInterceptor implements IGatewayInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(AiAuthGatewayInterceptor.class);

    private List<String> validKeys = new ArrayList<>();
    private List<String> skipPathPatterns = new ArrayList<>();

    public void setValidKeys(List<String> validKeys) {
        // null 归一为空集合：空集合 = 拒绝所有请求（fail-closed），避免 onRequest 中 NPE
        this.validKeys = validKeys == null ? new ArrayList<>() : validKeys;
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

        // header key 必须用小写读取：生产链路上 Vertx/Servlet 实现均已把 header key
        // 统一小写化（见 IHttpServerContext.HEADER_AUTHORIZATION 平台约定），混合大小写
        // 读取永远取到 null，会导致启用即全量 401
        String authHeader = ApiHeaders.getStringHeader(request.getHeaders(),
                IHttpServerContext.HEADER_AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            LOG.warn("Missing or invalid Authorization header");
            ApiResponse<?> rejected = ApiResponse.buildSuccess(null);
            rejected.setHttpStatus(HttpStatus.SC_UNAUTHORIZED);
            throw new GatewayRejectException(rejected);
        }

        String token = authHeader.substring(IHttpServerContext.BEARER_PREFIX.length()).trim();
        if (!validKeys.contains(token)) {
            // 凭证不得明文落日志：只记录前 4 位 + 长度，防日志聚合系统泄漏可用凭证
            LOG.warn("Invalid API key: {}", maskKey(token));
            ApiResponse<?> rejected = ApiResponse.buildSuccess(null);
            rejected.setHttpStatus(HttpStatus.SC_UNAUTHORIZED);
            throw new GatewayRejectException(rejected);
        }

        return request;
    }

    static String maskKey(String token) {
        if (token == null || token.isEmpty()) {
            return "***";
        }
        if (token.length() <= 8) {
            return "***len=" + token.length();
        }
        return token.substring(0, 4) + "***len=" + token.length();
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

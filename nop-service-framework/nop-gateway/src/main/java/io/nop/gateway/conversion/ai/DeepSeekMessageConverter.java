package io.nop.gateway.conversion.ai;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.gateway.conversion.IBackendMessageConverter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * DeepSeek 后端消息转换器。
 * <p>
 * DeepSeek 的 Chat Completions API 与 OpenAI 完全兼容（相同的 messages/choices 结构），
 * 因此请求和响应方向都采用透传语义：仅做防御性复制，保证调用链拿到的是独立副本，
 * 不修改客户端原始对象。模型名（如 deepseek-chat）也直接透传。
 */
public class DeepSeekMessageConverter implements IBackendMessageConverter {

    @Override
    public ApiRequest<?> toBackendRequest(ApiRequest<?> request) {
        ApiRequest<Object> backend = new ApiRequest<>();
        backend.setHeaders(request.getHeaders());
        backend.setData(copyData(request.getData()));
        return backend;
    }

    @Override
    public ApiResponse<?> toFrontendResponse(ApiResponse<?> backendResponse, ApiRequest<?> request) {
        ApiResponse<Object> frontend = new ApiResponse<>();
        frontend.setHttpStatus(backendResponse.getHttpStatus());
        frontend.setWrapper(backendResponse.isWrapper());
        frontend.setData(copyData(backendResponse.getData()));
        return frontend;
    }

    @Override
    public Map<String, Object> toFrontendStreamChunk(Map<String, Object> backendDelta, ApiRequest<?> request) {
        return backendDelta == null ? null : new LinkedHashMap<>(backendDelta);
    }

    @SuppressWarnings("unchecked")
    private static Object copyData(Object data) {
        if (data instanceof Map) {
            return new LinkedHashMap<>((Map<String, Object>) data);
        }
        return data;
    }
}

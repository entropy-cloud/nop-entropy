package io.nop.ai.gateway;

import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.ai.core.dialect.ILlmDialect;
import io.nop.ai.core.dialect.LlmDialectFactory;
import io.nop.ai.core.model.ApiStyle;
import io.nop.ai.core.model.LlmModel;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.core.lang.json.JsonTool;
import io.nop.gateway.conversion.IBackendMessageConverter;

import java.util.Map;

/**
 * 前后端 LLM 格式转换器。将客户端请求从前端格式转为 ChatRequest（标准内部模型），
 * 再用后端 dialect 转为 Provider 格式。响应反向处理。
 * <p>
 * 配置方式（IoC bean properties）：
 * <pre>
 * frontendLlm = ApiStyle.openai    （客户端发送的格式，默认 OpenAI）
 * backendLlm  = ApiStyle.anthropic （后端 Provider 使用的格式）
 * </pre>
 * 前端始终使用 OpenAI 消息结构（messages/tools/temperature 等）作为统一入口，
 * 后端通过 backendLlm 选择对应的 ILlmDialect 完成格式转换。
 */
public class AiDialectBackendMessageConverter implements IBackendMessageConverter {

    private ApiStyle frontendLlm = ApiStyle.openai;
    private ApiStyle backendLlm = ApiStyle.openai;

    public void setFrontendLlm(ApiStyle frontendLlm) {
        this.frontendLlm = frontendLlm;
    }

    public void setBackendLlm(ApiStyle backendLlm) {
        this.backendLlm = backendLlm;
    }

    @Override
    @SuppressWarnings("unchecked")
    public ApiRequest<?> toBackendRequest(ApiRequest<?> request) {
        Map<String, Object> data = (Map<String, Object>) request.getData();
        // 1. Frontend dialect parses request body → ChatRequest
        ILlmDialect frontendDialect = LlmDialectFactory.getDialect(frontendLlm);
        ChatRequest chatRequest = frontendDialect.parseRequestBody(data);
        // 2. Backend dialect builds provider-specific body
        ILlmDialect backendDialect = LlmDialectFactory.getDialect(backendLlm);
        String model = resolveModel(data);
        LlmModel config = new LlmModel();
        Map<String, Object> body = backendDialect.buildBody(chatRequest, config, null, model, false);
        ApiRequest<Map<String, Object>> backendReq = new ApiRequest<>();
        backendReq.setHeaders(request.getHeaders());
        backendReq.setData(body);
        return backendReq;
    }

    @Override
    @SuppressWarnings("unchecked")
    public ApiResponse<?> toFrontendResponse(ApiResponse<?> backendResponse, ApiRequest<?> request) {
        Map<String, Object> backendData = (Map<String, Object>) backendResponse.getData();
        ILlmDialect backendDialect = LlmDialectFactory.getDialect(backendLlm);
        LlmModel config = new LlmModel();
        ChatResponse chatResponse = backendDialect.parseResponse(JsonTool.serialize(backendData, false), config);
        ILlmDialect frontendDialect = LlmDialectFactory.getDialect(frontendLlm);
        return ApiResponse.success(frontendDialect.buildResponse(chatResponse));
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> toFrontendStreamChunk(Map<String, Object> backendDelta, ApiRequest<?> request) {
        ILlmDialect backendDialect = LlmDialectFactory.getDialect(backendLlm);
        ChatStreamChunk chunk = backendDialect.parseStreamChunk(JsonTool.serialize(backendDelta, false));
        if (chunk == null) return null;
        ILlmDialect frontendDialect = LlmDialectFactory.getDialect(frontendLlm);
        return frontendDialect.buildStreamChunk(chunk);
    }

    private String resolveModel(Map<String, Object> data) {
        if (data == null) return null;
        Object model = data.get("model");
        return model != null ? model.toString() : null;
    }

}

package io.nop.ai.gateway;

import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.ai.core.NopAiCoreErrors;
import io.nop.ai.core.NopAiCoreException;
import io.nop.ai.core.dialect.ILlmDialect;
import io.nop.ai.core.dialect.LlmDialectFactory;
import io.nop.ai.core.model.ApiStyle;
import io.nop.ai.core.model.LlmModel;
import io.nop.ai.core.service.LlmConfigHelper;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.core.lang.json.JsonTool;
import io.nop.gateway.conversion.IBackendMessageConverter;

import java.util.Map;

import static io.nop.ai.gateway.failover.FailoverConstants.PROP_API_STYLE;
import static io.nop.ai.gateway.failover.FailoverConstants.PROP_MODEL;
import static io.nop.ai.gateway.failover.FailoverConstants.PROP_PROVIDER;
import static io.nop.ai.gateway.failover.FailoverConstants.PROP_STREAM;

/**
 * 前后端 LLM 格式转换器。将客户端请求从前端格式转为 ChatRequest（标准内部模型），
 * 再用后端 dialect 转为 Provider 格式。响应反向处理。
 * <p>
 * 配置方式（IoC bean properties）：
 * <pre>
 * frontendLlm = ApiStyle.openai    （客户端发送的格式，默认 OpenAI）
 * backendLlm  = ApiStyle.anthropic （后端 Provider 使用的格式）
 * </pre>
 * 前端可为任意 dialect（openai/anthropic/gemini/ollama/responses）：请求方向经
 * frontendLlm 对应 dialect 的 {@link ILlmDialect#parseRequestBody} 解析为 ChatRequest，
 * 后端经 backendLlm 对应 dialect 完成格式转换；响应/流式方向反向同理。
 * <p>
 * <b>per-request 动态 dialect（W7，plan 2026-08-15-1116-3 Phase 3，GW-A4/B-9）</b>：
 * 候选切换后目标候选的 apiStyle/model/provider/stream 由拦截器写入
 * {@code ApiRequest.properties}（@JsonIgnore 通道，客户端不可注入、不转发给 provider），
 * 本类读取后覆盖固定 bean 属性：
 * <ul>
 *   <li>backend dialect：{@code nop.ai.gateway.failover.apiStyle} → {@link LlmDialectFactory}；
 *       无 per-request 信息 = 沿用 {@code backendLlm} bean 属性（零回归）。</li>
 *   <li>model：{@code nop.ai.gateway.failover.model}（Q2 路由覆盖语义）→ 覆盖请求体 model；
 *       无 = 沿用请求体 model。</li>
 *   <li>真实 config：{@code nop.ai.gateway.failover.provider} →
 *       {@link LlmConfigHelper#loadConfig}（含 apiStyle/errorMappings/defaultModel）替换
 *       {@code new LlmModel()} 空配置；无 = 沿用空配置路径（零回归）。</li>
 *   <li>stream 标志：{@code nop.ai.gateway.failover.stream} → stream=true 请求体生成；
 *       无 = false（既有非流式路径零回归）。</li>
 * </ul>
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
        // 2. Backend dialect builds provider-specific body（per-request 覆盖固定 bean 属性）
        ILlmDialect backendDialect = LlmDialectFactory.getDialect(resolveBackendStyle(request));
        String model = resolveRequestModel(request, data);
        LlmModel config = resolveConfig(request);
        boolean stream = resolveStream(request);
        Map<String, Object> body = backendDialect.buildBody(chatRequest, config, null, model, stream);
        ApiRequest<Map<String, Object>> backendReq = new ApiRequest<>();
        backendReq.setHeaders(request.getHeaders());
        backendReq.setData(body);
        return backendReq;
    }

    @Override
    @SuppressWarnings("unchecked")
    public ApiResponse<?> toFrontendResponse(ApiResponse<?> backendResponse, ApiRequest<?> request) {
        Map<String, Object> backendData = (Map<String, Object>) backendResponse.getData();
        ILlmDialect backendDialect = LlmDialectFactory.getDialect(resolveBackendStyle(request));
        LlmModel config = resolveConfig(request);
        ChatResponse chatResponse = backendDialect.parseResponse(JsonTool.serialize(backendData, false), config);
        ILlmDialect frontendDialect = LlmDialectFactory.getDialect(frontendLlm);
        return ApiResponse.success(frontendDialect.buildResponse(chatResponse));
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> toFrontendStreamChunk(Map<String, Object> backendDelta, ApiRequest<?> request) {
        // per-attempt 反向 dialect（GW-A5/B-10）：每次从 request 读当前 properties——
        // attempt 2 用 attempt 2 的 backend dialect（重执行回调已同步更新 properties）。
        ILlmDialect backendDialect = LlmDialectFactory.getDialect(resolveBackendStyle(request));
        ChatStreamChunk chunk = backendDialect.parseStreamChunk(JsonTool.serialize(backendDelta, false));
        if (chunk == null) return null;
        ILlmDialect frontendDialect = LlmDialectFactory.getDialect(frontendLlm);
        return frontendDialect.buildStreamChunk(chunk);
    }

    /**
     * per-request backend dialect：properties 中 {@code apiStyle} 优先；其次经 properties
     * {@code provider} 加载的真实 config 的 {@code apiStyle}（apiStyle 来自 provider 配置——
     * W7 Phase 3 测试断言面）；均无 = bean 属性（零回归）。非法枚举值 fail-loud（不静默回退）。
     */
    private ApiStyle resolveBackendStyle(ApiRequest<?> request) {
        String style = request != null ? request.getStringProperty(PROP_API_STYLE) : null;
        if (style == null || style.isEmpty()) {
            LlmModel config = resolveConfig(request);
            if (config.getApiStyle() != null) {
                return config.getApiStyle();
            }
            return backendLlm;
        }
        try {
            return ApiStyle.valueOf(style);
        } catch (IllegalArgumentException e) {
            throw new NopAiCoreException(NopAiCoreErrors.ERR_AI_AGENT_INVALID_ARG)
                    .param(NopAiCoreErrors.ARG_MSG, "invalid apiStyle property: " + style);
        }
    }

    /**
     * per-request model（Q2 路由覆盖）：properties 中 {@code model} 优先，无 = 请求体 model。
     */
    private String resolveRequestModel(ApiRequest<?> request, Map<String, Object> data) {
        String model = request != null ? request.getStringProperty(PROP_MODEL) : null;
        if (model != null && !model.isEmpty()) {
            return model;
        }
        return resolveModel(data);
    }

    /**
     * per-request 真实 config：properties 中 {@code provider} 非空时经
     * {@link LlmConfigHelper#loadConfig} 加载（含 apiStyle/errorMappings/defaultModel），
     * 替换 {@code new LlmModel()} 空配置；无 per-request provider = 空配置路径（零回归）。
     * provider 配置缺失 fail-loud（不静默吞）。
     */
    private LlmModel resolveConfig(ApiRequest<?> request) {
        String provider = request != null ? request.getStringProperty(PROP_PROVIDER) : null;
        if (provider == null || provider.isEmpty()) {
            return new LlmModel();
        }
        LlmModel config = LlmConfigHelper.loadConfig(provider);
        if (config == null) {
            throw new NopAiCoreException(NopAiCoreErrors.ERR_AI_AGENT_INVALID_ARG)
                    .param(NopAiCoreErrors.ARG_MSG, "no llm config for provider: " + provider);
        }
        return config;
    }

    /**
     * per-request stream 标志（W7 Phase 3）：properties 中 {@code stream} 非空时用之
     * （流式路径由拦截器写入 true）；无 = false（既有非流式路径零回归）。
     */
    private boolean resolveStream(ApiRequest<?> request) {
        Boolean stream = request != null ? request.getBooleanProperty(PROP_STREAM) : null;
        return stream != null && stream;
    }

    private String resolveModel(Map<String, Object> data) {
        if (data == null) return null;
        Object model = data.get("model");
        return model != null ? model.toString() : null;
    }

}

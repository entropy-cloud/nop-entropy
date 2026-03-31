package io.nop.gateway.conversion.ai;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.gateway.conversion.IBackendMessageConverter;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class AiBackendMessageConverter {
    private static final Map<AiBackendType, IBackendMessageConverter> CONVERTERS;

    static {
        Map<AiBackendType, IBackendMessageConverter> converters = new EnumMap<>(AiBackendType.class);
        converters.put(AiBackendType.OPENAI, new OpenAIMessageConverter());
        converters.put(AiBackendType.OLLAMA, new OllamaMessageConverter());
        converters.put(AiBackendType.CLAUDE, new ClaudeMessageConverter());
        CONVERTERS = Collections.unmodifiableMap(converters);
    }

    private AiBackendMessageConverter() {
    }

    public static ApiRequest<?> toBackendRequest(ApiRequest<?> request, AiBackendType backend) {
        return getConverter(backend).toBackendRequest(request);
    }

    public static ApiResponse<?> toOpenAIResponse(ApiResponse<?> backendResponse,
                                                   AiBackendType backend,
                                                   ApiRequest<?> request) {
        return getConverter(backend).toFrontendResponse(backendResponse, request);
    }

    public static Map<String, Object> toOpenAIStreamChunk(Map<String, Object> backendDelta,
                                                           AiBackendType backend,
                                                           ApiRequest<?> request) {
        return getConverter(backend).toFrontendStreamChunk(backendDelta, request);
    }

    private static IBackendMessageConverter getConverter(AiBackendType backend) {
        if (backend == null) {
            throw new IllegalArgumentException("Backend type is required");
        }
        IBackendMessageConverter converter = CONVERTERS.get(backend);
        if (converter == null) {
            throw new IllegalArgumentException("Unsupported backend: " + backend);
        }
        return converter;
    }
}

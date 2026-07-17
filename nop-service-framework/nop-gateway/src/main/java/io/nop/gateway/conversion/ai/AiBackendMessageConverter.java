package io.nop.gateway.conversion.ai;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.gateway.conversion.IBackendMessageConverter;

import java.util.Collections;
import java.util.Map;

/**
 * AI后端消息转换器工厂类。通过IoC容器发现所有注册的IBackendMessageConverter bean，
 * bean命名约定为 nopBackendMessageConverter_{AiBackendType}。
 * 新增Provider只需在beans.xml中注册对应bean，无需修改此工厂类。
 * <p>
 * IoC不可用时（如单元测试环境），回退到直接实例化默认转换器。
 */
public final class AiBackendMessageConverter {

    private static final Map<AiBackendType, IBackendMessageConverter> FALLBACK_CONVERTERS = java.util.Collections.emptyMap();

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

    public static IBackendMessageConverter getConverter(AiBackendType backend) {
        if (backend == null) {
            throw new IllegalArgumentException("Backend type is required");
        }
        try {
            Map<String, IBackendMessageConverter> converters = BeanContainer.instance().getBeansOfType(IBackendMessageConverter.class);
            String beanName = "nopBackendMessageConverter_" + backend.name();
            IBackendMessageConverter converter = converters.get(beanName);
            if (converter != null)
                return converter;
        } catch (Exception e) {
            // IoC不可用，回退到默认转换器
        }
        IBackendMessageConverter fallback = FALLBACK_CONVERTERS.get(backend);
        if (fallback == null) {
            throw new IllegalArgumentException("Unsupported backend: " + backend);
        }
        return fallback;
    }
}

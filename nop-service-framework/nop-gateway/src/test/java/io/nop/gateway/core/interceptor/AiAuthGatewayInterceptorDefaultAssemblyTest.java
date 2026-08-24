package io.nop.gateway.core.interceptor;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.ioc.IBeanContainer;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.gateway.GatewayRejectException;
import io.nop.gateway.core.context.GatewayContextImpl;
import io.nop.gateway.core.context.IGatewayContext;
import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.ioc.loader.BeanContainerBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.nop.core.unittest.BaseTestCase.clearTestConfig;
import static io.nop.core.unittest.BaseTestCase.setTestConfig;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 回归测试（CWE-798）：gateway-defaults.beans.xml 中 nopAiAuthGatewayInterceptor 的默认装配
 * 不得内置任何硬编码 API Key。未配置 nop.gateway.ai-auth.valid-keys 时必须拒绝所有请求（fail-closed）。
 */
class AiAuthGatewayInterceptorDefaultAssemblyTest {

    private static final String CONFIG_VALID_KEYS = "nop.gateway.ai-auth.valid-keys";

    @BeforeAll
    static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private AiAuthGatewayInterceptor loadDefaultInterceptor() {
        // 从生产装配文件 gateway-defaults.beans.xml 构建独立容器；
        // 额外挂一个 IHttpClient 占位 bean 以满足 AiFailoverGatewayInterceptor 的按类型自动装配
        //（真实应用中该 bean 由宿主应用提供）。
        BeanContainerBuilder builder = new BeanContainerBuilder((IBeanContainer) null);
        builder.addResource(VirtualFileSystem.instance()
                .getResource("/nop/test/beans/test-gateway-http-client.beans.xml"));
        builder.addResource(VirtualFileSystem.instance()
                .getResource("/nop/gateway/beans/gateway-defaults.beans.xml"));
        IBeanContainerImplementor container = builder.build("test");
        container.start();
        try {
            return (AiAuthGatewayInterceptor) container.getBean("nopAiAuthGatewayInterceptor");
        } finally {
            container.stop();
        }
    }

    private ApiRequest<Map<String, String>> requestWithBearer(String token) {
        ApiRequest<Map<String, String>> request = ApiRequest.build(Map.of());
        request.setHeaders(Map.of("authorization", "Bearer " + token));
        return request;
    }

    private IGatewayContext createContext() {
        GatewayContextImpl ctx = new GatewayContextImpl();
        ctx.setRequestPath("/v1/chat/completions");
        ctx.setRequest(ApiRequest.build(Map.of()));
        return ctx;
    }

    @Test
    void defaultAssembly_withoutConfig_rejectsFormerlyHardcodedKeys() {
        AiAuthGatewayInterceptor interceptor = loadDefaultInterceptor();
        assertNotNull(interceptor);

        // sk-test-key-1/2 曾硬编码于默认装配；修复后未配置 validKeys 时必须拒绝（fail-closed）
        assertThrows(GatewayRejectException.class,
                () -> interceptor.onRequest(requestWithBearer("sk-test-key-1"), createContext()));
        assertThrows(GatewayRejectException.class,
                () -> interceptor.onRequest(requestWithBearer("sk-test-key-2"), createContext()));
    }

    @Test
    void defaultAssembly_withConfiguredKeys_honorsConfig() {
        setTestConfig(CONFIG_VALID_KEYS, "sk-my-app-key");
        try {
            AiAuthGatewayInterceptor interceptor = loadDefaultInterceptor();

            assertDoesNotThrow(() -> interceptor.onRequest(requestWithBearer("sk-my-app-key"), createContext()));
            assertThrows(GatewayRejectException.class,
                    () -> interceptor.onRequest(requestWithBearer("sk-test-key-1"), createContext()));
        } finally {
            clearTestConfig(CONFIG_VALID_KEYS);
        }
    }
}

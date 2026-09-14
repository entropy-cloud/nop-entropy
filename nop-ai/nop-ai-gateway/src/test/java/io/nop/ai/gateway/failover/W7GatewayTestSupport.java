package io.nop.ai.gateway.failover;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.api.core.ioc.IBeanContainer;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.gateway.core.context.GatewayContextImpl;
import io.nop.gateway.core.context.IGatewayContext;
import io.nop.gateway.impl.GatewayHandler;
import io.nop.gateway.model.GatewayModel;
import io.nop.http.api.client.IHttpClient;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * W7 Phase 5 网关形态测试共享 harness（plan 2026-08-15-1116-3）。
 *
 * <p><b>容器策略</b>：{@code GatewayInterceptorModel.getOrCreateInterceptor} 与
 * {@code RouteExecutor.getConverter} 经全局 {@link BeanContainer} 解析 bean——这些测试需要
 * per-test 拦截器实例（每测试独立 breaker/registry/metrics 断言），故注册一个
 * <b>最小 IBeanContainer</b>（仅 {@code nopAiGatewayFailoverInterceptor} +
 * {@code nopBackendMessageConverter_AI_DIALECT}，实例经 per-test holder 提供）作为全局容器。
 * 初始化级别用 {@code initializeTo(INITIALIZER_PRIORITY_REGISTER_COMPONENT)}——VFS/config/组件注册
 * （含网关模型与 llm 配置加载）全部就绪，但 IoC 初始器（会构建含 autoconfig 的完整 app 容器，
 * 自 M7-P1 起已含本模块生产 bean：/nop/autoconfig/nop-ai-gateway.beans）不运行，避免 app 容器
 * 的网关 bean 遮蔽 per-test holder。未知 bean 访问 fail-fast（不静默 null）。
 *
 * <p><b>网关模型</b>：`/nop/test/w7-failover.gateway.xml`（VFS 测试资源），拦截器经
 * {@code <interceptors><interceptor bean=...>} 挂载（M-7 挂载契约）。路由遵守 F1
 * （无 requestMapping/onRequest xpl）与 B-18（无 route 级 onError 吞错者）前提。
 */
final class W7GatewayTestSupport {

    private W7GatewayTestSupport() {
    }

    /** per-test bean holder（@BeforeEach 刷新）。 */
    static final Map<String, Supplier<Object>> BEANS = new HashMap<>();
    private static final AtomicBoolean containerRegistered = new AtomicBoolean();

    static synchronized void init() {
        if (containerRegistered.compareAndSet(false, true)) {
            BeanContainer.registerInstance(new MinimalContainer());
        }
        // initializeTo(INITIALIZER_PRIORITY_REGISTER_COMPONENT): VFS/config/
        // component registration run, but the IoC initializer does NOT — the
        // full app container (which since M7-P1 includes the gateway's own
        // beans via /nop/autoconfig/nop-ai-gateway.beans) is never built, so
        // the per-test holder stays authoritative for bean-name resolution.
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    static void destroy() {
        CoreInitialization.destroy();
    }

    static GatewayHandler buildHandler(IHttpClient httpClient) {
        GatewayModel model = (GatewayModel) ResourceComponentManager.instance()
                .loadComponentModel("/nop/test/w7-failover.gateway.xml");
        // 模型经 ResourceComponentManager 缓存共享（冻结），而 GatewayInterceptorModel
        // getOrCreateInterceptor 会把解析出的拦截器实例缓存到模型上——每测试重置，
        // 防止跨测试串用上一测试的拦截器（含其 breaker/registry/retryBudget）。
        if (model.getInterceptors() != null) {
            for (io.nop.gateway.model.GatewayInterceptorModel interceptorModel : model.getInterceptors()) {
                interceptorModel.setInterceptor(null);
            }
        }
        return new GatewayHandler(model, null, httpClient, null, null);
    }

    static IGatewayContext context(ApiRequest<?> request, String path) {
        GatewayContextImpl ctx = new GatewayContextImpl();
        ctx.setRequest(request);
        ctx.setRequestPath(path);
        ctx.setQueryParams(new HashMap<>());
        ctx.setHttpMethod("POST");
        return ctx;
    }

    static ApiRequest<Object> aiRequest(String model, String userMsg) {
        ApiRequest<Object> request = new ApiRequest<>();
        request.setData(Map.of(
                "model", model,
                "messages", List.of(Map.of("role", "user", "content", userMsg))));
        return request;
    }

    /**
     * 收集型流式订阅者（客户端输出侧）：Map 元素提取 choices[0].delta.content；
     * ApiResponse 元素（降级终止）单独记录；终态 await。
     */
    static final class StreamCollector implements Flow.Subscriber<Object> {
        final List<String> texts = new ArrayList<>();
        final List<ApiResponse<?>> degraded = new ArrayList<>();
        Throwable error;
        boolean completed;
        private final CompletableFuture<Void> done = new CompletableFuture<>();

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void onNext(Object item) {
            if (item instanceof ApiResponse) {
                degraded.add((ApiResponse<?>) item);
            } else if (item instanceof Map) {
                Object delta = contentOf((Map<String, Object>) item);
                texts.add(delta != null ? delta.toString() : null);
            } else {
                texts.add(String.valueOf(item));
            }
        }

        @Override
        public void onError(Throwable throwable) {
            this.error = throwable;
            done.complete(null);
        }

        @Override
        public void onComplete() {
            this.completed = true;
            done.complete(null);
        }

        void await() {
            try {
                done.get(15, TimeUnit.SECONDS);
            } catch (Exception e) {
                fail("stream did not terminate: " + e);
            }
        }

        @SuppressWarnings("unchecked")
        private static Object contentOf(Map<String, Object> item) {
            Object choices = item.get("choices");
            if (choices instanceof List && !((List<?>) choices).isEmpty()) {
                Object choice = ((List<?>) choices).get(0);
                if (choice instanceof Map) {
                    Object delta = ((Map<String, Object>) choice).get("delta");
                    if (delta instanceof Map) {
                        return ((Map<String, Object>) delta).get("content");
                    }
                }
            }
            return null;
        }
    }

    /**
     * 最小 bean 容器：只解析测试 holder 中登记的 bean；未知 bean fail-fast。
     */
    private static final class MinimalContainer implements IBeanContainer {

        @Override
        public String getId() {
            return "w7-test-container";
        }

        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }

        @Override
        public void restart() {
        }

        @Override
        public boolean containsBean(String name) {
            return BEANS.containsKey(name);
        }

        @Override
        public boolean isRunning() {
            return true;
        }

        @Override
        public Object getBean(String name) {
            Supplier<Object> supplier = BEANS.get(name);
            if (supplier == null) {
                throw new IllegalStateException("unknown bean in w7 test container: " + name);
            }
            return supplier.get();
        }

        @Override
        public boolean containsBeanType(Class<?> clazz) {
            return false;
        }

        @Override
        public <T> T getBeanByType(Class<T> clazz) {
            throw new IllegalStateException("getBeanByType not supported in w7 test container: " + clazz);
        }

        @Override
        public <T> T tryGetBeanByType(Class<T> clazz) {
            return null;
        }

        @Override
        public <T> Map<String, T> getBeansOfType(Class<T> clazz) {
            throw new IllegalStateException("getBeansOfType not supported in w7 test container");
        }

        @Override
        public Map<String, Object> getBeansWithAnnotation(Class<? extends Annotation> annClass) {
            throw new IllegalStateException("getBeansWithAnnotation not supported in w7 test container");
        }

        @Override
        public String getBeanScope(String name) {
            return "singleton";
        }

        @Override
        public Class<?> getBeanClass(String name) {
            Object bean = getBean(name);
            return bean != null ? bean.getClass() : null;
        }

        @Override
        public String findAutowireCandidate(Class<?> beanType) {
            return null;
        }
    }
}

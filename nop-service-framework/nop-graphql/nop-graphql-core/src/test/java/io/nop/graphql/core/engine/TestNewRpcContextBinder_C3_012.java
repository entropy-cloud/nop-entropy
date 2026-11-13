package io.nop.graphql.core.engine;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.graphql.GraphQLReturn;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.context.IContext;
import io.nop.api.core.context.BaseContextProvider;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.i18n.I18nMessageManager;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.ast.GraphQLOperationType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA 2.3-C3-012（plan 2499 Phase 3，裁定③内置统一接入形态）：五入口
 * （gRPC/quarkus WS/spring WS/消息/文件）共用的统一 binder chokepoint =
 * {@code IGraphQLEngine.newRpcContext} 内置 binder 等价语义。
 * <p>
 * 语义与 ContextBinder 同构：线程已有 open context 则复用（HTTP filter 链入口不二次建
 * context 分叉、不重复 init），无则按请求头新建并附着（tenant/locale/timezone/callExpireTime/
 * propagateHeaders），随请求终态关闭。五个入口调用点对引擎入口零改码（nop-spring 第五入口
 * 断言见 nop-spring-web-starter TestSpringWsEntryContextBinding_C3_012）。
 * <p>
 * 本文件同时承载 CRA 2.3-C3-017 的 locale 读点判别用例：buildRpcResponse 的错误消息
 * 本地化必须优先取请求自身 context 的 locale（context 优先、线程读取降级兜底）。
 */
public class TestNewRpcContextBinder_C3_012 {

    /** C3-017 locale 判别用专用错误码（en-US/zh-CN 双语注册，消息文本可区分 locale 来源） */
    static final ErrorCode ERR_LOCALE_PROBE = ErrorCode.define("nop.err.test.locale-probe-c3-017",
            "默认消息文本", "arg");

    GraphQLEngine engine;

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
        I18nMessageManager.instance().registerMessages("en-US",
                Map.of("nop.err.test.locale-probe-c3-017", "EN-locale-probe-message"));
        I18nMessageManager.instance().registerMessages("zh-CN",
                Map.of("nop.err.test.locale-probe-c3-017", "ZH-locale-probe-message"));
    }

    @AfterAll
    public static void destroy() {
        I18nMessageManager.instance().clearI18nMessages("en-US");
        I18nMessageManager.instance().clearI18nMessages("zh-CN");
        CoreInitialization.destroy();
    }

    @BeforeEach
    public void setUp() {
        // 双向隔离：全量套件中其他测试类可能在线程上遗留 open context 残留（其
        // ServiceContextImpl 字段初始化自动附着），本类判别面要求无线附着前置
        BaseContextProvider.clear();
        engine = new GraphQLEngine();
        engine.setSchemaLoader(new MockGraphQLSchemaLoader(List.of(new BinderProbeBizModel())));
        engine.init();
    }

    @AfterEach
    public void tearDown() {
        BaseContextProvider.clear();
    }

    @SuppressWarnings("unchecked")
    private static ApiRequest<Object> requestWithHeaders(Map<String, Object> headers) {
        ApiRequest<Object> req = new ApiRequest<>();
        req.setData(Map.of("id", "1"));
        if (headers != null)
            headers.forEach(req::setHeader);
        return req;
    }

    @BizModel("Tree")
    public static class BinderProbeBizModel {

        @BizQuery("get")
        @GraphQLReturn(bizObjName = "Tree")
        public BinderTree get(@Name("id") String id) {
            BinderTree t = new BinderTree();
            t.setId(id);
            return t;
        }
    }

    public static class BinderTree {
        private String id;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }
    }

    /**
     * 无线程 context（gRPC/WS/消息/文件入口形态）：binder 按请求头新建并附着 context，
     * tenant/locale 落盘；执行上下文捕获的即是该 context；请求终态（complete）关闭。
     */
    @Test
    public void testBinderCreatesContextFromRequestHeadersWhenNoThreadContext() {
        ApiRequest<Object> req = requestWithHeaders(Map.of(
                "nop-tenant", "tenant-c3-012",
                "nop-locale", "en-US",
                "nop-timezone", "GMT+8"));

        IGraphQLExecutionContext ctx = engine.newRpcContext(GraphQLOperationType.query,
                "Tree__get", req);

        IContext bound = ContextProvider.currentContext();
        assertNotNull(bound, "binder must attach the created context to the calling thread");
        assertSame(bound, ctx.getContext(),
                "execution context must capture the binder-created context");
        assertEquals("tenant-c3-012", bound.getTenantId(), "tenant must land from request header");
        assertEquals("en-US", bound.getLocale(), "locale must land from request header");

        // 请求终态关闭：随执行上下文 complete 而关闭，closed 残留对平台读点不可观测
        // （平台根因 isClosed 守卫惰性清除）
        ctx.complete();
        assertTrue(bound.isClosed(), "binder-created context must close at request completion");
        assertNull(ContextProvider.currentContext(),
                "closed residue must be unobservable via the platform read guard");
    }

    /**
     * 线程已有 open context（HTTP filter 链入口形态）：binder 复用不二次建 context 分叉、
     * 不重复 init（既有入口已落盘的头部不被请求头覆盖），且不复初始化、请求终态不关闭
     * 非自建 context。
     */
    @Test
    public void testBinderReusesOpenThreadContextWithoutReinit() {
        IContext existing = ContextProvider.newContext(true);
        existing.setTenantId("tenant-keep-existing");
        try {
            ApiRequest<Object> req = requestWithHeaders(Map.of(
                    "nop-tenant", "tenant-should-not-overwrite",
                    "nop-locale", "zh-CN"));

            IGraphQLExecutionContext ctx = engine.newRpcContext(GraphQLOperationType.query,
                    "Tree__get", req);

            assertSame(existing, ctx.getContext(),
                    "binder must reuse the open thread context (no second-context fork)");
            assertEquals("tenant-keep-existing", existing.getTenantId(),
                    "open context must not be re-initialized from request headers");
            assertFalse(existing.isClosed());

            // 请求终态只关闭自建 context，复用的既有 context 生命周期归其入口所有
            ctx.complete();
            assertFalse(existing.isClosed(), "binder must not close a context it did not create");
        } finally {
            existing.close();
        }
    }

    /**
     * 入口失败路径（未知 operation 等）：binder 自建 context 立即关闭回收，不在线程残留
     * open context（rule 24：失败可见非静默泄漏）。
     */
    @Test
    public void testBinderClosesCreatedContextWhenEntrySetupFails() {
        ApiRequest<Object> req = requestWithHeaders(Map.of("nop-tenant", "tenant-c3-012-err"));

        assertThrows(NopException.class, () -> engine.newRpcContext(GraphQLOperationType.query,
                "Unknown__operation", req));

        assertNull(ContextProvider.currentContext(),
                "failed entry setup must not leave an attached context behind");
    }

    /**
     * CRA 2.3-C3-017：buildRpcResponse 错误消息本地化 context 优先——执行上下文携带
     * en-US，线程残留 open context 携带 zh-CN（异步完成线程残留形态）。修复前读线程
     * locale → ZH 文本（红）；修复后 context 优先 → EN 文本（绿）。
     */
    @Test
    public void testBuildRpcResponseLocalePrefersRequestContextOverThreadResidue() {
        ApiRequest<Object> req = requestWithHeaders(Map.of("nop-locale", "en-US"));

        // 线程残留：其他请求的 open context（zh-CN）——binder 复用分支不触发（先清残，
        // 由 binder 建执行上下文后再模拟完成线程的残留附着）
        BaseContextProvider.clear();
        IGraphQLExecutionContext ctx = engine.newRpcContext(GraphQLOperationType.query,
                "Tree__get", req);
        IContext bound = ctx.getContext();
        assertEquals("en-US", bound.getLocale());

        // 模拟异步完成线程的 open 残留（其他请求的 context 附着在本线程）
        IContext residue = ContextProvider.newContext(true);
        residue.setLocale("zh-CN");

        try {
            ApiResponse<?> res = engine.buildRpcResponse(null,
                    new NopException(ERR_LOCALE_PROBE), ctx);
            assertEquals("EN-locale-probe-message", res.getMsg(),
                    "error message must be localized by the request context locale (context-first)");
        } finally {
            residue.close();
        }
    }
}

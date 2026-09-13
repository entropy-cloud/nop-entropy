package io.nop.ai.gateway.failover;

/**
 * W7 网关形态 failover 常量（plan 2026-08-15-1116-3）。
 *
 * <p>per-request 信息经 {@link io.nop.api.core.beans.ApiRequest#setProperty} 通道传递
 * （Phase 1 GW-A4/B-9：@JsonIgnore 不序列化、客户端不可注入、不转发给 provider）——
 * baseUrl 覆盖键复用 nop-gateway 的 {@code GatewayStreamingConstants.PROP_BASE_URL}
 * （{@code nop.gateway.streaming.baseUrl}，Phase 1 GW-A7/B-14）。
 */
public final class FailoverConstants {

    /** 选中候选 provider（converter 真实 config 加载 + 并发计数键）。 */
    public static final String PROP_PROVIDER = "nop.ai.gateway.failover.provider";

    /** 选中候选 model（Q2 路由覆盖语义，覆盖请求体 model）。 */
    public static final String PROP_MODEL = "nop.ai.gateway.failover.model";

    /** 选中候选 accountKey（备用账号 apiKey / 主账号 null；并发计数键）。 */
    public static final String PROP_ACCOUNT_KEY = "nop.ai.gateway.failover.accountKey";

    /** 选中候选 backend apiStyle（converter 动态 dialect + 反向转换 per-attempt dialect）。 */
    public static final String PROP_API_STYLE = "nop.ai.gateway.failover.apiStyle";

    /** 流式标志（stream=true 请求体生成；仅流式路径由拦截器写入）。 */
    public static final String PROP_STREAM = "nop.ai.gateway.failover.stream";

    /** attempt 计数（重试预算记账；onRequest 置 1，重执行回调递增）。 */
    public static final String PROP_ATTEMPT = "nop.ai.gateway.failover.attempt";

    /** 接管标记：拦截器选中候选并下沉后置 true（onError/onStreamElement 只对接管请求生效）。 */
    public static final String PROP_ACTIVE = "nop.ai.gateway.failover.active";

    /** context attribute：本轮共享 {@link io.nop.ai.core.routing.ModelClassRouter}。 */
    public static final String ATTR_ROUTER = "nop.ai.gateway.failover.router";

    /** HTTP 429 Too Many Requests（RATE_LIMITED 分类）。 */
    public static final int HTTP_TOO_MANY_REQUESTS = 429;

    /** HTTP 401 Unauthorized（AUTH_INVALID 分类）。 */
    public static final int HTTP_UNAUTHORIZED = 401;

    /** HTTP 403 Forbidden（AUTH_INVALID 分类）。 */
    public static final int HTTP_FORBIDDEN = 403;

    /** HTTP 5xx 服务器错误区间下界（TRANSIENT 分类，含）。 */
    public static final int HTTP_SERVER_ERROR_MIN = 500;

    /** HTTP 5xx 服务器错误区间上界（不含）。 */
    public static final int HTTP_SERVER_ERROR_MAX = 600;

    /** HTTP 4xx 客户端错误区间下界（NON_TRANSIENT 分类，含）。 */
    public static final int HTTP_CLIENT_ERROR_MIN = 400;

    /** HTTP 4xx 客户端错误区间上界（不含）。 */
    public static final int HTTP_CLIENT_ERROR_MAX = 500;

    private FailoverConstants() {
    }
}

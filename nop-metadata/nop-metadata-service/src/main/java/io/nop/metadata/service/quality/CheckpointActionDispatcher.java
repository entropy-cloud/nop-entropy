
package io.nop.metadata.service.quality;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.message.IMessageService;
import io.nop.metadata.service.NopMetadataHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpClient;
import io.nop.http.api.client.IHttpResponse;
import io.nop.metadata.core._NopMetadataCoreConstants;
import io.nop.metadata.dao.entity.NopMetaQualityCheckpoint;
import io.nop.metadata.service.NopMetadataErrors;
import io.nop.metadata.service.NopMetadataException;
import io.nop.metadata.service.security.HostSecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 检查点执行结果动作分发器（架构基线 §2.7.3 D4）。在 BizModel 层（executor 返回摘要后）按 {@code actions}
 * 配置向外部投递执行摘要：{@code webhook}（HTTP POST 经 {@link IHttpClient}）与 {@code notify}（消息通道经
 * {@link IMessageService}）。{@code store} 由 executor 隐式完成（写 QualityResult 行），本分发器不重复处理。
 *
 * <p><b>事务隔离（post-commit）</b>：本分发器的设计预期是「store 提交后才投递」。调用方（BizModel）经
 * {@code ITransactionListener.onAfterCommit} 在事务成功提交后调用 {@link #dispatch}，因此投递失败/超时
 * 不可能回滚已落盘的 store，HTTP/消息调用也不占用 store 事务。若运行时无活跃事务，BizModel 退化为 execute
 * 返回后同步调用本方法（per-action try/catch 兜底，仍保证投递失败不阻断返回）。
 *
 * <p><b>per-action 隔离</b>：每个 action 独立 try/catch，单个投递失败记入摘要 {@code errors}（{@code source=actionDispatch}），
 * 不中断其他动作投递。{@code IHttpClient}/{@code IMessageService} 为 null（宿主未注册实现）时对应动作显式失败抛
 * ErrorCode（不 NPE、不静默跳过）。
 *
 * <p>无状态（依赖由构造时传入的 {@link IHttpClient} + {@link IMessageService}，均可为 null）。
 */
public class CheckpointActionDispatcher {

    private static final Logger LOG = LoggerFactory.getLogger(CheckpointActionDispatcher.class);


    /**
     * 维度13-04：webhook URL 被 SSRF 防护策略拒绝（协议非白名单 / 内网主机未在 allowed-hosts / method 非白名单）。
     * fail-closed 策略：默认禁内网（RFC1918 + 169.254 + localhost），允许的外网 webhook 主机需显式配置。
     */
    /**
     * 维度13-04：webhook method 不在白名单（仅允许 POST/PUT，避免 GET 触发副作用、TRACE 泄露等）。
     */

    /** 维度13-04：webhook 允许的 URL 协议白名单（http/https）。 */
    private static final Set<String> ALLOWED_WEBHOOK_PROTOCOLS = unmodifiableSet("http://", "https://");

    /** 维度13-04：webhook 允许的 HTTP method 白名单（POST/PUT）。GET 易触发副作用/CSRF；TRACE/DEBUG 易泄露。 */
    public static final Set<String> ALLOWED_WEBHOOK_METHODS = unmodifiableSet("POST", "PUT");

    /** 维度13-04：默认 webhook 超时（秒）。 */
    public static final int DEFAULT_WEBHOOK_TIMEOUT_SECONDS = 30;

    private static Set<String> unmodifiableSet(String... items) {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(items)));
    }

    private final IHttpClient httpClient;
    private final IMessageService messageService;

    /**
     * 维度13-04：可配置的允许内网主机集合（小写 host，逗号分隔）。
     * 默认空：fail-closed 拒绝内网（部署 webhook 必须先配 {@code nop.metadata.checkpoint.webhook-allowed-hosts}）。
     * BizModel 经 {@code @InjectValue} 读取并注入；测试时 setter 覆盖。
     */
    protected String webhookAllowedHostsCsv = "";

    /** 维度13-04：webhook 超时（秒），默认 30s。BizModel 经 {@code @InjectValue} 注入；测试时 setter 覆盖。 */
    protected int webhookTimeoutSeconds = DEFAULT_WEBHOOK_TIMEOUT_SECONDS;

    /**
     * 维度13-04（R6.2）：平台全局重定向跟随开关镜像（{@code nop.http.client.follow-redirects}）。
     * 默认 false（与平台默认一致）；显式 true 时 dispatchWebhook 在 fetch 前 fail-closed 拒绝——webhook
     * 路径不允许跟随重定向（跳转目标不经 {@link #validateWebhookUrl} 复核，经典重定向 SSRF 绕过面）。
     * BizModel 经 {@code @InjectValue} 注入；测试时 setter 覆盖。
     */
    protected boolean followRedirectsEnabled = false;

    public CheckpointActionDispatcher(IHttpClient httpClient, IMessageService messageService) {
        this.httpClient = httpClient;
        this.messageService = messageService;
    }

    /**
     * 维度13-04：允许显式配置 webhook 内网 host allowlist + timeout（BizModel 经 {@code @InjectValue} 读取后注入）。
     */
    public void configureWebhookSsrf(String allowedHostsCsv, int timeoutSeconds) {
        this.webhookAllowedHostsCsv = allowedHostsCsv == null ? "" : allowedHostsCsv;
        this.webhookTimeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : DEFAULT_WEBHOOK_TIMEOUT_SECONDS;
    }

    /**
     * 维度13-04（R6.2）：显式配置 webhook 重定向跟随策略（fail-closed）。
     *
     * <p>{@code followRedirectsEnabled} 镜像平台全局 {@code nop.http.client.follow-redirects} 配置：
     * 部署开启全局跟随（true）时，dispatchWebhook 在 fetch 前显式拒绝投递（不依赖 HttpClientConfig 默认值、
     * 不产生未复核跳转）；false/缺省（默认）放行——此时重定向跟随被平台客户端禁用，3xx 响应由
     * {@code dispatchWebhook} 的显式 3xx 拒绝分支兜底。新增 setter 对既有 4 处 2 参
     * {@code configureWebhookSsrf(url, timeout)} 调用点零影响。
     */
    public void configureRedirectPolicy(boolean followRedirectsEnabled) {
        this.followRedirectsEnabled = followRedirectsEnabled;
    }

    /** 维度13-04：解析后的允许内网主机集合（小写）。 */
    protected Set<String> resolveAllowedWebhookHosts() {
        if (webhookAllowedHostsCsv == null || webhookAllowedHostsCsv.trim().isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> set = new HashSet<>();
        for (String token : webhookAllowedHostsCsv.split(",")) {
            String trimmed = token.trim().toLowerCase();
            if (!trimmed.isEmpty()) {
                set.add(trimmed);
            }
        }
        return set;
    }

    /**
     * 按 {@code actions} 配置逐条分发。已 enabled 的 store 跳过（executor 已完成）；webhook/notify 投递执行摘要；
     * 未知 actionType 不应到达（executor 已 validate），defense-in-depth 跳过。per-action try/catch 隔离。
     *
     * @param cp     检查点（非 null，提供 actions 配置与 checkpointId）
     * @param summary 执行摘要（投递 payload；errors 列表会被追加 actionDispatch 错误条目）
     */
    @SuppressWarnings("unchecked")
    public void dispatch(NopMetaQualityCheckpoint cp, Map<String, Object> summary) {
        String actionsJson = cp.getActions();
        if (actionsJson == null || actionsJson.trim().isEmpty()) {
            return;
        }
        List<Object> actionList;
        try {
            Object parsed = JsonTool.parse(actionsJson);
            if (!(parsed instanceof List)) {
                return;
            }
            actionList = (List<Object>) parsed;
        } catch (Exception e) {
            LOG.error("actions JSON parse failed (should have been validated): checkpointId={}", cp.getCheckpointId(), e);
            return;
        }

        List<Map<String, Object>> errors = obtainErrors(summary);

        for (Object o : actionList) {
            if (!(o instanceof Map)) {
                continue;
            }
            Map<String, Object> action = (Map<String, Object>) o;
            String actionType = String.valueOf(action.get("actionType"));
            boolean enabled = !Boolean.FALSE.equals(action.get("enabled"));
            if (!enabled) {
                continue;
            }

            Object configObj = action.get("config");
            Map<String, Object> config = (configObj instanceof Map) ? (Map<String, Object>) configObj : null;

            try {
                if (_NopMetadataCoreConstants.CHECKPOINT_ACTION_TYPE_STORE.equals(actionType)) {
                    // store 由 executor 隐式完成（写 QualityResult 行），此处不重复处理
                } else if (_NopMetadataCoreConstants.CHECKPOINT_ACTION_TYPE_WEBHOOK.equals(actionType)) {
                    dispatchWebhook(cp, summary, config);
                } else if (_NopMetadataCoreConstants.CHECKPOINT_ACTION_TYPE_NOTIFY.equals(actionType)) {
                    dispatchNotify(cp, summary, config);
                } else {
                    // executor validate 已拒绝未知 actionType（含 update_docs），此处 defense-in-depth 不静默执行
                    LOG.warn("dispatch encountered unexpected actionType (should have been validated): "
                            + "checkpointId={}, actionType={}", cp.getCheckpointId(), actionType);
                }
            } catch (Exception e) {
                LOG.error("action dispatch failed: actionType={}, checkpointId={}", actionType, cp.getCheckpointId(), e);
                errors.add(buildDispatchError(actionType, e));
            }
        }
    }

    // ============================================================
    // webhook action
    // ============================================================

    /**
     * webhook 动作：经 IHttpClient 向 config.url POST 执行摘要 JSON。
     *
     * <p>维度13-04 SSRF 防护（fail-closed）：
     * <ul>
     *   <li>URL 协议白名单（http/https），拒绝 file:/ftp:/data: 等。</li>
     *   <li>主机白名单：默认禁内网（RFC1918 + 169.254 + localhost）；显式配置 {@code webhook-allowed-hosts} 后允许。</li>
     *   <li>method 白名单：POST/PUT（避免 GET 触发副作用、TRACE 泄露等）。</li>
     *   <li>显式 timeout：默认 30s，避免长卡死。</li>
     *   <li>重定向 fail-closed（R6.2）：全局 {@code nop.http.client.follow-redirects} 开启时 fetch 前显式拒绝；
     *       客户端直返 3xx 时显式归类为投递失败（跳转目标不经 {@link #validateWebhookUrl} 复核）。</li>
     * </ul>
     * 失败时显式抛 ErrorCode（不静默跳过、不返回 200/默认值）。
     */
    private void dispatchWebhook(NopMetaQualityCheckpoint cp, Map<String, Object> summary,
                                  Map<String, Object> config) {
        if (httpClient == null) {
            throw new NopMetadataException(NopMetadataErrors.ERR_CHECKPOINT_WEBHOOK_NO_CLIENT)
                    .param("checkpointId", cp.getCheckpointId());
        }
        Object urlObj = config == null ? null : config.get("url");
        String url = urlObj == null ? null : String.valueOf(urlObj).trim();
        if (url == null || url.isEmpty()) {
            throw new NopMetadataException(NopMetadataErrors.ERR_CHECKPOINT_WEBHOOK_NO_URL)
                    .param("checkpointId", cp.getCheckpointId());
        }
        // 维度13-04：URL 协议 + 主机白名单 + method 白名单
        validateWebhookUrl(cp, url);
        String method = (config != null && config.get("method") != null)
                ? String.valueOf(config.get("method")).trim().toUpperCase()
                : "POST";
        validateWebhookMethod(cp, method);

        HttpRequest request = new HttpRequest();
        request.setUrl(url);
        request.setMethod(method);
        request.setHeader("Content-Type", "application/json");
        request.setBody(JsonTool.stringify(summary));
        // 维度13-04：显式 timeout（毫秒）。默认 30s，避免长卡死。
        request.setTimeout(webhookTimeoutSeconds * 1000L);

        // 维度13-04（R6.2）：重定向跟随 fail-closed 门禁（per-dispatchWebhook、fetch 前）——部署开启全局
        // 跟随（nop.http.client.follow-redirects=true）时显式拒绝投递，不产生未经 validateWebhookUrl 复核的跳转。
        if (followRedirectsEnabled) {
            throw new NopMetadataException(NopMetadataErrors.ERR_CHECKPOINT_WEBHOOK_REDIRECT_NOT_ALLOWED)
                    .param("checkpointId", cp.getCheckpointId())
                    .param("url", url)
                    .param("reason", "redirect following enabled globally (nop.http.client.follow-redirects=true) "
                            + "but webhook redirects are fail-closed");
        }

        IHttpResponse response = httpClient.fetch(request, null);
        if (response == null) {
            throw new NopMetadataException(NopMetadataErrors.ERR_CHECKPOINT_WEBHOOK_NULL_RESPONSE)
                    .param("checkpointId", cp.getCheckpointId())
                    .param("url", url);
        }
        int status = response.getHttpStatus();
        // 维度13-04（R6.2）：3xx 显式归类为投递失败（互补面——客户端未跟随时直返 3xx），不落入隐含的非 2xx 分支
        if (status >= 300 && status < 400) {
            throw new NopMetadataException(NopMetadataErrors.ERR_CHECKPOINT_WEBHOOK_REDIRECT_NOT_ALLOWED)
                    .param("checkpointId", cp.getCheckpointId())
                    .param("url", url)
                    .param("reason", "redirect response status " + status);
        }
        if (status < 200 || status >= 300) {
            throw new NopMetadataException(NopMetadataErrors.ERR_CHECKPOINT_WEBHOOK_NON_2XX)
                    .param("checkpointId", cp.getCheckpointId())
                    .param("url", url)
                    .param("status", status);
        }
    }

    /**
     * 维度13-04：webhook URL 安全校验（fail-closed）。
     * <ol>
     *   <li>协议必须 http/https。</li>
     *   <li>主机白名单：默认禁内网（RFC1918 + RFC3927 link-local + loopback）；显式配置 allowlist 后允许。</li>
     * </ol>
     */
    void validateWebhookUrl(NopMetaQualityCheckpoint cp, String url) {
        String lower = url.toLowerCase();
        boolean protocolOk = false;
        for (String proto : ALLOWED_WEBHOOK_PROTOCOLS) {
            if (lower.startsWith(proto)) {
                protocolOk = true;
                break;
            }
        }
        if (!protocolOk) {
            throw new NopMetadataException(NopMetadataErrors.ERR_CHECKPOINT_WEBHOOK_URL_BLOCKED)
                    .param("checkpointId", cp.getCheckpointId())
                    .param("url", url)
                    .param("reason", "protocol not in whitelist (http/https)");
        }
        String host = extractWebhookHost(url);
        if (host != null && !host.isEmpty() && isInternalHost(host)
                && !resolveAllowedWebhookHosts().contains(host.toLowerCase())) {
            throw new NopMetadataException(NopMetadataErrors.ERR_CHECKPOINT_WEBHOOK_URL_BLOCKED)
                    .param("checkpointId", cp.getCheckpointId())
                    .param("url", url)
                    .param("reason", "internal/link-local/loopback host not in allowed-hosts: " + host);
        }
    }

    /** 从 webhook URL 粗提取 host（http(s)://host[:port]/path?query 形式，支持 IPv6 [::1]）。 */
    static String extractWebhookHost(String url) {
        int schemeEnd = url.indexOf("://");
        if (schemeEnd < 0) {
            return null;
        }
        String rest = url.substring(schemeEnd + 3);
        int slash = rest.indexOf('/');
        int q = rest.indexOf('?');
        int at = rest.indexOf('@'); // user:pass@host 形式跳过
        int start = at >= 0 && at < slash ? at + 1 : 0;
        int end = minPositive(minPositive(slash, q), -1);
        String hostPort = end > 0 ? rest.substring(start, end) : rest.substring(start);
        // 移除可能的 userinfo 残留
        int lastAt = hostPort.lastIndexOf('@');
        if (lastAt >= 0) {
            hostPort = hostPort.substring(lastAt + 1);
        }
        // IPv6 形如 [::1] 或 [::1]:8080
        if (hostPort.startsWith("[")) {
            int closeBracket = hostPort.indexOf(']');
            if (closeBracket > 0) {
                return hostPort.substring(1, closeBracket);
            }
            return null;
        }
        int colon = hostPort.indexOf(':');
        String host = colon > 0 ? hostPort.substring(0, colon) : hostPort;
        return host.isEmpty() ? null : host;
    }

    private static int minPositive(int a, int b) {
        if (a < 0) return b;
        if (b < 0) return a;
        return Math.min(a, b);
    }

    /**
     * 是否内网/保留段主机（RFC1918 + RFC3927 link-local + loopback + 0.0.0.0/8 + IP 记法变体）。
     * 统一委托 {@link HostSecurityUtil}（与 MetaDataSourceConnectionProcessor 共享同一实现，
     * 语义与 JDK 解析一致，消除注释漂移与双实现差异）。
     *
     * <p>所有归一化都是确定性字符串/字面量解析，不触发 DNS
     * （仅对含 {@code :} 的 IPv6 字面量调用 {@link InetAddress#getByName}，字面量解析不查 DNS）。
     */
    static boolean isInternalHost(String host) {
        return HostSecurityUtil.isInternalHost(host);
    }

    /** 维度13-04：method 白名单校验（仅 POST/PUT）。 */
    void validateWebhookMethod(NopMetaQualityCheckpoint cp, String method) {
        if (!ALLOWED_WEBHOOK_METHODS.contains(method)) {
            throw new NopMetadataException(NopMetadataErrors.ERR_CHECKPOINT_WEBHOOK_METHOD_BLOCKED)
                    .param("checkpointId", cp.getCheckpointId())
                    .param("method", String.valueOf(method));
        }
    }

    // ============================================================
    // notify action
    // ============================================================

    /** notify 动作：经 IMessageService 向 config.channel 投递执行摘要信封。 */
    private void dispatchNotify(NopMetaQualityCheckpoint cp, Map<String, Object> summary,
                                 Map<String, Object> config) {
        if (messageService == null) {
            throw new NopMetadataException(NopMetadataErrors.ERR_CHECKPOINT_NOTIFY_NO_SERVICE)
                    .param("checkpointId", cp.getCheckpointId());
        }
        Object channelObj = config == null ? null : config.get("channel");
        String channel = channelObj == null ? null : String.valueOf(channelObj).trim();
        if (channel == null || channel.isEmpty()) {
            throw new NopMetadataException(NopMetadataErrors.ERR_CHECKPOINT_NOTIFY_NO_CHANNEL)
                    .param("checkpointId", cp.getCheckpointId());
        }
        Object recipients = (config != null) ? config.get("recipients") : null;

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("checkpointId", cp.getCheckpointId());
        envelope.put("summary", summary);
        if (recipients != null) {
            envelope.put("recipients", recipients);
        }
        messageService.send(channel, envelope);
    }

    // ============================================================
    // helpers
    // ============================================================

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> obtainErrors(Map<String, Object> summary) {
        Object existing = summary.get("errors");
        if (existing instanceof List) {
            return (List<Map<String, Object>>) existing;
        }
        List<Map<String, Object>> errors = new ArrayList<>();
        summary.put("errors", errors);
        return errors;
    }

    private static Map<String, Object> buildDispatchError(String actionType, Exception e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("source", "actionDispatch");
        m.put("actionType", actionType);
        m.put("error", NopMetadataHelper.toErrorMessage(e));
        return m;
    }

}

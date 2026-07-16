package io.nop.metadata.service.quality;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.message.IMessageService;
import io.nop.core.lang.json.JsonTool;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpClient;
import io.nop.http.api.client.IHttpResponse;
import io.nop.metadata.core._NopMetadataCoreConstants;
import io.nop.metadata.dao.entity.NopMetaQualityCheckpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    static final ErrorCode ERR_CHECKPOINT_WEBHOOK_NO_CLIENT =
            ErrorCode.define("metadata.checkpoint-webhook-no-client",
                    "Quality checkpoint webhook action configured but IHttpClient is not registered "
                            + "(host has not pulled an HTTP client implementation): {checkpointId}",
                    "checkpointId");
    static final ErrorCode ERR_CHECKPOINT_WEBHOOK_NO_URL =
            ErrorCode.define("metadata.checkpoint-webhook-no-url",
                    "Quality checkpoint webhook action config is missing required 'url': {checkpointId}",
                    "checkpointId");
    static final ErrorCode ERR_CHECKPOINT_NOTIFY_NO_SERVICE =
            ErrorCode.define("metadata.checkpoint-notify-no-service",
                    "Quality checkpoint notify action configured but IMessageService is not registered "
                            + "(host has not pulled a message service implementation): {checkpointId}",
                    "checkpointId");
    static final ErrorCode ERR_CHECKPOINT_NOTIFY_NO_CHANNEL =
            ErrorCode.define("metadata.checkpoint-notify-no-channel",
                    "Quality checkpoint notify action config is missing required 'channel': {checkpointId}",
                    "checkpointId");

    private final IHttpClient httpClient;
    private final IMessageService messageService;

    public CheckpointActionDispatcher(IHttpClient httpClient, IMessageService messageService) {
        this.httpClient = httpClient;
        this.messageService = messageService;
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
            // actions 不可解析 —— executor validate 已回退 store-only，此处不应到达
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

    /** webhook 动作：经 IHttpClient 向 config.url POST 执行摘要 JSON。 */
    private void dispatchWebhook(NopMetaQualityCheckpoint cp, Map<String, Object> summary,
                                  Map<String, Object> config) {
        if (httpClient == null) {
            throw new NopException(ERR_CHECKPOINT_WEBHOOK_NO_CLIENT)
                    .param("checkpointId", cp.getCheckpointId());
        }
        Object urlObj = config == null ? null : config.get("url");
        String url = urlObj == null ? null : String.valueOf(urlObj).trim();
        if (url == null || url.isEmpty()) {
            throw new NopException(ERR_CHECKPOINT_WEBHOOK_NO_URL)
                    .param("checkpointId", cp.getCheckpointId());
        }
        String method = (config != null && config.get("method") != null)
                ? String.valueOf(config.get("method"))
                : "POST";

        HttpRequest request = new HttpRequest();
        request.setUrl(url);
        request.setMethod(method);
        request.setHeader("Content-Type", "application/json");
        request.setBody(JsonTool.stringify(summary));

        IHttpResponse response = httpClient.fetch(request, null);
        if (response == null) {
            throw new NopException(ErrorCode.define("metadata.checkpoint-webhook-null-response",
                    "Quality checkpoint webhook returned null response: {checkpointId} url={url}",
                    "checkpointId", "url"))
                    .param("checkpointId", cp.getCheckpointId())
                    .param("url", url);
        }
        int status = response.getHttpStatus();
        if (status < 200 || status >= 300) {
            throw new NopException(ErrorCode.define("metadata.checkpoint-webhook-non-2xx",
                    "Quality checkpoint webhook returned non-2xx HTTP status: {checkpointId} url={url} status={status}",
                    "checkpointId", "url", "status"))
                    .param("checkpointId", cp.getCheckpointId())
                    .param("url", url)
                    .param("status", status);
        }
    }

    // ============================================================
    // notify action
    // ============================================================

    /** notify 动作：经 IMessageService 向 config.channel 投递执行摘要信封。 */
    private void dispatchNotify(NopMetaQualityCheckpoint cp, Map<String, Object> summary,
                                 Map<String, Object> config) {
        if (messageService == null) {
            throw new NopException(ERR_CHECKPOINT_NOTIFY_NO_SERVICE)
                    .param("checkpointId", cp.getCheckpointId());
        }
        Object channelObj = config == null ? null : config.get("channel");
        String channel = channelObj == null ? null : String.valueOf(channelObj).trim();
        if (channel == null || channel.isEmpty()) {
            throw new NopException(ERR_CHECKPOINT_NOTIFY_NO_CHANNEL)
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
        m.put("error", toErrorMessage(e));
        return m;
    }

    private static String toErrorMessage(Exception e) {
        String msg = e.getMessage();
        return msg != null ? msg : e.getClass().getName();
    }
}

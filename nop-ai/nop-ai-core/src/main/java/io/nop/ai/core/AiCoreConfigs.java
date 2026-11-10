package io.nop.ai.core;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.util.SourceLocation;

import static io.nop.api.core.config.AppConfig.varRef;

public interface AiCoreConfigs {
    SourceLocation s_loc = SourceLocation.fromClass(AiCoreConfigs.class);

    @Description("默认的LLM引擎名称。根据llm的名称从/nop/ai/llm/{name}.llm.xml中加载引擎配置")
    IConfigReference<String> CFG_AI_SERVICE_DEFAULT_LLM =
            varRef(s_loc, "nop.ai.service.default-llm", String.class, null);

    @Description("LLM引擎执行时是否自动打印所有请求和响应消息。"
            + "全局默认关闭（安全，凭据脱敏见 DefaultChatLogger）；"
            + "开启需显式配置为 true，且单模型可经 llm 配置 logMessage=false 覆盖关闭")
    IConfigReference<Boolean> CFG_AI_SERVICE_LOG_MESSAGE =
            varRef(s_loc, "nop.ai.service.log-message", Boolean.class, false);

    @Description("LLM引擎采用mock方式执行时所使用的输入输出目录")
    IConfigReference<String> CFG_AI_SERVICE_MOCK_DIR =
            varRef(s_loc, "nop.ai.service.mock-dir", String.class, "/nop/ai/mock");

    @Description("是否启用系统提示词")
    IConfigReference<Boolean> CFG_AI_SERVICE_ENABLE_WORK_MODE_SYSTEM_PROMPT =
            varRef(s_loc, "nop.ai.service.enable-work-mode-system-prompt", Boolean.class, true);

    @Description("LLM HTTP读取超时（毫秒），默认60秒")
    IConfigReference<Integer> CFG_AI_SERVICE_READ_TIMEOUT =
            varRef(s_loc, "nop.ai.service.read-timeout", Integer.class, 60000);

    @Description("LLM 流式调用的全程总期限（毫秒），默认10分钟。独立于 "
            + "nop.ai.service.read-timeout（后者仅约束响应头到达）：provider 返回 200 后连接挂死时，"
            + "聚合 future 在本期限内以显式错误终态（3.1-C7-001，两侧同批）。0 = 不启用总期限（不推荐）")
    IConfigReference<Long> CFG_AI_SERVICE_STREAM_TOTAL_TIMEOUT =
            varRef(s_loc, "nop.ai.service.stream-total-timeout", Long.class, 600_000L);

    @Description("LLM 流式调用的 chunk 间 inactivity 上界（毫秒），默认2分钟。"
            + "服务端静默（hang/代理黑洞/进程假死）超过本预算时聚合 future 以显式错误终态"
            + "（3.1-C7-001，两侧同批；与传输层 idle 读超时构成双层预算）。0 = 不启用 inactivity 检测（不推荐）")
    IConfigReference<Long> CFG_AI_SERVICE_STREAM_IDLE_TIMEOUT =
            varRef(s_loc, "nop.ai.service.stream-idle-timeout", Long.class, 120_000L);

    @Description("LLM 流式聚合的单次调用累积字符上界（text+reasoning+tool-call arguments 总和），"
            + "默认200万。超界 fail-fast 终态化流（3.1-C5-001 聚合无界面）。0 = 不设上界（不推荐）")
    IConfigReference<Integer> CFG_AI_SERVICE_STREAM_MAX_ACCUMULATED_CHARS =
            varRef(s_loc, "nop.ai.service.stream-max-accumulated-chars", Integer.class, 2_000_000);

    @Description("LLM 流式聚合的单次调用 tool-call 条目数上界（按线上 itemIndex 计），默认100。"
            + "超界 fail-fast 终态化流（3.1-C5-001 线上可控键累加器无上界面）。0 = 不设上界（不推荐）")
    IConfigReference<Integer> CFG_AI_SERVICE_STREAM_MAX_TOOL_CALLS =
            varRef(s_loc, "nop.ai.service.stream-max-tool-calls", Integer.class, 100);

    @Description("AI 请求/响应持久化是否启用AES加密（nop.crypt.default-enc-key 或 setTextCipher 注入密钥）。"
            + "默认关闭以兼容历史明文文件；开启后新写入内容加密，旧明文文件仍可读取")
    IConfigReference<Boolean> CFG_AI_PERSIST_EXCHANGE_ENCRYPT =
            varRef(s_loc, "nop.ai.persist.exchange-encrypt", Boolean.class, false);

    @Description("AiChat 响应缓存的过期时间（秒），0=永不过期（兼容默认）。"
            + "读取时惰性过期：缓存条目超过 TTL 视为 miss 并删除，不主动清扫")
    IConfigReference<Long> CFG_AI_SERVICE_CACHE_TTL =
            varRef(s_loc, "nop.ai.service.cache-ttl", Long.class, 0L);

    @Description("LLM 调用本地限流（llm.xml 配置了 rateLimit 时）的许可获取超时（毫秒）。"
            + "超时未获许可时抛 ERR_AI_RATE_LIMITED（携带 httpStatus=429，LlmErrorClassifier 判为 "
            + "RATE_LIMITED 可重试）——替换旧的无限阻塞 acquire()，消除挂起风险（MA6.3-AR-6）。"
            + "0 = 立即失败（fail-fast），不等待")
    IConfigReference<Long> CFG_AI_SERVICE_RATE_LIMIT_ACQUIRE_TIMEOUT =
            varRef(s_loc, "nop.ai.service.rate-limit-acquire-timeout", Long.class, 1000L);

    @Description("Embedding 调用（IEmbeddingModel/EmbeddingServiceImpl，K1）的默认 provider"
            + "（/nop/ai/llm/{provider}.llm.xml 配置名）。EmbeddingOptions.provider 非空时优先；"
            + "两者皆空时 embedding 调用 fail-loud（无隐式 provider）")
    IConfigReference<String> CFG_AI_EMBEDDING_DEFAULT_LLM =
            varRef(s_loc, "nop.ai.embedding.default-llm", String.class, null);

    @Description("聊天日志保留期（天），缺省 0 = 不清理（兼容默认，运维须自行管理磁盘包络）。"
            + ">0 时 DefaultChatLogger 在写入路径上惰性删除早于 今天-保留期 的日期分区目录"
            + "（布局 logDir/yyyy/MM-dd/，目录级删除），每日期至多全量扫描一次（once-per-date 节流）。"
            + "清理为 best-effort，失败仅记日志，不影响日志写入主路径（3.1-MA-21，plan 2555 裁定 I）")
    IConfigReference<Integer> CFG_AI_CHAT_LOG_RETENTION_DAYS =
            varRef(s_loc, "nop.ai.chat-log.retention-days", Integer.class, 0);
}

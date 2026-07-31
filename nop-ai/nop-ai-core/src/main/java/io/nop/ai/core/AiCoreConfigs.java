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

    @Description("LLM HTTP连接超时（毫秒），默认30秒")
    IConfigReference<Integer> CFG_AI_SERVICE_CONNECT_TIMEOUT =
            varRef(s_loc, "nop.ai.service.connect-timeout", Integer.class, 30000);

    @Description("LLM HTTP读取超时（毫秒），默认60秒")
    IConfigReference<Integer> CFG_AI_SERVICE_READ_TIMEOUT =
            varRef(s_loc, "nop.ai.service.read-timeout", Integer.class, 60000);

    @Description("AiChatExchange 持久化是否启用AES加密（nop.crypt.default-enc-key 或 setTextCipher 注入密钥）。"
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
}

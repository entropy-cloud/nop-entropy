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

    @Description("LLM引擎执行时是否自动打印所有请求和响应消息")
    IConfigReference<Boolean> CFG_AI_SERVICE_LOG_MESSAGE =
            varRef(s_loc, "nop.ai.service.log-message", Boolean.class, true);

    @Description("LLM引擎采用mock方式执行时所使用的输入输出目录")
    IConfigReference<String> CFG_AI_SERVICE_MOCK_DIR =
            varRef(s_loc, "nop.ai.service.mock-dir", String.class, "/nop/ai/mock");
}
